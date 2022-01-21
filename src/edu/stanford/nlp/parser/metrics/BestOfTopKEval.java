package edu.stanford.nlp.parser.metrics;

import java.io.PrintWriter;
import java.util.List;

import edu.stanford.nlp.trees.Tree;

/**
 * Applies an AbstractEval to a list of trees to pick the best tree
 * using F1 measure.  Then uses a second AbstractEval to tally
 * statistics for the best tree chosen.  This is useful for
 * experiments to see how much the parser could improve if you were
 * able to correctly order the top N trees.
 * <br>
 * The comparisonEval will not have any useful statistics, as it will
 * tested against the top N trees for each parsing.  The countingEval
 * is the useful AbstractEval, as it is tallied only once per parse.
 * <br>
 * One example of this is the pcfgTopK eval, which looks for the best
 * LP/LR of constituents in the top K trees.
 *
 * @author John Bauer
 */
public class BestOfTopKEval {
  final private AbstractEval comparisonEval;
  final private AbstractEval countingEval;

  public BestOfTopKEval(AbstractEval comparisonEval, AbstractEval countingEval) {
    this.comparisonEval = comparisonEval;
    this.countingEval = countingEval;
  }

  public void evaluate(List<Tree> guesses, Tree gold, PrintWriter pw) {
    double bestF1 = Double.NEGATIVE_INFINITY;
    Tree bestTree = null;
    for (Tree tree : guesses) {
      comparisonEval.evaluate(tree, gold, null);
      double f1 = comparisonEval.getLastF1();
      if (bestTree == null || f1 > bestF1) {
        bestTree = tree;
        bestF1 = f1;
      }
    }

    countingEval.evaluate(bestTree, gold, pw);
  }

  public void display(boolean verbose) {
    display(verbose, new PrintWriter(System.out, true));
  }

  public void display(boolean verbose, PrintWriter pw) {
    countingEval.display(verbose, pw);
  }

  public double getEvalbF1() {
    return countingEval.getEvalbF1();
  }
}
