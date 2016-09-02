package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;

/**
 * Created by sugarik on 28.08.2016.
 */

public class PartOfSpeech implements Serializable, Comparable {
	final public Language lang;
	final public String name;
	public PartOfSpeech correspondsTo;

	final static String UNDEFINED = "UNDEFINED";
	final static String ANY = "ANY";

	public PartOfSpeech(String lang) {
		this(lang, UNDEFINED);
	}

	public PartOfSpeech(String langName, String partName) {
		this.lang = new Language(langName, "", Language.ENGLISH_ALPHABET);
		this.name = partName;
		this.correspondsTo = null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PartOfSpeech that = (PartOfSpeech) o;
		if (lang.compareTo(that.lang) != 0) return false;
		return name.compareTo(that.name) == 0;

	}

	@Override
	public int hashCode() {
		return 31 * lang.hashCode() + name.hashCode();
	}

	public boolean assignTo(PartOfSpeech corrPoS) {
		if (!corrPoS.lang.equals(this.lang))
			this.correspondsTo = corrPoS;
		return correspondsTo != null;
	}

	public String toString() {
		return name;
	}

	@Override
	public int compareTo(Object o) {
		return name.compareTo(((PartOfSpeech) o).name);
	}
}
