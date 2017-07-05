package com.nicedev.vocabularizer.service;

import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.util.function.Supplier;

public class DelayedTaskService<R> extends ScheduledService<R> {

	private Supplier<Task<R>> taskSupplier;
	private int delay;

	public DelayedTaskService(Supplier<Task<R>> delayedTaskProvider, int delay) {
		taskSupplier = delayedTaskProvider;
		this.delay = delay;
	}

	@Override
	protected Task<R> createTask() {
		Task<R> task = taskSupplier.get();
		setDelay(Duration.millis(delay));
		return task;
	}

	@Override
	protected void succeeded() {
		super.succeeded();
		cancel();
	}

	void setTaskSupplier(Supplier<Task<R>> taskSupplier) {
		this.taskSupplier = taskSupplier;
	}

	public void setDelay(int newDelay) {
		delay = newDelay;
		setDelay(Duration.millis(delay));
	}

}
