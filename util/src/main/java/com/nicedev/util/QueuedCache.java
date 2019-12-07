package com.nicedev.util;

import java.util.*;
import java.util.Collections;
import java.util.function.Function;

public class QueuedCache<K, V> implements Map<K, V>{

	private K persistentKey = null;
	private Map<K, V> cacheMap;
	private Queue<K> cacheQueue;
	private int capacity;

	public QueuedCache() {
		this(Integer.MAX_VALUE);
	}

	public QueuedCache(int capacity) {
		cacheQueue = new LinkedList<>();
		cacheMap = new LinkedHashMap<>();
		this.capacity = capacity;
	}

	public QueuedCache(K persistentKey, int capacity) {
		this(capacity);
		this.persistentKey = persistentKey;
	}

	public V computeIfAbsent(K key, Function<? super K, ? extends V> mapper) {
		int size = cacheMap.size();
		V value = cacheMap.computeIfAbsent(key, mapper);
		if (size < cacheMap.size()) cacheQueue.offer(key);
		if (cacheQueue.size() > capacity) {
			K oldest = cacheQueue.poll();
			if (!persistentKey.equals(oldest) && !key.equals(oldest)) cacheMap.remove(oldest);
		}
		return value;
	}

	public V put(K key, V value) {
		cacheQueue.offer(key);
		value = cacheMap.put(key, value);
		if ((cacheQueue.size() >= capacity) && (cacheMap.size() >= capacity)) {
			K oldest = cacheQueue.poll();
			if (oldest != persistentKey && oldest != key) cacheMap.remove(oldest);
		}
		return value;
	}

	@Override
	public V remove(Object key) {
		cacheQueue.remove(key);
		return cacheMap.remove(key);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		m.forEach(this::put);
	}

	public void clear() {
		cacheMap.clear();
		cacheQueue.clear();
	}

	@Override
	public Set<K> keySet() {
		return Collections.unmodifiableSet(cacheMap.keySet());
	}

	@Override
	public Collection<V> values() {
		return Collections.unmodifiableCollection(cacheMap.values());
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return Collections.unmodifiableSet(cacheMap.entrySet());
	}

	public void putPersistent(V val) {
		cacheMap.put(persistentKey, val);
	}

	@Override
	public int size() {
		return cacheMap.size();
	}

	@Override
	public boolean isEmpty() {
		return cacheMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return cacheMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return cacheMap.containsValue(value);
	}

	@Override
	public V get(Object key) {
		return cacheMap.get(key);
	}

}
