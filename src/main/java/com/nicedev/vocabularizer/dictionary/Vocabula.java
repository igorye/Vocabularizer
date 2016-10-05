package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.util.*;

public class Vocabula implements Serializable, Comparable {

	private final Language language;
	final public String charSeq;
	private String transcription;
	private Set<String> spellings;
	private Vocabula identicalTo;
	public final boolean isComposite;
	private Set<Definition> unresolvedAccordances = null;
	Map<PartOfSpeech, Set<Definition>> mapPOS;
	private Map<PartOfSpeech, Set<String>> knownForms;

	public Vocabula(String charSeq, Language lang, String transcription) {
		this.charSeq = charSeq;
		this.transcription = transcription;
		this.identicalTo = null;
		this.language = lang;
		this.isComposite = charSeq.contains("\\s");
		mapPOS = new TreeMap<>();
		knownForms = new TreeMap<>();
	}

	public Vocabula(Vocabula partialVoc) {
		this.charSeq = partialVoc.charSeq;
		this.transcription = partialVoc.transcription.intern();
		this.identicalTo = partialVoc.identicalTo;
		this.language = partialVoc.language;
		this.isComposite = charSeq.contains("\\s");
		mapPOS = new TreeMap<>(partialVoc.mapPOS);
		knownForms = new TreeMap<>(partialVoc.knownForms);
	}

	public Vocabula(String charSeq, Language lang) {
		this(charSeq, lang, "");
	}

	public boolean addForm(String partOfSpeechName, String meaning){
		PartOfSpeech pos = language.getPartOfSpeech(partOfSpeechName);
		Set<Definition> definitions = mapPOS.getOrDefault(pos, new LinkedHashSet<>());
		Map.Entry<Vocabula, PartOfSpeech> mEntry = new AbstractMap.SimpleEntry<>(this, pos);
		mapPOS.putIfAbsent(pos, definitions);
		return definitions.add(new Definition(language, mEntry, meaning));
	}

	public void addSpelling(String source) {
		if(spellings == null) spellings = new LinkedHashSet<>();
		spellings.add(source);
	}

	public void addForms(Vocabula newVoc) {
		for (PartOfSpeech partOfSpeech: newVoc.mapPOS.keySet())
			this.addDefinitions(partOfSpeech, newVoc.mapPOS.get(partOfSpeech));
	}


	public void addDefinition(PartOfSpeech partOfSpeech, Definition definition) {
		Set<Definition> defs = mapPOS.getOrDefault(partOfSpeech, new TreeSet<>());
		defs.add(definition);
		mapPOS.putIfAbsent(partOfSpeech, defs);
	}
	public void addDefinition(String partOfSpeechName, Definition definition) {
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
		addDefinition(partOfSpeech, definition);
	}

	public void addDefinitions(String partOfSpeechName, Set<Definition> definitions){
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
		addDefinitions(partOfSpeech, definitions);
	}

	public void addDefinitions(PartOfSpeech partOfSpeech, Set<Definition> definitions) {
		Set<Definition> defs = mapPOS.getOrDefault(partOfSpeech, new TreeSet<>());
		defs.addAll(definitions);
		mapPOS.putIfAbsent(partOfSpeech, defs);
	}

	public void removeForm(String partOfSpeechName) {
		mapPOS.remove(language.getPartOfSpeech(partOfSpeechName));
	}

	public Set<Definition> getDefinitions(PartOfSpeech partOfSpeech) {
		if (partOfSpeech == null) return Collections.<Definition>emptySet();
		Set<Definition> defs;
		switch (partOfSpeech.partName) {
			case PartOfSpeech.ANY:
				defs = mapPOS.keySet().stream()
						       .map(this::getDefinitions)
						       .collect(LinkedHashSet::new, Set::addAll, Set::addAll);
			break;
			default:
				defs = mapPOS.getOrDefault(partOfSpeech, Collections.emptySet());
		}
		return Collections.unmodifiableSet(defs);
	}

	public Set<Definition> getDefinitions(String partOfSpeechName) {
		return getDefinitions(language.getPartOfSpeech(partOfSpeechName));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Vocabula vocabula = (Vocabula) o;
		return charSeq.equalsIgnoreCase(vocabula.charSeq);
	}

	@Override
	public int hashCode() {
		return 31 * charSeq.hashCode() + language.hashCode();
	}

	public boolean hasAccordance() {
		return identicalTo != null;
	}

	public String getTranscription() {
		return transcription;
	}

	public void setTranscription(String transcription) {
		this.transcription = transcription;
	}

	public void assignTo(Vocabula vocabula) {
		if (vocabula.language.equals(this.language))
			identicalTo = vocabula;
	}

	public int getMeaningCount() {
		return mapPOS.values().stream().mapToInt(Set::size).sum();
	}

	public int getFormCount() {
		return mapPOS.keySet().size();
	}

	@Override
	public int compareTo(Object o) {
		return charSeq.compareTo(((Vocabula) o).charSeq);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append(String.format("%s [%s]%n", charSeq, getTranscription()));
		mapPOS.keySet().forEach(partOfSpeech -> {
			res.append(String.format("  : %s%n", partOfSpeech));
			Set<String> forms = getForms(partOfSpeech);
			if (forms.size() != 0){
				res.append(String.format("      form%s: ", forms.size() > 1 ? "s" : ""));
				forms.forEach(f -> res.append(String.format("%s, ", f)));
				if (res.lastIndexOf(", ") == res.length()-2)
					res.replace(res.lastIndexOf(", "), res.length(), "\n");
			}
			getDefinitions(partOfSpeech).forEach(res::append);
		});
		return res.toString();
	}

	private Set<String> getForms(PartOfSpeech partOfSpeech) {
		return knownForms.getOrDefault(partOfSpeech, Collections.<String>emptySet());
	}

	public void addSynonym(String definition, String synonym) {	}

	public void addUnresolvedAccordances(Definition definition) {
		if (unresolvedAccordances == null)
			unresolvedAccordances = new LinkedHashSet<>();
		unresolvedAccordances.add(definition);
	}

	public boolean needToResolveAccordances() {
		return unresolvedAccordances.size() == 0;
	}

	public void resolveAccordances() { }

	public void addKnownForms(String partOfSpeechName, Collection<String> newForms) {
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
		addKnownForms(partOfSpeech, newForms);
	}

	public void addKnownForms(PartOfSpeech partOfSpeech, Collection<String> newForms) {
		Set<String> forms = knownForms.getOrDefault(partOfSpeech, new LinkedHashSet<>());
		forms.addAll(newForms);
		knownForms.putIfAbsent(partOfSpeech, forms);
	}

}
