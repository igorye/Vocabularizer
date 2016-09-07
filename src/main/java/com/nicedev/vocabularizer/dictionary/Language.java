package com.nicedev.vocabularizer.dictionary;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import static java.lang.String.format;

public class Language implements Serializable, Comparable {

	public final String langName;
	public final String shortName;
	public final String alphabet;
	final public Map<String, PartOfSpeech> partsOfSpeech;


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
		this.langName = langName;
		this.shortName = shortName;
		this.alphabet = alphabet;
		langs.put(this.langName, this);

		partsOfSpeech = loadPartsOfSpeech();
	}

	private Map<String, PartOfSpeech> loadPartsOfSpeech() {
		Properties langProps = new Properties();
		String home = System.getProperties().getProperty("user.home");
		Map<String, PartOfSpeech> partsOS = new TreeMap<>();
		try (InputStream in = new FileInputStream(new File(home, format("%s.properties", langName)))) {
			langProps.load(in);
			int nPoS = 0;
			String PoSName;
			while ((PoSName = langProps.getProperty(format("name%d", ++nPoS))) != null) {
				partsOS.put(PoSName, new PartOfSpeech(this, PoSName, PoSName.substring(0, 4)));
			}
			partsOS.put(PartOfSpeech.ANY, new PartOfSpeech(this, PartOfSpeech.ANY));
			partsOS.put(PartOfSpeech.UNDEFINED, new PartOfSpeech(this, PartOfSpeech.UNDEFINED));
			partsOS.put(PartOfSpeech.COMPOSITE, new PartOfSpeech(this, PartOfSpeech.COMPOSITE));
		} catch (IOException e) {
			System.err.format("Unable to read language configuration. %s.properties file is corrupt or missing at" +
					                  " %s. %n", langName, home);
			return Collections.<String, PartOfSpeech>emptyMap();
		}
		return Collections.unmodifiableMap(partsOS);
	}

	public Language(String langName, String shortName) {
		this(langName, shortName, langs.get(langName).alphabet);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Language language = (Language) o;
		if (!langName.equals(language.langName)) return false;
		return alphabet.equals(language.alphabet);
	}

	@Override
	public int hashCode() {
		int result = langName.hashCode();
		result = 31 * result + alphabet.hashCode();
		return result;
	}

	public static boolean charsMatchLanguage(String src, Language lang) {
		return charsMatchLanguage(src, lang.langName);
	}

	public static boolean charsMatchLanguage(String src, String langName) {
		return CMLviaContains(src, langName);
//		return CMLviaMatch(src, langName);
	}

	private static boolean CMLviaMatch(String src, String langName) {
		char[] examinedChars = src.toCharArray();
		Arrays.sort(examinedChars);
		String alphabet = langs.get(langName).alphabet;
		String probablyCorrectChar1 = String.valueOf(examinedChars[0]);
		String probablyCorrectChar2 = String.valueOf(examinedChars[examinedChars.length - 1]);
		return Pattern.compile(probablyCorrectChar1).matcher(alphabet).find()
				         && Pattern.compile(probablyCorrectChar2).matcher(alphabet).find();
	}

	private static boolean CMLviaContains(String src, String langName) {
		char[] examinedChars = src.toCharArray();
		Arrays.sort(examinedChars);
		String alphabet = langs.get(langName).alphabet;
		String probablyCorrectChar1 = String.valueOf(examinedChars[0]);
		String probablyCorrectChar2 = String.valueOf(examinedChars[examinedChars.length - 1]);
		return alphabet.contains(probablyCorrectChar1) && alphabet.contains(probablyCorrectChar2);
	}

	@Override
	public int compareTo(Object o) {
		return alphabet.compareTo(((Language) o).alphabet);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append(format("%s(%s)%n", langName, shortName.length() != 0 ? shortName : langName.substring(0,2)));
		res.append(partsOfSpeech.keySet());
		res.append("\n");
		return res.toString();
	}
}
