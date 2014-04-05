package edu.stanford.nlp.parser.common;

import java.io.FileFilter;
import java.io.PrintStream;
import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.util.Pair;

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

  public static Pair<String, FileFilter> getTreebankDescription(String[] args, int argIndex, String flag) {
    String path = null;
    FileFilter filter = null;
    // the next arguments are the treebank path and maybe the range for testing
    int numSubArgs = numSubArgs(args, argIndex);
    if (numSubArgs > 0 && numSubArgs < 3) {
      argIndex++;
      path = args[argIndex++];
      if (numSubArgs == 2) {
        filter = new NumberRangesFileFilter(args[argIndex++], true);
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
    } else {
      throw new IllegalArgumentException("Bad arguments after " + flag);
    }
    return Pair.makePair(path, filter);
  }
}
