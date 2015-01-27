package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.List;
import java.util.Properties;

/**
 * @author Grace Muzny
 */
public class QuotationAnnotatorTest extends TestCase {

  private static StanfordCoreNLP pipeline;

  /**
   * Initialize the annotators at the start of the unit test.
   * If they've already been initialized, do nothing.
   */
  @Override
  public void setUp() {
    synchronized(QuotationAnnotatorTest.class) {
      if (pipeline == null) {
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,quote");
        pipeline = new StanfordCoreNLP(props);
      }
    }
  }

  public void testBasicDoubleQuotes() {
    String text = "\"Hello,\" he said, \"how are you doing?\"";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("\"Hello,\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("\"how are you doing?\"", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testUnclosedLastDoubleQuotes() {
    String text = "\"Hello,\" he said, \"how are you doing?";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("\"Hello,\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
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
    List<CoreMap> quotes = runQuotes(text, 3);
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
  }

  public void testQuotesFollowEachother() {
    String text = "\"Where?\"\n" +
        "\n" +
        "\"I don't see 'im!\"\n" +
        "\n" +
        "\"Bigger, he's behind the trunk!\" the girl whimpered.";
    List<CoreMap> quotes = runQuotes(text, 3);

  }


  //TODO: think about what the behavior should be here
//  public void testUnclosedFirstDoubleQuotes() {
//    String text = "\"Hello, he said, \"how are you doing?\"";
//    List<CoreMap> quotes = runQuotes(text, 1);
//    assertEquals("\"how are you doing?\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
//  }

  public void testBasicSingleQuotes() {
    String text = "'Hello,' he said, 'how are you doing?'";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("'Hello,'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("'how are you doing?'", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public void testUnclosedLastSingleQuotes() {
    String text = "'Hello,' he said, 'how are you doing?";
    List<CoreMap> quotes = runQuotes(text, 2);
    assertEquals("'Hello,'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
  }

  //TODO: think about what the behavior should be here
//  public void testUnclosedFirstSingleQuotes() {
//    String text = "'Hello, he said, 'how are you doing?'";
//    List<CoreMap> quotes = runQuotes(text, 1);
//    assertEquals("'how are you doing?'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
//  }

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
        "'I am the last.' followed by more words";
    List<CoreMap> quotes = runQuotes(text, 1);
    assertEquals("'Hello,\n\n" +
        " 'I am the second paragraph.\n\n" +
        "'I am the last.'", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
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
    assertEquals("\"I said that Jones' cow was better,\"", quotes.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("\"My cow is better than any one of Jones' bovines!\"", quotes.get(1).get(CoreAnnotations.TextAnnotation.class));
  }

  public static List<CoreMap> runQuotes(String text, int numQuotes) {
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

  public static void assertEmbedded(String embedded, String bed, List<CoreMap> quotes) {
    // find bed
    boolean found = false;
    for(CoreMap b : quotes) {
      if (b.get(CoreAnnotations.TextAnnotation.class).equals(bed)) {
        // get the embedded quotes
        List<CoreMap> eqs = b.get(CoreAnnotations.QuotationsAnnotation.class);
        for (CoreMap eq : eqs) {
          if (eq.get(CoreAnnotations.TextAnnotation.class).equals(embedded)) {
            found = true;
          }
        }
      }
    }
    assertTrue(found);
  }
}
