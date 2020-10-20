package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

import edu.stanford.nlp.util.PropertiesUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

/**
 * Unit test for the mentions annotator.
 *
 * @author Angel Chang
 */
public class EntityMentionsAnnotatorITest {

  private static AnnotationPipeline pipeline; // = null;
  private static final String ENTITY_MENTIONS_ANNOTATOR_NAME = "entitymentions";

  @Before
  public void setUp() throws Exception {
    synchronized(EntityMentionsAnnotatorITest.class) {
      if (pipeline == null) {
        Properties props = PropertiesUtils.asProperties(
                // TODO: remove need for ner and just have the mentions annotator
                "annotators", "tokenize, ssplit, pos, lemma, ner",
                "ner.applyFineGrained", "false",
                "ner.buildEntityMentions", "false");
        pipeline = new StanfordCoreNLP(props);
      }
    }
  }

  protected static Properties getDefaultProperties() {
    return new Properties();
  }

  private static EntityMentionsAnnotator getMentionsAnnotator() {
    return getMentionsAnnotator(getDefaultProperties());
  }

  private static EntityMentionsAnnotator getMentionsAnnotator(Properties props) {
    return new EntityMentionsAnnotator(ENTITY_MENTIONS_ANNOTATOR_NAME, props);
  }

  protected static Annotation createDocument(String text) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);
    return annotation;
  }

  private static void compareMentions(String prefix, String[] expectedMentions, List<CoreMap> mentions) {
    if (expectedMentions == null) {
      for (int i = 0; i < mentions.size(); i++) {
        String actual = mentions.get(i).toShorterString();
        System.err.println(prefix + ": Got mention." + i + ' ' + actual);
      }
      Assert.fail(prefix + ": No expected mentions provided");
    }
    int minMatchable = Math.min(expectedMentions.length, mentions.size());
    for (int i = 0; i < minMatchable; i++) {
      String expected = expectedMentions[i];
      String actual = mentions.get(i).toShorterString();
      Assert.assertEquals(prefix + ".mention." + i, expected, actual);
    }
    Assert.assertEquals(prefix + ".length", expectedMentions.length, mentions.size());
  }

  // Actual tests
  @Test
  public void testBasicMentions() {
    Annotation doc = createDocument("I was at Stanford University Albert Peacock");
    List<CoreLabel> tokens = doc.get(CoreAnnotations.TokensAnnotation.class);
    tokens.get(3).setNER("ORGANIZATION");
    tokens.get(4).setNER("ORGANIZATION");
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    String[] expectedMentions = {
        "[Text=Stanford University CharacterOffsetBegin=9 CharacterOffsetEnd=28 Tokens=[Stanford-4, University-5] TokenBegin=3 TokenEnd=5 NamedEntityTag=ORGANIZATION EntityType=ORGANIZATION SentenceIndex=0 EntityMentionIndex=0 CanonicalEntityMentionIndex=0 NamedEntityTagProbs={ORGANIZATION=0.9919133569959268}]",
        "[Text=Albert Peacock CharacterOffsetBegin=29 CharacterOffsetEnd=43 Tokens=[Albert-6, Peacock-7] TokenBegin=5 TokenEnd=7 NamedEntityTag=PERSON EntityType=PERSON SentenceIndex=0 EntityMentionIndex=1 CanonicalEntityMentionIndex=1 NamedEntityTagProbs={PERSON=0.9913517505487812}]"
    };
    compareMentions("testBasicMentions", expectedMentions, mentions);
  }

  @Test
  public void testDates() {
    Annotation doc = createDocument("July 3rd July 4th are two different dates");
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    String[] expectedMentions = {
        "[Text=July 3rd CharacterOffsetBegin=0 CharacterOffsetEnd=8 Tokens=[July-1, 3rd-2] TokenBegin=0 TokenEnd=2 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-07-03 EntityType=DATE SentenceIndex=0 Timex=<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"XXXX-07-03\">July 3rd</TIMEX3> EntityMentionIndex=0 CanonicalEntityMentionIndex=0 NamedEntityTagProbs={DATE=0.9990718702329264}]",
        "[Text=July 4th CharacterOffsetBegin=9 CharacterOffsetEnd=17 Tokens=[July-3, 4th-4] TokenBegin=2 TokenEnd=4 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-07-04 EntityType=DATE SentenceIndex=0 Timex=<TIMEX3 tid=\"t2\" type=\"DATE\" value=\"XXXX-07-04\">July 4th</TIMEX3> EntityMentionIndex=1 CanonicalEntityMentionIndex=1 NamedEntityTagProbs={DATE=-1.0}]",
        "[Text=two CharacterOffsetBegin=22 CharacterOffsetEnd=25 Tokens=[two-6] TokenBegin=5 TokenEnd=6 NamedEntityTag=NUMBER NormalizedNamedEntityTag=2.0 EntityType=NUMBER SentenceIndex=0 EntityMentionIndex=2 CanonicalEntityMentionIndex=2 NamedEntityTagProbs={NUMBER=-1.0}]"
    };
    compareMentions("testDates", expectedMentions, mentions);
  }

  @Test
  public void testDates2() {
    Annotation doc = createDocument("July 3rd July 3rd are two mentions of the same date");
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    // TODO: Fixme - separate out the two mentions of July 3rd!!!
    String[] expectedMentions = {
        "[Text=July 3rd July 3rd CharacterOffsetBegin=0 CharacterOffsetEnd=17 Tokens=[July-1, 3rd-2, July-3, 3rd-4] TokenBegin=0 TokenEnd=4 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-07-03 EntityType=DATE SentenceIndex=0 Timex=<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"XXXX-07-03\">July 3rd July 3rd</TIMEX3> EntityMentionIndex=0 CanonicalEntityMentionIndex=0 NamedEntityTagProbs={DATE=-1.0}]",
        "[Text=two CharacterOffsetBegin=22 CharacterOffsetEnd=25 Tokens=[two-6] TokenBegin=5 TokenEnd=6 NamedEntityTag=NUMBER NormalizedNamedEntityTag=2.0 EntityType=NUMBER SentenceIndex=0 EntityMentionIndex=1 CanonicalEntityMentionIndex=1 NamedEntityTagProbs={NUMBER=-1.0}]"
    };
    compareMentions("testDates2", expectedMentions, mentions);
  }

  @Test
  public void testNumbers() {
    Annotation doc = createDocument("one two three four five");
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    String[] expectedMentions = {
        "[Text=one CharacterOffsetBegin=0 CharacterOffsetEnd=3 Tokens=[one-1] TokenBegin=0 TokenEnd=1 NamedEntityTag=NUMBER NormalizedNamedEntityTag=1.0 EntityType=NUMBER SentenceIndex=0 EntityMentionIndex=0 CanonicalEntityMentionIndex=0 NamedEntityTagProbs={NUMBER=-1.0}]",
        "[Text=two CharacterOffsetBegin=4 CharacterOffsetEnd=7 Tokens=[two-2] TokenBegin=1 TokenEnd=2 NamedEntityTag=NUMBER NormalizedNamedEntityTag=2.0 EntityType=NUMBER SentenceIndex=0 EntityMentionIndex=1 CanonicalEntityMentionIndex=1 NamedEntityTagProbs={NUMBER=-1.0}]",
        "[Text=three CharacterOffsetBegin=8 CharacterOffsetEnd=13 Tokens=[three-3] TokenBegin=2 TokenEnd=3 NamedEntityTag=NUMBER NormalizedNamedEntityTag=3.0 EntityType=NUMBER SentenceIndex=0 EntityMentionIndex=2 CanonicalEntityMentionIndex=2 NamedEntityTagProbs={NUMBER=-1.0}]",
        "[Text=four CharacterOffsetBegin=14 CharacterOffsetEnd=18 Tokens=[four-4] TokenBegin=3 TokenEnd=4 NamedEntityTag=NUMBER NormalizedNamedEntityTag=4.0 EntityType=NUMBER SentenceIndex=0 EntityMentionIndex=3 CanonicalEntityMentionIndex=3 NamedEntityTagProbs={NUMBER=-1.0}]",
        "[Text=five CharacterOffsetBegin=19 CharacterOffsetEnd=23 Tokens=[five-5] TokenBegin=4 TokenEnd=5 NamedEntityTag=NUMBER NormalizedNamedEntityTag=5.0 EntityType=NUMBER SentenceIndex=0 EntityMentionIndex=4 CanonicalEntityMentionIndex=4 NamedEntityTagProbs={NUMBER=-1.0}]"
    };
    compareMentions("testNumbers", expectedMentions, mentions);
  }

  @Test
  public void testPercent() {
    Annotation doc = createDocument("12% 13%");
    EntityMentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    String[] expectedMentions = {
        "[Text=12% CharacterOffsetBegin=0 CharacterOffsetEnd=3 Tokens=[12-1, %-2] TokenBegin=0 TokenEnd=2 NamedEntityTag=PERCENT NormalizedNamedEntityTag=%12.0 EntityType=PERCENT SentenceIndex=0 EntityMentionIndex=0 CanonicalEntityMentionIndex=0 NamedEntityTagProbs={PERCENT=0.998762954773062}]",
        "[Text=13% CharacterOffsetBegin=4 CharacterOffsetEnd=7 Tokens=[13-3, %-4] TokenBegin=2 TokenEnd=4 NamedEntityTag=PERCENT NormalizedNamedEntityTag=%13.0 EntityType=PERCENT SentenceIndex=0 EntityMentionIndex=1 CanonicalEntityMentionIndex=1 NamedEntityTagProbs={PERCENT=0.998762954773062}]"
    };
    compareMentions("testPercent", expectedMentions, mentions);
  }

  @Test
  public void testNewsText() {
    Annotation doc = createDocument("Duke of Cambridge, Prince William, unveiled a new China Center in the University of Oxford Monday.\n" +
        "Covering an area nearly 5,500 square meters, the new Dickson Poon University of Oxford China Center in St Hugh's College cost about 21 million pounds.\n" +
        "Dickson Poon, a philanthropist from Hong Kong, China, is the one of the major donors of the center, who contributed 10 million British pounds (16.14 million U.S. dollars).");

    EntityMentionsAnnotator annotator = getMentionsAnnotator();

    annotator.annotate(doc);
    List<CoreMap> mentions = doc.get(CoreAnnotations.MentionsAnnotation.class);
    // TODO: "Duke of Cambridge" should be one mention. 
    // TODO: Not sure if should get "Prince William" rather than just "William", but going with the flow.
    // TODO: "nearly 5,500 square meters"? "10 million British pounds", "16.14 million U.S. dollars"
    // TODO: "China Center should definitely be an organization!
    String[] expectedMentions = {
        "[Text=Duke CharacterOffsetBegin=0 CharacterOffsetEnd=4 Tokens=[Duke-1] TokenBegin=0 TokenEnd=1 NamedEntityTag=PERSON EntityType=PERSON SentenceIndex=0 EntityMentionIndex=0 CanonicalEntityMentionIndex=0 NamedEntityTagProbs={PERSON=0.6040365210265772}]",
        "[Text=Cambridge CharacterOffsetBegin=8 CharacterOffsetEnd=17 Tokens=[Cambridge-3] TokenBegin=2 TokenEnd=3 NamedEntityTag=LOCATION EntityType=LOCATION SentenceIndex=0 EntityMentionIndex=1 CanonicalEntityMentionIndex=1 NamedEntityTagProbs={LOCATION=0.9214556405684146}]",
        "[Text=William CharacterOffsetBegin=26 CharacterOffsetEnd=33 Tokens=[William-6] TokenBegin=5 TokenEnd=6 NamedEntityTag=PERSON EntityType=PERSON SentenceIndex=0 EntityMentionIndex=2 CanonicalEntityMentionIndex=2 NamedEntityTagProbs={PERSON=0.9737141642947746}]",
        "[Text=China Center CharacterOffsetBegin=50 CharacterOffsetEnd=62 Tokens=[China-11, Center-12] TokenBegin=10 TokenEnd=12 NamedEntityTag=LOCATION EntityType=LOCATION SentenceIndex=0 EntityMentionIndex=3 CanonicalEntityMentionIndex=3 NamedEntityTagProbs={LOCATION=0.4710276807642457}]",
        "[Text=University of Oxford CharacterOffsetBegin=70 CharacterOffsetEnd=90 Tokens=[University-15, of-16, Oxford-17] TokenBegin=14 TokenEnd=17 NamedEntityTag=ORGANIZATION EntityType=ORGANIZATION SentenceIndex=0 EntityMentionIndex=4 CanonicalEntityMentionIndex=4 NamedEntityTagProbs={ORGANIZATION=0.9995768696970976}]",
        "[Text=Monday CharacterOffsetBegin=91 CharacterOffsetEnd=97 Tokens=[Monday-18] TokenBegin=17 TokenEnd=18 NamedEntityTag=DATE NormalizedNamedEntityTag=XXXX-WXX-1 EntityType=DATE SentenceIndex=0 Timex=<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"XXXX-WXX-1\">Monday</TIMEX3> EntityMentionIndex=5 CanonicalEntityMentionIndex=5 NamedEntityTagProbs={DATE=0.7012320920477302}]",
        "[Text=5,500 CharacterOffsetBegin=123 CharacterOffsetEnd=128 Tokens=[5,500-5] TokenBegin=23 TokenEnd=24 NamedEntityTag=NUMBER NormalizedNamedEntityTag=~5500.0 EntityType=NUMBER SentenceIndex=1 EntityMentionIndex=6 CanonicalEntityMentionIndex=6 NamedEntityTagProbs={NUMBER=-1.0}]",
        "[Text=Dickson Poon University of Oxford China Center CharacterOffsetBegin=152 CharacterOffsetEnd=198 Tokens=[Dickson-11, Poon-12, University-13, of-14, Oxford-15, China-16, Center-17] TokenBegin=29 TokenEnd=36 NamedEntityTag=ORGANIZATION EntityType=ORGANIZATION SentenceIndex=1 EntityMentionIndex=7 CanonicalEntityMentionIndex=7 NamedEntityTagProbs={ORGANIZATION=0.9937322804561051}]",
        "[Text=St Hugh's College CharacterOffsetBegin=202 CharacterOffsetEnd=219 Tokens=[St-19, Hugh-20, 's-21, College-22] TokenBegin=37 TokenEnd=41 NamedEntityTag=ORGANIZATION EntityType=ORGANIZATION SentenceIndex=1 EntityMentionIndex=8 CanonicalEntityMentionIndex=8 NamedEntityTagProbs={ORGANIZATION=0.8819253990060462}]",
        "[Text=21 million pounds CharacterOffsetBegin=231 CharacterOffsetEnd=248 Tokens=[21-25, million-26, pounds-27] TokenBegin=43 TokenEnd=46 NamedEntityTag=MONEY NormalizedNamedEntityTag=~£2.1E7 EntityType=MONEY SentenceIndex=1 EntityMentionIndex=9 CanonicalEntityMentionIndex=9 NamedEntityTagProbs={MONEY=-1.0}]",
        "[Text=Dickson Poon CharacterOffsetBegin=250 CharacterOffsetEnd=262 Tokens=[Dickson-1, Poon-2] TokenBegin=47 TokenEnd=49 NamedEntityTag=PERSON EntityType=PERSON SentenceIndex=2 EntityMentionIndex=10 CanonicalEntityMentionIndex=10 NamedEntityTagProbs={PERSON=0.9973724447807097}]",
        "[Text=Hong Kong CharacterOffsetBegin=286 CharacterOffsetEnd=295 Tokens=[Hong-7, Kong-8] TokenBegin=53 TokenEnd=55 NamedEntityTag=LOCATION EntityType=LOCATION SentenceIndex=2 EntityMentionIndex=11 CanonicalEntityMentionIndex=11 NamedEntityTagProbs={LOCATION=0.9988976914526051}]",
        "[Text=China CharacterOffsetBegin=297 CharacterOffsetEnd=302 Tokens=[China-10] TokenBegin=56 TokenEnd=57 NamedEntityTag=LOCATION EntityType=LOCATION SentenceIndex=2 EntityMentionIndex=12 CanonicalEntityMentionIndex=12 NamedEntityTagProbs={LOCATION=0.9976055940327417}]",
        "[Text=one CharacterOffsetBegin=311 CharacterOffsetEnd=314 Tokens=[one-14] TokenBegin=60 TokenEnd=61 NamedEntityTag=NUMBER NormalizedNamedEntityTag=1.0 EntityType=NUMBER SentenceIndex=2 EntityMentionIndex=13 CanonicalEntityMentionIndex=13 NamedEntityTagProbs={NUMBER=-1.0}]",
        "[Text=10 million CharacterOffsetBegin=366 CharacterOffsetEnd=376 Tokens=[10-25, million-26] TokenBegin=71 TokenEnd=73 NamedEntityTag=NUMBER NormalizedNamedEntityTag=1.0E7 EntityType=NUMBER SentenceIndex=2 EntityMentionIndex=14 CanonicalEntityMentionIndex=14 NamedEntityTagProbs={NUMBER=-1.0}]",
        "[Text=British CharacterOffsetBegin=377 CharacterOffsetEnd=384 Tokens=[British-27] TokenBegin=73 TokenEnd=74 NamedEntityTag=MISC EntityType=MISC SentenceIndex=2 EntityMentionIndex=15 CanonicalEntityMentionIndex=15 NamedEntityTagProbs={MISC=0.9999989541925414}]",
        "[Text=16.14 million CharacterOffsetBegin=393 CharacterOffsetEnd=406 Tokens=[16.14-30, million-31] TokenBegin=76 TokenEnd=78 NamedEntityTag=NUMBER NormalizedNamedEntityTag=1.614E7 EntityType=NUMBER SentenceIndex=2 EntityMentionIndex=16 CanonicalEntityMentionIndex=16 NamedEntityTagProbs={NUMBER=-1.0}]",
        "[Text=U.S. CharacterOffsetBegin=407 CharacterOffsetEnd=411 Tokens=[U.S.-32] TokenBegin=78 TokenEnd=79 NamedEntityTag=LOCATION EntityType=LOCATION SentenceIndex=2 EntityMentionIndex=17 CanonicalEntityMentionIndex=17 NamedEntityTagProbs={LOCATION=0.7836362279773935}]"
    };
        
    compareMentions("testNewsText", expectedMentions, mentions);
  }

}
