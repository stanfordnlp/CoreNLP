package edu.stanford.nlp.pipeline;

import java.util.List;
import java.util.Properties;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;

/** This class has tests for segmenter languages (Chinese, Arabic) that require
 *  resources to run. Most of the tests are in the unit test class.
 *
 *  @author Christopher Manning
 */
public class WordsToSentencesAnnotatorITest {

  private static final String headline = "“习主席欧洲行”漫评④：为世界经济联动增长贡献“中国方略”\n\n7日，国家主席习近平在“世界桥城”" +
          "汉堡出席二十国集团（G20）领导人第十二次峰会并发表题为《坚\n持开放包容 推动联动增长》的重要讲话，提出四点“中国主张”，" +
          "为G20未来发展规划蓝图，为世界经济联动增长指明方向，受到与会各方和国际社会高度评价。[详细]";

  @Test
  public void testTwoNewlineIsSentenceBreakTokenizeNLs() {
    String[] sents = {
            "“ 习 主席 欧洲行 ” 漫评 ④ ： 为 世界 经济 联动 增长 贡献 “ 中国 方略 ”",
            "7日 ， 国家 主席 习近平 在 “ 世界 桥 城 ” 汉堡 出席 二十 国 集团 （ G20 ） 领导人 第十二 次 峰会 并 发表 题 为 " +
                    "《 坚持 开放 包容 推动 联动 增长 》 的 重要 讲话 ， 提出 四点 “ 中国 主张 ” ， " +
                    "为 G20 未来 发展 规划 蓝图 ， 为 世界 经济 联动 增长 指明 方向 ， 受到 与会 各 方 和 国际 社会 高度 评价 。",
            "[ 详细 ]"
    };
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "tokenize.language", "zh",
            "tokenize.options", "tokenizeNLs,invertible,ptb3Escaping=true",
            "ssplit.boundaryTokenRegex", "[.。]|[!?！？]+",
            "ssplit.newlineIsSentenceBreak", "two",
            "segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz",
            "segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese",
            "segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz",
            "segment.sighanPostProcessing", "true"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(headline);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(sents.length, sentences.size());

    for (int i = 0; i < Math.min(sents.length, sentences.size()); i++) {
      CoreMap sentence = sentences.get(i);
      String sentenceText = SentenceUtils.listToString(sentence.get(CoreAnnotations.TokensAnnotation.class));
      assertEquals("Bad sentence #" + i, sents[i], sentenceText);
    }
  }

  @Test
  public void testTwoNewlineIsSentenceBreak() {
    String[] sents = {
            "“ 习 主席 欧洲行 ” 漫评 ④ ： 为 世界 经济 联动 增长 贡献 “ 中国 方略 ”",
            "7日 ， 国家 主席 习近平 在 “ 世界 桥 城 ” 汉堡 出席 二十 国 集团 （ G20 ） 领导人 第十二 次 峰会 并 发表 题 为 " +
                    "《 坚持 开放 包容 推动 联动 增长 》 的 重要 讲话 ， 提出 四点 “ 中国 主张 ” ， " +
                    "为 G20 未来 发展 规划 蓝图 ， 为 世界 经济 联动 增长 指明 方向 ， 受到 与会 各 方 和 国际 社会 高度 评价 。",
            "[ 详细 ]"
    };
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "tokenize.language", "zh",
            "tokenize.options", "invertible,ptb3Escaping=true",
            "ssplit.boundaryTokenRegex", "[.。]|[!?！？]+",
            "ssplit.newlineIsSentenceBreak", "two",
            "segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz",
            "segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese",
            "segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz",
            "segment.sighanPostProcessing", "true"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(headline);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(sents.length, sentences.size());

    for (int i = 0; i < Math.min(sents.length, sentences.size()); i++) {
      CoreMap sentence = sentences.get(i);
      String sentenceText = SentenceUtils.listToString(sentence.get(CoreAnnotations.TokensAnnotation.class));
      assertEquals("Bad sentence #" + i, sents[i], sentenceText);
    }
  }

  @Test
  public void testNewlineIsSentenceBreakTokenizeNLs() {
    String[] sents = {
            "“ 习 主席 欧洲行 ” 漫评 ④ ： 为 世界 经济 联动 增长 贡献 “ 中国 方略 ”",
            "7日 ， 国家 主席 习近平 在 “ 世界 桥 城 ” 汉堡 出席 二十 国 集团 （ G20 ） 领导人 第十二 次 峰会 并 发表 题 为 " +
                    "《 坚",
            "持 开放 包容 推动 联动 增长 》 的 重要 讲话 ， 提出 四点 “ 中国 主张 ” ， " +
                    "为 G20 未来 发展 规划 蓝图 ， 为 世界 经济 联动 增长 指明 方向 ， 受到 与会 各 方 和 国际 社会 高度 评价 。",
            "[ 详细 ]"
    };
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "tokenize.language", "zh",
            "tokenize.options", "tokenizeNLs,invertible,ptb3Escaping=true",
            "ssplit.boundaryTokenRegex", "[.。]|[!?！？]+",
            "ssplit.newlineIsSentenceBreak", "always",
            "segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz",
            "segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese",
            "segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz",
            "segment.sighanPostProcessing", "true"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(headline);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(sents.length, sentences.size());

    for (int i = 0; i < Math.min(sents.length, sentences.size()); i++) {
      CoreMap sentence = sentences.get(i);
      String sentenceText = SentenceUtils.listToString(sentence.get(CoreAnnotations.TokensAnnotation.class));
      assertEquals("Bad sentence #" + i, sents[i], sentenceText);
    }
  }

