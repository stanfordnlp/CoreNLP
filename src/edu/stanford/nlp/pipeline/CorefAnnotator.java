package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Locale;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefSystem;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefChain.CorefMention;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * This class adds coref information to an Annotation.
 *
 * A Map from id to CorefChain is put under the annotation
 * {@link edu.stanford.nlp.coref.CorefCoreAnnotations.CorefChainAnnotation}.
 *
 * @author heeyoung
 * @author Jason Bolton
 */
public class CorefAnnotator extends TextAnnotationCreator implements Annotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CorefAnnotator.class);

  private final CorefSystem corefSystem;

  private final Properties props;

  public CorefAnnotator(Properties props) {
    this.props = props;
    try {
      // suppress
      props.setProperty("coref.printConLLLoadingMessage","false");
      corefSystem = new CorefSystem(props);
      props.remove("coref.printConLLLoadingMessage");
      // if user tries to run with coref.language = ENGLISH and coref.algorithm = hybrid, throw Exception
      // we do not support those settings at this time
      if (CorefProperties.algorithm(props).equals(CorefProperties.CorefAlgorithmType.HYBRID) &&
          CorefProperties.getLanguage(props).equals(Locale.ENGLISH)) {
        log.error("Error: coref.algorithm=hybrid is not supported for English, " +
            "please change coref.algorithm or coref.langauge");
        throw new RuntimeException();
      }
    } catch (Exception e) {
      log.error("Error creating CorefAnnotator...terminating pipeline construction!");
      log.error(e);
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

      corefSystem.annotate(annotation);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static List<Pair<IntTuple, IntTuple>> getLinks(Map<Integer, CorefChain> result) {
    List<Pair<IntTuple, IntTuple>> links = new ArrayList<>();
    CorefChain.CorefMentionComparator comparator = new CorefChain.CorefMentionComparator();

    for (CorefChain c : result.values()) {
      List<CorefMention> s = c.getMentionsInTextualOrder();
      for (CorefMention m1 : s) {
        for (CorefMention m2 : s) {
          if (comparator.compare(m1, m2)==1) {
            links.add(new Pair<>(m1.position, m2.position));
          }
        }
      }
    }
    return links;
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

  @SuppressWarnings("unchecked")
  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    Set<Class<? extends CoreAnnotation>> requirements = new HashSet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.ValueAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.SentenceIndexAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class,
        CoreAnnotations.LemmaAnnotation.class,
        CoreAnnotations.NamedEntityTagAnnotation.class,
        CorefCoreAnnotations.CorefMentionsAnnotation.class,
        SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class
        ));
    if (CorefProperties.mdType(this.props) != CorefProperties.MentionDetectionType.DEPENDENCY) {
      requirements.add(TreeCoreAnnotations.TreeAnnotation.class);
      requirements.add(CoreAnnotations.CategoryAnnotation.class);
    }
    return Collections.unmodifiableSet(requirements);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CorefCoreAnnotations.CorefChainAnnotation.class);
  }

}
