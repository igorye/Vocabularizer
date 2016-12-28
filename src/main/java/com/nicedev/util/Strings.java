package com.nicedev.util;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class Strings {
	
	public static final Predicate<String> Blank = Strings::blank;
	public static final Predicate<String> notBlank = Strings::notBlank;
	public static final Predicate<String> allAlphas = Strings::allAlphas;
	public static final Predicate<String> noDigits = Strings::noDigits;

	public static boolean notBlank(String str) {
		return !blank(str);
	}
	
	public static boolean blank(String str) {
		return str.isEmpty() || str.matches("\\p{Blank}+");
	}
	
	public static boolean allAlphas(String str) {
		return str.matches("\\p{Alpha}+");
	}
	
	public static boolean noDigits(String str) {
		return str.matches("[^\\p{Digit}]+");
	}
	
	public static boolean isAValidPattern(String filter) {
		try {
			Pattern.compile(filter);
		} catch (PatternSyntaxException e) {
			return false;
		}
		return true;
	}
	
	public static String getValidPattern(String regex, String... matchFlags) {
		String matchFlagsRegex = String.format("(?%s)", stream(matchFlags).collect(joining("")));
		if (isAValidPattern(matchFlagsRegex) && isAValidPattern(regex)) return matchFlagsRegex.concat(regex);
		String[] regexParts = regex.split("(?<=[[\\[\\]()^&*.+-]&&[^\\\\]])|(?=[[\\[\\]()^&*.+-]&&[^\\\\]])");
		String slashedRegex = stream(regexParts)
				                      .map(s -> {
					                      if (s.matches("^[\\[\\]()^&*.+-]") && !s.equals("\\")) s = "\\".concat(s);
					                      return s;
				                      })
				                      .collect(joining(""));
		String result = matchFlagsRegex.concat(slashedRegex);
		//mock regex with always-fail-pattern if we can't "repair" available one
		if (!isAValidPattern(result)) result = "^\b$"; //or "(?!)", "\B\b"
		SimpleLog.log("getValidPattern: result==%s", result);
		return result;
	}
}
