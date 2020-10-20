package edu.stanford.nlp.trees.international.pennchinese;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class ChineseUtilsTest extends TestCase {

  public void testNormalize() {
    String input = "Hello  Ｅｎｇｌｉｓｈ - 你好\u3000班汉·西巴阿差\u3000Chris•Manning \uD83E\uDD16\uD83E\uDD16robot";
    String outputLLL = "Hello  Ｅｎｇｌｉｓｈ - 你好\u3000班汉·西巴阿差\u3000Chris•Manning \uD83E\uDD16\uD83E\uDD16robot";
    String outputAAN = "Hello  English - 你好 班汉·西巴阿差 Chris·Manning \uD83E\uDD16\uD83E\uDD16robot";
    String outputFFF = "Ｈｅｌｌｏ\u3000\u3000Ｅｎｇｌｉｓｈ\u3000－\u3000你好　班汉・西巴阿差　Ｃｈｒｉｓ・Ｍａｎｎｉｎｇ\u3000\uD83E\uDD16\uD83E\uDD16ｒｏｂｏｔ";
    assertEquals(outputLLL, ChineseUtils.normalize(input, ChineseUtils.LEAVE, ChineseUtils.LEAVE, ChineseUtils.LEAVE));
    assertEquals(outputAAN, ChineseUtils.normalize(input, ChineseUtils.ASCII, ChineseUtils.ASCII, ChineseUtils.NORMALIZE));
    assertEquals(outputFFF, ChineseUtils.normalize(input, ChineseUtils.FULLWIDTH, ChineseUtils.FULLWIDTH, ChineseUtils.FULLWIDTH));
  }

}