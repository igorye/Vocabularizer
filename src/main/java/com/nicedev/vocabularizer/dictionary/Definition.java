package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.util.*;

public class Definition implements Serializable, Comparable{
	public final Language language;
	public final String explanatory;
	protected Map.Entry<Vocabula, PartOfSpeech> defines;
	private Definition indenticalTo;
	private Set<String> useCases;
	private Set<Definition> synonyms;

	public Definition(Language lang, Map.Entry<Vocabula, PartOfSpeech> defines, String explanatory) {
		this.explanatory = explanatory;
		this.language = lang;
		synonyms = new TreeSet<>();
		useCases = new TreeSet<>();
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

	public void addSynonym(Definition definition) {
		synonyms.add(definition);
	}

	public void removeSynonym(Definition definition) {
		synonyms.add(definition);
	}

	public Set<Definition> getSynonyms() {
		return Collections.unmodifiableSet(synonyms);
	}

	public Set<String> getUseCases() {
		return Collections.unmodifiableSet(useCases);
	}

	public void assignTo(Definition definition) {
		if(! definition.language.equals(this.language))
			indenticalTo = definition;
	}

	@Override
	public int compareTo(Object o) {
		return explanatory.compareTo(((Definition) o).explanatory);
	}

	@Override
	public String toString() {
		StringBuilder res = new StringBuilder();
		res.append(String.format("\t: %s%n", defines.getValue().partName));
		res.append(String.format("\t\t- %s%n",explanatory));
		return res.toString();
	}
}
