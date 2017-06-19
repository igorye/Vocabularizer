package com.nicedev.vocabularizer.dictionary.model;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.stream.Collectors.*;

public class Vocabula implements Serializable, Comparable {

	private static final long serialVersionUID = 7318719810956065533L;

	final public String headWord;
	public final boolean isComposite;
	private final Language language;
	private final Map<PartOfSpeech, Set<Definition>> mapPOS;
	private final Map<PartOfSpeech, Set<String>> knownForms;
	private String transcription;
	private Set<String> pronunciations;
	private Vocabula identicalTo;
	private Set<Definition> unresolvedAccordances = null;

	public Vocabula(String charSeq, Language lang, String transcription) {
		this.headWord = charSeq.trim();
		this.transcription = transcription.trim();
		this.identicalTo = null;
		this.language = lang;
		this.isComposite = charSeq.matches(".*\\s.*");
		mapPOS = new TreeMap<>();
		knownForms = new TreeMap<>();
	}

	public Vocabula(Vocabula partialVoc) {
		this.headWord = partialVoc.headWord;
		this.transcription = partialVoc.transcription;
		this.identicalTo = partialVoc.identicalTo;
		this.language = partialVoc.language;
		this.isComposite = headWord.contains("\\s");
		mapPOS = new TreeMap<>(partialVoc.mapPOS);
		knownForms = new TreeMap<>(partialVoc.knownForms);
	}

	public Vocabula(String charSeq, Language lang) {
		this(charSeq, lang, "");
	}
	
	/*public boolean addDefinition(String partOfSpeechName, String meaning) {
		PartOfSpeech pos = language.getPartOfSpeech(partOfSpeechName);
		Set<Definition> definitions = mapPOS.computeIfAbsent(pos, partOfSpeech -> new LinkedHashSet<>());
		return definitions.add(new Definition(language, this, pos, meaning));
	}*/
	
	/*public boolean addDefinition(String partOfSpeechName, Definition definition) {
		PartOfSpeech pos = language.getPartOfSpeech(partOfSpeechName);
		Set<Definition> definitions = mapPOS.getOrDefault(pos, new LinkedHashSet<>());
		mapPOS.putIfAbsent(pos, definitions);
		return definitions.add(new Definition(definition, this, pos));
	}*/
	
	/*public void addDefinition(String partOfSpeechName, Definition definition) {
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
		addDefinition(partOfSpeech, definition);
	}*/

	public void addDefinition(PartOfSpeech partOfSpeech, Definition definition) {
		language.getPartOfSpeech(partOfSpeech.partName);
		Set<Definition> definitions = mapPOS.computeIfAbsent(partOfSpeech, pos -> new LinkedHashSet<>());
		definitions.add(definition);
	}
	
	/*public void addPartsOfSpeech(Vocabula newVoc) {
		newVoc.mapPOS.keySet().forEach(pos -> addDefinitions(pos, newVoc.mapPOS.get(pos)));
	}*/
	
	
	/*public void addPronunciation(String source) {
		if (pronunciations == null) initPronunciations();
		pronunciations.add(source);
	}*/

	public void addPronunciations(Collection<String> pronunciationSources) {
		if (pronunciations == null) initPronunciations();
		pronunciations.addAll(pronunciationSources);
	}

	private void initPronunciations() {
		pronunciations = new LinkedHashSet<>();
	}

