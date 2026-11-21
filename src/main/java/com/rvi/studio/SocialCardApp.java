package com.rvi.studio;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class SocialCardApp extends Application {
    private StudioController controller;

    @Override
    public void start(Stage stage) throws Exception {
        var loader = new FXMLLoader(SocialCardApp.class.getResource("studio.fxml"));
        var scene = new Scene(loader.load(), 1040, 690);
        controller = loader.getController();
        stage.setTitle("SocialCard Studio");
        stage.setMinWidth(840);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (controller.isBusy()) {
                event.consume();
            }
        });
        stage.show();
        controller.refreshReaders();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
