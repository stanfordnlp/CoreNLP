package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.common.ParserQueryFactory;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ScoredObject;

public class ShiftReduceParser implements Serializable, ParserQueryFactory {
  final Index<Transition> transitionIndex;
  final Index<String> featureIndex;
  final double[][] featureWeights;

  // TODO: replace this with our own options object?
  final Options op;

  // TODO: then we could fold the featureFactory into our options
  final FeatureFactory featureFactory;

  public ShiftReduceParser(Index<Transition> transitionIndex, Index<String> featureIndex,
                           double[][] featureWeights, Options op, FeatureFactory featureFactory) {
    this.transitionIndex = transitionIndex;
    this.featureIndex = featureIndex;
    this.featureWeights = featureWeights;
    this.op = op;
    this.featureFactory = featureFactory;
  }

  public ParserQuery parserQuery() {
    return new ShiftReduceParserQuery(this);
  }

  public void outputStats() {
    int countZeros = 0;
    for (int i = 0; i < featureWeights.length; ++i) {
      for (int j = 0; j < featureWeights[i].length; ++j) {
        if (featureWeights[i][j] == 0) {
          countZeros++;
        }
      }
    }
    System.err.println("Number of zeros: " + countZeros + " out of weights: " + featureWeights.length * featureWeights[0].length);

    System.err.println("Feature index size: " + featureIndex.size());
    int wordLength = 0;
    for (String feature : featureIndex) {
      wordLength += feature.length();
    }
    System.err.println("Total word length: " + wordLength);

    System.err.println("Number of transitions: " + transitionIndex.size());
  }

  /** TODO: add an eval which measures transition accuracy? */
  public List<Eval> getExtraEvals() {
    return Collections.emptyList();
  }

  public ScoredObject<Integer> findHighestScoringTransition(State state, Set<String> features, boolean requireLegal) {
    double[] scores = new double[featureWeights.length];
    for (String feature : features) {
      int featureNum = featureIndex.indexOf(feature);
      if (featureNum >= 0) {
        // Features not in our index are represented by < 0 and are ignored
        for (int i = 0; i < scores.length; ++i) {
          scores[i] += featureWeights[i][featureNum];
        }
      }
    }

    int bestTransition = -1;
    for (int i = 0; i < scores.length; ++i) {
      if ((bestTransition < 0 || scores[i] > scores[bestTransition]) && 
          (!requireLegal || transitionIndex.get(i).isLegal(state))) {
        bestTransition = i;
      }
    }
    
    if (bestTransition >= 0) {
      return new ScoredObject<Integer>(bestTransition, scores[bestTransition]);
    } else {
      return new ScoredObject<Integer>(-1, Double.NEGATIVE_INFINITY);
    }
  }

  public static State initialStateFromGoldTagTree(Tree tree) {
    return initialStateFromTaggedSentence(tree.taggedYield());
  }

  public static State initialStateFromTaggedSentence(List<? extends HasWord> words) {
    List<Tree> preterminals = Generics.newArrayList();
    for (HasWord hw : words) {
      CoreLabel wordLabel = new CoreLabel();
      wordLabel.setValue(hw.word());
      if (!(hw instanceof HasTag)) {
        throw new RuntimeException("Expected tagged words");
      }
      String tag = ((HasTag) hw).tag();
      if (tag == null) {
        throw new RuntimeException("Word is not tagged");
      }
      CoreLabel tagLabel = new CoreLabel();
      tagLabel.setValue(((HasTag) hw).tag());
      
      LabeledScoredTreeNode wordNode = new LabeledScoredTreeNode(wordLabel);
      LabeledScoredTreeNode tagNode = new LabeledScoredTreeNode(tagLabel);
      tagNode.addChild(wordNode);

      wordLabel.set(TreeCoreAnnotations.HeadWordAnnotation.class, wordNode);
      wordLabel.set(TreeCoreAnnotations.HeadTagAnnotation.class, tagNode);
      tagLabel.set(TreeCoreAnnotations.HeadWordAnnotation.class, wordNode);
      tagLabel.set(TreeCoreAnnotations.HeadTagAnnotation.class, tagNode);

      preterminals.add(tagNode);
    }
    return new State(preterminals);
  }

  private static final long serialVersionUID = 1;  
}

