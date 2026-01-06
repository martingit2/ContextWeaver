package com.contextweaver.app.controller;

import com.contextweaver.app.model.FileNode;
import com.contextweaver.app.view.MainView;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class MainController {

    private final MainView view;
    private final Stage stage;
    private final Label selectedPathLabel;
    private Path currentRootPath;

    // Holder på den komplette trestrukturen (etter filtrering)
    private CheckBoxTreeItem<FileNode> masterTreeRoot;

    // Holder på hvilke filer som er valgt på tvers av filtreringsmoduser
    private final Set<Path> persistentSelections = new HashSet<>();

    // Filtreringsmodus
    private enum FilterMode {
        SMART,      // Skjuler cache/build/IDE-mapper osv.
        ALL_FILES   // Viser alle mapper/filer (bortsett fra binært/media/.env/lockfiles)
    }

    private FilterMode currentFilterMode = FilterMode.SMART;

    /**
     * Mapper / filer som skjules i SMART-modus.
     * Typisk: build-output, cache, IDE, verktøy-mapper osv.
     */
    private static final List<String> DEFAULT_EXCLUDED_ITEMS = Arrays.asList(
            // VCS / IDE / verktøy
            ".git", ".svn", ".hg",
            ".idea", ".vscode", ".fleet", ".settings",
            ".gradle", ".terraform", ".dart_tool",

            // Java / JVM / build
            "target", "build", "out", "classes", ".scannerwork",

            // Node / frontend / React / Next.js / diverse JS-rammeverk
            "node_modules", ".next", ".turbo", ".vercel",
            ".parcel-cache", ".yarn", ".pnpm-store", ".cache",
            ".nuxt", ".svelte-kit",
            "dist", "coverage", "storybook-static",

            // Python / backend
            "__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache", ".tox", ".eggs",
            "venv", ".venv", "env", ".venv.bak", ".conda",
            "migrations", "alembic",

            // Flutter / mobil
            ".dart_tool", "build"
    );

    /**
     * Vanlige kode-, konfig- og prosjektfiler vi typisk vil ha med i en AI-kontekst.
     * Brukes av "Velg vanlige kodefiler"-preset.
     */
    private static final List<String> COMMON_CODE_EXTENSIONS = Arrays.asList(
            // JVM / Java / Kotlin / Android
            ".java", ".kt", ".kts",
            ".groovy", ".gradle", ".gradle.kts",
            ".properties",
            "pom.xml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts",
            ".xml", ".yml", ".yaml",

            // JavaScript / TypeScript / React / Next.js / frontend
            ".js", ".jsx", ".ts", ".tsx",
            ".mjs", ".cjs",
            ".json",
            ".html", ".htm",
            ".css", ".scss", ".sass", ".less",

            // Flutter / Dart
            ".dart",
            "pubspec.yaml", "analysis_options.yaml",

            // Python
            ".py", ".pyw",
            ".toml", ".ini", ".cfg",
            ".yaml", ".yml",

            // C# / .NET
            ".cs", ".fs", ".vb",

            // C / C++ / Rust / Go
            ".c", ".h", ".hpp", ".hh", ".cpp", ".cc", ".cxx",
            ".rs", ".go",

            // PHP / Ruby
            ".php", ".phtml",
            ".rb", ".rake",

            // Swift / Obj-C
            ".swift", ".m", ".mm",

            // SQL / databasedefinisjoner
            ".sql",

            // Infra / devops / scripts
            "Dockerfile",
            "docker-compose.yml", "docker-compose.yaml",
            ".sh", ".bash", ".zsh", ".ps1", ".bat",
            "Makefile",

            // Dokumentasjon / meta
            ".md", ".markdown", ".txt", ".adoc", ".rst",

            // Konfig-eksempler
            ".env.example", ".env.template"
    );

    /**
     * Filtyper vi aldri vil ha med (binært, media, store artefakter, runtime-filer osv.).
     * Gjelder i både SMART og ALL_FILES.
     */
    private static final List<String> ALWAYS_EXCLUDED_FILE_EXTENSIONS = Arrays.asList(
            // Compiled / bytecode
            ".class", ".pyc", ".pyo", ".o", ".obj",

            // Arkiver / pakker / artefakter
            ".zip", ".tar", ".gz", ".tgz", ".rar", ".7z",
            ".jar", ".war", ".ear",
            ".apk", ".aab", ".ipa",

            // Executables / libs
            ".exe", ".dll", ".so", ".dylib", ".bin",

            // Bilder / media
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg", ".ico",
            ".mp3", ".wav", ".ogg", ".flac",
            ".mp4", ".mov", ".avi", ".mkv", ".webm",

            // Fonts
            ".ttf", ".otf", ".eot", ".woff", ".woff2",

            // Databaser / større datafiler
            ".db", ".sqlite", ".sqlite3",

            // Logg- og runtime-filer
            ".log"
    );

    /**
     * Filnavn vi eksplisitt aldri vil ha med (lockfiles, hemmelige .env-filer etc.).
     * Gjelder i både SMART og ALL_FILES.
     */
    private static final List<String> ALWAYS_EXCLUDED_FILE_NAMES = Arrays.asList(
            // JS / package managers
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",

            // Øvrige lockfiles
            "composer.lock",
            "Cargo.lock",
            "poetry.lock",
            "Pipfile.lock",

            // Miljøfiler (inneholder ofte hemmeligheter)
            ".env",
            ".env.local",
            ".env.development",
            ".env.production",
            ".env.test"
    );

    public MainController(MainView view, Stage stage, Label selectedPathLabel) {
        this.view = view;
        this.stage = stage;
        this.selectedPathLabel = selectedPathLabel;
        attachEventHandlers();
    }

    private void attachEventHandlers() {
        view.getSelectDirButton().setOnAction(e -> selectDirectory());
        view.getGenerateButton().setOnAction(e -> generateFile());
        view.getPresetCodeButton().setOnAction(e -> selectPreset(COMMON_CODE_EXTENSIONS));
        view.getDeselectAllButton().setOnAction(e -> deselectAll());

        // Filtreringsmodus endret
        view.getFilterModeComboBox().getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            int idx = newVal.intValue();
            currentFilterMode = (idx == 0) ? FilterMode.SMART : FilterMode.ALL_FILES;

            // Hvis vi allerede har en mappe lastet, bygg treet på nytt med ny filtrering
            if (currentRootPath != null) {
                loadDirectory(currentRootPath);
            }
        });

        // Vis/skjul mapper (flat vs hierarkisk visning)
        view.getToggleFoldersButton().setOnAction(e -> {
            updateTreeViewVisibility();
            if (view.getToggleFoldersButton().isSelected()) {
                view.getToggleFoldersButton().setText("Vis mapper");
            } else {
                view.getToggleFoldersButton().setText("Skjul mapper");
            }
        });

        // Drag-and-drop av mappe rett inn i appen
        view.getRoot().setOnDragOver(event -> {
            if (event.getGestureSource() != view.getRoot() && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        view.getRoot().setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File file = db.getFiles().get(0);
                if (file.isDirectory()) {
                    loadDirectory(file.toPath());
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void selectDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Velg Prosjektmappe");
        File selectedDir = chooser.showDialog(stage);
        if (selectedDir != null) {
            loadDirectory(selectedDir.toPath());
        }
    }

    private void addSelectionListenerToAll(TreeItem<FileNode> item) {
        if (item instanceof CheckBoxTreeItem) {
            ((CheckBoxTreeItem<FileNode>) item).selectedProperty().addListener((obs, oldVal, newVal) -> updateSummary());
        }
        for (TreeItem<FileNode> child : item.getChildren()) {
            addSelectionListenerToAll(child);
        }
    }

    /**
     * Sentralt filter: bestemmer om en path skal hoppes over (ikke være med i treet).
     * - ALLTID ekskluderer binære/media/.env/lockfiles.
     * - I SMART-modus ekskluderer vi i tillegg DEFAULT_EXCLUDED_ITEMS (cache/build/IDE osv.).
     */
    private boolean shouldSkipPath(Path path) {
        String name = path.getFileName().toString();

        // Hvis det er en fil, sjekk navn + extension (gjelder i begge moduser)
        if (Files.isRegularFile(path)) {
            String lowerName = name.toLowerCase(Locale.ROOT);

            // Spesifikke filnavn (lockfiles, .env osv.)
            if (ALWAYS_EXCLUDED_FILE_NAMES.contains(lowerName)) {
                return true;
            }

            // Filendelser vi aldri vil ha med
            for (String ext : ALWAYS_EXCLUDED_FILE_EXTENSIONS) {
                if (lowerName.endsWith(ext)) {
                    return true;
                }
            }
        }

        // SMART-modus: ekskluder kjente støy-mapper / filer
        if (currentFilterMode == FilterMode.SMART) {
            if (DEFAULT_EXCLUDED_ITEMS.contains(name)) {
                return true;
            }
        }

        // ALL_FILES-modus: vi hopper IKKE over DEFAULT_EXCLUDED_ITEMS,
        // men binært/media/.env/lockfiles er allerede filtrert over.
        return false;
    }

    private void loadDirectory(Path rootPath) {
        boolean sameRoot = Objects.equals(this.currentRootPath, rootPath);

        // Hvis vi bytter til en annen mappe, nullstill tidligere valg
        if (!sameRoot) {
            persistentSelections.clear();
        }

        this.currentRootPath = rootPath;
        selectedPathLabel.setText("Laster: " + rootPath);

        // Hvis vi allerede har et tre (samme rot), ta vare på nåværende valg før vi bygger nytt
        if (sameRoot && masterTreeRoot != null) {
            List<Path> selectedNow = new ArrayList<>();
            collectSelected(masterTreeRoot, selectedNow);
            persistentSelections.clear();
            persistentSelections.addAll(selectedNow);
        }

        Task<CheckBoxTreeItem<FileNode>> loadTask = new Task<>() {
            @Override
            protected CheckBoxTreeItem<FileNode> call() {
                updateMessage("Laster filstruktur...");
                return createTreeItem(rootPath);
            }
        };

        loadTask.setOnSucceeded(e -> {
            view.getStatusLabel().textProperty().unbind();

            this.masterTreeRoot = loadTask.getValue();

            // Gjenopprett tidligere valg (hvis noen) før vi justerer visning
            if (!persistentSelections.isEmpty()) {
                restoreSelections(masterTreeRoot);
            }

            updateTreeViewVisibility();
            addSelectionListenerToAll(view.getFileTreeView().getRoot());

            selectedPathLabel.setText("Valgt mappe: " + rootPath);
            view.getStatusLabel().setText("Klar. Velg filer for veving.");
            updateSummary();
        });

        loadTask.setOnFailed(e -> {
            view.getStatusLabel().textProperty().unbind();
            view.getStatusLabel().setText("Feil ved lasting av mappe.");
            new Alert(Alert.AlertType.ERROR, "Kunne ikke lese mappen: " + loadTask.getException().getMessage()).show();
        });

        view.getStatusLabel().textProperty().bind(loadTask.messageProperty());
        new Thread(loadTask).start();
    }

    private void updateTreeViewVisibility() {
        if (masterTreeRoot == null) return;

        boolean hideFolders = view.getToggleFoldersButton().isSelected();
        if (hideFolders) {
            // Flat visning: kun filer, men checkboxene er synket med master-treet
            CheckBoxTreeItem<FileNode> flatRoot = new CheckBoxTreeItem<>(masterTreeRoot.getValue());
            collectFilesRecursively(masterTreeRoot, flatRoot);
            view.getFileTreeView().setRoot(flatRoot);
        } else {
            // Normal hierarkisk visning
            view.getFileTreeView().setRoot(masterTreeRoot);
        }
        // Legg til lyttere på nytt hver gang visningen endres
        addSelectionListenerToAll(view.getFileTreeView().getRoot());
    }

    private void collectFilesRecursively(CheckBoxTreeItem<FileNode> source, CheckBoxTreeItem<FileNode> target) {
        for (TreeItem<FileNode> child : source.getChildren()) {
            if (Files.isRegularFile(child.getValue().getPath())) {
                // Lag en kopi for å unngå problemer med at en node har flere foreldre
                CheckBoxTreeItem<FileNode> copy = new CheckBoxTreeItem<>(child.getValue());

                // Koble checkboxene sammen slik at de alltid er synkronisert
                copy.selectedProperty().bindBidirectional(((CheckBoxTreeItem<FileNode>) child).selectedProperty());

                target.getChildren().add(copy);
            } else if (Files.isDirectory(child.getValue().getPath())) {
                // Fortsett letingen ned i undermapper
                collectFilesRecursively((CheckBoxTreeItem<FileNode>) child, target);
            }
        }
    }

    private CheckBoxTreeItem<FileNode> createTreeItem(Path path) {
        CheckBoxTreeItem<FileNode> item = new CheckBoxTreeItem<>(new FileNode(path));
        item.setExpanded(true);

        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
                stream
                        .filter(p -> !shouldSkipPath(p))
                        .sorted()
                        .map(this::createTreeItem)
                        .forEach(item.getChildren()::add);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return item;
    }

    /**
     * Gjenoppretter valgte filer basert på persistentSelections.
     */
    private void restoreSelections(TreeItem<FileNode> item) {
        if (item instanceof CheckBoxTreeItem) {
            Path path = item.getValue().getPath();
            if (Files.isRegularFile(path) && persistentSelections.contains(path)) {
                ((CheckBoxTreeItem<FileNode>) item).setSelected(true);
            }
        }
        for (TreeItem<FileNode> child : item.getChildren()) {
            restoreSelections(child);
        }
    }

    private void generateFile() {
        if (currentRootPath == null) {
            new Alert(Alert.AlertType.WARNING, "Du må velge en mappe først!").show();
            return;
        }

        List<Path> selectedPaths = new ArrayList<>();
        // Bruk alltid master-treet for å samle inn filer, siden det alltid er komplett.
        collectSelected(this.masterTreeRoot, selectedPaths);

        if (selectedPaths.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Ingen filer er valgt.").show();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lagre Vevd Kontekst");
        chooser.setInitialFileName("woven_context.txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File outputFile = chooser.showSaveDialog(stage);

        if (outputFile == null) return;

        Task<Void> generateTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Vever kontekst...");
                StringBuilder sb = new StringBuilder(
                        "/*\n--- Context woven by ContextWeaver ---\n\n" +
                                "Project: " + currentRootPath.getFileName() + "\n" +
                                "Files included: " + selectedPaths.size() + "\n" +
                                "*/\n\n\n"
                );

                long total = selectedPaths.size();
                long current = 0;

                for (Path path : selectedPaths) {
                    if (isCancelled()) break;
                    updateProgress(++current, total);
                    String relativePath = currentRootPath.relativize(path).toString().replace('\\', '/');
                    sb.append("--- START OF FILE: ").append(relativePath).append(" ---\n\n");
                    try {
                        sb.append(Files.readString(path));
                    } catch (IOException e) {
                        sb.append("!!! ERROR READING FILE: ").append(e.getMessage()).append(" !!!");
                    }
                    sb.append("\n\n--- END OF FILE: ").append(relativePath).append(" ---\n\n\n");
                }

                Files.writeString(outputFile.toPath(), sb.toString());
                return null;
            }
        };

        generateTask.setOnSucceeded(e -> {
            view.getStatusLabel().textProperty().unbind();
            view.getProgressBar().progressProperty().unbind();

            view.getStatusLabel().setText("Kontekst vevd og lagret!");
            view.getProgressBar().setVisible(false);
            new Alert(Alert.AlertType.INFORMATION, "Filen ble lagret!\n" + outputFile.getAbsolutePath()).show();
        });

        generateTask.setOnFailed(e -> {
            view.getStatusLabel().textProperty().unbind();
            view.getProgressBar().progressProperty().unbind();

            view.getStatusLabel().setText("Feil under veving.");
            view.getProgressBar().setVisible(false);
            new Alert(Alert.AlertType.ERROR, "Kunne ikke generere fil: " + generateTask.getException().getMessage()).show();
        });

        view.getStatusLabel().textProperty().bind(generateTask.messageProperty());
        view.getProgressBar().progressProperty().bind(generateTask.progressProperty());
        view.getProgressBar().setVisible(true);
        new Thread(generateTask).start();
    }

    private void collectSelected(TreeItem<FileNode> item, List<Path> selectedPaths) {
        if (item == null || !(item instanceof CheckBoxTreeItem)) return;

        if (((CheckBoxTreeItem<FileNode>) item).isSelected() && Files.isRegularFile(item.getValue().getPath())) {
            selectedPaths.add(item.getValue().getPath());
        }

        for (TreeItem<FileNode> child : item.getChildren()) {
            collectSelected(child, selectedPaths);
        }
    }

    private void selectPreset(List<String> extensions) {
        if (masterTreeRoot == null) return;
        // Bruk alltid master-treet for å sette valg
        setSelection(this.masterTreeRoot, true, extensions);
        updateSummary();
    }

    private void deselectAll() {
        if (masterTreeRoot == null) return;
        // Bruk alltid master-treet for å fjerne valg
        setSelection(this.masterTreeRoot, false, null);
        updateSummary();
    }

    private void setSelection(TreeItem<FileNode> item, boolean selected, List<String> extensions) {
        if (item == null || !(item instanceof CheckBoxTreeItem)) return;

        CheckBoxTreeItem<FileNode> cbItem = (CheckBoxTreeItem<FileNode>) item;
        Path path = cbItem.getValue().getPath();

        if (Files.isRegularFile(path)) {
            boolean shouldSelect = selected;
            if (selected && extensions != null) {
                String fileName = path.getFileName().toString();
                String lowerName = fileName.toLowerCase(Locale.ROOT);
                shouldSelect = extensions.stream()
                        .anyMatch(ext -> lowerName.endsWith(ext.toLowerCase(Locale.ROOT)));
            }
            cbItem.setSelected(shouldSelect);
        } else {
            // Mapper skal aldri være direkte valgt
            cbItem.setSelected(false);
        }

        for (TreeItem<FileNode> child : item.getChildren()) {
            setSelection(child, selected, extensions);
        }
    }

    private void updateSummary() {
        if (masterTreeRoot == null) {
            Platform.runLater(() -> view.getSummaryLabel().setText("0 filer valgt\n0 KB"));
            return;
        }

        List<Path> selectedPaths = new ArrayList<>();
        // Bruk alltid master-treet for oppsummering
        collectSelected(this.masterTreeRoot, selectedPaths);


        persistentSelections.clear();
        persistentSelections.addAll(selectedPaths);

        AtomicLong totalSize = new AtomicLong(0);
        selectedPaths.forEach(p -> {
            try {
                totalSize.addAndGet(Files.size(p));
            } catch (IOException e) {
                // Ignorer feil her
            }
        });

        String summaryText = String.format("%d filer valgt\n%.2f KB", selectedPaths.size(), totalSize.get() / 1024.0);
        Platform.runLater(() -> view.getSummaryLabel().setText(summaryText));
    }
}
