package edu.stanford.nlp.process;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/**@author Jan von der Assen **/
public class StemmerTest {
	private Stemmer sut;
	private String happyTestCase;
	private String emptyTestCase;
	private String noChangeTestCase;
	
	private String wordEnd;

	/* Provide shallow testing of public methods */
	@Before
	public void initialize() {
        sut = new Stemmer();
        happyTestCase = "testing";
        emptyTestCase = "";
        noChangeTestCase = "make";
	}
	@Test
	public void testOptimalStemming() {
		String expectedResult = "test";
		String actualResult = sut.stem(happyTestCase);
		assertEquals(expectedResult, actualResult);
	}
	
	@Test
	public void testValidWord() {
		String invalidWord = "";
		String stemmedWord = sut.stem(invalidWord);
		assertEquals(invalidWord, stemmedWord);
	}
	@Test
	public void testShortWord() {
		String shortWord = "going";
		String stemmedWord = sut.stem(shortWord);
		String expectedStem = "go";
		assertEquals(expectedStem, stemmedWord);
	}
	@Test
	public void testLongWord() {
		String longWord = "Pneumonoultramicroscopicsilicovolcanoconiosis";
		String stemmedWord = sut.stem(longWord);
		String expectedStem = "Pneumonoultramicroscopicsilicovolcanoconiosi";
		assertEquals(expectedStem, stemmedWord);
	}

	@Test
	public void testStemmingWithEmptyWord() {
		String expectedResult = "";
		String actualResult = sut.stem(emptyTestCase);
		assertEquals(expectedResult, actualResult);
	}
	@Test
	public void testStemmingWithStem() {
		String expectedResult = "make";
		String actualResult = sut.stem(noChangeTestCase);
		assertEquals(expectedResult, actualResult);
	}

	/* Tests various branches of word endings using verbs*/

	@Test
	public void testStemmingWithIngEnding() {
        wordEnd = "ing";
        String wordToTest = WordMocker.prepareVerb(wordEnd);
        String expectedResult = WordMocker.getVerbStem();
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}
	
	/* Tests various branches of plural words */

	@Test
	public void testStemmingWithIngPluralWord() {
        wordEnd = "s";
        String wordToTest = WordMocker.prepareNoun(wordEnd);
        String expectedResult = WordMocker.getNounStem();
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testStemmingWithIesPluralWord() {
        String wordToTest = "ponies";
        String expectedResult = "poni";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testStemmingWithEdPluralWord() {
        String wordToTest = "disabled";
        String expectedResult = "disabl";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testStemmingWithEssesPluralWord() {
        wordEnd = "resses";
        String wordToTest = WordMocker.prepareNoun(wordEnd);
        String expectedResult = "mattress";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}
	
	/* Adjectives tested */
	@Test
	public void testStemmingWithIveAdjective() {
        String wordToTest = "tentative";
        String expectedResult = "tent";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}
	@Test
	public void testStemmingWithAteAdjective() {
        String wordToTest = "illiterate";
        String expectedResult = "illiter";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testStemmingWithAbleAdjective() {
        String wordToTest = "doable";
        String expectedResult = "doabl";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testStemmingWithErAdjective() {
        String wordToTest = "bigger";
        String expectedResult = "bigger";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}

	@Test
	public void testStemmingWithEstAdjective() {
        String wordToTest = "biggest";
        String expectedResult = "biggest";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}
	@Test
	public void testStemmingWithIbleAdjective() {
        String wordToTest = "gullible";
        String expectedResult = "gullibl";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}
	

	/* Adverbs tested */
	@Test
	public void testStemmingWithLyAdverb() {
        String wordToTest = "anxiously";
        String expectedResult = "anxious";
		String actualResult = sut.stem(wordToTest);
		assertEquals(expectedResult, actualResult);
	}

	private static class WordMocker {
		static String VERBSTEM = "go";
		static String NOUNSTEM = "matt";

		public static String prepareVerb(String ending) {
			return VERBSTEM + ending;
		}
		public static String getVerbStem() {
            return VERBSTEM;
		}
		public static String prepareNoun(String ending) {
			return NOUNSTEM + ending;
		}
		public static String getNounStem() {
            return NOUNSTEM;
		}
	}

}
