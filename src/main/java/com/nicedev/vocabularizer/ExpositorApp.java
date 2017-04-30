package com.nicedev.vocabularizer;


import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.nicedev.vocabularizer.dictionary.Vocabula;
import com.nicedev.vocabularizer.services.Expositor;
import com.nicedev.vocabularizer.services.sound.PronouncingService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.nicedev.util.SimpleLog.log;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.*;

public class ExpositorApp {

	private static final boolean ACCEPT_SIMILAR = Boolean.getBoolean(System.getProperties().getProperty("acceptSimilar", "false"));
	static private String home = System.getProperties().getProperty("user.home");
	private static String storageEn = String.format("%s\\%s.dict", home, "english");
	static private PronouncingService pronouncingService;
	static private Expositor[] expositors;
	static private Dictionary dictionary;

//  enum Command {DELETE, LIST, EXPLAIN, FIND}

	public static void main(String[] args) {
		loadDictionary();
		pronouncingService = new PronouncingService(5, false);
		expositors = new Expositor[]{new Expositor(dictionary, false), new Expositor(dictionary, true)};
		System.out.println(dictionary);
		Scanner input;
		input = (args.length > 0) ? new Scanner(args[0]) : new Scanner(System.in);
		String query;
		int updateCount = 0;
		String lastQuerry = "";
		while (input.hasNextLine()) {
			query = input.nextLine().trim();
			int defCount = dictionary.getDefinitionCount();
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
				if (defCount != dictionary.getDefinitionCount())
					updateCount++;
				System.out.println(dictionary);
				continue;
			}
			if (query.startsWith("::")) {
				query = query.substring(2, query.length()).trim();
				System.out.println(dictionary.explainVocabula(query));
				Optional<Vocabula> queried = dictionary.getVocabula(query);
				queried.ifPresent(ExpositorApp::pronounce);
				continue;
			} else if (query.startsWith(":")) {
				query = query.substring(1, query.length()).trim();
				System.out.println(collectionToString(dictionary.filterHeadwords(query, "i"), query));
				continue;
			}
			try {
				String[] tokens = query.split("\\s{2,}|\t|\\[|\\\\");
				Predicate<String> notEmpty = s -> !s.trim().isEmpty();
				query = Stream.of(tokens).filter(notEmpty).map(String::trim).findFirst().orElse("");
				boolean acceptSimilar = query.startsWith("~");
				if (acceptSimilar) query = query.substring(1, query.length());
				acceptSimilar |= ACCEPT_SIMILAR;
				Optional<Vocabula> vocabula = dictionary.getVocabula(query);
				if (!vocabula.isPresent()) {
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

				if (defCount != dictionary.getDefinitionCount())
					System.out.println(dictionary);
				if (updateCount % 5 == 0)
					Dictionary.save(dictionary, storageEn);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				Dictionary.save(dictionary, storageEn);
			}
		}
		Dictionary.save(dictionary, storageEn);
		pronouncingService.release();
	}

	private static void loadDictionary() {
		Optional<Dictionary> loaded = Dictionary.load("english", storageEn);
		String backUpPath = storageEn.concat(".back");
		if (loaded.isPresent()) {
			dictionary = loaded.get();
			try {
				Files.copy(Paths.get(storageEn), Paths.get(backUpPath), REPLACE_EXISTING);
			} catch (IOException e) {
				log("Unable to create backup - %s", e.getMessage());
			}
		} else {
			//read backup or create new
			dictionary = Dictionary.load("english", backUpPath).orElse(new Dictionary("english"));
		}
	}

	private static String collectionToString(Collection<String> strings, String sortBy) {
		StringBuilder res = new StringBuilder();
		if (!strings.isEmpty()) {
			String listed = strings.parallelStream().sorted()
					                .collect(partitioningBy(s -> !s.startsWith(sortBy))).values().stream()
					                .flatMap(List::stream)
					                .map(s -> String.format("  %s %s", s, dictionary.getPartsOfSpeech(s)))
					                .collect(joining("\n"));
			int size = strings.size();
			res.append(listed).append(String.format("%ntotal %d entr%s%n", size, size > 1 ? "ies" : "y"));
		} else {
			res.append("Nothing found\n");
		}
		return res.toString();

	}

	private static Collection<String> getSuggestions() {
		return Stream.of(expositors).parallel()
				       .sorted(Comparator.comparingInt(expositor -> expositor.priority))
				       .flatMap(expositor -> expositor.getRecentSuggestions().stream())
				       .collect(toSet());
	}

	@SuppressWarnings("unchecked")
	private static Collection<Vocabula> findVocabula(String query, boolean acceptSimilar) {
		return Stream.of(expositors)
				       .parallel()
				       .map(expositor -> new Object[] {expositor.priority, expositor.getVocabula(query, acceptSimilar)})
				       .sorted(Comparator.comparingInt(tuple -> (int) tuple[0]))
				       .flatMap(tuple -> ((Collection<Vocabula>) tuple[1]).stream())
				       .collect(LinkedHashSet::new, Set::add, Set::addAll);
	}

	private static void pronounce(Vocabula vocabula) {
		Iterator<String> sIt = vocabula.getPronunciationSources().iterator();
		if (sIt.hasNext()) pronouncingService.pronounce(sIt.next(), 500);
		else pronouncingService.pronounce(vocabula.headWord, 500);
	}

}