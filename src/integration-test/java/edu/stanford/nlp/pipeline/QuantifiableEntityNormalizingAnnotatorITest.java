package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

/**
 *
 * @author mcdm
 */
public class QuantifiableEntityNormalizingAnnotatorITest {

  private static AnnotationPipeline pipeline;

  @Before
  public void setUp() throws Exception {
    synchronized(QuantifiableEntityNormalizingAnnotatorITest.class) {
      Properties props = new Properties();
      props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
      pipeline = new StanfordCoreNLP(props);
    }
  }

  @Test
  public void testQuantifiableEntityNormalizingAnnotator() {
    Annotation document = new Annotation(text);
    pipeline.annotate(document);

    int i = 0;
    for (CoreMap sentence: document.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        for (CoreLabel token : tokens) {
          System.out.println(token.get(CoreAnnotations.TextAnnotation.class) + ": " + token.get(CoreAnnotations.NamedEntityTagAnnotation.class) + ", " + token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
        }
      for (CoreLabel token : tokens) {
        String normalization = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
        if (normalization != null) {
          Assert.assertEquals(answer_text[i], token.get(CoreAnnotations.OriginalTextAnnotation.class));
          Assert.assertEquals(answer_time[i], normalization);
          i++;
        }
      }
    }
    Assert.assertEquals(answer_text.length, i);
    Assert.assertEquals(answer_time.length, i);
  }

  static final String text =
          "On January 3 1980, Ellinais used the 2nd century A.D. temple of Zeus in Athens to stage the first known ceremony of the kind since the late 4th century.";

  /* For the record: values without SUTime
  static final String[] answer = {
      "19800103","19800103","19800103",  // same normalization for every token in the entity
      "2*****02","2*****02",  // actually wrong!  it should catch A.D. as well
      "1.0",
      "******04","******04","******04","******04", // TODO: was "4*****04". why?
  };
  */
  // With SUTime
  private static final String[] answer_text = {
    "January","3","1980",  // same normalization for every token in the entity
    "the","2nd", "century", "A.D.",
    "first",
    "the","late", "4th",  "century"
  };

  private static final String[] answer_time = {
    "1980-01-03","1980-01-03","1980-01-03",  // same normalization for every token in the entity
    "01XX","01XX", "01XX", "01XX",
    "1.0",
    //"P100Y-#4", "P100Y-#4", "P100Y-#4", "P100Y-#4",
    "03XX","03XX", "03XX",  "03XX"
  };

}

