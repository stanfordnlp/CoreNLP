package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
import edu.stanford.nlp.hcoref.CorefSystem;
import edu.stanford.nlp.hcoref.data.CorefChain;
import edu.stanford.nlp.hcoref.data.CorefChain.CorefMention;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.*;

import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.scoref.StatisticalCorefSystem;

/**
 * This class adds coref information to an Annotation.
 *
 * A Map from id to CorefChain is put under the annotation
 * {@link edu.stanford.nlp.hcoref.CorefCoreAnnotations.CorefChainAnnotation}.
 *
 * @author heeyoung
 * @author Jason Bolton
 */

public class CorefAnnotator extends TextAnnotationCreator implements Annotator {

  private static final boolean VERBOSE = false;

  private final CorefSystem hcorefSystem;
  private final StatisticalCorefSystem scorefSystem;

  // for backward compatibility
  private final boolean OLD_FORMAT;

  // String to determine whether to use hybrid or statistical mode
  private String COREF_MODE;
  private final String HYBRID_MODE = "hybrid";
  private final String STATISTICAL_MODE = "statistical";

  private static final Map<Pair<Dictionaries.MentionType, Dictionaries.MentionType>, Double> COREF_THRESHOLDS = new HashMap<>();
  static {
    COREF_THRESHOLDS.put(new Pair<>(Dictionaries.MentionType.PROPER, Dictionaries.MentionType.PROPER), 0.3);
    COREF_THRESHOLDS.put(new Pair<>(Dictionaries.MentionType.PRONOMINAL, Dictionaries.MentionType.PRONOMINAL), 0.3);
    COREF_THRESHOLDS.put(new Pair<>(Dictionaries.MentionType.PROPER, Dictionaries.MentionType.PRONOMINAL), 0.1);
    COREF_THRESHOLDS.put(new Pair<>(Dictionaries.MentionType.PRONOMINAL, Dictionaries.MentionType.PROPER), 1.0);
    COREF_THRESHOLDS.put(new Pair<>(Dictionaries.MentionType.NOMINAL, Dictionaries.MentionType.PROPER), 1.0);
    COREF_THRESHOLDS.put(new Pair<>(Dictionaries.MentionType.PROPER, Dictionaries.MentionType.NOMINAL), 1.0);
  }

  public CorefAnnotator(Properties props) {
    try {
      COREF_MODE = props.getProperty("coref.mode", STATISTICAL_MODE);
      if (COREF_MODE.equals(HYBRID_MODE)) {
        hcorefSystem = new CorefSystem(props);
        scorefSystem = null;
      } else if (COREF_MODE.equals(STATISTICAL_MODE)) {
        // create corefSystem for statistical
        scorefSystem = StatisticalCorefSystem.fromProps(props);
        hcorefSystem = null;
      } else {
        scorefSystem = null;
        hcorefSystem = null;
      }
      OLD_FORMAT = Boolean.parseBoolean(props.getProperty("oldCorefFormat", "false"));
    } catch (Exception e) {
      System.err.println("ERROR: cannot create CorefAnnotator!");
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

      // choose between hybrid mode or statistical mode
      if (COREF_MODE.equals(HYBRID_MODE)) {
        Document corefDoc = hcorefSystem.docMaker.makeDocument(annotation);
        Map<Integer, CorefChain> result = hcorefSystem.coref(corefDoc);
        annotation.set(CorefCoreAnnotations.CorefChainAnnotation.class, result);
        // for backward compatibility
        if(OLD_FORMAT) annotateOldFormat(result, corefDoc);
      } else if (COREF_MODE.equals(STATISTICAL_MODE)) {
        scorefSystem.annotate(annotation);
      } else {
        System.err.println("ERROR: invalid selection for coreference mode!");
        throw new RuntimeException() ;
      }
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

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    Set<Class<? extends CoreAnnotation>> requirements = new HashSet<>(Arrays.asList(
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class,
        CoreAnnotations.LemmaAnnotation.class,
        CoreAnnotations.NamedEntityTagAnnotation.class
        ));
    switch (COREF_MODE) {
      case HYBRID_MODE:
        requirements.add(TreeCoreAnnotations.TreeAnnotation.class);
        requirements.add(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        requirements.add(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
        requirements.add(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
        break;
      case STATISTICAL_MODE:
        requirements.add(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        requirements.add(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
        requirements.add(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
        break;
      default:
        break;
    }
    return Collections.unmodifiableSet(requirements);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    switch (COREF_MODE) {
      case STATISTICAL_MODE:
      case HYBRID_MODE:
        return Collections.singleton(CorefCoreAnnotations.CorefChainAnnotation.class);
      default:
        throw new IllegalStateException("Unknown requirementsSatisfied() for coref mode: " + COREF_MODE);
    }
  }

}
