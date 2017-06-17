package com.nicedev.vocabularizer.service;

import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.util.function.Function;

public class DelayedTaskService<R> extends ScheduledService<R> {
	private String filter = "";
	
	private final Function<String, Task<R>> taskProvider;
	private int delay;
	public DelayedTaskService(Function<String, Task<R>> delayedTaskProvider, int delay) {
		taskProvider = delayedTaskProvider;
		this.delay = delay;
	}
	@Override
	protected Task<R> createTask() {
		Task<R> task = taskProvider.apply(filter);
		setDelay(Duration.millis(delay));
		return task;
	}
	
	@Override
	protected void succeeded() {
		super.succeeded();
		cancel();
	}
	
	public void setFilter(String newFilter) {
		filter = newFilter;
		restart();
	}
	
	public void setDelay(int newDelay) {
		delay = newDelay;
		setDelay(Duration.millis(delay));
	}
	

}
