package com.nicedev.vocabularizer.gui;

import com.nicedev.util.Strings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.function.Consumer;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class WebViewTab extends Tab {
	private static final int DEFAULT_LENGTH = 25;
	private WebEngine webEngine;
	private Tooltip tooltip;
	private WebView webView;
	private final ObjectProperty<Point2D> cursorPosition;

	public WebViewTab() {
		this("", null);
	}

	public WebViewTab(String text, Object userData) {
		this(text, new WebView());
		enableFirstSelectionHandler();
		getView().setUserData(userData);
	}

	private WebViewTab(String text, Node content) {
		super(ellipsis(text), content);
		cursorPosition = new SimpleObjectProperty<>(new Point2D((int) (content.getBoundsInParent().getWidth() / 2),
		                                                        (int) (content.getBoundsInParent().getHeight() / 2)));
		setOnCloseRequest(event -> {
			webEngine.loadContent(null);
			webEngine = null;
			webView = null;
			tooltip = null;
		});
		webView = (WebView) content;
		webView.setOnMouseMoved(event -> cursorPosition.setValue(new Point2D(event.getSceneX(), event.getSceneY())));
		webEngine = webView.getEngine();
		webEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36");
		tooltip = new Tooltip(filter(text));
		Font font = tooltip.getFont();
		tooltip.fontProperty().set(new Font(font.getName(), font.getSize() + 1.0));
		setTooltip(tooltip);
		textProperty().addListener(onChangeListener);
	}

	private static String filter(String text) {
		return text.replaceAll(" {2,}|\\t+", " ");
	}

	private static String ellipsis(String text, int... lengthLimit) {
		if (text == null) return text;
		int textLength = text.length();
		text = stream(text.split("(?<=[\\r\\n\\f])"))
				       .filter(Strings::notBlank).map(s -> s.replaceAll("[ ]{2,}|\\t+", " "))
				       .collect(joining(" "));
		text = text.split("[\\n\\r\\f]")[0];
		int limit = lengthLimit != null && lengthLimit.length > 0
				            ? lengthLimit[0] : Math.min(DEFAULT_LENGTH, text.length());
		return (textLength > limit) ? text.substring(0, limit).concat(" ...") : text;
	}

	private ChangeListener<String> onChangeListener = (observable, oldValue, newValue) -> {
		setText(ellipsis(newValue));
		tooltip.setText(filter(newValue));
	};

	public WebEngine getEngine() {
		return webEngine;
	}

	public WebView getView() {
		return webView;
	}

	public ObjectProperty<Point2D> getCursorPos() {
		return cursorPosition;
	}

	public void addHandlers(Consumer<WebViewTab> handlersBulk) {
		handlersBulk.accept(this);
	}

	private void enableFirstSelectionHandler() {
		setUserData(Boolean.TRUE);
	}

	private void disableFirstSelectionHandler() {
		setUserData(Boolean.FALSE);
	}

	public boolean firstSelection() {
		return (Boolean) getUserData();
	}

	public void setOnSelected(Consumer<WebViewTab> handler) {
		selectedProperty().addListener((observable, oldValue, newValue) -> {
			handler.accept(this);
			disableFirstSelectionHandler();
		});
	}
}
