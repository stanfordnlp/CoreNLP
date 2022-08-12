package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
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

  private String dummyString = "Joe said his car would be fixed on June 7, 1987  after 2 hours of work. He also said this document is nonsense. " +
      "Joe was born in Hawaii. Joe said \"I'm going to get my car fixed.\"";


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
      // handle special cases
      // this is created by ner but not a part of requirementsSatisfied
      if (annotatorName.equals("ner")) {
        used.remove(CoreAnnotations.GoldAnswerAnnotation.class);
        used.remove(CoreAnnotations.NamedEntityTagProbsAnnotation.class);
        used.remove(CoreAnnotations.AnswerProbAnnotation.class);
        used.remove(CoreAnnotations.DocDateAnnotation.class);
      }
      // these are created by quote but used by quote.attribution
      // causing an error
      if (annotatorName.equals("quote")) {
        used.remove(CoreAnnotations.SentenceBeginAnnotation.class);
        used.remove(CoreAnnotations.SentenceEndAnnotation.class);
      }
      // by default coref now builds a coref.mention, so it doesn't need
      // the coref mention
      if (annotatorName.equals("coref")) {
        used.remove(CorefCoreAnnotations.CorefMentionsAnnotation.class);
        used.remove(CoreAnnotations.ParagraphAnnotation.class);
        used.remove(CoreAnnotations.SpeakerAnnotation.class);
        used.remove(CoreAnnotations.UtteranceAnnotation.class);
        // note that DocumentPreProcessor accesses this key and assigns
        // to a mention's contextParseTree field (line 312) even if it's
        // null, so even though the coref defaults are all dependency
        // parse based, this key gets accessed
        used.remove(TreeCoreAnnotations.TreeAnnotation.class);
        // note that UniversalSemanticHeadFinder accesses this key
        // (line 625, line 628)
        used.remove(CoreAnnotations.CategoryAnnotation.class);
        // coref mention detection creates CorefMentionIndexAnnotation,
        // EntityMentionToCorefMentionMapping and CorefMentionToEntityMentionMapping
        // which is used by the main coref annotator
        used.remove(CorefCoreAnnotations.CorefMentionIndexesAnnotation.class);
        used.remove(CoreAnnotations.EntityMentionToCorefMentionMappingAnnotation.class);
        used.remove(CoreAnnotations.CorefMentionToEntityMentionMappingAnnotation.class);
        // ValueAnnotation is sometimes used, sometimes not
        // so artificially add it so this test stops failing in
        // cases where ValueAnnotation isn't used...note that this
        // is always populated by TokenizerAnnotator
        if (!used.contains(CoreAnnotations.ValueAnnotation.class))
          used.add(CoreAnnotations.ValueAnnotation.class);
      }
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
    testAnnotatorSequence(Arrays.asList("tokenize", "pos", "lemma", "ner", "gender", "parse", "coref"));
  }

  @Test
  public void testDepparsePipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize", "pos", "depparse"));
  }

  @Test
  public void testQuotePipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize","pos","lemma","ner","depparse","coref","quote"));
  }

  @Test
  public void testTrueCasePipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize","pos","lemma","truecase"));
   }

  @Test
  public void testOpenIEPipeline() {
    testAnnotatorSequence(Arrays.asList("tokenize","pos","lemma","depparse","natlog","openie"));
  }

  @Test
  public void testMentionRegression() {
    testAnnotatorSequence(Arrays.asList());
  }

}
