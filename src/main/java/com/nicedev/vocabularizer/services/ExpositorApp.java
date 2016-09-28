package com.nicedev.vocabularizer.services;


import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.nicedev.vocabularizer.dictionary.Language;
import com.nicedev.vocabularizer.dictionary.Vocabula;

import java.util.Scanner;

public class ExpositorApp {

	static String home = System.getProperties().getProperty("user.home");
	static String storageEn = String.format("%s\\%s.dict", home, "english");

//	enum Command {DELETE, LIST, EXPLAIN, FIND}

	public static void main(String[] args) {
		Dictionary en = Dictionary.load("english", storageEn);
		if (en == null) {
			storageEn = storageEn.concat(".back");
			en = Dictionary.load("english", storageEn);
			if (en == null)
				en = new Dictionary("english");
			storageEn = storageEn.replace(".back", "");
		}
		Expositor exp = new Expositor(en, false);
		System.out.println(en);
		Scanner input;
		input = (args.length > 0)  ? new Scanner(args[0]) :  new Scanner(System.in);
		String query;
		int updateCount = 0;
		String lastQuerry = "";
		while (input.hasNext()) {
			query = input.nextLine();
			int defCount = en.getDefinitionCount();
			if(query.trim().length() == 0)
				continue;
			if(query.trim().equalsIgnoreCase("q")) {
				if (updateCount > 0)
					Dictionary.save(en, storageEn);
				break;
			}
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
				continue;
			} else if (query.startsWith(":")) {
				query = query.substring(1, query.length()).trim();
				System.out.println(en.listVocabula(query));
				continue;
			}
			try {
				String[] tokens = query.split("\\s{2,}");
				query = tokens[0];
				if (! Language.charsMatchLanguage(query.replace("\"",""), en.language)) {
					System.out.printf("Error: query has an invalid char - \"%s\"%n", query);
					continue;
				}
				Vocabula vocabula;
				if ((vocabula = en.getVocabula(query)) == null) {
					vocabula = exp.getVocabula(query);
					if (vocabula != null) {
						en.addVocabula(vocabula);
						System.out.printf("found: %s%n", vocabula.charSeq);
					}
				} else System.out.printf("found: %s%n", vocabula);

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
		//calling finalize is bad but it's a temporary approach to kill speller when we done
		exp.finalize();
	}
	
}