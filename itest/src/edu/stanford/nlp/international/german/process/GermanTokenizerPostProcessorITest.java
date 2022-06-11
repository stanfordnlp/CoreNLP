package edu.stanford.nlp.international.german.process;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;

import junit.framework.TestCase;

import java.util.*;
import java.util.stream.*;

/**
 * Testing for German tokenization post-processing.  This should fix:
 * - ordinal issue: 21. Dezember
 * - not splitting number ranges on hyphen: 1989-1990
 */


public class GermanTokenizerPostProcessorITest extends TestCase {

  public StanfordCoreNLP pipeline;

  @Override
  public void setUp() {
    Properties props = StringUtils.argsToProperties("-props", "german");
    props.setProperty("annotators", "tokenize,ssplit");
    pipeline = new StanfordCoreNLP(props);
  }

  public void testExample(String inputText, List<String> goldTokens) {
    CoreDocument doc = new CoreDocument(pipeline.process(inputText));
    assertEquals(goldTokens, doc.tokens().stream().map(tok -> tok.word()).collect(Collectors.toList()));
  }

  public void testPostProcessor() {
    // test ordinal
    String ordinalExample = "Der Vertrag läuft offiziell bis zum 31. Dezember 1992.";
    List<String> ordinalExampleGoldTokens = Arrays.asList("Der", "Vertrag", "läuft", "offiziell", "bis",
        "zum", "31.", "Dezember", "1992", ".");
    testExample(ordinalExample, ordinalExampleGoldTokens);
    // test number ranges
    String numberRangeExample = "Das Artepitheton bolusii ehrt den südafrikanischen Bankier Harry Bolus (1834-1911).";
    List<String> numberRangeExampleGoldTokens = Arrays.asList("Das", "Artepitheton", "bolusii", "ehrt", "den",
        "südafrikanischen", "Bankier", "Harry", "Bolus", "(", "1834-1911", ")", ".");
    testExample(numberRangeExample, numberRangeExampleGoldTokens);
    // test abbreviations
    String abbreviationExample = "Zusätzlich hatte ich eine Terasse bzw. Balkon.";
    List<String> abbreviationExampleGoldTokens = Arrays.asList("Zusätzlich", "hatte", "ich", "eine", "Terasse",
        "bzw.", "Balkon", ".");
    testExample(abbreviationExample,abbreviationExampleGoldTokens);
  }

  /**
   * You probably can't tell in your editor, but the input has 4 characters for
   * <pre>für</pre>
   * and the output has 3
   */
  public void testUmlauts() {
    String fur = "für";
    assertEquals(4, fur.length());

    String furry = "für";
    assertEquals(3, furry.length());

    String umlautExample = "Welcher der Befunde ist " + fur + " eine Gehirnerkrankung typisch?";
    List<String> umlautGoldTokens = Arrays.asList("Welcher", "der", "Befunde", "ist", furry, "eine", "Gehirnerkrankung", "typisch", "?");
    testExample(umlautExample, umlautGoldTokens);
  }

  /**
   * Test that an umlaut at the start of a word doesn't crash
   */
  public void testUmlautSpaces() {
    String antik = "Antik ̈orper";
    assertEquals(12, antik.length());

    List<String> goldTokens = Arrays.asList(antik.substring(0, 5), antik.substring(6, 12));
    testExample(antik, goldTokens);
  }
}
