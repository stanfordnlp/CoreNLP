package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

/**
 * Refactored a useful semgrex key comparison, so as to allow multiple places to use it
 *
 * @author John Bauer
 */
public class SemgrexUtils {
  /** Creates a SemgrexUtils; all of its methods are static. */
  public SemgrexUtils() { }

  /**
   * Compares two keys element by element.  A null key sorts after a
   * non-null key.  Keys which match up to the length of the shorter
   * key are considered equal.
   *
   * @param first The first key
   * @param second The second key
   * @return A negative number, zero, or a positive number as {@code first}
   * sorts before, equal to, or after {@code second}
   */
  static public int compareKeys(List<String> first, List<String> second) {
    if (first == null && second == null) {
      return 0;
    }
    if (second == null) {
      return -1;
    }
    if (first == null) {
      return 1;
    }
    for (int idx = 0; idx < first.size() && idx < second.size(); ++idx) {
      int cmp = first.get(idx).compareTo(second.get(idx));
      if (cmp != 0) {
        return cmp;
      }
    }
    // what if they are different lengths?
    // shouldn't happen here anyway
    return 0;
  }

  /**
   * For a pattern such as UniqPattern, get this key from the SemgrexMatch.
   *<br>
   * The key is expected to be unambiguous, which can be enforced at SemgrexPattern compilation time
   *
   * @param match The match to read from
   * @param key The name of a node, variable group, or edge, checked in that order
   * @return The node's value, the variable's string, or the edge's relation,
   * or null if nothing in the match has that name
   */
  static public String getKey(SemgrexMatch match, String key) {
    IndexedWord node = match.getNode(key);
    if (node != null) {
      return node.value();
    }
    String varString = match.getVariableString(key);
    if (varString != null) {
      return varString;
    }
    SemanticGraphEdge edge = match.getEdge(key);
    if (edge != null) {
      return edge.getRelation().toString();
    }
    return null;
  }

  /**
   * Builds a key from a match using {@link #getKey} for each name.
   *
   * @param match The match to read from
   * @param keys The names to look up
   * @return The value for each name, with null for names not in the match
   */
  static public List<String> buildKey(SemgrexMatch match, List<String> keys) {
    List<String> matchKey = new ArrayList<>();
    for (String key : keys) {
      matchKey.add(getKey(match, key));
    }
    return matchKey;
  }

  static class KeyPairComparator implements Comparator<Pair<Integer, List<String>>> {
    public int compare(Pair<Integer, List<String>> first, Pair<Integer, List<String>> second) {
      return SemgrexUtils.compareKeys(first.second, second.second);
    }
  }

  /**
   * Reads trees from a file and converts them to English dependency graphs.
   * Each graph is stored as the {@code BasicDependenciesAnnotation} of a
   * new sentence, whatever {@code mode} is, with the graph's words as
   * its tokens.
   *
   * @param treeFile The file or directory of trees to read
   * @param mode Which kind of dependencies to build
   * @param useExtras Whether to include all extra dependencies
   * @return One sentence per tree
   */
  public static List<CoreMap> readTreeFile(String treeFile, SemanticGraphFactory.Mode mode, boolean useExtras) {
    List<CoreMap> sentences = new ArrayList<>();
    MemoryTreebank treebank = new MemoryTreebank(new TreeNormalizer());
    treebank.loadPath(treeFile);
    for (Tree tree : treebank) {
      // TODO: allow other languages... this defaults to English
      SemanticGraph graph = SemanticGraphFactory.makeFromTree(tree, mode, useExtras ?
              GrammaticalStructure.Extras.MAXIMAL : GrammaticalStructure.Extras.NONE);
      CoreMap sentence = new ArrayCoreMap();
      sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
      List<CoreLabel> tokens = graph.vertexListSorted().stream().map(x -> x.backingLabel()).collect(Collectors.toList());
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
      sentences.add(sentence);
    }
    return sentences;
  }
}
