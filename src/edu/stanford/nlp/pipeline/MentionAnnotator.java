package edu.stanford.nlp.pipeline;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.md.CorefMentionFinder;
import edu.stanford.nlp.coref.md.DependencyCorefMentionFinder;
import edu.stanford.nlp.coref.md.HybridCorefMentionFinder;
import edu.stanford.nlp.coref.md.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * This class adds mention information to an Annotation.
 *
 * After annotation each sentence will have a List<Mention> representing the Mentions in the sentence
 *
 * the List<Mention> containing the Mentions will be put under the annotation
 * {@link edu.stanford.nlp.coref.CorefCoreAnnotations.CorefMentionsAnnotation}.
 *
 * @author heeyoung
 * @author Jason Bolton
 */

public class MentionAnnotator extends TextAnnotationCreator implements Annotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(MentionAnnotator.class);

  HeadFinder headFinder;
  CorefMentionFinder md;
  String mdName;
  Dictionaries dictionaries;
  Properties corefProperties;

  Set<Class<? extends CoreAnnotation>> mentionAnnotatorRequirements = new HashSet<>();

  public MentionAnnotator(Properties props) {
    try {
      corefProperties = props;
      //System.out.println("corefProperties: "+corefProperties);
      dictionaries = new Dictionaries(props);
      //System.out.println("got dictionaries");
      headFinder = getHeadFinder(props);
      //System.out.println("got head finder");
      md = getMentionFinder(props, headFinder);
      log.info("Using mention detector type: "+mdName);
      mentionAnnotatorRequirements.addAll(Arrays.asList(
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class,
          CoreAnnotations.SentenceIndexAnnotation.class,
          CoreAnnotations.PartOfSpeechAnnotation.class,
          CoreAnnotations.NamedEntityTagAnnotation.class,
          CoreAnnotations.IndexAnnotation.class,
          CoreAnnotations.TextAnnotation.class,
          SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
          SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class

      ));
    } catch (Exception e) {
      log.err("Error with building coref mention annotator!", e);
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    // TO DO: be careful, this could introduce a really hard to find bug
    // this is necessary for Chinese coreference
    // removeNested needs to be set to "false" for newswire text or big performance drop
    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
    if (docID == null) {
      docID = "";
    }
    if (docID.contains("nw") && corefProperties.getProperty("coref.input.type", "raw").equals("conll") &&
            corefProperties.getProperty("coref.language", "en").equals("zh") &&
            PropertiesUtils.getBool(corefProperties,"coref.specialCaseNewswire")) {
      CorefProperties.setRemoveNestedMentions(corefProperties, false);
    } else {
      CorefProperties.setRemoveNestedMentions(corefProperties, true);
    }
    List<List<Mention>> mentions = md.findMentions(annotation, dictionaries, corefProperties);
    int mentionIndex = 0;
    int currIndex = 0;
    for (CoreMap sentence : sentences) {
      List<Mention> mentionsForThisSentence = mentions.get(currIndex);
      sentence.set(CorefCoreAnnotations.CorefMentionsAnnotation.class, mentionsForThisSentence);
      // increment to next list of mentions
      currIndex++;
      // assign latest mentionID
      for (Mention m : mentionsForThisSentence) {
        m.mentionID = mentionIndex;
        mentionIndex++;
      }
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
      case DEPENDENCY:
        mdName = "dependency";
        return new DependencyCorefMentionFinder(props);

      case HYBRID:
        mdName = "hybrid";
        mentionAnnotatorRequirements.add(TreeCoreAnnotations.TreeAnnotation.class);
        mentionAnnotatorRequirements.add(CoreAnnotations.BeginIndexAnnotation.class);
        mentionAnnotatorRequirements.add(CoreAnnotations.EndIndexAnnotation.class);
        return new HybridCorefMentionFinder(headFinder, props);

      case RULE:
      default:
        mentionAnnotatorRequirements.add(TreeCoreAnnotations.TreeAnnotation.class);
        mentionAnnotatorRequirements.add(CoreAnnotations.BeginIndexAnnotation.class);
        mentionAnnotatorRequirements.add(CoreAnnotations.EndIndexAnnotation.class);
        mdName = "rule";
        return new RuleBasedCorefMentionFinder(headFinder, props);
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return mentionAnnotatorRequirements;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CorefCoreAnnotations.CorefMentionsAnnotation.class,
        CoreAnnotations.ParagraphAnnotation.class,
        CoreAnnotations.SpeakerAnnotation.class,
        CoreAnnotations.UtteranceAnnotation.class
    )));
  }

}
