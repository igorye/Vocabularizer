package com.nicedev.vocabularizer.dictionary;

import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

public class Language implements Serializable, Comparable {
	
	private static final long serialVersionUID = 6763466261152320139L;
	public static String ENGLISH_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	public static String RUSSIAN_ALPHABET = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЬЫЪЭЮЯабвгдеёжзийклмнопрстуфхцчшщьыъэюя0123456789";
	private static Map<String, Language> langs;

	static {
		String lang1 = "english";
		String lang2 = "russian";
		langs = new TreeMap<>();
		langs.put(lang1, new Language(lang1, "en", ENGLISH_ALPHABET));
		langs.put(lang2, new Language(lang2, "ru", RUSSIAN_ALPHABET));
	}
	
	public final String langName;
	public final String shortName;
	public final String alphabet;
	final public Map<String, PartOfSpeech> partsOfSpeech;

	public Language(String langName, String shortName, String alphabet) {
		this.langName = langName;
		this.shortName = shortName;
		this.alphabet = alphabet;
		langs.put(this.langName, this);

		partsOfSpeech = loadPartsOfSpeech();
	}

	public Language(String langName, String shortName) {
		this(langName, shortName, langs.get(langName).alphabet);
	}

	public Language(String langName) {
		this(langName, langName.substring(0,3), langs.get(langName).alphabet);
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
		String copy = src.toLowerCase().replace("[^\\p{L}]", "");
		char[] examinedChars = src.toCharArray();
		Arrays.sort(examinedChars);
		String alphabet = langs.get(langName).alphabet;
		int first = 0,
				last = examinedChars.length - 1;
		String regex = "[^\\p{L}]";
		String probablyCorrectChar1 = String.valueOf(examinedChars[first]);
		int wrongPos = (probablyCorrectChar1.matches(regex)) ? first : last;
		if (wrongPos == first)
			while (first < last && probablyCorrectChar1.matches(regex))
				probablyCorrectChar1 = String.valueOf(examinedChars[++first]);
		String probablyCorrectChar2 = String.valueOf(examinedChars[last]);
		if (wrongPos == last)
			while (last > 0 && probablyCorrectChar2.matches(regex))
				probablyCorrectChar2 = String.valueOf(examinedChars[--last]);
		return alphabet.contains(probablyCorrectChar1) && alphabet.contains(probablyCorrectChar2);
	}

	private Map<String, PartOfSpeech> loadPartsOfSpeech() {
		Properties langProps = new Properties();
		String home = System.getProperties().getProperty("user.home");
		Map<String, PartOfSpeech> partsOS = new TreeMap<>();
		try (InputStream in = new FileInputStream(new File(home, String.format("%s.properties", langName)))) {
			langProps.loadFromXML(in);
			int nPoS = 0;
			String PoSName;
			while ((PoSName = langProps.getProperty(String.format("name%d", ++nPoS))) != null) {
				int len = Math.min(3, PoSName.length());
				partsOS.put(PoSName, new PartOfSpeech(this, PoSName, PoSName.substring(0, len)));
			}
			partsOS.put(PartOfSpeech.ANY, new PartOfSpeech(this, PartOfSpeech.ANY));
			partsOS.put(PartOfSpeech.UNDEFINED, new PartOfSpeech(this, PartOfSpeech.UNDEFINED));
			partsOS.put(PartOfSpeech.COMPOSITE, new PartOfSpeech(this, PartOfSpeech.COMPOSITE));
		} catch (IOException e) {
			System.err.format("Unable to read language configuration. %s.properties file is corrupt or missing at" +
					                  " %s. %n", langName, home);
		}
		return partsOS;
	}

	private boolean savePartsOfSpeech() {
		Properties langProps = new Properties();
		final int[] i = {1};
		partsOfSpeech.keySet().forEach( p -> langProps.put(String.format("name%d", i[0]++),p));
		String home = System.getProperties().getProperty("user.home");
//		Map<String, PartOfSpeech> partsOS = new TreeMap<>();
		try (OutputStream out = new FileOutputStream(new File(home, String.format("%s.properties", langName)))) {
			langProps.storeToXML(out, String.format("%s language parts of speech", langName));
		} catch (IOException e) {
			System.err.format("Unable to write language configuration to %s\\%s.properties%n",  home, langName );
		}
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Language language = (Language) o;
		if (!langName.equalsIgnoreCase(language.langName)) return false;
		return alphabet.equals(language.alphabet);
	}

	@Override
	public int hashCode() {
		int result = langName.hashCode();
		result = 31 * result + alphabet.hashCode();
		return result;
	}

	@Override
	public int compareTo(Object o) {
		return alphabet.compareTo(((Language) o).alphabet);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		String shortLngName = shortName.length() != 0 ? shortName : langName.substring(0,2);
		res.append(String.format("Language: %s(%s)%n", langName, shortLngName))
			.append(String.format("Available parts of speech: %s", partsOfSpeech.keySet()));
		return res.toString();
	}

	public PartOfSpeech getPartOfSpeech(String partOfSpeechName) {
		if (partOfSpeechName.isEmpty()) return new PartOfSpeech(this, PartOfSpeech.UNDEFINED);
		PartOfSpeech partOfSpeech =
				partsOfSpeech.getOrDefault(partOfSpeechName,
						new PartOfSpeech(this, partOfSpeechName));
		partsOfSpeech.putIfAbsent(partOfSpeechName, partOfSpeech);
		savePartsOfSpeech();
		return partOfSpeech;
	}
}
