package com.nicedev.vocabularizer;

import com.nicedev.gtts.sound.PronouncingService;
import com.nicedev.util.*;
import com.nicedev.util.Comparators;
import com.nicedev.vocabularizer.dictionary.model.Dictionary;
import com.nicedev.vocabularizer.dictionary.model.IndexingService;
import com.nicedev.vocabularizer.dictionary.model.Vocabula;
import com.nicedev.vocabularizer.dictionary.parser.MerriamWebsterParser;
import com.nicedev.vocabularizer.gui.*;
import com.nicedev.vocabularizer.service.DelayedTaskService;
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
import javafx.concurrent.WorkerStateEvent;
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
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.nicedev.util.Comparators.indexOfCmpr;
import static com.nicedev.util.Comparators.startsWithCmpr;
import static com.nicedev.util.Html.wrapInTag;
import static com.nicedev.util.Streams.getStream;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.*;
import static javafx.scene.input.KeyCode.INSERT;
import static javafx.scene.input.KeyCode.SPACE;

public class GUIController implements Initializable {

	static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());
	@FXML
	private ToggleButton toggleSimilar;
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
	private TableColumn<String, String> formsColumn;
	@FXML
	private Label stats;
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

	private static final String BASE_PACKAGE = "com.nicedev";
	private final String PROJECT_NAME = "Vocabularizer";
	private final String USER_HOME = System.getProperties().getProperty("user.home");
	private final String PROJECT_HOME = System.getProperty(PROJECT_NAME + ".home",
	                                                       String.format("%s\\%s", USER_HOME, PROJECT_NAME));
	private final String storageEn = String.format("%s\\%s.dict", PROJECT_HOME, "english");
	private final String HISTORY_FILENAME = PROJECT_NAME + ".history";
	private final boolean ALLOW_PARALLEL_STREAM = Boolean.valueOf(System.getProperty("allowParallelStream", "false"));

	private final int INDEXING_ALGO = Integer.valueOf(System.getProperty("indexingAlgo", "0"));
	private final int FILTER_CACHE_SIZE = Integer.valueOf(System.getProperty("fcSize", "25"));

	private final String COMPARING_DELIMITER = " | ";
	private final String ALL_MATCH = "";
	private final int LIST_FILTERING_DELAY = 500;
	private final int INPUT_FILTERING_DELAY = 400;
	private final int PRONUNCIATION_DELAY = 500;
	private static final int RECENT_QUERIES_TO_LOAD = 150;
	private static final int FILTER_DELAY = 500;

	private final String WH_REQUEST_URL = "http://wooordhunt.ru/word/%s";
	private final String GT_REQUEST_URL = "https://translate.google.com/#%s/%s/%s";
	private final String CLASS_HIGHLIGHTED = "highlighted";
	private final String cssHref;
	private final String jsHref;

	private Scene mainScene;
	private String localLanguage;
	private String foreignLanguage;
	private int updateCount = 0;
	private PronouncingService pronouncingService;
	private MerriamWebsterParser[] parsers;
	private Dictionary dictionary;
	private boolean autoPronounce = true;
	private QueuedCache<String, Collection<String>> referencesCache;
	private ObservableList<String> hwData;
	private Node relevantSelectionOwner;
	private ObservableList<String> selectedHWItems;
	private boolean filterOn = true;
	private boolean clearOnEsc = true;
	private final StringProperty queryBoxValue = new SimpleStringProperty();
	private boolean ctrlIsDown = false;
	private final DelayedTaskService<Collection<String>> queryBoxFiltering;
	private final DelayedTaskService<String> tableItemFiltering;
	private final ExecutorService executor;
	private IndexingService indexer;
	private int leastSelectedIndex;
	private int caretPos = 0;

	@SuppressWarnings("unchecked")
	public GUIController() {
		String res = getClass().getProtectionDomain().getClassLoader().getResource("view.css").toExternalForm();
		cssHref = res != null ? res : "";
		res = getClass().getProtectionDomain().getClassLoader().getResource("view.js").toExternalForm();
		jsHref = res != null ? res : "";
		tableItemFiltering = getTableFilteringTask();
		queryBoxFiltering = getQueryFiltering();
		executor = Executors.newCachedThreadPool();
	}

	private DelayedTaskService<String> getTableFilteringTask() {
		Function<String, Task<String>> tableFilterTaskProvider = pattern -> new Task<String>() {
			@Override
			protected String call() throws Exception {
				String selectedItem = hwTable.getSelectionModel().getSelectedItem();
				if (selectedItem == null) selectedItem = "";
				return isCancelled() ? queryBoxValue.get() : selectedItem;
			}
		};
		DelayedTaskService<String> task = new DelayedTaskService<>(tableFilterTaskProvider, FILTER_DELAY);
		task.setOnSucceeded(event -> {
			String selectedItem = (String) event.getSource().getValue();
			updateQueryField(selectedItem, false);
			Map<String, Collection<String>> index = indexer.getIndex();
			Collection<String> referencesAtSelected = index.getOrDefault(selectedItem, findReferences(ALL_MATCH)).stream()
					                                          .sorted(Comparators.partialMatchComparator(selectedItem, true))
					                                          .collect(toList());
			hwData.setAll(referencesAtSelected);
		});
		return task;
	}

	@SuppressWarnings("unchecked")
	private DelayedTaskService<Collection<String>> getQueryFiltering() {
		Function<String, Task<Collection<String>>> queryFilterTaskProvider = pattern -> new Task<Collection<String>>() {
			@Override
			protected Collection<String> call() throws Exception {
				return isCancelled() ? Collections.emptyList() : findReferences(pattern, toggleRE.isSelected());
			}
		};
		DelayedTaskService<Collection<String>> task = new DelayedTaskService<>(queryFilterTaskProvider,
		                                                                       INPUT_FILTERING_DELAY);
		task.setOnSucceeded(event -> {
			if (tabPane.getSelectedTabIndex() == 0) hwData.setAll((Collection<String>) event.getSource().getValue());
		});
		return task;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		pronouncingService = new PronouncingService(100, false);
		loadDictionary();
		parsers = new MerriamWebsterParser[] { new MerriamWebsterParser(dictionary, false),
		                                       new MerriamWebsterParser(dictionary, true) };
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
				LOGGER.error("Unable to create backup - {}", e.getMessage());
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
	void onLoad() {
		loadRecentQueries();
		referencesCache = new QueuedCache<>("", FILTER_CACHE_SIZE);
		initIndex();
		queryBoxValue.bind(queryBox.editorProperty().get().textProperty());
		queryBox.editorProperty().get().setOnContextMenuRequested(this::onContextMenuRequested);
		queryBox.editorProperty().get().caretPositionProperty().addListener(caretPositionChangeListener);
		queryBoxValue.addListener(queryBoxValueChangeListener);
		queryBox.focusedProperty().addListener(queryBoxFocusChangeListener);
		queryBox.showingProperty().addListener(queryBoxShowingChangeListener);
		queryBox.addEventFilter(KeyEvent.KEY_PRESSED, queryBoxEventFilter);
		queryBox.addEventFilter(KeyEvent.KEY_RELEASED, queryBoxEventFilter);
		hwData = FXCollections.observableArrayList();
		hwTable.itemsProperty().setValue(hwData);
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
		formsColumn.setCellValueFactory(formsCellValueFactory);
		mainScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> ctrlIsDown = event.isControlDown());
		mainScene.addEventFilter(KeyEvent.KEY_RELEASED, event -> ctrlIsDown = event.isControlDown());
		tabPane.focusedProperty().addListener(tabPaneFocusChangeListener);
		tabPane.getSelectionModel().selectedItemProperty().addListener(tabSelectionListener);
		queryBoxFiltering.setExecutor(executor);
		queryBoxFiltering.start();
		tableItemFiltering.setExecutor(executor);
		tableItemFiltering.start();
		updateStatistics(false);
	}

	private void initIndex() {
		EventHandler<WorkerStateEvent> onSucceededEventHandler = event -> {
			resetCache();
			hwData.setAll(findReferences(queryBoxValue.get()));
		};
		indexer = new IndexingService(dictionary, onSucceededEventHandler, ALLOW_PARALLEL_STREAM, INDEXING_ALGO,
		                              referencesCache);
		indexer.setExecutor(executor);
		indexer.start();
	}

	void stop() {
		LOGGER.info("stop: terminating app");
		LOGGER.info("stop: saving dict");
		if (updateCount != 0) saveDictionary();
		LOGGER.info("stop: canceling indexer");
		indexer.cancel();
		LOGGER.info("stop: canceling delayed filtering");
		queryBoxFiltering.cancel();
		tableItemFiltering.cancel();
		LOGGER.info("stop: terminating executor");
		executor.shutdownNow();
		LOGGER.info("stop: clearing pronouncingService");
		pronouncingService.clear();
		LOGGER.info("stop: terminating pronouncingService");
		pronouncingService.release(0);
		LOGGER.info("stop: saving recents");
		saveRecentQueries();
		LOGGER.info("terminating");
	}

	/************************************
	 *  state change listeners          *
	 ***********************************/

	private final ListChangeListener<? super String> tableSelectedItemsChangeListener = (Change<? extends String> c) -> {
		hwTable.setContextMenu(c.getList().size() > 0 ? getTableViewContextMenu() : null);
		selectedHWItems = hwTable.getSelectionModel().getSelectedItems();
	};

	private final ChangeListener<Boolean> tableFocusChangeListener = (observable, oldValue, newValue) -> {
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

	private final ChangeListener<Boolean> tabPaneFocusChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) tabPane.getActiveTab().getContent().requestFocus();
	};

	private final ChangeListener<Tab> tabSelectionListener = (observable, oldValue, newValue) -> {
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

	private final ChangeListener<Boolean> queryBoxFocusChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) {
			relevantSelectionOwner = queryBox;
			queryBox.editorProperty().get().positionCaret(caretPos);
		} else caretPos = queryBox.editorProperty().get().getCaretPosition();
		filterOn = newValue || tabPane.getSelectedTabIndex() == 0;
	};

	private final ChangeListener<Boolean> queryBoxShowingChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) clearOnEsc = false;
	};

	private final ChangeListener<Number> caretPositionChangeListener = (observable, oldValue, newValue) -> {
		caretPos = newValue.intValue();
	};

	private final ChangeListener<String> queryBoxValueChangeListener = (observable, oldValue, newValue) -> {
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
				LOGGER.error("queryBoxLisener: unexpected exception - {} {}", e,
				             Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
			}
		}

	};

	private void engageFiltering(String pattern, int delay) {
		queryBoxFiltering.setFilter(pattern);
		queryBoxFiltering.setDelay(delay);
	}

	private final Callback<TableColumn<String, String>, TableCell<String, String>> filteredCellFactory =
			param -> new FilteredTableCell(queryBoxValue);

	private final Callback<CellDataFeatures<String, String>, ObservableValue<String>> posCellValueFactory =
			param -> new ReadOnlyObjectWrapper<>(dictionary.getPartsOfSpeech(param.getValue()).stream()
					                                     .filter(PoS -> PoS.partName.matches("[\\p{Lower} ,]+"))
					                                     .map(PoS -> PoS.partName)
					                                     .collect(joining(", ")));

	private final Callback<CellDataFeatures<String, String>, ObservableValue<String>> formsCellValueFactory =
			param -> new ReadOnlyObjectWrapper<>(dictionary.getVocabula(param.getValue())
					                                     .map(v -> v.getKnownForms().stream()
							                                               .distinct()
							                                               .collect(joining(", ")))
					                                     .orElse(""));

	private final Callback<CellDataFeatures<String, String>, ObservableValue<String>> hwCellValueFactory =
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
		if (event.getCode() == INSERT) engageTableSelectedItemFiltering();
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
			hwData.setAll(findReferences(ALL_MATCH));
			updateTableState(false);
		} catch (Exception e) {
			LOGGER.error("resetFilter: unexpected exception - {} | {}", e, Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
		}
	}

	@FXML
	private void onWebViewKeyReleased(KeyEvent event) {
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

	private final EventHandler<KeyEvent> queryBoxEventFilter = event -> {
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
		hwData.setAll(findReferences(queryBoxValue.getValue()));
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
				if (mainTabActive) handleQuery(selection);
				else handleQuery(selection, tabPane.getActiveTab().getText());
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
	private void onContextMenuRequested(ContextMenuEvent e) {
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

	private void resetCache() {
		Platform.runLater(() -> {
			referencesCache.clear();
			referencesCache.putPersistent(findReferences(ALL_MATCH));
			try {
				ofNullable(mainTab.getText()).filter(Strings::isBlank).ifPresent(val -> hwData.setAll(findReferences(val)));
			} catch (Exception e) {
				LOGGER.error("reset cache: unexpected exception - {} | {}", e,
				             Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
			}
		});
	}

	private void updateTableState() {
		updateTableState(true);
	}

	private void updateTableState(boolean searching) {
		Platform.runLater(() -> {
			String failedSearchMarker = " ";
			hwTable.refresh();
			hwTable.setPlaceholder(searching ? SEARCHING
					                       : queryBoxValue.getValue().endsWith(failedSearchMarker)
							                         ? NOTHING_FOUND : NO_MATCH);
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
			LOGGER.error("engageTableFiltering: unexpected exception - {}%n{}", e,
			             Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
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
//			log("jsBridge: jsHandleQuery: requested %s", headWord);
			// remove any tags
			headWord = headWord.replaceAll("<[^<>]+>", "");
			handleQuery(headWord, highlight);
		}

		@SuppressWarnings("unused")
		public void jsHandleQuery(String headWord) {
//			log("jsBridge: jsHandleQuery: requested %s", headWord);
			handleQuery(headWord);
		}
	}

	private final Consumer<WebViewTab> viewHndlrsSetter = tab -> {
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
		tab.setOnClosed(event -> pronouncingService.clear());
	};

	private final BiFunction<String, String[], WebViewTab> expositorTabSupplier = (title, highlights) -> {
		WebViewTab newTab;
		if (title.contains(COMPARING_DELIMITER)) {
			newTab = new ExpositorTab(title);
			newTab.setOnSelected(comparedFirstSelectionListener());
		} else {
			newTab = new ExpositorTab(title, dictionary.findVocabula(title));
			newTab.setOnSelected(vocabulaFirstSelectionHandler(highlights));
		}
		newTab.addHandlers(viewHndlrsSetter);
		return newTab;
	};

	private Consumer<WebViewTab> vocabulaFirstSelectionHandler(String[] highlights) {
		return (tab) -> {
			if (tab.firstSelection()) {
				Optional<Vocabula> optVocabula = getAssignedVocabula(tab);
				String headWord = optVocabula.map(voc -> voc.headWord).orElse(tab.getTooltip().getText());
				if (!optVocabula.isPresent()) {
					handleQuery(headWord);
					updateQueryField(headWord, false);
				} else {
					render(Collections.singletonList(optVocabula.get()), highlights);
					pronounce(optVocabula.get());
				}
			}
		};
	}

	private Consumer<WebViewTab> comparedFirstSelectionListener() {
		return tab -> {
			if (tab.firstSelection()) {
				String title = tab.getTooltip().getText();
				String[] headwords = title.split(Strings.escapeSymbols(COMPARING_DELIMITER, "|"));
				Collection<Vocabula> compared = Stream.of(headwords)
						                                .map(String::trim)
						                                .map(s -> dictionary.containsVocabula(s)
								                                          ? dictionary.findVocabula(s)
								                                          : indexer.getReferencingVocabula(s))
						                                .filter(Optional::isPresent)
						                                .map(Optional::get)
						                                .collect(toList());
				Collection<String> availableCompared = compared.stream().map(v -> v.headWord).collect(toList());
				String[] highlights = Stream.of(headwords)
						                      .filter(s -> !availableCompared.contains(s))
						                      .collect(toList()).toArray(new String[0]);
				render(compared, highlights);
			}
		};
	}

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
		Platform.runLater(() -> {
			boolean filterState = filterOn;
			filterOn = filterActive;
			if (filterActive) {
				if (tabPane.getSelectedTabIndex() > 0) tabPane.selectTab(mainTab);
				if (relevantSelectionOwner != queryBox) queryBox.requestFocus();
				mainTab.setText(text);
			}
			int textLength = queryBoxValue.getValue().length();
			int newCaretPos = caretPos == textLength ? text.length() : caretPos == 0 ? caretPos + 1 : caretPos;
			synchronized (queryBox) {
				queryBox.editorProperty().getValue().setText(text);
				queryBox.editorProperty().get().positionCaret(newCaretPos);
				caretPos = queryBox.editorProperty().get().getCaretPosition();
			}
			filterOn = filterState;
		});
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

	private final Function<String, String> requestFormatterGT = s -> String.format(GT_REQUEST_URL, foreignLanguage,
	                                                                               localLanguage, s);

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void appendContextMenuItems(Object contextMenu, Optional<String> context) {
		EventHandler<ActionEvent> handleQueryEH = this::onSearch;
		GUIUtil.addMenuItem("Explain", handleQueryEH, contextMenu);
		// headwords comparing available only from tableView
		boolean appendingToTableViewContextMenu = contextMenu instanceof ContextMenu;
		if (appendingToTableViewContextMenu && selectedHWItems.size() > 1) {
			EventHandler<ActionEvent> compareHeadwordsEH = e -> compareHeadwords();
			GUIUtil.addMenuItem("Compare", compareHeadwordsEH, contextMenu);
		}
		// default translation behavior is not available in compare mode
		if (!context.filter(s -> s.contains("|")).isPresent()) {
			EventHandler<ActionEvent> showGTTranslationEH = e -> showTranslation(context, requestFormatterGT);

			final Function<String, String> requestFormatterWH = s -> String.format(WH_REQUEST_URL, s);
			EventHandler<ActionEvent> showWHTranslationEH = e -> showTranslation(context, requestFormatterWH);

			Menu translations = new Menu("Translate");
			GUIUtil.addMenuItem(" via Google", showGTTranslationEH, translations);
			GUIUtil.addMenuItem(" via WooordHunt", showWHTranslationEH, translations);
			GUIUtil.addMenuItem(translations, contextMenu);
		}

		EventHandler<ActionEvent> pronounceHeadwordEH = e -> pronounce(context);
		EventHandler<ActionEvent> pronounceArticleEH = e -> pronounce(context, true);

		boolean showExtendedPronounceMenu = !appendingToTableViewContextMenu && tabPane.getActiveTab() instanceof ExpositorTab;
		if (showExtendedPronounceMenu) {
			Menu pronounce = new Menu("Pronounce");
			GUIUtil.addMenuItem(" headword", pronounceHeadwordEH, pronounce);
			GUIUtil.addMenuItem(" article", pronounceArticleEH, pronounce);
			GUIUtil.addMenuItem(pronounce, contextMenu);
		} else {
			GUIUtil.addMenuItem("Pronounce", pronounceHeadwordEH, contextMenu);
		}

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

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
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
			LOGGER.error(e.getMessage());
		}
		return encoded;
	}

	private void saveRecentQueries() {
		try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(PROJECT_HOME, HISTORY_FILENAME), CREATE,
		                                                 TRUNCATE_EXISTING)) {
			synchronized (queryBox) {
				String history = queryBox.getItems().stream()
						                 .distinct()
						                 .filter(Strings::notBlank)
						                 .collect(joining("\n"));
				bw.write(history);
			}
		} catch (IOException e) { /* NOP */ }
	}

	private void loadRecentQueries() {
		try (BufferedReader br = Files.newBufferedReader(Paths.get(PROJECT_HOME, HISTORY_FILENAME))) {
			br.mark(1_000_000);
			br.reset();
			queryBox.getItems().setAll(br.lines().limit(RECENT_QUERIES_TO_LOAD).collect(toList()));
		} catch (IOException | ClassCastException e) { /*NOP*/ }
	}

	private void handleQuery(String query, String... textsToHighlight) {
		String validatedQuery = removeGaps(query);
		if (validatedQuery.isEmpty()) return;
		boolean queriedTabPresent = tabPane.trySelectTab(ExpositorTab.sameTab(validatedQuery))
				                            && getAssignedVocabula(tabPane.getActiveTab()).isPresent();
		if (!queriedTabPresent) {
			updateTableState();
			setSceneCursor(Cursor.WAIT);
			executor.submit(getQueryHandlingTask(validatedQuery, textsToHighlight));
		}
	}

	private Runnable getQueryHandlingTask(String query, String... textsToHighlight) {
		return () -> {
			boolean changeOccurred = false;
			try {
				int defCount = dictionary.getDefinitionsCount();
				boolean acceptSimilar = query.startsWith("~");
				// skip dictionary querying if "~" flag present
				Optional<Vocabula> optVocabula = acceptSimilar ? Optional.empty() : dictionary.findVocabula(query);
				final String fQuery = query.replaceAll("~", "").trim();
				if (optVocabula.isPresent()) {
					showQueryResult(optVocabula.get(), textsToHighlight);
					appendRequestHistory(fQuery);
				} else {
					acceptSimilar |= toggleSimilar.isSelected();
					Set<Vocabula> vocabulas = findVocabula(fQuery, acceptSimilar);
					Function<Set<Vocabula>, Set<Vocabula>> availableOf = vocabulaSet -> {
						return dictionary.getVocabulas(vocabulaSet.stream().map(voc -> voc.headWord).collect(toSet()));
					};
					Set<Vocabula> availableVs = availableOf.apply(vocabulas);
					int nAdded = dictionary.addVocabulas(vocabulas);
					if (!vocabulas.isEmpty() && nAdded > 0) {
						updateCount++;
						Set<Vocabula> addedVs = availableOf.apply(vocabulas);
						addedVs.removeAll(availableVs);
						List<String> addedHWs = addedVs.stream().map(v -> v.headWord).collect(toList());
						Platform.runLater(() -> {
							hwData.setAll(addedHWs);
							indexer.alter(addedVs);
						});
						if (nAdded == 1 && addedHWs.contains(fQuery)) {
							showQueryResult(addedVs.iterator().next(), textsToHighlight);
						} else {
							Platform.runLater(() -> {
								tabPane.removeTab(fQuery);
								tabPane.selectTab(mainTab);
							});
						}
						updateQueryField(fQuery, false);
						appendRequestHistory(fQuery);
						resetCache();
					} else {
						List<String> suggestions = getSuggestions(fQuery);
						boolean lookForReferencingVocabula = false;
						if (!suggestions.isEmpty()) {
							Platform.runLater(() -> hwData.setAll(suggestions));
							String suggested = suggestions.get(0);
							if (suggestions.size() == 1 && Strings.partEquals(suggested, fQuery)) {
								dictionary.findVocabula(suggested).ifPresent(vocabula -> showQueryResult(vocabula, fQuery));
							} else {
								lookForReferencingVocabula = true;
							}
						} else {
							lookForReferencingVocabula = true;
						}
						if (!lookForReferencingVocabula || !showReferencingVocabula(fQuery)) {
							updateQueryField(lookForReferencingVocabula ? fQuery.concat(" ") : fQuery, false);
							if (suggestions.isEmpty())
								Platform.runLater(() -> showTranslation(Optional.of(fQuery), requestFormatterGT));
						}
					}
				}
				if (changeOccurred = (defCount != dictionary.getDefinitionsCount())) updateStatistics(false);
			} catch (Exception e) {
				LOGGER.error(Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
			} finally {
				if (changeOccurred) saveDictionary();
				updateTableState(false);
				setSceneCursor(Cursor.DEFAULT);
			}
		};
	}

	private boolean showReferencingVocabula(String referenced) {
		Task<Optional<Vocabula>> task = new Task<Optional<Vocabula>>() {
			@Override
			protected Optional<Vocabula> call() throws Exception {
				return indexer.getReferencingVocabula(referenced);
			}
		};
		Platform.runLater(task);
		Optional<Vocabula> referencingVocabula;
		try {
			referencingVocabula = task.get();
		} catch (InterruptedException | ExecutionException e) {
			LOGGER.warn("Error has occurred showing referencing vocabula: {} {}",
			            e, Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
			return false;
		}
		if (referencingVocabula.isPresent()) {
			showQueryResult(referencingVocabula.get(), referenced);
			appendRequestHistory(referenced);
			return true;
		}
		return referencingVocabula.isPresent();
	}

	@SuppressWarnings("unused")
	private boolean saveDictionary() {
		saveRecentQueries();
		return Dictionary.save(dictionary, storageEn);
	}

	private void appendRequestHistory(String query) {
		Platform.runLater(() -> {
			if (Strings.isBlank(query)) return;
			boolean oldState = filterOn;
			filterOn = false;
			int index;
			while ((index = queryBox.getItems().indexOf(query)) >= 0) queryBox.getItems().remove(index);
			queryBox.getItems().add(0, query);
			filterOn = oldState;
			queryBox.getSelectionModel().select(0);
		});
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
			render(Collections.singletonList(vocabula), textsToHighlight);
			pronounce(vocabula);
			updateQueryField(vocabula.headWord, false);
		}
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void deleteHeadword(Optional<String> selection) {
		Collection<String> headwords = selection
				                               .map(s -> Stream.of(s.split("[\\n\\r\\f]"))
						                                         .filter(Strings::notBlank)
						                                         .collect(toList()))
				                               .orElse(selectedHWItems).stream()
				                               .collect(toList());
		int count = headwords.size();
		if (count == 0) return;
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, getDeleteMsg(count), ButtonType.YES, ButtonType.NO);
		GUIUtil.hackAlert(confirm);
		confirm.showAndWait().filter(response -> response == ButtonType.YES).ifPresent(response -> {
			if (tabPane.getSelectedTabIndex() > 0 && headwords.contains(tabPane.getActiveTab().getText()))
				tabPane.removeActiveTab();
			getDeletionTask(headwords).run();
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
					saveDictionary();
					hwData.removeAll(deleted);
					updateStatistics(true);
					if (tabPane.getTabs().size() == 1) updateQueryField(queryBoxValue.getValue(), true);
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
		Platform.runLater(() -> {
			stats.setText(dictionary.toString());
			if (invalidateIndex) indexer.restart();
		});
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
		return Stream.of(parsers)
				       .parallel()
				       .map(parser -> new Object[] { parser.priority, parser.getVocabula(query, acceptSimilar) })
				       .sorted(Comparator.comparingInt(tuple -> (int) tuple[0]))
				       .flatMap(tuple -> ((Collection<Vocabula>) tuple[1]).stream())
				       .collect(LinkedHashSet::new, Set::add, Set::addAll);
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void pronounce(Optional<String> selection, boolean isArticle) {
		pronouncingService.clear();
		if (!isArticle) {
			pronounce(selection);
			return;
		}
		selection.ifPresent(s -> {
			Optional<Vocabula> optVocabula = dictionary.findVocabula(s);
			pronouncingService.clear();
			optVocabula.ifPresent(vocabula -> {
				Platform.runLater(() -> pronouncingService.pronounce(vocabula.toString().replaceAll("[<>]", "")));
			});
		});
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void pronounce(Optional<String> selection) {
		pronouncingService.clear();
		Platform.runLater(() -> {
			pronouncingService.pronounce(selection.orElse(selectedHWItems.stream().collect(joining("\n"))),
			                             PRONUNCIATION_DELAY);
		});
	}

	private void pronounce(Vocabula vocabula) {
		if (!autoPronounce) return;
		pronouncingService.clear();
		Platform.runLater(() -> {
			Iterator<String> sIt = vocabula.getPronunciationSources().iterator();
			if (sIt.hasNext()) pronouncingService.pronounce(sIt.next(), PRONUNCIATION_DELAY);
			else pronouncingService.pronounce(vocabula.headWord, PRONUNCIATION_DELAY);
		});

	}

	private List<String> getSuggestions(String match) {
		return getStream(asList(parsers), ALLOW_PARALLEL_STREAM)
				       .flatMap(parser -> parser.getRecentSuggestions().stream())
				       .distinct()
				       .sorted(startsWithCmpr(match).thenComparing(indexOfCmpr(match)))
				       .filter(Strings::notBlank)
				       .collect(toList());
	}

	private void render(Collection<Vocabula> vocabulas, String... textsToHighlight) {
		LOGGER.debug("rendering {} with highlight {}",
		             com.nicedev.util.Collections.toString(vocabulas, v -> v.headWord), Arrays.toString(textsToHighlight));
		if (vocabulas.isEmpty()) {
			tabPane.getActiveEngine().ifPresent(engine -> engine.loadContent(""));
			return;
		}
		tabPane.getActiveEngine().ifPresent(engine -> {
			setSceneCursor(Cursor.WAIT);
			String contentFmt = "<html>%n<head>%n<link rel='stylesheet' href='%s'/>%n<script src='%s'></script>%n" +
					                    "</head>%n<body>%n%s</body>%n</html>";
			String bodyContent;
			if (vocabulas.size() == 1) {
				bodyContent = vocabulas.iterator().next().toHTML();
			} else {
				String comparing = vocabulas.stream().map(Vocabula::toHTML).collect(joining("</td><td width='50%'>"));
				bodyContent = String.format("<table><tr><td width='50%%'>%s</td></tr></table>", comparing);
			}
			bodyContent = highlight(bodyContent, textsToHighlight);
			bodyContent = injectAnchors(bodyContent);
			LOGGER.debug("body content:\n{}", bodyContent);
			String htmlContent = String.format(contentFmt, cssHref, jsHref, bodyContent);
			LOGGER.debug("resulting content:\n{}", htmlContent);
			engine.loadContent(htmlContent);
			tabPane.getActiveTab().getContent().requestFocus();
		});
		setSceneCursor(Cursor.DEFAULT);
	}

	private String highlight(String bodyContent, String[] textsToHighlight) {
		if (textsToHighlight == null || textsToHighlight.length == 0) return bodyContent;
		String highlighted = Stream.of(textsToHighlight)
				                     .map(s -> String.format("(?:%s)", Strings.escapeSymbols(s, "[()]")))
				                     .collect(joining("|"));
		if (highlighted.isEmpty()) return bodyContent;
		highlighted = String.format("(%s)", highlighted);
		// match tag's boundary or word's boundary inside tag
		String highlightedMatch = String.format("(?i)(?:%s(?=([^<>]+)?</b>))|(?:(?<=<b>([^<>])?)%1$s)", highlighted);
		bodyContent = wrapInTag(bodyContent, highlightedMatch, "span", CLASS_HIGHLIGHTED);
		return bodyContent;
	}

	private String injectAnchors(String body) {
		////todo: probably should split bTag content if there's a <span> inside already
		Matcher bToA = Pattern.compile("(?i)(<b>([^<>]+)</b>)").matcher(body);
		String headWord = Strings.regexSubstr("<td class=\"headword\">([^<>]+)</td>", body);
		String anchorFmt = "<a href=\"#\" onclick=\"explainHeadWord(this, " + CLASS_HIGHLIGHTED + ");\">%s</a>";
		String alteredBody = body;
		while (bToA.find()) {
			String replaced = Strings.escapeSymbols(bToA.group(1), "[()]");
			String anchored = bToA.group(2);
			if (!anchored.equalsIgnoreCase(headWord)) {
				String replacement = String.format(anchorFmt, anchored);
				alteredBody = alteredBody.replaceAll(replaced, replacement);
			}
		}
		headWord = Strings.toNonstrictSymbolsRegex(headWord, "[()/]");
		alteredBody = String.format("<script>%s = \"%s\";</script>%s", CLASS_HIGHLIGHTED, headWord, alteredBody);
		return alteredBody;
	}

	private Collection<String> findReferences(String pattern) {
		return findReferences(pattern, toggleRE.isSelected());
	}

	private Collection<String> findReferences(String pattern, boolean isRegex) {
		Function<String, Collection<String>> referencesFinder = pat -> indexer.getReferencesFinder().apply(pat, isRegex);
		return isRegex
				       ? referencesFinder.apply(pattern)
				       : referencesCache.computeIfAbsent(pattern, referencesFinder);
	}

}