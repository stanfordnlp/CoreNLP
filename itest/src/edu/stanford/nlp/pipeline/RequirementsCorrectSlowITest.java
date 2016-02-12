package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TypesafeMap;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A test to ensure that the declared requirements of an annotator are correct,
 * at least in its default invocation.
 */
public class RequirementsCorrectSlowITest {

  private String dummyString = "Joe said his car would be fixed on June 7, 1987  after 2 hours of work. He also said this document is nonsense. Joe was born in Hawaii.";


  /**
   * Ensures that the given sequence of annotators actually abides by its stated requirements,
   * at least in a default invocation of CoreNLP.
   * @param annotators
   */
  @SuppressWarnings("unchecked")
  private void testAnnotatorSequence(List<String> annotators) {
    final Set<Class<? extends TypesafeMap.Key<?>>> keysRead = new HashSet<>();
    ArrayCoreMap.listener = keysRead::add;
    Annotation ann = new Annotation(dummyString);

    for (int annotatorI = 0; annotatorI < annotators.size(); ++annotatorI) {
      keysRead.clear();
      String annotatorName = annotators.get(annotatorI);
      System.err.println("Running " + annotatorName);
      StanfordCoreNLP corenlp = new StanfordCoreNLP(new Properties(){{
        setProperty("annotators", annotatorName);
        setProperty("enforceRequirements", "false");
      }});
      corenlp.annotate(ann);
      Annotator annotator = StanfordCoreNLP.getExistingAnnotator(annotatorName);
      assertNotNull(annotator);

      Set declared = annotator.requires();
      Set used = new HashSet<>(keysRead);
      used.removeAll(annotator.requirementsSatisfied());
      if (annotatorI > 0) {
        if (!declared.equals(used)) {
          // Failure to declare a requirement!
          System.err.println("ANNOTATOR " + annotatorName);
          System.err.println("Used but not declared:");
          for (Object key : CollectionUtils.diffAsSet(used, declared)) {
            System.err.println("  " + key);
          }
          System.err.println("Declared but not Used:");
          for (Object key : CollectionUtils.diffAsSet(declared, used)) {
            System.err.println("  " + key);
          }
        }
        assertEquals(declared, used);
      }
    }

    // Run enforcing requirements
    StanfordCoreNLP corenlp = new StanfordCoreNLP(new Properties(){{
      setProperty("annotators", StringUtils.join(annotators, ","));
    }});
    corenlp.annotate(ann);
  }


  @Test
  public void testDefaultPipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize", "ssplit", "pos", "lemma", "ner", "gender", "parse", "mention", "coref"));
  }

  @Test
  public void testDepparsePipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize", "ssplit", "pos", "depparse"));
  }

  @Test
  public void testQuotePipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize","ssplit","quote"));
  }

  @Test
  public void testTrueCasePipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize","ssplit","pos","lemma","truecase"));
  }

  @Test
  public void testOpenIEPipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize","ssplit","pos","lemma","depparse","natlog","openie"));
  }

}
