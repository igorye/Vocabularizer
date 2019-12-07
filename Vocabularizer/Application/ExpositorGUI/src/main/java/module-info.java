module vocabularizer.gui {
    requires javafx.fxml;
    requires javafx.graphics;
    requires gtts;
    requires javafx.controls;
    requires javafx.web;
    requires jdk.jsobject;
    requires vocabularizer.dictionary;
    requires nicedev.util;
    exports com.nicedev.vocabularizer;
    exports com.nicedev.vocabularizer.gui to javafx.fxml,javafx.web;
    opens com.nicedev.vocabularizer to javafx.fxml;
}