package com.nicedev.vocabularizer.dictionary;

import java.io.Serializable;
import java.util.*;

/**
 * Created by sugarik on 28.08.2016.
 */
public class Meaning implements Serializable, Comparable{
	protected final Language language;
	protected String explanatory;
	protected int hashCode;
	protected Meaning indenticalTo;
	final public Set<String> useCases;
	final public Set<Meaning> synonyms;

	public Meaning(String langName, String explanatory, Set<String> newUseCases, Set<Meaning> newSynonyms) {
		this.explanatory = explanatory;
		this.language = new Language(langName, "");
		hashCode = explanatory.hashCode();
		synonyms = new TreeSet<>(newSynonyms);
		useCases = new TreeSet<>(newUseCases);
	}

	public Meaning(String language, String explanatory) {
		this(language, explanatory,  Collections.<String>emptySortedSet(), Collections.<Meaning>emptySortedSet());
	}

	public void update(String explanatory) {
		this.explanatory = explanatory;
	}

	public void assignTo(Meaning meaning) {
		if(! meaning.language.equals(this.language))
			indenticalTo = meaning;
	}

	@Override
	public int compareTo(Object o) {
		return explanatory.compareTo(((Meaning) o).explanatory);
	}

	@Override
	public String toString() {
		return explanatory;
	}
}
