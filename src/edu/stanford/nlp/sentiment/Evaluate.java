package edu.stanford.nlp.sentiment; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

/** @author John Bauer */
public class Evaluate extends AbstractEvaluate  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Evaluate.class);

  final SentimentCostAndGradient cag;
  final SentimentModel model;

  public Evaluate(SentimentModel model) {
    super(model.op);
    this.model = model;
    this.cag = new SentimentCostAndGradient(model, null);
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
   *   edu/stanford/nlp/models/sentiment/sentiment.ser.gz
   *   /u/nlp/data/sentiment/trees/dev.txt
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
