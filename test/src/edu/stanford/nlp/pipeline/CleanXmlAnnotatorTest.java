package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

import static org.junit.Assert.assertEquals;

/**
 * @author John Bauer
 */
public class CleanXmlAnnotatorTest {

  private static Annotator ptbInvertible; // = null;
  private static Annotator ptbNotInvertible; // = null;

  private static Annotator cleanXmlAllTags; // = null;
  private static Annotator cleanXmlSomeTags; // = null;
  private static Annotator cleanXmlEndSentences; // = null;
  private static Annotator cleanXmlWithFlaws; // = null;

  private static Annotator wtsSplitter; // = null;

  /**
   * Initialize the annotators at the start of the unit test.
   * If they've already been initialized, do nothing.
   */
  @Before
  public void setUp() throws Exception {
    synchronized(CleanXmlAnnotatorTest.class) {
      // we create the TokenizerAnnotator without the ssplit so we can
      // manually control the pieces
      // another alternative would be to create TokenizerAnnotators
      // with the CleanXML as part of it
      if (ptbInvertible == null) {
        Properties props = new Properties();
        props.setProperty("tokenize.language", "en");
        props.setProperty("tokenize.ssplit", "false");
        ptbInvertible = new TokenizerAnnotator(false, props, "invertible,ptb3Escaping=true");
      }
      if (ptbNotInvertible == null) {
        Properties props = new Properties();
        props.setProperty("tokenize.language", "en");
        props.setProperty("tokenize.ssplit", "false");
        ptbNotInvertible = new TokenizerAnnotator(false, props, "invertible=false,ptb3Escaping=true");
      }
      if (cleanXmlAllTags == null) {
        cleanXmlAllTags = new CleanXmlAnnotator(".*", "", "", false);
      }
      if (cleanXmlSomeTags == null) {
        cleanXmlSomeTags = new CleanXmlAnnotator("p", "", "", false);
      }
      if (cleanXmlEndSentences == null) {
        cleanXmlEndSentences = new CleanXmlAnnotator(".*", "p", "", false);
      }
      if (cleanXmlWithFlaws == null) {
        cleanXmlWithFlaws = new CleanXmlAnnotator(".*", "", "", true);
      }
      if (wtsSplitter == null) {
        wtsSplitter = new WordsToSentencesAnnotator(false);
      }
    }
  }

  public static Annotation annotate(String text,
                                    Annotator tokenizer, Annotator xmlRemover,
                                    Annotator splitter) {
    Annotation annotation = new Annotation(text);
    tokenizer.annotate(annotation);
    if (xmlRemover != null)
      xmlRemover.annotate(annotation);
    if (splitter != null)
      splitter.annotate(annotation);
    return annotation;
  }

  private static void checkResult(Annotation annotation,
                                  String... gold) {
    List<CoreLabel> goldTokens = new ArrayList<>();
    Annotation[] goldAnnotations = new Annotation[gold.length];
    for (int i = 0; i < gold.length; ++i) {
      goldAnnotations[i] = annotate(gold[i], ptbInvertible, null, null);
      goldTokens.addAll(goldAnnotations[i].get(CoreAnnotations.TokensAnnotation.class));
    }
    List<CoreLabel> annotationLabels = annotation.get(CoreAnnotations.TokensAnnotation.class);

    if (goldTokens.size() != annotationLabels.size()) {
      for (CoreLabel annotationLabel : annotationLabels) {
        System.err.print(annotationLabel.word());
        System.err.print(' ');
      }
      System.err.println();
      for (CoreLabel goldToken : goldTokens) {
        System.err.print(goldToken.word());
        System.err.print(' ');
      }
      System.err.println();
    }

    assertEquals("Token count mismatch (gold vs: actual)", goldTokens.size(), annotationLabels.size());
    for (int i = 0; i < annotationLabels.size(); ++i) {
      assertEquals(goldTokens.get(i).word(),
              annotationLabels.get(i).word());
    }

    if (annotation.get(CoreAnnotations.SentencesAnnotation.class) != null) {
      List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      assertEquals("Sentence count mismatch", gold.length, sentences.size());
    }
  }

  private static void checkBeforeInvert(Annotation annotation, String gold) {
    List<CoreLabel> annotationLabels =
      annotation.get(CoreAnnotations.TokensAnnotation.class);
    StringBuilder original = new StringBuilder();
    for (CoreLabel label : annotationLabels) {
      original.append(label.get(CoreAnnotations.BeforeAnnotation.class));
      original.append(label.get(CoreAnnotations.OriginalTextAnnotation.class));
    }
    original.append(annotationLabels.get(annotationLabels.size() - 1).
                    get(CoreAnnotations.AfterAnnotation.class));
    assertEquals(gold, original.toString());
  }

