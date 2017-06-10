package com.nicedev.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Html {
	
	public static String wrapInTag(String source, String match, String tag, String className) {
		Matcher matcher = Pattern.compile(match).matcher(source);
		StringBuilder sb = new StringBuilder();
		String wrapFmt = className.isEmpty() ? "<%1$s>%3$s</%1$s>" : "<%1$s class=\"%2$s\">%3$s</%1$s>";
		int lastEnd = -1;
		while (matcher.find()) {
			int targetGroup = matcher.groupCount() > 1 ? 1 : 0;
			String matched = matcher.group(targetGroup);
			String wrapped = String.format(wrapFmt, tag, className, matched);
			if (lastEnd < 0) {
				sb.append(source, 0, matcher.start(targetGroup) );
			} else {
				sb.append(source, lastEnd, matcher.start(targetGroup));
			}
			sb.append(wrapped);
			lastEnd = matcher.end(targetGroup);
		}
		if (lastEnd > 0)
			sb.append(source, lastEnd, source.length());
		else
			sb.append(source);
		return sb.toString();
	}
	
	public static String wrapInTag_(String source, String match, String tag, String className) {
		Matcher matcher = Pattern.compile(match).matcher(source);
		String result = source;
		String wrapFmt = className.isEmpty() ? "<%1$s>%3$s</%1$s>" : "<%1$s class=\"%2$s\">%3$s</%1$s>";
		while (matcher.find()) {
			String wrapped = matcher.group(matcher.groupCount() > 1 ? 1 : 0);
			result = source.replace(wrapped, String.format(wrapFmt, tag, className, wrapped));
		}
		return result;
	}
	
	// transform tags content into several tags made of source tag's split content
	// e.g. "<b>tok1, tok2, tok3</b>" -> "<b>tok1</b>, <b>tok2</b>, <b>tok3</b>"
	public static String splitTagContents(String source, String tagName, String splitRegex) {
		String regexBody = String.format("<(?<tagDefinition>%s[^<>]*)>[^<>]+((?<delimiter>%s)[^<>]+)+</%1$s>",
																			tagName, splitRegex);
		regexBody = Strings.getValidPatternOrFailAnyMatch(regexBody);
		if (!source.matches(String.format(".*%s.*",regexBody))) return source;
		String result = source;
		Matcher matcher = Pattern.compile(String.format("(?<tagToSplit>%s)",regexBody)).matcher(source);
		while (matcher.find()) {
			String sourceTag = matcher.group("tagToSplit");
			String sourceTagDefinition = matcher.group("tagDefinition");
			String splitDelimiter = matcher.group("delimiter");
			String taggedDelimiter = String.format("</%s>%s<%s>", tagName, splitDelimiter, sourceTagDefinition);
			result = result.replace(sourceTag, sourceTag.replaceAll(splitDelimiter, taggedDelimiter));
		}
		return result;
	}
}
