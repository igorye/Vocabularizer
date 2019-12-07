package com.nicedev.vocabularizer.gui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Function;

public class GUIUtil {
	//stackoverflow-impl
	public static void hackAlert(Alert confirm) {
		DialogPane dialogPane = confirm.getDialogPane();
		((Button) dialogPane.lookupButton(ButtonType.YES)).setDefaultButton(false);
		EventHandler<KeyEvent> fireOnEnter = event -> {
			if (KeyCode.ENTER.equals(event.getCode()) && event.getTarget() instanceof Button)
				((Button) event.getTarget()).fire();
		};
		dialogPane.getButtonTypes().stream()
				  .map(dialogPane::lookupButton)
				  .forEach(button -> button.addEventHandler(KeyEvent.KEY_RELEASED, fireOnEnter));
	}

	public static void addMenuItem(MenuItem menuItem, Object menu) {
		switch (menu.getClass().getSimpleName()) {
			case "Menu":
				((Menu) menu).getItems().add(menuItem);
				break;
			case "ContextMenu":
				((ContextMenu) menu).getItems().add(menuItem);
				break;
		}
	}

	public static void addMenuItem(String title, EventHandler<ActionEvent> eventHandler, Object contextMenu) {
		MenuItem menuItem;
		if (title.isEmpty()) {
			menuItem = new SeparatorMenuItem();
			menuItem.setDisable(true);
		} else {
			menuItem = new MenuItem(title);
		}
		Function<EventHandler<ActionEvent>, EventHandler<ActionEvent>> handleEventAndHidePopup =
				handler -> event -> {
					handler.handle(event);
					menuItem.getParentPopup().hide();
				};
		if (eventHandler != null) {
			menuItem.setOnAction(handleEventAndHidePopup.apply(eventHandler));
		}
		addMenuItem(menuItem, contextMenu);
	}

}
