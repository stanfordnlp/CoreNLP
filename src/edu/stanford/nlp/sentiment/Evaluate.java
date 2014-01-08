package edu.stanford.nlp.sentiment;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.neural.rnn.TopNGramRecord;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ConfusionMatrix;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

public class Evaluate {
  final SentimentCostAndGradient cag;
  final SentimentModel model;

  final int[][] equivalenceClasses;
  final String[] equivalenceClassNames;
  
  int labelsCorrect;
  int labelsIncorrect;

  // the matrix will be [gold][predicted]
  int[][] labelConfusion;

  int rootLabelsCorrect;
  int rootLabelsIncorrect;

  int[][] rootLabelConfusion;

  IntCounter<Integer> lengthLabelsCorrect;
  IntCounter<Integer> lengthLabelsIncorrect;

  TopNGramRecord ngrams;

  // TODO: make this an option
  static final int NUM_NGRAMS = 5;

  private static final NumberFormat NF = new DecimalFormat("0.000000");

  public Evaluate(SentimentModel model) {
    this.model = model;
    this.cag = new SentimentCostAndGradient(model, null);
    this.equivalenceClasses = model.op.equivalenceClasses;
    this.equivalenceClassNames = model.op.equivalenceClassNames;

    reset();
  }

  public void reset() {
    labelsCorrect = 0;
    labelsIncorrect = 0;
    labelConfusion = new int[model.op.numClasses][model.op.numClasses];

    rootLabelsCorrect = 0;
    rootLabelsIncorrect = 0;
    rootLabelConfusion = new int[model.op.numClasses][model.op.numClasses];

    lengthLabelsCorrect = new IntCounter<Integer>();
    lengthLabelsIncorrect = new IntCounter<Integer>();

    if (model.op.testOptions.ngramRecordSize > 0) {
      ngrams = new TopNGramRecord(model.op.numClasses, model.op.testOptions.ngramRecordSize, model.op.testOptions.ngramRecordMaximumLength);
    } else {
      ngrams = null;
    }
  }

  public void eval(List<Tree> trees) {
    for (Tree tree : trees) {
      eval(tree);
    }
  }

  public void eval(Tree tree) {
    cag.forwardPropagateTree(tree);

    countTree(tree);
    countRoot(tree);
    countLengthAccuracy(tree);
    if (ngrams != null) {
      ngrams.countTree(tree);
    }
  }

  private int countLengthAccuracy(Tree tree) {
    if (tree.isLeaf()) {
      return 0;
    }
    Integer gold = RNNCoreAnnotations.getGoldClass(tree);
    Integer predicted = RNNCoreAnnotations.getPredictedClass(tree);
    int length;
    if (tree.isPreTerminal()) {
      length = 1;
    } else {
      length = 0;
      for (Tree child : tree.children()) {
        length += countLengthAccuracy(child);
      }
    }
    if (gold >= 0) {
      if (gold.equals(predicted)) {
        lengthLabelsCorrect.incrementCount(length);
      } else {
        lengthLabelsIncorrect.incrementCount(length);
      }
    }
    return length;
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
    if (gold >= 0) {
      if (gold.equals(predicted)) {
        labelsCorrect++;
      } else {
        labelsIncorrect++;
      }
      labelConfusion[gold][predicted]++;
    }
  }

  private void countRoot(Tree tree) {
    Integer gold = RNNCoreAnnotations.getGoldClass(tree);
    Integer predicted = RNNCoreAnnotations.getPredictedClass(tree);
    if (gold >= 0) {
      if (gold.equals(predicted)) {
        rootLabelsCorrect++;
      } else {
        rootLabelsIncorrect++;
      }
      rootLabelConfusion[gold][predicted]++;
    }
  }

  public double exactNodeAccuracy() {
    return (double) labelsCorrect / ((double) (labelsCorrect + labelsIncorrect));
  }

  public double exactRootAccuracy() {
    return (double) rootLabelsCorrect / ((double) (rootLabelsCorrect + rootLabelsIncorrect));
  }

  public Counter<Integer> lengthAccuracies() {
    Set<Integer> keys = Generics.newHashSet();
    keys.addAll(lengthLabelsCorrect.keySet());
    keys.addAll(lengthLabelsIncorrect.keySet());

    Counter<Integer> results = new ClassicCounter<Integer>();
    for (Integer key : keys) {
      results.setCount(key, lengthLabelsCorrect.getCount(key) / (lengthLabelsCorrect.getCount(key) + lengthLabelsIncorrect.getCount(key)));
    }
    return results;
  }

  public void printLengthAccuracies() {
    Counter<Integer> accuracies = lengthAccuracies();
    Set<Integer> keys = Generics.newTreeSet();
    keys.addAll(accuracies.keySet());
    System.err.println("Label accuracy at various lengths:");
    for (Integer key : keys) {
      System.err.println(StringUtils.padLeft(Integer.toString(key), 4) + ": " + NF.format(accuracies.getCount(key)));
    }
  }

  private static void printConfusionMatrix(String name, int[][] confusion) {
    System.err.println(name + " confusion matrix");
    ConfusionMatrix<Integer> confusionMatrix = new ConfusionMatrix<Integer>();
    confusionMatrix.setUseRealLabels(true);
    for (int i = 0; i < confusion.length; ++i) {
      for (int j = 0; j < confusion[i].length; ++j) {
        confusionMatrix.add(j, i, confusion[i][j]);
      }
    }
    System.err.println(confusionMatrix);
  }

