package edu.stanford.nlp.sentiment; 
import edu.stanford.nlp.util.logging.Redwood;

import java.text.DecimalFormat;
import java.util.List;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;


/** @author John Bauer */
public class Evaluate extends AbstractEvaluate  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Evaluate.class);

  final SentimentCostAndGradient cag;
  final SentimentModel model;

  // Count how many trees are unknown to the model
  // The alternate version, ExternalEvaluate, has no concept of
  // unknown, so this is exclusive to the evaluate which uses a model
  int treesWithUnks;
  int treesWithUnksCorrect;

  public Evaluate(SentimentModel model) {
    super(model.op);
    this.model = model;
    this.cag = new SentimentCostAndGradient(model, null);
  }

  @Override
  public void reset() {
    super.reset();

    treesWithUnks = 0;
    treesWithUnksCorrect = 0;
  }

  @Override
  public void eval(Tree tree) {
    super.eval(tree);

    countUnks(tree);
  }

  /**
   * Keep track of how many trees have at least one unknown, and how
   * many of those have the top level annotation correct.
   */
  protected void countUnks(Tree tree) {
    List<Label> labels = tree.yield();
    boolean hasUnk = false;
    for (Label label : labels) {
      if (!model.wordVectors.containsKey(label.value())) {
        hasUnk = true;
        break;
      }
    }

    if (hasUnk) {
      int gold = RNNCoreAnnotations.getGoldClass(tree);
      int guess = RNNCoreAnnotations.getPredictedClass(tree);

      treesWithUnks += 1;
      if (gold == guess)
        treesWithUnksCorrect += 1;
    }
  }

  private static final String FORMAT = "#.##";
  protected DecimalFormat format = new DecimalFormat(FORMAT);

  @Override
  public void printSummary() {
    super.printSummary();

    log.info("Saw " + treesWithUnks + " trees with at least one unknown token.");
    if (treesWithUnks > 0) {
      double percent = (float) treesWithUnksCorrect / treesWithUnks * 100.0;
      log.info(treesWithUnksCorrect + " / " + treesWithUnks + " trees (" + format.format(percent) +
               "%) with at least one unknown token were classified correctly at the top level.");
    }
  }

  @Override
  public void populatePredictedLabels(List<Tree> trees) {
    for (Tree tree : trees) {
      cag.forwardPropagateTree(tree);
    }
  }

  /**
   * Expected arguments are <code> -model model -treebank treebank </code>
   * <br>
   *
   * For example <br>
   * <code>
   *  java edu.stanford.nlp.sentiment.Evaluate
   *   -model edu/stanford/nlp/models/sentiment/sentiment.ser.gz
   *   -treebank /u/nlp/data/sentiment/sentiment-treebank/fiveclass/dev.txt
   * </code>
   *
   * Other arguments are available, for example <code> -numClasses</code>.
   *
   * See RNNOptions.java, RNNTestOptions.java and RNNTrainOptions.java for
   * more arguments.
   *
   * The configuration is usually derived from the RNN model file, which is
   * not available here as the predictions are external. It is the caller's
   * responsibility to provide a configuration matching the settings of
   * the external predictor. Flags of interest include
   * <code> -equivalenceClasses </code>.
   */
  public static void main(String[] args) {
    String modelPath = null;
    String treePath = null;
    boolean filterUnknown = false;
    List<String> remainingArgs = Generics.newArrayList();
    for (int argIndex = 0; argIndex < args.length;) {
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
    for (int argIndex = 0; argIndex < newArgs.length;) {
      int newIndex = model.op.setOption(newArgs, argIndex);
      if (argIndex == newIndex) {
        log.info("Unknown argument " + newArgs[argIndex]);
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
