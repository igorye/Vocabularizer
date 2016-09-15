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

	public void addTranslation(String nEntry, String nPartOSName, String nExplanatory,
	                           String fEntry, String fPartOSName, String fExplanatory) {
		int nEntryID = dictNative.lookupDefinitionEntry(nEntry, nPartOSName, nExplanatory);
		assert (nEntryID == dictNative.lookupDefinitionEntry(nExplanatory));
		int fEntryID = dictForeign.lookupDefinitionEntry(fEntry, fPartOSName, fExplanatory);
		assert (fEntryID == dictForeign.lookupDefinitionEntry(fExplanatory));
		nToF.put(nEntryID, fEntryID);
		fToN.put(fEntryID, nEntryID);
	}

	public void addTranslation(String nExplanatory, String fExplanatory) {
		int nEntryID = dictNative.lookupDefinitionEntry(nExplanatory);
		int fEntryID = dictForeign.lookupDefinitionEntry(fExplanatory);
		nToF.put(nEntryID, fEntryID);
		fToN.put(fEntryID, nEntryID);
	}

	private Set<String> translate(String entry, String partOfSpeechName, Dictionary dFrom, Dictionary dTo, Map<Integer, Integer> transform){
		int entryID;
		Set<String> translations = new TreeSet<>();
		if (partOfSpeechName.equalsIgnoreCase(PartOfSpeech.ANY)) {
			for(PartOfSpeech partOfSpeech: dFrom.getForms(entry))
				translations.addAll(translate(entry, partOfSpeech.partName, dFrom, dTo, transform));
			return translations;
		}
		for(Definition definition : dFrom.getDefinitions(entry, partOfSpeechName))
			if (transform.get(definition.explanatory.hashCode()) != null) {
				entryID = dFrom.lookupDefinitionEntry(definition.explanatory);
				if (entryID != 0) {
					Integer id = transform.get(entryID);
					Vocabula vocabulaFromMeaning = dTo.getVocabulaFromMeaning(id);
					translations.add(vocabulaFromMeaning.charSeq);
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
