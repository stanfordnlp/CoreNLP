package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.util.CoreMap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import edu.stanford.nlp.util.PropertiesUtils;


/** Tests for converting token sequences to sentences. Also effectively includes some CleanXML tests.
 *
 *  @author Adam Vogel
 *  @author Christopher Manning
 */
public class WordsToSentencesAnnotatorTest {

  @Test
  public void testAnnotator() {
    String text = "I saw Dr. Spock yesterday, he was speaking with Mr. McCoy.  They were walking down Mullholand Dr. talking about www.google.com.  Dr. Spock returns!";
    runSentence(text, 3);

    // This would fail for "Yahoo! Research", since we don't yet know to chunk "Yahoo!"
    text = "I visited Google Research.  Dr. Spock, Ph.D., was working there and said it's an awful place!  What a waste of Ms. Pacman's last remaining life. Indeed";
    runSentence(text, 4);
  }

  private static void runSentence(String text, int num_sentences) {
    Annotation doc = new Annotation(text);
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize,ssplit",
             "tokenize.language", "en"
    );
    //Annotator annotator = new TokenizerAnnotator("en");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(doc);

    // now check what's up...
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    assertNotNull(sentences);
    assertEquals(num_sentences, sentences.size());
    /*
    for(CoreMap s : sentences) {
      String position = s.get(SentencePositionAnnotation.class); // what's wrong here?
      System.out.print("position: ");
      System.out.println(position);
      //throw new RuntimeException(position);
    }
    */
  }

  @Test
  public void testSentenceSplitting() {
    String text = "Date :\n01/02/2012\nContent :\nSome words are here .\n";
    // System.out.println(text);
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "ssplit.eolonly", "true",
            "tokenize.whitespace", "true"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    // System.out.println("* Num of sentences in text = "+sentences.size());
    // System.out.println("Sentences is " + sentences);
    assertEquals(4, sentences.size());
  }

  @Test
  public void testTokenizeNLsDoesntChangeSsplitResults() {
    String text = "This is one sentence\n\nThis is not another with default ssplit settings.";
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "tokenize.options", "tokenizeNLs"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(1, sentences.size());

    // make sure that there are the correct # of tokens
    // (does NOT contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(15, tokens.size());
  }

  @Test
  public void testDefaultNewlineIsSentenceBreakSettings() {
    String text = "This is one sentence\n\nThis is not another with default ssplit settings.";
    Properties props = PropertiesUtils.asProperties("annotators", "tokenize, ssplit");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(1, sentences.size());

    // make sure that there are the correct # of tokens
    // (does NOT contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(13, tokens.size());
  }

  @Test
  public void testTwoNewlineIsSentenceBreakSettings() {
    String text = "This is \none sentence\n\nThis is not another.";
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
             "ssplit.newlineIsSentenceBreak", "two"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(2, sentences.size());

    // make sure that there are the correct # of tokens (does contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(12, tokens.size());
  }

  @Test
  public void testTwoNewlineIsSentenceBreakTokenizeNLs() {
    String text = "This is \none sentence\n\nThis is not another.";
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "tokenize.language", "english",
            "tokenize.options", "tokenizeNLs,invertible,ptb3Escaping=true",
            "ssplit.newlineIsSentenceBreak", "two"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(2, sentences.size());

    // make sure that there are the correct # of tokens (does contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(12, tokens.size());

    List<CoreLabel> sentenceTwoTokens = sentences.get(1).get(CoreAnnotations.TokensAnnotation.class);
    String sentenceTwo = SentenceUtils.listToString(sentenceTwoTokens);
    assertEquals("Bad tokens in sentence", "This is not another .", sentenceTwo);
  }

  @Test
  public void testAlwaysNewlineIsSentenceBreakSettings() {
    String text = "This is \none sentence\n\nThis is not another.";
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "ssplit.newlineIsSentenceBreak", "always"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(3, sentences.size());

    // make sure that there are the correct # of tokens (does contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(12, tokens.size());
  }

  private static final String[] dateLineTexts =
          { "<P>\n" +
                  "GAZA, Dec. 1 (Xinhua) -- Hamas will respect any Palestinian referendum on a\n" +
                  "peaceful settlement with Israel even if the agreement was against its agenda,\n" +
                  "deposed Prime Minister of the Hamas government Ismail Haneya said Wednesday.\n" +
                  "</P>\n",
                  "\nLOS ANGELES, Dec. 31 (Xinhua) -- Body",
                  "\nCARBONDALE, United States, Dec. 13 (Xinhua) -- Body",
                  "<P>\nBRISBANE, Australia, Jan. 1(Xinhua) -- Body.</P>",
                  "\nRIO DE JANEIRO, Dec. 31 (Xinhua) -- Body",
                  "\nPORT-AU-PRINCE, Jan. 1 (Xinhua) -- Body",
                  "\nWASHINGTON, May 12 (AFP) -- Body",
                  "\nPanama  City,  Sept. 8 (CNA) -- Body",
                  "\nUNITED NATIONS, April 3 (Xinhua) -- The",
                  "<P>\nSAN FRANCISCO - California\n</P>",
                  "<P>\nRIO DE JANEIRO - Edward J. Snowden\n</P>",
                  "<P>\nPARETS DEL VALLÈS, Spain - From\n</P>",
          };

  private static final String[] dateLineTokens =
          { "GAZA , Dec. 1 -LRB- Xinhua -RRB- --",
                  "LOS ANGELES , Dec. 31 -LRB- Xinhua -RRB- --",
                  "CARBONDALE , United States , Dec. 13 -LRB- Xinhua -RRB- --",
                  "BRISBANE , Australia , Jan. 1 -LRB- Xinhua -RRB- --",
                  "RIO DE JANEIRO , Dec. 31 -LRB- Xinhua -RRB- --",
                  "PORT-AU-PRINCE , Jan. 1 -LRB- Xinhua -RRB- --",
                  "WASHINGTON , May 12 -LRB- AFP -RRB- --",
                  "Panama City , Sept. 8 -LRB- CNA -RRB- --",
                  "UNITED NATIONS , April 3 -LRB- Xinhua -RRB- --",
                  "SAN FRANCISCO -",
                  "RIO DE JANEIRO -",
                  "PARETS DEL VALLÈS , Spain -",
          };

  /** Test whether you can separate off a dateline as a separate sentence using ssplit.boundaryMultiTokenRegex. */
  @Test
  public void testDatelineSeparation() {
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, cleanxml, ssplit",
            "tokenize.language", "english",
            // this cleanxml flag is just the same as the default. I tried adding this to see if it fixes things, but it doesn't
            "clean.xmltags", ".*",
            "ssplit.newlineIsSentenceBreak", "two",
            "ssplit.boundaryMultiTokenRegex",
            "( /\\*NL\\*/ /\\p{Lu}[-\\p{L}]+/+ /,/ ( /[-\\p{L}]+/+ /,/ )? " +
                    "/\\p{Lu}\\p{Ll}{2,5}\\.?/ /[1-3]?[0-9]/ /-LRB-/ /\\p{Lu}\\p{L}+/ /-RRB-/ /--/ | " +
                    "/\\*NL\\*/ /\\p{Lu}[-\\p{Lu}]+/+ ( /,/ /[-\\p{L}]+/+ )? /-/ )");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    assertEquals("Bad test data", dateLineTexts.length, dateLineTokens.length);
    for (int i = 0; i < dateLineTexts.length; i++) {
      Annotation document1 = new Annotation(dateLineTexts[i]);
      pipeline.annotate(document1);
      List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);

      // for (CoreMap sentence : sentences) {
      //   String sentenceText = SentenceUtils.listToString(sentence.get(CoreAnnotations.TokensAnnotation.class));
      //   System.err.println(sentenceText);
      // }
      assertEquals("For " + dateLineTexts[i] + " annotation is " + document1, 2, sentences.size());

      List<CoreLabel> sentenceOneTokens = sentences.get(0).get(CoreAnnotations.TokensAnnotation.class);
      String sentenceOne = SentenceUtils.listToString(sentenceOneTokens);
      assertEquals("Bad tokens in dateline", dateLineTokens[i], sentenceOne);
    }
  }


  private static final String kbpDocument =
          "<DOC    id=\"ENG_NW_001278_20130413_F00012OVI\">\n" +
                  "<DATE_TIME>2013-04-13T04:49:26</DATE_TIME>\n" +
                  "<HEADLINE>\n" +
                  "Urgent: powerful quake jolts western Japan\n" +
                  "</HEADLINE>\n" +
                  "<AUTHOR>马兴华</AUTHOR>\n" +
                  "<TEXT>\n" +
                  "Urgent: powerful quake jolts western Japan\n" +
                  "\n" +
                  "Urgent: powerful quake jolts western Japan\n" +
                  "\n" +
                  "OSAKA, April 13 (Xinhua) -- A powerful earthquake stroke a wide area in Japan's Kinki region in western Japan early Saturday. The quake was strongly felt in Osaka. Enditem\n" +
                  "</TEXT>\n" +
                  "</DOC>\n";

  private static final String[] kbpSentences = {
          "Urgent : powerful quake jolts western Japan",
          "Urgent : powerful quake jolts western Japan",
          "Urgent : powerful quake jolts western Japan",
          "OSAKA , April 13 -LRB- Xinhua -RRB- --", "" +
          "A powerful earthquake stroke a wide area in Japan 's Kinki region in western Japan early Saturday .",
          "The quake was strongly felt in Osaka .",
          "Enditem",
  };


  /** Test written in 2017 to debug why the KBP setup doesn't work once you introduce newlineIsSentenceBreak=two.
   *  Somehow it fell apart with Angel's complex configuration option, stuck in forced wait. */
  @Test
  public void testKbpWorks() {
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, cleanxml, ssplit",
            "tokenize.language", "english",
            "tokenize.options", "tokenizeNLs,invertible,ptb3Escaping=true",
            "ssplit.newlineIsSentenceBreak", "two",
            "ssplit.tokenPatternsToDiscard", "\\n,\\*NL\\*",
            "ssplit.boundaryMultiTokenRegex",
            "( /\\*NL\\*/ /\\p{Lu}[-\\p{L}]+/+ /,/ ( /[-\\p{L}]+/+ /,/ )? " +
                    "/\\p{Lu}\\p{Ll}{2,5}\\.?/ /[1-3]?[0-9]/ /-LRB-/ /\\p{Lu}\\p{L}+/ /-RRB-/ /--/ | " +
                    "/\\*NL\\*/ /\\p{Lu}[-\\p{Lu}]+/+ ( /,/ /[-\\p{L}]+/+ )? /-/ )",
            "clean.xmltags", "headline|dateline|text|post",
            "clean.singlesentencetags", "HEADLINE|DATELINE|SPEAKER|POSTER|POSTDATE",
            "clean.sentenceendingtags", "P|POST|QUOTE",
            "clean.turntags", "TURN|POST|QUOTE",
            "clean.speakertags", "SPEAKER|POSTER",
            "clean.docidtags", "DOCID",
            "clean.datetags", "DATETIME|DATE|DATELINE",
            "clean.doctypetags", "DOCTYPE",
            "clean.docAnnotations", "docID=doc[id],doctype=doc[type],docsourcetype=doctype[source]",
            "clean.sectiontags", "HEADLINE|DATELINE|POST",
            "clean.sectionAnnotations", "sectionID=post[id],sectionDate=post[date|datetime],sectionDate=postdate,author=post[author],author=poster",
            "clean.quotetags", "quote",
            "clean.quoteauthorattributes", "orig_author",
            "clean.tokenAnnotations", "link=a[href],speaker=post[author],speaker=quote[orig_author]"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(kbpDocument);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);

    for (int i = 0; i < Math.min(kbpSentences.length, sentences.size()); i++) {
      CoreMap sentence = sentences.get(i);
      String sentenceText = SentenceUtils.listToString(sentence.get(CoreAnnotations.TokensAnnotation.class));
      assertEquals("Bad sentence #" + i, kbpSentences[i], sentenceText);
    }

    assertEquals("Bad total number of sentences", kbpSentences.length, sentences.size());
  }

}
