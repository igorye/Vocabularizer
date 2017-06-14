package com.nicedev.util;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class Exceptions {
	public static String getPackageStackTrace(Exception e, String packageNameRegex) {
		return String.format("%s at\n%s", e, getPackageStackTraceElementStrings(e, packageNameRegex));
	}
	
	private static String getPackageStackTraceElementStrings(Exception e, String packageNameRegex) {
		return Stream.of(e.getStackTrace())
				       .filter(ste -> ste.getClassName().matches(packageNameRegex))
				       .map(StackTraceElement::toString)
				       .collect(joining("\n"));
	}
}
