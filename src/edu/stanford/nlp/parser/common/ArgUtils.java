package edu.stanford.nlp.parser.common;

import java.io.FileFilter;
import java.io.PrintStream;
import java.util.regex.Pattern;
import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.io.RegExFileFilter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

/**
 * Utility methods or common blocks of code for dealing with parser
 * arguments, such as extracting Treebank information
 */
public class ArgUtils {
  private ArgUtils() {}

  // helper function
  public static int numSubArgs(String[] args, int index) {
    int i = index;
    while (i + 1 < args.length && args[i + 1].charAt(0) != '-') {
      i++;
    }
    return i - index;
  }

  public static void printArgs(String[] args, PrintStream ps) {
    ps.print("Parser invoked with arguments:");
    for (String arg : args) {
      ps.print(' ' + arg);
    }
    ps.println();
  }

  static final Pattern DOUBLE_PATTERN = Pattern.compile("[-]?[0-9]+[.][0-9]+");

  public static Pair<String, FileFilter> getTreebankDescription(String[] args, int argIndex, String flag) {
    Triple<String, FileFilter, Double> description = getWeightedTreebankDescription(args, argIndex, flag);
    return Pair.makePair(description.first(), description.second());
  }

  public static Triple<String, FileFilter, Double> getWeightedTreebankDescription(String[] args, int argIndex, String flag) {
    String path = null;
    FileFilter filter = null;
    Double weight = 1.0;
    // the next arguments are the treebank path and maybe the range for testing
    int numSubArgs = numSubArgs(args, argIndex);
    if (numSubArgs > 0 && numSubArgs < 4) {
      argIndex++;
      path = args[argIndex++];
      boolean hasWeight = false;
      if (numSubArgs > 1 && DOUBLE_PATTERN.matcher(args[argIndex + numSubArgs - 2]).matches()) {
        weight = Double.parseDouble(args[argIndex + numSubArgs - 2]);
        hasWeight = true;
        numSubArgs--;
      }
      if (numSubArgs == 2) {
        if (args[argIndex].equals("train")) {
          filter = new RegExFileFilter(".*train.*");
        } else if (args[argIndex].equals("dev")) {
          filter = new RegExFileFilter(".*dev.*");
        } else if (args[argIndex].equals("test")) {
          filter = new RegExFileFilter(".*test.*");
        } else {
          filter = new NumberRangesFileFilter(args[argIndex], true);
        }
        argIndex++;
      } else if (numSubArgs == 3) {
        try {
          int low = Integer.parseInt(args[argIndex]);
          int high = Integer.parseInt(args[argIndex + 1]);
          filter = new NumberRangeFileFilter(low, high, true);
          argIndex += 2;
        } catch (NumberFormatException e) {
          // maybe it's a ranges expression?
          filter = new NumberRangesFileFilter(args[argIndex++], true);
        }
      }
      if (hasWeight) {
        argIndex++;
      }
    } else {
      throw new IllegalArgumentException("Bad arguments after " + flag);
    }
    return Triple.makeTriple(path, filter, weight);
  }
}
