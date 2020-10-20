package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NERConfidenceITest {

  @Test
  public void testDefaultPipeline() {
    // example string
    String example = "ABCDEFG Smith lives on Krypton in June 2012.  " +
        "Tom Johnson paid $500 for it.  Joe Smith was born in California.  He works for the EU.";
    // make pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("ner.combinationMode", "HIGH_RECALL");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // annotate
    Annotation ann = new Annotation(example);
    pipeline.annotate(ann);
    // print results
    System.err.println("Text: "+example);
    System.err.println("---");
    System.err.println("entities and confidences");
    // check token values
    List<CoreLabel> tokens = ann.get(CoreAnnotations.TokensAnnotation.class);
    for (CoreLabel token : tokens) {
      System.err.println(token.word() + "\t" + token.ner() + "\t" + token.nerConfidence());
    }
    // token index = 0 text: ABCDEFG ner label: PERSON ner confidence: 0.7986500069491159
    assertEquals("ABCDEFG",tokens.get(0).word());
    assertEquals("PERSON", tokens.get(0).ner());
    assertEquals(0.7303923784928416, tokens.get(0).nerConfidence().get("PERSON").doubleValue(), .001);
    // token index = 4 text: Krypton ner label: LOCATION ner confidence: 0.6698305229562858
    assertEquals("Krypton",tokens.get(4).word());
    assertEquals("LOCATION", tokens.get(4).ner());
    assertEquals(0.6638043455561479, tokens.get(4).nerConfidence().get("LOCATION").doubleValue(), .001);
    // token index = 7 text: 2012 ner label: DATE ner confidence: 0.9733532843140376
    assertEquals("2012",tokens.get(7).word());
    assertEquals("DATE", tokens.get(7).ner());
    assertEquals(0.9756507027537803, tokens.get(7).nerConfidence().get("DATE").doubleValue(), .001);
    // token index = 8 text: . ner label: O ner confidence: O=0.9999996795616342
    assertEquals(".",tokens.get(8).word());
    assertEquals("O", tokens.get(8).ner());
    assertEquals(0.9999996795616342, tokens.get(8).nerConfidence().get("O").doubleValue(), .001);
    // token index = 9 text: Tom ner label: PERSON ner confidence: 0.9971518907102458
    assertEquals("Tom",tokens.get(9).word());
    assertEquals("PERSON", tokens.get(9).ner());
    assertEquals(0.9971518907102458, tokens.get(9).nerConfidence().get("PERSON").doubleValue(), .001);
    // token index = 22 text: California ner label: LOCATION ner confidence: 0.9999100454498762
    assertEquals("California",tokens.get(22).word());
    assertEquals("STATE_OR_PROVINCE", tokens.get(22).ner());
    assertEquals(0.9999100454498762, tokens.get(22).nerConfidence().get("LOCATION").doubleValue(), .001);
    // check entity mention values

    // ABCDEFG Smith
    List<CoreMap> entityMentions = ann.get(CoreAnnotations.MentionsAnnotation.class);
    assertEquals("ABCDEFG Smith", entityMentions.get(0).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("PERSON", entityMentions.get(0).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    assertEquals(0.7303923784928416,
        entityMentions.get(0).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class).get("PERSON").doubleValue(),
        .001);
    // Krypton
    assertEquals("Krypton", entityMentions.get(1).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("LOCATION", entityMentions.get(1).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    assertEquals(0.6638043455561479,
        entityMentions.get(1).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class).get("LOCATION").doubleValue(),
        .001);
    // June 2012
    assertEquals("June 2012", entityMentions.get(2).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("DATE", entityMentions.get(2).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    assertEquals(0.9756507027537803,
        entityMentions.get(2).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class).get("DATE").doubleValue(),
        .001);
    // Tom Johnson
    assertEquals("Tom Johnson", entityMentions.get(3).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("PERSON", entityMentions.get(3).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    assertEquals(0.9973978964285446,
        entityMentions.get(3).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class).get("PERSON").doubleValue(),
        .001);
    // $500
    assertEquals("$500", entityMentions.get(4).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("MONEY", entityMentions.get(4).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    assertEquals(0.9976584316476499,
        entityMentions.get(4).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class).get("MONEY").doubleValue(),
        .001);
    // Joe Smith
    assertEquals("Joe Smith", entityMentions.get(5).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("PERSON", entityMentions.get(5).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    assertEquals(0.9994082742139784,
        entityMentions.get(5).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class).get("PERSON").doubleValue(),
        .001);
    // California
    assertEquals("California", entityMentions.get(6).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("STATE_OR_PROVINCE", entityMentions.get(6).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    assertEquals(0.9992931870292338,
        entityMentions.get(6).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class).get("LOCATION").doubleValue(),
        .001);
    // EU
    assertEquals("EU", entityMentions.get(7).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("ORGANIZATION", entityMentions.get(7).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    assertEquals(0.7634907668729419,
        entityMentions.get(7).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class).get("ORGANIZATION").doubleValue(),
        .001);
  }

}