	public void removeDefinition(String partName, String explanation) {
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partName);
		mapPOS.get(partOfSpeech).removeIf(def -> def.explanation.equals(explanation));
	}
	
	/*public void addDefinitions(String partOfSpeechName, Set<Definition> definitions) {
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
		addDefinitions(partOfSpeech, definitions);
	}*/

	public void addDefinitions(PartOfSpeech partOfSpeech, Set<Definition> newDefinitions) {
		Set<Definition> definitions = mapPOS.computeIfAbsent(partOfSpeech, PoS -> new LinkedHashSet<>());
		newDefinitions.forEach(definition -> definitions.add(new Definition(definition, this, partOfSpeech)));
	}

	public void removeDefinitions(String partName, Set<String> explanations) {
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partName);
		mapPOS.get(partOfSpeech).removeIf(def -> explanations.contains(def.explanation));
	}
	
	/*public void addDefinitions(Vocabula vocabula) {
		if (!headWord.equals(vocabula.headWord)) return;
		vocabula.mapPOS.keySet().forEach(pos -> mapPOS.putIfAbsent(pos, vocabula.mapPOS.get(pos)));
	}*/

	public boolean removePartOfSpeech(String partOfSpeechName) {
		return mapPOS.remove(language.getPartOfSpeech(partOfSpeechName)) != null;
	}

	public Set<Definition> getDefinitions() {
		return getDefinitions(PartOfSpeech.ANY);
	}

	public Set<Definition> getDefinitions(PartOfSpeech partOfSpeech) {
		if (partOfSpeech == null) return Collections.emptySet();
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
		Vocabula other = (Vocabula) o;
		return headWord.equals(other.headWord)
				       && mapPOS.keySet().size() == other.mapPOS.keySet().size()
				       && mapPOS.keySet().containsAll(other.mapPOS.keySet())
				       && mapPOS.keySet().stream().allMatch(pOS -> mapPOS.get(pOS).containsAll(other.mapPOS.get(pOS)));
	}

	@Override
	public int hashCode() {
		int result = headWord.hashCode();
		result = 31 * result + mapPOS.hashCode();
		result = 31 * result + knownForms.hashCode();
		result = 31 * result + transcription.hashCode();
		return result;
	}
	
	/*public boolean hasAccordance() {
		return identicalTo != null;
	}*/

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

	public int getDefinitionsCount() {
		return mapPOS.values().stream().mapToInt(Set::size).sum();
	}

	// returns count of defenitions not starting with prefixes 'form of ' and 'see '
	public int getDecentDefinitionsCount() {
		return (int) mapPOS.values().stream().flatMap(defs -> defs.stream().filter(Definition::isDecent)).count();
	}
	
	/*public int getFormCount() {
		return mapPOS.keySet().size();
	}*/

	@Override
	public int compareTo(Object o) {
		return headWord.compareTo(((Vocabula) o).headWord);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append(format("%s [%s]%n", headWord, getTranscription()));
		mapPOS.keySet().forEach(partOfSpeech -> {
			String partOfSpeechName = partOfSpeech.partName;
			if (partOfSpeechName.equals(PartOfSpeech.UNDEFINED)) partOfSpeechName = "";
			res.append(format("  : %s%n", partOfSpeechName));
			Collection<String> forms = getKnownForms(partOfSpeech);
			if (!forms.isEmpty())
				res.append(format("   form%s: ", forms.size() > 1 ? "s" : ""))
						.append(forms.stream().collect(joining(", "))).append("\n");
			getDefinitions(partOfSpeech).forEach(res::append);
		});
		return res.toString();
	}

	public String toHTML() {
		StringBuilder res = new StringBuilder();
		String headWordFmt = "<table><tr><td class=\"headword\">%s</td><td class=\"transcription\">[%s]</td></tr></table>";
		res.append(format(headWordFmt, headWord, getTranscription()));
		mapPOS.keySet().forEach(partOfSpeech -> {
			String partOfSpeechName = partOfSpeech.partName;
			if (partOfSpeechName.equals(PartOfSpeech.UNDEFINED)) partOfSpeechName = "";
			res.append(format("<div class=\"partofspeech\"><span style=\"none\">:</span> %s", partOfSpeechName));
			Collection<String> forms = getKnownForms(partOfSpeech);
			if (!forms.isEmpty())
				res.append(format("<div>form%s: ", forms.size() > 1 ? "s" : ""))
						.append("<b>").append(forms.stream().collect(joining("</b>, <b>"))).append("</b></div>\n");
			getDefinitions(partOfSpeech).forEach(definition -> res.append(definition.toHTML()));
			res.append("</div>");
		});
		return res.toString();
	}
	
    /*public String toXML() {
        StringBuilder res = new StringBuilder();
        res.append(String.format("<article>"));
        String headWordFmt = "%n<headword>%s</headword>%n<transcription>%s</transcription>%n";
        res.append(String.format(headWordFmt, headWord, getTranscription()));
        res.append(String.format("<partsofspeech>%n"));
        mapPOS.keySet().forEach(partOfSpeech -> {
            res.append(String.format("<partofspeech>%s</partofspeech>%n", partOfSpeech));
            Set<String> forms = getKnownForms(partOfSpeech);
            if (!forms.isEmpty()){
                res.append(String.format("<forms>%n"));
                forms.forEach(f -> res.append(String.format("<form>%s</form>%n", f)));
                res.append(String.format("</forms>%n"));
            }
            res.append(String.format("<definitions>%n"));
            getDefinitions(partOfSpeech).forEach(definition -> res.append(definition.toXML()));
            res.append(String.format("</definitions>%n"));
            res.append("</article>");
        });
        return res.toString();
    }*/

	public Collection<String> getKnownForms(PartOfSpeech partOfSpeech) {
		Collection<String> forms = partOfSpeech.partName.equals(PartOfSpeech.ANY)
				                           ? knownForms.values().stream()
						                             .flatMap(Collection::stream)
						                             .distinct()
						                             .sorted().collect(Collectors.toList())
				                           : knownForms.getOrDefault(partOfSpeech, Collections.emptySet());
		return Collections.unmodifiableCollection(forms);
	}
	
	/*public Set<String> getKnownForms(String partOfSpeechName) {
		return getKnownForms(new PartOfSpeech(language, partOfSpeechName));
	}*/

	public Collection<String> getKnownForms() {
		return getKnownForms(language.getPartOfSpeech(PartOfSpeech.ANY));
	}
	
	/*public void addSynonym(String definition, String synonym) {
	
	}*/
	
	/*public void addSynonyms(String definition, Collection<String> synonyms) {
	
	}*/
	
	/*public void addKnownForms(String partOfSpeechName, Collection<String> newForms) {
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
		addKnownForms(partOfSpeech, newForms);
	}*/

	public void addKnownForms(PartOfSpeech partOfSpeech, Collection<String> newForms) {
		Set<String> forms = knownForms.computeIfAbsent(partOfSpeech, PoS -> new LinkedHashSet<>());
		forms.addAll(newForms);
	}

	public Collection<String> getPronunciationSources() {
		return pronunciations != null ? Collections.unmodifiableCollection(pronunciations) : Collections.emptyList();
	}

	public boolean hasDecentDefinition() {
		return mapPOS.values().stream().anyMatch(defs -> defs.stream().anyMatch(Definition::isDecent));
	}

	public Set<PartOfSpeech> getPartsOfSpeech() {
		return Collections.unmodifiableSet(mapPOS.keySet());
	}

	public Vocabula merge(Vocabula other) {
		other.getPartsOfSpeech().stream()
				.filter(PoS -> !getPartsOfSpeech().contains(PoS))
				.forEach(PoS -> {
					addKnownForms(PoS, other.getKnownForms(PoS));
					addDefinitions(PoS, other.getDefinitions(PoS));
				});
		removeInessentialDefinitions();
		if (getTranscription().isEmpty()) setTranscription(other.getTranscription());
		return this;
	}

	public void removeInessentialDefinitions() {
		Map<PartOfSpeech, Set<String>> inessentialDefinitions = getDefinitions().stream()
				                                                        .filter(Definition::isInessential)
				                                                        .collect(groupingBy(Definition::getDefinedPartOfSpeech,
				                                                                            mapping(d -> d.explanation,
				                                                                                    toSet())));
		inessentialDefinitions.keySet().stream()
				.filter(pos -> getDefinitions(pos).size() > inessentialDefinitions.get(pos).size())
				.forEach(pos -> removeDefinitions(pos.partName, inessentialDefinitions.get(pos)));
		mapPOS.keySet().removeIf(partOfSpeech -> getDefinitions(partOfSpeech).isEmpty());
	}

}
