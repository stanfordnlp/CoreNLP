package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import org.junit.Assert;
import junit.framework.TestCase;

import java.util.List;
import java.util.Properties;

/**
 * @author Grace Muzny
 */
public class QuoteAnnotatorITest extends TestCase {

  private static StanfordCoreNLP pipeline;
  private static StanfordCoreNLP pipelineNoSingleQuotes;
  private static StanfordCoreNLP pipelineMaxFive;
  private static StanfordCoreNLP pipelineAsciiQuotes;
  private static StanfordCoreNLP pipelineAllowEmbeddedSame;
  private static StanfordCoreNLP pipelineUnclosedQuotes;

  /**
   * Initialize the annotators at the start of the unit test.
   * If they've already been initialized, do nothing.
   */
  @Override
  public void setUp() throws Exception {
    super.setUp();
    synchronized(QuoteAnnotatorITest.class) {
      if (pipeline == null) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, quote1");
        props.setProperty("customAnnotatorClass.quote1", "edu.stanford.nlp.pipeline.QuoteAnnotator");
        props.setProperty("quote1.attributeQuotes", "false");
        props.setProperty("quote1.singleQuotes", "true");
        pipeline = new StanfordCoreNLP(props);
      }
      if (pipelineNoSingleQuotes == null) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, quote2");
        props.setProperty("customAnnotatorClass.quote2", "edu.stanford.nlp.pipeline.QuoteAnnotator");
        props.setProperty("quote2.attributeQuotes", "false");
        pipelineNoSingleQuotes = new StanfordCoreNLP(props);
      }

