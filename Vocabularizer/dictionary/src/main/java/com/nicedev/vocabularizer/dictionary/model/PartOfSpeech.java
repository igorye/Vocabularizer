package com.nicedev.vocabularizer.dictionary.model;

import java.io.Serializable;

public class PartOfSpeech implements Serializable, Comparable {

	final public static String UNDEFINED = "UNDEFINED";
	final public static String ANY = "ANY";
	final public static String COMPOSITE = "COMPOSITE";
	private static final long serialVersionUID = 2010332645168347315L;
	final public Language lang;
	final public String partName;
	final public String shortName;
	public PartOfSpeech correspondsTo;
	public boolean identiacalCorrespondance;

	public PartOfSpeech(Language language) {
		this(language, UNDEFINED, "");
	}

	public PartOfSpeech(Language language, String partName, String shortName) {
		this.lang = language;
		this.partName = partName.trim();
		this.shortName = shortName.trim();
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
		return lang.equals(that.lang) && partName.equalsIgnoreCase(that.partName);
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

	@Override
	public String toString() {
		return partName;
	}

	@Override
	public int compareTo(Object o) {
		return partName.compareTo(((PartOfSpeech) o).partName);
	}
}
