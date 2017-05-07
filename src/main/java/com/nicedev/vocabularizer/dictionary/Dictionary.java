package com.nicedev.vocabularizer.dictionary;


import com.nicedev.util.SimpleLog;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.nicedev.util.Strings.getValidPattern;
import static com.nicedev.util.Strings.isAValidPattern;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

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
		try (ObjectInput in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(path), 1024*1024))) {
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
		try (ObjectOutput out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(path), 1024*1024))) {
			out.writeObject(dict);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public boolean addDefinitions(String newEntry, String partOfSpeechName, Set<Definition> definitions) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		definitions.forEach(definition -> {
			index.putIfAbsent(definition.explanation.hashCode(), voc);
			boolean res = voc.addPartOfSpeech(pos.partName, definition);
		});
		updateStatistics();
		return true;
	}

	private void addKnownForms(String headWord, String partOfSpeechName, Set<String> knownForms) {
			Optional<Vocabula> voc = Optional.ofNullable(articles.get(headWord));
			voc.ifPresent(vocabula -> vocabula.addKnownForms(partOfSpeechName, knownForms));
	}

	public boolean addDefinition(String newEntry, String partOfSpeechName, Definition definition) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		index.putIfAbsent(definition.explanation.hashCode(), voc);
		boolean res = voc.addPartOfSpeech(pos.partName, definition);
		SimpleLog.log("adding definition: %s || %s ||", res, definition);
		updateStatistics();
		return res;
	}

	public boolean addDefinition(String newEntry, String partOfSpeechName, String explanation) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		index.putIfAbsent(explanation.hashCode(), voc);
		boolean res = voc.addPartOfSpeech(pos.partName, explanation);
		updateStatistics();
		return res;
	}

