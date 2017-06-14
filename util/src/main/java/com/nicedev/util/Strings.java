package com.nicedev.util;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

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
		String matchFlagsRegex = String.format("(?%s)", Stream.of(matchFlags).collect(joining("")));
		if (!isAValidPattern(matchFlagsRegex)) return ALWAYS_FAIL_MATCH_PATTERN;
		if ( isAValidPattern(regex) ) return matchFlagsRegex.concat(regex);
		// try to "fix" regex escaping certain service symbols
		String escapedRegex = escapeSymbols(regex, "[\\[\\]()^{}&*.+-]");
		String result = matchFlagsRegex.concat(escapedRegex);
		// mock regex with always-fail-match-pattern if we can't "fix" available one
		if (!isAValidPattern(result)) result = ALWAYS_FAIL_MATCH_PATTERN;
		return result;
	}
	
	public static String escapeSymbols(String text, String regex) {
		return Stream.of(text.split(String.format("(?=%s)", regex)))
				       .map(s -> (s.matches("\\p{Punct}.*")) ? "\\".concat(s) : s)
				       .collect(joining());
	}
	
	public static String toNonstrictSymbolsRegex(String source, String regex) {
		return Stream.of(source.split(String.format("(?<=%s)", regex)))
				       .map(s -> (s.matches("\\p{Punct}.*")) ? "\\".concat(s) : s)
				       .collect(joining());
	}
	
	private static String forEachSymbol(String regex, Function<? super String, ? extends String> transformer) {
		return Stream.of(regex.split("|")).map(transformer).collect(joining());
	}
	
	// returns substring matching provided regex's 1st (or sole) capturing group
	public static String regexSubstr(String regex, String source) {
		Matcher matcher = Pattern.compile(regex).matcher(source);
		if (matcher.find()) return matcher.group(matcher.groupCount() > 0 ? 1 : 0);
		return "";
	}
	
	// check for partial equivalence in collocations
	public static boolean partEquals(String compared, String match) {
		String pattern = compared.contains(" ")
				                 ? String.format("(?i)[\\w\\s()/]*(?<![()])%s(?![()])[\\w\\s()/]*", match)
				                 : String.format("(?i)(\\w+[-])*(?<![()])%s(?![()])([/-]\\w+)*", match);
		return compared.matches(pattern);
	}
	
	// check for equality ignoring case allowing mismatch at punctuation chars
	public static boolean equalIgnoreCaseAndPunct(String compared, String match) {
		if (compared.isEmpty() || match.isEmpty()) return false;
		if (compared.length() == 1) return compared.equalsIgnoreCase(match);
		compared = compared.replaceAll("[^\\p{L}]", "").toLowerCase().replace(" ", "");
		match = match.replaceAll("[^\\p{L}]", "").toLowerCase().replace(" ", "");
		return compared.equals(match);
	}
	
	// checks for equality ignoring case allowing mismatch at punctuation chars and partial equivalence
	public static boolean isSimilar(String compared, String match) {
		if (compared.isEmpty() || match.isEmpty()) return false;
		if (compared.length() == 1) return compared.equalsIgnoreCase(match);
		String comparedLC = compared.toLowerCase().replaceFirst("[^\\p{L}]", "").replace(" ", "");
		String matchLC = match.toLowerCase().replaceFirst("[^\\p{L}]", "").replace(" ", "");
		return comparedLC.contains(matchLC);
	}
	
}
