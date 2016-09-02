package com.nicedev.vocabularizer.dictionary;

import javafx.geometry.Pos;

import java.io.*;
import java.util.*;

/**
 * Created by sugarik on 28.08.2016.
 */



public class Dictionary implements Serializable{

	final public Language language;
	private Map<Vocabula, Map<PartOfSpeech, Set<Meaning>>> definitions;
	private Set<PartOfSpeech> partsOS;


	public Dictionary(String lang) {
		this.language = new Language(lang, "", Language.ENGLISH_ALPHABET);
		definitions = new TreeMap<>();
		partsOS = new LinkedHashSet<>(10);
		Properties langProps = new Properties();
		String home = System.getProperties().getProperty("user.home");
		try (InputStream in = new FileInputStream(new File (home, String.format("%s.properties", this.language.name)))) {
			langProps.load(in);
			int nPoS = 0;
			String PoSName;
			while ((PoSName = langProps.getProperty(String.format("name%d", ++nPoS))) != null) {
				partsOS.add(new PartOfSpeech(language.name, PoSName));
			}
		} catch (IOException e) {
			System.err.format("Unable to read language configuration. %s.properties file is corrupt or missing at" +
					                  " %s. %n", this.language.name, home);
		}
	}

	public static boolean load(Dictionary[] dict, String path) throws IOException {
		try (ObjectInput in = new ObjectInputStream(new FileInputStream(path))){
			return (dict[0] = (Dictionary) in.readObject()) != null;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static boolean save(Dictionary dict, String path) throws IOException {
		try (ObjectOutput out = new ObjectOutputStream(new FileOutputStream(path))){
			out.writeObject(dict);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	boolean addVocabula(String newEntry, PartOfSpeech pos, Meaning explanatory) {
		return true;
	}

	boolean addVocabula(String newEntry, String partOS, String explanatory) {
		PartOfSpeech PoS = new PartOfSpeech(language.name, partOS);
		if (! partsOS.contains(new PartOfSpeech(language.name, partOS)))
			PoS = new PartOfSpeech(language.name, PartOfSpeech.UNDEFINED);

		Vocabula newV = new Vocabula(newEntry, this.language.name);
		Map<PartOfSpeech, Set<Meaning>> mMeanings =
				definitions.getOrDefault(newV, new TreeMap<>());
		Set<Meaning> meanings = mMeanings.getOrDefault(PoS, new TreeSet<>());
		mMeanings.putIfAbsent(PoS, meanings);
		meanings.add(new Meaning(this.language.name, explanatory));

		return definitions.putIfAbsent(newV, mMeanings)!= null;
	}

	boolean addVocabula(String newEntry, PartOfSpeech PoS) {
		return addVocabula(newEntry, PoS.name, newEntry);
	}

	public boolean addVocabula(String newEntry) {
		return addVocabula(newEntry, new PartOfSpeech(language.name, PartOfSpeech.UNDEFINED));
	}

	boolean removeVocabula(String charSeq, String PoS) {
		Vocabula voc = new Vocabula(charSeq, language.name);
		if (PoS.equals(PartOfSpeech.ANY))
			definitions.remove(voc);
		else {
			definitions.get(voc).remove(new PartOfSpeech(PoS, language.name));
		}
		return true;
	}

	boolean updateVocabula() {
		return true;
	}

	public String toString(){
		StringBuilder res = new StringBuilder();
		res.append(String.format("Language = %s%n", language));
		res.append(String.format("Parts of speech: %n%s%n", partsOS));
		res.append("Tesaurus:\n");
		for(Vocabula voc: definitions.keySet()) {
			res.append(lookUpDefinition(voc.charSeq, PartOfSpeech.ANY));
			res.append("\n");
		}
		return res.toString();
	}

	public String lookUpDefinition(String entry, String poSpeech) {
		StringBuilder res = new StringBuilder();
		res.append(String.format(" %s%n", entry));
		res.append(lookUpDefinition(new Vocabula(entry, language.name), new PartOfSpeech(language.name, poSpeech)));
		return res.toString();
	}

	private String lookUpDefinition(Vocabula voc, PartOfSpeech PoS) {
		StringBuilder res = new StringBuilder();
		Map<PartOfSpeech, Set<Meaning>> meanings = definitions.get(voc);
		if (PoS.name.equals(PartOfSpeech.ANY)) {
			for(PartOfSpeech pos: meanings.keySet())
				res.append(lookUpDefinition(voc, pos));
		} else {
			for(Meaning mean: meanings.getOrDefault(PoS, Collections.<Meaning>emptySortedSet()))
				res.append(String.format("\t\t- \"%s\"%n",mean));
			if (res.length() != 0) res.insert(0, String.format("\t: %s%n",PoS));
			else res.append(String.format("\t: (%s) has no definition%n", PoS.name));
		}
		return res.toString();
	}

	public Map<PartOfSpeech, Set<Meaning>> getDefinitions(String entry) {
		Vocabula voc = new Vocabula(entry, language.name);
		return Collections.unmodifiableMap(definitions.get(voc));
	}

	public Set<Meaning> getMeanings(String entry, String PoS) {
		Vocabula voc = new Vocabula(entry, language.name);
		if (PoS.equals(PartOfSpeech.ANY)) {
			PoS = definitions.get(voc).keySet().iterator().next().name;
		}
		PartOfSpeech partOS = new PartOfSpeech(language.name, PoS);
		return Collections.unmodifiableSet(definitions.get(voc).get(partOS));
	}
}
