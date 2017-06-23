package edu.stanford.nlp.pipeline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefSystem;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefChain.CorefMention;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
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
  private static Redwood.RedwoodChannels log = Redwood.channels(CorefAnnotator.class);

  private static final boolean VERBOSE = false;

  private final CorefSystem corefSystem;

  // for backward compatibility
  private final boolean oldFormat;

  private final Properties props;

  public CorefAnnotator(Properties props) {
    this.props = props;
    try {
      corefSystem = new CorefSystem(props);
      oldFormat = Boolean.parseBoolean(props.getProperty("oldCorefFormat", "false"));
    } catch (Exception e) {
      log.error("cannot create CorefAnnotator!");
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
