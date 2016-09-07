package com.nicedev.vocabularizer.dictionary;

import org.junit.Test;

import java.io.IOException;

public class DictionaryTest {

	Dictionary en = new Dictionary("english");
	Dictionary ru = new Dictionary("russian");
	String home = System.getProperties().getProperty("user.home");
	String storageEn = String.format("%s\\%s.dict", home, en.language.langName);
	String storageRu = String.format("%s\\%s.dict", home, ru.language.langName);

	@Test
	public void testLoad() throws Exception {
		Dictionary inEn;
		try {
			inEn = Dictionary.load("english", storageEn);
			System.out.println(inEn);
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (Throwable thr) {
			System.err.println(thr.getMessage());
		}
	}

	@Test
	public void testSave() throws Exception {
		Dictionary.save(en, storageEn);
		Dictionary.save(ru, storageRu);
	}

	String[] entries = {"a stuffed or inflated bag, typically cylindrical or pear-shaped, suspended so it can be punched " +
			                    "for exercise or training, especially by boxers.",
			"наполненный или надутый мешок, обычно цилиндрической или грушевидной формы, подвешенный так, что его можно " +
					"бить для тренировки или упражнений, особенно боксёрами",
							   "a yellowish- or brownish-green edible fruit that is typically narrow at the stalk and " +
									   "wider toward the base, with sweet, slightly gritty flesh",
			"жёлто- или коричнево-зеленый съедобный фрукт обычно узкий у ножки и шире к основанию, со сладкой, " +
					"слегка зернистой мякотью"};

	@Test
	public void testAddVocabula() throws Exception {
		en.addVocabula("apple", "noun", "the round fruit of a tree of the rose family, " +
				                                "which typically has thin red or green skin and crisp flesh");
		en.addVocabula("clog", "noun", "something that blocks or clogs a pipe");
		en.addVocabula("punchbag", "noun", entries[0]);
		en.addVocabula("clog", "noun", "a shoe or sandal that has a thick usually wooden sole");
		en.addVocabula("clog", "verb", "to slowly form a block in (something, such as a pipe " +
				                               "or street) so that things cannot move through quickly or easily");
		en.addVocabula("ASAP");
		String newWord = "grape";
		en.addVocabula(newWord, "noun", newWord);
		ru.addVocabula("яблоко", "существительное", "округлый плод дерева семейства розоцветных, обычно " +
				                                            "с кожурой красного или зеленого цвета и хрустящей мякотью");
		ru.addVocabula("груша", "существительное", entries[1]);
		en.addVocabula("pear", "noun", entries[2]);
		ru.addVocabula("груша", "существительное", entries[3]);

	}

	@Test
	public void testAddVocabula1() throws Exception {

	}

	@Test
	public void testRemoveVocabula() throws Exception {
		en.removeVocabula("ASAP", "noun");
	}

	@Test
	public void testAddVocabula2() throws Exception {

	}

	@Test
	public void testUpdateVocabula() throws Exception {

	}

	public static void main(String[] args) {
		DictionaryTest dt = new DictionaryTest();
		try {
			System.out.println(dt.en);
			dt.testAddVocabula();
			dt.testSave();
			dt.testLoad();
			System.out.println(dt.en.lookupDefinition("apple", "verb"));
			System.out.println(dt.en.lookupDefinition("apple"));
			System.out.println(dt.en.lookupDefinition("punchbag"));
			System.out.println(dt.en);
			System.out.println(dt.ru);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}