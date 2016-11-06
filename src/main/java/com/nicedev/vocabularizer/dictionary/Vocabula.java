package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.util.*;

public class Vocabula implements Serializable, Comparable {

	private final Language language;
	final public String headWord;
	private String transcription;
	private Set<String> pronunciations;
	private Vocabula identicalTo;
	public final boolean isComposite;
	private Set<Definition> unresolvedAccordances = null;
	Map<PartOfSpeech, Set<Definition>> mapPOS;
	private Map<PartOfSpeech, Set<String>> knownForms;

	public Vocabula(String charSeq, Language lang, String transcription) {
		this.headWord = charSeq;
		this.transcription = transcription;
		this.identicalTo = null;
		this.language = lang;
		this.isComposite = charSeq.contains("\\s");
		mapPOS = new TreeMap<>();
		knownForms = new TreeMap<>();
	}

	public Vocabula(Vocabula partialVoc) {
		this.headWord = partialVoc.headWord;
		this.transcription = partialVoc.transcription.intern();
		this.identicalTo = partialVoc.identicalTo;
		this.language = partialVoc.language;
		this.isComposite = headWord.contains("\\s");
		mapPOS = new TreeMap<>(partialVoc.mapPOS);
		knownForms = new TreeMap<>(partialVoc.knownForms);
	}

	public Vocabula(String charSeq, Language lang) {
		this(charSeq, lang, "");
	}

	public boolean addPartOfSpeech(String partOfSpeechName, String meaning){
		PartOfSpeech pos = language.getPartOfSpeech(partOfSpeechName);
		Set<Definition> definitions = mapPOS.getOrDefault(pos, new LinkedHashSet<>());
		mapPOS.putIfAbsent(pos, definitions);
		return definitions.add(new Definition(language, this, pos, meaning));
	}

	public void addPronunciation(String source) {
		if(pronunciations == null) initPronunciations();
		pronunciations.add(source);
	}

	public void addPronunciations(Collection<String> pronunciationSources) {
		if(pronunciations == null) initPronunciations();
		pronunciations.addAll(pronunciationSources);
	}

	private void initPronunciations() {
		pronunciations = new LinkedHashSet<>();
	}

	public void addForms(Vocabula newVoc) {
		for (PartOfSpeech partOfSpeech: newVoc.mapPOS.keySet())
			this.addDefinitions(partOfSpeech, newVoc.mapPOS.get(partOfSpeech));
	}


	public void addDefinition(PartOfSpeech partOfSpeech, Definition definition) {
		language.getPartOfSpeech(partOfSpeech.partName);
		Set<Definition> defs = mapPOS.getOrDefault(partOfSpeech, new LinkedHashSet<>());
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
		Set<Definition> defs = mapPOS.getOrDefault(partOfSpeech, new LinkedHashSet<>());
		defs.addAll(definitions);
		mapPOS.putIfAbsent(partOfSpeech, defs);
	}

	public void removePartOfSpeech(String partOfSpeechName) {
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
		return headWord.equalsIgnoreCase(vocabula.headWord);
	}

	@Override
	public int hashCode() {
		return 31 * headWord.hashCode() + language.hashCode();
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
		return headWord.compareTo(((Vocabula) o).headWord);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append(String.format("%s [%s]%n", headWord, getTranscription()));
		mapPOS.keySet().forEach(partOfSpeech -> {
			res.append(String.format("  : %s%n", partOfSpeech));
			Set<String> forms = getPartsOfSpeech(partOfSpeech);
			if (!forms.isEmpty()){
				res.append(String.format("   form%s: ", forms.size() > 1 ? "s" : ""));
				forms.forEach(f -> res.append(String.format("%s, ", f)));
				if (res.lastIndexOf(", ") == res.length()-2)
					res.replace(res.lastIndexOf(", "), res.length(), "\n");
			}
			getDefinitions(partOfSpeech).forEach(res::append);
		});
		return res.toString();
	}

	public String toHTML() {
		StringBuilder res = new StringBuilder();
		String headWordFmt = "<table><tr><td class='headword'>%s</td><td class='transcription'>[%s]</td></tr></table>";
		res.append(String.format(headWordFmt, headWord, getTranscription()));
		mapPOS.keySet().forEach(partOfSpeech -> {
			res.append(String.format("<div class='partofspeech'><span style='none'>:</span> %s", partOfSpeech));
			Set<String> forms = getPartsOfSpeech(partOfSpeech);
			if (!forms.isEmpty()){
				res.append(String.format("<div>form%s: ", forms.size() > 1 ? "s" : ""));
				forms.forEach(f -> res.append(String.format("%s, ", f)));
				if (res.lastIndexOf(", ") == res.length()-2)
					res.replace(res.lastIndexOf(", "), res.length(), "</div>\n");
			}
			getDefinitions(partOfSpeech).forEach(definition -> res.append(definition.toHTML()));
			res.append("</div>");
		});
		return res.toString();
	}

	private Set<String> getPartsOfSpeech(PartOfSpeech partOfSpeech) {
		return knownForms.getOrDefault(partOfSpeech, Collections.<String>emptySet());
	}

	public void addSynonym(String definition, String synonym) {

	}

	public void addSynonyms(String definition, Collection<String> synonyms) {

	}

	public void addUnresolvedAccordances(Definition definition) {
		if (unresolvedAccordances == null)
			unresolvedAccordances = new LinkedHashSet<>();
		unresolvedAccordances.add(definition);
	}

	public boolean needToResolveAccordances() {
		return unresolvedAccordances.isEmpty();
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

	public Collection<String> getPronunciationSources() {
		return Collections.unmodifiableCollection(pronunciations);
	}
}
