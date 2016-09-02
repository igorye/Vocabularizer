package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.io.StreamCorruptedException;

/**
 * Created by sugarik on 28.08.2016.
 */
public class Vocabula implements Serializable, Comparable {

	private final Language language;
	final public String charSeq;
	private String transcription;
	private Vocabula identicalTo;
	public final boolean isComposite;

	public Vocabula(String charSeq, String langName, String transcription) {
		this.charSeq = charSeq;
		this.transcription = transcription;
		this.identicalTo = null;
		this.language = new Language(langName, "");
		this.isComposite = charSeq.contains("\\s");
	}

	public Vocabula(String charSeq, String language) {
		this(charSeq, language, "");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Vocabula vocabula = (Vocabula) o;

		if (!charSeq.equals(vocabula.charSeq)) return false;
		return language.equals(vocabula.language);

	}

	@Override
	public int hashCode() {
		return 31 * charSeq.hashCode() + language.hashCode();
	}

	public boolean hasCorrespondance() {
		return identicalTo != null;
	}

	public String getTranscription() {
		return transcription;
	}

	void setTranscription(String transcription) {
		this.transcription = transcription;
	}

	void assignTo(Vocabula vocabula) {
		if (vocabula.language.equals(this.language))
			identicalTo = vocabula;
	}


	@Override
	public int compareTo(Object o) {
		return charSeq.compareTo(((Vocabula) o).charSeq);
	}

	@Override
	public String toString() {
		return charSeq;
	}
}