//    public boolean addDefinition(String newEntry, String partOfSpeechName, Set<Definition> definitions) {
//        Vocabula vocabula = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
//        PartOfSpeech pOS = language.getPartOfSpeech(partOfSpeechName);
//        Set<Definition> defs = vocabula.mapPOS.getOrDefault(pOS, new LinkedHashSet<>(definitions));
//        vocabula.mapPOS.putIfAbsent(pOS,defs);
//        articles.putIfAbsent(newEntry, vocabula);
//        updateStatistics();
//	    return true;
//    }

	public boolean addPartOfSpeech(String newEntry, String partOfSpeechName) {
		return addDefinition(newEntry, partOfSpeechName, newEntry);
	}
	
	public boolean addPartOfSpeech(String newEntry) {
		return addPartOfSpeech(newEntry, PartOfSpeech.UNDEFINED);
	}
	
	public boolean addPartsOfSpeech(Vocabula newVoc) {
		Optional<Vocabula> optVocabula = ofNullable(articles.get(newVoc.headWord));
		optVocabula.ifPresent(availableVoc -> {
			newVoc.getPartsOfSpeech().stream()
					.filter(pOS -> !availableVoc.getPartsOfSpeech().contains(pOS) || !availableVoc.hasDecentDefinition(pOS))
					.forEach(pOS -> {
						addKnownForms(newVoc.headWord, pOS.partName, newVoc.getKnownForms(pOS));
						addDefinitions(newVoc.headWord, pOS.partName, newVoc.getDefinitions(pOS));
					});
			if (availableVoc.getPartsOfSpeech().size() > 1) availableVoc.removePartOfSpeech(PartOfSpeech.UNDEFINED);
			if (availableVoc.getTranscription().isEmpty()) availableVoc.setTranscription(newVoc.getTranscription());
		});
		if (!optVocabula.isPresent()) articles.put(newVoc.headWord, newVoc);
		updateStatistics();
		return true;
	}
	
	public int addVocabulas(Collection<Vocabula> newVocabulas) {
		int before = getVocabulaCount();
		newVocabulas.forEach(this::addPartsOfSpeech);
		return getVocabulaCount() - before;
	}
	
	public boolean removeVocabula(String charSeq) {
		return removeVocabula(charSeq, PartOfSpeech.ANY);
	}
	
	public boolean removeVocabula(String charSeq, String partOfSpeechName) {
		boolean succeed = false;
		if (partOfSpeechName.equalsIgnoreCase(PartOfSpeech.ANY))
			succeed = articles.remove(charSeq) != null;
		else {
			Vocabula vocabula = articles.get(charSeq);
			if (vocabula != null) succeed = vocabula.removePartOfSpeech(partOfSpeechName);
		}
		updateStatistics();
		return succeed;
	}
	
	boolean updateVocabula() {
		return true;
	}
	
	public String explainVocabula(String filter) {
		StringBuilder res = new StringBuilder();
		if (getVocabulaCount() != 0) {
			filter = isAValidPattern(filter)  ? filter.toLowerCase()
					                              : String.join("", "\\", filter.toLowerCase());
			articles.values().stream()
					.filter(getVocabulaPredicate(filter))
					.forEach(voc -> res.append(voc).append("\n"));
		}
		return res.toString();
	}
	
	private Predicate<Vocabula> getVocabulaPredicate(String filter) {
		return voc -> filter.isEmpty() || voc.headWord.toLowerCase().matches(filter);
	}
	
	public Collection<Vocabula> getVocabulas(String filter) {
		if (filter.isEmpty()) return Collections.unmodifiableCollection(articles.values());
		if (getVocabulaCount() != 0) {
			String fFilter = isAValidPattern(filter) ? filter.toLowerCase() : String.join("", "\\", filter.toLowerCase());
			return articles.values().stream()
					       .filter(vocabula -> vocabula.headWord.toLowerCase().matches(fFilter))
					       .collect(toList());
		}
		return Collections.emptyList();
	}

	public Set<Vocabula> getVocabulas(Collection<String> headwords) {
		if (headwords.isEmpty()) return Collections.emptySet();
		if (getVocabulaCount() != 0) {
			return headwords.stream()
					       .filter(articles::containsKey)
					       .map(articles::get)
					       .collect(toSet());
		}
		return Collections.emptySet();
	}
	
	public List<Vocabula> filterVocabulas(String filter) {
		if (getVocabulaCount() != 0) {
			String fFilter = isAValidPattern(filter) ? filter.toLowerCase() : String.join("", "\\", filter.toLowerCase());
			return articles.keySet().stream()
					       .filter(k -> k.toLowerCase().contains(fFilter) || k.toLowerCase().matches(fFilter))
					       .map(articles::get)
					       .collect(toList());
		}
		return Collections.emptyList();
	}
	
	public Set<String> filterHeadwords(String filter, String... matchFlags) {
		if (filter.isEmpty()) return Collections.unmodifiableSet(articles.keySet());
		if (getVocabulaCount() != 0) {
			String fFilter = getValidPattern(filter, matchFlags);
			String filterLC = filter.toLowerCase();
			return articles.keySet().stream()
					       .filter(key -> key.isEmpty() || key.toLowerCase().contains(filterLC) || key.matches(fFilter))
					       .collect(toSet());
		}
		return Collections.emptySet();
	}
	
	public String toString() {
		return String.format("Vocabulas: %d | Definitions: %d", getVocabulaCount(), getDefinitionCount());
	}
	
	public int lookupDefinitionEntry(String entry, String partOfSpeechName, String explanation) {
		Vocabula vocabula = articles.get(entry);
		if (vocabula == null) return 0;
		return vocabula.getDefinitions(partOfSpeechName).stream()
				       .filter(definition -> definition.explanation.equals(explanation))
				       .mapToInt(definition -> definition.explanation.hashCode()).findFirst().orElse(0);
	}
	
	public int lookupDefinitionEntry(String explanation) {
		return index.keySet().contains(explanation.hashCode()) ? explanation.hashCode() : 0;
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
