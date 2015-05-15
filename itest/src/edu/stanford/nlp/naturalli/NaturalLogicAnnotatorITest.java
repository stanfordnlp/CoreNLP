package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * A lightweight test to makes sure the annotator runs in the pipeline.
 * For more in-depth tests, see {@link edu.stanford.nlp.naturalli.OperatorScopeITest} and
 * {@link edu.stanford.nlp.naturalli.PolarityITest}.
 *
 * @author Gabor Angeli
 */
public class NaturalLogicAnnotatorITest {

  @Test
  public void testAnnotatorRuns() {
    // Run pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
      setProperty("annotators", "tokenize,ssplit,pos,lemma,parse,natlog");
      setProperty("ssplit.isOneSentence", "true");
      setProperty("tokenize.class", "PTBTokenizer");
      setProperty("tokenize.language", "en");
      setProperty("enforceRequirements", "false");
    }});
    Annotation ann = new Annotation("All cats have tails");
    pipeline.annotate(ann);

    // Check output
    List<CoreLabel> tokens = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class);
    assertTrue(tokens.get(0).containsKey(NaturalLogicAnnotations.OperatorAnnotation.class));
    assertTrue(tokens.get(0).get(NaturalLogicAnnotations.PolarityAnnotation.class).isUpwards());
    assertTrue(tokens.get(1).get(NaturalLogicAnnotations.PolarityAnnotation.class).isDownwards());
    assertTrue(tokens.get(2).get(NaturalLogicAnnotations.PolarityAnnotation.class).isUpwards());
    assertTrue(tokens.get(3).get(NaturalLogicAnnotations.PolarityAnnotation.class).isUpwards());
  }
}
