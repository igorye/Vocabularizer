package com.nicedev.vocabularizer.dictionary.model;


import com.nicedev.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.util.stream.Collectors.toList;

public class Dictionary implements Serializable {

	private static final long serialVersionUID = -1507438212189317827L;

	private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().getName());

	final public Language language;

	final private Map<String, Vocabula> articles;
//	final private Map<Integer, Vocabula> index;
	private int definitionsCount;
	private int vocabulasCount;
	private static final String CACHE_INVALID = "[I_N_V_A_L_I_D]";
	transient private String cachedRequest;
	transient private Optional<Vocabula> optCachedResponse = Optional.empty();
	transient private boolean invalidateStatistics = true;
//  private Locale locale;


	public Dictionary(String lang) {
		this.language = new Language(lang);
		articles = new HashMap<>();
//		index = new TreeMap<>();
//      locale = new Locale(language.langName.toLowerCase());
	}

	public static Optional<Dictionary> load(String langName, String path) {
		LOGGER.debug("Loading {}", path);
		Dictionary dictionary = null;
		try (ObjectInput in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(path), 1024 * 1024))) {
			dictionary = (Dictionary) in.readObject();
			if (!dictionary.language.langName.equalsIgnoreCase(langName))
				throw new IllegalArgumentException(String.format("Invalid target language: looking %s found %s",
				                                                 langName, dictionary.language.langName));
		} catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
			LOGGER.error("Unable to load \"{}\"\n{}\n", path, e.toString());
		}
		if (dictionary != null && !dictionary.articles.isEmpty()) {
			dictionary.updateStatistics();
		}
		return Optional.ofNullable(dictionary);
	}

	public static boolean save(Dictionary dict, String path) {
		Path filePath;
		if (!Files.exists(filePath = Paths.get(path))) {
			try {
				Path dir = filePath.toAbsolutePath().getParent();
				if (!Files.exists(dir)) Files.createDirectories(dir);
			} catch (IOException e) {
				logError(e);
				path = System.getProperty("user.home").concat("\\").concat(Paths.get(path).getFileName().toString());
				LOGGER.info("Saving to {}", path);
			}
		}
		try (ObjectOutput out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(path), 1024 * 1024))) {
			out.writeObject(dict);
			return true;
		} catch (IOException e) {
			logError(e);
		}
		return false;
	}

	private static void logError(IOException e) {
		LOGGER.error("Error has occurred while saving dictionary: {} {}", e, e.getMessage());
	}

	/*public boolean addDefinitions(String newEntry, String partOfSpeechName, Set<Definition> definitions) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		definitions.forEach(definition -> {
			voc.addDefinition(pos.partName, definition);
		});
		updateStatistics();
		return true;
	}*/

	/*private void addKnownForms(String headWord, String partOfSpeechName, Set<String> knownForms) {
			Optional<Vocabula> voc = Optional.ofNullable(articles.get(headWord));
			voc.ifPresent(vocabula -> vocabula.addKnownForms(partOfSpeechName, knownForms));
	}*/

	/*public boolean addDefinition(String newEntry, String partOfSpeechName, Definition definition) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		voc.addDefinition(pos.partName, definition);
//		SimpleLog.log("adding definition: %s || %s ||", res, definition);
		updateStatistics();
		return true;
	}*/

	/*public boolean addDefinition(String newEntry, String partOfSpeechName, String explanation) {
		if (!Language.charsMatchLanguage(newEntry, language)) return false;
		Vocabula voc = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
		PartOfSpeech pos = language.partsOfSpeech
				                   .getOrDefault(partOfSpeechName, new PartOfSpeech(language, PartOfSpeech.UNDEFINED));
		articles.putIfAbsent(newEntry, voc);
		boolean res = voc.addDefinition(pos.partName, explanation);
		updateStatistics();
		return res;
	}*/

