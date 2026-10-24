package com.rapidswing.generator;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.binding.Bindings;
import com.rapidswing.forms.Form;
import com.rapidswing.forms.FormBuilder;
import com.rapidswing.property.Property;
import com.rapidswing.rest.ApiResource;
import com.rapidswing.rest.OpenApiSpec;
import com.rapidswing.task.TaskService;
import com.rapidswing.ui.AppAction;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The application wizard's content: four steps (type, project, features, review) and Generate. It is a panel, so
 * it can be tested without a window; {@link GeneratorWizard} shows it in a frame.
 */
final class WizardPanel extends JPanel {

    private static final String[][] STEPS = {
            {"Choose the type of application", "Pick the window structure that fits your application, like the MFC Application Wizard."},
            {"Name your project", "The project folder, Maven coordinates and Java package."},
            {"Choose features", "Everything can still be changed in the generated code."},
            {"Review and generate", "Check the details, then generate the project."}};

    private final TaskService tasks;
    private final Consumer<ProjectGenerator.Result> onGenerated;
    private final Runnable onClose;

    private final CardLayout cards = new CardLayout();
    private final JPanel steps = new JPanel(cards);
    private final JLabel title = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final JLabel stepNumber = new JLabel();
    private final JLabel problems = new JLabel(" ");
    private final JButton backButton;
    private final JButton nextButton;
    private final JButton openFolderButton;
    private final AppAction backAction = AppAction.of("Back", this::back);
    private final AppAction nextAction = AppAction.of("Next", this::next);

    private final Map<AppType, TypeCard> typeCards = new EnumMap<>(AppType.class);
    private final Property<String> name = Property.of("");
    private final Property<String> artifact = Property.of("");
    private final Property<String> group = Property.of("");
    private final Property<String> packageName = Property.of("");
    private final Property<String> directory = Property.of(Paths.get(".").toAbsolutePath().normalize().toString());
    private final Map<ProjectSpec.Feature, Property<Boolean>> features = new EnumMap<>(ProjectSpec.Feature.class);
    private final Property<Boolean> restEnabled = Property.of(false);
    private final Property<String> openApiSource = Property.of("");
    private final JLabel apiStatus = new JLabel(" ");
    private final JTextArea summary = new JTextArea();
    private Form projectForm;

    private AppType type = AppType.SDI;
    private int step;
    private boolean busy;
    private OpenApiSpec checkedApi;
    private String checkedSource;
    private ProjectGenerator.Result result;

    WizardPanel(TaskService tasks, Consumer<ProjectGenerator.Result> onGenerated, Runnable onClose) {
        super(new BorderLayout());
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.onGenerated = Objects.requireNonNull(onGenerated, "onGenerated");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
        for (ProjectSpec.Feature feature : ProjectSpec.Feature.values()) {
            features.put(feature, Property.of(false));
        }

        steps.add(typeStep(), "0");
        steps.add(projectStep(), "1");
        steps.add(featureStep(), "2");
        steps.add(summaryStep(), "3");

        title.putClientProperty(FlatClientProperties.STYLE_CLASS, "h2");
        subtitle.setEnabled(false);
        stepNumber.setEnabled(false);
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        header.add(stepNumber, BorderLayout.EAST);

        backButton = new JButton(backAction);
        nextButton = new JButton(nextAction);
        openFolderButton = new JButton(AppAction.of("Open folder", this::openFolder));
        openFolderButton.setVisible(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(openFolderButton);
        buttons.add(backButton);
        buttons.add(nextButton);
        buttons.add(new JButton(AppAction.of("Cancel", onClose)));
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        problems.setForeground(error != null ? error : new Color(0xCC3333));
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 20, 14, 20));
        footer.add(problems, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);

        steps.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
        add(header, BorderLayout.NORTH);
        add(steps, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        name.onChange(value -> updatePlaceholders());
        group.onChange(value -> updatePlaceholders());
        updatePlaceholders();
        selectType(AppType.SDI);
        showStep(0);
    }

    // ---- Steps ---------------------------------------------------------------------------------------

    private JComponent typeStep() {
        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        for (AppType appType : AppType.values()) {
            TypeCard card = new TypeCard(appType);
            typeCards.put(appType, card);
            grid.add(card);
        }
        return grid;
    }

    private JComponent projectStep() {
        JPanel location = new JPanel(new BorderLayout(6, 0));
        JTextField directoryField = new JTextField();
        Bindings.text(directoryField, directory);
        location.add(directoryField, BorderLayout.CENTER);
        location.add(new JButton(AppAction.of("Browse...", this::chooseDirectory)), BorderLayout.EAST);

        projectForm = Form.builder()
                .text("Application name", name).required().placeholder("Order Desk")
                .text("Artifact id", artifact)
                .text("Group id", group).placeholder(ProjectSpec.DEFAULT_GROUP)
                .text("Java package", packageName)
                .custom("Location", location)
                .build();
        return top(projectForm.getComponent());
    }

