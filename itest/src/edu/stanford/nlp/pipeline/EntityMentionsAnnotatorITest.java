package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import junit.framework.TestCase;

import java.util.List;
import java.util.Properties;

/**
 * Unit test for the mentions annotator.
 *
 * @author Angel Chang
 */
public class EntityMentionsAnnotatorITest extends TestCase {
  static AnnotationPipeline pipeline = null;
  protected static final String ENTITY_MENTIONS_ANNOTATOR_NAME = "entitymentions";

  @Override
  public void setUp() throws Exception {
    synchronized(EntityMentionsAnnotatorITest.class) {
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

  protected EntityMentionsAnnotator getMentionsAnnotator()
  {
    return new EntityMentionsAnnotator(ENTITY_MENTIONS_ANNOTATOR_NAME, getDefaultProperties());
  }

  protected static EntityMentionsAnnotator getMentionsAnnotator(Properties props)
  {
    return new EntityMentionsAnnotator(ENTITY_MENTIONS_ANNOTATOR_NAME, props);
  }

  protected static Annotation createDocument(String text) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);
    return annotation;
  }

  protected static void compareMentions(String prefix, String[] expectedMentions, List<CoreMap> mentions) {
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
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

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
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

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
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    // TODO: Fixme - separate out the two mentions of July 3rd!!!
    String[] expectedMentions = {
        "[Text=July 3rd July 3rd CharacterOffsetBegin=0 CharacterOffsetEnd=17 Tokens=[July-1, 3rd-2, July-3, 3rd-4] TokenBegin=0 TokenEnd=4 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-07-03 EntityType=DATE Timex=<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"XXXX-07-03\">July 3rd July 3rd</TIMEX3>]",
        "[Text=two CharacterOffsetBegin=22 CharacterOffsetEnd=25 Tokens=[two-6] TokenBegin=5 TokenEnd=6 NamedEntityTag=NUMBER NormalizedNamedEntityTag=2.0 EntityType=NUMBER]"
    };
    compareMentions("testDates2", expectedMentions, mentions);
  }

