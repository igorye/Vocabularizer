package com.nicedev.vocabularizer.gui;

import com.nicedev.util.Exceptions;
import com.nicedev.util.Strings;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.text.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import static java.util.Optional.ofNullable;

public class FilteredTableCell extends TableCell<String, String> {
	
	static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());
	
	private final StringProperty filter;
	
	public FilteredTableCell(StringProperty filter) {
		this.filter = filter;
	}
	
	@Override
	protected void updateItem(String item, boolean empty) {
		super.updateItem(item, empty);
		try {
			if (empty || item == null) {
				setText(null);
				setGraphic(null);
			} else if (ofNullable(filter.get()).orElse("").isEmpty() || !Strings.isAValidPattern(getFilterRegex())) {
				setText(item);
				setGraphic(null);
			} else {
				setText(null);
				setGraphic(getFlowItem(item));
			}
		} catch (Exception e) {
			LOGGER.error("TableView.updateItem: unexpected exception {} {}", e,
								Exceptions.getPackageStackTrace(e, "com.nicedev"));
		}
	}
	
	private String getFilterRegex() {
		return String.format("(?=%s)|(?<=%1$s)", ofNullable(filter.get()).orElse(""));
	}
	
	private TextFlow getFlowItem(String item) {
		String regex = getFilterRegex();
		String[] parts = item.split(regex);
		List<Node> nodes = new ArrayList<>();
		for (String part : parts) {
			Text text = new Text(part);
			String pattern = ofNullable(filter.get()).orElse("");
			if (part.equalsIgnoreCase(pattern)) {
				Font fnt = text.getFont();
				text.setFont(Font.font(fnt.getFamily(), FontWeight.BOLD, FontPosture.REGULAR, fnt.getSize()));
			}
			nodes.add(text);
		}
		return new TextFlow(nodes.toArray(new Node[0]));
	}
}
