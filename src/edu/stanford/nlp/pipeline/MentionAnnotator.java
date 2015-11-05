package edu.stanford.nlp.pipeline;

import java.io.IOException;
import java.util.*;

import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.CorefProperties;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Mention;
import edu.stanford.nlp.hcoref.md.CorefMentionFinder;
import edu.stanford.nlp.hcoref.md.DependencyCorefMentionFinder;
import edu.stanford.nlp.hcoref.md.HybridCorefMentionFinder;
import edu.stanford.nlp.hcoref.md.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;

/**
 * This class adds mention information to an Annotation.
 *
 * After annotation each sentence will have a List<Mention> representing the Mentions in the sentence
 *
 * the List<Mention> containing the Mentions will be put under the annotation
 * {@link edu.stanford.nlp.hcoref.CorefCoreAnnotations.CorefMentionsAnnotation}.
 *
 * @author heeyoung
 * @author Jason Bolton
 */

public class MentionAnnotator extends TextAnnotationCreator implements Annotator {

  HeadFinder headFinder;
  CorefMentionFinder md;
  String mdName;
  Dictionaries dictionaries;
  Properties corefProperties;

  Set<Requirement> mentionAnnotatorRequirements;

  public MentionAnnotator(Properties props) {
    try {
      corefProperties = props;
      //System.out.println("corefProperties: "+corefProperties);
      dictionaries = new Dictionaries(props);
      //System.out.println("got dictionaries");
      headFinder = getHeadFinder(props);
      //System.out.println("got head finder");
      md = getMentionFinder(props, headFinder);
      System.err.println("Using mention detector: "+mdName+" which requires these parses: "+mentionAnnotatorRequirements);
      mentionAnnotatorRequirements.addAll(Annotator.REQUIREMENTS.get(STANFORD_MENTION));
    } catch (Exception e) {
      System.err.println("Error with building coref mention annotator!");
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    // TO DO: be careful, this could introduce a really hard to find bug
    // this is necessary for Chinese coreference
    // removeNested needs to be set to "false" for newswire text or big performance drop
    if (annotation.get(CoreAnnotations.DocIDAnnotation.class).contains("nw") &&
            corefProperties.getProperty("coref.input.type").equals("conll") &&
            corefProperties.getProperty("coref.language", "en").equals("zh")) {
      CorefProperties.setRemoveNested(corefProperties, false);
    } else {
      CorefProperties.setRemoveNested(corefProperties, true);
    }
    List<List<Mention>> mentions = md.findMentions(annotation, dictionaries, corefProperties);
    int currIndex = 0;
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

  private CorefMentionFinder getMentionFinder(Properties props, HeadFinder headFinder)
          throws ClassNotFoundException, IOException {

    switch (CorefProperties.getMDType(props)) {
      case RULE:
        mentionAnnotatorRequirements = new ArraySet<>(DEPENDENCY_REQUIREMENT, PARSE_REQUIREMENT);
        mdName = "rule";
        return new RuleBasedCorefMentionFinder(headFinder, props);

      case HYBRID:
        mdName = "hybrid";
        mentionAnnotatorRequirements = new ArraySet<>(DEPENDENCY_REQUIREMENT, PARSE_REQUIREMENT);
        return new HybridCorefMentionFinder(headFinder, props);

      case DEPENDENCY:
      default:  // default is dependency
        mdName = "dependency";
        mentionAnnotatorRequirements = new ArraySet<>(DEPENDENCY_REQUIREMENT);
        return new DependencyCorefMentionFinder(props);
    }
  }

  @Override
  public Set<Requirement> requires() { return mentionAnnotatorRequirements; }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(MENTION_REQUIREMENT);
  }

  public static void main(String[] args) {

  }

}
