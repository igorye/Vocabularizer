package com.nicedev.util;

import java.util.Arrays;

import static java.util.stream.Collectors.joining;

public class SimpleLog {
	
	public static void log(String s, Object... args) {
			System.out.println(Arrays.stream(new Throwable().getStackTrace())
											.skip(1)
											.map(StackTraceElement::toString)
											.filter(sts -> sts.contains(".nicedev."))
											.map(SimpleLog::extractLine)
											.collect(joining("\n")));
			System.out.printf("\t" + s + "%n", args);
	}
	
	private static String extractLine(String stackTraceElement) {
		int from = stackTraceElement.indexOf("(");
		return stackTraceElement.substring(from, stackTraceElement.length());
	}
}
