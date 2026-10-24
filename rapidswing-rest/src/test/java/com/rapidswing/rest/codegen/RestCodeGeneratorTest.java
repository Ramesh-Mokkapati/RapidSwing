package com.rapidswing.rest.codegen;

import com.rapidswing.crud.CrudRepository;
import com.rapidswing.rest.OpenApiSpec;
import com.rapidswing.rest.RestClient;
import com.rapidswing.rest.sample.SampleApiServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generates source for the sample API, compiles it with javac, and runs it against the API. */
class RestCodeGeneratorTest {

    @TempDir
    Path dir;

    private SampleApiServer server;
    private RestClient client;
    private Map<String, String> files;

    @BeforeEach
    void generate() {
        server = SampleApiServer.start(0);
        client = new RestClient(server.getBaseUrl());
        files = new RestCodeGenerator(OpenApiSpec.fetch(client), "com.acme.shop").generate();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void generatesModelRepositoryAndScreenPerResource() {
        assertTrue(files.containsKey("com/acme/shop/model/Customer.java"));
        assertTrue(files.containsKey("com/acme/shop/api/ProductRepository.java"));
        assertTrue(files.containsKey("com/acme/shop/ui/OrderScreen.java"));
        assertTrue(files.containsKey("com/acme/shop/ui/ApiViews.java"));

        String screen = files.get("com/acme/shop/ui/CustomerScreen.java");
        assertTrue(screen.contains(".text(\"Email\", edit.property(Customer::getEmail, Customer::setEmail)).required()"));
        assertTrue(screen.contains(".validate(Validators.email())"));
        assertTrue(screen.contains(".combo(\"Tier\", edit.property(Customer::getTier, Customer::setTier), \"BRONZE\", \"SILVER\", \"GOLD\")"));
        assertTrue(files.get("com/acme/shop/ui/ProductScreen.java").contains(".validate(Validators.range(0, 100000))"));
        assertTrue(files.get("com/acme/shop/api/ProductRepository.java").contains("does not allow deleting"));
    }

    @Test
    void generatedCodeCompilesAndTalksToTheApi() throws Exception {
        Path sources = dir.resolve("src");
        Path classes = Files.createDirectories(dir.resolve("classes"));
        List<String> arguments = new ArrayList<>();
        arguments.add("-d");
        arguments.add(classes.toString());
        arguments.add("-classpath");
        arguments.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        arguments.add("-encoding");
        arguments.add("UTF-8");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = sources.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue().getBytes(StandardCharsets.UTF_8));
            arguments.add(path.toString());
        }

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "tests must run on a JDK");
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int result = javac.run(null, null, errors, arguments.toArray(new String[0]));
        assertEquals(0, result, "Generated code does not compile:\n" + errors.toString("UTF-8"));

        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> repositoryClass = loader.loadClass("com.acme.shop.api.CustomerRepository");
            Class<?> customerClass = loader.loadClass("com.acme.shop.model.Customer");
            @SuppressWarnings("unchecked")
            CrudRepository<Object> customers = (CrudRepository<Object>) repositoryClass
                    .getConstructor(RestClient.class).newInstance(client);

            List<Object> all = customers.findAll();
            assertEquals(3, all.size());
            assertEquals("Asha Rao", all.get(0).toString());

            Object created = customerClass.getConstructor().newInstance();
            customerClass.getMethod("setName", String.class).invoke(created, "Noah Schmidt");
            customerClass.getMethod("setEmail", String.class).invoke(created, "noah@example.com");
            Object saved = customers.save(created);
            assertNotNull(customerClass.getMethod("getId").invoke(saved));
            assertEquals(4, customers.findAll().size());
        }
    }

    @Test
    void namesBecomeValidJava() {
        assertEquals("firstName", RestCodeGenerator.javaName("first-name"));
        assertEquals("classValue", RestCodeGenerator.javaName("class"));
        assertEquals("field2fa", RestCodeGenerator.javaName("2fa"));
    }
}
