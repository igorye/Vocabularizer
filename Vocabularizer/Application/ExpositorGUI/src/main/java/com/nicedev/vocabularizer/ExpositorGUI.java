package com.nicedev.vocabularizer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class ExpositorGUI extends Application {

	private GUIController controller;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		try {
			URL resource = getClass().getProtectionDomain().getClassLoader().getResource("tableView.fxml");
			FXMLLoader fxmlLoader = new FXMLLoader(resource);
			Parent root = fxmlLoader.load();
			Scene scene = new Scene(root);
			controller = fxmlLoader.getController();
			controller.setMainScene(scene);
			primaryStage.setTitle("Vocabularizer");
			primaryStage.setScene(scene);
			primaryStage.setOnShown(controller::onStageShown);
			primaryStage.show();
		} catch (Exception e) {
			GUIController.LOGGER.error("Exception has occurred:", e);
		}

	}

	@Override
	public void stop() throws Exception {
		super.stop();
		controller.stop();
	}


}