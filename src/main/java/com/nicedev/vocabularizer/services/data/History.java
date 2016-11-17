package com.nicedev.vocabularizer.services.data;

import java.util.*;

public class History<T> {
	private List<T> list = new LinkedList<>();
	private int current = 0;
	private int capacity;

	public History(int capacity) {
		this.capacity = capacity;
	}

	public void add(T item) {
		list.add(item);
		if(list.size() > capacity) list.remove(0);
		current = list.size()-1;
	}

	public T last() {
		return list.get(capacity-1);
	}

	public T next() {
		if(++current >= list.size()) current = list.size() - 1;
		if(list.isEmpty()) return null;
		return list.get(current);
	}

	public T prev() {
		if(--current < 0 ) current = 0;
		if(list.isEmpty()) return null;
		return list.get(current);
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}
}
