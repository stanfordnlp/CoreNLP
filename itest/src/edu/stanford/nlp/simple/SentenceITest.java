package edu.stanford.nlp.simple;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.Operator;
import edu.stanford.nlp.naturalli.OperatorSpec;
import edu.stanford.nlp.naturalli.Polarity;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * A test for {@link edu.stanford.nlp.simple.Sentence}, using the NLP models.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("unchecked")
public class SentenceITest {
  @Test
  public void testPOSTag() {
    assertEquals(
        new ArrayList <String>(){{ add("DT"); add("NN"); add("VBZ"); add("JJ"); add("."); }},
        new Sentence("The cat is blue.").posTags());
  }

  @Test
  public void testLemma() {
    assertEquals(
        new ArrayList <String>(){{ add("the"); add("cat"); add("be"); add("blue"); add("."); }},
        new Sentence("The cats are blue.").lemmas());
  }

  @Test
  public void testNER() {
    assertEquals(
        new ArrayList <String>(){{ add("PERSON"); add("PERSON"); add("O"); add("O"); add("O"); add("LOCATION"); add("LOCATION"); add("O"); }},
        new Sentence("George Bush lives in the United States.").ners());
  }

  @Test
  public void testParse() {
    assertEquals("(ROOT (S (NP (DT The) (NN cat)) (VP (VBZ is) (ADJP (JJ blue))) (. .)))",
        new Sentence("The cat is blue.").parse().pennString().replaceAll("\n", " ").replaceAll("\\s+", " ").trim());
  }

  @Test
  public void testNatlogOperators() {
    Sentence sentence = new Sentence("All cats have tails.");
    List<Optional<OperatorSpec>> operators = sentence.operators();
    assertTrue(operators.get(0).isPresent());
    assertTrue(sentence.operatorAt(0).isPresent());
    assertFalse(operators.get(1).isPresent());
    assertFalse(sentence.operatorAt(1).isPresent());

    assertEquals(1, sentence.operatorsNonempty().size());

    assertEquals(Operator.ALL, sentence.operatorsNonempty().get(0).instance);
  }

  @Test
  public void testNatlogPolarities() {
    Sentence sentence = new Sentence("All cats have tails.");
    List<Polarity> polarities = sentence.natlogPolarities();
    assertTrue(polarities.get(0).isUpwards());
    assertTrue(polarities.get(1).isDownwards());
    assertTrue(polarities.get(2).isUpwards());
    assertTrue(polarities.get(3).isUpwards());
    assertTrue(polarities.get(4).isUpwards());

    assertTrue(sentence.natlogPolarity(0).isUpwards());
    assertTrue(sentence.natlogPolarity(1).isDownwards());
    assertTrue(sentence.natlogPolarity(2).isUpwards());
    assertTrue(sentence.natlogPolarity(3).isUpwards());
    assertTrue(sentence.natlogPolarity(4).isUpwards());

  }

  @Test
  public void testDependencyParse() {
    Sentence sentence = new Sentence("The cat is blue.");
    assertEquals(new Integer(1), sentence.governor(0).orElse(-42));
    assertEquals(new Integer(3), sentence.governor(1).orElse(-42));
    assertEquals(new Integer(3), sentence.governor(2).orElse(-42));
    assertEquals(new Integer(-1), sentence.governor(3).orElse(-42));
    assertEquals(new Integer(3), sentence.governor(4).orElse(-42));

    assertEquals("det", sentence.incomingDependencyLabel(0).orElse("???"));
    assertEquals("nsubj", sentence.incomingDependencyLabel(1).orElse("???"));
    assertEquals("cop", sentence.incomingDependencyLabel(2).orElse("???"));
    assertEquals("root", sentence.incomingDependencyLabel(3).orElse("???"));
    assertEquals("punct", sentence.incomingDependencyLabel(4).orElse("???"));

    // Make sure we called the right annotator
    assertNotNull(sentence.asCoreMap().get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));
    assertNotNull(sentence.asCoreMap().get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
    assertNotNull(sentence.asCoreMap().get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class));
    assertNull(sentence.asCoreMap().get(TreeCoreAnnotations.TreeAnnotation.class));

    for (int i = 0; i < sentence.length(); ++i) {
      assertEquals(sentence.governor(i), sentence.governors().get(i));
      assertEquals(sentence.incomingDependencyLabel(i), sentence.incomingDependencyLabels().get(i));

    }
  }

  @Test
  public void testDependencyParseWithParseAnnotator() {
    Sentence sentence = new Sentence("The cat is blue.");
    sentence.parse();
    assertEquals(new Integer(1), sentence.governor(0).orElse(-42));
    assertEquals(new Integer(3), sentence.governor(1).orElse(-42));
    assertEquals(new Integer(3), sentence.governor(2).orElse(-42));
    assertEquals(new Integer(-1), sentence.governor(3).orElse(-42));
    assertEquals(new Integer(3), sentence.governor(4).orElse(-42));

    assertEquals("det", sentence.incomingDependencyLabel(0).orElse("???"));
    assertEquals("nsubj", sentence.incomingDependencyLabel(1).orElse("???"));
    assertEquals("cop", sentence.incomingDependencyLabel(2).orElse("???"));
    assertEquals("root", sentence.incomingDependencyLabel(3).orElse("???"));
    assertEquals("punct", sentence.incomingDependencyLabel(4).orElse("???"));

    // Make sure we called the right annotator
    assertNotNull(sentence.asCoreMap().get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));
    assertNotNull(sentence.asCoreMap().get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
    assertNotNull(sentence.asCoreMap().get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class));
    assertNotNull(sentence.asCoreMap().get(TreeCoreAnnotations.TreeAnnotation.class));

    for (int i = 0; i < sentence.length(); ++i) {
      assertEquals(sentence.governor(i), sentence.governors().get(i));
      assertEquals(sentence.incomingDependencyLabel(i), sentence.incomingDependencyLabels().get(i));

    }
  }

  @Test
  public void testToCoreLabels() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog");
    List<CoreLabel> tokens = sent.asCoreLabels(sent::posTags);
    assertEquals(9, tokens.size());
    assertEquals("the", tokens.get(0).word());
    assertEquals("dog", tokens.get(8).word());
    assertEquals("DT", tokens.get(0).tag());
    assertEquals("NN", tokens.get(8).tag());
  }


  @Test
  public void testWriteRead() throws IOException {
    File tmp = File.createTempFile("sentenceITest", ".ser");
    tmp.deleteOnExit();
    FileOutputStream out = new FileOutputStream(tmp);
    Sentence orig = new Sentence("Cats have tails");
    orig.serialize(out);
    out.close();

    InputStream in = new FileInputStream(tmp);
    Sentence loaded = Sentence.deserialize(in);
    assertEquals(orig, loaded);
    in.close();
  }
}
