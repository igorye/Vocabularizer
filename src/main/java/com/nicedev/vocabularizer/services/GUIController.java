package com.nicedev.vocabularizer.services;

import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.nicedev.vocabularizer.dictionary.Vocabula;
import com.nicedev.vocabularizer.services.data.History;
import com.nicedev.vocabularizer.services.sound.PronouncingService;
import com.sun.javafx.scene.control.skin.ContextMenuContent;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GUIController implements Initializable{

	@FXML private ToggleButton toggleSound;
	@FXML private WebView view;
	@FXML private Label stats;
	@FXML private TextArea htmlText;
	@FXML private TextField queryText;
	private Scene mainScene;
	private WebEngine engine;

	private int updateCount = 0;
	private static final String WH_REQUEST_URL = "http://wooordhunt.ru/word/%s";
	private final String GT_REQUEST_URL = "https://translate.google.com/#%s/%s/%s";
	private String lastQuery = "";
	private String home = System.getProperties().getProperty("user.home");
	private String storageEn = String.format("%s\\%s.dict", home, "english");
	private PronouncingService pronouncingService;
	private Expositor[] expositors = {};
	private Dictionary en;
	private boolean autoPronounce = true;
	private History<String> history = new History<>(100);


	public void setMainScene(Scene scene) {
		mainScene = scene;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		((Runnable) () -> pronouncingService = new PronouncingService(100, false)).run();
		en = Dictionary.load("english", storageEn);
		if (en == null) {
			storageEn = storageEn.concat(".back");
			en = Dictionary.load("english", storageEn);
			if (en == null) {
				en = new Dictionary("english");
			}
			storageEn = storageEn.replace(".back", "");
		}
		expositors = new Expositor[]{new Expositor(en, false), new Expositor(en, true)};
		engine = view.getEngine();
	}

	public void stop() {
		Dictionary.save(en, storageEn);
		pronouncingService.release(0);
	}

	private void setSceneCursor(Cursor cursor) {
		mainScene.setCursor(cursor);
		requestView.setCursor(cursor);
	}

	@FXML
	protected void onKeyReleased(Event event) {
		String query = queryText.getText();
		String recent = "";
		if (query.matches(".*\\W.*")) lastQuery = query;
		EventType eventType = event.getEventType();
		Node sourceNode = (Node) event.getSource();
		if (eventType == KeyEvent.KEY_RELEASED) {
			boolean hasHistory = !history.isEmpty();
			switch (((KeyEvent)event).getCode()) {
				case ENTER:
					if (sourceNode instanceof TextField)
						handleQuery(query);
					else if (sourceNode instanceof WebView)
						handleQuery(getViewSelection());
					queryText.setText("");
					break;
				case UP:
					if(hasHistory) recent = history.next();
					queryText.setText(recent);
					break;
				case DOWN:
					if(hasHistory) recent = history.prev();
					queryText.setText(recent);
					break;
				case ESCAPE:
					queryText.setText("");
					break;
				case DELETE:
					String sToDelete = getViewSelection();
					if (sToDelete.replaceAll("\\W", "").trim().isEmpty()) sToDelete = query;
					if (!sToDelete.isEmpty()) deleteHeadword(sToDelete);
					queryText.setText("");
					break;
				default:
					updateView(filterVocabulas(query));
			}
		}
		queryText.requestFocus();
	}

	@FXML
	protected void toggleSound() {
		toggleSound.setStyle("-fx-background-image: url('"
				                     + ((autoPronounce = !autoPronounce) ? "soundOn.png" : "soundOff.png") + "');" +
				                     "-fx-background-size: contain;");
		queryText.requestFocus();
		pronouncingService.clear();
	}

	@FXML
	protected void onMouseClicked(MouseEvent event) {
		if(event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
			handleQuery(getViewSelection());
		}
		if (getViewSelection().isEmpty())
			queryText.requestFocus();
	}

	private String getViewSelection() {
		return ((String) engine.executeScript("window.getSelection().toString()")).trim();
	}

	@FXML
	public void onContextMenuRequested(ContextMenuEvent e) {
		updateContextMenu();
		queryText.requestFocus();
	}

	//stackoverflow-impl
	private PopupWindow updateContextMenu(){
		String selection = getViewSelection();
		if(selection.isEmpty()) return null;
		@SuppressWarnings("deprecation")
		final Iterator<Window> windows = Window.impl_getWindows();
		while (windows.hasNext()) {
			final Window window = windows.next();
			if (window instanceof ContextMenu) {
				if(window.getScene() != null && window.getScene().getRoot() != null) {
					Parent root = window.getScene().getRoot();
					// access to context menu content
					if(root.getChildrenUnmodifiable().size() > 0) {
						Node popup = root.getChildrenUnmodifiable().get(0);
						if(popup.lookup(".context-menu") != null) {
							Node bridge = popup.lookup(".context-menu");
							ContextMenuContent cmc = (ContextMenuContent)((Parent)bridge).getChildrenUnmodifiable().get(0);
							// adding new item:
							MenuItem menuItem = new MenuItem("Explain");
							menuItem.setOnAction(e -> handleQuery(selection));
							cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(menuItem));
							// add new item:
							menuItem = new MenuItem("Translate via Google");
							menuItem.setOnAction(e -> showTranslation(selection, requestFormatterGT));
							cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(menuItem));
							menuItem = new MenuItem("Translate via WooordHunt");
							menuItem.setOnAction(e -> showTranslation(selection, requestFormatterWH));
							cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(menuItem));
							menuItem = new MenuItem("Pronounce");
							menuItem.setOnAction(e -> pronouncingService.pronounce(selection));
							cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(menuItem));
							menuItem = new MenuItem("Delete");
							menuItem.setOnAction(e -> deleteHeadword(selection));
							cmc.getItemsContainer().getChildren().add(cmc.new MenuItemContainer(menuItem));
							return (PopupWindow)window;
						}
					}
				}
				return null;
			}
		}
		return null;
	}

	Function<String, String> requestFormatterWH = s -> String.format(WH_REQUEST_URL, s);

	Function<String, String> requestFormatterGT = s -> String.format(GT_REQUEST_URL, en.language.shortName,
																Locale.getDefault().getLanguage(), s);

	private void showTranslation(String selection, Function<String, String> requestFormatter) {
		translateTab.setText(getTrimedString(selection, 30));
		tabPane.getSelectionModel().select(translateTab);
		String textToTranslate = selection.replaceAll("\\s", " ");
		translationTooltip.setText(textToTranslate);
		translateTab.setTooltip(translationTooltip);
		translateView.getEngine().load(requestFormatter.apply(textToTranslate));
		queryTabInactive = true;
	}

	private void deleteHeadword(String selection) {
		Collection<String> headwords = Arrays.asList(selection.split("\n")).stream()
				                               .filter(s -> !s.trim().isEmpty()).collect(Collectors.toList());
		int count = headwords.size();
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
				                         String.format("You're about to delete %d headword%s." +
						                                       " Continue?", count, count > 1 ? "s": ""),
				                         ButtonType.YES, ButtonType.NO);
		hackAlert(confirm);
		confirm.showAndWait().filter(response -> response == ButtonType.YES).ifPresent(response -> {
			headwords.forEach(headword -> en.removeVocabula(headword.indexOf("[") > 0
					                                                ? headword.substring(0, headword.indexOf("[")-1)
					                                                : headword.trim()));
		});
		updateView(filterVocabulas(lastQuery));
	}

	//stackoverflow-impl
	private void hackAlert(Alert confirm) {
		DialogPane dialogPane = confirm.getDialogPane();
		((Button) dialogPane.lookupButton(ButtonType.YES)).setDefaultButton(false);
		EventHandler<KeyEvent> fireOnEnter = event -> {
			if (KeyCode.ENTER.equals(event.getCode()) && event.getTarget() instanceof Button)
				((Button) event.getTarget()).fire();
		};
		dialogPane.getButtonTypes().stream()
				.map(dialogPane::lookupButton)
				.forEach(button -> button.addEventHandler(KeyEvent.KEY_PRESSED, fireOnEnter));
	}

	protected void handleQuery(String query) {
		if (query.isEmpty()) {
			updateView(filterVocabulas(query));
			return;
		}
		String[] fQuery = new String[]{query};
		Platform.runLater( () -> {
			history.add(fQuery[0]);
			setSceneCursor(Cursor.WAIT);
			int defCount = en.getDefinitionCount();
			fQuery[0] = filterRequest(fQuery[0]);
			if (fQuery[0].isEmpty()) return;
			try {
				fQuery[0] = fQuery[0].split("\\s{2,}|\t")[0];
				Vocabula vocabula;
				Collection<Vocabula> vocabulas;
				if ((vocabula = en.getVocabula(fQuery[0])) == null) {
					boolean strictLookup = !fQuery[0].contains("*");
					fQuery[0] = fQuery[0].replaceAll("\\*", "");
					vocabulas = findVocabula(fQuery[0], strictLookup);
					if (!vocabulas.isEmpty() && strictLookup) {
						en.addVocabulas(vocabulas);
						Collection<String> headwords = vocabulas.stream()
								                               .map(voc -> voc.headWord)
								                               .collect(Collectors.toList());
						String content = headwords.size() > 1
								                 ? stringCollectionToHTML(headwords, fQuery[0])
								                 : vocabulas.iterator().next().toHTML();
						updateView(content);
						if (headwords.size() == 1) pronounce(vocabulas.iterator().next());
					} else
						updateView(stringCollectionToHTML(getSuggestions(fQuery[0]), fQuery[0]));
				} else {
					updateView(vocabula.toHTML());
					pronounce(vocabula);
				}
				if (defCount != en.getDefinitionCount()) stats.setText(en.toString());
				if (++updateCount % 5 == 0) Dictionary.save(en, storageEn);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (defCount != en.getDefinitionCount())
					Dictionary.save(en, storageEn);
			}
			lastQuery = fQuery[0];
		});
	}

	private String[] emphasis(String query) {
		query = query.replaceAll("\\p{Punct}", "");
		return !query.isEmpty() ? new String[]{query} : new String[]{};
	}

	private Collection<String> getSuggestions(String filter) {
		Set<String> suggestions = new HashSet<>();
		for(Expositor expositor: expositors)
			suggestions.addAll(expositor.getRecentSuggestions().stream()
					                   .filter(s -> s.toLowerCase().contains(filter.toLowerCase()))
					                   .collect(Collectors.toList()));
		return suggestions;
	}

	private Collection<Vocabula> findVocabula(String query, boolean lookupSimilar) {
		return Arrays.asList(expositors).parallelStream()
				       .map(expositor -> expositor.getVocabula(query, lookupSimilar))
				       .collect(HashSet::new, Collection::addAll, Collection::addAll);
	}

	private void pronounce(Vocabula vocabula) {
		if(!autoPronounce) return;
		pronouncingService.clear();
		Iterator<String> sIt = vocabula.getPronunciationSources().iterator();
		if (sIt.hasNext()) pronouncingService.pronounce(sIt.next(), 500);
		else pronouncingService.pronounce(vocabula.headWord, 500);
	}

	private void updateView(String body) {
		Platform.runLater(() -> {
			String cssHref = getClass().getProtectionDomain().getClassLoader().getResource("view.css").toString();
//		String srcHref = getClass().getProtectionDomain().getClassLoader().getResource("view.js").toString();
			if (cssHref == null) cssHref = "";
			String htmlContent = String.format("<html>\n<head>\n<link rel='stylesheet' href='%s'/>" +
//				                                   "<script src='%s'></script>" +
					                                   "</head>\n<body>\n%s</body>\n</html>", cssHref, body);
			engine.loadContent(htmlContent);
			String tabText = lastQuery;
			queryTab.setText(getTrimedString(tabText, 30));
			if (queryTabInactive) {
				tabPane.getSelectionModel().select(queryTab);
				queryTabInactive = false;
			}
			setSceneCursor(Cursor.DEFAULT);
		});
	}

	private String stringCollectionToHTML(Collection<String> collection, String... args) {
		if(collection.isEmpty()) return "Nothing found";
		StringBuilder content = new StringBuilder("");
		boolean emphasisQuery = args.length > 0;
		List<String> list = new LinkedList<>(collection);
		if(emphasisQuery) {
			list = collection.stream().filter(s -> s.startsWith(args[0])).sorted().collect(Collectors.toList());
			list.addAll(collection.stream().filter(s -> !s.startsWith(args[0])).sorted().collect(Collectors.toList()));
		}
		list.stream().forEach(item -> {
			String fmt = "<span class='list'>%s</span> [<span class='partofspeech'> %s </span>]\n<br>";
			String parts = Arrays.toString(en.getPartsOfSpeech(item).toArray()).replaceAll("\\[|\\]", "");
			if (emphasisQuery && item.toLowerCase().indexOf(args[0].toLowerCase()) >= 0) {
				int beginIndex = item.toLowerCase().indexOf(args[0].toLowerCase());
				String emSubStr = item.substring(beginIndex, beginIndex + args[0].length());
				item = item.replace(emSubStr, String.format("<b>%s</b>", emSubStr));
			}
			String formatted = String.format(fmt, item, parts);
			content.append(formatted);
		});
		return content.append("").toString();
	}

	private String vocabulaCollectionToHTML(Collection<Vocabula> collection, String query) {
		return stringCollectionToHTML(collection.stream().map(item -> item.headWord).collect(Collectors.toList()), query);
	}

	public void onLoad() {
		updateView(filterVocabulas(""));
	}

	private String filterVocabulas(String filter) {
		return stringCollectionToHTML(en.listVocabula(filter), emphasis(filter));
	}
}
