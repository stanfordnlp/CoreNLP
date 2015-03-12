package edu.stanford.nlp.trees;

import java.io.IOException;
import java.util.List;

import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Pair;

/**
 * Helper class to perform a context-sensitive mapping of POS
 * tags in a tree to universal POS tags.
 *
 * @author Sebastian Schuster
 */

public class UniversalPOSMapper {

  public static final String DEFAULT_TSURGEON_FILE = "edu/stanford/nlp/models/upos/ENUniversalPOS.tsurgeon";

  private static boolean loaded = false;

  private static List<Pair<TregexPattern, TsurgeonPattern>> operations = null;

  private UniversalPOSMapper() {} // static methods

  public static void load() {
    load(DEFAULT_TSURGEON_FILE);
  }

  public static void load(String filename) {
    loaded = true;

    try {
      operations = Tsurgeon.getOperationsFromFile(filename, "UTF-8", new TregexPatternCompiler());
    } catch (IOException e) {
      System.err.printf("%s: Warning - could not load Tsurgeon file from %s.%n",
          UniversalPOSMapper.class.getSimpleName(), filename);
    }

  }

  public static Tree mapTree(Tree t) {
    if (!loaded) {
      load();
    }

    if (operations == null) {
      return t;
    }

    return Tsurgeon.processPatternsOnTree(operations, t.deepCopy());
  }

}
