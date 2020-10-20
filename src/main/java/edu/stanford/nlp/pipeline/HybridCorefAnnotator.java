package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.CorefChain.CorefMention;
import edu.stanford.nlp.coref.hybrid.HybridCorefSystem;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.*;

public class HybridCorefAnnotator extends TextAnnotationCreator implements Annotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(HybridCorefAnnotator.class);

  private static final boolean VERBOSE = false;

  private final HybridCorefSystem corefSystem;

  // for backward compatibility
  private final boolean OLD_FORMAT;

  public HybridCorefAnnotator(Properties props) {
    try {
      // Load the default properties
      Properties corefProps = new Properties();
      try (BufferedReader reader = IOUtils.readerFromString("edu/stanford/nlp/hcoref/properties/coref-default-dep.properties")){
        corefProps.load(reader);
      } catch (IOException ignored) { }
      // Add passed properties
      Enumeration<Object> keys = props.keys();
      while (keys.hasMoreElements()) {
        String key = keys.nextElement().toString();
        corefProps.setProperty(key, props.getProperty(key));
      }
      // Create coref system
      corefSystem = new HybridCorefSystem(corefProps);
      OLD_FORMAT = Boolean.parseBoolean(props.getProperty("oldCorefFormat", "false"));
    } catch (Exception e) {
      log.error("cannot create HybridCorefAnnotator!");
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Override
  public void annotate(Annotation annotation){
    try {
      if (!annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
        log.error("this coreference resolution system requires SentencesAnnotation!");
        return;
      }

      if (hasSpeakerAnnotations(annotation)) {
        annotation.set(CoreAnnotations.UseMarkedDiscourseAnnotation.class, true);
      }

      Document corefDoc = corefSystem.docMaker.makeDocument(annotation);
      Map<Integer, CorefChain> result = corefSystem.coref(corefDoc);
      annotation.set(CorefCoreAnnotations.CorefChainAnnotation.class, result);

      // for backward compatibility
      if(OLD_FORMAT) annotateOldFormat(result, corefDoc);

    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static List<Pair<IntTuple, IntTuple>> getLinks(Map<Integer, CorefChain> result) {
    List<Pair<IntTuple, IntTuple>> links = new ArrayList<>();
    CorefChain.CorefMentionComparator comparator = new CorefChain.CorefMentionComparator();

    for(CorefChain c : result.values()) {
      List<CorefMention> s = c.getMentionsInTextualOrder();
      for(CorefMention m1 : s){
        for(CorefMention m2 : s){
          if(comparator.compare(m1, m2)==1) links.add(new Pair<>(m1.position, m2.position));
        }
      }
    }
    return links;
  }

  private static void annotateOldFormat(Map<Integer, CorefChain> result, Document corefDoc) {

    List<Pair<IntTuple, IntTuple>> links = getLinks(result);
    Annotation annotation = corefDoc.annotation;

    if(VERBOSE){
      System.err.printf("Found %d coreference links:%n", links.size());
      for(Pair<IntTuple, IntTuple> link: links){
        System.err.printf("LINK (%d, %d) -> (%d, %d)%n", link.first.get(0), link.first.get(1), link.second.get(0), link.second.get(1));
      }
    }

    //
    // save the coref output as CorefGraphAnnotation
    //

    // this graph is stored in CorefGraphAnnotation -- the raw links found by the coref system
    List<Pair<IntTuple, IntTuple>> graph = new ArrayList<>();

    for(Pair<IntTuple, IntTuple> link: links){
      //
      // Note: all offsets in the graph start at 1 (not at 0!)
      //       we do this for consistency reasons, as indices for syntactic dependencies start at 1
      //
      int srcSent = link.first.get(0);
      int srcTok = corefDoc.getOrderedMentions().get(srcSent - 1).get(link.first.get(1)-1).headIndex + 1;
      int dstSent = link.second.get(0);
      int dstTok = corefDoc.getOrderedMentions().get(dstSent - 1).get(link.second.get(1)-1).headIndex + 1;
      IntTuple dst = new IntTuple(2);
      dst.set(0, dstSent);
      dst.set(1, dstTok);
      IntTuple src = new IntTuple(2);
      src.set(0, srcSent);
      src.set(1, srcTok);
      graph.add(new Pair<>(src, dst));
    }
    annotation.set(CorefCoreAnnotations.CorefGraphAnnotation.class, graph);

    for (CorefChain corefChain : result.values()) {
      if(corefChain.getMentionsInTextualOrder().size() < 2) continue;
      Set<CoreLabel> coreferentTokens = Generics.newHashSet();
      for (CorefMention mention : corefChain.getMentionsInTextualOrder()) {
        CoreMap sentence = annotation.get(CoreAnnotations.SentencesAnnotation.class).get(mention.sentNum - 1);
        CoreLabel token = sentence.get(CoreAnnotations.TokensAnnotation.class).get(mention.headIndex - 1);
        coreferentTokens.add(token);
      }
      for (CoreLabel token : coreferentTokens) {
        token.set(CorefCoreAnnotations.CorefClusterAnnotation.class, coreferentTokens);
      }
    }
  }

  private static boolean hasSpeakerAnnotations(Annotation annotation) {
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreLabel t : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        if (t.get(CoreAnnotations.SpeakerAnnotation.class) != null) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class,
        CorefCoreAnnotations.CorefMentionsAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CorefChainAnnotation.class);
  }

  private static Annotation testEnglish() {
    String text = "Barack Obama is the president of United States. He visited California last week.";
    return testAnnoation(text,new String[] {
        "-props", "edu/stanford/nlp/hcoref/properties/coref-default-dep.properties"
    });
  }

  private static Annotation testChinese(){
//    String text = "中国武道太学和中国书道太学成立。新华社北京９月１日电。旨在振兴中华文化于"
//        + "国际的中国武道太学和中国书道太学今天在北京成立。上述两所太学是在国家体委、"
//        + "文化部、中国武术研究院、中国艺术研究院的关杯和支持下，在台湾著名企业家、书"
//        + "画家、艺术品收藏家李志仁先生倡议和出资下，经国家教委和北京市成人教育局批准"
//        + "而成立的。李志仁先生在台湾有“笔墨大王”之称，近几年先后出资一千万元新台币"
//        + "，在中国大陆老、少、边、穷地区建立了百所小学，受到海内外人士的称赞。（完）\n";
    String text = "俄罗斯 航空 公司 一 名 官员 在 ９号 说 ， 米洛舍维奇 的 儿子 马可·米洛舍维奇 ９号 早上 持 外交 护照 从 俄国 首都 莫斯科 搭机 飞往 中国 大陆 北京 ， 可是 就 在 稍后 就 返回 莫斯科 。 这 名 俄国 航空 公司 官员 说 马可 是 因为 护照 问题 而 在 北京 机场 被 中共 遣返 莫斯科 。 北京 机场 方面 的 这 项 举动 清楚 显示 中共 有意 放弃 在 总统 大选 落败 的 前 南斯拉夫 总统 米洛舍维奇 ， 因此 他 在 南斯拉夫 受到 民众 厌恶 的 儿子 马可 才 会 在 北京 机场 被 中共 当局 送回 莫斯科 。 马可 持 外交 护照 能够 顺利 搭机 离开 莫斯科 ， 但是 却 在 北京 受阻 ， 可 算是 踢到 了 铁板 。 可是 这 项 消息 和 先前 外界 谣传 中共 当局 准备 提供 米洛舍维奇 和 他 的 家人 安全 庇护所 有 着 很 大 的 出入 ， 一般 认为 在 去年 米洛舍维奇 挥兵 攻打 科索沃 境内 阿尔巴尼亚 一 分离主义 分子 的 时候 ， 强力 反对 北约 组织 攻击 南斯拉夫 的 中共 ， 会 全力 保护 米洛舍维奇 和 他 的 家人 及 亲信 。 可是 从 ９号 马可 被 送回 莫斯科 一 事 看 起来 ， 中共 很 可能 会 放弃 米洛舍维奇 。";
    return testAnnoation(text,new String[]{
        "-props", "edu/stanford/nlp/hcoref/properties/zh-dcoref-default.properties"
    });
  }

  private static Annotation testAnnoation(String text,String[] args){
    Annotation document = new Annotation(text);
    Properties props = StringUtils.argsToProperties(args);
    StanfordCoreNLP corenlp = new StanfordCoreNLP(props);
    corenlp.annotate(document);
    HybridCorefAnnotator hcoref = new HybridCorefAnnotator(props);
    hcoref.annotate(document);
    return document;
  }

  public static void main(String[] args) {

//    String text = "Since the implementation of the Individual Visit Scheme between Hong Kong and the mainland , more and more mainland tourists are coming to visit Hong Kong. "
//                  +"From the beginning up till now , more than seven million individual tourists , have come to Hong Kong. "
//                  +"Well , we now , er , believe more will be coming . "
//                  +"At this point , it has been about two years . "
//                  +"Also , the current number of 34 cities will be increased . "
//                  +"Hong Kong was developed from a fishing harbor one hundred years ago to become today 's international metropolis . "
//                  +"Here , eastern and western cultures have gathered , and the new and the old coexist . "
//                  +"When in Hong Kong , you can wander among skyscrapers , heartily enjoy shopping sprees in well - known stores and malls for goods from various countries , and taste delicious snacks from all over the world at tea shops or at street stands in Mong Kok . "
//                  +"You can go to burn incense and make a vow at the Repulse Bay , where all deities gather . "
//                  +"You can enjoy the most charming sun - filled sandy beaches in Hong Kong. "
//                  +"You can ascend Victoria Peak to get a panoramic view of Victoria Harbor 's beautiful scenery . "
//                  +"Or hop onto a trolley with over a century of history , and feel the city 's blend of the old and the modern in slow motion .";
//

    Annotation document = testChinese();
    System.out.println(document.get(CorefChainAnnotation.class));
    log.info();
  }
}
