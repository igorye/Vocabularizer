package com.nicedev.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Collections.emptyMap;

public class Maps {
	
	private static final Collection EMPTY_COLLECTION = new ArrayList<>();
	
	@SuppressWarnings("unchecked")
	private static <K, T> Function<K, Collection<T>> getDefaultMappingFunction() {
		return k -> (Collection<T>) EMPTY_COLLECTION;
	}
	
	private static <K, T> Supplier<Map<K, Collection<T>>> getDefaultSupplier() {
		return HashMap::new;
	}
	
	//apply action on dest with each entry from source; apply defaultMappingFunction if key is missing in dest
	public static <K, V> void apply(BiConsumer<V, V> action, Map<K, V> dest, Map<K, V> source,
									Function<K, V> mappingFunction) {
		source.keySet()
				.forEach(key -> action.accept(dest.computeIfAbsent(key, mappingFunction), source.get(key)));
	}
	
	//merge source into dest; applies mappingFunction if dest key is missing
	public static <K, T> void merge(Map<K, Collection<T>> dest, Map<K, Collection<T>> source,
	                                Function<K, Collection<T>> mappingFunction) {
		apply(Collection::addAll, dest, source, mappingFunction);
	}
	
	//returns map that equals dest.merge(source); applies DefaultMappingFunction if dest key is missing
	public static <K, T> Map<K, Collection<T>> combine(Map<K, Collection<T>> m1, Map<K, Collection<T>> m2) {
		return combine(m1, m2, getDefaultMappingFunction());
	}
	
	//returns map that equals dest.merge(source); applies mappingFunction if dest key is missing
	public static <K, T> Map<K, Collection<T>> combine(Map<K, Collection<T>> m1, Map<K, Collection<T>> m2,
	                                                   Function<K, Collection<T>> mappingFunction) {
		return combine(getDefaultSupplier(), m1, m2, mappingFunction);
	}
	
	// returns Map that equals dest.merge(source)
	// supplier is a function that creates a new result container
	// DEFAULT_MAPPING_FUNCTION applied if dest key is missing
	public static <K, T> Map<K, Collection<T>> combine(Supplier<Map<K, Collection<T>>> supplier,
	                                                   Map<K, Collection<T>> m1, Map<K, Collection<T>> m2,
	                                                   Function<K, Collection<T>> mappingFunction) {
		Map<K, Collection<T>> result = supplier.get();
		result.putAll(m1);
		apply(Collection::addAll, result, m2, mappingFunction);
		return result;
	}
	
	//merges right into left
	public static <K, T> void mergeLeft(Map<K, Collection<T>> left, Map<K, Collection<T>> right, boolean parallelizable) {
		parallelizable &= left instanceof ConcurrentMap;
		(parallelizable ? right.keySet().parallelStream() : right.keySet().stream())
				.forEach(key -> left.merge(key, right.get(key), (c1, c2) -> { c1.addAll(c2); return c1; }));
	}
	
	public static <K, T> Map<K, Collection<T>> clone(Map<K, Collection<T>> m1, boolean parallelizable) {
		return parallelizable ? new ConcurrentHashMap<>(m1) : clone(m1);
	}
	
	//returns a Map with the same contents and same underlying implementation as the "map"
	@SuppressWarnings("unchecked")
	public static <K, T> Map<K, Collection<T>> clone(Map<K, Collection<T>> map) {
		Class<?> clAss = map.getClass();
		Object result = emptyMap();
		try {
			Constructor<?> ctor = clAss.getConstructor(Map.class);
			result = ctor.newInstance(map);
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
		return (Map<K, Collection<T>>) result;
	}
}
