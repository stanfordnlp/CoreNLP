package edu.stanford.nlp.parser.shiftreduce;

import java.io.PrintWriter;
import java.util.List;

import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.metrics.ParserQueryEval;
import edu.stanford.nlp.trees.Tree;

/**
 * Tally and output the number of each type of transition used.
 * Useful for cases where you are adding a new transition type and
 * want to make sure it is actually firing
 */
public class TransitionTypeEval implements ParserQueryEval {
  private IntCounter<Class<? extends Transition>> transitionCounts = new IntCounter<>();

  @Override
  public void evaluate(ParserQuery query, Tree gold, PrintWriter pw) {
    if (!(query instanceof ShiftReduceParserQuery)) {
      throw new IllegalArgumentException("This evaluator only works for the ShiftReduceParser");
    }

    ShiftReduceParserQuery srquery = (ShiftReduceParserQuery) query;
    List<Transition> transitions = srquery.getBestTransitionSequence();

    for (Transition t : transitions) {
      transitionCounts.incrementCount(t.getClass());
    }
  }

  @Override
  public void display(boolean verbose, PrintWriter pw) {
    pw.println("Shift-Reduce transition type frequency");
    List<Class<? extends Transition>> sorted = Counters.toSortedList(transitionCounts);
    for (Class<? extends Transition> t : sorted) {
      String className = ShiftReduceUtils.transitionShortName(t);
      pw.println("  " + className + ": " + transitionCounts.getCount(t));
    }
  }

}

