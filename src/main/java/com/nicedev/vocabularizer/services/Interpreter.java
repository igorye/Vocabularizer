package com.nicedev.vocabularizer.services;

import com.nicedev.vocabularizer.dictionary.*;
import com.nicedev.vocabularizer.dictionary.Dictionary;

import java.util.*;

public class Interpreter {
	private Dictionary dictNative;
	private Dictionary dictForeign;

	public final Language nativeLang;
	public final Language foreignLang;

	final static public boolean NATIVE_TO_FOREIGN = true;
	final static public boolean FOREIGN_TO_NATIVE = false;

	private Map<Integer, Integer> nToF;
	private Map<Integer, Integer> fToN;


	public Interpreter(Dictionary dNative, Dictionary dForeign) {
		this.dictNative = dNative;
		nativeLang = dNative.language;
		this.dictForeign = dForeign;
		foreignLang = dictForeign.language;
		nToF = new TreeMap<>();
		fToN = new TreeMap<>();
	}

	public void addTranslation(String nEntry, String nPartOSName, String nExplanation,
	                           String fEntry, String fPartOSName, String fExplanation) {
		int nEntryID = dictNative.lookupDefinitionEntry(nEntry, nPartOSName, nExplanation);
		assert (nEntryID == dictNative.lookupDefinitionEntry(nExplanation));
		int fEntryID = dictForeign.lookupDefinitionEntry(fEntry, fPartOSName, fExplanation);
		assert (fEntryID == dictForeign.lookupDefinitionEntry(fExplanation));
		nToF.put(nEntryID, fEntryID);
		fToN.put(fEntryID, nEntryID);
	}

	public void addTranslation(String nExplanation, String fExplanation) {
		int nEntryID = dictNative.lookupDefinitionEntry(nExplanation);
		int fEntryID = dictForeign.lookupDefinitionEntry(fExplanation);
		nToF.put(nEntryID, fEntryID);
		fToN.put(fEntryID, nEntryID);
	}

	private Set<String> translate(String entry, String partOfSpeechName, Dictionary dFrom, Dictionary dTo, Map<Integer, Integer> transform){
		int entryID;
		Set<String> translations = new TreeSet<>();
		if (partOfSpeechName.equalsIgnoreCase(PartOfSpeech.ANY)) {
			for(PartOfSpeech partOfSpeech: dFrom.getPartsOfSpeech(entry))
				translations.addAll(translate(entry, partOfSpeech.partName, dFrom, dTo, transform));
			return translations;
		}
		for(Definition definition : dFrom.getDefinitions(entry, partOfSpeechName))
			if (transform.get(definition.explanation.hashCode()) != null) {
				entryID = dFrom.lookupDefinitionEntry(definition.explanation);
				if (entryID != 0) {
					Integer id = transform.get(entryID);
					Vocabula vocabulaFromMeaning = dTo.getVocabulaFromMeaning(id);
					translations.add(vocabulaFromMeaning.headWord);
				}
			}
		return Collections.unmodifiableSet(translations);
	}

	public Set<String> translate(String entry) {
		return translate(entry, PartOfSpeech.ANY);
	}

	public Set<String> translate(String entry, boolean translateDirection) {
		return translate(entry, PartOfSpeech.ANY, translateDirection);
	}

	public Set<String> translate(String entry, String partOfSpeechName) {
		boolean translateDirection = Language.charsMatchLanguage(entry, nativeLang);
		return translate(entry, partOfSpeechName, translateDirection);
	}

	public Set<String> translate(String entry, String partOfSpeechName, boolean translateDirection) {
 		if (translateDirection == NATIVE_TO_FOREIGN) {
			return translate(entry, partOfSpeechName, dictNative, dictForeign, nToF);
		} else {
			return translate(entry, partOfSpeechName, dictForeign, dictNative, fToN);
		}
	}

}
