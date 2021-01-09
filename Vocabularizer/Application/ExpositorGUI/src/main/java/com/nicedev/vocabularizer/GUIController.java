package com.nicedev.vocabularizer;

import com.nicedev.tts.service.TTSPlaybackService;
import com.nicedev.tts.service.TTSService;
import com.nicedev.util.Comparators;
import com.nicedev.util.*;
import com.nicedev.vocabularizer.dictionary.model.Dictionary;
import com.nicedev.vocabularizer.dictionary.model.Vocabula;
import com.nicedev.vocabularizer.dictionary.parser.MerriamWebsterParser;
import com.nicedev.vocabularizer.gui.*;
import com.nicedev.vocabularizer.service.DelayedFilteringTaskService;
import com.nicedev.vocabularizer.service.DelayedTaskService;
import com.nicedev.vocabularizer.service.IndexingService;
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
import javafx.event.EventTarget;
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
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.*;
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
    private final String DICT_PATH = PROJECT_HOME.concat("\\dict");
    private final String storageEn = String.format("%s\\%s.dict", DICT_PATH, "english");
    private final String HISTORY_FILENAME = PROJECT_NAME + ".history";
    private final boolean ALLOW_PARALLEL_STREAM = Boolean.parseBoolean(System.getProperty("allowParallelStream", "false"));
    private final int INDEXING_ALGO = Integer.parseInt(System.getProperty("indexingAlgo", "0"));
    private final int FILTER_CACHE_SIZE = Integer.parseInt(System.getProperty("fcSize", "25"));
    private final String COMPARING_DELIMITER = " | ";
    private final String ALL_MATCH = "";
    private final int LIST_FILTERING_DELAY = 500;
    private final int INPUT_FILTERING_DELAY = 400;
    private final int EXPLAIN_SELECTION_DELAY = 500;
    private final int PRONUNCIATION_DELAY = 500;
    private static final int RECENT_QUERIES_TO_LOAD = 150;
    private static final int FILTER_DELAY = 500;

    private final String WH_REQUEST_URL = "http://wooordhunt.ru/word/%s";
    private final String GT_REQUEST_URL = "https://translate.google.com/?sl=%s&tl=%s&text=%s&op=translate";
    //    private final String GT_REQUEST_URL = "https://translate.google.com/#%s/%s/%s";
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
    private boolean                                 autoPronounce = true;
    private QueuedCache<String, Collection<String>> referencesCache;
    private ObservableList<String>                  hwData;
    private Node relevantSelectionOwner;
    private ObservableList<String> selectedHWItems;
    private boolean filterOn = true;
    private boolean clearOnEsc = true;
    private final StringProperty                                  queryBoxValue = new SimpleStringProperty();
    private       DelayedFilteringTaskService<Collection<String>> queryBoxFiltering;
    private       DelayedFilteringTaskService<String> tableItemFiltering;
    private       DelayedTaskService<String>          explainSelection;
    private final ExecutorService                     executor;
    private IndexingService indexer;
    private int leastSelectedIndex;
    private int caretPos = 0;
    private boolean ctrlIsDown = false;
    private boolean commandCtrl = false;
    private boolean mouseSelectionMode = false;
    private ContextMenu webViewContextMenu;
    private ContextMenu tableViewContextMenu;

    public GUIController() {
        URL resource = getClass().getProtectionDomain().getClassLoader().getResource("view.css");
        cssHref = resource != null ? resource.toString() : "";
        resource = getClass().getProtectionDomain().getClassLoader().getResource("view.js");
        jsHref = resource != null ? resource.toString() : "";
        executor = Executors.newCachedThreadPool();
    }

    private DelayedFilteringTaskService<String> getTableFilteringTask() {
        Function<String, Task<String>> tableFilterTaskProvider = pattern -> new Task<>() {
            @Override
            protected String call() {
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
        Function<String, Task<Collection<String>>> queryFilterTaskProvider = pattern -> new Task<>() {
            @Override
            protected Collection<String> call() {
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
        Supplier<Task<String>> explainTaskSupplier = () -> new Task<>() {
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

    void onStageShown( WindowEvent windowEvent ) {
        final EventHandler<WorkerStateEvent> onIndexInit = event -> {
            resetCache();
            String filter = queryBoxValue.get();
            hwData.setAll(findReferences(filter));
            tableItemFiltering.start();
            explainSelection.start();
            queryBoxFiltering.start();
        };
        setupServiceTasks();
        initIndex(onIndexInit);
        loadRecentQueries();
        tabPane.focusedProperty().addListener(tabPaneFocusChangeListener);
        tabPane.getSelectionModel().selectedItemProperty().addListener(tabSelectionListener);
        mainScene.addEventFilter(KeyEvent.KEY_RELEASED, event -> ctrlIsDown = event.isControlDown());
        setupHeadwordsTable();
        setupQueryBox();
        mainScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> ctrlIsDown = event.isControlDown());
        updateStatistics(false);
    }

    private void setupServiceTasks() {
        tableItemFiltering = getTableFilteringTask();
        tableItemFiltering.setExecutor(executor);
        queryBoxFiltering = getQueryFilteringTask();
        queryBoxFiltering.setExecutor(executor);
        explainSelection = getExplainSelectionTask();
        explainSelection.setExecutor(executor);
    }

    private void setupHeadwordsTable() {
        hwTable.setPlaceholder(NOTHING_FOUND);
        hwTable.refresh();
        hwColumn.setCellFactory(filteredCellFactory);
        hwColumn.setCellValueFactory(hwCellValueFactory);
        posColumn.setCellValueFactory(posCellValueFactory);
        formsColumn.setCellValueFactory(formsCellValueFactory);
        hwData = FXCollections.observableArrayList();
        hwTable.itemsProperty().setValue(hwData);
        hwTable.getSelectionModel().setCellSelectionEnabled(false);
        hwTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
//		hwTable.setOnScroll(tableScrollEventHandler);
        hwTable.focusedProperty().addListener(tableFocusChangeListener);
        hwTable.getSelectionModel().getSelectedItems().addListener(tableSelectedItemsChangeListener);
        selectedHWItems = hwTable.getSelectionModel().getSelectedItems();
        relevantSelectionOwner = hwTable;
        hwData.setAll(findReferences(ALL_MATCH));
    }

    private void setupQueryBox() {
        queryBoxValue.bind(queryBox.editorProperty().get().textProperty());
//        queryBox.editorProperty().get().setOnContextMenuRequested(this::onContextMenuRequested);
        queryBox.editorProperty().get().caretPositionProperty().addListener(caretPositionChangeListener);
        queryBoxValue.addListener(queryBoxValueChangeListener);
        queryBox.focusedProperty().addListener(queryBoxFocusChangeListener);
        queryBox.showingProperty().addListener(queryBoxShowingChangeListener);
        queryBox.addEventFilter(KeyEvent.KEY_PRESSED, queryBoxEventFilter);
        queryBox.addEventFilter(KeyEvent.KEY_RELEASED, queryBoxEventFilter);
    }

    private void initIndex( EventHandler<WorkerStateEvent> onSucceededEventHandler ) {
        referencesCache = new QueuedCache<>("", FILTER_CACHE_SIZE);
        indexer = new IndexingService(dictionary,
                                      onSucceededEventHandler,
                                      ALLOW_PARALLEL_STREAM,
                                      INDEXING_ALGO,
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
        if (hwTable.getContextMenu() != null)
            hwTable.getContextMenu().hide();
        tableViewContextMenu = c.getList().size() > 0 ? getTableViewContextMenu() : null;
        hwTable.setContextMenu(tableViewContextMenu);
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

    private final ChangeListener<Number> caretPositionChangeListener =
            (observable, oldValue, newValue) -> caretPos = newValue.intValue();

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
        event.consume();
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
        if (event.getEventType() != KeyEvent.KEY_RELEASED) return;
        String query = getSelectedText();
        KeyCode keyCode = event.getCode();
        switch (keyCode) {
            case W:
                if (event.isControlDown() || commandCtrl) tabPane.removeActiveTab();
                break;
            case ESCAPE:
                if (webViewContextMenu.isShowing()) {
                    webViewContextMenu.hide();
                    break;
                }
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
        if (event.getEventType() != KeyEvent.KEY_RELEASED) return;
        String query = queryBoxValue.getValue();
        KeyCode keyCode = event.getCode();
        event.consume();
        switch (keyCode) {
            case UP:
            case DOWN:
                if (!queryBox.isShowing()) queryBox.show();
                break;
            case ENTER:
                if (event.getTarget() != queryBox) break;
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
                if (event.isControlDown() || commandCtrl) {
                    tabPane.removeActiveTab();
                }
                break;
            default:
                if (!event.isControlDown() && event.getText().matches("[\\p{Graph} ]")) {
                    mainTab.setText(queryBoxValue.getValue());
                    updateQueryBoxText(queryBoxValue.getValue());
                }
        }
    }

    @FXML
    protected void toggleSound() {
        autoPronounce = !autoPronounce;
        toggleSound.setStyle(String.format("-fx-background-image: url('%s');-fx-background-size: contain;",
                                           autoPronounce ? "soundOn.png" : "soundOff.png"));
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
        Stream.of(webViewContextMenu, tableViewContextMenu)
              .filter(Objects::nonNull)
              .forEach(ContextMenu::hide);
        switch (event.getSource().getClass().getSimpleName()) {
            case "WebView":
                if (event.getButton().equals(MouseButton.SECONDARY)) {
                    webViewContextMenu = getWebViewContextMenu();
                    webViewContextMenu.show(tabPane.getActiveTab().getTabPane(), event.getScreenX(), event.getScreenY());
                }
                break;
            case "TableView":
                if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                    if (tabPane.getActiveTab() instanceof TranslationTab) return;
                    if (tabPane.getSelectedTabIndex() == 0) {
                        selectedHWItems.stream().findFirst().ifPresent(this::handleQuery);
                    }
                }
                break;
        }
        mouseSelectionMode = true;
        event.consume();
    }

    @FXML
    protected void onSearch(@SuppressWarnings("unused") ActionEvent event) {
        updateTableState();
        Node source = relevantSelectionOwner;
        commandCtrl = true;
        if (event.getSource() != source) {
            final KeyEvent keyEvent = new KeyEvent(source, source, KeyEvent.KEY_RELEASED, "", "",
                                                   KeyCode.ENTER, false, commandCtrl, false, false);
            Event.fireEvent(source, keyEvent);
        }
    }

    @FXML
    private void onContextMenuRequested(ContextMenuEvent e) {
        String context = Optional.of(getSelectedText()).filter(Strings::notBlank).orElse("");
        final Object source = e.getSource();
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
        view.setContextMenuEnabled(false);
        view.setOnMouseClicked(this::onMouseClicked);
        view.setOnMousePressed(this::onWebViewMousePressed);
        view.setOnMouseReleased(this::onWebViewMouseReleased);
        view.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onWebViewMouseDragged);
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
        explainSelection.restart();
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
                if (optVocabula.isEmpty()) {
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
                                            .toArray(String[]::new);
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
            final String url = requestFormatter.apply(encodeURL(title));
            LOGGER.info("Translation tab ({}, {}) URL: \"{}\"", title, Arrays.toString(_arg), url);
            WebViewTab newTab = new TranslationTab(title, url);
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
                IndexRange selectionRange = queryBox.editorProperty().get().selectionProperty().get();
                selection = selectionRange.getLength() > 0
                                    ? queryBoxValue.getValue().substring(selectionRange.getStart(), selectionRange.getEnd())
                                    : queryBoxValue.getValue().trim();
                break;
            case "TableView":
                selection = String.join("\n", selectedHWItems);
        }
        return selection;
    }

    private Task<String> getSelectedTextTask() {
        return new Task<>() {
            @Override
            protected String call() {
                return isCancelled() ? "" : getSelectedText();
            }
        };
    }

    private ContextMenu getTableViewContextMenu() {
        ContextMenu cm = new ContextMenu();
        appendContextMenuItems(cm, "");
        LOGGER.debug("returning table context menu");
        return cm;
    }

    private ContextMenu getWebViewContextMenu() {
        ContextMenu cm = new ContextMenu();
        cm.setAutoHide(true);
        appendContextMenuItems(cm, getSelectedText());
        LOGGER.debug("returning webview context menu");
        return cm;
    }

    private final Function<String, String> requestFormatterGT =
            s -> String.format(GT_REQUEST_URL,
                               foreignLanguage,
                               localLanguage.equals(foreignLanguage) ? "ru" : localLanguage,
                               s);

    private void appendContextMenuItems(Object contextMenu, String context) {
        appendSearchMenuItem(contextMenu, context);
        boolean appendingToTableViewContextMenu = tabPane.getActiveTab().getContent() instanceof TableView;
        if (appendingToTableViewContextMenu) appendCompareMenuItem(contextMenu);
        appendTranslationMenuItem(contextMenu, context);
        appendPronounceMenuItem(contextMenu, context, appendingToTableViewContextMenu);
        // allow deletion only of an active article or from list
        if (context.isEmpty()) appendDeleteMenuItem(contextMenu);
    }

    private void appendSearchMenuItem(Object contextMenu, String context) {
        if (context.isEmpty() || tableViewIsAContextSource()) {
            EventHandler<ActionEvent> handleQueryEH = this::onSearch;
            GUIUtil.addMenuItem("Explain", handleQueryEH, contextMenu);
        }
    }

    private void appendCompareMenuItem(Object contextMenu) {
        if (selectedHWItems.size() > 1) {
            EventHandler<ActionEvent> compareHeadwordsEH = e -> compareHeadwords();
            GUIUtil.addMenuItem("Compare", compareHeadwordsEH, contextMenu);
        }
    }

    private void appendTranslationMenuItem(Object contextMenu, String context) {
        String translationContext = context.isEmpty()
                                            ? getAssignedVocabula(tabPane.getActiveTab())
                                                      .map(voc -> voc.headWord)
                                                      .orElse("")
                                            : context;
        if (!translationContext.isEmpty() || tableViewIsAContextSource()) {
            EventHandler<ActionEvent> showGTTranslationEH = e -> showTranslation(translationContext, requestFormatterGT);
            final Function<String, String> requestFormatterWH = s -> String.format(WH_REQUEST_URL, s);
            EventHandler<ActionEvent> showWHTranslationEH = e -> showTranslation(translationContext, requestFormatterWH);
            Menu translations = new Menu("Translate");
            GUIUtil.addMenuItem(" via Google", showGTTranslationEH, translations);
            GUIUtil.addMenuItem(" via WooordHunt", showWHTranslationEH, translations);
            GUIUtil.addMenuItem(translations, contextMenu);
        }
    }

    private boolean tableViewIsAContextSource() {
        return tabPane.getSelectedTabIndex() == 0
                       && relevantSelectionOwner == hwTable
                       && !hwTable.getSelectionModel().getSelectedItems().isEmpty();
    }

    private void appendPronounceMenuItem(Object contextMenu, String context, boolean appendingToTableViewContextMenu) {
        boolean showExtendedPronounceSubmenu = !appendingToTableViewContextMenu
                                                       && tabPane.getActiveTab() instanceof ExpositorTab
                                                       && context.isEmpty();
        String pronunciationContext = context.isEmpty()
                                              ? getAssignedVocabula(tabPane.getActiveTab()).map(voc -> voc.headWord)
                                                                                           .orElse("")
                                              : context;
        EventHandler<ActionEvent> pronounceSelectionEH = e -> pronounce(pronunciationContext);
        EventHandler<ActionEvent> pronounceArticleEH = e -> pronounce(pronunciationContext, true);
        if (!pronunciationContext.isEmpty() || tableViewIsAContextSource())
            if (showExtendedPronounceSubmenu) {
                Menu pronounce = new Menu("Pronounce");
                GUIUtil.addMenuItem(" headword", pronounceSelectionEH, pronounce);
                GUIUtil.addMenuItem(" article", pronounceArticleEH, pronounce);
                GUIUtil.addMenuItem(pronounce, contextMenu);
            } else {
                GUIUtil.addMenuItem("Pronounce", pronounceSelectionEH, contextMenu);
            }
    }

    private void appendDeleteMenuItem(Object contextMenu) {
        if (tabPane.getActiveTab() instanceof ExpositorTab || tableViewIsAContextSource()) {
            GUIUtil.addMenuItem("", null, contextMenu);
            EventHandler<ActionEvent> deleteHeadwordEH = e -> deleteHeadwords();
            GUIUtil.addMenuItem("Delete headword", deleteHeadwordEH, contextMenu);
        }
    }

    private void compareHeadwords() {
        if (relevantSelectionOwner == hwTable) {
            List<String> comparingPairs;
            if (selectedHWItems.size() > 2)
                comparingPairs = Combinatorics.getUniqueCombinations(selectedHWItems, 2).stream()
                                              .map(strings -> String.join(COMPARING_DELIMITER, strings))
                                              .collect(toList());
            else
                comparingPairs = Collections.singletonList(String.join(COMPARING_DELIMITER, selectedHWItems));
            tabPane.insertIfAbsent(comparingPairs, expositorTabSupplier, ExpositorTab.class);
        }
    }

    private void showTranslation(String textToTranslate, Function<String, String> requestFormatter) {
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
            text = textToTranslate.isEmpty() ? getActiveTabHeadword() : textToTranslate.replaceAll("\\t+| {2,}", " ");
        }
        if (!text.isEmpty()) {
            String translationURL = requestFormatter.apply(encodeURL(text));
            if (!tabPane.trySelectTab(TranslationTab.sameTab(translationURL)))
                tabPane.insertTab(translationTabSupplier(requestFormatter).apply(text, null), true);
        }
    }

    private String encodeURL(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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
                    Function<Collection<Vocabula>, Collection<Vocabula>> availableOf =
                            vocabulaSet -> dictionary.getVocabulas(vocabulaSet.stream()
                                                                              .map(voc -> voc.headWord)
                                                                              .collect(toSet()));
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
                        LOGGER.debug("on request \"{}\" nothing found, suggestions: {}", fQuery, suggestions);
                        boolean showTranslation = false;
                        if (!suggestions.isEmpty()) {
                            Platform.runLater(() -> hwData.setAll(suggestions));
                            String suggested;
                            if (suggestions.size() == 1 && Strings.partEquals(suggested = suggestions.get(0), fQuery)) {
                                LOGGER.debug("among suggested found present: \"{}\"", suggested);
                                dictionary.findVocabula(suggested).ifPresent(vocabula -> showResponse(vocabula, fQuery));
                            } else {
                                tabPane.selectTab(mainTab);
                            }
                        } else if (!showReferencingVocabula(fQuery)) {
                            Platform.runLater(() -> showTranslation(fQuery, requestFormatterGT));
                        }
                    }
                }
                if (changeOccurred = (defCount != dictionary.getDefinitionsCount())) updateStatistics(false);
            } catch (Exception e) {
//                LOGGER.error(Exceptions.getPackageStackTrace(e, BASE_PACKAGE));
                LOGGER.error("",e);
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
        Task<Optional<Vocabula>> task = new Task<>() {
            @Override
            protected Optional<Vocabula> call() {
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
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(PROJECT_HOME, HISTORY_FILENAME),
                                                         CREATE,
                                                         TRUNCATE_EXISTING))
        {
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

    private boolean saveDictionary() {
        saveRecentQueries();
        return Dictionary.save(dictionary, storageEn);
    }

    private Collection<String> findReferences(String pattern) {
        return findReferences(pattern, toggleRE.isSelected());
    }

    private Collection<String> findReferences(String pattern, boolean isRegex) {
        Function<String, Collection<String>> referencesFinder = pattrn -> indexer.getReferencesFinder()
                                                                                 .apply(pattrn, isRegex);
        return isRegex
                       ? referencesFinder.apply(pattern)
                       : referencesCache.computeIfAbsent(pattern, referencesFinder);
    }

    //	private void deleteHeadwords(Optional<String> selection) {
    private void deleteHeadwords() {
        Collection<String> headwords = new ArrayList<>();
        if (tabPane.getSelectedTabIndex() == 0) {
            headwords.addAll(selectedHWItems);
        } else if (tabPane.getActiveTab() instanceof ExpositorTab) {
            String activeViewHeadword = getActiveTabHeadword();
            if (!activeViewHeadword.isEmpty()) headwords.add(activeViewHeadword);
        }
        if (headwords.isEmpty()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, getDeleteMsg(headwords.size()), ButtonType.YES, ButtonType.NO);
        GUIUtil.hackAlert(confirm);
        confirm.showAndWait().filter(response -> response == ButtonType.YES).ifPresent(response -> {
            if (tabPane.getSelectedTabIndex() > 0 && headwords.contains(getActiveTabHeadword()))
                tabPane.removeActiveTab();
            List<String> available = headwords.stream()
                                              .map(String::trim)
                                              .filter(headword -> dictionary.containsVocabula(headword))
                                              .collect(toList());
            hwData.removeAll(available);
            updateTableState(false);
            Platform.runLater(getDeletionTask(available));
        });
    }

    private Task<Void> getDeletionTask(Collection<String> headwords) {
        return new Task<>() {
            @Override
            protected Void call() {
                LOGGER.debug("deleting entries: {}", headwords);
                if (!headwords.isEmpty()) {
                    headwords.forEach(dictionary::removeVocabula);
                    saveDictionary();
                    updateStatistics(true);
                    if (tabPane.getTabs().size() == 1) updateQueryBoxText(queryBoxValue.getValue(), true);
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

    private void pronounce(String text, boolean isArticle) {
        if (isArticle && !text.isEmpty()) {
            dictionary.findVocabula(text)
                      .ifPresent(vocabula -> Platform.runLater(() -> {
                          pronouncingService.clearQueue();
                          pronouncingService.enqueueAsync(vocabula.toString().replaceAll("[<>]", ""));
                      }));
        } else {
            Platform.runLater(() -> {
                pronouncingService.clearQueue();
                Supplier<String> contextProvider = () -> {
                    String selectedText = getSelectedText();
                    return selectedText.isEmpty() ? getActiveTabHeadword() : selectedText;
                };
                pronouncingService.enqueueAsync(text.isEmpty() ? contextProvider.get() : text, PRONUNCIATION_DELAY);
            });
        }
    }

    private void pronounce(String selection) {
        pronounce(selection, false);
    }

    private String getActiveTabHeadword() {
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
                     com.nicedev.util.Collections.toString(vocabulas, v -> v.headWord),
                     Arrays.toString(textsToHighlight));
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
//            LOGGER.debug("resulting content:\n{}", htmlContent);
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