  private static double[] approxAccuracy(int[][] confusion, int[][] classes) {
    int[] correct = new int[classes.length];
    int[] total = new int[classes.length];
    double[] results = new double[classes.length];
    for (int i = 0; i < classes.length; ++i) {
      for (int j = 0; j < classes[i].length; ++j) {
        for (int k = 0; k < classes[i].length; ++k) {
          correct[i] += confusion[classes[i][j]][classes[i][k]];
        }
        for (int k = 0; k < confusion[classes[i][j]].length; ++k) {
          total[i] += confusion[classes[i][j]][k];
        }
      }
      results[i] = ((double) correct[i]) / ((double) (total[i]));
    }
    return results;
  }

  private static double approxCombinedAccuracy(int[][] confusion, int[][] classes) {
    int correct = 0;
    int total = 0;
    for (int i = 0; i < classes.length; ++i) {
      for (int j = 0; j < classes[i].length; ++j) {
        for (int k = 0; k < classes[i].length; ++k) {
          correct += confusion[classes[i][j]][classes[i][k]];
        }
        for (int k = 0; k < confusion[classes[i][j]].length; ++k) {
          total += confusion[classes[i][j]][k];
        }
      }
    }

    return ((double) correct) / ((double) (total));
  }

  public void printSummary() {
    System.err.println("EVALUATION SUMMARY");
    System.err.println("Tested " + (labelsCorrect + labelsIncorrect) + " labels");
    System.err.println("  " + labelsCorrect + " correct");
    System.err.println("  " + labelsIncorrect + " incorrect");
    System.err.println("  " + NF.format(exactNodeAccuracy()) + " accuracy");
    System.err.println("Tested " + (rootLabelsCorrect + rootLabelsIncorrect) + " roots");
    System.err.println("  " + rootLabelsCorrect + " correct");
    System.err.println("  " + rootLabelsIncorrect + " incorrect");
    System.err.println("  " + NF.format(exactRootAccuracy()) + " accuracy");

    printConfusionMatrix("Label", labelConfusion);
    printConfusionMatrix("Root label", rootLabelConfusion);

    if (equivalenceClasses != null && equivalenceClassNames != null) {
      double[] approxLabelAccuracy = approxAccuracy(labelConfusion, equivalenceClasses);
      for (int i = 0; i < equivalenceClassNames.length; ++i) {
        System.err.println("Approximate " + equivalenceClassNames[i] + " label accuracy: " + NF.format(approxLabelAccuracy[i]));
      }
      System.err.println("Combined approximate label accuracy: " + NF.format(approxCombinedAccuracy(labelConfusion, equivalenceClasses)));
      
      double[] approxRootLabelAccuracy = approxAccuracy(rootLabelConfusion, equivalenceClasses);
      for (int i = 0; i < equivalenceClassNames.length; ++i) {
        System.err.println("Approximate " + equivalenceClassNames[i] + " root label accuracy: " + NF.format(approxRootLabelAccuracy[i]));
      }
      System.err.println("Combined approximate root label accuracy: " + NF.format(approxCombinedAccuracy(rootLabelConfusion, equivalenceClasses)));
      System.err.println();
    }

    if (model.op.testOptions.ngramRecordSize > 0) {
      System.err.println(ngrams);
    }

    if (model.op.testOptions.printLengthAccuracies) {
      printLengthAccuracies();
    }
  }

  /**
   * Expected arguments are <code> -model model -treebank treebank </code> <br>
   *
   * For example <br>
   * <code> 
   *  java edu.stanford.nlp.sentiment.Evaluate 
   *   edu/stanford/nlp/models/sentiment/sentiment.ser.gz 
   *   /u/nlp/data/sentiment/trees/dev.txt
   * </code>
   */
  public static void main(String[] args) {
    String modelPath = null;
    String treePath = null;
    boolean filterUnknown = false;

    List<String> remainingArgs = Generics.newArrayList();

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-model")) {
        modelPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-treebank")) {
        treePath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-filterUnknown")) {
        filterUnknown = true;
        argIndex++;
      } else {
        remainingArgs.add(args[argIndex]);
        argIndex++;
      }
    }

    String[] newArgs = new String[remainingArgs.size()];
    remainingArgs.toArray(newArgs);

    SentimentModel model = SentimentModel.loadSerialized(modelPath);
    for (int argIndex = 0; argIndex < newArgs.length; ) {
      int newIndex = model.op.setOption(newArgs, argIndex);
      if (argIndex == newIndex) {
        System.err.println("Unknown argument " + newArgs[argIndex]);
        throw new IllegalArgumentException("Unknown argument " + newArgs[argIndex]);
      }
      argIndex = newIndex;
    }

    List<Tree> trees = SentimentUtils.readTreesWithGoldLabels(treePath);
    if (filterUnknown) {
      trees = SentimentUtils.filterUnknownRoots(trees);
    }

    Evaluate eval = new Evaluate(model);
    eval.eval(trees);
    eval.printSummary();
  }
}
