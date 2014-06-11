package edu.stanford.nlp.parser.metrics;

import java.io.PrintWriter;

import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.trees.Tree;

public interface ParserQueryEval {
  public void evaluate(ParserQuery query, Tree gold, PrintWriter pw);
}

