package com.nicedev.vocabularizer.dictionary;

import com.sun.javaws.exceptions.InvalidArgumentException;
import com.sun.javaws.exceptions.LaunchDescException;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Dictionary implements Serializable{

	final public Language language;
	final private Map<String, Vocabula> articles;
	final private Map<Integer, Vocabula> index;
	private int definitionCount;
	private int vocabulaCount;
//	private Locale locale;


	public Dictionary(String lang) {
		this.language = new Language(lang, "");
		articles = new TreeMap<>();
		index = new TreeMap<>();
//		locale = new Locale(language.langName.toLowerCase());
	}

	public static Dictionary load(String langName, String path) {
		Dictionary dictionary = null;
		try (ObjectInput in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(path)))){
			 dictionary = (Dictionary) in.readObject();
			if (!dictionary.language.langName.equalsIgnoreCase(langName))
				throw new InvalidArgumentException(new String[]{String.format("Invalid target language: looking %s found %s",
						langName, dictionary.language.langName)});
		} catch (IOException | ClassNotFoundException | InvalidArgumentException e) {
			System.err.printf("Unable to load \"%s\"%n%s%n", path, e.getLocalizedMessage());
//			e.printStackTrace();
		}
		if (dictionary != null && dictionary.articles.size() != 0) {
			dictionary.updateStatistics();
			System.out.println(dictionary.language);
		}
		return dictionary;
	}

	public static boolean save(Dictionary dict, String path) {
//		dict.language.savePartsOfSpeech();
		try (ObjectOutput out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(path)))){
			out.writeObject(dict);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean addVocabula(String newEntry, String partOfSpeechName, Set<Definition> definitions) {
		definitions.forEach(definition -> addVocabula(newEntry, partOfSpeechName, definition.explanatory));
		updateStatistics();
		return true;
	}

	public boolean addVocabula(String newEntry, String partOfSpeechName, String explanatory) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		index.putIfAbsent(explanatory.hashCode(), voc);
		boolean res = voc.addForm(pos.partName, explanatory);
		updateStatistics();
		return res;
	}

	public boolean addVocabula(String newEntry, String partOfSpeechName) {
		return addVocabula(newEntry, partOfSpeechName, newEntry);
	}

	public boolean addVocabula(String newEntry) {
		return addVocabula(newEntry, PartOfSpeech.UNDEFINED);
	}

	public boolean addVocabula(Vocabula newVoc) {
		Vocabula vocabula = articles.get(newVoc.charSeq);
		if (vocabula != null){
			//copy to prevent concurrent modification exception
			Set<PartOfSpeech> formsCopy = new TreeSet<>(newVoc.mapPOS.keySet());
			formsCopy.forEach(form -> addVocabula(newVoc.charSeq, form.partName, newVoc.mapPOS.get(form)));
			vocabula.setTranscription(newVoc.getTranscription());
		} else
			articles.put(newVoc.charSeq, newVoc);
		updateStatistics();
		return true;
	}

	public void removeVocabula(String charSeq) {
		removeVocabula(charSeq, PartOfSpeech.ANY);
	}

	public void removeVocabula(String charSeq, String partOfSpeechName) {
		if (partOfSpeechName.equalsIgnoreCase(PartOfSpeech.ANY))
			articles.remove(charSeq);
		else {
			Vocabula vocabula = articles.get(charSeq);
			if (vocabula != null) vocabula.removeForm(partOfSpeechName);
		}
		updateStatistics();
	}

	boolean updateVocabula() {
		return true;
	}

	public String explainVocabula(String filter) {
		StringBuilder res = new StringBuilder();
		int vocCount = getVocabulaCount();
		if (vocCount != 0) {
			String fmt = filter.length() > 0 ? String.format(" starting on \"%s\"", filter) : "";
			res.append(String.format("Thesaurus%s:%n", fmt));
			//maybe use streaming through keySet
			String filterLC = filter.toLowerCase();
			articles.values().stream()
					.filter(voc -> !filterLC.isEmpty()
							               && (voc.charSeq.toLowerCase().contains(filterLC)
									                   || filterLC.contains(voc.charSeq.toLowerCase())))
					.forEach(voc -> res.append(voc).append("\n"));
		}
		return res.toString();
	}

	public String listVocabula(String filter) {
		StringBuilder res = new StringBuilder();
		int vocCount = getVocabulaCount();
		String fmt = filter.length() > 0 ? String.format(" starting on \"%s\"", filter) : "";
		final int[] totalFound = {0};
		if (vocCount != 0) {
			String filterLC = filter.toLowerCase();
			articles.keySet().stream()
					.filter(k -> !filterLC.isEmpty()
							             && (k.toLowerCase().contains(filterLC)
									                 || filterLC.contains(k.toLowerCase())))
					.forEach(k -> {
						res.append(String.format("  %s %s%n", k, getForms(k)));
						totalFound[0]++;
					});
			if (totalFound[0] != 0)
				res.append(String.format("total %d entr%s%n", totalFound[0], totalFound[0] > 1 ? "ies" : "y"));
		}
		res.insert(0, totalFound[0] != 0 ? String.format("Found%s:%n", fmt) : "Nothing found\n");
		return res.toString();
	}

	public String toString(){
		return String.format("Vocabulas: %d%nDefinitions: %d%n", getVocabulaCount(), getDefinitionCount());
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
		return String.format(" %s%n%s",
				entry,
				lookupDefinition(articles.get(entry), language.getPartOfSpeech(partOfSpeechName)));
	}

	private String lookupDefinition(Vocabula voc, PartOfSpeech partOfSpeech) {
		StringBuilder res = new StringBuilder();
		if (partOfSpeech.partName.equals(PartOfSpeech.ANY)) {
			for(PartOfSpeech pos: articles.get(voc.charSeq).mapPOS.keySet())
					res.append(lookupDefinition(voc, pos));
		} else
			articles.get(voc.charSeq).getDefinitions(partOfSpeech).forEach(res::append);

		return res.toString();
	}

	public Vocabula getVocabulaFromMeaning(int ID) {
		return index.get(ID);
	}

	public Set<PartOfSpeech> getForms(String entry) {
		Vocabula voc = articles.get(entry);
		if (voc == null)
			return Collections.<PartOfSpeech>emptySet();
		return Collections.unmodifiableSet(voc.mapPOS.keySet());
	}

	public int getVocabulaCount() {
		return vocabulaCount;
	}

	private void updateStatistics() {
		vocabulaCount = articles.keySet().size();
		definitionCount = articles.values().stream().mapToInt(Vocabula::getMeaningCount).sum();
	}

	public int getDefinitionCount() {
		return definitionCount;
	}

	public Set<Definition> getDefinitions(String entry, String partOfSpeechName) {
		Vocabula vocabula = articles.get(entry);
		if (vocabula == null) return Collections.<Definition>emptySet();
		return vocabula.getDefinitions(language.getPartOfSpeech(partOfSpeechName));
	}

	public boolean containsVocabula(String entry) {
		return articles.get(entry) != null;
	}

	public Vocabula getVocabula(String entry) {
		return articles.get(entry);
	}


}
