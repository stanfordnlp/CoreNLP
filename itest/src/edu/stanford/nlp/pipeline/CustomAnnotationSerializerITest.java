package edu.stanford.nlp.pipeline;

import java.io.*;
import java.util.Properties;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

public class CustomAnnotationSerializerITest extends TestCase {
  static StanfordCoreNLP fullPipeline = null;
  static CustomAnnotationSerializer serializer = new CustomAnnotationSerializer(false, false);

  public void setUp() {
    synchronized(CustomAnnotationSerializerITest.class) {
      if (fullPipeline == null) {
        fullPipeline = new StanfordCoreNLP();
      }
    }
    
  }

  public void testSimple() throws IOException {
    Annotation annotation = new Annotation("This is a test");
    fullPipeline.annotate(annotation);
    runTest(annotation);
  }

  public void testCollapsedGraphs() throws IOException {
    Annotation annotation = new Annotation("I bought a bone for my dog.");
    fullPipeline.annotate(annotation);
    runTest(annotation);
  }

  public void testTwoSentences() throws IOException {
    Annotation annotation = new Annotation("I bought a bone for my dog.  He chews it every day.");
    fullPipeline.annotate(annotation);
    runTest(annotation);
  }

  public void testCopyWordGraphs() throws IOException {
    Annotation annotation = new Annotation("I went over the river and through the woods");
    fullPipeline.annotate(annotation);
    runTest(annotation);
  }

  private void runTest(Annotation annotation) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    serializer.write(annotation, out);
    byte[] serialized = out.toByteArray();

    ByteArrayInputStream in = new ByteArrayInputStream(serialized);
    Annotation deserialized = serializer.read(in).first();

    assertEquals(annotation.get(CoreAnnotations.SentencesAnnotation.class).size(), deserialized.get(CoreAnnotations.SentencesAnnotation.class).size());
    for (int i = 0; i < annotation.get(CoreAnnotations.SentencesAnnotation.class).size(); ++i) {
      verifySentence(annotation.get(CoreAnnotations.SentencesAnnotation.class).get(i), deserialized.get(CoreAnnotations.SentencesAnnotation.class).get(i));
    }
  }

  private void verifySentence(CoreMap expected, CoreMap result) {
    assertEquals(expected.get(CoreAnnotations.TokensAnnotation.class).size(), result.get(CoreAnnotations.TokensAnnotation.class).size());
    for (int i = 0; i < expected.get(CoreAnnotations.TokensAnnotation.class).size(); ++i) {
      verifyWord(expected.get(CoreAnnotations.TokensAnnotation.class).get(i), result.get(CoreAnnotations.TokensAnnotation.class).get(i));
    }
    verifyTree(expected.get(TreeCoreAnnotations.TreeAnnotation.class), result.get(TreeCoreAnnotations.TreeAnnotation.class));
    verifyGraph(expected.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class), result.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
    verifyGraph(expected.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class), result.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class));
    verifyGraph(expected.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class), result.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));
  }

  Class[] tokenAnnotations = { CoreAnnotations.TextAnnotation.class, CoreAnnotations.ValueAnnotation.class, CoreAnnotations.LemmaAnnotation.class, CoreAnnotations.PartOfSpeechAnnotation.class, CoreAnnotations.NamedEntityTagAnnotation.class, CoreAnnotations.CharacterOffsetBeginAnnotation.class, CoreAnnotations.CharacterOffsetEndAnnotation.class };
  
  private void verifyTree(Tree expected, Tree result) {
    if (expected == null) {
      assertEquals(expected, result);
      return;
    }
    assertEquals(expected.toString(), result.toString());
  }

  private void verifyGraph(SemanticGraph expected, SemanticGraph result) {
    if (expected == null) {
      assertEquals(expected, result);
      return;
    }
    assertEquals(expected.vertexSet(), result.vertexSet());
    // TODO: Fix the equals for the DirectedMultiGraph so we can compare the two graphs directly
    assertEquals(expected.toString(), result.toString());
  }

  private void verifyWord(CoreLabel expected, CoreLabel result) {
    for (Class annotation : tokenAnnotations) {
      if (expected.get(annotation) == null && result.get(annotation) != null && "".equals(result.get(annotation))) {
        // allow "" in place of null
        continue;
      }
      assertEquals("Different for class " + annotation, expected.get(annotation), result.get(annotation));
    }
  }
}

