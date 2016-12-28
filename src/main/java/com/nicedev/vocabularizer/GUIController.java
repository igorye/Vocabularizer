package com.nicedev.vocabularizer;

import com.nicedev.util.Maps;
import com.nicedev.util.QueuedCache;
import com.nicedev.util.Strings;
import com.nicedev.vocabularizer.dictionary.Definition;
import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.nicedev.vocabularizer.dictionary.PartOfSpeech;
import com.nicedev.vocabularizer.dictionary.Vocabula;
import com.nicedev.vocabularizer.gui.*;
import com.nicedev.vocabularizer.services.Expositor;
import com.nicedev.vocabularizer.services.sound.PronouncingService;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.nicedev.util.Comparators.indexOfCmpr;
import static com.nicedev.util.Comparators.startsWithCmpr;
import static com.nicedev.util.SimpleLog.log;
import static com.nicedev.util.Streams.getStream;
import static com.nicedev.util.Strings.getValidPattern;
import static java.lang.Math.max;
import static java.lang.String.format;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.*;
import static javafx.scene.input.KeyCode.SPACE;

public class GUIController implements Initializable {
	
	private final String ALL_MATCH = "";
	private final int RECENT_QUERIES_TO_LOAD = 150;
	@FXML
	private ToggleButton toggleSound;
	@FXML
	private ToggleButton toggleShowParts;
	@FXML
	private ToggleButton toggleRE;
	@FXML
	private Button searchBtn;
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
	
