package com.nicedev.util;

import java.util.Comparator;
import java.util.function.Predicate;

public class Comparators {
	
	public static <T> Comparator<T> firstComparing(Predicate<T> predicate) {
		return (o1, o2) -> {
			if (predicate.test(o1) && !predicate.test(o2)) return -1;
			if (!predicate.test(o1) && predicate.test(o2)) return 1;
			return 0;
		};
	}
	
	public static Comparator<String> startsWithCmpr(String str) {
		return startsWithCmpr(str, false);
	}
	
	public static Comparator<String> startsWithCmpr(String str, boolean ignoreCase) {
		Predicate<String> predicate = s -> ignoreCase ? s.toLowerCase().startsWith(str.toLowerCase())
				                                   : s.startsWith(str);
		return firstComparing(predicate);
	}
	
	public static Comparator<String> indexOfCmpr(String subStr) {
		return Comparator.comparingInt(s -> {
			int index = s.indexOf(subStr);
			return index >= 0 ? index : Integer.MAX_VALUE;
		});
	}
}

