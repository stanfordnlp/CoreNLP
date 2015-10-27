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

  Set<Requirement> mentionDetectorRequirements;

  public MentionAnnotator(Properties props) {
    try {
      corefProperties = props;
      System.out.println("corefProperties: "+corefProperties);
      dictionaries = new Dictionaries(props);
      System.out.println("got dictionaries");
      headFinder = getHeadFinder(props);
      System.out.println("got head finder");
      md = getMentionFinder(props, headFinder);
      System.err.println("using mention detector: "+mdName);
    } catch (Exception e) {
      System.err.println("Error with building coref mention annotator!");
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    // check first sentence to see if it has the right parse information
    checkForRequiredAnnotations(annotation);
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    List<List<Mention>> mentions = md.findMentions(annotation, dictionaries, corefProperties);
    int currIndex = 0;
    for (CoreMap sentence : sentences) {
      List<Mention> mentionsForThisSentence = mentions.get(currIndex);
      sentence.set(CorefCoreAnnotations.CorefMentionsAnnotation.class, mentionsForThisSentence);
      // increment to next list of mentions
      currIndex++;
    }
  }

  public void checkForRequiredAnnotations(Annotation annotation) {
    // check for necessary annotations for the mention detector
    // throw an exception if something is missing
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    Set<Requirement> parseRequirementsFound = new HashSet<Requirement>();
    for (CoreMap sentence : sentences) {
      if (sentence.get(TreeCoreAnnotations.TreeAnnotation.class) != null) {
        parseRequirementsFound.add(PARSE_REQUIREMENT);
      }
      if (sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class) != null) {
        parseRequirementsFound.add(DEPENDENCY_REQUIREMENT);
      }
      // mentionDetectorRequirements is set when the mention detector is set
      for (Requirement mdRequirement : mentionDetectorRequirements) {
        if (!parseRequirementsFound.contains(mdRequirement)) {
          String missingRequirementMessage = "mention detector: "+mdName+"  missing  "+mdRequirement.name+" requirement";
          missingRequirementMessage += "; add missing annotator: "+mdRequirement.name+" ";
          missingRequirementMessage += "or change coref.md.type to resolve this issue";
          throw new RuntimeException(missingRequirementMessage);
        }
      }
      // if no problems found with first sentence don't throw exception
      return;
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
        mentionDetectorRequirements = Collections.unmodifiableSet(new ArraySet<>(DEPENDENCY_REQUIREMENT, PARSE_REQUIREMENT));
        mdName = "rule";
        return new RuleBasedCorefMentionFinder(headFinder, props);

      case HYBRID:
        mdName = "hybrid";
        mentionDetectorRequirements = Collections.unmodifiableSet(new ArraySet<>(DEPENDENCY_REQUIREMENT, PARSE_REQUIREMENT));
        return new HybridCorefMentionFinder(headFinder, props);

      case DEPENDENCY:
      default:  // default is dependency
        mdName = "dependency";
        mentionDetectorRequirements = Collections.unmodifiableSet(new ArraySet<>(DEPENDENCY_REQUIREMENT));
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