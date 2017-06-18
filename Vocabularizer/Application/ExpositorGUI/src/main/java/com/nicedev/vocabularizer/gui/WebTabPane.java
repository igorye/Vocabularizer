package com.nicedev.vocabularizer.gui;

import javafx.application.Platform;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class WebTabPane extends TabPane {

	private final SingleSelectionModel<Tab> tabSingleSelectionModel;
	private int activeTabIndex;

	public WebTabPane() {
		super();
		tabSingleSelectionModel = selectionModelProperty().get();
	}

	public WebTabPane(Tab... tabs) {
		super(tabs);
		tabSingleSelectionModel = selectionModelProperty().get();
	}

	public int getSelectedTabIndex() {
//		Node focusOwner = getScene().getFocusOwner();
//		should?? have focus to get actual selected tab index
//		requestFocus();
//		int selectedIndex = tabSingleSelectionModel.getSelectedIndex();
//		focusOwner.requestFocus();
		return tabSingleSelectionModel.getSelectedIndex();
	}

	public Tab getActiveTab() {
		return tabSingleSelectionModel.getSelectedItem();
	}

	public Optional<Tab> findFirst(Predicate<Tab> predicate) {
		return getTabs().stream().skip(Math.max(getSelectedTabIndex() - 1, 0)).filter(predicate).findFirst();
	}

	public void insertTab(Tab newTab, boolean setActive) {
		Platform.runLater(() -> {
			updateTabIndices();
			getTabs().add(activeTabIndex + 1, newTab);
			if (setActive) tabSingleSelectionModel.select(activeTabIndex + 1);
		});
	}

	private void addTab(Tab newTab, boolean setActive) {
		Platform.runLater(() -> {
			updateTabIndices();
			getTabs().add(newTab);
			if (setActive) tabSingleSelectionModel.select(activeTabIndex + 1);
		});
	}

	public void removeActiveTab() {
		updateTabIndices();
		if (!tabSingleSelectionModel.isSelected(0)) {
			getTabs().remove(getActiveTab());
		}
		tabSingleSelectionModel.select(Math.min(activeTabIndex, getTabs().size() - 1));
	}

	public void removeTab(String title) {
		getTabs().removeIf(ExpositorTab.sameTab(title));
	}

	private void updateTabIndices() {
		activeTabIndex = getSelectedTabIndex();
	}

	public Optional<WebView> getActiveWebView() {
		return Optional.of(getActiveTab().getContent())
				       .filter(node -> node instanceof WebView)
				       .map(node -> (WebView) node);
	}

	public Optional<WebEngine> getActiveEngine() {
		return getActiveWebView().map(WebView::getEngine);
	}

	public String getActiveViewSelection() {
		return getActiveEngine()
				       .map(engine -> ((String) engine.executeScript("window.getSelection().toString()")).trim())
				       .orElse("");
	}

	public void selectTab(Tab tab) {
		tabSingleSelectionModel.select(tab);
	}

	public boolean trySelectTab(int startIndex, String... tabTitle) {
		if (tabTitle.length == 0) return false;
		Optional<Tab> tabToSelect = stream(tabTitle)
				                            .flatMap(s -> getTabs().stream().skip(startIndex)
						                                          .filter(tab -> tab.getText().equals(s)))
				                            .findFirst();
		if (tabToSelect.isPresent()) {
			tabSingleSelectionModel.select(tabToSelect.get());
			return true;
		}
		return false;
	}
	
	/*public boolean trySelectTab(int startIndex, Predicate<Tab> tabPredicate) {
		int selected = tryWithTab(startIndex, tabPredicate, this::selectTab, false);
		return selected == 1;
	}*/

	public boolean trySelectTab(Predicate<Tab> tabPredicate) {
		int selected = tryWithTab(1, tabPredicate, this::selectTab, false);
		return selected == 1;
	}
	
	/*public int tryWithTab(Predicate<Tab> tabPredicate, Consumer<Tab> perform) {
		return tryWithTab(0, tabPredicate, perform, true);
	}*/

	private int tryWithTab(int startIndex, Predicate<Tab> tabPredicate, Consumer<Tab> perform, boolean forEach) {
		Collection<Tab> tabs = getTabs().stream()
				                       .skip(startIndex)
				                       .filter(tabPredicate)
				                       .limit(forEach ? Long.MAX_VALUE : 1)
				                       .collect(toList());
		tabs.forEach(perform);
		return tabs.size();
	}

	public void insertIfAbsent(List<String> tabTitles,
	                           BiFunction<String, String[], WebViewTab> tabSupplier,
	                           Class<? extends WebViewTab> suppliedClass) {
		List<String> presentTabs = tabTitles.stream()
				                           .flatMap(s -> getTabs().stream()
						                                         .filter(suppliedClass::isInstance)
						                                         .map(tab -> tab.getTooltip().getText()))
				                           .collect(toList());
		tabTitles.stream()
				.filter(Objects::nonNull)
				.filter(s -> !presentTabs.contains(s))
				.forEach(s -> insertTab(tabSupplier.apply(s, new String[0]), tabTitles.size() == 1));
	}

	public void addIfAbsent(List<String> tabArgs,
	                        Function<String, WebViewTab> tabSupplier, Class<? extends WebViewTab> suppliedClass) {
		List<String> presentTabs = tabArgs.stream()
				                           .flatMap(s -> getTabs().stream()
						                                         .filter(suppliedClass::isInstance)
						                                         .map(tab -> tab.getTooltip().getText()))
				                           .collect(toList());
		tabArgs.stream()
				.filter(s -> !presentTabs.contains(s))
				.forEach(s -> addTab(tabSupplier.apply(s), tabArgs.size() == 1));
	}

}
