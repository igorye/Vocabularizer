package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;

public class PartOfSpeech implements Serializable, Comparable {
	final public Language lang;
	final public String partName;
	final public String shortName;
	public PartOfSpeech correspondsTo;
	public boolean identiacalCorrespondance;

	final public static String UNDEFINED = "UNDEFINED";
	final public static String ANY = "ANY";
	final public static String COMPOSITE = "COMPOSITE";



	public PartOfSpeech(Language lang) {
		this(lang, UNDEFINED, "");
	}

	public PartOfSpeech(Language lang, String partName, String shortName) {
		this.lang = lang;
		this.partName = partName;
		this.shortName = shortName;
		this.correspondsTo = null;
	}

	public PartOfSpeech(Language language, String partName) {
		this(language, partName, partName);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PartOfSpeech that = (PartOfSpeech) o;
		if (!lang.equals(that.lang)) return false;
		return partName.equalsIgnoreCase(that.partName);
	}

	@Override
	public int hashCode() {
		return 31 * partName.hashCode();
	}

	public boolean assignTo(PartOfSpeech corrPoS) {
		if (!corrPoS.lang.equals(this.lang))
			this.correspondsTo = corrPoS;
		return correspondsTo != null;
	}

	public String toString() {
		return partName;
	}

	@Override
	public int compareTo(Object o) {
		return partName.compareTo(((PartOfSpeech) o).partName);
	}
}
