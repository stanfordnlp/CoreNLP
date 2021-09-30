package edu.stanford.nlp.pipeline;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;

public class ChineseTokenizationITest {


  public static List<List<String>> xmlDocSentenceTokens = new ArrayList<>();
  public static List<List<Pair<Integer,Integer>>> xmlDocCharOffsets = new ArrayList<>();

  static {
    // set token strings
    xmlDocSentenceTokens.add(Arrays.asList("巴拉克·奥巴马", "是", "美国", "总统", "。"));
    xmlDocSentenceTokens.add(Arrays.asList("他", "在", "2008年", "当选", "。"));
    // set char offsets
    xmlDocCharOffsets.add(new ArrayList<>());
    xmlDocCharOffsets.add(new ArrayList<>());
    // sentence #1
    xmlDocCharOffsets.get(0).add(new Pair<>(55,62));
    xmlDocCharOffsets.get(0).add(new Pair<>(62,63));
    xmlDocCharOffsets.get(0).add(new Pair<>(63,66));
    xmlDocCharOffsets.get(0).add(new Pair<>(66,68));
    xmlDocCharOffsets.get(0).add(new Pair<>(68,69));
    // sentence #2
    xmlDocCharOffsets.get(1).add(new Pair<>(79,80));
    xmlDocCharOffsets.get(1).add(new Pair<>(80,81));
    xmlDocCharOffsets.get(1).add(new Pair<>(81,86));
    xmlDocCharOffsets.get(1).add(new Pair<>(86,88));
    xmlDocCharOffsets.get(1).add(new Pair<>(88,89));
  }

  @Test
  public void testXMLDocWithNewlines() throws Exception {
    // set up properties
    String RESOURCE_DIR = String.format("%s/stanford-corenlp-testing/", TestPaths.testHome());
    Properties props = StringUtils.argsToProperties("-args",
        RESOURCE_DIR+"test-props/kbp-2017-chinese.properties");
    props.setProperty("annotators", "tokenize,cleanxml,ssplit,pos");
    // set up pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // set up document
    String xmlFilePath = RESOURCE_DIR+"test-docs/example-chinese-basic.xml";
    Annotation xmlAnnotation = new Annotation(IOUtils.stringFromFile(xmlFilePath));
    // annotate document
    pipeline.annotate(xmlAnnotation);
    // check correct tokenization
    int sentNum = 0;
    for (CoreMap sentence : xmlAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      int tokenNum = 0;
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        // check token text
        assertEquals(xmlDocSentenceTokens.get(sentNum).get(tokenNum), token.word());
        // check char offsets
        Pair<Integer,Integer> tokenCharOffsets =
            new Pair<>(token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
            token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
        assertEquals(xmlDocCharOffsets.get(sentNum).get(tokenNum), tokenCharOffsets);
        tokenNum++;
      }
      sentNum++;
    }
  }
}
