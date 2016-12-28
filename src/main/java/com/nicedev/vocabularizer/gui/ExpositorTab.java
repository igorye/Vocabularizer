package com.nicedev.vocabularizer.gui;

import javafx.scene.control.Tab;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class ExpositorTab extends WebViewTab {
	
	public ExpositorTab(String title, Object userData) {
		this(title, "", userData);
	}
	
	public ExpositorTab(String title, String htmlContent, Object userData) {
		super(title, userData);
		setStyle("-fx-font-weight: bold;");
		getEngine().loadContent(htmlContent);
	}
	
	public static Predicate<Tab> sameTab(String title) {
		return tab -> tab instanceof ExpositorTab && tab.getTooltip().getText().equals(title);
	}
	
	public static BiPredicate<Tab, String> sameTab() {
		return (tab, title) -> tab instanceof ExpositorTab && tab.getTooltip().getText().equals(title);
	}
	
}
