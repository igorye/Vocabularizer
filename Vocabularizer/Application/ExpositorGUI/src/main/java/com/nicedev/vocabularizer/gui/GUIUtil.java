package com.nicedev.vocabularizer.gui;

import com.sun.javafx.scene.control.skin.ContextMenuContent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

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

	//stackoverflow-impl
	public static PopupWindow updateContextMenu(String arg,
	                                            BiConsumer<ContextMenuContent, Optional<String>> menuItemsAppender) {
		if (arg.isEmpty()) return null;
		@SuppressWarnings("deprecation")    final Iterator<Window> windows = Window.impl_getWindows();
		while (windows.hasNext()) {
			final Window window = windows.next();
			if (window instanceof ContextMenu) {
				if (window.getScene() != null && window.getScene().getRoot() != null) {
					Parent root = window.getScene().getRoot();
					// access to context menu content
					if (root.getChildrenUnmodifiable().size() > 0) {
						Node popup = root.getChildrenUnmodifiable().get(0);
						if (popup.lookup(".context-menu") != null) {
							Node bridge = popup.lookup(".context-menu");
							ContextMenuContent cmc = (ContextMenuContent) ((Parent) bridge).getChildrenUnmodifiable().get(0);
							addMenuItem("", null, cmc);
							menuItemsAppender.accept(cmc, Optional.of(arg));
							return (PopupWindow) window;
						}
					}
				}
				return null;
			}
		}
		return null;
	}

	public static void addMenuItem(MenuItem menuItem, Object menu) {
		switch (menu.getClass().getSimpleName()) {
			case "Menu":
				((Menu) menu).getItems().add(menuItem);
				break;
			case "ContextMenu":
				((ContextMenu) menu).getItems().add(menuItem);
				break;
			case "ContextMenuContent":
				ContextMenuContent cmc = (ContextMenuContent) menu;
				cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(menuItem));
				if (menuItem instanceof Menu) {
					//update submenu's showing property listeners
					try {
						Class[] argTypes = { List.class, boolean.class };
						Method method = menu.getClass().getDeclaredMethod("updateMenuShowingListeners", argTypes);
						method.setAccessible(true);
						method.invoke(cmc, Collections.singletonList(menuItem), true);
//						Optional<EventHandler<ActionEvent>> optOnActionHndlr = Optional.ofNullable(menuItem.getOnAction());
						menuItem.setOnAction(event -> {
//							optOnActionHndlr.ifPresent(actionEventEventHandler -> actionEventEventHandler.handle(event));
							((ContextMenu) cmc.getStyleableParent()).hide();
						});
					} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
						e.printStackTrace();
					}
				}
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
		Optional.ofNullable(eventHandler).ifPresent(menuItem::setOnAction);
		addMenuItem(menuItem, contextMenu);
	}

}
