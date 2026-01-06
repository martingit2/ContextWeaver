package com.contextweaver.app.view;

import com.contextweaver.app.controller.MainController;
import com.contextweaver.app.model.FileNode;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;

public class MainView {

    private final BorderPane root;
    private final MainController controller;

    // UI-komponenter
    private final TreeView<FileNode> fileTreeView;
    private final Button selectDirButton;
    private final Button generateButton;
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final Label summaryLabel;
    private final Button presetCodeButton;
    private final Button deselectAllButton;
    private final ToggleButton toggleFoldersButton;
    private final ComboBox<String> filterModeComboBox;

    public MainView(Stage primaryStage) {
        root = new BorderPane();
        root.setPadding(new Insets(15));
        root.getStyleClass().add("root-pane");

        // --- SENTER: Filtreet ---
        fileTreeView = new TreeView<>();
        fileTreeView.setShowRoot(true);
        fileTreeView.setCellFactory(CheckBoxTreeCell.forTreeView());
        fileTreeView.getStyleClass().add("file-tree");
        root.setCenter(fileTreeView);

        // --- TOPP: Valg av prosjektmappe + info ---
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(0, 0, 0, 0));

        selectDirButton = new Button("Velg Prosjektmappe…");
        selectDirButton.setMinWidth(180);

        Label selectedPathLabel = new Label("Dra og slipp en prosjektmappe her, eller klikk på knappen for å velge.");
        selectedPathLabel.getStyleClass().add("path-label");
        selectedPathLabel.setWrapText(true);
        HBox.setHgrow(selectedPathLabel, Priority.ALWAYS);

        Tooltip selectDirTooltip = new Tooltip("Velg rotmappen til prosjektet (f.eks. der .git-mappen ligger).");
        selectDirButton.setTooltip(selectDirTooltip);

        topBar.getChildren().addAll(selectDirButton, selectedPathLabel);
        root.setTop(topBar);
        BorderPane.setMargin(topBar, new Insets(0, 0, 15, 0));

        // --- HØYRE: Filtrering, presets, visning og sammendrag ---
        VBox rightPanel = new VBox(15);
        rightPanel.setPadding(new Insets(0, 0, 0, 15));
        rightPanel.getStyleClass().add("right-panel");
        rightPanel.setMinWidth(260);

        // Filtrering-seksjon
        Label filterTitle = new Label("Filtrering");
        filterTitle.getStyleClass().add("panel-title");

        Label filterDescription = new Label(
                "Velg hvordan filtreet skal bygges. Smart filtrering skjuler cache-, build- og " +
                        "mellomfiler. 'Vis alle' viser også migrations, cache osv."
        );
        filterDescription.setWrapText(true);
        filterDescription.getStyleClass().add("panel-description");

        filterModeComboBox = new ComboBox<>();
        filterModeComboBox.getItems().addAll(
                "Smart filtrering (anbefalt)",
                "Vis alle filer (inkl. cache/migrations)"
        );
        filterModeComboBox.getSelectionModel().selectFirst();
        filterModeComboBox.setMaxWidth(Double.MAX_VALUE);

        Tooltip filterTooltip = new Tooltip(
                "Smart filtrering: skjuler støy som node_modules, .next, __pycache__, venv, build osv.\n" +
                        "Vis alle filer: tar med nesten alt, slik at du får full oversikt.\n" +
                        "Binærfiler, media og .env-filer holdes uansett utenfor."
        );
        filterModeComboBox.setTooltip(filterTooltip);

        Separator filterSeparator = new Separator();

        // Presets-seksjon
        Label presetsTitle = new Label("Hurtigvalg");
        presetsTitle.getStyleClass().add("panel-title");

        Label presetsDescription = new Label(
                "Bruk hurtigvalg for å automatisk merke typiske kode- og konfigfiler " +
                        "på tvers av språk (Java, Kotlin, Python, React/Next.js, Flutter, osv.)."
        );
        presetsDescription.setWrapText(true);
        presetsDescription.getStyleClass().add("panel-description");

        presetCodeButton = new Button("Velg vanlige kodefiler");
        presetCodeButton.setMaxWidth(Double.MAX_VALUE);
        Tooltip presetTooltip = new Tooltip(
                "Marker alle filer som ser ut som kode, konfig, infrastruktur eller dokumentasjon.\n" +
                        "Støy som cache, build-output, binærfiler og media blir hoppet over."
        );
        presetCodeButton.setTooltip(presetTooltip);

