package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.CoreAnnotations.*;
import edu.stanford.nlp.util.*;

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.junit.Before;
import org.junit.Test;

public class ChineseSegmenterRegressionITest {

  public StanfordCoreNLP pipeline;

  // strings to test on
  public List<String> inputStrings = new ArrayList<>();

  // expected token lists
  List<List<String>> expectedTokenLists = new ArrayList<>();

  @Before
  public void setUp() {

    // first set up Chinese pipeline
    Properties props = StringUtils.argsToProperties("-props", "StanfordCoreNLP-chinese.properties");
    props.setProperty("annotators", "tokenize,ssplit");
    pipeline = new StanfordCoreNLP(props);

    // build list of sample input strings
    inputStrings = new ArrayList<>();
    expectedTokenLists= new ArrayList<>();

    // example 1
    inputStrings.add("巴拉克·奥巴马是美国总统。他在2008年当选");
    List<String> exampleOneTokenList = 
      Arrays.asList("巴拉克·奥巴马","是","美国","总统","。","他","在","2008年","当选");
    expectedTokenLists.add(exampleOneTokenList);

    // example 2
    inputStrings.add("声明全文如下:\n" +
        "    \n" +
        "   \n" +
        "中国政府欢迎乌克兰销毁其境内全部核武器的决定,\n" +
        "对乌克兰议会于11月16日批准乌克兰作为无核武器国\n" +
        "家加入《不扩散核武器条约》表示赞赏。");
    List<String> exampleTwoTokenList =
      Arrays.asList("声明","全","文","如下",":","中国","政府","欢迎","乌克兰","销毁","其","境内",
                    "全部","核武器","的","决定",",","对","乌克兰","议会","于","11月","16日","批准","乌克兰","作为",
                    "无", "核武器","国家","加入","《","不", "扩散","核武器","条约","》","表示","赞赏","。");
    expectedTokenLists.add(exampleTwoTokenList);

    // example 3
    inputStrings.add("协定规定,自协定签署之日起一年后,缔约四国之间\n" +
        "实现澜沧江-湄公河商船通航,缔约任何一方的船舶均可\n" +
        "按照协定的规定在中国的思茅港和老挝的琅勃拉邦港之间\n" +
        "自由航行。");
    List<String> exampleThreeTokenList =
      Arrays.asList("协定","规定",",","自","协定","签署","之","日","起","一","年","后",",","缔约","四",
                    "国","之间","实现","澜沧江","-","湄公河","商船","通航",",","缔约","任何","一","方","的","船舶",
                    "均","可","按照","协定","的","规定","在","中国","的","思茅港","和","老挝","的","琅勃拉邦港",
                    "之间","自由","航行","。");
    expectedTokenLists.add(exampleThreeTokenList);
  }

  @Test
  public void testChineseSegmentation() {
    int exampleCount = 0;
    for (String inputString : inputStrings) {
      Annotation ann = new Annotation(inputString);
      pipeline.annotate(ann);
      ArrayList<String> foundTokens = new ArrayList<>();
      for (CoreMap sentence : ann.get(SentencesAnnotation.class)) {
        for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
          foundTokens.add(token.word());
        }
      }
      // check if the token lists are identical
      //System.err.println(foundTokens.toString());
      //System.err.println(expectedTokenLists.get(exampleCount));
      if (!expectedTokenLists.get(exampleCount).equals(foundTokens)) {
        if (expectedTokenLists.get(exampleCount).size() != foundTokens.size()) {
          System.err.println("Expected " + expectedTokenLists.get(exampleCount).size() +
                             " tokens, got " + foundTokens.size());
        }
        for (int i = 0; i < Math.min(expectedTokenLists.get(exampleCount).size(), foundTokens.size()); ++i) {
          if (!expectedTokenLists.get(exampleCount).get(i).equals(foundTokens.get(i))) {
            System.err.println("First difference at index " + i +
                               ": expected " + expectedTokenLists.get(exampleCount).get(i) +
                               " got " + foundTokens.get(i));
            break;
          }
        }
      }
      assertEquals(expectedTokenLists.get(exampleCount), foundTokens);
      exampleCount++;
    }
  }

}
