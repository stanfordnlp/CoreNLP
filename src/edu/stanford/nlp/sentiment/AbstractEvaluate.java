package edu.stanford.nlp.sentiment; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.neural.rnn.TopNGramRecord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ConfusionMatrix;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Set;

/**
 *
 * @author John Bauer
 * @author <a href="mailto:haas@cl.uni-heidelberg.de"> Michael Haas </a>(extracted this abstract class from Evaluate)
 */
public abstract class AbstractEvaluate  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AbstractEvaluate.class);

  String[] equivalenceClassNames;
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
  int[][] equivalenceClasses;
  protected static final NumberFormat NF = new DecimalFormat("0.000000");

  private RNNOptions op = null;

  public AbstractEvaluate(RNNOptions options) {
    this.op = options;
    this.reset();
  }

  protected static void printConfusionMatrix(String name, int[][] confusion) {
    log.info(name + " confusion matrix");
    ConfusionMatrix<Integer> confusionMatrix = new ConfusionMatrix<>();
    confusionMatrix.setUseRealLabels(true);
    for (int i = 0; i < confusion.length; ++i) {
      for (int j = 0; j < confusion[i].length; ++j) {
        confusionMatrix.add(j, i, confusion[i][j]);
      }
    }
    log.info("\n" + confusionMatrix);
  }

  protected static double[] approxAccuracy(int[][] confusion, int[][] classes) {
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

  protected static double approxCombinedAccuracy(int[][] confusion, int[][] classes) {
    int correct = 0;
    int total = 0;
    for (int[] aClass : classes) {
      for (int j = 0; j < aClass.length; ++j) {
        for (int k = 0; k < aClass.length; ++k) {
          correct += confusion[aClass[j]][aClass[k]];
        }
        for (int k = 0; k < confusion[aClass[j]].length; ++k) {
          total += confusion[aClass[j]][k];
        }
      }
    }
    return ((double) correct) / ((double) (total));
  }

  public void reset() {
    labelsCorrect = 0;
    labelsIncorrect = 0;
    labelConfusion = new int[op.numClasses][op.numClasses];
    rootLabelsCorrect = 0;
    rootLabelsIncorrect = 0;
    rootLabelConfusion = new int[op.numClasses][op.numClasses];
    lengthLabelsCorrect = new IntCounter<>();
    lengthLabelsIncorrect = new IntCounter<>();
    equivalenceClasses = op.equivalenceClasses;
    equivalenceClassNames = op.equivalenceClassNames;
    if (op.testOptions.ngramRecordSize > 0) {
      ngrams = new TopNGramRecord(op.numClasses, op.testOptions.ngramRecordSize,
                                  op.testOptions.ngramRecordMaximumLength);
    } else {
      ngrams = null;
    }
  }

  public void eval(List<Tree> trees) {
    this.populatePredictedLabels(trees);
    for (Tree tree : trees) {
      eval(tree);
    }
  }

  public void eval(Tree tree) {
    //cag.forwardPropagateTree(tree);
    countTree(tree);
    countRoot(tree);
    countLengthAccuracy(tree);
    if (ngrams != null) {
      ngrams.countTree(tree);
    }
  }

  protected int countLengthAccuracy(Tree tree) {
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

  protected void countTree(Tree tree) {
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

  protected void countRoot(Tree tree) {
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
    Counter<Integer> results = new ClassicCounter<>();
    for (Integer key : keys) {
      results.setCount(key, lengthLabelsCorrect.getCount(key) / (lengthLabelsCorrect.getCount(key) + lengthLabelsIncorrect.getCount(key)));
    }
    return results;
  }

  public void printLengthAccuracies() {
    Counter<Integer> accuracies = lengthAccuracies();
    Set<Integer> keys = Generics.newTreeSet();
    keys.addAll(accuracies.keySet());
    log.info("Label accuracy at various lengths:");
    for (Integer key : keys) {
      log.info(StringUtils.padLeft(Integer.toString(key), 4) + ": " + NF.format(accuracies.getCount(key)));
    }
  }

  public void printSummary() {
    log.info("EVALUATION SUMMARY");
    log.info("Tested " + (labelsCorrect + labelsIncorrect) + " labels");
    log.info("  " + labelsCorrect + " correct");
    log.info("  " + labelsIncorrect + " incorrect");
    log.info("  " + NF.format(exactNodeAccuracy()) + " accuracy");
    log.info("Tested " + (rootLabelsCorrect + rootLabelsIncorrect) + " roots");
    log.info("  " + rootLabelsCorrect + " correct");
    log.info("  " + rootLabelsIncorrect + " incorrect");
    log.info("  " + NF.format(exactRootAccuracy()) + " accuracy");
    printConfusionMatrix("Label", labelConfusion);
    printConfusionMatrix("Root label", rootLabelConfusion);
    if (equivalenceClasses != null && equivalenceClassNames != null) {
      double[] approxLabelAccuracy = approxAccuracy(labelConfusion, equivalenceClasses);
      for (int i = 0; i < equivalenceClassNames.length; ++i) {
        log.info("Approximate " + equivalenceClassNames[i] + " label accuracy: " + NF.format(approxLabelAccuracy[i]));
      }
      log.info("Combined approximate label accuracy: " + NF.format(approxCombinedAccuracy(labelConfusion, equivalenceClasses)));
      double[] approxRootLabelAccuracy = approxAccuracy(rootLabelConfusion, equivalenceClasses);
      for (int i = 0; i < equivalenceClassNames.length; ++i) {
        log.info("Approximate " + equivalenceClassNames[i] + " root label accuracy: " + NF.format(approxRootLabelAccuracy[i]));
      }
      log.info("Combined approximate root label accuracy: " + NF.format(approxCombinedAccuracy(rootLabelConfusion, equivalenceClasses)));
    }
    if (op.testOptions.ngramRecordSize > 0) {
      log.info(ngrams);
    }
    if (op.testOptions.printLengthAccuracies) {
      printLengthAccuracies();
    }
  }

  /**
   * Sets the predicted sentiment label for all trees given.
   *
   * This method sets the {@link RNNCoreAnnotations.PredictedClass} annotation
   * for all nodes in all trees.
   *
   * @param trees List of Trees to be annotated
   */
  public abstract void populatePredictedLabels(List<Tree> trees);
}