    private JComponent featureStep() {
        FormBuilder builder = Form.builder().section("Features");
        for (ProjectSpec.Feature feature : ProjectSpec.Feature.values()) {
            builder = builder.checkbox(feature.getTitle(), features.get(feature));
        }
        JPanel apiButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton browse = new JButton(AppAction.of("Choose file...", this::chooseApiFile));
        JButton check = new JButton(AppAction.of("Check", this::checkApi));
        apiButtons.add(browse);
        apiButtons.add(Box.createHorizontalStrut(6));
        apiButtons.add(check);
        restEnabled.onChange(enabled -> {
            browse.setEnabled(enabled);
            check.setEnabled(enabled);
        });
        browse.setEnabled(false);
        check.setEnabled(false);
        Form form = builder
                .section("Spring Boot REST API")
                .checkbox("Generate screens from its OpenAPI document", restEnabled)
                .text("Document", openApiSource).placeholder("http://localhost:8080  or  api-docs.json")
                    .enabledWhen(restEnabled)
                .custom("", apiButtons)
                .custom("", apiStatus)
                .build();
        return top(form.getComponent());
    }

    private JComponent summaryStep() {
        summary.setEditable(false);
        summary.setFont(new Font(Font.MONOSPACED, Font.PLAIN, summary.getFont().getSize()));
        summary.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return new JScrollPane(summary);
    }

    // ---- Navigation ----------------------------------------------------------------------------------

    void selectType(AppType appType) {
        type = appType;
        typeCards.forEach((candidate, card) -> card.setSelected(candidate == appType));
    }

    void next() {
        if (busy) {
            return;
        }
        if (result != null) {
            onClose.run();
            return;
        }
        List<String> stepProblems = problemsOf(step);
        if (!stepProblems.isEmpty()) {
            problems.setText(stepProblems.get(0));
            return;
        }
        if (step == STEPS.length - 1) {
            generate();
        } else {
            showStep(step + 1);
        }
    }

    void back() {
        if (!busy && step > 0 && result == null) {
            showStep(step - 1);
        }
    }

    private void showStep(int index) {
        step = index;
        problems.setText(" ");
        title.setText(STEPS[index][0]);
        subtitle.setText(STEPS[index][1]);
        stepNumber.setText("Step " + (index + 1) + " of " + STEPS.length);
        cards.show(steps, String.valueOf(index));
        backAction.setEnabled(index > 0);
        nextAction.putValue(AppAction.NAME, index == STEPS.length - 1 ? "Generate" : "Next");
        if (index == STEPS.length - 1) {
            summary.setText(String.join("\n", reviewLines()));
            summary.setCaretPosition(0);
        }
    }

    private List<String> problemsOf(int index) {
        List<String> found = new ArrayList<>();
        if (index == 1) {
            projectForm.validate();
            found.addAll(spec().validate());
        }
        if (index == 2 && restEnabled.get() && openApiSource.get().trim().isEmpty()) {
            found.add("Enter the URL or file of the OpenAPI document, or untick the REST option.");
        }
        return found;
    }

    // ---- Actions -------------------------------------------------------------------------------------

    /** Loads the OpenAPI document in the background and shows which resources it has. */
    void checkApi() {
        String source = openApiSource.get().trim();
        if (source.isEmpty()) {
            apiStatus.setText("Enter a URL or choose a file first.");
            return;
        }
        apiStatus.setText("Loading...");
        tasks.call(() -> ProjectGenerator.loadApi(source))
                .onSuccess(api -> {
                    checkedApi = api;
                    checkedSource = source;
                    apiStatus.setText("Found " + resourceTitles(api) + " in " + api.getTitle());
                })
                .onError(e -> apiStatus.setText("Could not load the document: " + e.getMessage()))
                .start();
    }

    void generate() {
        ProjectSpec spec = spec();
        busy = true;
        nextAction.setEnabled(false);
        backAction.setEnabled(false);
        problems.setText(" ");
        summary.setText("Generating...");
        tasks.call(() -> new ProjectGenerator().generate(spec, false))
                .onSuccess(generated -> {
                    result = generated;
                    title.setText("Your project is ready");
                    subtitle.setText("Build it with Maven and start developing.");
                    summary.setText(String.join("\n", GeneratorCli.summary(spec, generated)));
                    nextAction.putValue(AppAction.NAME, "Close");
                    openFolderButton.setVisible(Desktop.isDesktopSupported());
                    onGenerated.accept(generated);
                })
                .onError(e -> {
                    summary.setText(String.join("\n", reviewLines()));
                    problems.setText("Could not generate the project: " + e.getMessage());
                    backAction.setEnabled(true);
                })
                .onFinally(() -> {
                    busy = false;
                    nextAction.setEnabled(true);
                })
                .start();
    }

    // ---- State for tests -----------------------------------------------------------------------------

