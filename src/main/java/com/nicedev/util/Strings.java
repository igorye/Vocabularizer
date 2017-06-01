package com.nicedev.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class Strings {
	
	private static final String ALWAYS_FAIL_MATCH_PATTERN = "^\b$"; //or "(?!)", "\B\b"
	
	public static boolean notBlank(String str) {
		return !isBlank(str);
	}
	
	public static boolean isBlank(String str) {
		return str.isEmpty() || str.matches("\\p{Blank}+");
	}
	
	public static boolean allAlphas(String str) {
		return str.matches("\\p{Alpha}+");
	}
	
	public static boolean noDigits(String str) {
		return str.matches("[^\\p{Digit}]+");
	}
	
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static boolean isAValidPattern(String filter) {
		try {
			Pattern.compile(filter);
		} catch (PatternSyntaxException e) {
			return false;
		}
		return true;
	}
	
	public static String getValidPatternOrFailAnyMatch(String regex, String... matchFlags) {
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
		// mock regex with always-fail-match-pattern if we can't "repair" available one
		if (!isAValidPattern(result)) result = ALWAYS_FAIL_MATCH_PATTERN;
		SimpleLog.log("getValidPatternOrFailAnyMatch: result==%s", result);
		return result;
	}
	
	public static String regexEscapeSymbols(String regex, String escapeRegex) {
		return forEachSymbol(regex, escapeRegex, s -> s.equals(escapeRegex) || s.matches(escapeRegex) ? String.format("\\%s", s) : s);
	}
	
	public static String regexToNonstrictSymbols(String regex, String symbolRegex) {
		return forEachSymbol(regex, symbolRegex, s -> s.equals(symbolRegex) || s.matches(symbolRegex) ? String.format("%s?", s) : s);
	}
	
	private static String forEachSymbol(String regex, String escapeRegex, Function<? super String, ? extends String> transformer) {
		return Stream.of(regex.split("|")).map(transformer).collect(joining());
	}
	
	// return substring matching provided regex's 1st (or sole) capturing group
	public static String regexSubstr(String regex, String source) {
		Matcher matcher = Pattern.compile(regex).matcher(source);
		if (matcher.find()) return matcher.group(matcher.groupCount() > 0 ? 1 : 0);
		return "";
	}
	
}