  public void testNumbers() {
    Annotation doc = createDocument("one two three four five");
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

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

  public void testNewsText() {
    Annotation doc = createDocument("Duke of Cambridge, Prince William, unveiled a new China Center in the University of Oxford Monday.\n" +
        "Covering an area nearly 5,500 square meters, the new Dickson Poon University of Oxford China Center in St Hugh's College cost about 21 million pounds.\n" +
        "Dickson Poon, a philanthropist from Hong Kong, China, is the one of the major donors of the center, who contributed 10 million British pounds (16.14 million U.S. dollars).");

    EntityMentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    // TODO: "Duke of Cambridge" should be one mention. Perhaps should get "Prince William" rather than just "William"
    //       "nearly 5,500 square meters"? "10 million British pounds", "16.14 million U.S. dollars"
    // TODO: "China Center should be organization, but is currently coming out as location. :(
    String[] expectedMentions = {
        "[Text=Duke CharacterOffsetBegin=0 CharacterOffsetEnd=4 Tokens=[Duke-1] TokenBegin=0 TokenEnd=1 NamedEntityTag=PERSON EntityType=PERSON]",
        "[Text=Cambridge CharacterOffsetBegin=8 CharacterOffsetEnd=17 Tokens=[Cambridge-3] TokenBegin=2 TokenEnd=3 NamedEntityTag=LOCATION EntityType=LOCATION]",
        "[Text=William CharacterOffsetBegin=26 CharacterOffsetEnd=33 Tokens=[William-6] TokenBegin=5 TokenEnd=6 NamedEntityTag=PERSON EntityType=PERSON]",
        "[Text=China Center CharacterOffsetBegin=50 CharacterOffsetEnd=62 Tokens=[China-11, Center-12] TokenBegin=10 TokenEnd=12 NamedEntityTag=LOCATION EntityType=LOCATION]",
        "[Text=University of Oxford CharacterOffsetBegin=70 CharacterOffsetEnd=90 Tokens=[University-15, of-16, Oxford-17] TokenBegin=14 TokenEnd=17 NamedEntityTag=ORGANIZATION EntityType=ORGANIZATION]",
        "[Text=Monday CharacterOffsetBegin=91 CharacterOffsetEnd=97 Tokens=[Monday-18] TokenBegin=17 TokenEnd=18 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-WXX-1 EntityType=DATE Timex=<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"XXXX-WXX-1\">Monday</TIMEX3>]",
        "[Text=5,500 CharacterOffsetBegin=123 CharacterOffsetEnd=128 Tokens=[5,500-5] TokenBegin=23 TokenEnd=24 NamedEntityTag=NUMBER NormalizedNamedEntityTag=~5500.0 EntityType=NUMBER]",
        "[Text=Dickson Poon University of Oxford China Center CharacterOffsetBegin=152 CharacterOffsetEnd=198 Tokens=[Dickson-11, Poon-12, University-13, of-14, Oxford-15, China-16, Center-17] TokenBegin=29 TokenEnd=36 NamedEntityTag=ORGANIZATION EntityType=ORGANIZATION]",
        "[Text=St Hugh 's College CharacterOffsetBegin=202 CharacterOffsetEnd=219 Tokens=[St-19, Hugh-20, 's-21, College-22] TokenBegin=37 TokenEnd=41 NamedEntityTag=ORGANIZATION EntityType=ORGANIZATION]",
        "[Text=21 million pounds CharacterOffsetBegin=231 CharacterOffsetEnd=248 Tokens=[21-25, million-26, pounds-27] TokenBegin=43 TokenEnd=46 NamedEntityTag=MONEY NormalizedNamedEntityTag=~£2.1E7 EntityType=MONEY]",
        "[Text=Dickson Poon CharacterOffsetBegin=250 CharacterOffsetEnd=262 Tokens=[Dickson-1, Poon-2] TokenBegin=47 TokenEnd=49 NamedEntityTag=PERSON EntityType=PERSON]",
        "[Text=Hong Kong CharacterOffsetBegin=286 CharacterOffsetEnd=295 Tokens=[Hong-7, Kong-8] TokenBegin=53 TokenEnd=55 NamedEntityTag=LOCATION EntityType=LOCATION]",
        "[Text=China CharacterOffsetBegin=297 CharacterOffsetEnd=302 Tokens=[China-10] TokenBegin=56 TokenEnd=57 NamedEntityTag=LOCATION EntityType=LOCATION]",
        "[Text=one CharacterOffsetBegin=311 CharacterOffsetEnd=314 Tokens=[one-14] TokenBegin=60 TokenEnd=61 NamedEntityTag=NUMBER NormalizedNamedEntityTag=1.0 EntityType=NUMBER]",
        "[Text=10 million CharacterOffsetBegin=366 CharacterOffsetEnd=376 Tokens=[10-25, million-26] TokenBegin=71 TokenEnd=73 NamedEntityTag=NUMBER NormalizedNamedEntityTag=1.0E7 EntityType=NUMBER]",
        "[Text=British CharacterOffsetBegin=377 CharacterOffsetEnd=384 Tokens=[British-27] TokenBegin=73 TokenEnd=74 NamedEntityTag=MISC EntityType=MISC]",
        "[Text=16.14 million CharacterOffsetBegin=393 CharacterOffsetEnd=406 Tokens=[16.14-30, million-31] TokenBegin=76 TokenEnd=78 NamedEntityTag=NUMBER NormalizedNamedEntityTag=1.614E7 EntityType=NUMBER]",
        "[Text=U.S. CharacterOffsetBegin=407 CharacterOffsetEnd=411 Tokens=[U.S.-32] TokenBegin=78 TokenEnd=79 NamedEntityTag=LOCATION EntityType=LOCATION]"
    };
    compareMentions("testNewsText", expectedMentions, mentions);
  }
}