        deselectAllButton = new Button("Fjern alle valg");
        deselectAllButton.setMaxWidth(Double.MAX_VALUE);
        Tooltip deselectTooltip = new Tooltip("Fjern alle nåværende filvalg.");
        deselectAllButton.setTooltip(deselectTooltip);

        toggleFoldersButton = new ToggleButton("Skjul mapper");
        toggleFoldersButton.setMaxWidth(Double.MAX_VALUE);
        toggleFoldersButton.setSelected(false); // Starter med å vise mapper
        Tooltip toggleFoldersTooltip = new Tooltip(
                "Skjul mappestrukturen og vis kun en flat liste med filer.\n" +
                        "Nyttig i store prosjekter for å få rask oversikt."
        );
        toggleFoldersButton.setTooltip(toggleFoldersTooltip);

        Separator presetsSeparator = new Separator();

        // Sammendrag-seksjon
        Label summaryTitle = new Label("Sammendrag");
        summaryTitle.getStyleClass().add("panel-title");

        summaryLabel = new Label("0 filer valgt\n0 KB");
        summaryLabel.setWrapText(true);

        Label summaryHint = new Label(
                "Tips: Hold antall filer moderat for å unngå å fylle opp AI-modellens token-grense. " +
                        "Bruk filtrering og hurtigvalg for å fokusere på relevante filer."
        );
        summaryHint.setWrapText(true);
        summaryHint.getStyleClass().add("panel-hint");

        rightPanel.getChildren().addAll(
                filterTitle,
                filterDescription,
                filterModeComboBox,
                filterSeparator,
                presetsTitle,
                presetsDescription,
                presetCodeButton,
                deselectAllButton,
                toggleFoldersButton,
                presetsSeparator,
                summaryTitle,
                summaryLabel,
                summaryHint
        );
        root.setRight(rightPanel);

        // --- BUNN: Generer-knapp + status ---
        VBox bottomBar = new VBox(10);

        generateButton = new Button("Weave Context");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.getStyleClass().add("generate-button");
        Tooltip generateTooltip = new Tooltip(
                "Generer én samlet .txt-fil med innholdet i alle valgte filer.\n" +
                        "Klar til å limes rett inn i ChatGPT, Claude, Gemini, osv."
        );
        generateButton.setTooltip(generateTooltip);

        HBox statusBox = new HBox(10);
        statusBox.setPadding(new Insets(5, 0, 0, 0));
        statusLabel = new Label("Klar.");
        statusLabel.setWrapText(true);

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(250);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        statusBox.getChildren().addAll(statusLabel, progressBar);

        bottomBar.getChildren().addAll(generateButton, statusBox);
        root.setBottom(bottomBar);
        BorderPane.setMargin(bottomBar, new Insets(15, 0, 0, 0));

        // --- Controller-kobling ---
        controller = new MainController(this, primaryStage, selectedPathLabel);

        // --- Scene + CSS ---
        Scene scene = new Scene(root, 1150, 820);

        URL cssUrl = getClass().getResource("/com/contextweaver/app/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("ADVARSEL: Kunne ikke finne style.css. " +
                    "Sjekk at filen ligger i resources/com/contextweaver/app/");
        }

        primaryStage.setTitle("ContextWeaver – Project Context Builder");
        primaryStage.setScene(scene);
    }

    // Get-metoder for controlleren
    public TreeView<FileNode> getFileTreeView() { return fileTreeView; }
    public Button getSelectDirButton() { return selectDirButton; }
    public Button getGenerateButton() { return generateButton; }
    public Button getPresetCodeButton() { return presetCodeButton; }
    public Button getDeselectAllButton() { return deselectAllButton; }
    public Label getStatusLabel() { return statusLabel; }
    public ProgressBar getProgressBar() { return progressBar; }
    public Label getSummaryLabel() { return summaryLabel; }
    public BorderPane getRoot() { return root; }
    public ToggleButton getToggleFoldersButton() { return toggleFoldersButton; }
    public ComboBox<String> getFilterModeComboBox() { return filterModeComboBox; }
}
