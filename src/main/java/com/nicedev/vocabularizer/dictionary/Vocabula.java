package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collector.Characteristics.*;
import static java.util.stream.Collectors.joining;

public class Vocabula implements Serializable, Comparable {
	
	private static final long serialVersionUID = 7318719810956065533L;
	final public String headWord;
	public final boolean isComposite;
	private final Language language;
	private Map<PartOfSpeech, Set<Definition>> mapPOS;
	private Map<PartOfSpeech, Set<String>> knownForms;
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
	
	private Collector<Map.Entry<PartOfSpeech, Definition>, Map<PartOfSpeech, Set<Definition>>, Map<PartOfSpeech, Set<Definition>>> getDefinesToMapPoSCollector() {
		return new Collector<Map.Entry<PartOfSpeech, Definition>, Map<PartOfSpeech, Set<Definition>>, Map<PartOfSpeech, Set<Definition>>>() {
			@Override
			public Supplier<Map<PartOfSpeech, Set<Definition>>> supplier() {
				return TreeMap::new;
			}
			
			@Override
			public BiConsumer<Map<PartOfSpeech, Set<Definition>>, Map.Entry<PartOfSpeech, Definition>> accumulator() {
				return (partOfSpeechSetMap, partOfSpeechDefinitionEntry) -> {
					Set<Definition> defs = partOfSpeechSetMap.getOrDefault(partOfSpeechDefinitionEntry.getKey(), new LinkedHashSet<>());
					defs.add(partOfSpeechDefinitionEntry.getValue());
					partOfSpeechSetMap.putIfAbsent(partOfSpeechDefinitionEntry.getKey(), defs);
				};
			}
			
			@Override
			public BinaryOperator<Map<PartOfSpeech, Set<Definition>>> combiner() {
				return (partOfSpeechSetMap, partOfSpeechSetMap2) -> {
					partOfSpeechSetMap2.keySet().forEach(partOfSpeech -> {
						Set<Definition> defsTo = partOfSpeechSetMap.getOrDefault(partOfSpeech, new LinkedHashSet<>());
						Set<Definition> defsFrom = partOfSpeechSetMap2.getOrDefault(partOfSpeech, new LinkedHashSet<>());
						if (defsTo.isEmpty() && !defsFrom.isEmpty()) {
							defsTo.addAll(defsFrom);
							partOfSpeechSetMap.putIfAbsent(partOfSpeech, defsTo);
						}
					});
					return partOfSpeechSetMap;
				};
			}
			
			@Override
			public Function<Map<PartOfSpeech, Set<Definition>>, Map<PartOfSpeech, Set<Definition>>> finisher() {
				return Function.identity();
			}
			
			@Override
			public Set<Characteristics> characteristics() {
				return Collections.unmodifiableSet(EnumSet.of(IDENTITY_FINISH, UNORDERED, CONCURRENT));
			}
		};
	}
	
	public boolean addPartOfSpeech(String partOfSpeechName, String meaning) {
		PartOfSpeech pos = language.getPartOfSpeech(partOfSpeechName);
		Set<Definition> definitions = mapPOS.getOrDefault(pos, new LinkedHashSet<>());
		mapPOS.putIfAbsent(pos, definitions);
		return definitions.add(new Definition(language, this, pos, meaning));
	}
	
	public boolean addPartOfSpeech(String partOfSpeechName, Definition definition) {
		PartOfSpeech pos = language.getPartOfSpeech(partOfSpeechName);
		Set<Definition> definitions = mapPOS.getOrDefault(pos, new LinkedHashSet<>());
		mapPOS.putIfAbsent(pos, definitions);
		return definitions.add(new Definition(definition, this, pos));
	}
	
	public void addPartsOfSpeech(Vocabula newVoc) {
		newVoc.mapPOS.keySet().forEach(pos -> addDefinitions(pos, newVoc.mapPOS.get(pos)));
	}
	
