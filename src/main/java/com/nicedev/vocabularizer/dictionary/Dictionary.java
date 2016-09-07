package com.nicedev.vocabularizer.dictionary;

import com.sun.javaws.exceptions.InvalidArgumentException;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Dictionary implements Serializable{

	final public Language language;
	final private Map<String, Vocabula> articles;
	final private Map<Integer, Vocabula> index;


	public Dictionary(String lang) {
		this.language = new Language(lang, "");
		articles = new TreeMap<>();
		index = new TreeMap<>();
	}

	public static Dictionary load(String langName, String path) {
		Dictionary dictionary = null;
		try (ObjectInput in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(path)))){
			 dictionary = (Dictionary) in.readObject();
			if (!dictionary.language.langName.equals(langName))
				throw new InvalidArgumentException(new String[]{String.format("Invalid target language: looking %s found %s",
						langName, dictionary.language.langName)});
		} catch (IOException | ClassNotFoundException | InvalidArgumentException e) {
			e.printStackTrace();
		}
		return dictionary;
	}

	public static boolean save(Dictionary dict, String path) {
		try (ObjectOutput out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(path)))){
			out.writeObject(dict);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean addVocabula(String newEntry, String partOfSpeechName, String explanatory) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		index.putIfAbsent(explanatory.hashCode(), voc);
		return voc.addForm(pos.partName, explanatory);
	}

	public boolean addVocabula(String newEntry, String partOfSpeechName) {
		return addVocabula(newEntry, partOfSpeechName, newEntry);
	}

	public boolean addVocabula(String newEntry) {
		return addVocabula(newEntry, PartOfSpeech.UNDEFINED);
	}

	public void removeVocabula(String charSeq, String partOfSpeechName) {
		if (partOfSpeechName.equals(PartOfSpeech.ANY))
			articles.remove(charSeq);
		else
			articles.get(charSeq).removeForm(partOfSpeechName);
	}

	boolean updateVocabula() {
		return true;
	}

	public String toString(){
		int vocCount = getVocabulaCount();
		StringBuilder res = new StringBuilder();
		res.append(language);
		res.append(String.format("Vocabulas: %d%nDefinitions: %d%n", vocCount, getDefinitionCount()));
		if (vocCount != 0) {
			res.append("Thesaurus:\n");
			for(Vocabula voc: articles.values())
				res.append(voc).append("\n");
		}
		return res.toString();
	}

	public int lookupDefinitionEntry(String entry, String partOfSpeechName, String explanatory) {
		Vocabula voc = articles.get(entry);
		if(voc == null) return 0;
		for(Definition definition : voc.getDefinitions(partOfSpeechName))
			if(definition.explanatory.equals(explanatory))
				return definition.explanatory.hashCode();
		return 0;
	}

	public int lookupDefinitionEntry(String explanatory) {
		if (index.keySet().contains(explanatory.hashCode()))
				return explanatory.hashCode();
		return 0;
	}

	public String lookupDefinition(String entry) {
		return lookupDefinition(entry, PartOfSpeech.ANY);
	}

	public String lookupDefinition(String entry, String partOfSpeechName) {
		StringBuilder res = new StringBuilder();
		res.append(String.format(" %s%n", entry));
		res.append(lookupDefinition(articles.get(entry), language.partsOfSpeech.get(partOfSpeechName)));
		return res.toString();
	}

	private String lookupDefinition(Vocabula voc, PartOfSpeech PoS) {
		StringBuilder res = new StringBuilder();
		if (PoS.partName.equals(PartOfSpeech.ANY)) {
			for(PartOfSpeech pos: articles.get(voc.charSeq).forms.keySet())
					res.append(lookupDefinition(voc, pos));
		} else {
			articles.get(voc.charSeq).getDefinitions(PoS).forEach(res::append);
		}
		return res.toString();
	}

	public Vocabula getVocabulaFromMeaning(int ID) {
		return index.get(ID);
	}

	public Set<PartOfSpeech> getForms(String entry) {
		Vocabula voc = articles.get(entry);
		if (voc == null) return Collections.<PartOfSpeech>emptySet();
		return Collections.unmodifiableSet(voc.forms.keySet());
	}

	public int getVocabulaCount() {
		return articles.keySet().size();
	}

	public int getDefinitionCount() {
		return articles.values().stream().mapToInt(Vocabula::getMeaningCount).sum();
	}

	public Set<Definition> getDefinitions(String entry, String partOfSpeechName) {
		Vocabula vocabula = articles.get(entry);
		if (vocabula == null) return Collections.<Definition>emptySet();
		return vocabula.getDefinitions(language.partsOfSpeech.get(partOfSpeechName));
	}
}
