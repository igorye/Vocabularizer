package com.nicedev.vocabularizer.dictionary;

import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * Created by sugarik on 02.09.2016.
 */
public class DictionaryTest {
	
	@Test
	public void testLoad() throws Exception {

	}

	@Test
	public void testSave() throws Exception {

	}

	@Test
	public void testAddVocabula() throws Exception {

	}

	@Test
	public void testAddVocabula1() throws Exception {

	}

	@Test
	public void testRemoveVocabula() throws Exception {

	}

	@Test
	public void testAddVocabula2() throws Exception {

	}

	@Test
	public void testUpdateVocabula() throws Exception {

	}

	public static void main(String[] args) {
		Dictionary en = new Dictionary("english");
		try {
			String home = System.getProperties().getProperty("user.home");
			String saveTo = String.format("%s\\%s.dict", home, en.language);
			en.addVocabula("apple", "noun", "the round fruit of a tree of the rose family, " +
					                                "which typically has thin red or green skin and crisp flesh");
			en.addVocabula("clog", "noun", "something that blocks or clogs a pipe");
			en.addVocabula("clog", "noun", "a shoe or sandal that has a thick usually wooden sole");
			en.addVocabula("clog", "verb", "to slowly form a block in (something, such as a pipe " +
					                               "or street) so that things cannot move through quickly or easily");
			en.addVocabula("ASAP");
			System.out.print(en);
			Dictionary.save(en, saveTo );
			en.removeVocabula("ASAP", "noun");
			System.out.print(en);
			Dictionary[] inEn = new Dictionary[1];
			if (Dictionary.load(inEn, saveTo))
				System.out.print(inEn[0]);
			System.out.println(en.lookUpDefinition("apple", "verb"));
			String newWord = "graфe";
			if (! Language.hasForeignChar(newWord, en.language.name))
				en.addVocabula(newWord, "noun", newWord);
			System.out.print(en);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}