	public void addPartsOfSpeech(Set<Definition> definitions, Predicate<Vocabula> predicate) {
		Map<PartOfSpeech, Set<Definition>> newMapPosDefinitions =
				definitions.stream()
						.filter(def -> def.defines.getKey().headWord.equals(headWord) && predicate.test(this))
						.map(def -> new AbstractMap.SimpleEntry<>(def.defines.getValue(), def))
						.collect(getDefinesToMapPoSCollector());
		mapPOS.putAll(newMapPosDefinitions);
	}
	
	public void addPronunciation(String source) {
		if (pronunciations == null) initPronunciations();
		pronunciations.add(source);
	}
	
	public void addPronunciations(Collection<String> pronunciationSources) {
		if (pronunciations == null) initPronunciations();
		pronunciations.addAll(pronunciationSources);
	}
	
	
	private void initPronunciations() {
		pronunciations = new LinkedHashSet<>();
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
	
	public void addDefinitions(String partOfSpeechName, Set<Definition> definitions) {
		PartOfSpeech partOfSpeech = language.getPartOfSpeech(partOfSpeechName);
		addDefinitions(partOfSpeech, definitions);
	}
	
	public void addDefinitions(PartOfSpeech partOfSpeech, Set<Definition> definitions) {
		Set<Definition> defs = mapPOS.getOrDefault(partOfSpeech, new LinkedHashSet<>());
		defs.addAll(definitions);
		mapPOS.putIfAbsent(partOfSpeech, defs);
	}
	
	public void addPartsOfSpeechIf(Set<Definition> definitions) {
		
	}
	
	public void addDefinitions(Vocabula vocabula) {
		if (!headWord.equals(vocabula.headWord)) return;
		vocabula.mapPOS.keySet().forEach(pos -> mapPOS.putIfAbsent(pos, vocabula.mapPOS.get(pos)));
	}
	
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
			Set<String> forms = getKnownForms(partOfSpeech);
			if (!forms.isEmpty())
				res.append(String.format("   form%s: ", forms.size() > 1 ? "s" : ""))
						.append(forms.stream().collect(joining(", "))).append("\n");
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
			Set<String> forms = getKnownForms(partOfSpeech);
			if (!forms.isEmpty())
				res.append(String.format("<div>form%s: ", forms.size() > 1 ? "s" : ""))
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
	
	public Set<String> getKnownForms(PartOfSpeech partOfSpeech) {
		return partOfSpeech.partName.equals(PartOfSpeech.ANY)
				       ? knownForms.values().stream().flatMap(Collection::stream).collect(Collectors.toSet())
				       : knownForms.getOrDefault(partOfSpeech, Collections.emptySet());
	}
	
	public Set<String> getKnownForms(String partOfSpeechName) {
		return getKnownForms(new PartOfSpeech(language, partOfSpeechName));
	}
	
	public Set<String> getKnownForms() {
		return getKnownForms(new PartOfSpeech(language, PartOfSpeech.ANY));
	}
	
	
//	public Map<PartOfSpeech, Set<Definition>> getPartOfSpeech(PartOfSpeech partOfSpeech) {
//		return ((SortedMap) mapPOS).subMap(partOfSpeech, partOfSpeech);
//	}
	
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
	
	public void resolveAccordances() {
	}
	
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
		return pronunciations != null ? Collections.unmodifiableCollection(pronunciations) : Collections.emptyList();
	}
	
	public boolean hasDecentDefinition(PartOfSpeech partOfSpeech) {
		return Optional.ofNullable(mapPOS.get(partOfSpeech))
				       .filter(defs -> defs.size() == 1)
				       .map(defs -> defs.stream().noneMatch(def -> def.explanation.startsWith("see ")))
				       .orElse(false);
//				       .flatMap(defs -> defs.stream()
//						                        .findFirst()
//						                        .map(def -> !def.explanation.startsWith("see ")))
//				       .orElse(true);
	
	}
	
	
	public Set<PartOfSpeech> getPartsOfSpeech() {
		return Collections.unmodifiableSet(mapPOS.keySet());
	}
}