      if (pipelineMaxFive == null) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, quote3");
        props.setProperty("customAnnotatorClass.quote3", "edu.stanford.nlp.pipeline.QuoteAnnotator");
        props.setProperty("quote3.maxLength", "5");
        props.setProperty("quote3.attributeQuotes", "false");
        pipelineMaxFive = new StanfordCoreNLP(props);
      }

      if (pipelineAsciiQuotes == null) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, quote4");
        props.setProperty("customAnnotatorClass.quote4", "edu.stanford.nlp.pipeline.QuoteAnnotator");
        props.setProperty("quote4.asciiQuotes", "true");
        props.setProperty("quote4.attributeQuotes", "false");
        pipelineAsciiQuotes = new StanfordCoreNLP(props);
      }
      if (pipelineAllowEmbeddedSame == null) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, quote5");
        props.setProperty("customAnnotatorClass.quote5", "edu.stanford.nlp.pipeline.QuoteAnnotator");
        props.setProperty("quote5.allowEmbeddedSame", "true");
        props.setProperty("quote5.attributeQuotes", "false");
        pipelineAllowEmbeddedSame = new StanfordCoreNLP(props);
      }
      if(pipelineUnclosedQuotes == null){
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, quote6");
        props.setProperty("customAnnotatorClass.quote6", "edu.stanford.nlp.pipeline.QuoteAnnotator");
        props.setProperty("quote6.extractUnclosedQuotes", "true");
        props.setProperty("quote6.attributeQuotes", "false");
        pipelineUnclosedQuotes = new StanfordCoreNLP(props);
      }
    }
  }

  public void testBasicEmbeddedSameUnicode() {
    String text = "“Hello,” he said, “how “are” you doing?”";
    List<CoreMap> quotes = runQuotes(text, 2, pipeline);
    assertEquals("“Hello,”", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("“how “are” you doing?”", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
    List<CoreMap> embedded = quotes.get(1).get(CoreAnnotations.QuotationsAnnotation.class);
    assertEquals(embedded.size(), 0);
  }

  public void testBasicAllowEmbeddedSameUnicode() {
    String text = "“Hello,” he said, “how “are” you doing?”";
    List<CoreMap> quotes = runQuotes(text, 2, pipelineAllowEmbeddedSame);
    assertEquals("“Hello,”", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("“how “are” you doing?”", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("“are”", "“how “are” you doing?”", quotes);
  }

  public void testBasicAsciiQuotes() {
    String text = "“Hello,“ he said, “how are you doing?”";
    List<CoreMap> quotes = runQuotes(text, 2, pipelineAsciiQuotes);
    assertEquals("“Hello,“", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("“how are you doing?”", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testMaxLength() {
    String text = "`Hel,' he said, ``how are \"you\" blar a \"farrrrrooom\"";
    List<CoreMap> quotes = runQuotes(text, 2, pipelineMaxFive);
    assertEquals("`Hel,'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("\"you\"", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testTis() {
    String text = "\"'Tis Impossible, “Mr. 'tis “Mr. Bennet” Bennet”, impossible, when 'tis I am not acquainted with him\n" +
        " myself; how can you be so teasing?\"";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("“Mr. Bennet”", "“Mr. 'tis “Mr. Bennet” Bennet”", quotes);
    assertEmbedded("“Mr. 'tis “Mr. Bennet” Bennet”", text, quotes);
  }

  public void testDashes() {
    String text = "\"Hello\"--said Mr. Cornwallaby";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("\"Hello\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    text = "“-Wish- you success!”—In what";
    quotes = runQuotes(text, 1);
    assertEquals("“-Wish- you success!”", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    text = "\"-Wish- you success!\"—In what";
    quotes = runQuotes(text, 1);
    assertEquals("\"-Wish- you success!\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testBasicInternalPunc() {
    String text = "\"Impossible, Mr. Bennet, impossible, when I am not acquainted with him\n" +
        " myself; how can you be so teasing?\"";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertInnerAnnotationValues(quotes.get(0), 0, 0, 0, 0, 24);
  }

  public void testBasicLatexQuotes() {
    String text = "`Hello,' he said, ``how are you doing?''";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("`Hello,'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("``how are you doing?''", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
    assertInnerAnnotationValues(quotes.get(0), 0, 0, 0, 0, 3);
    assertInnerAnnotationValues(quotes.get(1), 1, 0, 0, 7, 13);
  }

  public void testLatexQuotesWithDirectedApostrophes() {
    String text = "John`s he said, ``how are you doing?''";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("``how are you doing?''", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testEmbeddedLatexQuotes() {
    String text = "``Hello ``how are you doing?''''";
    List<CoreMap> quotes = runQuotes(text, 1, pipelineAllowEmbeddedSame);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("``how are you doing?''", text, quotes);
    assertInnerAnnotationValues(quotes.get(0), 0, 0, 0, 0, 9);
  }

  public void testEmbeddedLatexQuotesNoEmbedded() {
    String text = "``Hello ``how are you doing?''''";
    List<CoreMap> quotes = runQuotes(text, 1, pipeline);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    List<CoreMap> embedded = quotes.get(0).get(CoreAnnotations.QuotationsAnnotation.class);
    assertEquals(0, embedded.size());
  }

  public void testEmbeddedSingleLatexQuotes() {
    String text = "`Hello `how are you doing?''";
    List<CoreMap> quotes = runQuotes(text, 1, pipelineAllowEmbeddedSame);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("`how are you doing?'", text, quotes);
  }

  public void testEmbeddedLatexQuotesAllEndSamePlace() {
    String text = "``Hello ``how `are ``you doing?'''''''";
    List<CoreMap> quotes = runQuotes(text, 1, pipelineAllowEmbeddedSame);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("``how `are ``you doing?'''''", text, quotes);
    assertEmbedded("`are ``you doing?'''", "``how `are ``you doing?'''''", quotes);
    assertEmbedded("``you doing?''", "`are ``you doing?'''", quotes);
  }

  public void testEmbeddedLatexQuotesAllEndSamePlaceNoEmbedded() {
    String text = "``Hello ``how ``are ``you doing?''''''''";
    List<CoreMap> quotes = runQuotes(text, 1, pipeline);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    List<CoreMap> embedded = quotes.get(0).get(CoreAnnotations.QuotationsAnnotation.class);
    assertEquals(0, embedded.size());
  }

  public void testTripleEmbeddedLatexQuotes() {
    String text = "``Hel ``lo ``how'' are you'' doing?''";
    List<CoreMap> quotes = runQuotes(text, 1, pipelineAllowEmbeddedSame);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("``lo ``how'' are you''", text, quotes);
    assertEmbedded("``how''", "``lo ``how'' are you''", quotes);
  }

  public void testTripleEmbeddedLatexQuotesNoEmbedded() {
    String text = "``Hel ``lo ``how'' are you'' doing?''";
    // This case fails unless you also don't consider single quotes
    List<CoreMap> quotes = runQuotes(text, 1, pipelineNoSingleQuotes);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    List<CoreMap> embedded = quotes.get(0).get(CoreAnnotations.QuotationsAnnotation.class);
    assertEquals(0, embedded.size());
  }

  public void testTripleEmbeddedUnicodeQuotes() {
    String text = "“Hel «lo “how” are you» doing?”";
    List<CoreMap> quotes = runQuotes(text, 1, pipelineAllowEmbeddedSame);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("«lo “how” are you»", text, quotes);
    assertEmbedded("“how”", "«lo “how” are you»", quotes);
  }

  public void testBasicIgnoreSingleQuotes() {
    String text = "“Hello,” he 'said', “how are you doing?”";
    List<CoreMap> quotes = runQuotes(text, 2, pipelineAllowEmbeddedSame);
    assertEquals("“Hello,”", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("“how are you doing?”", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));

    text = "\"'Tis Impossible, “Mr. 'tis “Mr. Bennet” Bennet”, impossible, when 'tis I am not acquainted with him\n" +
        " myself; how can you be so teasing?\"";
    quotes = runQuotes(text, 1, pipelineAllowEmbeddedSame);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("“Mr. Bennet”", "“Mr. 'tis “Mr. Bennet” Bennet”", quotes);
    assertEmbedded("“Mr. 'tis “Mr. Bennet” Bennet”", text, quotes);
  }

  public void testBasicUnicodeQuotes() {
    String text = "“Hello,” he said, “how are you doing?”";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("“Hello,”", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("“how are you doing?”", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testUnicodeQuotesWithBadUnicodeQuotes() {
    String text = "“Hello,” he said, “how‚ are‘ you doing?”";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("“Hello,”", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("“how‚ are‘ you doing?”", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testUnicodeQuotesWithApostrophes() {
    String text = "“Hello,” he said, “where is the dog‘s ball today?”";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("“Hello,”", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("“where is the dog‘s ball today?”", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testBasicDoubleQuotes() {
    String text = "\"Hello,\" he said, \"how are you doing?\"";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("\"Hello,\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals(quotes.get(0).get(CoreAnnotations.TokensAnnotation.class).size(), 4);
    assertEquals("\"how are you doing?\"", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testUnclosedInitialQuotes() {
    String text = "Hello,   \" he said, 'how are you doing?'";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("'how are you doing?'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testUnclosedLastDoubleQuotes() {
    String text = "\"Hello,\" he said, \"how are you doing?";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("\"Hello,\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testDoubleEnclosedInSingle() {
    String text = "'\"Hello,\" he said, \"how are you doing?\"'";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("'\"Hello,\" he said, \"how are you doing?\"'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("\"Hello,\"", text, quotes);
    assertEmbedded("\"how are you doing?\"", text, quotes);
  }

  public void testSingleEnclosedInDouble() {
    String text = "\"'Hello,' he said, 'how are you doing?'\"";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals(text, quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEmbedded("'Hello,'", text, quotes);
    assertEmbedded("'how are you doing?'", text, quotes);
  }

  public void testEmbeddedQuotes() {
    String text = "\"'Enter,' said De Lacy; 'and I will\n" +
        "\n" +
        "try in what manner I can relieve your\n" +
        "\n" +
        "wants; but, unfortunately, my children\n" +
        "\n" +
        "are from home, and, as I am blind, I\n" +
        "\n" +
        "am afraid I shall find it difficult to procure\n" +
        "\n" +
        "food for you.'\"";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEmbedded("'Enter,'", text, quotes);
    String second = "'and I will\n" +
        "\n" +
        "try in what manner I can relieve your\n" +
        "\n" +
        "wants; but, unfortunately, my children\n" +
        "\n" +
        "are from home, and, as I am blind, I\n" +
        "\n" +
        "am afraid I shall find it difficult to procure\n" +
        "\n" +
        "food for you.'";
    assertEmbedded(second, text, quotes);
    assertInnerAnnotationValues(quotes.get(0), 0, 0, 0, 0, 55);
  }

  public void testEmbeddedQuotesTwo() {
    String text = "It was all very well to say 'Drink me,' but the wise little Alice was\n" +
        "not going to do THAT in a hurry. 'No, I'll \"look\" first,' she said, 'and\n" +
        "see whether it's marked \"poison\" or not';";
    List<CoreMap> quotes = runQuotes(text, 3);
    assertEmbedded("\"poison\"", "'and\n" +
        "see whether it's marked \"poison\" or not'", quotes);
    assertEmbedded("\"look\"", "'No, I'll \"look\" first,'", quotes);
    assertInnerAnnotationValues(quotes.get(0), 0, 0, 0, 7, 11);
    assertInnerAnnotationValues(quotes.get(1), 1, 1, 1, 27, 37);
  }


  public void testEmbeddedMixedComplicated() {
    String text = "It was all very 「well to say `Drink me,' but the wise little Alice was\n" +
        "not going to do THAT in a hurry. ‘No, I'll \"look\" first,’ she said, «and\n" +
        "see whether it's marked ``poison'' or \"not»";
    List<CoreMap> quotes = runQuotes(text, 3);
    assertEmbedded("``poison''", "«and\n" +
        "see whether it's marked ``poison'' or \"not»", quotes);
    assertEmbedded("\"look\"", "‘No, I'll \"look\" first,’", quotes);
  }

  public void testQuotesFollowEachother() {
    String text = "\"Where?\"\n" +
        "\n" +
        "\"I don't see 'im!\"\n" +
        "\n" +
        "\"Bigger, he's behind the trunk!\" the girl whimpered.";
    List<CoreMap> quotes = runQuotes(text, 3);
    assertEquals("\"Where?\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("\"I don't see 'im!\"", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("\"Bigger, he's behind the trunk!\"", quotes.get(2).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testBasicSingleQuotes() {
    String text = "'Hello,' he said, 'how are you doing?'";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("'Hello,'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("'how are you doing?'", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testUnclosedLastSingleQuotes() {
    String text = "'Hello,' he said, 'how are you doing?";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("'Hello,'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testMultiParagraphQuoteDouble() {
    String text = "Words blah bla \"Hello,\n\n \"I am the second paragraph.\n\n" +
        "\"I am the last.\" followed by more words";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("\"Hello,\n\n" +
        " \"I am the second paragraph.\n\n" +
        "\"I am the last.\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testMultiParagraphQuoteSingle() {
    String text = "Words blah bla 'Hello,\n\n 'I am the second paragraph.\n\n" +
        "'I am the second to last.\n\n'see there's more here.' followed by more words";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("'Hello,\n\n" +
        " 'I am the second paragraph.\n\n" +
        "'I am the second to last.\n\n" +
        "'see there's more here.'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertInnerAnnotationValues(quotes.get(0), 0, 0, 3, 3, 28);
  }

  public void testMultiLineQuoteDouble() {
    String text = "Words blah bla \"Hello,\nI am the second paragraph.\n" +
        "I am the last.\" followed by more words";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("\"Hello,\n" +
        "I am the second paragraph.\n" +
        "I am the last.\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testMultiLineQuoteSingle() {
    String text = "Words blah bla 'Hello,\nI am the second paragraph.\n" +
        "I am the last.' followed by more words";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("'Hello,\n" +
        "I am the second paragraph.\n" +
        "I am the last.'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testWordBeginningWithApostropheAtQuoteBeginningSingleQuotes() {
    String text = "''Tis nobler' Words blah bla 'I went to the house yesterday,' he said";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("''Tis nobler'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("'I went to the house yesterday,'", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

//  public void testWordsWithApostropheTerminalsInSingleQuote() {
//    String text = "'Jones' cow is cuter!'";
//    List<CoreMap> quotes = runQuotes(text, 1);
//    assertEquals("'Jones' cow is cuter!'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
//  }

  public void testWordsWithApostropheTerminalsInOneDoubleQuote() {
    String text = "\"Jones' cow is cuter!\"";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("\"Jones' cow is cuter!\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testWordsWithApostropheTerminalsInDoubleQuotes() {
    String text = "\"I said that Jones' cow was better,\" but then he " +
        "rebutted. I was shocked--\"My cow is better than any one of Jones' bovines!\"";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("\"I said that Jones' cow was better,\"",
        quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("\"My cow is better than any one of Jones' bovines!\"",
        quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testUnclosedLastDoubleQuotesUnclosedAnnotation() {
    String text = "\"Hello,\" he said, \"how are you doing?";
    List<CoreMap> quotes = runQuotes(text, 1);
    List<CoreMap> unclosedQuotes = runUnclosedQuotes(text, 1, pipelineUnclosedQuotes);
    assertEquals("\"Hello,\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("\"how are you doing?",
        unclosedQuotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  public List<CoreMap> runQuotes(String text, int numQuotes) {
    return runQuotes(text, numQuotes, pipeline);
  }

  public List<CoreMap> runQuotes(String text, int numQuotes, StanfordCoreNLP pipeline) {
    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);

    // now check what's up...
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);

    // look for embedded quotes and make sure they are already being reported
//    for(CoreMap s : quotes) {
//
//      String quote = s.get(CoreAnnotations.TextAnnotation.class); // what's wrong here?
//      System.out.print("text: ");
//      System.out.println(quote);
//    }

    Assert.assertNotNull(quotes);
    Assert.assertEquals(numQuotes, quotes.size());
    return quotes;
  }

  public static void assertInnerAnnotationValues(CoreMap quote, int quoteIndex,
                                                 int sentenceBegin, int sentenceEnd,
                                                 int tokenBegin, int tokenEnd) {
    assertEquals((int) quote.get(CoreAnnotations.QuotationIndexAnnotation.class), quoteIndex);
    assertEquals((int) quote.get(CoreAnnotations.SentenceBeginAnnotation.class), sentenceBegin);
    assertEquals((int) quote.get(CoreAnnotations.SentenceEndAnnotation.class), sentenceEnd);
    assertEquals((int) quote.get(CoreAnnotations.TokenBeginAnnotation.class), tokenBegin);
    assertEquals((int) quote.get(CoreAnnotations.TokenEndAnnotation.class), tokenEnd);
    List<CoreLabel> quoteTokens = quote.get(CoreAnnotations.TokensAnnotation.class);
    if (quoteTokens != null && quote.get(CoreAnnotations.QuotationsAnnotation.class) == null) {
      for (CoreLabel qt : quoteTokens) {
        assertEquals((int) qt.get(CoreAnnotations.QuotationIndexAnnotation.class), quoteIndex);
      }
    }
  }


  public static void assertEmbedded(String embedded, String bed, List<CoreMap> quotes) {
    // find bed
    boolean found = assertEmbeddedHelper(embedded, bed, quotes);
    assertTrue(found);
  }

  public static boolean assertEmbeddedHelper(String embedded, String bed, List<CoreMap> quotes) {
    // find bed
    for(CoreMap b : quotes) {
      if (b.get(CoreAnnotations.TextAnnotation.class).equals(bed)) {
        // get the embedded quotes
        List<CoreMap> eqs = b.get(CoreAnnotations.QuotationsAnnotation.class);
//        System.out.println("eqs: " + eqs);
        for (CoreMap eq : eqs) {
          if (eq.get(CoreAnnotations.TextAnnotation.class).equals(embedded)) {
            return true;
          }
        }
      } else {
        List<CoreMap> bEmbed = b.get(CoreAnnotations.QuotationsAnnotation.class);
        boolean recurse = assertEmbeddedHelper(embedded, bed, bEmbed);
        if (recurse) return true;
      }
    }
    return false;
  }

  public static List<CoreMap> runUnclosedQuotes(String text, int numQuotes, StanfordCoreNLP pipeline) {
    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);

    // now check what's up...
    List<CoreMap> quotes = doc.get(CoreAnnotations.UnclosedQuotationsAnnotation.class);

    Assert.assertNotNull(quotes);
    Assert.assertEquals(numQuotes, quotes.size());
    return quotes;
  }

}
