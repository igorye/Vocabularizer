package com.nicedev.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Html {
	
	static Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());
	
	// wrap sole group match in specified html tag <tag> of class <tag class='className'>
	public static String wrapInTag(String source, String match, String tag, String className) {
		Matcher matcher = Pattern.compile(match).matcher(source);
		StringBuilder sb = new StringBuilder();
		String wrapFmt = className.isEmpty() ? "<%1$s>%3$s</%1$s>" : "<%1$s class=\"%2$s\">%3$s</%1$s>";
		int matchEnd = -1;
		try {
		while (matcher.find()) {
			int targetGroup = matcher.groupCount() > 1 ? 1 : 0;
			String matched = matcher.group(targetGroup);
			while (targetGroup < matcher.groupCount() && matched == null)
				matched = matcher.group(++targetGroup);
			String wrapped = String.format(wrapFmt, tag, className, matched);
			int matchStart = matcher.start(targetGroup);
			if (matchStart < 0) matchStart = matcher.start(targetGroup + 1);
			if (matchEnd < 0) {
				sb.append(source, 0, matchStart);
			} else {
				sb.append(source, matchEnd, matchStart);
			}
			sb.append(wrapped);
			matchEnd = matcher.end(targetGroup);
		}
		if (matchEnd > 0)
			sb.append(source, matchEnd, source.length());
		else
			sb.append(source);
		return sb.toString();
		} catch (Exception e) {
			LOGGER.error("{}. Unable to wrap {} at {}", e.toString(), match, source);
		}
		return source;
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
