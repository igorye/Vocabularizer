package com.nicedev.vocabularizer.services.task;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.util.function.Function;

public class DelayedTaskService<R> extends ScheduledService<R> {
	
	private StringProperty filter = new SimpleStringProperty("");
	private Function<String, Task<R>> taskProvider;
	private int delay;
	
	public DelayedTaskService(Function<String, Task<R>> delayedTaskProvider, int delay) {
		taskProvider = delayedTaskProvider;
		this.delay = delay;
	}
	
	protected Task<R> createTask() {
		Task<R> task = taskProvider.apply(filter.get());
		setDelay(Duration.millis(delay));
		return task;
	}
	
	public void setFilter(String newFilter) {
		filter.set(newFilter);
		restart();
	}
	
	public String getFilter() {
		return filter.get();
	}
	
}
