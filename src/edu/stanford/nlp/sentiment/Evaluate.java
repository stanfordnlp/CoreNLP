package edu.stanford.nlp.sentiment;

import java.util.List;

import edu.stanford.nlp.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.trees.Tree;

public class Evaluate {
  final SentimentCostAndGradient cag;

  int labelsCorrect;
  int labelsIncorrect;

  public Evaluate(SentimentCostAndGradient cag) {
    this.cag = cag;
  }

  public Evaluate(SentimentModel model) {
    this.cag = new SentimentCostAndGradient(model, null);
  }

  public void eval(List<Tree> trees) {
    for (Tree tree : trees) {
      eval(tree);
    }
  }

  public void eval(Tree tree) {
    cag.forwardPropagateTree(tree);

    countTree(tree);
  }

  private void countTree(Tree tree) {
    if (tree.isLeaf()) {
      return;
    }
    for (Tree child : tree.children()) {
      countTree(child);
    }
    Integer gold = RNNCoreAnnotations.getGoldClass(tree);
    Integer predicted = RNNCoreAnnotations.getPredictedClass(tree);
    if (gold == null) {
      return;
    }
    if (gold.equals(predicted)) {
      labelsCorrect++;
    } else {
      labelsIncorrect++;
    }
  }

  public double exactNodeAccuracy() {
    return (double) labelsCorrect / ((double) (labelsCorrect + labelsIncorrect));
  }

  public void printSummary() {
    System.err.println("Tested " + (labelsCorrect + labelsIncorrect) + " labels");
    System.err.println("  " + labelsCorrect + " correct");
    System.err.println("  " + labelsIncorrect + " correct");
    System.err.println("  " + exactNodeAccuracy() + " accuracy");
  }

  public static void main(String[] args) {
    String modelPath = args[0];
    String treePath = args[1];

    List<Tree> trees = SentimentUtils.readTreesWithGoldLabels(treePath);
    SentimentModel model = SentimentModel.loadSerialized(modelPath);

    Evaluate eval = new Evaluate(model);
    eval.eval(trees);
    eval.printSummary();
  }
}
