package com.nicedev.vocabularizer.services;

import com.nicedev.vocabularizer.dictionary.Dictionary;
import com.nicedev.vocabularizer.dictionary.Language;
import org.junit.Test;
import org.omg.CORBA.portable.Streamable;

import static org.junit.Assert.*;

public class InterpreterTest {
	Interpreter inter;

	String[] entries = {"a stuffed or inflated bag, typically cylindrical or pear-shaped, suspended so it can be punched " +
			                    "for exercise or training, especially by boxers.",
			"наполненный или надутый мешок, обычно цилиндрической или грушевидной формы, подвешенный так, что его можно " +
					"бить для тренировки или упражнений, особенно боксёрами",
			"a yellowish- or brownish-green edible fruit that is typically narrow at the stalk and " +
					"wider toward the base, with sweet, slightly gritty flesh",
			"жёлто- или коричнево-зеленый съедобный фрукт обычно узкий у ножки и шире к основанию, со сладкой, " +
					"слегка зернистой мякотью"};

	String home = System.getProperties().getProperty("user.home");

	@Test
	public void testAddTranslation() throws Exception {
		inter.addTranslation("apple", "noun", "the round fruit of a tree of the rose family, " +
				                                "which typically has thin red or green skin and crisp flesh",
				"яблоко", "существительное", "округлый плод дерева семейства розоцветных, обычно " +
						                             "с кожурой красного или зеленого цвета и хрустящей мякотью");
		inter.addTranslation(entries[0], entries[1]);
		inter.addTranslation(entries[2], entries[3]);
	}

	@Test
	public void testTranslate() throws Exception {
		String fmt = "%s(%s) - %s(%s)%n";
		String flng = inter.foreignLang.langName,
				nlng = inter.nativeLang.langName;

		System.out.printf(fmt, "яблоко", flng, inter.translate("яблоко", "существительное", Interpreter.FOREIGN_TO_NATIVE), nlng);
		System.out.printf(fmt, "груша", flng, inter.translate("груша", Interpreter.FOREIGN_TO_NATIVE), nlng);
		System.out.printf(fmt, "apple", nlng, inter.translate("apple", "noun"), flng);
		System.out.printf(fmt, "яблоко", flng, inter.translate("яблоко"), nlng);
		System.out.printf(fmt, "apple", nlng, inter.translate("apple"), flng);
		System.out.printf(fmt, "груша", flng, inter.translate("груша"), nlng);
		System.out.printf(fmt, "pear", nlng, inter.translate("pear"), flng);
		System.out.printf(fmt, "punchbag", nlng, inter.translate("punchbag"), flng);
	}

	public static void main(String[] args) throws Throwable {
		InterpreterTest iTest = new InterpreterTest();
		Dictionary dNative = Dictionary.load("english", String.format("%s\\%s.dict", iTest.home, "english"));
		Dictionary dForeign = Dictionary.load("russian", String.format("%s\\%s.dict", iTest.home, "russian"));
		System.out.println(dNative);
		System.out.println(dForeign);
		System.out.println(dNative.lookupDefinition("apple"));
		System.out.println(dForeign.lookupDefinition("яблоко"));
		iTest.inter = new Interpreter(dNative, dForeign);
		iTest.testAddTranslation();
		iTest.testTranslate();
	}
}