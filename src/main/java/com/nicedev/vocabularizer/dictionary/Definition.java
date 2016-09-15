package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.util.*;

public class Definition implements Serializable, Comparable{
	public final Language language;
	public final String explanatory;
	protected Map.Entry<Vocabula, PartOfSpeech> defines;
	private String indenticalTo;
	private Set<String> useCases;
	private Set<String> synonyms;

	private boolean hasAccordance;
//	private Set<Definition> synonyms;

	public Definition(Language lang, Map.Entry<Vocabula, PartOfSpeech> defines, String explanatory) {
		this.explanatory = explanatory;
		this.language = lang;
		synonyms = new TreeSet<>();
		useCases = new LinkedHashSet<>();
		this.defines = defines;
	}

	public Definition(Language lang, Map.Entry<Vocabula, PartOfSpeech> defines) {
		this(lang, defines, defines.getKey().charSeq);
	}

	public void addUseCase(String useCase) {
		useCases.add(useCase);
	}

	public void removeUseCase(String useCase) {
		useCases.remove(useCase);
	}

	public void addSynonym(String definition) {
		synonyms.add(definition);
	}

	public void removeSynonym(String definition) {
//		synonyms.add(definition);
	}

//	public Set<Definition> getSynonyms() {
//		return Collections.unmodifiableSet(synonyms);
//	}

	public Set<String> getSynonyms() {
		return Collections.unmodifiableSet(synonyms);
	}

	public Set<String> getUseCases() {
		return Collections.unmodifiableSet(useCases);
	}

	public void assignTo(String entry) {
		if( Language.charsMatchLanguage(entry, language)) {
			indenticalTo = entry;
			hasAccordance = true;
		}
	}

	@Override
	public int compareTo(Object o) {
		return explanatory.compareTo(((Definition) o).explanatory);
	}

	public boolean hasAccordance() {
		return hasAccordance;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Definition that = (Definition) o;

		return explanatory.equalsIgnoreCase(that.explanatory);
	}

	@Override
	public int hashCode() {
		return explanatory.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		if (explanatory.length() != 0)
			res.append(String.format("    - %s",explanatory));
		else if (indenticalTo != null)
			res.append(String.format("    - see <it>%s</it>", indenticalTo));
		if (synonyms.size() != 0){
			res.append(String.format("\n      synonym%s: ", synonyms.size() > 1 ? "s" : ""));
			synonyms.forEach(uc -> res.append(uc).append(", "));
			if (res.lastIndexOf(", ") == res.length()-2)
				res.replace(res.lastIndexOf(", "), res.length(), "");
		}

		if (useCases.size() != 0){
			res.append("\n      : ");
			useCases.forEach(uc -> res.append(String.format("\"%s\"",uc)).append(", "));
			if (res.lastIndexOf(", ") == res.length()-2)
				res.replace(res.lastIndexOf(", "), res.length(), "");
		}
		res.append("\n");
		return res.toString();
	}

	public void addSynonyms(Set<String> synonyms) {
		this.synonyms.addAll(synonyms);
	}

	public void addUseCases(Set<String> useCases) {
		this.useCases.addAll(useCases);
	}

}
