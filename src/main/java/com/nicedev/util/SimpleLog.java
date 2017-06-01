package com.nicedev.util;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class SimpleLog {
	
	public static void log(String s, Object... args) {
			System.err.println(Arrays.stream(new Throwable().getStackTrace())
											.skip(1)
											.map(StackTraceElement::toString)
											.filter(sts -> sts.contains(".nicedev."))
											.map(SimpleLog::extractLine)
											.collect(joining("\n")));
		Object[] tabbedArgs = Stream.of(args)
				                      .map(arg -> arg instanceof String ? ((String) arg).replaceAll("\n", "\n\t") : arg)
				                      .collect(Collectors.toList()).toArray();
		System.err.printf("\t".concat(s) + "%n", tabbedArgs);
	}
	
	private static String extractLine(String stackTraceElement) {
		int from = stackTraceElement.indexOf("(");
		return stackTraceElement.substring(from, stackTraceElement.length());
	}
}
