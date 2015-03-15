package edu.stanford.nlp.parser.metrics;

import java.io.PrintWriter;

import edu.stanford.nlp.trees.Tree;

/**
 * An interface which can be implemented by anything that evaluates
 * one tree at a time and then prints out a summary when done.  This
 * interface is convenient for eval types that do not want the p/r/f1
 * tools built in to AbstractEval.
 * <br>
 * {@see edu.stanford.nlp.parser.metrics.BestOfTopKEval} for a similar
 * data type that works on multiple trees.
 * <br>
 * @author John Bauer
 */
public interface Eval {
  public void evaluate(Tree guess, Tree gold);

  public void evaluate(Tree guess, Tree gold, PrintWriter pw);

  public void evaluate(Tree guess, Tree gold, PrintWriter pw, double weight);

  public void display(boolean verbose);

  public void display(boolean verbose, PrintWriter pw);
}
