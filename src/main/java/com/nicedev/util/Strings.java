package com.nicedev.util;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class Strings {
	
	public static final Predicate<String> BLANK = Strings::blank;
	public static final Predicate<String> NOT_BLANK = Strings::notBlank;
	public static final Predicate<String> ALL_ALPHAS = Strings::allAlphas;
	public static final Predicate<String> NO_DIGITS = Strings::noDigits;
	public static final String ALWAYS_FAIL_MATCH_PATTERN = "^\b$"; //or "(?!)", "\B\b"
	
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
		String escapedRegex = stream(regexParts)
				                      .map(s -> {
					                      if (s.matches("^[\\[\\]()^&*.+-]") && !s.equals("\\")) s = "\\".concat(s);
					                      return s;
				                      })
				                      .collect(joining(""));
		String result = matchFlagsRegex.concat(escapedRegex);
		//mock regex with always-fail-match-pattern if we can't "repair" available one
		if (!isAValidPattern(result)) result = ALWAYS_FAIL_MATCH_PATTERN;
		SimpleLog.log("getValidPattern: result==%s", result);
		return result;
	}
	
	public static String escapeRegEx(String regex, String escapeTarget) {
		return Stream.of(regex.split("|"))
				       .map(s -> s.length()==1 && s.equals(escapeTarget) ? String.format("\\%s", escapeTarget) : s)
				       .collect(joining());
	}
}