//    public boolean addDefinition(String newEntry, String partOfSpeechName, Set<Definition> definitions) {
//        Vocabula vocabula = articles.getOrDefault(newEntry, new Vocabula(newEntry, language));
//        PartOfSpeech pOS = language.getPartOfSpeech(partOfSpeechName);
//        Set<Definition> defs = vocabula.mapPOS.getOrDefault(pOS, new LinkedHashSet<>(definitions));
//        vocabula.mapPOS.putIfAbsent(pOS,defs);
//        articles.putIfAbsent(newEntry, vocabula);
//        updateStatistics();
//	    return true;
//    }

	/*public boolean addPartOfSpeech(String newEntry, String partOfSpeechName) {
		return addDefinition(newEntry, partOfSpeechName, newEntry);
	}*/
	
	/*public boolean addPartOfSpeech(String newEntry) {
		return addPartOfSpeech(newEntry, PartOfSpeech.UNDEFINED);
	}*/

	public boolean addVocabula(Vocabula newVoc) {
		newVoc.removeInessentialDefinitions();
		int articlesCount = articles.size();
		articles.computeIfPresent(newVoc.headWord, (s, vocabula) -> vocabula.merge(newVoc));
		articles.putIfAbsent(newVoc.headWord, newVoc);
		assert articles.get(newVoc.headWord).getDefinitions().stream().allMatch(Objects::nonNull);
//		updateStatistics();
		invalidateStatistics = true;
		boolean added = articlesCount != articles.size();
		if (added && cachedRequest.equals(newVoc.headWord))
			cachedRequest = CACHE_INVALID;
		return added;
	}

	public int addVocabulas(Collection<Vocabula> newVocabulas) {
		if (newVocabulas.isEmpty()) return 0;
		int before = articles.size();
		newVocabulas.forEach(this::addVocabula);
//		int nAdded = (int) newVocabulas.stream().filter(this::addVocabula).count();
		// remove any vocabula that after possible merge left with no definitions
		newVocabulas.forEach(v -> {
			if (v.getDefinitionsCount() == 0) removeVocabula(v.headWord);
		});
		return articles.size() - before;
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
//		updateStatistics();
		invalidateStatistics = true;
		if (succeed) {
			cachedRequest = CACHE_INVALID;
		}
		return succeed;
	}
	
	/*boolean updateVocabula() {
		return true;
	}*/

	/*public String explainVocabulas(String regex) {
		profiler.start("explainVocabulas(stringBuilderOverFilter)");
		StringBuilder res = new StringBuilder();
		if (getVocabulasCount() != 0) {
			regex = Strings.isAValidPattern(regex)
					        ? regex.toLowerCase()
					        : String.join("", "\\", regex.toLowerCase());
			articles.values().stream()
					.sorted()
					.filter(getVocabulaPredicate(regex))
					.forEach(voc -> res.append(voc).append("\n"));
		}
		profiler.stop().log();
		return res.toString();
	}

	private Predicate<Vocabula> getVocabulaPredicate(String regex) {
		return voc -> regex.isEmpty() || voc.headWord.matches(regex);
	}*/

	public String explainVocabulas(String regex) {
		StringBuilder res = new StringBuilder();
		if (getVocabulasCount() != 0) {
			Collection<String> candidates = filterHeadwords(regex, true);
			if (getVocabula(regex).isPresent()) {
				res.append(articles.get(regex)).append("\n");
			} else {
				candidates.stream()
						.map(this::getVocabula)
						.filter(Optional::isPresent)
						.map(Optional::get)
						.forEach(voc -> res.append(voc).append("\n"));
			}
		}
		return res.toString();
	}

	public Collection<String> filterHeadwords(String regex, boolean sorted) {
		if (articles.isEmpty()) return Collections.emptyList();
		Stream<String> matched;
		if (regex.isEmpty() || regex.trim().matches("\\(\\?[idmsux-]+\\)")) {
			matched = articles.keySet().stream();
		} else {
			String validatedRegex = Strings.getValidPatternOrFailAnyMatch(regex);
			matched = articles.keySet().stream()
					          .filter(hw -> hw.contains(regex) || hw.matches(validatedRegex));
		}
		if (sorted) matched = matched.sorted();
		return matched.collect(toList());
	}

