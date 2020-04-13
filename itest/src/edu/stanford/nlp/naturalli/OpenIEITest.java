package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Sets;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Test the natural logic OpenIE extractor at {@link edu.stanford.nlp.naturalli.OpenIE}.
 *
 * @author Gabor Angeli
 */
public class OpenIEITest {
  protected static StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties() {{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,natlog,openie");

    setProperty("openie.splitter.threshold", "0.25");
    setProperty("openie.ignoreaffinity", "false");
    setProperty("openie.max_entailments_per_clause", "1000");
    setProperty("openie.triple.strict", "true");
//    setProperty("openie.splitter.model", "/home/gabor/tmp/clauseSearcher.ser.gz");

    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
    setProperty("enforceRequirements", "true");
  }});

  public CoreMap annotate(String text) {
    Annotation ann = new Annotation(text);
    pipeline.annotate(ann);
    return ann.get(CoreAnnotations.SentencesAnnotation.class).get(0);
  }

  public void assertExtracted(String expected, String text) {
    boolean found = false;
    Collection<RelationTriple> extractions = annotate(text).get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
    for (RelationTriple extraction : extractions) {
      if (extraction.toString().equals("1.0\t" + expected)) {
        found = true;
      }
    }
    assertTrue("The extraction (" + expected.replace("\t", "; ") + ") was not found in '" + text + "'", found);
  }

  public void assertExtracted(Set<String> expectedSet, String text) {
    Collection<RelationTriple> extractions = annotate(text).get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
    Set<String> actual = 
      extractions.stream().map(x -> x.toString().substring(x.toString().indexOf("\t") + 1).toLowerCase())
      .collect(Collectors.toSet());
    Set<String> expected = expectedSet.stream().map(String::toLowerCase).collect(Collectors.toSet());
    Sets.assertEquals(expected, actual, "expected", "actual", true, 
                      () -> "Unexpected results processing " + text);
  }

  public void assertEntailed(String expected, String text) {
    boolean found = false;
    Collection<SentenceFragment> extractions = annotate(text).get(NaturalLogicAnnotations.EntailedSentencesAnnotation.class);
    for (SentenceFragment extraction : extractions) {
      if (extraction.toString().equals(expected)) {
        found = true;
      }
    }
    assertTrue("The sentence '" + expected + "' was not entailed from '" + text + "'", found);
  }

  // TODO: tests to add:
  //
  // in addition to
  //   "he did X and did Y"
  // such as ObamaWikiFour
  //   he did X, did Y, and did Z
  //
  // a few more negation examples:
  //   each of the tests in RelationTripleSegmenter could be tested,
  //   and their negations tested as well
  //
  // some time examples:
  //   this may actually require some different detection of time phrases
  //   for example, "on Thursday" is obl:on, "in 2019" is obl:in,
  //   "from 1992 to 2004" is obl:from, and finally "this summer" is obl:tmod
  //   so ultimately we probably need to incorporate sutime
  //   in order to get good results
  //
  // some of the tests from RelationTripleSegmenterTest
  //   in particular, "Tom and Jerry were fighting" is not working as expected
  //   see testTomJerry for more information.  basically, it's impossible to
  //   distinguish "fighting" from other verbs
  
  @Test
  public void testAnnotatorRuns() {
    annotate("all cats have tails");
  }

  @Test
  public void testBasicEntailments() {
    assertEntailed("some cats have tails", "some blue cats have tails");
    assertEntailed("blue cats have tails", "some blue cats have tails");
    assertEntailed("cats have tails",      "some blue cats have tails");
  }

  @Test
  public void testBasicExtractions() {
    assertExtracted("cats\thave\ttails", "some cats have tails");
  }

  @Test
  public void testPaperExamples() {
//    assertExtracted("Fish\tlike to\tswim", "Fish like to swim");  // Parse is persistently broken

//    assertExtracted("Tom\tfighting\tJerry", "Tom and Jerry are fighting.");  // Collapsed CC Processed tree incorrectly adds nsubj from fighting->jrry
    assertExtracted("cats\tis with\ttails", "There are cats with tails.");
    assertExtracted("IBM\thas\tresearch group", "IBM's research group.");
    assertExtracted("rabbits\teat\tvegetables", "All rabbits eat vegetables.");
  }

  @Test
  public void testOtherExamples() {
    // Preconj (but, parser currently fails)
//    assertExtracted("Mary\tis\tbeautiful", "Mary is both beautiful and smart.");
//    assertExtracted(Collections.EMPTY_SET, "Mary is neither beautiful and smart.");
  }

  @Test
  public void testExtractionsGeorgeBoyd() {
    assertExtracted(new HashSet<String>() {{
      add("George Boyd\tjoined on\t21 february 2013");
      add("George Boyd\tjoined for\tremainder");
      add("George Boyd\tjoined for\tremainder of season");
      add("George Boyd\tjoined on\tloan");
      add("George Boyd\tjoined on\tloan from peterborough united");
    }}, "On 21 February 2013 George Boyd joined on loan from Peterborough United for the remainder of the season.");
  }

  @Test
  public void testExtractionsObamaWikiOne() {
    assertExtracted(new HashSet<String>() {{
      add("Barack Hussein Obama II\tis 44th and current President of\tUnited States");
      add("Barack Hussein Obama II\tis 44th President of\tUnited States");
//      add("Barack Hussein Obama II\tis current President of\tUnited States");
      add("Barack Hussein Obama II\tis President of\tUnited States");
      add("Barack Hussein Obama II\tis\tPresident");
//      add("Barack Hussein Obama II\tis\tcurrent President");
      add("Barack Hussein Obama II\tis\t44th President");
    }}, "Barack Hussein Obama II is the 44th and current President of the United States, and the first African American to hold the office.");
  }

  @Test
  @Ignore  // TODO(gabor) dependency parse error.
  public void testExtractionsObamaWikiTwo() {
    assertExtracted(new HashSet<String>() {{
      add("Obama\tis graduate of\tColumbia University");
//      add("Obama\tis graduate of\tHarvard Law School");
      add("Obama\tborn in\tHawaii");
      add("Obama\tborn in\tHonolulu");
      add("he\tserved as\tpresident of Harvard Law Review");
      add("he\tserved as\tpresident");
      add("he\tpresident of\tHarvard Law Review");
      add("Obama\tis\tgraduate");
    }}, "Born in Honolulu, Hawaii, Obama is a graduate of Columbia University and Harvard Law School, where he served as president of the Harvard Law Review.");
  }

  @Test
  @Ignore  // TODO(gabor) why does this fail? [2016-06-07]
  public void testExtractionsObamaWikiThree() {
    assertExtracted(new HashSet<String>() {{
      add("He\twas\tcommunity organizer in Chicago");
      add("He\twas\tcommunity organizer");
//      add("He\tearning\tlaw degree");
      add("He\tearning\this law degree");
      add("community organizer\tis in\tChicago");
    }}, "He was a community organizer in Chicago before earning his law degree.");
  }

  @Test
  public void testExtractionsObamaWikiFour() {
    assertExtracted(new HashSet<String>() {{
      add("He\tworked as\tcivil rights attorney");
      add("He\tworked as\trights attorney");
      add("He\ttaught\tconstitutional law");
      add("He\ttaught\tlaw");
      add("He\ttaught law at\tUniversity of Chicago Law School");
      // currently this one isn't found because the underlying parse
      // is missing a tmod
      // add("He\ttaught law at_time\tLaw School");
      
      // add("He\ttaught law at\tUniversity of Chicago Law School from 1992");
      add("He\ttaught law at\tUniversity");
      add("He\ttaught law from\t1992 to 2004");  // shouldn't be here, but sometimes appears?
    }}, "He worked as a civil rights attorney and taught constitutional law at the University of Chicago Law School from 1992 to 2004.");
  }

  //@Test
  public void testExtractionsObamaWikiFive() {
    assertExtracted(new HashSet<String>() {{
      add("He\tserved\tthree terms");
      // note[gabor] Should get these
      //      add("He\trepresenting\t13th District in Illinois Senate");
      //      add("He\trepresenting\t13th District");
      //      add("He\trepresenting\tDistrict in Illinois Senate");
      //      add("He\trepresenting\tDistrict");
      //      add("He\trunning unsuccessfully for\tUnited States House of Representatives in 2000");
      add("13th district\tis in\tIllinois Senate");
      add("He\trunning unsuccessfully for\tUnited States House of Representatives");
      add("He\trunning unsuccessfully for\tUnited States House");
      //      add("He\trunning for\tUnited States House of Representatives in 2000");
      add("He\trunning for\tUnited States House of Representatives");
      add("He\trunning for\tUnited States House");
      add("He\trunning in\t2000");
      add("He\trunning unsuccessfully in\t2000");
    }}, "He served three terms representing the 13th District in the Illinois Senate from 1997 to 2004, running unsuccessfully for the United States House of Representatives in 2000.");
  }

  @Test
  public void testExtractionsObamaWikiSix() {
    assertExtracted(new HashSet<String>() {{
      add("He\tdefeated\tRepublican nominee John McCain");
      // The exact mention sometimes changes as the model changes...
      //add("He\tdefeated\tnominee John McCain");
      //add("He\tdefeated nominee John McCain in\telection");
      //add("He\tdefeated nominee John McCain in\tgeneral election");
      add("He\tdefeated Republican nominee John McCain in\telection");
      add("He\tdefeated Republican nominee John McCain in\tgeneral election");
      add("He\twas\tinaugurated as president on January 20 2009");
      add("He\twas inaugurated as\tpresident");
      add("He\twas\tinaugurated");
      add("He\twas inaugurated on\tJanuary 20 2009");
      // note[gabor] these are wrong!
//      add("nominee john mccain\twas\tinaugurated");
//      add("nominee john mccain\twas inaugurated as\tpresident");
//      add("nominee john mccain\twas inaugurated as\tpresident on january 20 2009");
//      add("Republican nominee John McCain\twas\tinaugurated");
//      add("Republican nominee John McCain\twas inaugurated as\tpresident");
//      add("Republican nominee John McCain\twas inaugurated as\tpresident on january 20 2009");
      // note[gabor] end wrong extractions
    }}, "He then defeated Republican nominee John McCain in the general election, and was inaugurated as president on January 20, 2009.");
  }

  @Test
  public void testThereIsNoDoubt() {
    assertExtracted(new HashSet<String>() {{
      add("I\tam\tgreat");
    }}, "There is no doubt that I am great.");
    assertExtracted(new HashSet<String>() {{
      add("cats\thave\ttails");
    }}, "There is no doubt that cats have tails.");
    assertExtracted(new HashSet<String>() {{
    }}, "There is doubt that cats have tails.");
  }


  @Test
  public void testChessIsNotAPhysicalSport() {
    assertExtracted(new HashSet<String>() {{
      add("Chess\tis\tphysical");
      add("Chess\tis\tphysical sport");
      add("Chess\tis\tsport");
    }}, "Chess is a physical sport");
    assertExtracted(new HashSet<String>() {{
    }}, "Chess is not a physical sport");
  }

  @Test
  public void testSara() {
    assertExtracted(new HashSet<String>() {{
      add("John\tdid see\tSara");
    }}, "John did see Sara");
    assertExtracted(new HashSet<String>() {{
    }}, "John did not see Sara");  // the "not" should reject the relation
  }

  @Test
  public void testTomJerry() {
    assertExtracted(new HashSet<String>() {{
      add("Tom\thave\ttails");
      add("Jerry\thave\ttails");
    }}, "Tom and Jerry have tails");
    assertExtracted(new HashSet<String>() {{
      add("Tom\tare\tfriends");
      add("Jerry\tare\tfriends");
    }}, "Tom and Jerry are friends");
    // TODO:
    //   given the current system, it is almost impossible to
    //   distinguish the following examples
    //
    //   we would want
    //     "Tom, fighting, Jerry"
    //   and
    //     "Tom, is, crying"
    //     "Jerry, is, crying"
    //
    //   Unfortunately, the parses come back as follows:
    //
    //    -> crying/VBG (root)
    //      -> Tom/NNP (nsubj)
    //        -> Jerry/NNP (conj:and)
    //          -> and/CC (cc)
    //      -> Jerry/NNP (nsubj)
    //      -> are/VBP (aux)
    //
    //    -> fighting/VBG (root)
    //      -> Tom/NNP (nsubj)
    //        -> Jerry/NNP (conj:and)
    //          -> and/CC (cc)
    //      -> Jerry/NNP (nsubj)
    //      -> were/VBD (aux)
    //
    // so...  ¯\_(ツ)_/¯
    //
    //assertExtracted(new HashSet<String>() {{
    //}}, "Tom and Jerry are crying");
    //assertExtracted(new HashSet<String>() {{
    //}}, "Tom and Jerry are fighting");
  }
  
  @Test
  public void dummyTest() {
    assertTrue(true);
  }

}
