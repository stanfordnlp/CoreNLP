package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
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
  protected static StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,parse,natlog,openie");
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
      if (extraction.toString().equals(expected)) {
        found = true;
      }
    }
    assertTrue("The extraction '" + expected + "' was not found in '" + text + "'", found);
  }

  public void assertExtracted(Set<String> expected, String text) {
    Collection<RelationTriple> extractions = annotate(text).get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
    Set<String> guess = extractions.stream().map(RelationTriple::toString).collect(Collectors.toSet());
    assertEquals(StringUtils.join(expected.stream().sorted(), "\n"), StringUtils.join(guess.stream().sorted(), "\n"));
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
  public void testExtractionsObamaWikiOne() {
    assertExtracted(new HashSet<String>() {{
      add("Barack Hussein Obama II\tis 44th and current President of\tUnited States");
      add("Barack Hussein Obama II\tis 44th President of\tUnited States");
      add("Barack Hussein Obama II\tis current President of\tUnited States");
      add("Barack Hussein Obama II\tis President of\tUnited States");
    }}, "Barack Hussein Obama II is the 44th and current President of the United States, and the first African American to hold the office.");
  }

  @Test
  public void testExtractionsObamaWikiTwo() {
    assertExtracted(new HashSet<String>() {{
      add("Obama\tis graduate of\tColumbia University");
      add("Obama\tis graduate of\tHarvard Law School");
      add("Obama\tborn in\tHonolulu Hawaii");
      add("he\tserved as\tpresident of the Harvard Law Review");
      add("he\tserved as\tpresident");
    }}, "Born in Honolulu, Hawaii, Obama is a graduate of Columbia University and Harvard Law School, where he served as president of the Harvard Law Review");
  }
}
