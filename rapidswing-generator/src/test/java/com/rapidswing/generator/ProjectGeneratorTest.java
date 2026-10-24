package com.rapidswing.generator;

import com.rapidswing.rest.OpenApiSpec;
import com.rapidswing.rest.RestClient;
import com.rapidswing.rest.sample.SampleApiServer;
import com.rapidswing.security.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectGeneratorTest {

    @TempDir
    Path dir;

    private static ProjectSpec spec(AppType type, boolean allFeatures) {
        ProjectSpec spec = new ProjectSpec().type(type).name("Order Desk");
        for (ProjectSpec.Feature feature : ProjectSpec.Feature.values()) {
            spec.feature(feature, allFeatures);
        }
        return spec;
    }

    @Test
    void namesAreDerivedAndValidated() {
        ProjectSpec spec = new ProjectSpec().name("Order Desk 2");
        assertEquals("order-desk-2", spec.getArtifactId());
        assertEquals("com.example.orderdesk2", spec.getPackageName());
        assertTrue(spec.validate().isEmpty());

        assertFalse(new ProjectSpec().name("").validate().isEmpty());
        assertFalse(new ProjectSpec().name("A <b>").validate().isEmpty());
        assertFalse(new ProjectSpec().name("Notes").packageName("Com.Example").validate().isEmpty());
        assertEquals("app-42", ProjectSpec.deriveArtifactId("42"));
    }

    @Test
    void featuresOnlyAddWhatTheyNeed() {
        Map<String, String> plain = new ProjectGenerator().render(spec(AppType.SDI, false), null, "");
        Map<String, String> full = new ProjectGenerator().render(spec(AppType.SDI, true), null, "HASH");

        assertFalse(plain.containsKey("src/main/resources/messages_de.properties"));
        assertTrue(full.containsKey("src/main/resources/messages_de.properties"));
        assertFalse(plain.get("pom.xml").contains("rapidswing-logviewer"));
        assertTrue(full.get("pom.xml").contains("rapidswing-logviewer"));
        assertFalse(plain.get("src/main/java/com/example/orderdesk/AppSupport.java").contains("Authenticator"));
        assertTrue(full.get("src/main/java/com/example/orderdesk/AppSupport.java").contains("\"HASH\""));
        assertTrue(full.get("src/main/resources/app.properties").contains("app.theme=dark"));
        assertFalse(plain.get("src/main/java/com/example/orderdesk/App.java").contains("{{"));
    }

    @Test
    void writesTheProjectAndShowsTheAdminPasswordOnce() throws IOException {
        ProjectSpec spec = spec(AppType.DIALOG, false).feature(ProjectSpec.Feature.LOGIN, true).directory(dir);

        ProjectGenerator.Result result = new ProjectGenerator().generate(spec, false);

        Path project = dir.resolve("order-desk");
        assertEquals(project, result.getDirectory());
        assertTrue(Files.exists(project.resolve("src/main/java/com/example/orderdesk/MainDialogView.java")));
        String password = result.getAdminPassword();
        assertNotNull(password);
        String support = new String(Files.readAllBytes(project.resolve("src/main/java/com/example/orderdesk/AppSupport.java")),
                StandardCharsets.UTF_8);
        assertFalse(support.contains(password), "only the hash is stored");
        String hash = support.substring(support.indexOf("pbkdf2-sha256$"), support.indexOf('"', support.indexOf("pbkdf2-sha256$")));
        assertTrue(PasswordHasher.verify(password.toCharArray(), hash));

        assertThrows(IOException.class, () -> new ProjectGenerator().generate(spec, false), "never overwrites");
        assertNull(new ProjectGenerator().generate(spec(AppType.SDI, false).name("Other").directory(dir), false)
                .getAdminPassword());
    }

    /** Every type, with no features and with all features, must compile against the real framework. */
    @Test
    void everyGeneratedProjectCompiles() throws IOException {
        try (SampleApiServer api = SampleApiServer.start(0)) {
            OpenApiSpec shop = OpenApiSpec.fetch(new RestClient(api.getBaseUrl()));
            for (AppType type : AppType.values()) {
                compile(type + " without features", new ProjectGenerator().render(spec(type, false), null, ""));
                compile(type + " with all features", new ProjectGenerator().render(spec(type, true), shop,
                        PasswordHasher.hash("secret".toCharArray(), 1000)));
            }
        }
    }

    private void compile(String name, Map<String, String> files) throws IOException {
        Path root = Files.createTempDirectory(dir, "project");
        Path classes = Files.createDirectories(root.resolve("classes"));
        List<String> arguments = new ArrayList<>();
        arguments.add("-d");
        arguments.add(classes.toString());
        arguments.add("-classpath");
        arguments.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        arguments.add("-encoding");
        arguments.add("UTF-8");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = root.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue().getBytes(StandardCharsets.UTF_8));
            if (file.getKey().endsWith(".java")) {
                arguments.add(path.toString());
            }
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "tests must run on a JDK");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int result = javac.run(null, null, errors, arguments.toArray(new String[0]));
        assertEquals(0, result, name + " does not compile:\n" + errors.toString("UTF-8"));
    }
}