	private static final boolean ALLOW_PARALLEL_STREAM = Boolean.valueOf(System.getProperty("allowParallelStream", "false"));
	private static final int INDEXING_ALGO = Integer.valueOf(System.getProperty("indexingAlgo", "0"));
	private static final int FILTER_CACHE_SIZE = Integer.valueOf(System.getProperty("fcSize", "25"));
	private static final Label NOTHING_FOUND = new Label("Nothing found");
	private static final Label NO_MATCH = new Label("No matches");
	private static final Label SEARCHING = new Label("Searching...");
	private final String WH_REQUEST_URL = "http://wooordhunt.ru/word/%s";
	private final String GT_REQUEST_URL = "https://translate.google.com/#%s/%s/%s";
	private static final String HOME_PATH = System.getProperties().getProperty("user.home");
	private final String HISTORY_FILENAME = "vocabularizer.history";
	private final String cssHref;
	private final String jsHref;
	private int indexingTimeout = 45;
	public static String storageEn = format("%s\\%s.dict", HOME_PATH, "english");
	private String localLanguage;
	private String foreignLanguage;
	private Scene mainScene;
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
	private ScheduledThreadPoolExecutor scheduler;
	private ExecutorService executor;
	private volatile Map<String, Collection<String>> fullSearchIndex = emptyMap();
	private Future<Boolean> indexer;
	private ScheduledFuture<?> queryBoxScheduledFiltering;
	private ScheduledFuture<?> tableViewScheduledFiltering;
	private int leastSelectedIndex;
	
	
	public GUIController() {
		String res = getClass().getProtectionDomain().getClassLoader().getResource("view.css").toExternalForm();
		cssHref = res != null ? res : "";
		res = getClass().getProtectionDomain().getClassLoader().getResource("view.js").toExternalForm();
		jsHref = res != null ? res : "";
		scheduler = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(5);
		executor = Executors.newCachedThreadPool();
		queryBoxScheduledFiltering = scheduler.schedule(Thread::yield, 10, TimeUnit.SECONDS);
		tableViewScheduledFiltering = scheduler.schedule(Thread::yield, 10, TimeUnit.SECONDS);
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		((Runnable) () -> pronouncingService = new PronouncingService(100, false)).run();
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
	
	
	public void setMainScene(Scene scene) {
		mainScene = scene;
	}
	
	public void onLoad() {
		scheduler.setRemoveOnCancelPolicy(true);
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
		hwTable.itemsProperty().setValue(hwData);
		hwData.setAll(filterList(ALL_MATCH));
		hwTable.getSelectionModel().setCellSelectionEnabled(false);
		hwTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		hwTable.setPlaceholder(NOTHING_FOUND);
		hwTable.setOnScroll(tableScrollEventHandler);
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
	
	private void testIndex(int n) {
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
	}
	
	public void initIndex() {
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
		log("stop: canceling queryBoxScheduledFiltering");
		queryBoxScheduledFiltering.cancel(true);
		log("stop: terminating scheduler");
		scheduler.shutdownNow();
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
	
	private EventHandler<? super ScrollEvent> tableScrollEventHandler = event -> {
		if (event.isControlDown() && event.getSource() instanceof TableView) {
			if (event.getDeltaX() >= 0) {
			} else {
			}
			event.consume();
		}
	};
	
	private ChangeListener<Boolean> tabPaneFocusChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) tabPane.getActiveTab().getContent().requestFocus();
	};
	
	private ChangeListener<Tab> tabSelectionListener = (observable, oldValue, newValue) -> {
		synchronized (tabPane) {
			filterOn = tabPane.getSelectedTabIndex() == 0;
			String caption = filterOn ? newValue.getText() : newValue.getTooltip().getText();
			log("TSL: updateQueryField(%s,%s)", caption, false);
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
		if (newValue) relevantSelectionOwner = queryBox;
		filterOn = newValue || tabPane.getSelectedTabIndex() == 0;
	};
	
	private ChangeListener<Boolean> queryBoxShowingChangeListener = (observable, oldValue, newValue) -> {
		if (newValue) clearOnEsc = !newValue;
	};
	
	private ChangeListener<String> queryBoxValueChangeListener = (observable, oldValue, newValue) -> {
//		log("queryBoxValueChangeListener: filterOn==%s, value==%s", filterOn, newValue);
		if (filterOn) {
			try {
				if (queryBox.isShowing()) {
					engageFiltering(newValue, 500);
				} else {
					mainTab.setText(newValue);
					Optional.ofNullable(newValue).ifPresent(val -> engageFiltering(val, 400));
					updateTableState(false);
				}
			} catch (Exception e) {
				log("queryBoxLisener: unexpected exception - %s | %s", e.getMessage(), e.getCause().getMessage());
			}
		}
	};
	
	private void engageFiltering(String pattern, int delay) {
		queryBoxScheduledFiltering.cancel(true);
		Runnable delayedFiltering = () -> {
			try {
				if (!Thread.currentThread().isInterrupted()) hwData.setAll(filterList(pattern));
			} catch (Exception e) {
				log("NPE in delayedFiltering");
			}
		};
		queryBoxScheduledFiltering = scheduler.schedule(delayedFiltering, delay, TimeUnit.MILLISECONDS);
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
	protected void onTableMousePressed(MouseEvent event) {
		log("onTableMousePressed");
		engageTableSelectedItemFiltering();
		int selectedIndex = hwTable.getSelectionModel().getSelectedIndex();
		if (selectedIndex != -1) {
			leastSelectedIndex = selectedIndex;
		} else {
			if (selectedHWItems.size() > 0) selectedIndex = 0;
		}
	}
	
	@FXML
	protected void onTableMouseReleased(MouseEvent event) {
//		log("onTableMouseeleased");
		tableViewScheduledFiltering.cancel(true);
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
				if (!event.isControlDown() && event.getText().matches("[\\p{Graph} ]")) {
					updateQueryField(event.getText());
				} else if (event.isControlDown() && event.getCode() == SPACE) {
					tableViewScheduledFiltering.cancel(true);
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
					List<String> queries = stream(query.split("[\\n\\r\\f]"))
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
	
	protected EventHandler<KeyEvent> queryBoxEventFilter = event -> {
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
	protected void onMouseClicked(MouseEvent event) {
		log("onMouseClicked");
		ctrlIsDown = event.isControlDown();
		if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
			if (tabPane.getActiveTab() instanceof TranslationTab) return;
			String selection = tabPane.getSelectedTabIndex() == 0
					                   ? selectedHWItems.stream().findFirst().orElse("")
					                   : tabPane.getActiveViewSelection();
			if (!selection.isEmpty()) {
				handleQuery(removeGaps(selection));
			}
			event.consume();
		}
	}
	
	@FXML
	protected void onSearch(ActionEvent event) {
		updateTableState();
		Node source = relevantSelectionOwner;
		String query = getSelectedText();
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
		//log("composeRE: composed==%s", composed);
		return (!toggleRE.isSelected()) ? stream(text.split("(?=\\p{Punct})"))
											.map(s -> (s.matches("\\p{Punct}.*")) ? "\\".concat(s) : s)
											.collect(joining())
										: text;
	}
	
	private void rebuildIndex() {
		executor.submit(() -> {
//			log("rebuildIndex: start");
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
//					log("rebuildIndex: Done");
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
	
	private Map<String, Collection<String>> getIndex(Collection<Vocabula> vocabulas, int indexingAlgo) {
		//log("chosen algo %d", indexingAlgo);
		//long start = System.currentTimeMillis();
		Map<String, Collection<String>> res;
		switch (indexingAlgo) {
			case 1 : res = getIndex1(vocabulas); break;
			case 2 : res = getIndex2(vocabulas); break;
			default: res = getIndex(vocabulas);
		}
		//log("built index in %f (allowParallelStream==%s, indexingAlgo==%d",
		//	(System.currentTimeMillis() - start) / 1000f, ALLOW_PARALLEL_STREAM, indexingAlgo);
		return  res;
	}
	
	private void alterSearchIndex(Collection<Vocabula> vocabulas) {
		Map<String, Collection<String>> addToIndex = getIndex(vocabulas, INDEXING_ALGO);
		Maps.mergeLeft(fullSearchIndex, addToIndex, ALLOW_PARALLEL_STREAM);
	}
	
	private void resetCache() {
		Platform.runLater(() -> {
			filterCache.clear();
			filterCache.putPersistent(filterList(ALL_MATCH));
			try {
//				log("resetCache: queryBoxValue==\"%s\"", queryBoxValue.getValue());
				Optional.ofNullable(mainTab.getText()).ifPresent(val -> hwData.setAll(filterList(val)));
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
		log("engageTableSelectedItemFiltering");
		if (selectedHWItems.isEmpty()) return;
		tableViewScheduledFiltering.cancel(true);
		String selection = selectedHWItems.size() > 0
				                   ? hwTable.getSelectionModel().getSelectedItem()
				                   : hwTable.getItems().get(leastSelectedIndex);
		tableViewScheduledFiltering = scheduler.schedule(() -> filterWithSelectedItem(selection), 500, TimeUnit.MILLISECONDS);
	}
	
	private void filterWithSelectedItem(String pattern) {
		log("filterWithSelectedItem");
		try {
			if (!Thread.currentThread().isInterrupted()) updateQueryField(pattern);
		} catch (NullPointerException e) {
			log("NPE in filterWithSelectedItem");
		}
	}
	
	private void setSceneCursor(Cursor cursor) {
		Platform.runLater(() -> {
			tabPane.setCursor(cursor);
			tabPane.getActiveTab().getContent().setCursor(cursor);
		});
	}
	
	public class JSBridge {
		
		public void jsHandleQuery(String headWord) {
			log("jsBridge: jsHandleQuery: requested %s", headWord);
			handleQuery(headWord);
		}
		
		public void jsTest() {
			log("jsBridge: jsTest: test succeeded ");
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
	
	private Function<String, WebViewTab> expositorTabSupplier = (title) -> {
		WebViewTab newTab = new ExpositorTab(title, dictionary.getVocabula(title));
		//"update tab at first sight" flag
		newTab.setUserData(Boolean.TRUE);
		newTab.selectedProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(() -> {
			if ((Boolean) newTab.getUserData() && newValue) {
				Optional<Vocabula> optVocabula = getAssignedVocabula(newTab);
				String headWord = optVocabula.map(voc -> voc.headWord).orElse(title);
//				log("ETS: updateQueryField(%s,%s)", headWord, false);
//				updateQueryField(headWord, false);
				if (!optVocabula.isPresent()) {
					handleQuery(headWord);
//					log("ETS: updateQueryField(%s,%s)", headWord, false);
					updateQueryField(headWord, false);
				} else {
					log("tabSelectedChangeListener: updateView for \"%s\"", optVocabula.get().headWord);
					updateView(optVocabula.get().toHTML());
					pronounce(optVocabula.get());
				}
				newTab.setUserData(Boolean.FALSE);
			}
		}));
		newTab.addHandlers(viewHndlrsSetter);
		return newTab;
	};
	
	@SuppressWarnings("unchecked")
	private Optional<Vocabula> getAssignedVocabula(Tab newTab) {
		Optional<Optional<Vocabula>> userData = Optional.ofNullable((Optional<Vocabula>) newTab.getContent().getUserData());
		return userData.filter(Optional::isPresent).map(Optional::get);
	}
	
	private final Function<String, String> requestFormatterWH = s -> format(WH_REQUEST_URL, s);
	
	private final Function<String, String> requestFormatterGT = s -> format(GT_REQUEST_URL, foreignLanguage, localLanguage, s);
	
	private Function<String, WebViewTab> translationTabSupplier(Function<String, String> requestFormatter) {
		return (title) -> {
			WebViewTab newTab = new TranslationTab(title, requestFormatter.apply(encodeURL(title)));
			newTab.addHandlers(viewHndlrsSetter);
			return newTab;
		};
	}
	
	private void updateQueryField(String text) {
		updateQueryField(text, true);
	}
	
	private void updateQueryField(String text, boolean filterActive) {
//		log("updateQueryField(%s, %s)", text, filterActive);
		Platform.runLater(() -> {
			int caretPos = queryBox.editorProperty().get().getCaretPosition();
			int textLength = queryBoxValue.getValue().length();
			boolean filterState = filterOn;
			filterOn = filterActive;
//			log("updateQueryField: filterOn==%s)", filterOn);
			queryBox.editorProperty().getValue().setText(text);
			if (filterActive) queryBox.requestFocus();
			if (filterActive) {
				mainTab.setText(text);
				if (tabPane.getSelectedTabIndex() > 0) tabPane.selectTab(mainTab);
			}
			int newCaretPos = caretPos != textLength ? caretPos : queryBoxValue.getValue().length();
			queryBox.editorProperty().get().positionCaret(newCaretPos);
			//hwTable.refresh();
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
	
	private void appendContextMenuItems(Object contextMenu, Optional<String> context) {
		EventHandler<ActionEvent> handleQueryEH = this::onSearch;
		GUIUtil.addMenuItem("Explain", handleQueryEH, contextMenu);
		Menu translations = new Menu("Translate");
		EventHandler<ActionEvent> showGTTranslationEH = e -> showTranslation(context, requestFormatterGT);
		GUIUtil.addMenuItem(" via Google", showGTTranslationEH, translations);
		EventHandler<ActionEvent> showWHTranslationEH = e -> showTranslation(context, requestFormatterWH);
		GUIUtil.addMenuItem(" via WooordHunt", showWHTranslationEH, translations);
		GUIUtil.addMenuItem(translations, contextMenu);
		EventHandler<ActionEvent> pronounceEH = e -> pronounce(context);
		GUIUtil.addMenuItem("Pronounce", pronounceEH, contextMenu);
		GUIUtil.addMenuItem("", null, contextMenu);
		EventHandler<ActionEvent> deleteHeadwordEH = e -> deleteHeadword(context);
		GUIUtil.addMenuItem("Delete headword", deleteHeadwordEH, contextMenu);
	}
	
	private void showTranslation(Optional<String> textToTranslate, Function<String, String> requestFormatter) {
		if (relevantSelectionOwner == hwTable && ctrlIsDown)
			tabPane.insertIfAbsent(selectedHWItems, translationTabSupplier(requestFormatter), TranslationTab.class);
		else {
			String text = textToTranslate.orElse(selectedHWItems.stream().sorted().collect(joining("\n")))
					              .replaceAll("\\t+| {2,}", " ");
			String translationURL = requestFormatter.apply(encodeURL(text));
			if (!tabPane.trySelectTab(TranslationTab.sameTab(translationURL)))
				tabPane.insertTab(translationTabSupplier(requestFormatter).apply(text), true);
		}
	}
	
	private String encodeURL(String s) {
		String encoded = "";
		try {
			encoded = URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
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
		} catch (IOException e) {
		}
	}
	
	private void loadRecentQueries() {
		try (BufferedReader br = Files.newBufferedReader(Paths.get(HOME_PATH, HISTORY_FILENAME))) {
			br.mark(1_000_000);
			int linesCount = (int) br.lines().count();
			br.reset();
			queryBox.getItems().setAll(br.lines().limit(RECENT_QUERIES_TO_LOAD).collect(toList()));
		} catch (IOException | ClassCastException e) {
		}
	}
	
	private void handleQuery(String query) {
		boolean foundPresent = tabPane.trySelectTab(ExpositorTab.sameTab(query))
				                       && getAssignedVocabula(tabPane.getActiveTab()).isPresent();
		if (!foundPresent) handleQueryImpl(query);
	}
	
	private void handleQueryImpl(String query) {
		String filteredQuery = removeGaps(query);
		if (filteredQuery.isEmpty()) return;
		updateTableState();
		setSceneCursor(Cursor.WAIT);
		int defCount = dictionary.getDefinitionCount();
		scheduler.execute(() -> {
			try {
				Optional<Vocabula> optVocabula = dictionary.getVocabula(filteredQuery);
				String cQuery = filteredQuery.replaceAll("\\*", "").trim();
				if (optVocabula.isPresent()) {
					showQueryResult(optVocabula);
				} else {
					boolean strictLookup = !filteredQuery.contains("*");
					Collection<Vocabula> vocabulas = findVocabula(filteredQuery, strictLookup);
					if (!vocabulas.isEmpty() && strictLookup) {
						updateCount++;
						int nAdded = dictionary.addVocabulas(vocabulas);
						hwData.setAll(vocabulas.stream().map(v -> v.headWord).distinct().collect(toList()));
						//ad hoc appending each vocabula to searchIndex while one being rebuilding
						alterSearchIndex(vocabulas);
						if (nAdded == 1 && hwData.contains(cQuery)) {
							Optional<Vocabula> optVoc = vocabulas.stream()
									                            .filter(vocabula -> vocabula.headWord.equals(cQuery))
									                            .findFirst();
							showQueryResult(optVoc);
						} else {
							hwData.setAll(vocabulas.stream().map(v -> v.headWord).distinct().collect(toList()));
							Platform.runLater(() -> {
								tabPane.removeTab(cQuery);
								tabPane.selectTab(mainTab);
							});
							updateTableState(false);
						}
//						log("HQ: updateQueryField(%s,%s)", cQuery, false);
						updateQueryField(cQuery, false);
					} else {
						Optional<Vocabula> featuredVocabula = getFeaturedVocabula(cQuery);
						if (featuredVocabula.isPresent()) {
							showQueryResult(featuredVocabula);
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
				e.printStackTrace();
			} finally {
				if (updateCount % 5 == 0) saveDictionary();
				updateTableState(false);
				setSceneCursor(Cursor.DEFAULT);
			}
		});
	}
	
	private boolean saveDictionary() {
		saveRecentQueries();
		return Dictionary.save(dictionary, storageEn);
	}
	
	private void appendRequestHistory(String query) {
		boolean oldState = filterOn;
		filterOn = false;
		int index;
		queryBox.getItems().add(0, query);
		while((index = queryBox.getItems().indexOf(query)) > 0) queryBox.getItems().remove(index);
		filterOn = oldState;
		queryBox.getSelectionModel().select(0);
	}
	
	private Optional<Vocabula> getFeaturedVocabula(String headWord) {
		List<String> mentions = filterList(headWord);
		return filterList(headWord).stream()
				       .filter(hw -> !hw.equals(headWord) && (filterList(hw).contains(headWord) || mentions.size() == 2))
				       .findFirst()
				       .flatMap(hw -> dictionary.getVocabula(hw));
	}
	
	private void showQueryResult(Optional<Vocabula> optVocabula) {
		optVocabula.ifPresent(this::showQueryResult);
	}
	
	private void showQueryResult(Vocabula vocabula) {
		Platform.runLater(() -> showQueryResultImpl(vocabula));
	}
	
	private void showQueryResultImpl(Vocabula vocabula) {
		Tab currentTab = tabPane.getActiveTab();
		if (ctrlIsDown || tabPane.getTabs().size() == 1 || (currentTab instanceof TranslationTab)
				    || (!tabPane.trySelectTab(ExpositorTab.sameTab(vocabula.headWord))
						        && !(currentTab instanceof ExpositorTab
											|| tabPane.trySelectTab(tab -> tab instanceof ExpositorTab)))) {
			tabPane.insertTab(expositorTabSupplier.apply(vocabula.headWord), true);
		} else {
			currentTab = tabPane.getActiveTab();
			currentTab.setText(vocabula.headWord);
			currentTab.getContent().setUserData(Optional.of(vocabula));
			log("showQueryResultImpl: updateView for\"%s\"", vocabula.headWord);
			updateView(vocabula.toHTML());
			pronounce(vocabula);
			log("SQRI: updateQueryField(%s,%s)", vocabula.headWord, false);
			updateQueryField(vocabula.headWord, false);
		}
	}
	
	private void deleteHeadword(Optional<String> selection) {
		Collection<String> headwords = selection.map(s -> stream(s.split("\\n|\\r|\\f"))
				                                                  .filter(Strings::notBlank).collect(toList()))
				                               .orElse(selectedHWItems);
		int count = headwords.size();
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, getDeleteMsg(count), ButtonType.YES, ButtonType.NO);
		GUIUtil.hackAlert(confirm);
		confirm.showAndWait().filter(response -> response == ButtonType.YES).ifPresent(response -> {
			indexer.cancel(true);
			if (tabPane.getSelectedTabIndex() > 0 && headwords.contains(tabPane.getActiveTab().getText()))
				tabPane.removeActiveTab();
			executor.execute(() -> {
			long deleted = headwords.stream()
					               .map(String::trim)
					               .filter(headword -> dictionary.removeVocabula(headword))
					               .count();
			if (deleted > 0) {
				saveDictionary();
				updateStatistics();
				if (tabPane.getTabs().size() == 1) {
					log("DH: updateQueryField(%s,%s)", queryBoxValue.getValue(), true);
					updateQueryField(queryBoxValue.getValue(), true);
					hwData.setAll(filterList(queryBoxValue.getValue()));
				}
				hwData.removeAll(headwords);
				updateTableState(false);
			}
			});
		});
	}
	
	private String getDeleteMsg(int count) {
		return format("You're about to delete %d headword%s. Continue?", count, count > 1 ? "s" : "");
	}
	
	private void updateStatistics() {
		updateStatistics(true);
	}
	
	private void updateStatistics(boolean invalidateIndex) {
		Platform.runLater(() -> stats.setText(dictionary.toString()));
		if (invalidateIndex) rebuildIndex();
	}
	
	private String removeGaps(String queryText) {
		queryText = stream(queryText.split("[\\n\\r\\f]"))
				            .filter(Strings::notBlank).map(String::trim).findFirst().orElse("")
				            .replaceAll("\\t| {2,}", " ");
		int trimPos = queryText.indexOf('[');
		if (trimPos > 0) queryText = queryText.substring(0, trimPos).trim();
		return queryText;
	}
	@SuppressWarnings("unchecked")
	private Collection<Vocabula> findVocabula(String query, boolean lookupSimilar) {
		return asList(expositors).parallelStream()
//				       .sorted(Comparator.comparingInt(expositor -> expositor.priority))
//				       .flatMap(expositor -> expositor.getVocabula(query, lookupSimilar).stream())
				       .map(expositor -> new Object[] { new Integer(expositor.priority), expositor.getVocabula(query, lookupSimilar)})
				       .sorted(Comparator.comparingInt(tuple -> (Integer) tuple[0]))
				       .flatMap(tuple -> ((Collection<Vocabula>) tuple[1]).stream())
				       .collect(LinkedHashSet::new, Set::add, Set::addAll);
	}
	
	private Map<Vocabula, Map<PartOfSpeech, List<Definition>>> findVocabula2(String query, boolean lookupSimilar) {
		return asList(expositors).parallelStream()
				       .flatMap(expositor -> expositor.getVocabula(query, lookupSimilar).stream())
				       .flatMap(vocabula -> vocabula.getDefinitions().stream())
				       .collect(groupingBy(Definition::getDefinedVocabula, groupingBy(Definition::getDefinedPartOfSpeech)));
	}
	
	private void pronounce(Optional<String> selection) {
		Platform.runLater(() -> {
			pronouncingService.clear();
			pronouncingService.pronounce(selection.orElse(selectedHWItems.stream().collect(joining("\n"))), 500);
		});
	}
	
	private void pronounce(Vocabula vocabula) {
		if (!autoPronounce) return;
		pronouncingService.clear();
		Iterator<String> sIt = vocabula.getPronunciationSources().iterator();
		if (sIt.hasNext()) pronouncingService.pronounce(sIt.next(), 500);
		else pronouncingService.pronounce(vocabula.headWord, 500);
	}
	
	private List<String> getSuggestions(String match) {
		return getStream(asList(expositors), ALLOW_PARALLEL_STREAM)
				       .flatMap(expositor -> expositor.getRecentSuggestions().stream())
				       .distinct()
				       .sorted(startsWithCmpr(match).thenComparing(indexOfCmpr(match)))
				       .filter(Strings::notBlank)
				       .collect(toList());
	}
	
	private void updateView(String body) {
		if (body.isEmpty()) {
			log("updateView: body is empty");
			tabPane.getActiveEngine().ifPresent(engine -> engine.loadContent(""));
			return;
		}
		tabPane.getActiveEngine().ifPresent(engine -> {
			log("updateView: body is present");
			setSceneCursor(Cursor.WAIT);
			String fmt = "<html>\n<head>\n<link rel='stylesheet' href='%s'/>" +
					             "<script src='%s'></script>" +
					             "</head>\n<body>\n%s</body>\n</html>";
			String htmlContent = format(fmt, cssHref, jsHref, enableAnchors(body));
			engine.loadContent(htmlContent);
			tabPane.getActiveTab().getContent().requestFocus();
		});
		setSceneCursor(Cursor.DEFAULT);
	}
	
	private String enableAnchors(String body) {
		Matcher bToA = Pattern.compile("(<b>([^<>]+)</b>)").matcher(body);
		String modifiedBody = body;
		String aFmt = "<a href=\"#\" onclick=\"explainHeadWord(this);\">%s</a>";
		String headWord = getAssignedVocabula(tabPane.getActiveTab())
				                  .map(vocabula -> vocabula.headWord)
				                  .orElse(tabPane.getActiveTab().getTooltip().getText());
		while (bToA.find()) {
			String replaced = bToA.group(1).replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)");
			String anchored = bToA.group(2);
			if (!anchored.equals(headWord)) {
				int i = modifiedBody.indexOf(replaced);
				modifiedBody = modifiedBody.replaceAll(replaced, String.format(aFmt, anchored));
			}
		}
		return modifiedBody;
	}
	
	private List<String> filterList(String pattern) {
		return toggleRE.isSelected()
				       ? filteredListSupplier.apply(pattern)
				       : filterCache.computeIfAbsent(pattern, filteredListSupplier);
	}
	
	private Function<String, List<String>> filteredListSupplier = pattern -> {
		String patternLC = pattern.toLowerCase();
		String validPattern = getValidPattern(composeRE(pattern), "i");
		Predicate<String> definedPartOfSpeech = hw -> !dictionary.getPartsOfSpeech(hw).isEmpty();
//		Comparator<String> partialMatchComparator = firstComparing(definedPartOfSpeech)
//				                                            .thenComparing(startsWithCmpr(pattern))
//				                                            .thenComparing(indexOfCmpr(pattern))
//															.thenComparing(Comparator.naturalOrder());
		Comparator<String> partialMatchComparator = startsWithCmpr(pattern)
				                                            .thenComparing(indexOfCmpr(pattern))
				                                            .thenComparing(Comparator.naturalOrder());
		Predicate<String> regExMatchPredicate = s -> toggleRE.isSelected() ? s.matches(validPattern) : true;
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
	
	private Function<Definition, Stream<String>> hwFromDefinition = def -> {
		if (isIndexingAborted()) return Stream.empty();
		return hwCollector(singleton(def.explanation), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Function<Definition, Stream<String>> hwFromKnownForms = def -> {
		if (isIndexingAborted()) return Stream.empty();
		return hwCollector(def.getDefinedVocabula().getKnownForms(), allMatch, Stream::of);
	};
	
	private Function<Definition, Stream<String>> hwFromSynonyms = def -> {
		if (isIndexingAborted()) return Stream.empty();
		return hwCollector(def.getSynonyms(), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Function<Definition, Stream<String>> hwFromUseCases = def -> {
		if (isIndexingAborted()) return Stream.empty();
		return hwCollector(def.getUseCases(), bTagWrapped, this::extract_bTag_Wrapped);
	};
	
	private Stream<String> hwCollector(Set<String> source,
	                                   Predicate<String> hwMatcher,
	                                   Function<String, Stream<String>> hwExtractor) {
		return source.stream().filter(hwMatcher).flatMap(hwExtractor).filter(Strings::notBlank);
	}
	
	private Stream<String> extract_bTag_Wrapped(String source) {
		Set<String> HWs = new HashSet<>();
		Matcher matcher = Pattern.compile("<b>((?<=<b>)[^<>]+(?=</b>))</b>").matcher(source);
		while (matcher.find()) HWs.add(matcher.group(1).trim());
		Set<String> separateHWs = HWs.stream()
										  .filter(s -> !s.matches("\\p{Punct}}"))
				                          .flatMap(s -> stream(s.split("[,.:;'/()\\s\\\\]"))
						                                        .filter(Strings.notBlank)
						                                        .distinct())
				                          .collect(toSet());
		HWs.addAll(separateHWs);
		return HWs.stream();
	}
	
	private boolean isIndexingAborted() {
		return Thread.currentThread().isInterrupted() || indexer.isCancelled();
	}
	
	private Map<String, Collection<String>> indexFor(Collection<Definition> definitions,
	                                                 Collection<Function<Definition, Stream<String>>> hwSuppliers) {
		if (definitions.isEmpty() || isIndexingAborted()) return emptyMap();
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> combiner = (m1, m2) -> {
			Maps.mergeLeft(m1, m2, ALLOW_PARALLEL_STREAM);
		};
		return getStream(definitions, ALLOW_PARALLEL_STREAM)
				       .collect(HashMap::new,
						       (resultMap, definition) -> {
							       String definedAt = definition.getDefinedVocabula().headWord;
							       Function<String, Collection<String>> keyMapper = key -> new HashSet<>();
							       Collection<String> references = resultMap.computeIfAbsent(definedAt, keyMapper);
							       references.add(definedAt);
							       getStream(hwSuppliers, ALLOW_PARALLEL_STREAM)
									       .flatMap(supplier -> supplier.apply(definition))
									       .forEach(hw -> {
										       String hwLC = hw.toLowerCase();
										       Collection<String> refs = resultMap.computeIfAbsent(hwLC, keyMapper);
										       refs.add(definedAt);
										       refs.add(hwLC);
										       references.add(hwLC);
									       });
						       },
						       combiner);
	}
	
	private Map<String, Collection<String>> getIndex(Collection<Vocabula> vocabulas) {
		BiConsumer<Map<String, Collection<String>>, Map<String, Collection<String>>> accumulator = (m1, m2) -> {
			if (!isIndexingAborted())  Maps.mergeLeft(m1, m2, ALLOW_PARALLEL_STREAM);
		};
		if (isIndexingAborted()) return emptyMap();
		return getStream(vocabulas, ALLOW_PARALLEL_STREAM)
				       .map(this::indexFor)
				       .collect(HashMap::new, accumulator, accumulator);
	}
	
	private int getValueSize(Map<String, Collection<String>> m, String k) {
		return Optional.ofNullable(m.get(k)).map(Collection::size).orElse(0);
	}
	
	protected Map<String, Collection<String>> getIndex1(Collection<Vocabula> vocabulas) {
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
	
	private Map<String, Collection<String>> getIndex2(Collection<Vocabula> vocabulas) {
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
	
	private Map<String, Collection<String>> indexFor(Vocabula vocabula) {
		if (isIndexingAborted()) return emptyMap();
		return indexFor(vocabula.getDefinitions(),
						asList(hwFromKnownForms, hwFromDefinition, hwFromSynonyms, hwFromUseCases));
	}
		
}