package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.dcoref.Constants;
import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.Mention;
import edu.stanford.nlp.dcoref.MentionExtractor;
import edu.stanford.nlp.dcoref.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.dcoref.SieveCoreferenceSystem;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.SemanticGraphFactory.Mode;
import edu.stanford.nlp.trees.GrammaticalStructure.Extras;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntTuple;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Implements the Annotator for the new deterministic coreference resolution system.
 * In other words, this depends on: POSTaggerAnnotator, NERCombinerAnnotator (or equivalent), and ParserAnnotator.
 *
 * @author Mihai Surdeanu, based on the CorefAnnotator written by Marie-Catherine de Marneffe
 */

public class DeterministicCorefAnnotator implements Annotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(DeterministicCorefAnnotator.class);

  private static final boolean VERBOSE = false;

  private final MentionExtractor mentionExtractor;
  private final SieveCoreferenceSystem corefSystem;

  private boolean performMentionDetection;
  private CorefMentionAnnotator mentionAnnotator;


  // for backward compatibility
  private final boolean OLD_FORMAT;

  private final boolean allowReparsing;

  public DeterministicCorefAnnotator(Properties props) {
    try {
      corefSystem = new SieveCoreferenceSystem(props);
      mentionExtractor = new MentionExtractor(corefSystem.dictionaries(), corefSystem.semantics());
      OLD_FORMAT = Boolean.parseBoolean(props.getProperty("oldCorefFormat", "false"));
      allowReparsing = PropertiesUtils.getBool(props, Constants.ALLOW_REPARSING_PROP, Constants.ALLOW_REPARSING);
      // unless custom mention detection is set, just use the default coref mention detector
      performMentionDetection = !PropertiesUtils.getBool(props, "dcoref.useCustomMentionDetection", false);
      if (performMentionDetection)
        mentionAnnotator = new CorefMentionAnnotator(props);
    } catch (Exception e) {
      log.error("cannot create DeterministicCorefAnnotator!");
      log.error(e);
      throw new RuntimeException(e);
    }
  }

  // flip which granularity of ner tag is primary
  public void setNamedEntityTagGranularity(Annotation annotation, String granularity) {
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    Class<? extends CoreAnnotation<String>> sourceNERTagClass;
    if (granularity.equals("fine"))
      sourceNERTagClass = CoreAnnotations.FineGrainedNamedEntityTagAnnotation.class;
    else if (granularity.equals("coarse"))
      sourceNERTagClass = CoreAnnotations.CoarseNamedEntityTagAnnotation.class;
    else
      sourceNERTagClass = CoreAnnotations.NamedEntityTagAnnotation.class;
    // switch tags
    for (CoreLabel token : tokens) {
      if (!"".equals(token.get(sourceNERTagClass)) && token.get(sourceNERTagClass) != null)
        token.set(CoreAnnotations.NamedEntityTagAnnotation.class, token.get(sourceNERTagClass));
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    // temporarily set the primary named entity tag to the coarse tag
    setNamedEntityTagGranularity(annotation, "coarse");
    if (performMentionDetection)
      mentionAnnotator.annotate(annotation);
    try {
      List<Tree> trees = new ArrayList<>();
      List<List<CoreLabel>> sentences = new ArrayList<>();

      // extract trees and sentence words
      // we are only supporting the new annotation standard for this Annotator!
      boolean hasSpeakerAnnotations = false;
      if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
        // int sentNum = 0;
        for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
          List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
          sentences.add(tokens);
          Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
          trees.add(tree);

          SemanticGraph dependencies = SemanticGraphFactory.makeFromTree(tree, Mode.COLLAPSED, Extras.NONE, null, true); // locking here is crucial for correct threading!
          sentence.set(SemanticGraphCoreAnnotations.AlternativeDependenciesAnnotation.class, dependencies);

          if (!hasSpeakerAnnotations) {
            // check for speaker annotations
            for (CoreLabel t:tokens) {
              if (t.get(CoreAnnotations.SpeakerAnnotation.class) != null) {
                hasSpeakerAnnotations = true;
                break;
              }
            }
          }
          MentionExtractor.mergeLabels(tree, tokens);
          MentionExtractor.initializeUtterance(tokens);
        }
      } else {
        log.error("this coreference resolution system requires SentencesAnnotation!");
        return;
      }
      if (hasSpeakerAnnotations) {
        annotation.set(CoreAnnotations.UseMarkedDiscourseAnnotation.class, true);
      }

      // extract all possible mentions
      // this is created for each new annotation because it is not threadsafe
      RuleBasedCorefMentionFinder finder = new RuleBasedCorefMentionFinder(allowReparsing);
      List<List<Mention>> allUnprocessedMentions = finder.extractPredictedMentions(annotation, 0, corefSystem.dictionaries());

      // add the relevant info to mentions and order them for coref
      Document document = mentionExtractor.arrange(annotation, sentences, trees, allUnprocessedMentions);
      List<List<Mention>> orderedMentions = document.getOrderedMentions();
      if (VERBOSE) {
        for(int i = 0; i < orderedMentions.size(); i ++){
          System.err.printf("Mentions in sentence #%d:%n", i);
          for(int j = 0; j < orderedMentions.get(i).size(); j ++){
            log.info("\tMention #" + j + ": " + orderedMentions.get(i).get(j).spanToString());
          }
        }
      }


      Map<Integer, edu.stanford.nlp.coref.data.CorefChain> result = corefSystem.corefReturnHybridOutput(document);
      annotation.set(edu.stanford.nlp.coref.CorefCoreAnnotations.CorefChainAnnotation.class, result);

      if(OLD_FORMAT) {
        Map<Integer, CorefChain> oldResult = corefSystem.coref(document);
        addObsoleteCoreferenceAnnotations(annotation, orderedMentions, oldResult);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      // restore to the fine-grained
      setNamedEntityTagGranularity(annotation, "fine");
    }
  }

  // for backward compatibility with a few old things
  // TODO: Aim to get rid of this entirely
  private static void addObsoleteCoreferenceAnnotations(Annotation annotation, List<List<Mention>> orderedMentions,
                                                        Map<Integer, CorefChain> result) {
    List<Pair<IntTuple, IntTuple>> links = SieveCoreferenceSystem.getLinks(result);

    if(VERBOSE){
      System.err.printf("Found %d coreference links:\n", links.size());
      for(Pair<IntTuple, IntTuple> link: links){
        System.err.printf("LINK (%d, %d) -> (%d, %d)\n", link.first.get(0), link.first.get(1), link.second.get(0), link.second.get(1));
      }
    }

    //
    // save the coref output as CorefGraphAnnotation
    //

    // cdm 2013: this block didn't seem to be doing anything needed....
    // List<List<CoreLabel>> sents = new ArrayList<List<CoreLabel>>();
    // for (CoreMap sentence: annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
    //   List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    //   sents.add(tokens);
    // }

    // this graph is stored in CorefGraphAnnotation -- the raw links found by the coref system
    List<Pair<IntTuple, IntTuple>> graph = new ArrayList<>();

    for(Pair<IntTuple, IntTuple> link: links){
      //
      // Note: all offsets in the graph start at 1 (not at 0!)
      //       we do this for consistency reasons, as indices for syntactic dependencies start at 1
      //
      int srcSent = link.first.get(0);
      int srcTok = orderedMentions.get(srcSent - 1).get(link.first.get(1)-1).headIndex + 1;
      int dstSent = link.second.get(0);
      int dstTok = orderedMentions.get(dstSent - 1).get(link.second.get(1)-1).headIndex + 1;
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
      if (corefChain.getMentionsInTextualOrder().size() < 2) continue;
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


  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        TreeCoreAnnotations.TreeAnnotation.class,
        CoreAnnotations.NamedEntityTagAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CorefCoreAnnotations.CorefChainAnnotation.class);
  }

}
