package com.nicedev.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.max;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.stream.Collectors.toList;

public class History<T> {
	private final String HISTORY_FILENAME = "vocabularizer.history";
	private List<T> list = new LinkedList<>();
	private int current = 0;
	private int capacity;
	
	public History(int capacity) {
		this.capacity = capacity;
	}
	
	public void add(T item) {
		list.add(item);
		if (list.size() > capacity) list.remove(0);
		current = list.size();
//      System.out.printf("Added to History \"%s\"%n", item);
	}
	
	public T last() {
		return list.get(list.size() - 1);
	}
	
	public T next() {
		if (++current >= list.size()) current = list.size() - 1;
		if (list.isEmpty()) return null;
		return list.get(current);
	}
	
	public T prev() {
		if (--current < 0) current = 0;
		if (current >= list.size()) current = list.size() - 1;
		if (list.isEmpty()) return null;
		return list.get(current);
	}
	
	public boolean isEmpty() {
		return list.isEmpty();
	}
	
	public boolean hasNext() {
		return current < list.size() - 1;
	}
	
	public Collection<T> getAll() {
		return Collections.unmodifiableCollection(list);
	}
	
	public void save(String path) {
		try (BufferedWriter bw = Files.newBufferedWriter(get(path, HISTORY_FILENAME), CREATE, TRUNCATE_EXISTING)) {
			for (T item : list.stream().distinct().filter(s -> Strings.notBlank.test(s.toString())).collect(toList())) {
				bw.write(item.toString());
				bw.newLine();
			}
		} catch (IOException e) {
		}
	}
	
	@SuppressWarnings("unchecked")
	public void load(String path) {
		try (BufferedReader br = Files.newBufferedReader(get(path, HISTORY_FILENAME))) {
			br.mark(1_000_000);
			int linesCount = (int) br.lines().count();
			br.reset();
			list.addAll(br.lines().skip(max(0, linesCount - 3 * capacity)).map(s -> (T) s).collect(toList()));
			current = list.size();
		} catch (IOException | ClassCastException e) {
		}
	}
}
