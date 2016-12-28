package com.nicedev.util;

import java.util.Collection;
import java.util.stream.Stream;

public class Streams {
	
	public static <T> Stream<T> getStream(Collection<T> sourceCollection, boolean parallel) {
		return parallel ? sourceCollection.parallelStream() : sourceCollection.stream();
	}
	
}
