package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import junit.framework.TestCase;

import java.util.List;

/**
 * Tests Chunk Annotation Utility functions
 *
 * @author Angel Chang
 */
public class ChunkAnnotationUtilsTest extends TestCase {
  public void testMergeChunks() throws Exception {
    // Create 4 sentences
    String text = "I have created sentence1.  And then sentence2.  Now sentence3. Finally sentence4.";
    Annotator tokenizer = new TokenizerAnnotator("en");
    Annotator ssplit = new WordsToSentencesAnnotator();
    Annotation annotation = new Annotation(text);
    tokenizer.annotate(annotation);
    ssplit.annotate(annotation);

    // Get sentences
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals("4 sentence expected", 4, sentences.size());

    // Merge last 3 into one
    ChunkAnnotationUtils.mergeChunks(sentences, text, 1,4);
    assertEquals("2 sentence expected", 2, sentences.size());
  }

}
