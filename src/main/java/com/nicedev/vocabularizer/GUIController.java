package com.nicedev.vocabularizer;

import com.nicedev.util.Combinatorics;
import com.nicedev.util.Maps;
import com.nicedev.util.QueuedCache;
import com.nicedev.util.Strings;
import com.nicedev.vocabularizer.dictionary.Definition;
import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.nicedev.vocabularizer.dictionary.Vocabula;
import com.nicedev.vocabularizer.gui.*;
import com.nicedev.vocabularizer.services.Expositor;
import com.nicedev.vocabularizer.services.sound.PronouncingService;
import com.nicedev.vocabularizer.services.task.DelayedTaskService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.input.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.nicedev.util.Comparators.indexOfCmpr;
import static com.nicedev.util.Comparators.startsWithCmpr;
import static com.nicedev.util.Html.wrapInTag;
import static com.nicedev.util.SimpleLog.log;
import static com.nicedev.util.Streams.getStream;
import static com.nicedev.util.Strings.getValidPattern;
import static com.nicedev.util.Strings.regexEscapeSymbols;
import static java.lang.Math.max;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.*;
import static javafx.scene.input.KeyCode.SPACE;

public class GUIController implements Initializable {
	
	public static final String CLASS_HIGHLIGHTED = "highlighted";
	@FXML
	public ToggleButton toggleSimilar;
	@FXML
	private ToggleButton toggleSound;
	@FXML
	private ToggleButton toggleShowParts;
	@FXML
	private ToggleButton toggleRE;
	@FXML
	private TableView<String> hwTable;
	@FXML
	private TableColumn<String, String> hwColumn;
	@FXML
	private TableColumn<String, String> posColumn;
	@FXML
	private Label stats;
	//	@FXML private TextField queryBox;
	@FXML
	private ComboBox<String> queryBox;
	@FXML
	private Tab mainTab;
	//	    @FXML private TabPane tabPane;
	@FXML
	private WebTabPane tabPane;
	
	private final Label NOTHING_FOUND = new Label("Nothing found");
	private final Label NO_MATCH = new Label("No matches");
	private final Label SEARCHING = new Label("Searching...");
	
	private final String COMPARING_DELIMITER = " | ";
	private final int LIST_FILTERING_DELAY = 500;
	private final int INPUT_FILTERING_DELAY = 400;
	private final int PRONUNCIATION_DELAY = 500;
	private final int RECENT_QUERIES_TO_LOAD = 150;
	private final String ALL_MATCH = "";
	private final int FILTER_DELAY = 500;
	private final boolean ALLOW_PARALLEL_STREAM = Boolean.valueOf(System.getProperty("allowParallelStream", "false"));
	private final int INDEXING_ALGO = Integer.valueOf(System.getProperty("indexingAlgo", "0"));
	private final int FILTER_CACHE_SIZE = Integer.valueOf(System.getProperty("fcSize", "25"));
	private final String WH_REQUEST_URL = "http://wooordhunt.ru/word/%s";
	private final String GT_REQUEST_URL = "https://translate.google.com/#%s/%s/%s";
	private final String HOME_PATH = System.getProperties().getProperty("user.home");
	private final String HISTORY_FILENAME = "vocabularizer.history";
	private final String cssHref;
	private final String jsHref;
	private final String storageEn = String.format("%s\\%s.dict", HOME_PATH, "english");
	
	private Scene mainScene;
	private String localLanguage;
	private String foreignLanguage;
	private int updateCount = 0;
	private PronouncingService pronouncingService;
	private Expositor[] expositors;
	private Dictionary dictionary;
	private boolean autoPronounce = true;
	private QueuedCache<String, List<String>> filterCache;
	private ObservableList<String> hwData;
	private Node relevantSelectionOwner;
	private ObservableList<String> selectedHWItems;
	private boolean filterOn = true;
	private boolean clearOnEsc = true;
	private StringProperty queryBoxValue = new SimpleStringProperty();
	private boolean ctrlIsDown = false;
	private DelayedTaskService<Collection<String>> queryBoxFiltering;
	private DelayedTaskService<String> tableItemFiltering;
	private ExecutorService executor;
	private volatile Map<String, Collection<String>> fullSearchIndex = emptyMap();
	private Future<Boolean> indexer;
	private int leastSelectedIndex;
	private int caretPos = 0;
	
	@SuppressWarnings("unchecked")
	public GUIController() {
		String res = getClass().getProtectionDomain().getClassLoader().getResource("view.css").toExternalForm();
		cssHref = res != null ? res : "";
		res = getClass().getProtectionDomain().getClassLoader().getResource("view.js").toExternalForm();
		jsHref = res != null ? res : "";
		
		Function<String, Task<Collection<String>>> queryFilterTaskProvider = pattern -> new Task<Collection<String>>() {
			@Override
			protected Collection<String> call() throws Exception {
				return isCancelled() ? Collections.emptyList() : filterList(pattern);
			}
		};
		queryBoxFiltering = new DelayedTaskService<>(queryFilterTaskProvider, INPUT_FILTERING_DELAY);
		queryBoxFiltering.setOnSucceeded(event -> {
			hwData.setAll((Collection<String>) event.getSource().getValue());
			event.getSource().cancel();
		});
		
		Function<String, Task<String>> tableFilterTaskProvider = pattern -> new Task<String>() {
			@Override
			protected String call() throws Exception {
				return isCancelled() ? "" : queryBoxValue.get();
			}
		};
		tableItemFiltering = new DelayedTaskService<>(tableFilterTaskProvider, FILTER_DELAY);
		tableItemFiltering.setOnSucceeded(event -> {
			updateQueryField(((DelayedTaskService)event.getSource()).getFilter());
			event.getSource().cancel();
		});
		executor = Executors.newCachedThreadPool();
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		pronouncingService = new PronouncingService(100, false);
		loadDictionary();
		expositors = new Expositor[]{new Expositor(dictionary, false), new Expositor(dictionary, true)};
		localLanguage = Locale.getDefault().getLanguage();
		foreignLanguage = dictionary.language.shortName;
		queryBox.setValue("");
	}
	
	/*************************************
	 *                                   *
	 *   init helper methods             *
	 *                                   *
	 ************************************/
	
	private void loadDictionary() {
		Optional<Dictionary> loaded = Dictionary.load("english", storageEn);
		String backUpPath = storageEn.concat(".back");
		if (loaded.isPresent()) {
			dictionary = loaded.get();
			try {
				Files.copy(Paths.get(storageEn), Paths.get(backUpPath), REPLACE_EXISTING);
			} catch (IOException e) {
				log("Unable to create backup - %s", e.getMessage());
			}
		} else {
			//read backup or create new
			dictionary = Dictionary.load("english", backUpPath).orElse(new Dictionary("english"));
		}
	}
	
	
	void setMainScene(Scene scene) {
		mainScene = scene;
	}
	
