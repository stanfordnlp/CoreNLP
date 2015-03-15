package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.CorefSystem;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.CorefChain.CorefMention;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

public class HybridCorefAnnotator extends TextAnnotationCreator implements Annotator {

  private static final boolean VERBOSE = false;

  private final CorefSystem corefSystem;

  // for backward compatibility
  private final boolean OLD_FORMAT;

  public HybridCorefAnnotator(Properties props) {
    try {
      corefSystem = new CorefSystem(props);
      OLD_FORMAT = Boolean.parseBoolean(props.getProperty("oldCorefFormat", "false"));
    } catch (Exception e) {
      System.err.println("ERROR: cannot create HybridCorefAnnotator!");
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Override
  public void annotate(Annotation annotation){
    try {
      if (!annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
        System.err.println("ERROR: this coreference resolution system requires SentencesAnnotation!");
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
    List<Pair<IntTuple, IntTuple>> links = new ArrayList<Pair<IntTuple, IntTuple>>();
    CorefChain.CorefMentionComparator comparator = new CorefChain.CorefMentionComparator();

    for(CorefChain c : result.values()) {
      List<CorefMention> s = c.getMentionsInTextualOrder();
      for(CorefMention m1 : s){
        for(CorefMention m2 : s){
          if(comparator.compare(m1, m2)==1) links.add(new Pair<IntTuple, IntTuple>(m1.position, m2.position));
        }    
      }    
    }    
    return links;
  }

  private void annotateOldFormat(Map<Integer, CorefChain> result, Document corefDoc) {
    
    List<Pair<IntTuple, IntTuple>> links = getLinks(result);
    Annotation annotation = corefDoc.annotation;

    if(VERBOSE){
      System.err.printf("Found %d coreference links:\n", links.size());
      for(Pair<IntTuple, IntTuple> link: links){
        System.err.printf("LINK (%d, %d) -> (%d, %d)\n", link.first.get(0), link.first.get(1), link.second.get(0), link.second.get(1));
      }
    }

    //
    // save the coref output as CorefGraphAnnotation
    //

    // this graph is stored in CorefGraphAnnotation -- the raw links found by the coref system
    List<Pair<IntTuple, IntTuple>> graph = new ArrayList<Pair<IntTuple,IntTuple>>();

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
      graph.add(new Pair<IntTuple, IntTuple>(src, dst));
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

  private boolean hasSpeakerAnnotations(Annotation annotation) {
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
  public Set<Requirement> requires() {
    return new ArraySet<Requirement>(TOKENIZE_REQUIREMENT, SSPLIT_REQUIREMENT, POS_REQUIREMENT, NER_REQUIREMENT, DEPENDENCY_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(COREF_REQUIREMENT);
  }
  
  public static void main(String[] args) {
    Properties props = StringUtils.argsToProperties(args);
    StanfordCoreNLP corenlp = new StanfordCoreNLP(props);
    String text = "Since the implementation of the Individual Visit Scheme between Hong Kong and the mainland , more and more mainland tourists are coming to visit Hong Kong."
+"From the beginning up till now , more than seven million individual tourists , have come to Hong Kong."
+"Well , we now , er , believe more will be coming ."
+"At this point , it has been about two years . "
+"Also , the current number of 34 cities will be increased ."
+"Hong Kong was developed from a fishing harbor one hundred years ago to become today 's international metropolis ."
+"Here , eastern and western cultures have gathered , and the new and the old coexist ."
+"When in Hong Kong , you can wander among skyscrapers , heartily enjoy shopping sprees in well - known stores and malls for goods from various countries , and taste delicious snacks from all over the world at tea shops or at street stands in Mong Kok ."
+"You can go to burn incense and make a vow at the Repulse Bay , where all deities gather ."
+"You can enjoy the most charming sun - filled sandy beaches in Hong Kong."
+"You can ascend Victoria Peak to get a panoramic view of Victoria Harbor 's beautiful scenery ."
+"Or hop onto a trolley with over a century of history , and feel the city 's blend of the old and the modern in slow motion ."; 
        //"Barack Obama is the president of United States. He visited California last week.";
    Annotation document = new Annotation(text);
    corenlp.annotate(document);
    
    HybridCorefAnnotator hcoref = new HybridCorefAnnotator(props);
    hcoref.annotate(document);
    
    System.err.println();
  }
}
