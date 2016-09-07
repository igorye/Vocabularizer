package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.util.*;

public class Vocabula implements Serializable, Comparable {

	private final Language language;
	final public String charSeq;
	private String transcription;
	private Vocabula identicalTo;
	public final boolean isComposite;
	Map<PartOfSpeech, Set<Definition>> forms;

	public Vocabula(String charSeq, Language lang, String transcription) {
		this.charSeq = charSeq;
		this.transcription = transcription;
		this.identicalTo = null;
		this.language = lang;
		this.isComposite = charSeq.contains("\\s");
		forms = new HashMap<>();
	}

	public Vocabula(String charSeq, Language lang) {
		this(charSeq, lang, "");
	}

	public boolean addForm(String partOfSpeechName, String meaning){
		PartOfSpeech pos = language.partsOfSpeech.get(partOfSpeechName);
		Set<Definition> definitions = forms.getOrDefault(pos, new TreeSet<>());
		Map.Entry<Vocabula, PartOfSpeech> mEntry = new AbstractMap.SimpleEntry<>(this, pos);
		forms.putIfAbsent(pos, definitions);
		return definitions.add(new Definition(language, mEntry, meaning));
	}

	public void removeForm(String partOfSpeechName) {
		forms.remove(language.partsOfSpeech.get(partOfSpeechName));
	}

	public Set<Definition> getDefinitions(PartOfSpeech partOfSpeech) {
		if (partOfSpeech == null) return Collections.<Definition>emptySet();
		Set<Definition> defs;
		switch (partOfSpeech.partName) {
			case PartOfSpeech.ANY:
				defs = forms.keySet().stream()
						       .map(this::getDefinitions)
						       .collect(TreeSet::new, Set::addAll, Set::addAll);
			break;
			default:
				defs = forms.getOrDefault(partOfSpeech, Collections.emptySet());
		}
		return Collections.<Definition>unmodifiableSet(defs);
	}

	public Set<Definition> getDefinitions(String partOfSpeechName) {
		return getDefinitions(language.partsOfSpeech.get(partOfSpeechName));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Vocabula vocabula = (Vocabula) o;
		return charSeq.equals(vocabula.charSeq);
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

	public int getMeaningCount() {
		return forms.values().stream().mapToInt(Set::size).sum();
	}

	public int getFormCount() {
		return forms.keySet().size();
	}

	@Override
	public int compareTo(Object o) {
		return charSeq.compareTo(((Vocabula) o).charSeq);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append(String.format("%s%n", charSeq));
		getDefinitions(PartOfSpeech.ANY).forEach(res::append);
		return res.toString();
	}
//
//	public String toString() {
//		StringBuilder res = new StringBuilder();
//		res.append(String.format("%s%n", charSeq));
//		for (PartOfSpeech partOfSpeech: forms.keySet()){
//			res.append(String.format("\t: %s%n", partOfSpeech.partName));
//			for(Definition definition : getDefinitions(partOfSpeech))
//				res.append(String.format("\t\t- %s%n", definition));
//		}
//		return res.toString();
//	}
}