  @Test
  public void testNewlineIsSentenceBreakNever() {
    String[] sents = {
            "“ 习 主席 欧洲行 ” 漫评 ④ ： 为 世界 经济 联动 增长 贡献 “ 中国 方略 ” " +
                    "7日 ， 国家 主席 习近平 在 “ 世界 桥 城 ” 汉堡 出席 二十 国 集团 （ G20 ） 领导人 第十二 次 峰会 并 " +
                    "发表 题 为 《 坚持 开放 包容 推动 联动 增长 》 的 重要 讲话 ， 提出 四点 “ 中国 主张 ” ， " +
                    "为 G20 未来 发展 规划 蓝图 ， 为 世界 经济 联动 增长 指明 方向 ， 受到 与会 各 方 和 国际 社会 高度 评价 。",
            "[ 详细 ]"
    };
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "tokenize.language", "zh",
            "ssplit.boundaryTokenRegex", "[.。]|[!?！？]+",
            "ssplit.newlineIsSentenceBreak", "never",
            "segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz",
            "segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese",
            "segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz",
            "segment.sighanPostProcessing", "true"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(headline);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(sents.length, sentences.size());

    for (int i = 0; i < Math.min(sents.length, sentences.size()); i++) {
      CoreMap sentence = sentences.get(i);
      String sentenceText = SentenceUtils.listToString(sentence.get(CoreAnnotations.TokensAnnotation.class));
      assertEquals("Bad sentence #" + i, sents[i], sentenceText);
    }
  }

  @Test
  public void testEolOnly() {
    String[] sents = {
            "“ 习 主席 欧洲行 ” 漫评 ④ ： 为 世界 经济 联动 增长 贡献 “ 中国 方略 ”",
            // "",
            "7日 ， 国家 主席 习近平 在 “ 世界 桥 城 ” 汉堡 出席 二十 国 集团 （ G20 ） 领导人 第十二 次 峰会 并 发表 题 为 " +
                    "《 坚",
            "持 开放 包容 推动 联动 增长 》 的 重要 讲话 ， 提出 四点 “ 中国 主张 ” ， 为 G20 未来 发展 规划 蓝图 ， " +
                    "为 世界 经济 联动 增长 指明 方向 ， 受到 与会 各 方 和 国际 社会 高度 评价 。 [ 详细 ]"
    };
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "tokenize.language", "zh",
            "tokenize.options", "invertible",
            "ssplit.boundaryTokenRegex", "[.。]|[!?！？]+",
            "ssplit.eolonly", "true",
            "segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz",
            "segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese",
            "segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz",
            "segment.sighanPostProcessing", "true"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(headline);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(sents.length, sentences.size());
    // Note: although there is an option for allowing empty sentences, at present it is always turned off in Annotator.

    for (int i = 0; i < Math.min(sents.length, sentences.size()); i++) {
      CoreMap sentence = sentences.get(i);
      String sentenceText = SentenceUtils.listToString(sentence.get(CoreAnnotations.TokensAnnotation.class));
      assertEquals("Bad sentence #" + i, sents[i], sentenceText);
    }
  }

  @Test
  public void testIsOneSentence() {
    String[] sents = {
            "“ 习 主席 欧洲行 ” 漫评 ④ ： 为 世界 经济 联动 增长 贡献 “ 中国 方略 ” 7日 ，" +
                    " 国家 主席 习近平 在 “ 世界 桥 城 ” 汉堡 出席 二十 国 集团 （ G20 ） 领导人 第十二 次 峰会 并 发表 题 为 " +
                    "《 坚持 开放 包容 推动 联动 增长 》 的 重要 讲话 ， 提出 四点 “ 中国 主张 ” ， 为 G20 " +
                    "未来 发展 规划 蓝图 ， 为 世界 经济 联动 增长 指明 方向 ， 受到 与会 各 方 和 国际 社会 高度 评价 。 " +
                    "[ 详细 ]"
    };
    Properties props = PropertiesUtils.asProperties(
            "annotators", "tokenize, ssplit",
            "tokenize.language", "zh",
            "ssplit.boundaryTokenRegex", "[.。]|[!?！？]+",
            "ssplit.isOneSentence", "true",
            "segment.model", "edu/stanford/nlp/models/segmenter/chinese/ctb.gz",
            "segment.sighanCorporaDict", "edu/stanford/nlp/models/segmenter/chinese",
            "segment.serDictionary", "edu/stanford/nlp/models/segmenter/chinese/dict-chris6.ser.gz",
            "segment.sighanPostProcessing", "true"
    );
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(headline);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(sents.length, sentences.size());

    for (int i = 0; i < Math.min(sents.length, sentences.size()); i++) {
      CoreMap sentence = sentences.get(i);
      String sentenceText = SentenceUtils.listToString(sentence.get(CoreAnnotations.TokensAnnotation.class));
      assertEquals("Bad sentence #" + i, sents[i], sentenceText);
    }
  }

}
