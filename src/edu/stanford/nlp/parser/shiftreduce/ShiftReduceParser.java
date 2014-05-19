package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

public class ShiftReduceParser implements Serializable {
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

  public int findHighestScoringTransition(State state, Set<String> features, boolean requireLegal) {
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

    int bestFeature = -1;
    for (int i = 0; i < scores.length; ++i) {
      if ((bestFeature < 0 || scores[i] > scores[bestFeature]) && 
          (!requireLegal || transitionIndex.get(i).isLegal(state))) {
        bestFeature = i;
      }
    }
    
    return bestFeature;
  }

  public static State initialStateFromGoldTagTree(Tree tree) {
    List<Tree> preterminals = Generics.newArrayList();
    for (TaggedWord tw : tree.taggedYield()) {
      CoreLabel word = new CoreLabel();
      word.setValue(tw.word());
      CoreLabel tag = new CoreLabel();
      tag.setValue(tw.tag());
      
      LabeledScoredTreeNode wordNode = new LabeledScoredTreeNode(word);
      LabeledScoredTreeNode tagNode = new LabeledScoredTreeNode(tag);
      tagNode.addChild(wordNode);

      word.set(TreeCoreAnnotations.HeadWordAnnotation.class, wordNode);
      word.set(TreeCoreAnnotations.HeadTagAnnotation.class, tagNode);
      tag.set(TreeCoreAnnotations.HeadWordAnnotation.class, wordNode);
      tag.set(TreeCoreAnnotations.HeadTagAnnotation.class, tagNode);

      preterminals.add(tagNode);
    }
    return new State(preterminals);
  }

  private static final long serialVersionUID = 1;  
}

