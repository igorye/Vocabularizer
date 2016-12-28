package com.nicedev.util;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.nicedev.util.SimpleLog.log;
import static java.lang.Math.min;

public class QueuedCache<K extends Comparable<K>, V> {

	private K persistentKey = null;
	private Map<K, V> cacheMap;
	private Queue<K> cacheQueue;
//	private Map<V, K> equalsCache;
	private int capacity;
//	IdenticalCacheOptimizer<K, V> optimizer = null;

	public QueuedCache() {
		this(Integer.MAX_VALUE);
	}

	public QueuedCache(int capacity) {
		cacheQueue = new LinkedList<>();
		cacheMap = new LinkedHashMap<>();
//		equalsCache = new LinkedHashMap<>();
		this.capacity = capacity;
	}

	/*public QueuedCache(int capacity, IdenticalCacheOptimizer<K, V> optimizer) {
		this(capacity);
		this.optimizer = optimizer;
	}

	public void setOptimizer(IdenticalCacheOptimizer<K, V> optimizer) {
		this.optimizer = optimizer;
	}*/

	public QueuedCache(K persistentKey, int capacity) {
		this(capacity);
		this.persistentKey = persistentKey;
	}

	public V get(K key) {
		return cacheMap.get(key);
	}
	
	public V computeIfAbsent(K key, Function<? super K, ? extends V> mapper) {
		int size = cacheMap.size();
		V value = cacheMap.computeIfAbsent(key, mapper);
		if (size < cacheMap.size()) cacheQueue.offer(key);
		if (cacheQueue.size() > capacity) {
			K oldest = cacheQueue.poll();
			if (!oldest.equals(persistentKey) && !oldest.equals(key)) cacheMap.remove(oldest);
		}
		return value;
	}
	
	private void showContents() {
		String keys = cacheQueue.stream().map(k -> "\"" + k.toString() + "\"").collect(Collectors.joining(", "));
		log("\tcache: keys in cache: %s", keys);
		cacheQueue.forEach(k -> log("\tkey \"%s\" | val %s", k, getValue(k)));
	}
	
	private String getValue(K k) {
		String str = cacheMap.get(k).toString();
		return str.substring(0, min(str.length(),30));
	}
	
	private String valueToString(V v) {
		String str = v.toString();
		return str.substring(0, min(str.length(), 30));
	}
	
	public V put(K key, V value) {
		cacheQueue.offer(key);
		value = cacheMap.put(key, value);
		if (cacheQueue.size() >= capacity && cacheMap.size() >= capacity) {
			K oldest = cacheQueue.poll();
			if (oldest != persistentKey && oldest != key) cacheMap.remove(oldest);
		}
		return value;
	}

	public void clear() {
		cacheMap.clear();
		cacheQueue.clear();
	}
	
	public void remove(K key) {
		cacheMap.remove(key);
	}
	
	public void remove(Collection<K> keys) {
		keys.forEach(this::remove);
	}
	
	public void putPersistent(V val) {
		cacheMap.put(persistentKey, val);
	}
	
	public int size() {
		return cacheMap.keySet().size();
	}
}
