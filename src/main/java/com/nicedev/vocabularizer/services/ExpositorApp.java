package com.nicedev.vocabularizer.services;


import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.nicedev.vocabularizer.dictionary.Language;
import com.nicedev.vocabularizer.dictionary.Vocabula;
import com.nicedev.vocabularizer.services.sound.PronouncingService;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class ExpositorApp {

	static private String home = System.getProperties().getProperty("user.home");
	private static String storageEn = String.format("%s\\%s.dict", home, "english");
	static private PronouncingService pronouncingService;
	static private  Expositor[] expositors;
	static private Dictionary en;

//	enum Command {DELETE, LIST, EXPLAIN, FIND}

	public static void main(String[] args) {
		en = Dictionary.load("english", storageEn);
		if (en == null) {
			storageEn = storageEn.concat(".back");
			en = Dictionary.load("english", storageEn);
			if (en == null)
				en = new Dictionary("english");
			storageEn = storageEn.replace(".back", "");
		}
		pronouncingService = new PronouncingService(5, false);
		expositors = new Expositor[]{new Expositor(en, false), new Expositor(en, true)};
		System.out.println(en);
		Scanner input;
		input = (args.length > 0)  ? new Scanner(args[0]) :  new Scanner(System.in);
		String query;
		int updateCount = 0;
		String lastQuerry = "";
		while (input.hasNextLine()) {
			query = input.nextLine().trim();
			int defCount = en.getDefinitionCount();
			if(query.trim().length() == 0)
				continue;
			if(query.equals("<"))
				query = lastQuerry;
			lastQuerry = query;
			if (query.startsWith("-")) {
				query = query.replaceFirst("-", "");
				String[] tokens = query.split("\" ");
				if (tokens.length == 0)
					tokens = query.split("\\s");
				if (tokens.length == 2)
					en.removeVocabula(tokens[0].replace("\"", "").trim(), tokens[1]);
				else
					en.removeVocabula(tokens[0].replace("\"", "").trim());
				if (defCount != en.getDefinitionCount())
					updateCount++;
				System.out.println(en);
				continue;
			}
			if (query.startsWith("::")) {
				query = query.substring(2,query.length()).trim();
				System.out.println(en.explainVocabula(query));
				Vocabula queried = en.getVocabula(query);
				if(queried != null) pronunciate(queried);
				continue;
			} else if (query.startsWith(":")) {
				query = query.substring(1, query.length()).trim();
				System.out.println(vocabulaCollectionToString(en.listVocabula(query)));
				continue;
			}
			try {
				String[] tokens = query.split("\\s{2,}|\t|\\[");
				Predicate<String> isEmpty = String::isEmpty;
				query = stream(tokens).filter(isEmpty.negate()).map(String::trim).findFirst().orElse("");
				Vocabula vocabula;
				Collection<Vocabula> vocabulas;
				if ((vocabula = en.getVocabula(query)) == null) {
					vocabulas = getVocabula(query);
					if (!vocabulas.isEmpty()) {
						en.addVocabulas(vocabulas);
						System.out.printf("found:%n%s%n",
								vocabulaCollectionToString(vocabulas.stream()
										                           .map(voc -> voc.headWord)
								                                   .collect(toList())));
					} else
						System.out.printf("No definition for \"%s\"%nMaybe %s?%n", query, getSuggestions());
				} else {
					System.out.printf("found: %s%n", vocabula);
					pronunciate(vocabula);
				}

				if(defCount != en.getDefinitionCount())
					System.out.println(en);
				if(++updateCount % 5 == 0)
					Dictionary.save(en, storageEn);

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				Dictionary.save(en, storageEn);
			}
		}
		Dictionary.save(en, storageEn);
		pronouncingService.release();
	}

	private static String vocabulaCollectionToString(Collection<String> strings) {
		StringBuilder res = new StringBuilder();
		if(!strings.isEmpty()) {
			strings.forEach(s -> res.append(String.format("  %s %s%n", s, en.getPartsOfSpeech(s))));
			int size = strings.size();
			res.append(String.format("total %d entr%s%n", size, size > 1 ? "ies" : "y"));
		} else
			res.append("Nothing found\n");
		return res.toString();

	}

	private static Collection<String> getSuggestions() {
		Set<String> suggestions = new HashSet<>();
		for(Expositor expositor: expositors)
			suggestions.addAll(expositor.getRecentSuggestions());
 		return suggestions;
	}

	private static Collection<Vocabula> getVocabula(String query) {
		return asList(expositors).parallelStream()
				       .map(expositor -> expositor.getVocabula(query, true))
				       .collect(HashSet::new, Collection::addAll, Collection::addAll);
	}

	private static void pronunciate(Vocabula vocabula) {
		Iterator<String> sIt = vocabula.getPronunciationSources().iterator();
		if (sIt.hasNext()) pronouncingService.pronounce(sIt.next(), 500);
		else pronouncingService.pronounce(vocabula.headWord, 500);
	}
	
}