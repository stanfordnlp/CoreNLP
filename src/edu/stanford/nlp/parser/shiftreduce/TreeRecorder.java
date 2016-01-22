package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.metrics.ParserQueryEval;
import edu.stanford.nlp.trees.Tree;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Outputs either binarized or debinarized trees to a given filename.
 * Useful for seeing the intermediate results of the ShiftReduceParser
 *
 * @author John Bauer
 */
public class TreeRecorder implements ParserQueryEval {
  public enum Mode {
    BINARIZED, DEBINARIZED
  };

  private final Mode mode;

  private final BufferedWriter out;

  public TreeRecorder(Mode mode, String filename) {
    this.mode = mode;
    try {
      out = new BufferedWriter(new FileWriter(filename));
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public void evaluate(ParserQuery query, Tree gold, PrintWriter pw) {
    if (!(query instanceof ShiftReduceParserQuery)) {
      throw new IllegalArgumentException("This evaluator only works for the ShiftReduceParser");
    }
    
    ShiftReduceParserQuery srquery = (ShiftReduceParserQuery) query;
    try {
      switch (mode) {
      case BINARIZED:
        out.write(srquery.getBestBinarizedParse().toString());
        break;
      case DEBINARIZED:
        out.write(srquery.debinarized.toString());
        break;
      default:
        throw new IllegalArgumentException("Unknown mode " + mode);
      }
      out.newLine();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public void display(boolean verbose, PrintWriter pw) {
    try {
      out.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  
}
