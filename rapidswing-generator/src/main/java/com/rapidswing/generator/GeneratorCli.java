package com.rapidswing.generator;

import com.rapidswing.rest.ApiResource;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The generator's command line.
 * <pre>
 * java -jar rapidswing-generator-app.jar --type mdi --name "Order Desk" --features login,i18n
 *         [--artifact order-desk] [--group com.example] [--package com.example.orderdesk] [--dir .]
 *         [--openapi http://localhost:8080 | api-docs.json] [--force]
 * </pre>
 */
final class GeneratorCli {

    static final String USAGE = String.join("\n",
            "Usage: java -jar rapidswing-generator-app.jar [options]   (no options opens the wizard)",
            "",
            "  --type sdi|mdi|dialog|outlook   application type (default: sdi)",
            "  --name \"Order Desk\"             application name (required)",
            "  --artifact order-desk           Maven artifact id and folder name (default: from the name)",
            "  --group com.example             Maven group id (default: com.example)",
            "  --package com.example.orderdesk Java package (default: group + artifact)",
            "  --dir .                         where to create the project folder (default: current folder)",
            "  --features login,i18n,logviewer,encryption,dark",
            "  --openapi URL|FILE              generate screens for a REST API from its OpenAPI 3 document",
            "                                  (a URL of a Spring Boot app with springdoc, or a JSON file)",
            "  --force                         write into an existing, non-empty folder",
            "  --help                          show this help");

    private GeneratorCli() {
    }

    /** Runs the command line; returns the process exit code. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, String> options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println();
            err.println(USAGE);
            return 2;
        }
        if (options.containsKey("help")) {
            out.println(USAGE);
            return 0;
        }
        ProjectSpec spec = new ProjectSpec();
        try {
            spec.type(AppType.parse(options.getOrDefault("type", "sdi")))
                    .name(options.getOrDefault("name", ""))
                    .artifactId(options.getOrDefault("artifact", ""))
                    .groupId(options.getOrDefault("group", ""))
                    .packageName(options.getOrDefault("package", ""))
                    .directory(Paths.get(options.getOrDefault("dir", ".")))
                    .openApiSource(options.getOrDefault("openapi", ""));
            for (String feature : options.getOrDefault("features", "").split(",")) {
                if (!feature.trim().isEmpty()) {
                    spec.feature(ProjectSpec.Feature.parse(feature), true);
                }
            }
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        }
        List<String> problems = spec.validate();
        if (!problems.isEmpty()) {
            problems.forEach(err::println);
            return 2;
        }
        try {
            ProjectGenerator.Result result = new ProjectGenerator().generate(spec, options.containsKey("force"));
            for (String line : summary(spec, result)) {
                out.println(line);
            }
            return 0;
        } catch (IOException | RuntimeException e) {
            // E.g. an existing folder, an unreachable API or an invalid OpenAPI document.
            err.println("Could not generate the project: " + e.getMessage());
            return 1;
        }
    }

    /** What was generated and what to do next. Shared with the wizard. */
    static List<String> summary(ProjectSpec spec, ProjectGenerator.Result result) {
        List<String> lines = new ArrayList<>();
        lines.add("Generated " + spec.getType().getTitle() + " application \"" + spec.getName() + "\" in "
                + result.getDirectory().toAbsolutePath().normalize() + " (" + result.getFiles().size() + " files)");
        if (result.getApi() != null) {
            List<String> titles = new ArrayList<>();
            for (ApiResource resource : result.getApi().getResources()) {
                titles.add(resource.getTitle());
            }
            lines.add("  REST screens: " + String.join(", ", titles) + " (from " + result.getApi().getTitle() + ")");
        }
        if (result.getAdminPassword() != null) {
            lines.add("  Sign-in account: admin / " + result.getAdminPassword());
            lines.add("  (shown only now: the project stores just a hash of the password)");
        }
        lines.add("");
        lines.add("Next steps:");
        lines.add("  cd " + result.getDirectory().toAbsolutePath().normalize());
        lines.add("  mvn verify");
        lines.add("  java -jar target/" + spec.getArtifactId() + "-1.0.0-SNAPSHOT-app.jar");
        return lines;
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String name = arg.substring(2);
            if (name.equals("help") || name.equals("force")) {
                options.put(name, "true");
            } else if (i + 1 < args.length) {
                options.put(name, args[++i]);
            } else {
                throw new IllegalArgumentException("Missing value for " + arg);
            }
            if (!Arrays.asList("help", "force", "type", "name", "artifact", "group", "package", "dir", "features",
                    "openapi").contains(name)) {
                throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return options;
    }
}
