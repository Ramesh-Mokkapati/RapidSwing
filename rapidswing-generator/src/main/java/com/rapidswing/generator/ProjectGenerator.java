package com.rapidswing.generator;

import com.rapidswing.rest.OpenApiSpec;
import com.rapidswing.rest.RestClient;
import com.rapidswing.rest.codegen.RestCodeGenerator;
import com.rapidswing.security.PasswordHasher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generates a complete Maven project for a {@link ProjectSpec}: application class, views, translations, a smoke
 * test that uses {@code rapidswing-testing}, and, if an OpenAPI document is given, typed screens for its resources.
 * <pre>
 * ProjectGenerator.Result result = new ProjectGenerator().generate(new ProjectSpec()
 *         .type(AppType.MDI).name("Order Desk").directory(Paths.get("projects")), false);
 * </pre>
 */
public final class ProjectGenerator {

    /** The RapidSwing version generated projects depend on. */
    public static final String RAPIDSWING_VERSION = "0.1.0-SNAPSHOT";

    private static final String TEMPLATES = "/com/rapidswing/generator/templates/";
    private static final String PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What {@link #generate} produced. */
    public static final class Result {
        private final Path directory;
        private final Map<String, String> files;
        private final String adminPassword;
        private final OpenApiSpec api;

        Result(Path directory, Map<String, String> files, String adminPassword, OpenApiSpec api) {
            this.directory = directory;
            this.files = Collections.unmodifiableMap(files);
            this.adminPassword = adminPassword;
            this.api = api;
        }

        public Path getDirectory() {
            return directory;
        }

        /** File contents by path relative to the project directory. */
        public Map<String, String> getFiles() {
            return files;
        }

        /**
         * The generated {@code admin} account's password when sign-in was chosen, else {@code null}. Only its
         * hash is written to the project: show it to the user once.
         */
        public String getAdminPassword() {
            return adminPassword;
        }

        /** The OpenAPI document screens were generated for, or {@code null}. */
        public OpenApiSpec getApi() {
            return api;
        }
    }

    /** Validates, loads the OpenAPI document if any, renders and writes the project. */
    public Result generate(ProjectSpec spec, boolean overwrite) throws IOException {
        if (!spec.validate().isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", spec.validate()));
        }
        Path directory = spec.getProjectDirectory();
        if (Files.isDirectory(directory) && !overwrite && !isEmpty(directory)) {
            throw new IOException(directory.toAbsolutePath() + " already exists and is not empty");
        }
        OpenApiSpec api = spec.hasOpenApi() ? loadApi(spec.getOpenApiSource()) : null;
        String password = null;
        String hash = "";
        if (spec.has(ProjectSpec.Feature.LOGIN)) {
            password = randomPassword(16);
            char[] secret = password.toCharArray();
            hash = PasswordHasher.hash(secret);
            Arrays.fill(secret, '\0');
        }
        Map<String, String> files = render(spec, api, hash);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = directory.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, file.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return new Result(directory, files, password, api);
    }

    /** Renders all files without writing anything: paths relative to the project directory. */
    public Map<String, String> render(ProjectSpec spec, OpenApiSpec api, String adminPasswordHash) {
        Map<String, String> values = values(spec, api, adminPasswordHash);
        Map<String, Boolean> flags = flags(spec, api != null);
        Map<String, String> files = new LinkedHashMap<>();
        renderFolder("common", values, flags, files);
        renderFolder(spec.getType().name().toLowerCase(Locale.ROOT), values, flags, files);
        if (api != null) {
            for (Map.Entry<String, String> source : new RestCodeGenerator(api, spec.getPackageName() + ".rest").generate().entrySet()) {
                files.put("src/main/java/" + source.getKey(), source.getValue());
            }
        }
        return files;
    }

    /** Downloads the document from a running API (or a URL to the JSON), or reads it from a file. */
    public static OpenApiSpec loadApi(String source) throws IOException {
        if (source.matches("(?i)https?://.*")) {
            if (source.contains("/v3/api-docs") || source.toLowerCase(Locale.ROOT).endsWith(".json")) {
                return OpenApiSpec.parse(new RestClient(source).get(source));
            }
            return OpenApiSpec.fetch(new RestClient(source));
        }
        return OpenApiSpec.load(Paths.get(source));
    }

    private static Map<String, String> values(ProjectSpec spec, OpenApiSpec api, String adminPasswordHash) {
        Map<String, String> values = new HashMap<>();
        values.put("package", spec.getPackageName());
        values.put("packagePath", spec.getPackageName().replace('.', '/'));
        values.put("groupId", spec.getGroupId());
        values.put("artifactId", spec.getArtifactId());
        values.put("appName", spec.getName());
        values.put("rapidswingVersion", RAPIDSWING_VERSION);
        values.put("theme", spec.has(ProjectSpec.Feature.DARK_THEME) ? "dark" : "light");
        values.put("adminHash", adminPasswordHash);
        values.put("mainView", mainView(spec.getType()));
        values.put("apiUrl", apiUrl(spec, api));
        values.put("width", spec.getType() == AppType.DIALOG ? "520" : "1100");
        values.put("height", spec.getType() == AppType.DIALOG ? "420" : "720");
        return values;
    }

    private static Map<String, Boolean> flags(ProjectSpec spec, boolean rest) {
        Map<String, Boolean> flags = new HashMap<>();
        for (AppType type : AppType.values()) {
            flags.put(type.name().toLowerCase(Locale.ROOT), spec.getType() == type);
        }
        flags.put("documents", spec.getType() == AppType.SDI || spec.getType() == AppType.MDI);
        flags.put("login", spec.has(ProjectSpec.Feature.LOGIN));
        flags.put("i18n", spec.has(ProjectSpec.Feature.I18N));
        flags.put("logviewer", spec.has(ProjectSpec.Feature.LOG_VIEWER));
        flags.put("encryption", spec.has(ProjectSpec.Feature.ENCRYPTION));
        flags.put("tools", spec.has(ProjectSpec.Feature.LOG_VIEWER) || spec.has(ProjectSpec.Feature.ENCRYPTION));
        flags.put("dark", spec.has(ProjectSpec.Feature.DARK_THEME));
        flags.put("rest", rest);
        return flags;
    }

    static String mainView(AppType type) {
        switch (type) {
            case MDI:
                return "WorkspaceView";
            case DIALOG:
                return "MainDialogView";
            case OUTLOOK:
                return "OutlookView";
            default:
                return "DocumentView";
        }
    }

    private static String apiUrl(ProjectSpec spec, OpenApiSpec api) {
        if (api != null && api.getServerUrl() != null) {
            return api.getServerUrl();
        }
        String source = spec.getOpenApiSource();
        if (source.matches("(?i)https?://.*")) {
            int docs = source.indexOf("/v3/api-docs");
            return docs > 0 ? source.substring(0, docs) : source;
        }
        return "http://localhost:8080";
    }

    private static void renderFolder(String folder, Map<String, String> values, Map<String, Boolean> flags,
                                     Map<String, String> files) {
        for (String line : readLines(TEMPLATES + folder + "/files.txt")) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            if (entry.startsWith("if:")) {
                String flag = entry.substring(3, entry.indexOf(' '));
                if (!Boolean.TRUE.equals(flags.get(flag))) {
                    continue;
                }
                entry = entry.substring(entry.indexOf(' ') + 1).trim();
            }
            String source = entry;
            String target = entry.endsWith(".tmpl") ? entry.substring(0, entry.length() - 5) : entry;
            int arrow = entry.indexOf("->");
            if (arrow > 0) {
                source = entry.substring(0, arrow).trim();
                target = entry.substring(arrow + 2).trim();
            }
            String template = read(TEMPLATES + folder + "/" + source);
            files.put(target.replace("__package__", values.get("packagePath")), Template.render(template, values, flags));
        }
    }

    private static Iterable<String> readLines(String resource) {
        return Arrays.asList(read(resource).split("\r?\n"));
    }

    private static String read(String resource) {
        try (InputStream in = ProjectGenerator.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing template " + resource);
            }
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) > 0) {
                    text.append(buffer, 0, read);
                }
            }
            return text.toString().replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isEmpty(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return !entries.findAny().isPresent();
        }
    }

    static String randomPassword(int length) {
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }
}
