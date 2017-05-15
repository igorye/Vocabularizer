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
		else sb.append(source);
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
}
