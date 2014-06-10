package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.List;

import edu.stanford.nlp.ling.ChineseCoreAnnotations.CharactersAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ChineseCharAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ChineseSegAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

public class ChineseAnnotationPipelineITest extends TestCase {
  AnnotationPipeline pipeline = null;

  @Override
  public void setUp() throws Exception {
    synchronized(ChineseAnnotationPipelineITest.class) {
      if (pipeline == null) {
        // This is loaded from the Chinese models jar file.  Editing
        // it directly in the source tree and hoping to see changes
        // will be a very frustrating experience.
        pipeline = new StanfordCoreNLP("StanfordCoreNLP-chinese.properties");
      }
    }
  }

  public void testFullPipeline() {
    String query = "你马上回来北京吗？";
    String[] expectedWords = {"你", "马上", "回来", "北京", "吗", "？"};
    String[] expectedCharacters = {"你","马","上","回","来",
                                   "北","京","吗","？"};
    boolean[] expectedSegs = {true, true, false, true, false,
                              true, false, true, true};
    String[] expectedNer = {"O", "O", "O", "GPE", "O", "O"};

    assertEquals(expectedCharacters.length, expectedSegs.length);
    assertEquals(expectedWords.length, expectedNer.length);

    // pipeline is expected to have tokenization, segmentation and ner
    Annotation ann = new Annotation(query);
    pipeline.annotate(ann);

    List<CoreMap> sentences = ann.get(SentencesAnnotation.class);
    assertFalse(sentences == null);
    assertEquals(1, sentences.size());

    List<CoreLabel> tokens = sentences.get(0).get(TokensAnnotation.class);
    assertEquals(expectedWords.length, tokens.size());
    for (int i = 0; i < expectedWords.length; ++i) {
      assertEquals(expectedWords[i], tokens.get(i).word());
      assertEquals(expectedNer[i], tokens.get(i).ner());
    }

    List<CoreLabel> characters = ann.get(CharactersAnnotation.class);
    assertEquals(expectedCharacters.length, characters.size());
    for (int i = 0; i < expectedCharacters.length; ++i) {
      CoreLabel word = characters.get(i);
      assertEquals(expectedCharacters[i],
                   word.get(ChineseCharAnnotation.class));
      assertEquals(expectedSegs[i] ? "1" : "0",
                   word.get(ChineseSegAnnotation.class));
    }
  }

  public void testTwoSentences() {
    String query = "你马上回来北京吗？我要回去美国。";
    Annotation ann = new Annotation(query);
    pipeline.annotate(ann);

    List<CoreMap> sentences = ann.get(SentencesAnnotation.class);
    assertFalse(sentences == null);
    assertEquals(2, sentences.size());

    String[][] expectedWords = { {"你", "马上", "回来", "北京", "吗", "？"},
                                 {"我", "要", "回去", "美国", "。"} };
    int[][] expectedPositions = { {0, 1, 3, 5, 7, 8, 9},
                                  {9, 10, 11, 13, 15, 16} };
    for (int i = 0; i < 2; ++i) {
      List<CoreLabel> tokens = sentences.get(i).get(TokensAnnotation.class);
      assertEquals(expectedWords[i].length, tokens.size());
      for (int j = 0; j < expectedWords.length; ++j) {
        assertEquals(expectedWords[i][j], tokens.get(j).word());
        assertEquals(expectedPositions[i][j], tokens.get(j).beginPosition());
        assertEquals(expectedPositions[i][j+1], tokens.get(j).endPosition());
      }
    }
  }

}

