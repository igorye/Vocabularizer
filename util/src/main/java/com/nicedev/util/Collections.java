package com.nicedev.util;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Collections {

	public static <T> String toString(Collection<T> source) {
		return toString(source, Object::toString, source.size());
	}
	
	public static <T> String toString(Collection<T> source, Function<T, String> mapper) {
		return toString(source, mapper, source.size());
	}
	
	public static <T> String toString(Collection<T> source, Function<T, String> mapper, int limit) {
		return source.stream()
							 .limit(limit)
							 .map(mapper)
							 .collect(Collectors.joining(", ", "[", "]"));
	}
	
}
