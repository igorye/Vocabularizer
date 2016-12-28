package com.nicedev.vocabularizer.dictionary;


import javax.swing.text.StringContent;
import javax.swing.text.html.HTML;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.nicedev.util.Strings.getValidPattern;
import static com.nicedev.util.Strings.isAValidPattern;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class Dictionary implements Serializable{
	
	private static final long serialVersionUID = -1507438212189317827L;
	
	final public Language language;
	final private Map<String, Vocabula> articles;
	final private Map<Integer, Vocabula> index;
	private int definitionCount;
	private int vocabulaCount;
//  private Locale locale;
	
	
	public Dictionary(String lang) {
		this.language = new Language(lang);
		articles = new TreeMap<>();
		index = new TreeMap<>();
//      locale = new Locale(language.langName.toLowerCase());
	}
	
	public static Optional<Dictionary> load(String langName, String path) {
		Dictionary dictionary = null;
		try (ObjectInput in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(path)))) {
			dictionary = (Dictionary) in.readObject();
			if (!dictionary.language.langName.equalsIgnoreCase(langName))
				throw new IllegalArgumentException(String.format("Invalid target language: looking %s found %s",
						langName, dictionary.language.langName));
		} catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
			System.err.printf("Unable to load \"%s\"%n%s%n", path, e.getLocalizedMessage());
		}
		if (dictionary != null && !dictionary.articles.isEmpty()) {
			dictionary.updateStatistics();
		}
		return ofNullable(dictionary);
	}
	
	public static boolean save(Dictionary dict, String path) {
		try (ObjectOutput out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(path)))) {
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
	
	private void addKnownForms(String headWord, String partOfSpeechName, Set<String> knownForms) {
			Optional<Vocabula> voc = Optional.ofNullable(articles.get(headWord));
			voc.ifPresent(vocabula -> vocabula.addKnownForms(partOfSpeechName, knownForms));
	}

	public boolean addVocabula(String newEntry, String partOfSpeechName, String explanatory) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		index.putIfAbsent(explanatory.hashCode(), voc);
		boolean res = voc.addPartOfSpeech(pos.partName, explanatory);
		updateStatistics();
		return res;
	}
	
	public boolean addPartOfSpeech(String newEntry, String partOfSpeechName) {
		return addDefinition(newEntry, partOfSpeechName, newEntry);
	}
	
	public boolean addPartOfSpeech(String newEntry) {
		return addPartOfSpeech(newEntry, PartOfSpeech.UNDEFINED);
	}
	
	public boolean addPartsOfSpeech(Vocabula newVoc) {
		Optional<Vocabula> vocabula = ofNullable(articles.get(newVoc.headWord));
		vocabula.ifPresent(voc -> {
			newVoc.getPartsOfSpeech().stream()
					.filter(pOS -> !voc.getPartsOfSpeech().contains(pOS) || !voc.hasDecentDefinition(pOS))
					.forEach(pOS -> {
						addDefinitions(newVoc.headWord, pOS.partName, newVoc.getDefinitions(pOS));
						addKnownForms(newVoc.headWord, pOS.partName, newVoc.getKnownForms(pOS));
					});
			if (voc.getTranscription().isEmpty()) voc.setTranscription(newVoc.getTranscription());
		});
		if (!vocabula.isPresent()) articles.put(newVoc.headWord, newVoc);
		updateStatistics();
		return true;
	}
	
	public int addVocabulas(Collection<Vocabula> newVocabulas) {
		int before = getVocabulaCount();
		newVocabulas.forEach(this::addPartsOfSpeech);
		return getVocabulaCount() - before;
	}

	public void removeVocabula(String charSeq) {
		removeVocabula(charSeq, PartOfSpeech.ANY);
	}

	public void removeVocabula(String charSeq, String partOfSpeechName) {
		if (partOfSpeechName.equalsIgnoreCase(PartOfSpeech.ANY))
			articles.remove(charSeq);
		else {
			Vocabula vocabula = articles.get(charSeq);
			if (vocabula != null) vocabula.removePartOfSpeech(partOfSpeechName);
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
			//maybe use streaming through keySet
			String filterLC = filter.toLowerCase();
			if(filterLC.matches("\\p{Punct}"))
				filterLC = String.join("", "\\", filterLC);
			final String finalFilterLC = filterLC;
			articles.values().stream()
					.filter(voc -> finalFilterLC.isEmpty()
							               || voc.headWord.toLowerCase().matches(finalFilterLC))
					.forEach(voc -> res.append(voc).append("\n"));
		}
		return res.toString();
	}

	public Collection<String> listVocabula(String filter) {
		if (getVocabulaCount() != 0) {
			String filterLC = String.join("\\", filter.split("(?=\\(|\\))")).toLowerCase();
			return articles.keySet().stream().filter(k -> filterLC.isEmpty() || k.toLowerCase().contains(filterLC))
					                                 .collect(toList());
		}
		return Collections.emptyList();
	}
	
	public Collection<Vocabula> filterVocabulas(String filter) {
		if (getVocabulaCount() != 0) {
			String fFilter = isAValidPattern(filter) ? filter.toLowerCase() : String.join("", "\\", filter.toLowerCase());
			return articles.keySet().stream()
					       .filter(k -> k.toLowerCase().contains(fFilter) || k.toLowerCase().matches(fFilter))
					       .map(articles::get)
					       .collect(toList());
		}
		return Collections.emptyList();
	}
	
	public Collection<String> filterHeadwords(String filter, String... matchFlags) {
		if (filter.isEmpty()) return Collections.unmodifiableCollection(articles.keySet());
		if (getVocabulaCount() != 0) {
			String fFilter = getValidPattern(filter, matchFlags);
			String filterLC = filter.toLowerCase();
			return articles.keySet().stream()
					       .filter(key -> key.isEmpty() || key.toLowerCase().contains(filterLC) || key.matches(fFilter))
					       .collect(toList());
		}
		return Collections.emptyList();
	}
	
	public String toString() {
		return String.format("Vocabulas: %d | Definitions: %d", getVocabulaCount(), getDefinitionCount());
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
			for(PartOfSpeech pos: articles.get(voc.headWord).mapPOS.keySet())
					res.append(lookupDefinition(voc, pos));
		} else
			articles.get(voc.headWord).getDefinitions(partOfSpeech).forEach(res::append);

		return res.toString();
	}

    /*public String lookupDefinition(String entry) {
        return lookupDefinition(entry, PartOfSpeech.ANY);
    }

    public String lookupDefinition(String entry, String partOfSpeechName) {
        return String.format(" %s%n%s", entry,
                lookupDefinition(articles.get(entry), language.getPartOfSpeech(partOfSpeechName)));
    }

    private String lookupDefinition(Vocabula vocabula, PartOfSpeech partOfSpeech) {
        StringBuilder res = new StringBuilder();
        if (partOfSpeech.partName.equals(PartOfSpeech.ANY)) {
            vocabula.mapPOS.keySet().forEach(pos -> res.append(lookupDefinition(vocabula, pos)));
        } else
            vocabula.getDefinitions(partOfSpeech).forEach(res::append);
        return res.toString();
    }*/
	
	public Vocabula getVocabulaFromMeaning(int ID) {
		return index.get(ID);
	}
	
	public Set<PartOfSpeech> getPartsOfSpeech(String entry) {
		return ofNullable(articles.get(entry))
				       .map(Vocabula::getPartsOfSpeech)
				       .orElse(Collections.emptySet());
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
		return ofNullable(articles.get(entry))
				       .map(vocabula -> vocabula.getDefinitions(language.getPartOfSpeech(partOfSpeechName)))
				       .orElse(Collections.emptySet());
	}
	
	public boolean containsVocabula(String entry) {
		return ofNullable(articles.get(entry)).isPresent();
	}
	
	public Optional<Vocabula> getVocabula(String entry) {
		return ofNullable(articles.get(entry));
	}
	
	private String splitBTag(String source) {
		if (!source.matches(".*<b>.*")) return source;
		String result = source;
		Matcher matcher = Pattern.compile("(<b>[^<>]+(, [^<>]+)*</b>)").matcher(source);
		while (matcher.find()) {
			String definitionVariants = matcher.group(1);
			result = result.replace(definitionVariants, definitionVariants.replaceAll(", ", "</b>, <b>"));
		}
		return result;
	}
	
}
