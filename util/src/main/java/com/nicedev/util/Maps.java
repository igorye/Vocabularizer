package com.nicedev.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Maps {
	
	private static final Collection EMPTY_COLLECTION = new ArrayList<>();
	
	@SuppressWarnings("unchecked")
	private static <K, V extends Collection> Function<K, V> getDefaultMappingFunction() {
		return k -> (V) EMPTY_COLLECTION;
	}
	
	private static <K, V extends Collection> Supplier<Map<K, V>> getDefaultSupplier() {
		return HashMap::new;
	}
	
	// apply action on dest with each entry from source
	// apply mappingFunction if key is missing in dest
	public static <K, V> void apply(BiConsumer<V, V> action, Map<K, V> dest, Map<K, V> source,
	                                Function<K, V> mappingFunction) {
		source.keySet()
				.forEach(key -> action.accept(dest.computeIfAbsent(key, mappingFunction), source.get(key)));
	}
	
	// merge source into dest
	// applies mappingFunction if dest key is missing
	@SuppressWarnings("unchecked")
	public static <K, V extends Collection> void merge(Map<K, V> dest, Map<K, V> source, Function<K, V> mappingFunction) {
		apply(Collection::addAll, dest, source, mappingFunction);
	}
	
	// returns map that equals m1.merge(m2)
	// applies DefaultMappingFunction if dest key is missing
	public static <K, V extends Collection> Map<K, V> combine(Map<K, V> m1, Map<K, V> m2) {
		return combine(m1, m2, getDefaultMappingFunction());
	}
	
	// returns map that equals m1.merge(m2)
	// applies mappingFunction if dest key is missing
	public static <K, V extends Collection> Map<K, V> combine(Map<K, V> m1, Map<K, V> m2, Function<K, V> mappingFunction) {
		return combine(getDefaultSupplier(), m1, m2, mappingFunction);
	}
	
	// returns Map that equals m1.merge(m2)
	// supplier is a function that creates a new result container
	// DEFAULT_MAPPING_FUNCTION applied if dest key is missing
	@SuppressWarnings("unchecked")
	public static <K, V extends Collection> Map<K, V> combine(Supplier<Map<K, V>> supplier,
	                                                          Map<K, V> m1, Map<K, V> m2,
	                                                          Function<K, V> mappingFunction) {
		Map<K, V> result = supplier.get();
		result.putAll(m1);
		apply(Collection::addAll, result, m2, mappingFunction);
		return result;
	}
	
	// returns Map that equals m1.merge(m2)
	// supplier is a function that creates a new result container
	public static <K, T> Map<K, Collection<T>> combine(Map<K, Collection<T>> m1, Map<K, Collection<T>> m2, boolean parallelizable) {
		Map<K, Collection<T>> result = Maps.clone(m1, parallelizable);
		Maps.mergeLeft(result, m2, parallelizable);
		return result;
	}
	
	//merges right into left
	@SuppressWarnings("unchecked")
	public static <K, V extends Collection> void mergeLeft(Map<K, V> left, Map<K, V> right, boolean parallelizable) {
		parallelizable &= left instanceof ConcurrentMap;
		(parallelizable ? right.keySet().parallelStream() : right.keySet().stream())
				.forEach(key -> left.merge(key, right.get(key), (c1, c2) -> { c1.addAll(c2); return c1; }));
	}
	
	public static <K, V extends Collection> Map<K, V> clone(Map<K, V> m1, boolean parallelizable) {
		return parallelizable ? new ConcurrentHashMap<>(m1) : clone(m1);
	}
	
	//returns a Map with the same contents and the same underlying implementation as provided map
	@SuppressWarnings("unchecked")
	public static <K, V extends Collection> Map<K, V> clone(Map<K, V> map) {
		Class clazz = map.getClass();
		Object result = java.util.Collections.emptyMap();
		try {
			Constructor ctor = clazz.getConstructor(Map.class);
			result = ctor.newInstance(map);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
		return (Map<K, V>) result;
	}
}