	@SuppressWarnings("unchecked")
	public void onLoad() {
//		scheduler.setRemoveOnCancelPolicy(true);
		loadRecentQueries();
		initIndex();
//		testIndex(1);
		filterCache = new QueuedCache<>("", FILTER_CACHE_SIZE);
//		hwData.addListener((ListChangeListener<String>) c -> { if (filterOn) hwTable.refresh();} );
		queryBoxValue.bind(queryBox.editorProperty().get().textProperty());
		queryBox.editorProperty().get().setOnContextMenuRequested(this::onContextMenuRequested);
		queryBoxValue.addListener(queryBoxValueChangeListener);
		queryBox.focusedProperty().addListener(queryBoxFocusChangeListener);
		queryBox.showingProperty().addListener(queryBoxShowingChangeListener);
		queryBox.addEventFilter(KeyEvent.KEY_PRESSED, queryBoxEventFilter);
		queryBox.addEventFilter(KeyEvent.KEY_RELEASED, queryBoxEventFilter);
		hwData = FXCollections.observableArrayList();
		queryBoxFiltering.start();
		tableItemFiltering.start();
		hwTable.itemsProperty().setValue(hwData);
//		hwData.setAll(filterList(ALL_MATCH));
		hwTable.getSelectionModel().setCellSelectionEnabled(false);
		hwTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		hwTable.setPlaceholder(NOTHING_FOUND);
//		hwTable.setOnScroll(tableScrollEventHandler);
		selectedHWItems = hwTable.getSelectionModel().getSelectedItems();
		relevantSelectionOwner = hwTable;
		hwTable.focusedProperty().addListener(tableFocusChangeListener);
		hwTable.getSelectionModel().getSelectedItems().addListener(tableSelectedItemsChangeListener);
		hwColumn.setCellFactory(filteredCellFactory);
		hwColumn.setCellValueFactory(hwCellValueFactory);
		posColumn.setCellValueFactory(posCellValueFactory);
		mainScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> ctrlIsDown = event.isControlDown());
		mainScene.addEventFilter(KeyEvent.KEY_RELEASED, event -> ctrlIsDown = event.isControlDown());
		tabPane.focusedProperty().addListener(tabPaneFocusChangeListener);
		tabPane.getSelectionModel().selectedItemProperty().addListener(tabSelectionListener);
		updateStatistics(false);
	}
	
	/*private void testIndex(int n) {
		indexer = executor.submit(() -> {
			for (int i = 0; i < 3; i++) {
				if (Thread.currentThread().isInterrupted() || indexer.isCancelled()) return false;
				for (int j = 0; j < n; j++)
					try {
						final int k = i;
						executor.submit(() -> { 
							Map<String, Collection<String>> map = getIndex(k);
							log("resulting index:");
							map.entrySet().forEach(me -> log(me.toString()));
							map = null;
							return true;
						}).get();
					} catch (InterruptedException e) {
						log("Interrupted while retrieving index");
					} catch (ExecutionException e) {
						log("Got error while building index: %s | %s", e.getMessage(), e.getCause().getMessage());
					}
			}
			log("test complete");
			return true;
		});
	}*/
	
	protected void initIndex() {
		fullSearchIndex = dictionary.filterHeadwords("", "i").stream()
				                  .collect(TreeMap::new, (map, s) -> map.put(s, singleton(s)), Map::putAll);
		//initializing mock-job
		indexer = executor.submit(() -> false);
		rebuildIndex();
	}
	
	
	public void stop() {
		log("stop: terminating app");
		log("stop: saving dict");
		if (updateCount != 0) saveDictionary();
		log("stop: canceling indexer");
		indexer.cancel(true);
		log("stop: canceling delayed filtering");
		queryBoxFiltering.cancel();
		tableItemFiltering.cancel();
		log("stop: terminating executor");
		executor.shutdownNow();
		log("stop: clearing pronouncingService");
		pronouncingService.clear();
		log("stop: terminating pronouncingService");
		pronouncingService.release(0);
		log("stop: saving recents");
		saveRecentQueries();
		log("terminating");
	}
	
	/************************************
	 *  state change listeners          *
	 ***********************************/
	
	private ListChangeListener<? super String> tableSelectedItemsChangeListener = (Change<? extends String> c) -> {
		hwTable.setContextMenu(c.getList().size() > 0 ? getTableViewContextMenu() : null);
		selectedHWItems = hwTable.getSelectionModel().getSelectedItems();
	};
	
	private ChangeListener<Boolean> tableFocusChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) {
			relevantSelectionOwner = hwTable;
			leastSelectedIndex = hwTable.getSelectionModel().getSelectedIndex();
		}
	};
	
