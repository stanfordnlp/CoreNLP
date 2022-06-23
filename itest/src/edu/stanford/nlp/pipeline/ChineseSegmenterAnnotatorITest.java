package edu.stanford.nlp.pipeline;

import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.StringUtils;


public class ChineseSegmenterAnnotatorITest {

  private StanfordCoreNLP pipeline; // = null

  @Before
  public void setUp() throws Exception {
    if (pipeline != null) {
      return;
    }
    Properties props = new Properties();
    props.setProperty("annotators", "cseg");
    props.setProperty("customAnnotatorClass.cseg", "edu.stanford.nlp.pipeline.ChineseSegmenterAnnotator");
    props.setProperty("cseg.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz");
    props.setProperty("cseg.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese");
    props.setProperty("cseg.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz");
    props.setProperty("cseg.sighanPostProcessing", "true");
    pipeline = new StanfordCoreNLP(props);
  }

  @Test
  public void testPipeline() {
    testOne("你马上回来北京吗？",
        new String[]{"你", "马上", "回来", "北京", "吗", "？"},
        new int[]{0, 1, 3, 5, 7, 8},
        new int[]{1, 3, 5, 7, 8, 9});

    // test that it does something reasonable with spaces
    testOne("我在 加州 工作 ",
            new String[]{"我", "在", "加州", "工作"},
            new int[]{0, 1, 3, 6},
            new int[]{1, 2, 5, 8});

    // test that it does something reasonable with NBSP
    testOne("我在 加州 工作 ",
            new String[]{"我", "在", "加州", "工作"},
            new int[]{0, 1, 3, 6},
            new int[]{1, 2, 5, 8});

    // All of the tools should now produce () instead of -LRB- -RRB-
    testOne("你马上回来(北京)吗？",
            new String[]{"你", "马上", "回来", "(", "北京", ")", "吗", "？"},
            new int[]{0, 1, 3, 5, 6, 8, 9, 10},
            new int[]{1, 3, 5, 6, 8, 9, 10, 11});

    // Properly handle XML tags
    testOne("<post id=\"something\" anything>这是一个测试</post>",
        new String[]{"<post id=\"something\" anything>", "这", "是", "一", "个", "测试", "</post>"},
        new int[]{0, 30, 31, 32, 33, 34, 36},
        new int[]{30, 31, 32, 33, 34, 36, 43});

    // KBP corpus examples, containing newlines and spaces
    // The segmenter should be able to keep spaces within the xml tags, but skip those out of xml tags
    testOne("<post author=\"拖垮美帝\" datetime=\"2011-12-06T22:36:00\" id=\"p3\">\n" +
        "这个很难回答。\n" +
        "</post>",
        new String[]{"<post author=\"拖垮美帝\" datetime=\"2011-12-06T22:36:00\" id=\"p3\">", "这个", "很", "难", "回答", "。", "</post>"},
        new int[]{0, 60, 62, 63, 64, 66, 68},
        new int[]{59, 62, 63, 64, 66, 67, 75});
    testOne("这里有一个图片。<img src=\"http://bbsfile.ifeng.com/bbsfile/images/smilies/default/lol.gif\"/>  希望你们都能看看。",
        new String[]{"这里", "有", "一", "个", "图片", "。", "<img src=\"http://bbsfile.ifeng.com/bbsfile/images/smilies/default/lol.gif\"/>",
          "希望", "你们", "都", "能", "看看", "。"},
        new int[]{0, 2, 3, 4, 5, 7, 8, 86, 88, 90, 91, 92, 94},
        new int[]{2, 3, 4, 5, 7, 8, 84, 88, 90, 91, 92, 94, 95});

    // Check still works with non-BMP chars
    testOne("买点咖啡\uD83D\uDE0A",
            new String[] { "买", "点", "咖啡", "\uD83D\uDE0A" },
            new int[] { 0, 1, 2, 4 },
            new int[] { 1, 2, 4, 6});
  }

  private void testOne(String query, String[] expectedWords, int[] expectedBeginPositions, int[] expectedEndPositions) {
    Annotation annotation = new Annotation(query);
    pipeline.annotate(annotation);

    List<CoreLabel> tokens = annotation.get(TokensAnnotation.class);
    if (expectedWords.length != tokens.size()) {
      System.err.println("Expected: " + StringUtils.join(expectedWords, "  "));
      System.err.println("Got:      " + StringUtils.joinWords(tokens, "  "));
    }
    Assert.assertEquals(expectedWords.length, tokens.size());
    for (int i = 0; i < expectedWords.length; ++i) {
      Assert.assertEquals(expectedWords[i], tokens.get(i).word());
      Assert.assertEquals(expectedBeginPositions[i], tokens.get(i).beginPosition());
      Assert.assertEquals(expectedEndPositions[i], tokens.get(i).endPosition());
    }
  }

}
