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

    public MainView(Stage primaryStage) {
        root = new BorderPane();
        root.setPadding(new Insets(15));
        root.getStyleClass().add("root-pane");

        fileTreeView = new TreeView<>();
        fileTreeView.setCellFactory(CheckBoxTreeCell.forTreeView());
        root.setCenter(fileTreeView);

        HBox topBar = new HBox(10);
        selectDirButton = new Button("Velg Prosjektmappe...");
        Label selectedPathLabel = new Label("Dra og slipp en mappe her, eller klikk for å velge...");
        selectedPathLabel.getStyleClass().add("path-label");
        HBox.setHgrow(selectedPathLabel, Priority.ALWAYS);
        topBar.getChildren().addAll(selectDirButton, selectedPathLabel);
        root.setTop(topBar);
        BorderPane.setMargin(topBar, new Insets(0, 0, 15, 0));

        VBox rightPanel = new VBox(15);
        rightPanel.setPadding(new Insets(0, 0, 0, 15));
        rightPanel.getStyleClass().add("right-panel");
        rightPanel.setMinWidth(220);

        Label presetsTitle = new Label("Hurtigvalg");
        presetsTitle.getStyleClass().add("panel-title");

        presetCodeButton = new Button("Velg vanlige kodefiler");
        presetCodeButton.setMaxWidth(Double.MAX_VALUE);
        deselectAllButton = new Button("Fjern alle valg");
        deselectAllButton.setMaxWidth(Double.MAX_VALUE);

        // NY KNAPP lagt til i panelet
        toggleFoldersButton = new ToggleButton("Skjul mapper");
        toggleFoldersButton.setMaxWidth(Double.MAX_VALUE);
        toggleFoldersButton.setSelected(false); // Starter med å vise mapper

        Separator separator = new Separator();

        Label summaryTitle = new Label("Sammendrag");
        summaryTitle.getStyleClass().add("panel-title");
        summaryLabel = new Label("0 filer valgt\n0 KB");
        summaryLabel.setWrapText(true);

        rightPanel.getChildren().addAll(presetsTitle, presetCodeButton, deselectAllButton, toggleFoldersButton, separator, summaryTitle, summaryLabel);
        root.setRight(rightPanel);

        VBox bottomBar = new VBox(10);
        generateButton = new Button("Weave Context");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.getStyleClass().add("generate-button");

        HBox statusBox = new HBox(10);
        statusBox.setPadding(new Insets(5, 0, 0, 0));
        statusLabel = new Label("Klar.");
        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        statusBox.getChildren().addAll(statusLabel, progressBar);

        bottomBar.getChildren().addAll(generateButton, statusBox);
        root.setBottom(bottomBar);
        BorderPane.setMargin(bottomBar, new Insets(15, 0, 0, 0));

        controller = new MainController(this, primaryStage, selectedPathLabel);

        Scene scene = new Scene(root, 1100, 800);

        URL cssUrl = getClass().getResource("/com/contextweaver/app/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            System.err.println("ADVARSEL: Kunne ikke finne style.css. Sjekk at filen ligger i resources/com/contextweaver/app/");
        }

        primaryStage.setTitle("ContextWeaver");
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
    // NY GET-METODE:
    public ToggleButton getToggleFoldersButton() { return toggleFoldersButton; }
}