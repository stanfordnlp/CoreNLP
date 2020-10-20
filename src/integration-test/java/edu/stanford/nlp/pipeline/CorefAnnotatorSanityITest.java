package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.util.StringUtils;

import static org.junit.Assert.assertEquals;

import java.util.*;
import org.junit.Test;

/** The purpose of this test is to check all flavors of coreference work
 *  when integrated with the CorefAnnotator.
 */

public class CorefAnnotatorSanityITest {

  public StanfordCoreNLP pipeline;

  public String englishDoc =
          "Barack Obama is the president of the United States. " +
                  "He was elected in 2008.  " +
                  "Over the course of the election, Obama inspired many young voters.";

  public String englishCorefResult = "(2,1,[1,2]) -> (1,2,[1,3]), that is: \"He\" -> \"Barack Obama\"\n" +
          "(3,8,[8,9]) -> (1,2,[1,3]), that is: \"Obama\" -> \"Barack Obama\"";

  public String chineseDoc = "巴拉克·奥巴马是美国总统。他在2008年当选";

  public String chineseCorefResult = "(2,1,[1,2]) -> (1,1,[1,2]), that is: \"他\" -> \"巴拉克·奥巴马\"";

  // helper to print out coref chains
  public String getCorefChainString(Map<Integer, CorefChain> corefChains) {
    String returnString = "";
    if (corefChains != null) {
      for (CorefChain chain : corefChains.values()) {
        CorefChain.CorefMention representative =
                chain.getRepresentativeMention();
        boolean outputHeading = false;
        for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
          if (mention == representative)
            continue;
          /*if (!outputHeading) {
            outputHeading = true;
            System.err.println("Coreference set:");
          }*/
          // all offsets start at 1!
          String corefResultString = String.format("(%d,%d,[%d,%d]) -> (%d,%d,[%d,%d]), that is: \"%s\" -> \"%s\"%n",
                  mention.sentNum,
                  mention.headIndex,
                  mention.startIndex,
                  mention.endIndex,
                  representative.sentNum,
                  representative.headIndex,
                  representative.startIndex,
                  representative.endIndex,
                  mention.mentionSpan,
                  representative.mentionSpan);
          returnString += corefResultString;
        }
      }
    }
    return returnString.trim();
  }

  @Test
  public void testStatisticalEnglishSlow() {
    // build pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref");
    props.setProperty("coref.algorithm", "clustering");
    props.setProperty("coref.md.type", "rule");
    pipeline = new StanfordCoreNLP(props);
    // build annotation
    Annotation annotation = new Annotation(englishDoc);
    // annotate
    pipeline.annotate(annotation);
    // check coref chains make sense
    Map<Integer, CorefChain> corefChains =
            annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    assertEquals(getCorefChainString(corefChains),englishCorefResult);
  }

  @Test
  public void testStatisticalEnglishFast() {
    // build pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref");
    props.setProperty("coref.algorithm", "statistical");
    props.setProperty("coref.md.type", "dependency");
    pipeline = new StanfordCoreNLP(props);
    // build annotation
    Annotation annotation = new Annotation(englishDoc);
    // annotate
    pipeline.annotate(annotation);
    // check coref chains make sense
    Map<Integer, CorefChain> corefChains =
            annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    assertEquals(getCorefChainString(corefChains),englishCorefResult);
  }

  @Test
  public void testNeuralEnglish() {
    // build pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,coref");
    props.setProperty("coref.algorithm", "neural");
    props.setProperty("coref.md.type", "rule");
    pipeline = new StanfordCoreNLP(props);
    // build annotation
    Annotation annotation = new Annotation(englishDoc);
    // annotate
    pipeline.annotate(annotation);
    // check coref chains make sense
    Map<Integer, CorefChain> corefChains =
            annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    assertEquals(getCorefChainString(corefChains), englishCorefResult);
  }

  @Test
  public void testHybridChinese() {
    // build pipeline
    Properties props = StringUtils.argsToProperties("-props",
            "StanfordCoreNLP-chinese.properties");
    pipeline = new StanfordCoreNLP(props);
    // build annotation
    Annotation annotation = new Annotation(chineseDoc);
    // annotate
    pipeline.annotate(annotation);
    // check coref chains make sense
    Map<Integer, CorefChain> corefChains =
            annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    assertEquals(getCorefChainString(corefChains), chineseCorefResult);
  }

  @Test
  public void testNeuralChinese() {
    // build pipeline
    Properties props = StringUtils.argsToProperties("-props",
            "StanfordCoreNLP-chinese.properties");
    props.setProperty("coref.algorithm", "neural");
    props.setProperty("coref.md.liberalChineseMD", "true");
    pipeline = new StanfordCoreNLP(props);
    // build annotation
    Annotation annotation = new Annotation(chineseDoc);
    // annotate
    pipeline.annotate(annotation);
    // check coref chains make sense
    Map<Integer, CorefChain> corefChains =
            annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    assertEquals(getCorefChainString(corefChains), chineseCorefResult);
  }

}
