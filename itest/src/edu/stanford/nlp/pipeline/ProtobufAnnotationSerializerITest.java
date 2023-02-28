package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Test some specific aspects of the ProtobufAnnotationSerializer
 *
 * @author John Bauer
 */
public class ProtobufAnnotationSerializerITest {
  public static Annotation annotateDocParse(String text) {
    Properties props = PropertiesUtils.asProperties("annotators", "tokenize,pos,parse");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);
    return doc;
  }

  public static void checkTokensLeavesSameObjects(Annotation doc) {
    CoreMap sentence = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    assertNotNull(sentence);
    assertNotNull(tree);

    List<Label> leaves = tree.yield();
    List<CoreLabel> words = sentence.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(leaves.size(), words.size());
    for (int i = 0; i < words.size(); ++i) {
      // not assertEquals because we want object equality
      assertSame(leaves.get(i), words.get(i));
    }
  }

  @Test
  public void testSentiment() {
    String text = "Jennifer has blue skin";
    Annotation doc = annotateDocParse(text);
    checkTokensLeavesSameObjects(doc);

    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    Annotation converted = serializer.fromProto(serializer.toProto(doc));
    checkTokensLeavesSameObjects(converted);
  }
}

