package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;

public class ChineseSegmenterAnnotatorITest extends TestCase {
  StanfordCoreNLP pipeline = null;

  @Override
  public void setUp()
    throws Exception
  {
    if (pipeline != null) {
      return;
    }
    Properties props = new Properties();
    props.setProperty("annotators", "cseg");
    props.setProperty("customAnnotatorClass.cseg", "edu.stanford.nlp.pipeline.ChineseSegmenterAnnotator");
    props.setProperty("cseg.model", "/u/nlp/data/gale/segtool/stanford-seg/classifiers-2010/05202008-ctb6.processed-chris6.lex.gz");
    props.setProperty("cseg.sighanCorporaDict", "/u/nlp/data/gale/segtool/stanford-seg/releasedata");
    props.setProperty("cseg.serDictionary", "/u/nlp/data/gale/segtool/stanford-seg/classifiers/dict-chris6.ser.gz");
    props.setProperty("cseg.sighanPostProcessing", "true");
    pipeline = new StanfordCoreNLP(props);
  }

  public void testPipeline() {
    testOne("你马上回来北京吗？", new String[]{"你", "马上", "回来", "北京", "吗", "？"}, new int[]{0, 1, 3, 5, 7, 8, 9});
    testOne("<post id=\"something\" anything>这是一个测试</post>",
            new String[]{"<post id=\"something\" anything>", "这", "是", "一", "个", "测试", "</post>"},
            new int[]{0, 30, 31, 32, 33, 34, 36});
  }

  private void testOne(String query, String[] expectedWords, int[] expectedPositions) {
    Annotation annotation = new Annotation(query);
    pipeline.annotate(annotation);

    List<CoreLabel> tokens = annotation.get(TokensAnnotation.class);
    assertEquals(expectedWords.length, tokens.size());
    for (int i = 0; i < expectedWords.length; ++i) {
      assertEquals(expectedWords[i], tokens.get(i).word());
      assertEquals(expectedPositions[i], tokens.get(i).beginPosition());
      assertEquals(expectedPositions[i+1], tokens.get(i).endPosition());
    }
  }
}