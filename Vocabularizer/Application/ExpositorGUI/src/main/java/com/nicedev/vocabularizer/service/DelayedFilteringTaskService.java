package com.nicedev.vocabularizer.service;

import javafx.concurrent.Task;

import java.util.function.Function;

public class DelayedFilteringTaskService<R> extends DelayedTaskService<R> {
	private String filter = "";

	public DelayedFilteringTaskService(Function<String, Task<R>> filteringTaskSupplier, int delay) {
		super(null, delay);
		setTaskSupplier(() -> filteringTaskSupplier.apply(filter));
	}

	public void setFilter(String newFilter) {
		filter = newFilter;
		restart();
	}

}
