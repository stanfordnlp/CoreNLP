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
