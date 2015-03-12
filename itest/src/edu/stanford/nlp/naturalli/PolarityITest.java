package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.*;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;


/**
 * A test to make sure {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator} marks the right polarities for the tokens
 * in the sentence.
 *
 * @author Gabor Angeli
 */
public class PolarityITest {

  private static final StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,parse");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
  }});

  static {
    pipeline.addAnnotator(new NaturalLogicAnnotator());
  }

  @SuppressWarnings("unchecked")
  private Polarity[] annotate(String text) {
    Annotation ann = new Annotation(text);
    pipeline.annotate(ann);
    List<CoreLabel> tokens = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class);
    Polarity[] polarities = new Polarity[tokens.size()];
    for (int i = 0; i < tokens.size(); ++i) {
      polarities[i] = tokens.get(i).get(NaturalLogicAnnotations.PolarityAnnotation.class);
    }
    return polarities;
  }

  @Test
  public void allCatsHaveTails() {
    Polarity[] p = annotate("all cats have tails");
    assertTrue(p[0].isUpwards());
    assertTrue(p[1].isDownwards());
    assertTrue(p[2].isUpwards());
    assertTrue(p[3].isUpwards());
  }

  @Test
  public void someCatsDontHaveTails() {
    Polarity[] p = annotate("some cats don't have tails");
    assertTrue(p[0].isUpwards());
    assertTrue(p[1].isUpwards());
    assertTrue(p[2].isUpwards());
    assertTrue(p[3].isUpwards());
    assertTrue(p[4].isDownwards());
    assertTrue(p[5].isDownwards());
  }

}