/*
	private EventHandler<? super ScrollEvent> tableScrollEventHandler = event -> {
		if (event.isControlDown() && event.getSource() instanceof TableView) {
			if (event.getDeltaX() >= 0) {
			} else {
			}
			event.consume();
		}
	};
*/
	
	private ChangeListener<Boolean> tabPaneFocusChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) tabPane.getActiveTab().getContent().requestFocus();
	};
	
	private ChangeListener<Tab> tabSelectionListener = (observable, oldValue, newValue) -> {
		synchronized (tabPane) {
			filterOn = tabPane.getSelectedTabIndex() == 0;
			String caption = filterOn ? newValue.getText() : newValue.getTooltip().getText();
			updateQueryField(caption, false);
		}
		Platform.runLater(() -> {
			if (!filterOn || relevantSelectionOwner != queryBox) {
				tabPane.getActiveTab().getContent().requestFocus();
			}
			relevantSelectionOwner = newValue.getContent();
		});
		oldValue.getContent().setCursor(Cursor.DEFAULT);
	};
	
	private ChangeListener<Boolean> queryBoxFocusChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) {
			relevantSelectionOwner = queryBox;
			queryBox.editorProperty().get().positionCaret(caretPos);
		} else caretPos = queryBox.editorProperty().get().getCaretPosition();
		filterOn = newValue || tabPane.getSelectedTabIndex() == 0;
	};
	
	private ChangeListener<Boolean> queryBoxShowingChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) clearOnEsc = false;
	};
	
	private ChangeListener<String> queryBoxValueChangeListener = (observable, oldValue, newValue) -> {
		if (filterOn) {
			try {
				if (queryBox.isShowing()) {
					engageFiltering(newValue, LIST_FILTERING_DELAY);
				} else {
					mainTab.setText(newValue);
					ofNullable(newValue).ifPresent(val -> engageFiltering(val, INPUT_FILTERING_DELAY));
					updateTableState(false);
					// auto enabling/disabling RE mode to simplify selection of words to compare
					if (!toggleRE.isSelected() || queryBoxValue.get().isEmpty())
						toggleRE.setSelected(queryBoxValue.get().contains("|"));
				}
			} catch (Exception e) {
				log("queryBoxLisener: unexpected exception - %s | %s", e.getMessage(), e.getCause().getMessage());
			}
		}
		
	};
	
	private void engageFiltering(String pattern, int delay) {
		queryBoxFiltering.setFilter(pattern);
		queryBoxFiltering.setDelay(Duration.millis(delay));
	}
	
	private Callback<TableColumn<String, String>, TableCell<String, String>> filteredCellFactory =
			param -> new FilteredTableCell(queryBoxValue);
	
	private Callback<CellDataFeatures<String, String>, ObservableValue<String>> posCellValueFactory =
			param -> new ReadOnlyObjectWrapper<>(dictionary.getPartsOfSpeech(param.getValue()).stream()
					                                     .map(pOS -> pOS.partName).collect(joining(", ")));
	
	private Callback<CellDataFeatures<String, String>, ObservableValue<String>> hwCellValueFactory =
			param -> new ReadOnlyObjectWrapper<>(param.getValue());
	
	/************************************
	 *                                  *
	 *  handlers                        *
	 *                                  *
	 ***********************************/
	@SuppressWarnings("unused")
	private EventHandler<ScrollEvent> onScrollEventHandler() {
		return event -> tabPane.getActiveEngine().ifPresent(we -> {
			if (event.isControlDown()) {
				we.executeScript("disableScroll()");
				if (event.getTextDeltaY() >= 0) we.executeScript("incFontSize()");
				else we.executeScript("decFontSize()");
			} else we.executeScript("enableScroll()");
		});
	}
	
	@FXML
	protected void onTableMousePressed(@SuppressWarnings("unused") MouseEvent event) {
		engageTableSelectedItemFiltering();
		int selectedIndex = hwTable.getSelectionModel().getSelectedIndex();
		if (selectedIndex != -1) {
			leastSelectedIndex = selectedIndex;
		} else {
			if (selectedHWItems.size() > 0) selectedIndex = 0;
		}
	}
	
	@FXML
	protected void onTableMouseReleased(@SuppressWarnings("unused") MouseEvent event) {
		tableItemFiltering.cancel();
	}
	
	@FXML
	protected void onTableViewKeyPressed(KeyEvent event) {
		if (ctrlIsDown && event.getCode() == SPACE) engageTableSelectedItemFiltering();
	}
	
	@FXML
	protected void onHWTVKeyReleased(KeyEvent event) {
		if (event.getEventType() != KeyEvent.KEY_RELEASED) return;
		String query = selectedHWItems.size() > 0 ? hwTable.getSelectionModel().getSelectedItem() : "";
		KeyCode keyCode = event.getCode();
		switch (keyCode) {
			case ENTER:
				if (selectedHWItems.size() > 1) {
					tabPane.insertIfAbsent(selectedHWItems, expositorTabSupplier, ExpositorTab.class);
				} else handleQuery(query);
				break;
			case DELETE:
				deleteHeadword(Optional.empty());
				break;
			case ESCAPE:
				resetFilter();
				updateTableState();
				break;
			default:
				if (!event.isControlDown() && event.getText().matches("[\\p{Graph}]")) {
					updateQueryField(event.getText());
				} else if (event.isControlDown() && event.getCode() == SPACE) {
					tableItemFiltering.cancel();
				}
		}
		event.consume();
	}
	
	private void resetFilter() {
		try {
			filterOn = true;
			updateQueryField(ALL_MATCH);
			queryBox.getSelectionModel().clearSelection();
			hwData.setAll(filterList(ALL_MATCH));
			updateTableState();
		} catch (Exception e) {
			log("resetFilter: unexpected exception - %s | %s", e.getMessage(), e.getCause().getMessage());
		}
	}
	
	@FXML
	protected void onWebViewKeyReleased(KeyEvent event) {
		if (event.getEventType() != KeyEvent.KEY_RELEASED) return;
		Node sourceNode = (Node) event.getSource();
		String query = getSelectedText();
		KeyCode keyCode = event.getCode();
		switch (keyCode) {
			case W:
				if (event.isControlDown()) tabPane.removeActiveTab();
				break;
			case ESCAPE:
				if (!(tabPane.getActiveTab() instanceof TranslationTab))
					tabPane.removeActiveTab();
				break;
			case ENTER:
				if (tabPane.getActiveTab() instanceof TranslationTab && !event.isControlDown()) break;
				if (query.isEmpty()) {
					getAssignedVocabula(tabPane.getActiveTab()).ifPresent(this::pronounce);
				} else {
					List<String> queries = Stream.of(query.split("[\\n\\r\\f]"))
							                       .filter(Strings::notBlank)
							                       .map(s -> s.replaceAll("\\t| {2,}", " ").trim())
							                       .collect(toList());
					if (queries.size() > 1) {
						tabPane.insertIfAbsent(queries, expositorTabSupplier, ExpositorTab.class);
					} else handleQuery(query);
				}
				break;
			case CONTEXT_MENU:
				double x = ((WebViewTab) tabPane.getActiveTab()).getCursorPos().getValue().getX();
				double y = ((WebViewTab) tabPane.getActiveTab()).getCursorPos().getValue().getY();
				Event.fireEvent(sourceNode, new MouseEvent(MouseEvent.MOUSE_RELEASED, x, y, 0, 0,
						                                          MouseButton.SECONDARY, 1,
						                                          event.isShiftDown(), event.isControlDown(),
						                                          event.isAltDown(), event.isMetaDown(),
						                                          false, false,
						                                          true, false,
						                                          true, true, null));
				break;
		}
		event.consume();
	}
	
	private EventHandler<KeyEvent> queryBoxEventFilter = event -> {
		if (!clearOnEsc && event.getCode() == KeyCode.ESCAPE) {
			queryBox.hide();
			clearOnEsc = true;
			event.consume();
		}
	};
	
	@FXML
	protected void onQueryBoxKeyReleased(KeyEvent event) {
		if (event.getEventType() != KeyEvent.KEY_RELEASED) return;
		String query = queryBoxValue.getValue();
		KeyCode keyCode = event.getCode();
		switch (keyCode) {
			case UP:
			case DOWN:
				if (!queryBox.isShowing()) queryBox.show();
				break;
			case ENTER:
				Tab currTab = tabPane.getActiveTab();
				if (tabPane.getSelectedTabIndex() > 0 && query.equals(currTab.getText())) {
					getAssignedVocabula(currTab).ifPresent(this::pronounce);
				} else {
					updateTableState();
					appendRequestHistory(query);
					handleQuery(query);
				}
				clearOnEsc = true;
				break;
			case ESCAPE:
				if (clearOnEsc) resetFilter();
			case W:
				if (event.isControlDown()) {
					tabPane.removeActiveTab();
					break;
				}
			default:
				if (!event.isControlDown() && event.getText().matches("[\\p{Graph} ]")) {
					mainTab.setText(queryBoxValue.getValue());
					updateQueryField(queryBoxValue.getValue());
				}
		}
		event.consume();
	}
	
	@FXML
	protected void toggleSound() {
		autoPronounce = !autoPronounce;
		toggleSound.setStyle("-fx-background-image: url('" + (autoPronounce ? "soundOn.png" : "soundOff.png") + "');"
				                     + "-fx-background-size: contain;");
		queryBox.requestFocus();
		pronouncingService.clear();
	}
	
	@FXML
	protected void toggleShowParts() {
		posColumn.setVisible(toggleShowParts.isSelected());
		queryBox.requestFocus();
	}
	
	@FXML
	protected void toggleUseRE() {
		Platform.runLater(() -> hwData.setAll(filterList(queryBoxValue.getValue())));
		queryBox.requestFocus();
	}
	
	@FXML
	public void toggleAcceptSimilar(ActionEvent actionEvent) {
		if (toggleSimilar.isSelected()) onSearch(actionEvent);
	}
	
	@FXML
	protected void onMouseClicked(MouseEvent event) {
		ctrlIsDown = event.isControlDown();
		if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
			event.consume();
			if (tabPane.getActiveTab() instanceof TranslationTab) return;
			boolean mainTabActive = tabPane.getSelectedTabIndex() == 0;
			String selection = mainTabActive
					                   ? selectedHWItems.stream().findFirst().orElse("")
					                   : tabPane.getActiveViewSelection();
			if (!selection.isEmpty()) {
				String highlight = mainTabActive ? null : tabPane.getActiveTab().getText();
				handleQuery(removeGaps(selection), highlight);
			}
		}
	}
	
	@FXML
	protected void onSearch(@SuppressWarnings("unused") ActionEvent event) {
		updateTableState();
		Node source = relevantSelectionOwner;
		Event.fireEvent(source, new KeyEvent(source, source, KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
				                                    false, true, false, false));
	}
	
	@FXML
	public void onContextMenuRequested(ContextMenuEvent e) {
		Node source = (Node) e.getSource();
		String context = "";
		if (source instanceof WebView) {
			context = tabPane.getActiveViewSelection();
			if (context.isEmpty()) {
				context = getAssignedVocabula(tabPane.getActiveTab()).map(voc -> voc.headWord).orElse("");
			}
		}
		GUIUtil.updateContextMenu(context, this::appendContextMenuItems);
	}
	
	/*************************************
	 *  utility methods                  *
	 ************************************/
	
	private String composeRE(String text) {
		return (!toggleRE.isSelected())
				       ? Stream.of(text.split("(?=\\p{Punct})"))
						         .map(s -> (s.matches("\\p{Punct}.*")) ? "\\".concat(s) : s)
						         .collect(joining())
				       : text;
	}
	
	private void rebuildIndex() {
		executor.submit(() -> {
			indexer.cancel(true);
			indexer = executor.submit(() -> {
//				log("rebuildIndex: scheduled");
				long start = System.currentTimeMillis();
				fullSearchIndex = getIndex(INDEXING_ALGO);
				if (isIndexingAborted()) return false;
				log("built index in %f (allowParallelStream==%s, indexingAlgo==%d",
						(System.currentTimeMillis() - start) / 1000f, ALLOW_PARALLEL_STREAM, INDEXING_ALGO);
				return true;
			});
			try {
				if (indexer.get()) {
					resetCache();
					updateTableState(false);
				}
			} catch (InterruptedException e) {
				log("Interrupted while retrieving index");
			} catch (ExecutionException e) {
				log("Got error while building index: %s | %s", e.getMessage(), e.getCause().getMessage());
			}
		});
	}
	
	private Map<String, Collection<String>> getIndex(int indexingAlgo) {
		return getIndex(dictionary.filterVocabulas(""), indexingAlgo);
	}
	
	private Map<String, Collection<String>> getIndex(Vocabula vocabula) {
		return getIndex(Collections.singleton(vocabula), INDEXING_ALGO);
	}
	
	private Map<String, Collection<String>> getIndex(Collection<Vocabula> vocabulas, int indexingAlgo) {
		Map<String, Collection<String>> res;
		switch (indexingAlgo) {
			case 2 : res = getIndex2(vocabulas); break;
			case 3 : res = getIndex3(vocabulas); break;
			default: res = getIndex1(vocabulas);
		}
		return  res;
	}
	
	private void alterSearchIndex(Collection<Vocabula> vocabulas) {
		executor.execute(() -> {
			try {
				indexer.get();
				Map<String, Collection<String>> addToIndex = getIndex(vocabulas, INDEXING_ALGO);
				Maps.mergeLeft(fullSearchIndex, addToIndex, ALLOW_PARALLEL_STREAM);
				resetCache();
			} catch (InterruptedException | ExecutionException | CancellationException e) {
				rebuildIndex();
			}
		});
	}
	
	private void removeFromSearchIndex(Collection<String> headwords) {
		try {
			//await indexer's job completion
			indexer.get();
			//remove vocabula's index entries
			headwords.forEach(hw -> dictionary.getVocabula(hw)
					                        .ifPresent(vocabula -> getIndex(vocabula).values().stream()
							                                               .flatMap(Collection::stream)
							                                               .forEach(s -> {
								                                               int size = ofNullable(fullSearchIndex.get(s))
										                                                          .map(Collection::size).orElse(0);
								                                               if (size <= 2) fullSearchIndex.remove(s);
							                                               })));
			//remove vocabula's index keys
			headwords.forEach(hw -> {
				int size = ofNullable(fullSearchIndex.get(hw)).map(Collection::size).orElse(0);
				if (size <= 2) fullSearchIndex.remove(hw);
			});
			resetCache();
		} catch (InterruptedException | ExecutionException e) {
			rebuildIndex();
		}
	}
	
	private void resetCache() {
		Platform.runLater(() -> {
			filterCache.clear();
			filterCache.putPersistent(filterList(ALL_MATCH));
			try {
				ofNullable(mainTab.getText()).filter(Strings::blank).ifPresent(val -> hwData.setAll(filterList(val)));
			} catch (Exception e) {
				log("reset cache: unexpected exception - %s | %s", e.getMessage(), e.getCause().getMessage());
			}
		});
	}
	
	private void updateTableState() {
		updateTableState(true);
	}
	
	private void updateTableState(boolean doSearch) {
		Platform.runLater(() -> {
			hwTable.setPlaceholder(doSearch ? SEARCHING
					                       : Strings.notBlank(queryBoxValue.getValue()) ? NO_MATCH : NOTHING_FOUND);
			hwTable.refresh();
		});
	}
	
	private void engageTableSelectedItemFiltering() {
		try {
			if (selectedHWItems.isEmpty()) return;
			String selection = selectedHWItems.size() > 0
					                   ? hwTable.getSelectionModel().getSelectedItem()
					                   : hwTable.getItems().get(leastSelectedIndex);
			tableItemFiltering.setFilter(selection);
		} catch (Exception e) {
			log("engageTableSelectedItemFiltering: unexpected exception - %s | %s", e.getMessage(), e.getCause().getMessage());
		}
	}
	
	private void setSceneCursor(Cursor cursor) {
		Platform.runLater(() -> {
			tabPane.setCursor(cursor);
			tabPane.getActiveTab().getContent().setCursor(cursor);
		});
	}
	
	public class JSBridge {
		@SuppressWarnings("unused")
		public void jsHandleQuery(String headWord, String highlight) {
			log("jsBridge: jsHandleQuery: requested %s", headWord);
			// remove any tags
			headWord = headWord.replaceAll("<[^<>]+>", "");
			handleQuery(headWord, highlight);
		}
		
		@SuppressWarnings("unused")
		public void jsHandleQuery(String headWord) {
			log("jsBridge: jsHandleQuery: requested %s", headWord);
			handleQuery(headWord);
		}
	}
	
	private Consumer<WebViewTab> viewHndlrsSetter = tab -> {
		WebView view = tab.getView();
		view.setOnKeyReleased(this::onWebViewKeyReleased);
		view.setOnMouseClicked(this::onMouseClicked);
		view.setOnContextMenuRequested(this::onContextMenuRequested);
		view.focusedProperty().addListener((observable, oldValue, newValue) -> {
			if (newValue) relevantSelectionOwner = view;
		});
		if (tab instanceof ExpositorTab) {
			WebEngine engine = view.getEngine();
			JSBridge jsBridge = new JSBridge();
			engine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
				if (newValue == Worker.State.SUCCEEDED) {
					engine.setJavaScriptEnabled(true);
					JSObject window = (JSObject) engine.executeScript("window");
					window.setMember("jsBridge", jsBridge);
				}
			});
		}
	};
	
	private BiFunction<String, String[], WebViewTab> expositorTabSupplier = (title, highlights) -> {
		WebViewTab newTab;
		if (!title.contains(COMPARING_DELIMITER)) {
			newTab = new ExpositorTab(title, dictionary.getVocabula(title));
			// "update tab at first sight" flag
			newTab.setUserData(Boolean.TRUE);
			newTab.selectedProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(() -> {
				if ((Boolean) newTab.getUserData() && newValue) {
					Optional<Vocabula> optVocabula = getAssignedVocabula(newTab);
					String headWord = optVocabula.map(voc -> voc.headWord).orElse(title);
					if (!optVocabula.isPresent()) {
						handleQuery(headWord);
						updateQueryField(headWord, false);
					} else {
						updateView(optVocabula.get().toHTML(), highlights);
						pronounce(optVocabula.get());
					}
					newTab.setUserData(Boolean.FALSE);
				}
			}));
		} else {
			newTab = new ExpositorTab(title, null);
			newTab.setUserData(Boolean.TRUE);
			newTab.selectedProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(() -> {
				if ((Boolean) newTab.getUserData() && newValue) {
					String[] headwords = title.split(Strings.regexEscapeSymbols(COMPARING_DELIMITER, "|"));
					Collection<Vocabula> compared = Arrays.stream(headwords)
							                                .map(dictionary::getVocabula)
							                                .filter(Optional::isPresent)
							                                .map(Optional::get)
							                                .collect(toList());
					String comparing = compared.stream().map(Vocabula::toHTML).collect(joining("</td><td width='50%'>"));
					String content = String.format("<table><tr><td width='50%%'>%s</td></tr></table>", comparing);
					updateView(content);
					newTab.setUserData(Boolean.FALSE);
				}
			}));
		}
		newTab.addHandlers(viewHndlrsSetter);
		return newTab;
	};
	
	@SuppressWarnings("unchecked")
	private Optional<Vocabula> getAssignedVocabula(Tab newTab) {
		Optional<Optional<Vocabula>> userData = ofNullable((Optional<Vocabula>) newTab.getContent().getUserData());
		return userData.filter(Optional::isPresent).map(Optional::get);
	}
	
	private BiFunction<String, String[], WebViewTab> translationTabSupplier(Function<String, String> requestFormatter) {
		return (title, _arg) -> {
			WebViewTab newTab = new TranslationTab(title, requestFormatter.apply(encodeURL(title)));
			newTab.addHandlers(viewHndlrsSetter);
			return newTab;
		};
	}
	
	private void updateQueryField(String text) {
		updateQueryField(text, true);
	}
	
	private void updateQueryField(String text, boolean filterActive) {
		boolean filterState = filterOn;
		filterOn = filterActive;
		if (filterActive) {
			if (tabPane.getSelectedTabIndex() > 0) tabPane.selectTab(mainTab);
			if (relevantSelectionOwner != queryBox) queryBox.requestFocus();
			mainTab.setText(text);
		}
		int textLength = queryBoxValue.getValue().length();
		int newCaretPos = caretPos == textLength ? text.length() : caretPos == 0 ? caretPos + 1 : caretPos;
		queryBox.editorProperty().getValue().setText(text);
		queryBox.editorProperty().get().positionCaret(newCaretPos);
		caretPos = queryBox.editorProperty().get().getCaretPosition();
		filterOn = filterState;
	}
	
	private String getSelectedText() {
		String selection = "";
		String selectionOwnerName = relevantSelectionOwner.getClass().getSimpleName();
		switch (selectionOwnerName) {
			case "WebView":
				selection = tabPane.getActiveViewSelection();
				break;
			case "ComboBox":
				selection = queryBoxValue.getValue().trim();
				break;
			case "TableView":
				selection = selectedHWItems.stream().collect(joining("\n"));
		}
		return selection;
	}
	
	private ContextMenu getTableViewContextMenu() {
		ContextMenu cm = new ContextMenu();
		appendContextMenuItems(cm, Optional.empty());
		return cm;
	}
	
	private void appendContextMenuItems(Object contextMenu, Optional<String> context) {
		EventHandler<ActionEvent> handleQueryEH = this::onSearch;
		GUIUtil.addMenuItem("Explain", handleQueryEH, contextMenu);
		
		// headwords comparing available only from tableView
		if (contextMenu instanceof ContextMenu) {
			EventHandler<ActionEvent> compareHeadwordsEH = e -> compareHeadwords();
			GUIUtil.addMenuItem("Compare", compareHeadwordsEH, contextMenu);
		}
		// default translation behavior is not available in compare mode
		if (!context.filter(s -> s.contains("|")).isPresent()) {
			Function<String, String> requestFormatterGT = s -> String.format(GT_REQUEST_URL, foreignLanguage, localLanguage, s);
			EventHandler<ActionEvent> showGTTranslationEH = e -> showTranslation(context, requestFormatterGT);
			
			Function<String, String> requestFormatterWH = s -> String.format(WH_REQUEST_URL, s);
			EventHandler<ActionEvent> showWHTranslationEH = e -> showTranslation(context, requestFormatterWH);
			
			Menu translations = new Menu("Translate");
			GUIUtil.addMenuItem(" via Google", showGTTranslationEH, translations);
			GUIUtil.addMenuItem(" via WooordHunt", showWHTranslationEH, translations);
			GUIUtil.addMenuItem(translations, contextMenu);
		}
		
		EventHandler<ActionEvent> pronounceEH = e -> pronounce(context);
		GUIUtil.addMenuItem("Pronounce", pronounceEH, contextMenu);
		
		GUIUtil.addMenuItem("", null, contextMenu);
		
		EventHandler<ActionEvent> deleteHeadwordEH = e -> deleteHeadword(context);
		GUIUtil.addMenuItem("Delete headword", deleteHeadwordEH, contextMenu);
	}
	
	private void compareHeadwords() {
			if (relevantSelectionOwner == hwTable) {
				List<String> comparingPairs;
				if (selectedHWItems.size() > 2)
					comparingPairs = Combinatorics.getUniqueCombinations(selectedHWItems, 2).stream()
							                  .map(strings -> strings.stream().collect(joining(COMPARING_DELIMITER)))
							                  .collect(toList());
				else
					comparingPairs = Collections.singletonList(selectedHWItems.stream().collect(joining(COMPARING_DELIMITER)));
				tabPane.insertIfAbsent(comparingPairs, expositorTabSupplier, ExpositorTab.class);
			}
	}
	
	private void showTranslation(Optional<String> textToTranslate, Function<String, String> requestFormatter) {
		if (relevantSelectionOwner == hwTable && ctrlIsDown)
			tabPane.insertIfAbsent(selectedHWItems, translationTabSupplier(requestFormatter), TranslationTab.class);
		else {
			String text = textToTranslate.orElse(selectedHWItems.stream().sorted().collect(joining("\n")))
					              .replaceAll("\\t+| {2,}", " ");
			String translationURL = requestFormatter.apply(encodeURL(text));
			if (!tabPane.trySelectTab(TranslationTab.sameTab(translationURL)))
				tabPane.insertTab(translationTabSupplier(requestFormatter).apply(text, null), true);
		}
	}
	
	private String encodeURL(String s) {
		String encoded = "";
		try {
			encoded = URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			log (e.getMessage());
		}
		return encoded;
	}
	
	private void saveRecentQueries() {
		try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(HOME_PATH, HISTORY_FILENAME), CREATE, TRUNCATE_EXISTING)) {
			String history = queryBox.getItems().stream()
					                 .distinct()
					                 .filter(Strings::notBlank)
					                 .collect(joining("\n"));
			bw.write(history);
		} catch (IOException e) { /* NOP */ }
	}
	
	private void loadRecentQueries() {
		try (BufferedReader br = Files.newBufferedReader(Paths.get(HOME_PATH, HISTORY_FILENAME))) {
			br.mark(1_000_000);
			br.reset();
			queryBox.getItems().setAll(br.lines().limit(RECENT_QUERIES_TO_LOAD).collect(toList()));
		} catch (IOException | ClassCastException e) { /*NOP*/ }
	}
	
	private void handleQuery(String query, String... textsToHighlight) {
		boolean foundQueried = tabPane.trySelectTab(ExpositorTab.sameTab(query))
				                       && getAssignedVocabula(tabPane.getActiveTab()).isPresent();
		if (!foundQueried) handleQueryImpl(query, textsToHighlight);
	}
	
	private void handleQueryImpl(String query, String... textsToHighlight) {
		String filteredQuery = removeGaps(query);
		if (filteredQuery.isEmpty()) return;
		updateTableState();
		setSceneCursor(Cursor.WAIT);
		int defCount = dictionary.getDefinitionCount();
		executor.execute(() -> {
			try {
				Optional<Vocabula> optVocabula = dictionary.getVocabula(filteredQuery);
				String cQuery = filteredQuery.replaceAll("~", "").trim();
				if (optVocabula.isPresent()) {
					showQueryResult(optVocabula.get(), textsToHighlight);
				} else {
					boolean acceptSimilar = filteredQuery.startsWith("~") || toggleSimilar.isSelected();
					Set<Vocabula> vocabulas = findVocabula(cQuery, acceptSimilar);
					if (!vocabulas.isEmpty()) {
						Function<Set<Vocabula>, Set<Vocabula>> availableOf = vocabulaSet -> {
							return dictionary.getVocabulas(vocabulaSet.stream().map(voc -> voc.headWord).collect(toSet()));
						};
						Set<Vocabula> availableVs = availableOf.apply(vocabulas);
						int nAdded = dictionary.addVocabulas(vocabulas);
						if (nAdded > 0)	updateCount++;
						Set<Vocabula> addedVs = availableOf.apply(vocabulas);
						addedVs.removeAll(availableVs);
						hwData.setAll(addedVs.stream().map(v -> v.headWord).collect(toList()));
						alterSearchIndex(addedVs);
						if (nAdded == 1 && hwData.contains(cQuery) ) {
							showQueryResult(addedVs.iterator().next(), textsToHighlight);
						} else {
							Platform.runLater(() -> {
								tabPane.removeTab(cQuery);
								tabPane.selectTab(mainTab);
							});
							updateTableState(false);
						}
						updateQueryField(cQuery, false);
					} else {
						Optional<Vocabula> featuringVocabula = getFeaturingVocabula(cQuery);
						if (featuringVocabula.isPresent()) {
							showQueryResult(featuringVocabula.get(), cQuery);
						} else {
							hwData.setAll(getSuggestions(cQuery));
							Platform.runLater(() -> {
								tabPane.selectTab(mainTab);
//								log("HQ: updateQueryField(%s,%s)", cQuery, false);
								updateQueryField(cQuery, false);
							});
						}
						updateTableState();
					}
				}
				if (defCount != dictionary.getDefinitionCount()) updateStatistics(false);
			} catch (Exception e) {
				log(e.getMessage() + e.getCause());
			} finally {
				if (updateCount % 5 == 0) saveDictionary();
				updateTableState(false);
				setSceneCursor(Cursor.DEFAULT);
			}
		});
	}
	
	private @SuppressWarnings("unused") boolean saveDictionary() {
		saveRecentQueries();
		return Dictionary.save(dictionary, storageEn);
	}
	
	private void appendRequestHistory(String query) {
		boolean oldState = filterOn;
		filterOn = false;
		queryBox.getItems().add(0, query);
		int index;
		while((index = queryBox.getItems().indexOf(query)) > 0) queryBox.getItems().remove(index);
		filterOn = oldState;
		queryBox.getSelectionModel().select(0);
	}
	
	private Optional<Vocabula> getFeaturingVocabula(String headWord) {
		List<String> mentions = filterList(headWord);
		return filterList(headWord).stream()
				       .filter(hw -> !hw.equalsIgnoreCase(headWord)
						                     && (filterList(hw.toLowerCase()).contains(headWord) || mentions.size() == 2))
				       .findFirst()
				       .flatMap(hw -> dictionary.getVocabula(hw));
	}
	
	private void showQueryResult(Vocabula vocabula, String... textsToHighlight) {
		Platform.runLater(() -> showQueryResultImpl(vocabula, textsToHighlight));
	}
	
	private void showQueryResultImpl(Vocabula vocabula, String... textsToHighlight) {
		Tab currentTab = tabPane.getActiveTab();
		if (ctrlIsDown || tabPane.getTabs().size() == 1 || (currentTab instanceof TranslationTab)
				    || (!tabPane.trySelectTab(ExpositorTab.sameTab(vocabula.headWord))
						        && !(currentTab instanceof ExpositorTab
								             || tabPane.trySelectTab(tab -> tab instanceof ExpositorTab)))) {
			tabPane.insertTab(expositorTabSupplier.apply(vocabula.headWord, textsToHighlight), true);
		} else {
			currentTab = tabPane.getActiveTab();
			currentTab.setText(vocabula.headWord);
			currentTab.getContent().setUserData(Optional.of(vocabula));
			log("showQueryResultImpl: updateView for\"%s\"", vocabula.headWord);
			updateView(vocabula.toHTML(), textsToHighlight);
			pronounce(vocabula);
			log("SQRI: updateQueryField(%s,%s)", vocabula.headWord, false);
			updateQueryField(vocabula.headWord, false);
		}
	}
	
	private void deleteHeadword(Optional<String> selection) {
		Collection<String> headwords = selection
				                               .map(s -> Stream.of(s.split("[\\n\\r\\f]"))
						                                         .filter(Strings::notBlank)
						                                         .collect(toList()))
				                               .orElse(selectedHWItems).stream().collect(toList());
		int count = headwords.size();
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, getDeleteMsg(count), ButtonType.YES, ButtonType.NO);
		GUIUtil.hackAlert(confirm);
		confirm.showAndWait().filter(response -> response == ButtonType.YES).ifPresent(response -> {
//			indexer.cancel(true);
			if (tabPane.getSelectedTabIndex() > 0 && headwords.contains(tabPane.getActiveTab().getText()))
				tabPane.removeActiveTab();
			executor.execute(getDeletionTask(headwords));
		});
	}
	
	private Task<Void> getDeletionTask(Collection<String> headwords) {
		return new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				List<String> deleted = headwords.stream()
						                       .map(String::trim)
						                       .filter(headword -> dictionary.removeVocabula(headword))
						                       .collect(toList());
				if (!deleted.isEmpty()) {
					hwData.removeAll(deleted);
					removeFromSearchIndex(deleted);
					saveDictionary();
					updateStatistics(false);
					if (tabPane.getTabs().size() == 1) {
						log("DH: updateQueryField(%s,%s)", queryBoxValue.getValue(), true);
						updateQueryField(queryBoxValue.getValue(), true);
					}
					updateTableState(false);
				}
				return null;
			}
		};
	}
	
	private String getDeleteMsg(int count) {
		return String.format("You're about to delete %d headword%s. Continue?", count, count > 1 ? "s" : "");
	}
	
	private void updateStatistics(boolean invalidateIndex) {
		Platform.runLater(() -> stats.setText(dictionary.toString()));
		if (invalidateIndex) rebuildIndex();
	}
	
	private String removeGaps(String queryText) {
		queryText = Stream.of(queryText.split("[\\n\\r\\f]"))
				            .filter(Strings::notBlank).map(String::trim).findFirst().orElse("")
				            .replaceAll("\\t| {2,}", " ");
		int trimPos = queryText.indexOf('[');
		if (trimPos > 0) queryText = queryText.substring(0, trimPos).trim();
		return queryText;
	}
	
	@SuppressWarnings("unchecked")
	private Set<Vocabula> findVocabula(String query, boolean acceptSimilar) {
		return Stream.of(expositors)
				       .parallel()
				       .map(expositor -> new Object[] {expositor.priority, expositor.getVocabula(query, acceptSimilar)})
				       .sorted(Comparator.comparingInt(tuple -> (int) tuple[0]))
				       .flatMap(tuple -> ((Collection<Vocabula>) tuple[1]).stream())
				       .collect(LinkedHashSet::new, Set::add, Set::addAll);
	}
	
	private void pronounce(Optional<String> selection) {
		Platform.runLater(() -> {
			pronouncingService.clear();
			pronouncingService.pronounce(selection.orElse(selectedHWItems.stream().collect(joining("\n"))), PRONUNCIATION_DELAY);
		});
	}
	
	private void pronounce(Vocabula vocabula) {
		if (!autoPronounce) return;
		pronouncingService.clear();
		Iterator<String> sIt = vocabula.getPronunciationSources().iterator();
		if (sIt.hasNext()) pronouncingService.pronounce(sIt.next(), PRONUNCIATION_DELAY);
		else pronouncingService.pronounce(vocabula.headWord, PRONUNCIATION_DELAY);
		
	}
	
	private List<String> getSuggestions(String match) {
		return getStream(asList(expositors), ALLOW_PARALLEL_STREAM)
				       .flatMap(expositor -> expositor.getRecentSuggestions().stream())
				       .distinct()
				       .sorted(startsWithCmpr(match).thenComparing(indexOfCmpr(match)))
				       .filter(Strings::notBlank)
				       .collect(toList());
	}
	
	private void updateView(String body, String... textsToHighlight) {
		if (body.isEmpty()) {
			tabPane.getActiveEngine().ifPresent(engine -> engine.loadContent(""));
			return;
		}
		tabPane.getActiveEngine().ifPresent(engine -> {
			setSceneCursor(Cursor.WAIT);
			String contentFmt = "<html>%n<head>%n<link rel='stylesheet' href='%s'/>%n<script src='%s'></script>%n" +
					                    "</head>%n<body>%n%s</body>%n</html>";
			String bodyContent = body;
			if (textsToHighlight != null && textsToHighlight.length > 0) {
				String highlighted = regexEscapeSymbols(Arrays.stream(textsToHighlight).collect(joining("|")), "[()]");
				// match tag's boundary or word's boundary inside tag
				String highlightedMatch = String.format("(?i)%s(?=</| )|(?<=>| )$1s", highlighted);
				if (!highlighted.isEmpty()) {
					bodyContent = wrapInTag(bodyContent, highlightedMatch, "span", CLASS_HIGHLIGHTED);
				}
			}
			bodyContent = injectAnchors(bodyContent);
			String htmlContent = String.format(contentFmt, cssHref, jsHref, bodyContent);
			engine.loadContent(htmlContent);
			tabPane.getActiveTab().getContent().requestFocus();
		});
		setSceneCursor(Cursor.DEFAULT);
	}
	
	private String injectAnchors(String body) {
		////todo: probably should split bTag content if there's a <span> inside already
		Matcher bToA = Pattern.compile("(?i)(<b>([^<>]+)</b>)").matcher(body);
		String headWord = Strings.regexSubstr("<td class=\"headword\">([^<>]+)</td>", body);
		String anchorFmt = "<a href=\"#\" onclick=\"explainHeadWord(this, " + CLASS_HIGHLIGHTED + ");\">%s</a>";
		String alteredBody = body;
		while (bToA.find()) {
			String replaced = regexEscapeSymbols(bToA.group(1), "[()]");
			String anchored = bToA.group(2);
			if (!anchored.equalsIgnoreCase(headWord)) {
				String replacement = String.format(anchorFmt, anchored);
				alteredBody = alteredBody.replaceAll(replaced, replacement);
			}
		}
		headWord = Strings.regexToNonstrictSymbols(headWord,"[()/]");
		alteredBody = String.format("<script>%s = \"%s\";</script>%s", CLASS_HIGHLIGHTED, headWord, alteredBody);
		return alteredBody;
	}
	
	private List<String> filterList(String pattern) {
		return toggleRE.isSelected()
				       ? filteredListSupplier.apply(pattern)
				       : filterCache.computeIfAbsent(pattern, filteredListSupplier);
	}
	
	private Function<String, List<String>> filteredListSupplier = pattern -> {
		String patternLC = pattern.toLowerCase();
		String validPattern = getValidPattern(composeRE(pattern), "i");
//		Predicate<String> definedPartOfSpeech = hw -> !dictionary.getPartsOfSpeech(hw).isEmpty();
//		Comparator<String> partialMatchComparator = firstComparing(definedPartOfSpeech)
//				                                            .thenComparing(startsWithCmpr(pattern))
//				                                            .thenComparing(indexOfCmpr(pattern))
//															.thenComparing(Comparator.naturalOrder());
		Comparator<String> partialMatchComparator = startsWithCmpr(pattern)
				                                            .thenComparing(indexOfCmpr(pattern))
				                                            .thenComparing(Comparator.naturalOrder());
		Predicate<String> regExMatchPredicate = s -> !toggleRE.isSelected() || s.matches(validPattern);
		return pattern.isEmpty()
				       ? getStream(fullSearchIndex.keySet(), ALLOW_PARALLEL_STREAM)
						         .sorted(Comparator.naturalOrder())
						         .collect(toList())
				       : getStream(fullSearchIndex.keySet(), ALLOW_PARALLEL_STREAM)
						         .filter(hw -> hw.toLowerCase().contains(patternLC) || hw.matches(validPattern))
						         .flatMap(hw -> hw.matches(validPattern)
								                        ? fullSearchIndex.get(hw).stream()
								                        : Stream.of(hw))
						         .filter(regExMatchPredicate)
						         .distinct()
						         .sorted(partialMatchComparator)
						         .collect(toList());
	};
	
	private Predicate<String> allMatch = s -> true;
	
	private Predicate<String> bTagWrapped = s -> s.contains("<b>");
	
	private Function<Definition, Stream<String>> hwsFromDefinition = def -> {
		if (isIndexingAborted()) return Stream.empty();
		return hwCollector(singleton(def.explanation), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Function<Definition, Stream<String>> hwsFromKnownForms = def -> {
		if (isIndexingAborted()) return Stream.empty();
		return hwCollector(def.getDefinedVocabula().getKnownForms(), allMatch, Stream::of);
	};
	
	private Function<Definition, Stream<String>> hwsFromSynonyms = def -> {
		if (isIndexingAborted()) return Stream.empty();
		return hwCollector(def.getSynonyms(), allMatch, this::extractSynonym);
	};
	
	private Stream<String> extractSynonym(String s) {
		return s.contains("(") ? extract_bTag_Wrapped(s) : Stream.of(s);
	}
	
	private Function<Definition, Stream<String>> hwsFromUseCases = def -> {
		if (isIndexingAborted()) return Stream.empty();
		return hwCollector(def.getUseCases(), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Stream<String> hwCollector(Collection<String> source,
	                                   Predicate<String> hwMatcher,
	                                   Function<String, Stream<String>> hwExtractor) {
		return source.stream().filter(hwMatcher).flatMap(hwExtractor).filter(Strings::notBlank);
	}
	
	private Stream<String> extract_bTag_Wrapped(String source) {
		Set<String> HWs = new HashSet<>();
		Matcher matcher = Pattern.compile("(?<=^|[\\s-=()])<b>([^<>]+)</b>(?=\\p{Punct}|\\s|$)").matcher(source);
		while (matcher.find()) HWs.add(matcher.group(1).trim());
		Set<String> separateHWs = HWs.stream()
				                          .filter(s -> !s.matches("\\p{Punct}}"))
				                          .flatMap(s -> Stream.of(s.split("[,.:;'\"/()\\s\\\\]"))
						                                        .filter(Strings.NOT_BLANK)
						                                        .filter(sp -> !sp.matches("\\p{Punct}"))
						                                        .distinct())
				                          .collect(toSet());
		HWs.addAll(separateHWs);
		return HWs.stream();
	}
	
	private boolean isIndexingAborted() {
		return Thread.currentThread().isInterrupted() || indexer.isCancelled();
	}
	
	private Map<String, Collection<String>> indexFor(Vocabula vocabula) {
		if (isIndexingAborted()) return emptyMap();
		return indexFor(vocabula.getDefinitions(),
				asList(hwsFromKnownForms, hwsFromDefinition, hwsFromSynonyms, hwsFromUseCases));
	}
	
	private Map<String, Collection<String>> indexFor(Collection<Definition> definitions,
	                                                 Collection<Function<Definition, Stream<String>>> hwSuppliers) {
		if (definitions.isEmpty() || isIndexingAborted()) return emptyMap();
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> combiner = (m1, m2) -> {
			Maps.mergeLeft(m1, m2, ALLOW_PARALLEL_STREAM);
		};
		BiConsumer<Map<String, Collection<String>>, Definition> accumulator = (resultMap, definition) -> {
			String definedAt = definition.getDefinedVocabula().headWord;
			Function<String, Collection<String>> keyMapper = key -> new HashSet<>();
			Collection<String> references = resultMap.computeIfAbsent(definedAt, keyMapper);
			references.add(definedAt);
			getStream(hwSuppliers, ALLOW_PARALLEL_STREAM)
					.flatMap(supplier -> supplier.apply(definition))
					.forEach(hw -> {
						String fHW = hw.matches("\\p{Lu}+|\\p{Lu}\\p{L}+(\\W\\p{Lu}\\p{L}+)+" +
								                    "|\\p{L}+(\\W\\p{L}*\\W?\\p{Lu}(\\p{L}|\\p{Lu})+)+")
								             ? hw
								             : hw.toLowerCase();
						Collection<String> refs = resultMap.computeIfAbsent(fHW, keyMapper);
						refs.add(definedAt);
						refs.add(fHW);
						references.add(fHW);
					});
		};
		return getStream(definitions, ALLOW_PARALLEL_STREAM).collect(HashMap::new, accumulator, combiner);
	}
	
	private Map<String, Collection<String>> getIndex1(Collection<Vocabula> vocabulas) {
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> accumulator = (m1, m2) -> {
			if (!isIndexingAborted())  Maps.mergeLeft(m1, m2, ALLOW_PARALLEL_STREAM);
		};
		if (isIndexingAborted()) return emptyMap();
		return getStream(vocabulas, ALLOW_PARALLEL_STREAM)
				       .map(this::indexFor)
				       .collect(HashMap::new, accumulator, accumulator);
	}
	
	private int getValueSize(Map<String, Collection<String>> m, String k) {
		return ofNullable(m.get(k)).map(Collection::size).orElse(0);
	}
	
	private Map<String, Collection<String>> getIndex2(Collection<Vocabula> vocabulas) {
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> accumulator = (m1, m2) -> {
			if (!isIndexingAborted()) Maps.merge(m1, m2, k -> {
				int size = max(getValueSize(m1, k), getValueSize(m2, k));
				return new HashSet<>(size);
			});
		};
		if (isIndexingAborted()) return emptyMap();
		return getStream(vocabulas, ALLOW_PARALLEL_STREAM)
				       .map(this::indexFor)
				       .collect(HashMap::new, accumulator, accumulator);
	}
	
	private Map<String, Collection<String>> getIndex3(Collection<Vocabula> vocabulas) {
		if (isIndexingAborted()) return emptyMap();
		return getStream(vocabulas, ALLOW_PARALLEL_STREAM)
				       .map(this::indexFor)
				       .reduce(this::accumulate)
				       .orElse(emptyMap());
	}
	
	private <K, T> Map<K, Collection<T>> accumulate(Map<K, Collection<T>> m1, Map<K, Collection<T>> m2) {
		Map<K, Collection<T>> result = Maps.clone(m1, ALLOW_PARALLEL_STREAM);
		if (isIndexingAborted()) return emptyMap();
		Maps.mergeLeft(result, m2, ALLOW_PARALLEL_STREAM);
		return result;
	}
	
	
}