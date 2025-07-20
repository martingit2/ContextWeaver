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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class MainController {

    private final MainView view;
    private final Stage stage;
    private final Label selectedPathLabel;
    private Path currentRootPath;

    // For å holde på den komplette, ufiltrerte trestrukturen
    private CheckBoxTreeItem<FileNode> masterTreeRoot;

    private static final List<String> DEFAULT_EXCLUDED_ITEMS = Arrays.asList(
            ".git", ".idea", "target", "build", "out", "node_modules", ".vscode", ".DS_Store", "dist"
    );
    private static final List<String> COMMON_CODE_EXTENSIONS = Arrays.asList(
            ".java", ".xml", ".properties", ".gradle", "pom.xml", ".yml",
            ".js", ".ts", ".jsx", ".tsx", ".html", ".css", ".scss", ".json",
            ".py",
            ".md", "Dockerfile", ".sh"
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

        // Listener for vis/skjul-knappen
        view.getToggleFoldersButton().setOnAction(e -> {
            updateTreeViewVisibility();
            if (view.getToggleFoldersButton().isSelected()) {
                view.getToggleFoldersButton().setText("Vis mapper");
            } else {
                view.getToggleFoldersButton().setText("Skjul mapper");
            }
        });

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

    private void loadDirectory(Path rootPath) {
        this.currentRootPath = rootPath;
        selectedPathLabel.setText("Laster: " + rootPath);

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
            CheckBoxTreeItem<FileNode> flatRoot = new CheckBoxTreeItem<>(masterTreeRoot.getValue());
            collectFilesRecursively(masterTreeRoot, flatRoot);
            view.getFileTreeView().setRoot(flatRoot);
        } else {
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

                // --- KORRIGERT LINJE ---
                // Kobler de to checkboxene sammen slik at de alltid er synkronisert.
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
                        .filter(p -> !DEFAULT_EXCLUDED_ITEMS.contains(p.getFileName().toString()))
                        .sorted()
                        .map(this::createTreeItem)
                        .forEach(item.getChildren()::add);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return item;
    }

    private void generateFile() {
        if (currentRootPath == null) {
            new Alert(Alert.AlertType.WARNING, "Du må velge en mappe først!").show();
            return;
        }

        List<Path> selectedPaths = new ArrayList<>();
        // VIKTIG: Bruk alltid master-treet for å samle inn filer, siden det alltid er komplett.
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
                StringBuilder sb = new StringBuilder("/*\n--- Context woven by ContextWeaver ---\n\nProject: " + currentRootPath.getFileName() + "\nFiles included: " + selectedPaths.size() + "\n*/\n\n\n");

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

        if (Files.isRegularFile(cbItem.getValue().getPath())) {
            boolean shouldSelect = selected;
            if (selected && extensions != null) {
                String fileName = cbItem.getValue().getPath().getFileName().toString();
                shouldSelect = extensions.stream().anyMatch(fileName::endsWith);
            }
            cbItem.setSelected(shouldSelect);
        } else {
            cbItem.setSelected(false); // Mapper skal aldri velges
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

        AtomicLong totalSize = new AtomicLong(0);
        selectedPaths.forEach(p -> {
            try {
                totalSize.addAndGet(Files.size(p));
            } catch (IOException e) { /* Ignorer feil her */ }
        });

        String summaryText = String.format("%d filer valgt\n%.2f KB", selectedPaths.size(), totalSize.get() / 1024.0);
        Platform.runLater(() -> view.getSummaryLabel().setText(summaryText));
    }
}