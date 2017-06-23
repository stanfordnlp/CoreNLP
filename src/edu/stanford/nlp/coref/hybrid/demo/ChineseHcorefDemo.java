package edu.stanford.nlp.coref.hybrid.demo;

import java.util.Properties;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;


/**
 * A simple example of Stanford Chinese coreference resolution.
 * <p>
 * When I use originAPI code, using the properties file in path edu/stanford/nlp/hcoref/properties/zh-dcoref-default.properties
 * the code could not run correctly in Chinese.
 * <p>
 * What I did is extracting the right properties file from stanford-chinese-corenlp-2015-12-08-models.jar
 * and replace edu/stanford/nlp/hcoref/properties/zh-coref-default.properties to our originAPI code
 * which finally run correctly.
 *
 * @see <a href="http://stanfordnlp.github.io/CoreNLP/coref.html">http://stanfordnlp.github.io/CoreNLP/coref.html</a>
 * @author zkli
 */
public class ChineseHcorefDemo {

  public static void main(String[] args) throws Exception {
    long startTime = System.currentTimeMillis();

    String text = "俄罗斯 航空 公司 一 名 官员 在 ９号 说 ， " +
            "米洛舍维奇 的 儿子 马可·米洛舍维奇 ９号 早上 持 外交 护照 从 俄国 首都 莫斯科 搭机 飞往 中国 大陆 北京 ， " +
            "可是 就 在 稍后 就 返回 莫斯科 。 " +
            "这 名 俄国 航空 公司 官员 说 马可 是 因为 护照 问题 而 在 北京 机场 被 中共 遣返 莫斯科 。 " +
            "北京 机场 方面 的 这 项 举动 清楚 显示 中共 有意 放弃 在 总统 大选 落败 的 前 南斯拉夫 总统 米洛舍维奇 ， " +
            "因此 他 在 南斯拉夫 受到 民众 厌恶 的 儿子 马可 才 会 在 北京 机场 被 中共 当局 送回 莫斯科 。 " +
            "马可 持 外交 护照 能够 顺利 搭机 离开 莫斯科 ， 但是 却 在 北京 受阻 ， 可 算是 踢到 了 铁板 。 " +
            "可是 这 项 消息 和 先前 外界 谣传 中共 当局 准备 提供 米洛舍维奇 和 他 的 家人 安全 庇护所 有 着 很 大 的 出入 ," +
            " 一般 认为 在 去年 米洛舍维奇 挥兵 攻打 科索沃 境内 阿尔巴尼亚 一 分离主义 分子 的 时候 ， " +
            "强力 反对 北约 组织 攻击 南斯拉夫 的 中共 ， 会 全力 保护 米洛舍维奇 和 他 的 家人 及 亲信 。 " +
            "可是 从 ９号 马可 被 送回 莫斯科 一 事 看 起来 ， 中共 很 可能 会 放弃 米洛舍维奇 。";

    args = new String[]{"-props", "edu/stanford/nlp/hcoref/properties/zh-coref-default.properties"};

    Annotation document = new Annotation(text);
    Properties props = StringUtils.argsToProperties(args);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);
    System.out.println("---");
    System.out.println("coref chains");

    for (CorefChain cc : document.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
      System.out.println("\t" + cc);
    }
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      System.out.println("---");
      System.out.println("mentions");
      for (Mention m : sentence.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)) {
        System.out.println("\t" + m);
      }
    }

    long endTime = System.currentTimeMillis();
    long time = (endTime - startTime) / 1000;
    System.out.println("Running time " + time / 60 + "min " + time % 60 + "s");
  }

}
