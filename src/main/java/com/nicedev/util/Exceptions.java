package com.nicedev.util;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class Exceptions {
	public static String getPackageStackTrace(Exception e, String packageName) {
		return String.format("%s at\n%s", e, getPackageStackTraceElementStrings(e, packageName));
	}
	
	private static String getPackageStackTraceElementStrings(Exception e, String packageName) {
		return Stream.of(e.getStackTrace())
				       .filter(ste -> ste.getClassName().contains(packageName))
				       .map(StackTraceElement::toString)
				       .collect(joining("\n"));
	}
}
