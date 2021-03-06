package com.nicedev.vocabularizer;

import com.nicedev.tts.service.TTSPlaybackService;
import com.nicedev.tts.service.TTSService;
import com.nicedev.vocabularizer.dictionary.model.Vocabula;
import com.nicedev.vocabularizer.dictionary.model.Dictionary;
import com.nicedev.vocabularizer.dictionary.parser.MerriamWebsterParser;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.*;

public class ExpositorCLI {

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(
			MethodHandles.lookup().lookupClass().getName());

	private static final boolean ACCEPT_SIMILAR = Boolean.getBoolean(
			System.getProperties().getProperty("acceptSimilar", "false"));
	private static final String PROJECT_NAME = "Vocabularizer";
	private static final String USER_HOME = System.getProperties().getProperty("user.home");
	private static final String PROJECT_HOME = System.getProperty(PROJECT_NAME + ".home",
	                                                              String.format("%s\\%s", USER_HOME, PROJECT_NAME));
	private static final String DICT_PATH = PROJECT_HOME.concat("\\dict");
	private static final String storageEn = String.format("%s\\%s.dict", DICT_PATH, "english");
	static private TTSService pronouncingService;
	static private MerriamWebsterParser[] expositors;
	static private Dictionary dictionary;

	public static void main(String[] args) {
		loadDictionary();
		pronouncingService = new TTSPlaybackService(1);
		expositors = new MerriamWebsterParser[] { new MerriamWebsterParser(dictionary, false),
		                                          new MerriamWebsterParser(dictionary, true) };
		System.out.println(dictionary);
		Scanner input;
		input = (args.length > 0) ? new Scanner(args[0]) : new Scanner(System.in);
		String query;
		int updateCount = 0;
		String lastQuerry = "";
		try {
			while (input.hasNextLine()) {
				query = input.nextLine().trim();
				int defCount = dictionary.getDefinitionsCount();
				if (query.trim().isEmpty())
					continue;
				if (query.equals("<"))
					query = lastQuerry;
				lastQuerry = query;
				if (query.startsWith("-")) {
					query = query.replaceFirst("-", "");
					String[] tokens = query.split("\" ");
					if (tokens.length == 0)
						tokens = query.split("\\s");
					if (tokens.length == 2)
						dictionary.removeVocabula(tokens[0].replace("\"", "").trim(), tokens[1]);
					else
						dictionary.removeVocabula(tokens[0].replace("\"", "").trim());
					if (defCount != dictionary.getDefinitionsCount())
						updateCount++;
					System.out.println(dictionary);
					continue;
				}
				if (query.startsWith("::")) {
					query = query.substring(2).trim();
					System.out.println(dictionary.explainVocabulas(query));
					Optional<Vocabula> queried = dictionary.findVocabula(query);
					queried.ifPresent(ExpositorCLI::pronounce);
					continue;
				} else if (query.startsWith(":")) {
					query = query.substring(1).trim();
					System.out.println(collectionToString(dictionary.filterHeadwords(query, false), query));
					continue;
				}
				String[] tokens = query.split("\\s{2,}|\t|\\[|\\\\");
				Predicate<String> notEmpty = s -> !s.trim().isEmpty();
				query = Stream.of(tokens).filter(notEmpty).map(String::trim).findFirst().orElse("");
				boolean acceptSimilar = query.startsWith("~");
				if (acceptSimilar) query = query.substring(1);
				Optional<Vocabula> vocabula = acceptSimilar ? Optional.empty() : dictionary.findVocabula(query);
				if (vocabula.isEmpty()) {
					acceptSimilar |= ACCEPT_SIMILAR;
					Collection<Vocabula> vocabulas = findVocabula(query, acceptSimilar);
					if (!vocabulas.isEmpty() && dictionary.addVocabulas(vocabulas) > 0) {
						updateCount++;
						System.out.printf("found:%n%s%n", collectionToString(vocabulas.stream()
								                                                     .map(voc -> voc.headWord)
								                                                     .distinct()
								                                                     .collect(toList()), query));
					} else
						System.out.printf("No definition for \"%s\"%nMaybe %s?%n", query, getSuggestions());
				} else {
					System.out.printf("found: %s%n", vocabula.get());
					pronounce(vocabula.get());
				}

				if (defCount != dictionary.getDefinitionsCount())
					System.out.println(dictionary);
				if (updateCount % 50 == 0)
					Dictionary.save(dictionary, storageEn);
				if (updateCount % 500 == 0)
					Dictionary.save(dictionary, storageEn.concat(".back"));
			}
		} catch (Exception e) {
			LOGGER.error("{} {}", e, e.getMessage());
		} finally {
			Dictionary.save(dictionary, storageEn);
			pronouncingService.release();
		}
		//Dictionary.append(com.nicedev.com.nicedev.dictionary.model, storageEn);
	}

	private static void loadDictionary() {
		Optional<Dictionary> loaded = Dictionary.load("english", storageEn);
		String backUpPath = storageEn.concat(".back");
		if (loaded.isPresent()) {
			dictionary = loaded.get();
			try {
				Files.copy(Paths.get(storageEn), Paths.get(backUpPath), REPLACE_EXISTING);
			} catch (IOException e) {
				LOGGER.error("Unable to create backup: {}", e.getMessage());
			}
		} else {
			//read backup or create new
			dictionary = Dictionary.load("english", backUpPath).orElse(new Dictionary("english"));
		}
	}

	private static String collectionToString(Collection<String> strings, String sortBy) {
		StringBuilder res = new StringBuilder();
		if (!strings.isEmpty()) {
			String listed = strings.parallelStream()
					                .collect(partitioningBy(s -> !s.startsWith(sortBy))).values().stream()
					                .flatMap(List::stream)
					                .map(s -> String.format("  %s %s", s, dictionary.getPartsOfSpeech(s)))
													.sorted()
					                .collect(joining("\n"));
			int size = strings.size();
			res.append(listed).append(String.format("%ntotal %d entr%s%n", size, size > 1 ? "ies" : "y"));
		} else {
			res.append("Nothing found\n");
		}
		return res.toString();
	}

	private static Collection<String> getSuggestions() {
		return Stream.of(expositors)
				       .flatMap(expositor -> expositor.getRecentSuggestions().stream())
							 .distinct()
				       .sorted()
				       .collect(toList());
	}

	@SuppressWarnings("unchecked")
	private static Collection<Vocabula> findVocabula(String query, boolean acceptSimilar) {
		return Stream.of(expositors)
				       .parallel()
				       .map(expositor -> new Object[] { expositor.priority, expositor.getVocabula(query, acceptSimilar) })
				       .sorted(Comparator.comparingInt(tuple -> (int) tuple[0]))
				       .flatMap(tuple -> ((Collection<Vocabula>) tuple[1]).stream())
				       .collect(LinkedHashSet::new, Set::add, Set::addAll);
	}

	private static void pronounce(Vocabula vocabula) {
		pronouncingService.clearQueue();
		Iterator<String> sIt = vocabula.getPronunciationSources().iterator();
		if (sIt.hasNext()) pronouncingService.enqueue(sIt.next(), 500);
		else pronouncingService.enqueue(vocabula.headWord, 500);
	}

}