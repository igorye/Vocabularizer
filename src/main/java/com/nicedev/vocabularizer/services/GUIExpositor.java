package com.nicedev.vocabularizer.services;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class GUIExpositor extends Application {

    private GUIController controller;

    @Override
    public void start(Stage primaryStage) throws IOException {
        URL resource = getClass().getProtectionDomain().getClassLoader().getResource("htmlView.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(resource);
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root);
        controller = fxmlLoader.getController();
        controller.setMainScene(scene);
        primaryStage.setTitle("Vocabularizer");
        primaryStage.setScene(scene);
        primaryStage.setOnShown(event -> controller.onLoad());
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        controller.stop();
    }


}