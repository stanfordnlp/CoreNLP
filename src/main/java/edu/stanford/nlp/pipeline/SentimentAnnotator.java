package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.sentiment.CollapseUnaryTransformer;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCostAndGradient;
import edu.stanford.nlp.sentiment.SentimentModel;
import edu.stanford.nlp.sentiment.SentimentUtils;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * This annotator attaches a binarized tree with sentiment annotations
 * to each sentence.  It requires there to already be binarized trees
 * attached to the sentence, which is best done in the
 * ParserAnnotator.
 *
 * The tree will be attached to each sentence in the
 * SentencesAnnotation via the SentimentCoreAnnotations.SentimentAnnotatedTree
 * annotation.  The class name for the top level class is also set
 * using the SentimentCoreAnnotations.SentimentClass annotation.
 *
 * The reason the decision was made to do the binarization in the
 * ParserAnnotator is because it may require specific options set in
 * the parser.  An alternative would be to do the binarization here,
 * which would require at a minimum the HeadFinder used in the parser.
 *
 * @author John Bauer
 */
public class SentimentAnnotator extends SentenceAnnotator {

  private static final String DEFAULT_MODEL = "edu/stanford/nlp/models/sentiment/sentiment.ser.gz";

  private final String modelPath;
  private final SentimentModel model;
  private final CollapseUnaryTransformer transformer = new CollapseUnaryTransformer();

  private final int nThreads;

  /**
   * Stop processing if we exceed this time limit, in milliseconds.
   * Use 0 for no limit.
   */
  private final long maxTime;

  public SentimentAnnotator(String annotatorName, Properties props) {
    this.modelPath = props.getProperty(annotatorName + ".model", DEFAULT_MODEL);
    if (modelPath == null) {
      throw new IllegalArgumentException("No model specified for Sentiment annotator");
    }
    this.model = SentimentModel.loadSerialized(modelPath);
    this.nThreads = PropertiesUtils.getInt(props, annotatorName + ".nthreads", PropertiesUtils.getInt(props, "nthreads", 1));
    this.maxTime = PropertiesUtils.getLong(props, annotatorName + ".maxtime", -1);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.PartOfSpeechAnnotation.class,
        TreeCoreAnnotations.TreeAnnotation.class,
        TreeCoreAnnotations.BinarizedTreeAnnotation.class,
        CoreAnnotations.CategoryAnnotation.class
    )));
  }

  public static String signature(String annotatorName, Properties props) {
    StringBuilder os = new StringBuilder();
    os.append(annotatorName + ".model:" +
              props.getProperty(annotatorName + ".model", DEFAULT_MODEL));
    os.append(annotatorName + ".nthreads:" +
              props.getProperty(annotatorName + ".nthreads", props.getProperty("nthreads", "")));
    os.append(annotatorName + ".maxtime:" +
              props.getProperty(annotatorName + ".maxtime", "-1"));
    return os.toString();
  }

  @Override
  protected int nThreads() {
    return nThreads;
  }

  @Override
  protected long maxTime() {
    return maxTime;
  }

  @Override
  public void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    // not sure what to do here, so just bail
  }

  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    Tree binarized = sentence.get(TreeCoreAnnotations.BinarizedTreeAnnotation.class);
    if (binarized == null) {
      throw new AssertionError("Binarized sentences not built by parser");
    }
    Tree collapsedUnary = transformer.transformTree(binarized);
    SentimentCostAndGradient scorer = new SentimentCostAndGradient(model, null);
    scorer.forwardPropagateTree(collapsedUnary);
    sentence.set(SentimentCoreAnnotations.SentimentAnnotatedTree.class, collapsedUnary);
    int sentiment = RNNCoreAnnotations.getPredictedClass(collapsedUnary);
    sentence.set(SentimentCoreAnnotations.SentimentClass.class, SentimentUtils.sentimentString(model, sentiment));
    Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    if (tree != null) {
      collapsedUnary.setSpans();
      // map the sentiment annotations onto the tree
      Map<IntPair,String> spanSentiment = Generics.newHashMap();
      for (Tree bt : collapsedUnary) {
        IntPair p = bt.getSpan();
        int sen = RNNCoreAnnotations.getPredictedClass(bt);
        String sentStr = SentimentUtils.sentimentString(model, sen);
        if ( ! spanSentiment.containsKey(p)) {
          // we'll take the first = highest one discovered
          spanSentiment.put(p, sentStr);
        }
      }
      if (((CoreLabel) tree.label()).containsKey(CoreAnnotations.SpanAnnotation.class)) {
        throw new IllegalStateException("This code assumes you don't have SpanAnnotation");
      }
      tree.setSpans();
      for (Tree t : tree) {
        IntPair p = t.getSpan();
        String str = spanSentiment.get(p);
        if (str != null) {
          CoreLabel cl = (CoreLabel) t.label();
          cl.set(SentimentCoreAnnotations.SentimentClass.class, str);
          cl.remove(CoreAnnotations.SpanAnnotation.class);
        }
      }
    }
  }

}
