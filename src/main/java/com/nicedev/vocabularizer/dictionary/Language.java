package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sugarik on 02.09.2016.
 */
public class Language implements Serializable, Comparable {

	public final String name;
	public final String shortName;
	public final String alphabet;

	private static Map<String, Language> langs;

	public static String ENGLISH_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	public static String RUSSIAN_ALPHABET = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЬЫЪЭЮЯабвгдеёжзийклмнопрстуфхцчшщьыъэюя";

	static {
		String lang1 = "english";
		String lang2 = "russian";
		langs = new TreeMap<>();
		langs.put(lang1, new Language(lang1, "", ENGLISH_ALPHABET));
		langs.put(lang2, new Language(lang2, "", RUSSIAN_ALPHABET));
	}

	public Language(String langName, String shortName, String alphabet) {
		this.name = langName;
		this.shortName = shortName;
		this.alphabet = alphabet;
		langs.put(name, this);
	}

	public Language(String langName, String shortName) {
		this.name = langName;
		this.shortName = shortName;
		this.alphabet = langs.get(langName).alphabet;
	}



	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Language language = (Language) o;

		if (!name.equals(language.name)) return false;
		return alphabet.equals(language.alphabet);
	}

	@Override
	public int hashCode() {
		int result = name.hashCode();
		result = 31 * result + alphabet.hashCode();
		return result;
	}

	public static boolean hasForeignChar(String src, String langName) {
		char[] alpabetFromSrc = src.toCharArray();
		Arrays.sort(alpabetFromSrc);
		String pattern1 = String.format("[%s]", String.valueOf(alpabetFromSrc[0]));
		String pattern2 = String.format("[%s]", String.valueOf(alpabetFromSrc[alpabetFromSrc.length-1]));
		Matcher m1 = Pattern.compile(pattern1).matcher(langs.get(langName).alphabet);
		Matcher m2 = Pattern.compile(pattern2).matcher(langs.get(langName).alphabet);
		return !(m1.find() && m2.find());
	}

	@Override
	public int compareTo(Object o) {
		return alphabet.compareTo(((Language) o).alphabet);
	}
}