    int getStep() {
        return step;
    }

    String getProblemText() {
        return problems.getText().trim();
    }

    String getSummaryText() {
        return summary.getText();
    }

    String getApiStatus() {
        return apiStatus.getText();
    }

    ProjectGenerator.Result getResult() {
        return result;
    }

    Property<String> nameProperty() {
        return name;
    }

    Property<String> directoryProperty() {
        return directory;
    }

    Property<Boolean> featureProperty(ProjectSpec.Feature feature) {
        return features.get(feature);
    }

    Property<Boolean> restProperty() {
        return restEnabled;
    }

    Property<String> openApiProperty() {
        return openApiSource;
    }

    // ---- Internals -----------------------------------------------------------------------------------

    ProjectSpec spec() {
        ProjectSpec spec = new ProjectSpec().type(type).name(name.get()).artifactId(artifact.get())
                .groupId(group.get()).packageName(packageName.get()).directory(Paths.get(directory.get().trim()));
        features.forEach((feature, enabled) -> spec.feature(feature, enabled.get()));
        if (restEnabled.get()) {
            spec.openApiSource(openApiSource.get());
        }
        return spec;
    }

    private List<String> reviewLines() {
        ProjectSpec spec = spec();
        List<String> lines = new ArrayList<>();
        lines.add("Application type:   " + spec.getType().getTitle());
        lines.add("Name:               " + spec.getName());
        lines.add("Project folder:     " + spec.getProjectDirectory().toAbsolutePath().normalize());
        lines.add("Maven coordinates:  " + spec.getGroupId() + ":" + spec.getArtifactId() + ":1.0.0-SNAPSHOT");
        lines.add("Java package:       " + spec.getPackageName());
        List<String> chosen = new ArrayList<>();
        spec.getFeatures().forEach(feature -> chosen.add(feature.getTitle()));
        lines.add("Features:           " + (chosen.isEmpty() ? "none" : String.join(", ", chosen)));
        OpenApiSpec api = spec.hasOpenApi() && spec.getOpenApiSource().equals(checkedSource) ? checkedApi : null;
        if (spec.hasOpenApi()) {
            lines.add("REST API:           " + (api != null ? api.getTitle() + " (" + resourceTitles(api) + ")"
                    : spec.getOpenApiSource() + " (loaded when you click Generate)"));
        }
        lines.add("");
        lines.add("Files:");
        for (String file : new ProjectGenerator().render(spec, api, "").keySet()) {
            lines.add("  " + file);
        }
        return lines;
    }

    private static String resourceTitles(OpenApiSpec api) {
        List<String> titles = new ArrayList<>();
        for (ApiResource resource : api.getResources()) {
            titles.add(resource.getTitle());
        }
        return titles.isEmpty() ? "no CRUD resources" : String.join(", ", titles);
    }

    private void updatePlaceholders() {
        ProjectSpec derived = new ProjectSpec().name(name.get()).groupId(group.get()).artifactId(artifact.get());
        projectForm.component("Artifact id").putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                ProjectSpec.deriveArtifactId(name.get()));
        projectForm.component("Java package").putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                derived.getPackageName());
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser(directory.get());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            directory.set(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseApiFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            openApiSource.set(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void openFolder() {
        try {
            Desktop.getDesktop().open(result.getDirectory().toFile());
        } catch (IOException | UnsupportedOperationException e) {
            problems.setText("Could not open the folder: " + e.getMessage());
        }
    }

    private static JComponent top(JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    /** A selectable card: preview picture, title and description. */
    private final class TypeCard extends JPanel {
        private final AppType cardType;

        TypeCard(AppType cardType) {
            super(new BorderLayout(10, 0));
            this.cardType = cardType;
            JLabel cardTitle = new JLabel(cardType.getTitle());
            cardTitle.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4");
            JTextArea description = new JTextArea(cardType.getDescription());
            description.setLineWrap(true);
            description.setWrapStyleWord(true);
            description.setEditable(false);
            description.setFocusable(false);
            description.setOpaque(false);
            description.setFont(UIManager.getFont("Label.font"));
            JPanel text = new JPanel(new BorderLayout(0, 4));
            text.setOpaque(false);
            text.add(cardTitle, BorderLayout.NORTH);
            text.add(description, BorderLayout.CENTER);
            add(new TypePreview(cardType), BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter select = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectType(TypeCard.this.cardType);
                }
            };
            addMouseListener(select);
            description.addMouseListener(select);
        }

        void setSelected(boolean selected) {
            Color accent = UIManager.getColor("Component.accentColor");
            Color line = UIManager.getColor("Component.borderColor");
            Color color = selected ? (accent != null ? accent : Color.BLUE) : (line != null ? line : Color.GRAY);
            int width = selected ? 2 : 1;
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color, width),
                    BorderFactory.createEmptyBorder(12 - width, 12 - width, 12 - width, 12 - width)));
        }
    }
}