/*
	public String explainVocabulas(String regex) {
		profiler.start("explainVocabulas(collect(joining))");
		String result = "";
		if (getVocabulasCount() != 0) {
			String prefix = Strings.isAValidPattern(regex) ? "(?i)" : "(?i)\\";
			String actRegex = prefix.concat(regex);
			result = articles.keySet().stream()
					         .filter(hw -> actRegex.isEmpty() || hw.matches(actRegex))
					         .sorted()
					         .map(this::getVocabula)
					         .filter(Optional::isPresent)
					         .map(Optional::get)
					         .map(Vocabula::toString)
					         .collect(joining("\n"));
		}
		profiler.stop().log();
		return result;
	}
*/

	public Collection<Vocabula> getVocabulas(String filter) {
		if (filter.isEmpty()) return Collections.unmodifiableCollection(articles.values());
		if (getVocabulasCount() != 0) {
			String fFilter = Strings.isAValidPattern(filter)
					                 ? filter.toLowerCase()
					                 : String.join("", "\\", filter.toLowerCase());
			return articles.values().stream()
					       .filter(vocabula -> vocabula.headWord.toLowerCase().matches(fFilter))
					       .collect(toList());
		}
		return Collections.emptyList();
	}

	public Collection<Vocabula> getVocabulas(Collection<String> headwords) {
		if (headwords.isEmpty()) return Collections.emptySet();
		if (getVocabulasCount() != 0) {
			return headwords.stream()
							.map(articles::get)
							.filter(Objects::nonNull)
							.collect(toList());
		}
		return Collections.emptyList();
	}

	public String toString() {
		return String.format("Vocabulas: %d | Definitions: %d", getVocabulasCount(), getDefinitionsCount());
	}

	public Collection<PartOfSpeech> getPartsOfSpeech(String entry) {
		return Optional.ofNullable(articles.get(entry))
				       .map(Vocabula::getPartsOfSpeech)
				       .orElse(Collections.emptySet());
	}

	public int getVocabulasCount() {
		if (invalidateStatistics) updateStatistics();
		return vocabulasCount;
	}

	private void updateStatistics() {
		// by agreement tenses of verbs, combining forms of adjectives ets. are not distinct headwords
		vocabulasCount = (int) articles.values().stream()
				                       .filter(Vocabula::hasDecentDefinition)
				                       .count();
		definitionsCount = articles.values().stream()
				                   .filter(Vocabula::hasDecentDefinition)
				                   .mapToInt(Vocabula::getDecentDefinitionsCount).sum();
		invalidateStatistics = false;
	}

	public int getDefinitionsCount() {
		if (invalidateStatistics) updateStatistics();
		return definitionsCount;
	}

	public Collection<Definition> getDefinitions(String entry, String partOfSpeechName) {
		Vocabula vocabula = articles.get(entry);
		return vocabula == null
					   ? Collections.emptyList()
					   : vocabula.getDefinitions(language.getPartOfSpeech(partOfSpeechName));
	}

	public boolean containsVocabula(String headword) {
		return findVocabula(headword).isPresent();
	}

	public Optional<Vocabula> getVocabula(String headword) {
		return Optional.ofNullable(articles.get(headword));
	}

	public Optional<Vocabula> findVocabula(String headword) {
		if (headword.isEmpty()) return Optional.empty();
		if (isCached(headword)) return optCachedResponse;
		optCachedResponse = Optional.ofNullable(articles.get(headword));
		if (optCachedResponse.isEmpty()) {
			String regex = "(?i)".concat(Strings.escapeSymbols(headword, "[()]"));
			List<String> candidates = articles.keySet().stream()
					                          .filter(s -> s.matches(regex))
					                          .filter(s -> headword.substring(1).equals(s.substring(1)))
					                          .collect(toList());
			optCachedResponse = (Character.isUpperCase(headword.charAt(0)) && candidates.size() == 1)
					                    ? Optional.ofNullable(articles.get(candidates.get(0)))
					                    : Optional.empty();
		}
		cachedRequest = headword;
		return optCachedResponse;
	}

	public Collection<Vocabula> filterVocabulas(String regex) {
		if (getVocabulasCount() != 0) {
			return filterHeadwords(regex, false).stream()
					       .map(articles::get)
					       .collect(toList());
		}
		return Collections.emptyList();
	}

	private boolean isCached(String headWord) {
		return (optCachedResponse != null) && (cachedRequest != null) && cachedRequest.equals(headWord);
	}

}
