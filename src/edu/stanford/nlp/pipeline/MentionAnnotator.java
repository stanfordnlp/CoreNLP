package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.hcoref.CorefProperties;
import edu.stanford.nlp.hcoref.CorefSystem;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.CorefChain.CorefMention;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.hcoref.md.CorefMentionFinder;
import edu.stanford.nlp.hcoref.md.DependencyCorefMentionFinder;
import edu.stanford.nlp.hcoref.md.HybridCorefMentionFinder;
import edu.stanford.nlp.hcoref.md.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

public class MentionAnnotator extends TextAnnotationCreator implements Annotator {

  HeadFinder headFinder;
  CorefMentionFinder md;
  Dictionaries dictionaries;
  Properties corefProperties;

  public MentionAnnotator(Properties props) {
    try {
      corefProperties = props;
      System.out.println("corefProperties: "+corefProperties);
      dictionaries = new Dictionaries(props);
      System.out.println("got dictionaries");
      headFinder = getHeadFinder(props);
      md = getMentionFinder(props, headFinder);
    } catch (Exception e) {
      System.out.println("Error with building coref mention annotator!");
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    List<List<Mention>> mentions = md.findMentions(annotation, dictionaries, corefProperties);
    int currIndex = 0;
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      List<Mention> mentionsForThisSentence = mentions.get(currIndex);
      sentence.set(CorefCoreAnnotations.CorefMentionsAnnotation.class, mentionsForThisSentence);
      // increment to next list of mentions
      currIndex++;
    }
  }

  private static HeadFinder getHeadFinder(Properties props) {
    Locale lang = CorefProperties.getLanguage(props);
    if(lang == Locale.ENGLISH) return new SemanticHeadFinder();
    else if(lang == Locale.CHINESE) return new ChineseSemanticHeadFinder();
    else {
      throw new RuntimeException("Invalid language setting: cannot load HeadFinder");
    }
  }

  private static CorefMentionFinder getMentionFinder(Properties props, HeadFinder headFinder)
          throws ClassNotFoundException, IOException {

    switch (CorefProperties.getMDType(props)) {
      case RULE:
        return new RuleBasedCorefMentionFinder(headFinder, props);

      case HYBRID:
        return new HybridCorefMentionFinder(headFinder, props);

      case DEPENDENCY:
      default:  // default is dependency
        return new DependencyCorefMentionFinder(props);
    }
  }


  @Override
  public Set<Requirement> requires() {
    return Annotator.REQUIREMENTS.get(STANFORD_MENTION);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(MENTION_REQUIREMENT);
  }

  public static void main(String[] args) {

  }


}
