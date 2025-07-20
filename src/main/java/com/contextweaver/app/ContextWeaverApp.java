package com.contextweaver.app;

import com.contextweaver.app.view.MainView;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Hovedinngangspunkt for ContextWeaver-applikasjonen.
 * Setter opp og viser hovedvinduet (MainView).
 */
public class ContextWeaverApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Oppretter MainView, som igjen oppretter MainController og bygger hele GUI-et.
        new MainView(primaryStage);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}