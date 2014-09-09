package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import junit.framework.TestCase;

import java.util.List;
import java.util.Properties;

/**
 * Unit test for the mentions annotator
 *
 * @author Angel Chang
 */
public class MentionsAnnotatorITest extends TestCase {
  static AnnotationPipeline pipeline = null;
  protected static final String MENTIONS_ANNOTATOR_NAME = "mentions";

  @Override
  public void setUp() throws Exception {
    synchronized(MentionsAnnotatorITest.class) {
      if (pipeline == null) {
        Properties props = new Properties();
        // TODO: remove need for ner and just have the mentions annotator
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");
        pipeline = new StanfordCoreNLP(props);
      }
    }
  }

  protected static Properties getDefaultProperties()
  {
    Properties props = new Properties();
    return props;
  }

  protected MentionsAnnotator getMentionsAnnotator()
  {
    return new MentionsAnnotator(MENTIONS_ANNOTATOR_NAME, getDefaultProperties());
  }

  protected static MentionsAnnotator getMentionsAnnotator(Properties props)
  {
    return new MentionsAnnotator(MENTIONS_ANNOTATOR_NAME, props);
  }

  protected static Annotation createDocument(String text) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);
    return annotation;
  }

  protected void compareMentions(String prefix, String[] expectedMentions, List<CoreMap> mentions) {
    if (expectedMentions == null) {
      for (int i = 0; i < mentions.size(); i++) {
        String actual = mentions.get(i).toShorterString();
        System.out.println(prefix + ": Got mention." + i + " " + actual);
      }
      assertTrue(prefix + ": No expected mentions provided", false);
    }
    int minMatchable = Math.min(expectedMentions.length, mentions.size());
    for (int i = 0; i < minMatchable; i++) {
      String expected = expectedMentions[i];
      String actual = mentions.get(i).toShorterString();
      assertEquals(prefix + ".mention." + i, expected, actual);
    }
    assertEquals(prefix + ".length", mentions.size(), expectedMentions.length);
  }

  // Actual tests
  public void testBasicMentions() {
    Annotation doc = createDocument("I was at Stanford University Albert Peacock");
    List<CoreLabel> tokens = doc.get(CoreAnnotations.TokensAnnotation.class);
    tokens.get(3).setNER("ORGANIZATION");
    tokens.get(4).setNER("ORGANIZATION");
    MentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    String[] expectedMentions = {
        "[Text=Stanford University CharacterOffsetBegin=9 CharacterOffsetEnd=28 Tokens=[Stanford-4, University-5] TokenBegin=3 TokenEnd=5 NamedEntityTag=ORGANIZATION EntityType=ORGANIZATION]",
        "[Text=Albert Peacock CharacterOffsetBegin=29 CharacterOffsetEnd=43 Tokens=[Albert-6, Peacock-7] TokenBegin=5 TokenEnd=7 NamedEntityTag=PERSON EntityType=PERSON]"
    };
    compareMentions("testBasicMentions", expectedMentions, mentions);
  }

  public void testDates() {
    Annotation doc = createDocument("July 3rd July 4th are two different dates");
    MentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    String[] expectedMentions = {
        "[Text=July 3rd CharacterOffsetBegin=0 CharacterOffsetEnd=8 Tokens=[July-1, 3rd-2] TokenBegin=0 TokenEnd=2 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-07-03 EntityType=DATE Timex=<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"XXXX-07-03\">July 3rd</TIMEX3>]",
        "[Text=July 4th CharacterOffsetBegin=9 CharacterOffsetEnd=17 Tokens=[July-3, 4th-4] TokenBegin=2 TokenEnd=4 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-07-04 EntityType=DATE Timex=<TIMEX3 tid=\"t2\" type=\"DATE\" value=\"XXXX-07-04\">July 4th</TIMEX3>]",
        "[Text=two CharacterOffsetBegin=22 CharacterOffsetEnd=25 Tokens=[two-6] TokenBegin=5 TokenEnd=6 NamedEntityTag=NUMBER NormalizedNamedEntityTag=2.0 EntityType=NUMBER]"
    };
    compareMentions("testDates", expectedMentions, mentions);
  }

  public void testDates2() {
    Annotation doc = createDocument("July 3rd July 3rd are two mentions of the same date");
    MentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    // TODO: Fixme!!!
    String[] expectedMentions = {
        "[Text=July 3rd July 3rd CharacterOffsetBegin=0 CharacterOffsetEnd=17 Tokens=[July-1, 3rd-2, July-3, 3rd-4] TokenBegin=0 TokenEnd=4 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-07-03 EntityType=DATE Timex=<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"XXXX-07-03\">July 3rd July 3rd</TIMEX3>]",
        "[Text=two CharacterOffsetBegin=22 CharacterOffsetEnd=25 Tokens=[two-6] TokenBegin=5 TokenEnd=6 NamedEntityTag=NUMBER NormalizedNamedEntityTag=2.0 EntityType=NUMBER]"
    };
    compareMentions("testDates2", expectedMentions, mentions);
  }

  public void testNumbers() {
    Annotation doc = createDocument("one two three four five");
    MentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    String[] expectedMentions = {
        "[Text=one CharacterOffsetBegin=0 CharacterOffsetEnd=3 Tokens=[one-1] TokenBegin=0 TokenEnd=1 NamedEntityTag=NUMBER NormalizedNamedEntityTag=1.0 EntityType=NUMBER]",
        "[Text=two CharacterOffsetBegin=4 CharacterOffsetEnd=7 Tokens=[two-2] TokenBegin=1 TokenEnd=2 NamedEntityTag=NUMBER NormalizedNamedEntityTag=2.0 EntityType=NUMBER]",
        "[Text=three CharacterOffsetBegin=8 CharacterOffsetEnd=13 Tokens=[three-3] TokenBegin=2 TokenEnd=3 NamedEntityTag=NUMBER NormalizedNamedEntityTag=3.0 EntityType=NUMBER]",
        "[Text=four CharacterOffsetBegin=14 CharacterOffsetEnd=18 Tokens=[four-4] TokenBegin=3 TokenEnd=4 NamedEntityTag=NUMBER NormalizedNamedEntityTag=4.0 EntityType=NUMBER]",
        "[Text=five CharacterOffsetBegin=19 CharacterOffsetEnd=23 Tokens=[five-5] TokenBegin=4 TokenEnd=5 NamedEntityTag=NUMBER NormalizedNamedEntityTag=5.0 EntityType=NUMBER]"
    };
    compareMentions("testNumbers", expectedMentions, mentions);
  }
}
