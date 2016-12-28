package com.nicedev.vocabularizer.gui;

import javafx.scene.control.Tab;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class TranslationTab extends WebViewTab {
	public TranslationTab(String title, String translationURL) {
		super(title, null);
		getEngine().load(translationURL);
		setStyle("-fx-font-style: italic;");
	}
	
	public static Predicate<Tab> sameTab(String url) {
		return tab -> tab instanceof TranslationTab && ((WebViewTab) tab).getEngine().getLocation().equals(url);
	}
	
	public static BiPredicate<Tab, String> sameTab() {
		return (tab, url) -> tab instanceof TranslationTab && ((WebViewTab) tab).getEngine().getLocation().equals(url);
	}
}
