package com.rapidswing.generator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What to generate: the application type, names and optional features. Names left empty are derived:
 * {@code "Order Desk"} gives artifact id {@code order-desk} and package {@code com.example.orderdesk}.
 */
public final class ProjectSpec {

    /** Optional features of a generated application. */
    public enum Feature {
        LOGIN("login", "Sign-in with roles"),
        I18N("i18n", "Translations (English, German, Spanish)"),
        LOG_VIEWER("logviewer", "Log viewer"),
        ENCRYPTION("encryption", "File encryption (AES-256)"),
        DARK_THEME("dark", "Dark theme by default");

        private final String id;
        private final String title;

        Feature(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public static Feature parse(String text) {
            for (Feature feature : values()) {
                if (feature.id.equalsIgnoreCase(text.trim())) {
                    return feature;
                }
            }
            throw new IllegalArgumentException("Unknown feature '" + text + "': use login, i18n, logviewer, "
                    + "encryption or dark");
        }
    }

    /** Safe inside Java strings, properties files and XML without escaping. */
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]*");
    private static final Pattern ARTIFACT = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final Pattern GROUP = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final Pattern PACKAGE = Pattern.compile("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*");
    public static final String DEFAULT_GROUP = "com.example";

    private AppType type = AppType.SDI;
    private String name = "";
    private String artifactId = "";
    private String groupId = "";
    private String packageName = "";
    private Path directory = Paths.get(".");
    private final Set<Feature> features = EnumSet.noneOf(Feature.class);
    private String openApiSource = "";

    public AppType getType() {
        return type;
    }

    public ProjectSpec type(AppType appType) {
        this.type = appType;
        return this;
    }

    /** The application's display name, e.g. "Order Desk". */
    public String getName() {
        return name;
    }

    public ProjectSpec name(String displayName) {
        this.name = displayName.trim();
        return this;
    }

    public String getArtifactId() {
        return artifactId.isEmpty() ? deriveArtifactId(name) : artifactId;
    }

    public ProjectSpec artifactId(String id) {
        this.artifactId = id.trim();
        return this;
    }

    public String getGroupId() {
        return groupId.isEmpty() ? DEFAULT_GROUP : groupId;
    }

    public ProjectSpec groupId(String id) {
        this.groupId = id.trim();
        return this;
    }

    public String getPackageName() {
        return packageName.isEmpty() ? getGroupId() + "." + getArtifactId().replace("-", "") : packageName;
    }

    public ProjectSpec packageName(String name) {
        this.packageName = name.trim();
        return this;
    }

    /** The folder the project folder is created in. */
    public Path getDirectory() {
        return directory;
    }

    public ProjectSpec directory(Path parent) {
        this.directory = parent;
        return this;
    }

    public Path getProjectDirectory() {
        return directory.resolve(getArtifactId());
    }

    public Set<Feature> getFeatures() {
        return EnumSet.copyOf(features);
    }

    public boolean has(Feature feature) {
        return features.contains(feature);
    }

    public ProjectSpec feature(Feature feature, boolean enabled) {
        if (enabled) {
            features.add(feature);
        } else {
            features.remove(feature);
        }
        return this;
    }

    /** URL of a running API (its OpenAPI document is fetched from {@code /v3/api-docs}) or a path to a JSON file. */
    public String getOpenApiSource() {
        return openApiSource;
    }

    public ProjectSpec openApiSource(String source) {
        this.openApiSource = source.trim();
        return this;
    }

    public boolean hasOpenApi() {
        return !openApiSource.isEmpty();
    }

    /** Problems that prevent generation, as readable sentences; empty when everything is fine. */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (name.isEmpty()) {
            problems.add("Enter an application name.");
        } else if (!NAME.matcher(name).matches()) {
            problems.add("The application name may contain letters, digits, spaces, dots, hyphens and underscores.");
        }
        if (!ARTIFACT.matcher(getArtifactId()).matches()) {
            problems.add("The artifact id must be lower case words separated by hyphens, e.g. order-desk.");
        }
        if (!GROUP.matcher(getGroupId()).matches()) {
            problems.add("The group id must be a lower case dotted name, e.g. com.example.");
        }
        if (!PACKAGE.matcher(getPackageName()).matches()) {
            problems.add("The package must be a lower case dotted Java name, e.g. com.example.orderdesk.");
        }
        return problems;
    }

    /** "Order Desk 2" becomes "order-desk-2". */
    static String deriveArtifactId(String displayName) {
        String id = displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (id.isEmpty()) {
            return "app";
        }
        return Character.isLetter(id.charAt(0)) ? id : "app-" + id;
    }
}
