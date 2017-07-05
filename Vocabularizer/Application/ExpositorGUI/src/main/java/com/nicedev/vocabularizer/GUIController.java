package com.nicedev.vocabularizer;

import com.nicedev.gtts.service.TTSPlaybackService;
import com.nicedev.gtts.service.TTSService;
import com.nicedev.util.*;
import com.nicedev.util.Comparators;
import com.nicedev.vocabularizer.dictionary.model.Dictionary;
import com.nicedev.vocabularizer.dictionary.model.IndexingService;
import com.nicedev.vocabularizer.dictionary.model.Vocabula;
import com.nicedev.vocabularizer.dictionary.parser.MerriamWebsterParser;
import com.nicedev.vocabularizer.gui.*;
import com.nicedev.vocabularizer.service.DelayedFilteringTaskService;
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
import javafx.event.*;
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
import java.util.function.Supplier;
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

	static final String BASE_PACKAGE = "com.nicedev";
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
	private final int EXPLAIN_SELECTION_DELAY = 500;
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
	private int updatesCount = 0;
	private TTSService pronouncingService;
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
	private final DelayedFilteringTaskService<Collection<String>> queryBoxFiltering;
	private final DelayedFilteringTaskService<String> tableItemFiltering;
	private final DelayedTaskService<String> explainSelection;
	private final ExecutorService executor;
	private IndexingService indexer;
	private int leastSelectedIndex;
	private int caretPos = 0;
	private boolean ctrlIsDown = false;
	private boolean commandCtrl = false;
	private boolean mouseSelectionMode = false;

	@SuppressWarnings("unchecked")
	public GUIController() {
		String res = getClass().getProtectionDomain().getClassLoader().getResource("view.css").toExternalForm();
		cssHref = res != null ? res : "";
		res = getClass().getProtectionDomain().getClassLoader().getResource("view.js").toExternalForm();
		jsHref = res != null ? res : "";
		tableItemFiltering = getTableFilteringTask();
		queryBoxFiltering = getQueryFilteringTask();
		explainSelection = getExplainSelectionTask();
		executor = Executors.newCachedThreadPool();
	}

	private DelayedFilteringTaskService<String> getTableFilteringTask() {
		Function<String, Task<String>> tableFilterTaskProvider = pattern -> new Task<String>() {
			@Override
			protected String call() throws Exception {
				String selectedItem = hwTable.getSelectionModel().getSelectedItem();
				if (selectedItem == null) selectedItem = "";
				return isCancelled() ? queryBoxValue.get() : selectedItem;
			}
		};
		DelayedFilteringTaskService<String> filteringTask =
				new DelayedFilteringTaskService<>(tableFilterTaskProvider, FILTER_DELAY);
		filteringTask.setOnSucceeded(event -> {
			String selectedItem = (String) event.getSource().getValue();
			updateQueryBoxText(selectedItem, false);
			Map<String, Collection<String>> index = indexer.getIndex();
			Collection<String> referencesAtSelected = index.getOrDefault(selectedItem, findReferences(ALL_MATCH)).stream()
					                                          .sorted(Comparators.partialMatchComparator(selectedItem, true))
					                                          .collect(toList());
			hwData.setAll(referencesAtSelected);
		});
		return filteringTask;
	}

	@SuppressWarnings("unchecked")
	private DelayedFilteringTaskService<Collection<String>> getQueryFilteringTask() {
		Function<String, Task<Collection<String>>> queryFilterTaskProvider = pattern -> new Task<Collection<String>>() {
			@Override
			protected Collection<String> call() throws Exception {
				return isCancelled() ? Collections.emptyList() : findReferences(pattern, toggleRE.isSelected());
			}
		};
		DelayedFilteringTaskService<Collection<String>> filteringTask =
				new DelayedFilteringTaskService<>(queryFilterTaskProvider, INPUT_FILTERING_DELAY);
		filteringTask.setOnSucceeded(event -> {
			if (tabPane.getSelectedTabIndex() == 0) hwData.setAll((Collection<String>) event.getSource().getValue());
		});
		return filteringTask;
	}

	private DelayedTaskService<String> getExplainSelectionTask() {
		Supplier<Task<String>> explainTaskSupplier = () -> new Task<String>() {
			@Override
			protected String call() throws Exception {
				Task<String> selectedTextTask = getSelectedTextTask();
				Platform.runLater(selectedTextTask);
				return isCancelled() ? "" : selectedTextTask.get();
			}
		};
		DelayedTaskService<String> explainTask = new DelayedTaskService<>(explainTaskSupplier, EXPLAIN_SELECTION_DELAY);
		explainTask.setOnSucceeded(event -> {
			String selection = (String) event.getSource().getValue();
			if (!selection.isEmpty() && !mouseSelectionMode) handleQuery(selection);
		});
		return explainTask;
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		pronouncingService = new TTSPlaybackService(100);
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
		tabPane.focusedProperty().addListener(tabPaneFocusChangeListener);
		tabPane.getSelectionModel().selectedItemProperty().addListener(tabSelectionListener);
		queryBoxFiltering.setExecutor(executor);
		queryBoxFiltering.start();
		tableItemFiltering.setExecutor(executor);
		tableItemFiltering.start();
		explainSelection.setExecutor(executor);
		explainSelection.start();
		mainScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> ctrlIsDown = event.isControlDown());
		mainScene.addEventFilter(KeyEvent.KEY_RELEASED, event -> ctrlIsDown = event.isControlDown());
		updateStatistics(false);
	}

	private void initIndex() {
		EventHandler<WorkerStateEvent> onSucceededEventHandler = event -> {
			resetCache();
			String filter = queryBoxValue.get();
			hwData.setAll(findReferences(filter));
		};
		indexer = new IndexingService(dictionary, onSucceededEventHandler, ALLOW_PARALLEL_STREAM, INDEXING_ALGO,
		                              referencesCache);
		indexer.setExecutor(executor);
		indexer.start();
	}

	void stop() {
		LOGGER.info("stop: terminating app");
		LOGGER.info("stop: saving dict");
		if (updatesCount != 0) saveDictionary();
		LOGGER.info("stop: canceling indexer");
		indexer.cancel();
		LOGGER.info("stop: canceling delayed filtering");
		queryBoxFiltering.cancel();
		tableItemFiltering.cancel();
		LOGGER.info("stop: terminating executor");
		executor.shutdownNow();
		LOGGER.info("stop: clearing pronouncingService");
		pronouncingService.release();
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
		filterOn = tabPane.getSelectedTabIndex() == 0;
		String caption = filterOn ? newValue.getText() : newValue.getTooltip().getText();
		updateQueryBoxText(caption, false);
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
		commandCtrl = event.isControlDown();
	}

	@FXML
	protected void onTableMouseReleased(@SuppressWarnings("unused") MouseEvent event) {
		tableItemFiltering.cancel();
	}

	@FXML
	protected void onTableViewKeyPressed(KeyEvent event) {
		if (event.getCode() == INSERT) engageTableSelectedItemFiltering();
		commandCtrl = event.isControlDown();
	}

	@FXML
	protected void onTableViewKeyReleased(KeyEvent event) {
		LOGGER.debug("onTableViewKeyReleased");
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
				deleteHeadwords();
				break;
			case ESCAPE:
				resetFilter();
				break;
			default:
				if (!commandCtrl && event.getText().matches("[\\p{Graph}]")) {
					updateQueryBoxText(event.getText());
				} else if (commandCtrl && event.getCode() == INSERT) {
					tableItemFiltering.cancel();
				}
		}
		event.consume();
	}

	private void resetFilter() {
		try {
			filterOn = true;
			updateQueryBoxText(ALL_MATCH);
			queryBox.getSelectionModel().clearSelection();
			hwData.setAll(findReferences(ALL_MATCH));
			updateTableState(false);
		} catch (Exception e) {
			LOGGER.error("resetFilter: unexpected exception - {} | {}", e, Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
		}
	}

	private void onWebViewKeyPressed(KeyEvent keyEvent) {
		commandCtrl = keyEvent.isControlDown();
	}

	private void onWebViewKeyReleased(KeyEvent event) {
		LOGGER.debug("onWebViewKeyReleased");
		if (event.getEventType() != KeyEvent.KEY_RELEASED) return;
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
				WebViewTab activeTab = (WebViewTab) tabPane.getActiveTab();
				double x = activeTab.getCursorPos().getValue().getX();
				double y = activeTab.getCursorPos().getValue().getY();
				Event.fireEvent((EventTarget) event.getSource(),
				                new MouseEvent(MouseEvent.MOUSE_RELEASED, x, y, 0, 0,
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
	protected void onQueryBoxKeyPressed(KeyEvent event) {
		commandCtrl = event.isControlDown();
	}

	@FXML
	protected void onQueryBoxKeyReleased(KeyEvent event) {
		LOGGER.debug("onQueryBoxKeyReleased");
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
				break;
			case W:
				if (event.isControlDown()) {
					tabPane.removeActiveTab();
				}
				break;
			default:
				if (!event.isControlDown() && event.getText().matches("[\\p{Graph} ]")) {
					mainTab.setText(queryBoxValue.getValue());
					updateQueryBoxText(queryBoxValue.getValue());
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
		pronouncingService.clearQueue();
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
		if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
			if (tabPane.getActiveTab() instanceof TranslationTab) return;
			if (tabPane.getSelectedTabIndex() == 0) {
				selectedHWItems.stream().findFirst().ifPresent(this::handleQuery);
			}
		}
		mouseSelectionMode = true;
		event.consume();
	}

	@FXML
	protected void onSearch(@SuppressWarnings("unused") ActionEvent event) {
		updateTableState();
		Node source = relevantSelectionOwner;
		commandCtrl = true;
		Event.fireEvent(source, new KeyEvent(source, source, KeyEvent.KEY_RELEASED, "", "", KeyCode.ENTER,
		                                     false, commandCtrl, false, false));
	}

	@FXML
	private void onContextMenuRequested(ContextMenuEvent e) {
		Node source = (Node) e.getSource();
		Optional<String> context = Optional.empty();
		if (source instanceof WebView) {
			context = tabPane.getActiveViewSelection();
		}
		GUIUtil.updateContextMenu(context, this::appendContextMenuItems);
	}

	/*************************************
	 *  utility methods                  *
	 ************************************/

	private void resetCache() {
		Runnable resetTask = () -> {
			referencesCache.clear();
			referencesCache.putPersistent(findReferences(ALL_MATCH));
			try {
				ofNullable(mainTab.getText()).ifPresent(val -> hwData.setAll(findReferences(val)));
			} catch (Exception e) {
				LOGGER.error("clearQueue cache: unexpected exception - {} | {}", e,
				             Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
			}
		};
		if (Platform.isFxApplicationThread()) {
			resetTask.run();
		} else {
			Platform.runLater(resetTask);
		}
	}

	private void updateTableState() {
		updateTableState(true);
	}

	private void updateTableState(boolean searching) {
		Runnable taskUpdate = () -> {
			String failedSearchMarker = " ";
			hwTable.refresh();
			hwTable.setPlaceholder(searching ? SEARCHING
					                       : queryBoxValue.getValue().endsWith(failedSearchMarker)
							                         ? NOTHING_FOUND : NO_MATCH);
			hwTable.refresh();
		};
		if (Platform.isFxApplicationThread()) {
			taskUpdate.run();
		} else
			Platform.runLater(taskUpdate);
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
		Runnable taskSet = () -> {
			tabPane.setCursor(cursor);
			tabPane.getActiveTab().getContent().setCursor(cursor);
		};
		if (Platform.isFxApplicationThread()) {
			taskSet.run();
		} else
			Platform.runLater(taskSet);
	}


	public class JSBridge {
		@SuppressWarnings("unused")
		public void jsHandleQuery(String headWord, String highlight) {
			// remove any tags
			LOGGER.debug("jsHandleQuery");
			headWord = headWord.replaceAll("<[^<>]+>", "");
			handleQuery(headWord, highlight);
		}

		@SuppressWarnings("unused")
		public void jsHandleQuery(String headWord) {
			LOGGER.debug("jsHandleQuery");
			handleQuery(headWord);
		}
	}

	private final Consumer<WebViewTab> viewHandlersSetter = tab -> {
		WebView view = tab.getView();
		view.setOnKeyReleased(this::onWebViewKeyReleased);
		view.setOnKeyPressed(this::onWebViewKeyPressed);
		view.setOnMouseClicked(this::onMouseClicked);
		view.setOnMousePressed(this::onWebViewMousePressed);
		view.setOnMouseReleased(this::onWebViewMouseReleased);
		view.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onWebViewMouseDragged);
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
		tab.setOnClosed(event -> pronouncingService.clearQueue());
	};

	private void onWebViewMouseDragged(MouseEvent mouseEvent) {
		mouseSelectionMode = mouseEvent.isPrimaryButtonDown();
		explainSelection.cancel();
		mouseEvent.consume();
	}

	private void onWebViewMousePressed(MouseEvent mouseEvent) {
		mouseSelectionMode = false;
		if (!mouseEvent.isPrimaryButtonDown()) return;
		commandCtrl = mouseEvent.isControlDown();
		if (!mouseSelectionMode) explainSelection.restart();
		mouseEvent.consume();
	}

	private void onWebViewMouseReleased(MouseEvent mouseEvent) {
		mouseSelectionMode = false;
		mouseEvent.consume();
	}

	private final BiFunction<String, String[], WebViewTab> expositorTabSupplier = (title, highlights) -> {
		WebViewTab newTab;
		if (title.contains(COMPARING_DELIMITER)) {
			newTab = new ExpositorTab(title);
			newTab.setOnSelected(comparedFirstSelectionListener());
		} else {
			newTab = new ExpositorTab(title, dictionary.findVocabula(title));
			newTab.setOnSelected(vocabulaFirstSelectionHandler(highlights));
		}
		newTab.addHandlers(viewHandlersSetter);
		return newTab;
	};

	private Consumer<WebViewTab> vocabulaFirstSelectionHandler(String[] highlights) {
		return (tab) -> {
			if (tab.firstSelection()) {
				Optional<Vocabula> optVocabula = getAssignedVocabula(tab);
				String headWord = optVocabula.map(voc -> voc.headWord).orElse(tab.getTooltip().getText());
				if (!optVocabula.isPresent()) {
					LOGGER.debug("vocabulaFirstSelectionHandler::vocabulaNotPresent");
					handleQuery(headWord);
					updateQueryBoxText(headWord, false);
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
			newTab.addHandlers(viewHandlersSetter);
			return newTab;
		};
	}

	private void updateQueryBoxText(String text) {
		updateQueryBoxText(text, true);
	}

	private void updateQueryBoxText(String text, boolean filterActive) {
		Runnable taskUpdate = () -> {
			boolean filterState = filterOn;
			filterOn = filterActive;
			if (filterActive) {
				if (tabPane.getSelectedTabIndex() > 0) tabPane.selectTab(mainTab);
				if (relevantSelectionOwner != queryBox) queryBox.requestFocus();
			}
			int textLength = queryBoxValue.getValue().length();
			int newCaretPos = caretPos == textLength ? text.length() : caretPos == 0 ? caretPos + 1 : caretPos;
			queryBox.editorProperty().getValue().setText(text);
			queryBox.editorProperty().get().positionCaret(newCaretPos);
			caretPos = queryBox.editorProperty().get().getCaretPosition();
			filterOn = filterState;
		};
		if (Platform.isFxApplicationThread()) {
			taskUpdate.run();
		} else Platform.runLater(taskUpdate);
	}

	private String getSelectedText() {
		String selection = "";
		String selectionOwnerName = relevantSelectionOwner.getClass().getSimpleName();
		switch (selectionOwnerName) {
			case "WebView":
				selection = tabPane.getActiveViewSelection().orElse("");
				break;
			case "ComboBox":
				selection = queryBoxValue.getValue().trim();
				break;
			case "TableView":
				selection = selectedHWItems.stream().collect(joining("\n"));
		}
		return selection;
	}

	private Task<String> getSelectedTextTask() {
		return new Task<String>() {
			@Override
			protected String call() throws Exception {
				return isCancelled() ? "" : getSelectedText();
			}
		};
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

		boolean showExtendedPronounceSubmenu = !appendingToTableViewContextMenu
				                                       && tabPane.getActiveTab() instanceof ExpositorTab
				                                       && !context.isPresent();
		Optional<String> pronunciationContext = context.isPresent()
				                                        ? context
				                                        : getAssignedVocabula(tabPane.getActiveTab()).map(voc -> voc.headWord);

		EventHandler<ActionEvent> pronounceSelectionEH = e -> pronounce(pronunciationContext);
		EventHandler<ActionEvent> pronounceArticleEH = e -> pronounce(pronunciationContext, true);

		if (showExtendedPronounceSubmenu) {
			Menu pronounce = new Menu("Pronounce");
			GUIUtil.addMenuItem(" headword", pronounceSelectionEH, pronounce);
			GUIUtil.addMenuItem(" article", pronounceArticleEH, pronounce);
			GUIUtil.addMenuItem(pronounce, contextMenu);
		} else {
			GUIUtil.addMenuItem("Pronounce", pronounceSelectionEH, contextMenu);
		}
		// allow deletion only of an active article or from list
		if (context.isPresent() || tabPane.getActiveTab() instanceof TranslationTab) return;

		GUIUtil.addMenuItem("", null, contextMenu);
//		EventHandler<ActionEvent> deleteHeadwordEH = e -> deleteHeadwords(context);
		EventHandler<ActionEvent> deleteHeadwordEH = e -> deleteHeadwords();
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
		String text;
		if (relevantSelectionOwner == hwTable) {
			if (commandCtrl) {
				tabPane.insertIfAbsent(selectedHWItems, translationTabSupplier(requestFormatter), TranslationTab.class);
				commandCtrl = ctrlIsDown;
				return;
			} else {
				text = selectedHWItems.stream().sorted().collect(joining("\n"));
			}
		} else {
			text = textToTranslate.map(t -> t.replaceAll("\\t+| {2,}", " ")).orElse(getActiveViewHeadword());
		}
		if (!text.isEmpty()) {
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
			LOGGER.debug("Handling request: {}", query);
			boolean changeOccurred = false;
			try {
				int defCount = dictionary.getDefinitionsCount();
				boolean acceptSimilar = query.startsWith("~");
				// skip dictionary querying if "~" flag present
				Optional<Vocabula> optVocabula = acceptSimilar ? Optional.empty() : dictionary.findVocabula(query);
				final String fQuery = query.replaceAll("~", "").trim();
				if (optVocabula.isPresent()) {
					showResponse(optVocabula.get(), textsToHighlight);
					appendRequestHistory(fQuery);
				} else {
					acceptSimilar |= toggleSimilar.isSelected();
					Collection<Vocabula> vocabulas = findVocabula(fQuery, acceptSimilar);
					LOGGER.debug("on request \"{}\" found: {}",
					             fQuery, com.nicedev.util.Collections.toString(vocabulas, vocabula -> vocabula.headWord));
					Function<Collection<Vocabula>, Collection<Vocabula>> availableOf = vocabulaSet -> {
						return dictionary.getVocabulas(vocabulaSet.stream().map(voc -> voc.headWord).collect(toSet()));
					};
					Collection<Vocabula> availableVs = availableOf.apply(vocabulas);
					int nAdded = dictionary.addVocabulas(vocabulas);
					LOGGER.debug("added {} vocabula(s)", nAdded);
					if (!vocabulas.isEmpty() && nAdded > 0) {
						updatesCount++;
						Collection<Vocabula> addedVs = availableOf.apply(vocabulas);
						addedVs.removeAll(availableVs);
						List<String> addedHWs = addedVs.stream().map(v -> v.headWord).distinct().collect(toList());
						Platform.runLater(() -> {
							hwData.setAll(addedHWs);
							indexer.alter(addedVs);
						});
						if (nAdded == 1 && addedHWs.contains(fQuery)) {
							Vocabula found = addedVs.iterator().next();
							LOGGER.debug("found \"{}\"", found.headWord);
							showResponse(found, textsToHighlight);
						} else {
							Platform.runLater(() -> {
								tabPane.removeTab(fQuery);
								tabPane.selectTab(mainTab);
								updateQueryBoxText(fQuery, false);
							});
						}
						appendRequestHistory(fQuery);
						resetCache();
					} else {
						List<String> suggestions = getSuggestions(fQuery);
						LOGGER.debug("on request \"{}\" nothing found, suggestions: {}", suggestions);
						boolean showTranslation = false;
						if (!suggestions.isEmpty()) {
							Platform.runLater(() -> hwData.setAll(suggestions));
							String suggested;
							if (suggestions.size() == 1 && Strings.partEquals(suggested = suggestions.get(0), fQuery)) {
								LOGGER.debug("among suggested found present: \"{}\"", suggested);
								dictionary.findVocabula(suggested).ifPresent(vocabula -> showResponse(vocabula, fQuery));
							} else {
								showTranslation = true;
							}
						} else if (!showReferencingVocabula(fQuery)) {
							showTranslation = true;
						}
						if (showTranslation) {
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

	@SuppressWarnings("unchecked")
	private Collection<Vocabula> findVocabula(String query, boolean acceptSimilar) {
		return Stream.of(parsers)
				       .parallel()
				       .map(parser -> new Object[] { parser.priority, parser.getVocabula(query, acceptSimilar) })
				       .sorted(Comparator.comparingInt(tuple -> (int) tuple[0]))
				       .flatMap(tuple -> ((Collection<Vocabula>) tuple[1]).stream())
				       .collect(LinkedHashSet::new, Set::add, Set::addAll);
	}

	private List<String> getSuggestions(String match) {
		return getStream(asList(parsers), ALLOW_PARALLEL_STREAM)
				       .flatMap(parser -> parser.getRecentSuggestions().stream())
				       .filter(Strings::notBlank)
				       .distinct()
				       .sorted(startsWithCmpr(match).thenComparing(indexOfCmpr(match)))
				       .collect(toList());
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
			showResponse(referencingVocabula.get(), referenced);
			appendRequestHistory(referenced);
			return true;
		}
		return referencingVocabula.isPresent();
	}

	private void showResponse(Vocabula vocabula, String... textsToHighlight) {
		Platform.runLater(() -> showResponseImpl(vocabula, textsToHighlight));
	}

	private void showResponseImpl(Vocabula vocabula, String... textsToHighlight) {
		Tab currentTab = tabPane.getActiveTab();
		if (newTabAccteptable(vocabula, currentTab)) {
			tabPane.insertTab(expositorTabSupplier.apply(vocabula.headWord, textsToHighlight), true);
			commandCtrl = ctrlIsDown;
		} else {
			currentTab = tabPane.getActiveTab();
			currentTab.setText(vocabula.headWord);
			currentTab.getContent().setUserData(Optional.of(vocabula));
			render(Collections.singletonList(vocabula), textsToHighlight);
			pronounce(vocabula);
			updateQueryBoxText(vocabula.headWord, false);
		}
	}

	private boolean newTabAccteptable(Vocabula vocabula, Tab currentTab) {
		return commandCtrl || tabPane.getTabs().size() == 1 || (currentTab instanceof TranslationTab)
				       || (!tabPane.trySelectTab(ExpositorTab.sameTab(vocabula.headWord))
						           && !(currentTab instanceof ExpositorTab
								                || tabPane.trySelectTab(tab -> tab instanceof ExpositorTab)));
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

	private void saveRecentQueries() {
		try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(PROJECT_HOME, HISTORY_FILENAME), CREATE,
		                                                 TRUNCATE_EXISTING)) {
			String history = queryBox.getItems().stream()
					                 .distinct()
					                 .filter(Strings::notBlank)
					                 .collect(joining("\n"));
			bw.write(history);
		} catch (IOException e) {
			LOGGER.error("Unable to save to {}\\{}: {}", PROJECT_HOME, HISTORY_FILENAME, e);
		}
	}

	private void loadRecentQueries() {
		try (BufferedReader br = Files.newBufferedReader(Paths.get(PROJECT_HOME, HISTORY_FILENAME))) {
			br.mark(1_000_000);
			br.reset();
			queryBox.getItems().setAll(br.lines().limit(RECENT_QUERIES_TO_LOAD).collect(toList()));
		} catch (IOException | ClassCastException e) { /*NOP*/ }
	}

	@SuppressWarnings("unused")
	private boolean saveDictionary() {
		saveRecentQueries();
		return Dictionary.save(dictionary, storageEn);
	}

	private Collection<String> findReferences(String pattern) {
		return findReferences(pattern, toggleRE.isSelected());
	}

	private Collection<String> findReferences(String pattern, boolean isRegex) {
		Function<String, Collection<String>> referencesFinder = pattrn -> indexer.getReferencesFinder().apply(pattrn, isRegex);
		return isRegex
				       ? referencesFinder.apply(pattern)
				       : referencesCache.computeIfAbsent(pattern, referencesFinder);
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
//	private void deleteHeadwords(Optional<String> selection) {
	private void deleteHeadwords() {
		Collection<String> headwords = new ArrayList<>();
		if (tabPane.getSelectedTabIndex() == 0) {
			headwords.addAll(selectedHWItems);
		} else if (tabPane.getActiveTab() instanceof ExpositorTab) {
			String activeViewHeadword = getActiveViewHeadword();
			if (!activeViewHeadword.isEmpty()) headwords.add(activeViewHeadword);
		}
		if (headwords.isEmpty()) return;
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, getDeleteMsg(headwords.size()), ButtonType.YES, ButtonType.NO);
		GUIUtil.hackAlert(confirm);
		confirm.showAndWait().filter(response -> response == ButtonType.YES).ifPresent(response -> {
			if (tabPane.getSelectedTabIndex() > 0 && headwords.contains(tabPane.getActiveTab().getText()))
				tabPane.removeActiveTab();
			Platform.runLater(getDeletionTask(headwords));
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
				LOGGER.debug("deleted entries: {}", deleted);
				if (!deleted.isEmpty()) {
					saveDictionary();
					hwData.removeAll(deleted);
					updateStatistics(true);
					if (tabPane.getTabs().size() == 1) updateQueryBoxText(queryBoxValue.getValue(), true);
					updateTableState(false);
				}
				return null;
			}
		};
	}

	private String getDeleteMsg(int count) {
		return String.format("You're about to delete %d headword%s. Continue?", count, count > 1 ? "s" : "");
	}

	private String removeGaps(String queryText) {
		queryText = Stream.of(queryText.split("[\\n\\r\\f]"))
				            .filter(Strings::notBlank).map(String::trim).findFirst().orElse("")
				            .replaceAll("\\t| {2,}", " ");
		int trimPos = queryText.indexOf('[');
		if (trimPos > 0) queryText = queryText.substring(0, trimPos).trim();
		return queryText;
	}

	private void updateStatistics(boolean invalidateIndex) {
		Runnable taskUpdate = () -> {
			stats.setText(dictionary.toString());
			if (invalidateIndex) indexer.restart();
		};
		if (Platform.isFxApplicationThread()) {
			taskUpdate.run();
		} else
			Platform.runLater(taskUpdate);
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void pronounce(Optional<String> text, boolean isArticle) {
		if (isArticle && text.isPresent()) {
			dictionary.findVocabula(text.get())
					.ifPresent(vocabula -> Platform.runLater(() -> {
						pronouncingService.clearQueue();
						pronouncingService.enqueueAsync(vocabula.toString().replaceAll("[<>]", ""));
					}));
		} else {
			Platform.runLater(() -> {
				pronouncingService.clearQueue();
				Supplier<String> contextProvider = () -> {
					String selectedText = getSelectedText();
					return selectedText.isEmpty() ? getActiveViewHeadword() : selectedText;
				};
				pronouncingService.enqueueAsync(text.orElse(contextProvider.get()), PRONUNCIATION_DELAY);
			});
		}
	}

	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void pronounce(Optional<String> selection) {
		pronounce(selection, false);
	}

	private String getActiveViewHeadword() {
		return getAssignedVocabula(tabPane.getActiveTab()).map(voc -> voc.headWord).orElse("");
	}

	private void pronounce(Vocabula vocabula) {
		if (!autoPronounce) return;
		pronouncingService.clearQueue();
		Platform.runLater(() -> {
			pronouncingService.clearQueue();
			Iterator<String> sIt = vocabula.getPronunciationSources().iterator();
			if (sIt.hasNext()) pronouncingService.enqueue(sIt.next(), PRONUNCIATION_DELAY);
			else pronouncingService.enqueue(vocabula.headWord, PRONUNCIATION_DELAY);
		});

	}

	private void render(Collection<Vocabula> vocabulas, String... textsToHighlight) {
		LOGGER.debug("rendering [{}] with highlight [{}]",
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

}