  private static void checkAfterInvert(Annotation annotation, String gold) {
    List<CoreLabel> annotationLabels =
      annotation.get(CoreAnnotations.TokensAnnotation.class);
    StringBuilder original = new StringBuilder();
    original.append(annotationLabels.get(0).get(CoreAnnotations.BeforeAnnotation.class));
    for (CoreLabel label : annotationLabels) {
      original.append(label.get(CoreAnnotations.OriginalTextAnnotation.class));
      original.append(label.get(CoreAnnotations.AfterAnnotation.class));
    }
    assertEquals(gold, original.toString());
  }

  private static void checkContext(CoreLabel label, String... expectedContext) {
    List<String> xmlContext = label.get(CoreAnnotations.XmlContextAnnotation.class);
    assertEquals(expectedContext.length, xmlContext.size());
    for (int i = 0; i < expectedContext.length; ++i) {
      assertEquals(expectedContext[i], xmlContext.get(i));
    }
  }

  @Test
  public void testRemoveXML() {
    String testString = "<xml>This is a test string.</xml>";
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlAllTags, wtsSplitter),
                "This is a test string.");
  }

  @Test
  public void testExtractSpecificTag() {
    String testString = ("<p>This is a test string.</p>" +
                         "<foo>This should not be found</foo>");
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlSomeTags, wtsSplitter),
                "This is a test string.");
  }

  @Test
  public void testSentenceSplitting() {
    String testString = ("<p>This sentence is split</p>" +
                         "<foo>over two tags</foo>");
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlAllTags, wtsSplitter),
                "This sentence is split over two tags");
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlEndSentences, wtsSplitter),
                "This sentence is split", "over two tags");
  }

  @Test
  public void testNestedTags() {
    String testString = "<p><p>This text is in a</p>nested tag</p>";
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlAllTags, wtsSplitter),
                "This text is in a nested tag");
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlEndSentences, wtsSplitter),
                "This text is in a", "nested tag");
  }

  @Test
  public void testMissingCloseTags() {
    String testString = "<text><p>This text <p>has closing tags wrong</text>";
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlWithFlaws, wtsSplitter),
                "This text has closing tags wrong");
    try {
      checkResult(annotate(testString, ptbInvertible,
                           cleanXmlAllTags, wtsSplitter),
                  "This text has closing tags wrong");
      throw new RuntimeException("it was supposed to barf");
    } catch(IllegalArgumentException e) {
      // this is what was supposed to happen
    }
  }

  @Test
  public void testEarlyEnd() {
    String testString = "<text>This text ends before all tags closed";
    checkResult(annotate(testString, ptbInvertible,
                         cleanXmlWithFlaws, wtsSplitter),
                "This text ends before all tags closed");
    try {
      checkResult(annotate(testString, ptbInvertible,
                           cleanXmlAllTags, wtsSplitter),
                  "This text ends before all tags closed");
      throw new RuntimeException("it was supposed to barf");
    } catch(IllegalArgumentException e) {
      // this is what was supposed to happen
    }
  }

  @Test
  public void testInvertible() {
    String testNoTags = "This sentence should be invertible.";
    String[] testInvertibles = { "  <xml>  This sentence should  be  invertible.  </xml>  ",
                                 " <xml>   <foo>       <bar>This sentence should     </bar>be invertible.   </foo>   </xml> ",
                                 "  This sentence <xml>should</xml>  be  invertible.  ",
                                 "  This sentence<xml> should </xml>be  invertible.  ",
                                 "  This sentence <xml> should </xml>  be  invertible.  " };

    Annotation annotation = annotate(testNoTags, ptbInvertible,
                                     cleanXmlAllTags, wtsSplitter);
    checkResult(annotation, testNoTags);
    checkBeforeInvert(annotation, testNoTags);
    checkAfterInvert(annotation, testNoTags);

    for (String test : testInvertibles) {
      annotation = annotate(test, ptbInvertible,
                            cleanXmlAllTags, wtsSplitter);
      checkResult(annotation, testNoTags);
      checkBeforeInvert(annotation, test);
      checkAfterInvert(annotation, test);
    }
  }

  @Test
  public void testContext() {
    String testManyTags =
      " <xml>   <foo>       <bar>This sentence should  " +
      "   </bar>be invertible.   </foo>   </xml> ";
    Annotation annotation = annotate(testManyTags, ptbInvertible,
                                     cleanXmlAllTags, wtsSplitter);

    List<CoreLabel> annotationLabels =
      annotation.get(CoreAnnotations.TokensAnnotation.class);
    for (int i = 0; i < 3; ++i) {
      checkContext(annotationLabels.get(i), "xml", "foo", "bar");
    }
    for (int i = 3; i < 5; ++i) {
      checkContext(annotationLabels.get(i), "xml", "foo");
    }
  }

  @Test
  public void testOffsets() {
    String testString = "<p><p>This text is in a</p>nested tag</p>";
    Annotation annotation = annotate(testString, ptbInvertible,
                                     cleanXmlAllTags, wtsSplitter);
    checkResult(annotation, "This text is in a nested tag");
    List<CoreLabel> labels = annotation.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(6,
            labels.get(0).
                    get(CoreAnnotations.CharacterOffsetBeginAnnotation.class).intValue());
    assertEquals(10,
            labels.get(0).
                    get(CoreAnnotations.CharacterOffsetEndAnnotation.class).intValue());
  }

  @Test
  public void testAttributes() {
    String testString = "<p a=\"b\">This text has an attribute</p>";
    Annotation annotation = annotate(testString, ptbInvertible,
                                     cleanXmlAllTags, wtsSplitter);
    checkResult(annotation, "This text has an attribute");
  }

  @Test
  public void testViaCoreNlp() {
    String testManyTags =
      " <xml>   <foo>       <bar>This sentence should  " +
      "   </bar>be invertible.   </foo>   </xml> ";
    Annotation anno = new Annotation(testManyTags);
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit, cleanxml",
            "tokenizer.options", "invertible,ptb3Escaping=true",
            "cleanxml.xmltags", ".*",
            "cleanxml.sentenceendingtags", "p",
            "cleanxml.datetags", "",
            "cleanxml.allowflawedxml", "false"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(anno);

    checkBeforeInvert(anno, testManyTags);
    checkAfterInvert(anno, testManyTags);
    List<CoreLabel> annotationLabels =
      anno.get(CoreAnnotations.TokensAnnotation.class);
    for (int i = 0; i < 3; ++i) {
      checkContext(annotationLabels.get(i), "xml", "foo", "bar");
    }
    for (int i = 3; i < 5; ++i) {
      checkContext(annotationLabels.get(i), "xml", "foo");
    }
  }

  @Test
  public void testKbpSectionMatching() {
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize,cleanxml,ssplit",
            "tokenize.language", "es",
            "ssplit.newlineIsSentenceBreak", "two",
            "ssplit.tokenPatternsToDiscard", "\\n,\\*NL\\*",
            "ssplit.boundaryMultiTokenRegex",
            "/\\*NL\\*/ /\\p{Lu}[-\\p{L}]+/+ /,/ ( /[-\\p{L}]+/+ /,/ )? " +
                    "/[1-3]?[0-9]/ /\\p{Ll}{3,5}/ /-LRB-/ /\\p{Lu}\\p{L}+/ /-RRB-/ /--/",
            "clean.xmltags", "headline|text|post",
            "clean.singlesentencetags", "HEADLINE|AUTHOR",
            "clean.sentenceendingtags", "TEXT|POST|QUOTE",
            "clean.turntags", "POST|QUOTE",
            "clean.speakertags", "AUTHOR",
            "clean.datetags", "DATE_TIME",
            "clean.doctypetags", "DOC",
            "clean.docAnnotations", "docID=doc[id]",
            "clean.sectiontags", "HEADLINE|POST",
            "clean.sectionAnnotations", "sectionID=post[id],sectionDate=post[datetime],author=post[author]",
            "clean.quotetags", "quote",
            "clean.quoteauthorattributes", "orig_author",
            "clean.tokenAnnotations", "link=a[href],speaker=post[author],speaker=quote[orig_author]"
    );
    String document = "<doc id=\"SPA_DF_000389_20090909_G00A09SM4\">\n" +
            "<headline>\n" +
            "Problema para Activar Restaurar Sistema En Win Ue\n" +
            "</headline>\n" +
            "<post author=\"mysecondskin\" datetime=\"2009-09-09T00:00:00\" id=\"p1\">\n" +
            "hola portalianos tengo un problemita,mi vieja tiene un pc en su casa y no tiene activado restaurar sistema ya que el pc tiene el xp ue v5,he tratado de arreglárselo pero no he podido dar con la solución y no he querido formatearle el pc porque tiene un sin numero de programas que me da paja reinstalar\n" +
            "ojala alguien me pueda ayudar\n" +
            "vale socios\n" +
            "</post>\n" +
            "<post author=\"pajenri\" datetime=\"2009-09-09T00:00:00\" id=\"p2\">\n" +
            "<quote orig_author=\"mysecondskin\">\n" +
            "hola portalianos tengo un problemita,mi vieja tiene un pc en su casa y no tiene activado restaurar sistema ya que el pc tiene el xp ue v5,he tratado de arreglárselo pero no he podido dar con la solución y no he querido formatearle el pc porque tiene un sin numero de programas que me da paja reinstalar\n" +
            "ojala alguien me pueda ayudar\n" +
            "vale socios\n" +
            "</quote>\n" +
            "\n" +
            "por lo que tengo entendido esa opcion en los win ue vienen eliminadas no desactivadas, asi que para activarla habria que reinstalar un xp limpio no tuneado. como dato es tipico en sistemas tuneados comos el win ue que suceda esto. el restaurador salva mas de lo que se cree. si toy equibocado con la info que alguien me corrija\n" +
            "</post>\n" +
            "<post author=\"UnknownCnR\" datetime=\"2009-09-09T00:00:00\" id=\"p3\">\n" +
            "<a href=\"http://www.sendspace.com/file/54pxbl\">http://www.sendspace.com/file/54pxbl</a>\n" +
            "\n" +
            "Con este registro podras activarlo ;)\n" +
            "</post>\n" +
            "<post author=\"mysecondskin\" datetime=\"2009-09-11T00:00:00\" id=\"p4\">\n" +
            "gracias pero de verdad esa solucion no sirve\n" +
            "</post>\n" +
            "</doc>\n";

    String[][] sections = {
            { null, null, "Problema para Activar Restaurar Sistema En Win Ue\n" },
            { "mysecondskin", "2009-09-09T00:00:00", "hola portalianos tengo un problemita , mi vieja tiene un pc en su casa y no tiene activado restaurar sistema ya que el pc tiene el xp ue v5 , he tratado de arreglárselo pero no he podido dar con la solución y no he querido formatearle el pc porque tiene un sin numero de programas que me da paja reinstalar ojala alguien me pueda ayudar vale socios\n" },
            { "pajenri", "2009-09-09T00:00:00", "(QUOTING: mysecondskin) hola portalianos tengo un problemita , mi vieja tiene un pc en su casa y no tiene activado restaurar sistema ya que el pc tiene el xp ue v5 , he tratado de arreglárselo pero no he podido dar con la solución y no he querido formatearle el pc porque tiene un sin numero de programas que me da paja reinstalar ojala alguien me pueda ayudar vale socios\n" +
                    "por lo que tengo entendido esa opcion en los win ue vienen eliminadas no desactivadas , asi que para activarla habria que reinstalar un xp limpio no tuneado .\n" +
                    "como dato es tipico en sistemas tuneados comos el win ue que suceda esto .\n" +
                    "el restaurador salva mas de lo que se cree .\n" +
                    "si toy equibocado con la info que alguien me corrija\n" },
            { "UnknownCnR", "2009-09-09T00:00:00", "http://www.sendspace.com/file/54pxbl\n" +
                    "Con este registro podras activarlo ;)\n" },
            { "mysecondskin", "2009-09-11T00:00:00", "gracias pero de verdad esa solucion no sirve\n" },
    };

    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Annotation testDocument = new Annotation(document);
    pipeline.annotate(testDocument);

    // check the forum posts
    int num = 0;
    for (CoreMap discussionForumPost : testDocument.get(CoreAnnotations.SectionsAnnotation.class)) {
      assertEquals(sections[num][0], discussionForumPost.get(CoreAnnotations.AuthorAnnotation.class));
      assertEquals(sections[num][1], discussionForumPost.get(CoreAnnotations.SectionDateAnnotation.class));

      StringBuilder sb = new StringBuilder();
      for (CoreMap sentence : discussionForumPost.get(CoreAnnotations.SentencesAnnotation.class)) {
        boolean sentenceQuoted = (sentence.get(CoreAnnotations.QuotedAnnotation.class) != null) &&
            sentence.get(CoreAnnotations.QuotedAnnotation.class);
        System.err.println("Sentence " + sentence + " quoted=" + sentenceQuoted);
        String sentenceAuthor = sentence.get(CoreAnnotations.AuthorAnnotation.class);
        String potentialQuoteText = sentenceQuoted ? "(QUOTING: "+sentenceAuthor+") " : "" ;
        sb.append(potentialQuoteText);
        sb.append(sentence.get(CoreAnnotations.TokensAnnotation.class).stream().
                map(CoreLabel::word).collect(Collectors.joining(" ")));
        sb.append('\n');
      }
      assertEquals(sections[num][2], sb.toString());
      num++;
    }
    assertEquals("Too few sections", sections.length, num);
  }

}
