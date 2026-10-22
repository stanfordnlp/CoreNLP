package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * Go through the nodes.  Reindex them all so that the starting index is 1,
 * then all the other indices follow from there.
 *<br>
 * Useful in cases such as when a dependency graph is split into two pieces,
 * perhaps via manual edits
 *<br>
 * The words of every graph of the sentence are numbered together, in
 * order, so a word which only one graph has still gets its own place.
 * A copy of a word keeps the index of the word.  An empty word follows
 * the nearest word before it, as {@link SsurgeonUtils#moveNodes} places
 * it, so one before the first word stays before it, and the empty words
 * after each word are numbered from 1.
 *
 * @author John Bauer
 *
 */
public class ReindexGraph extends SsurgeonEdit {
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL = "reindexGraph";

  /** Creates the edit; it has no arguments. */
  public ReindexGraph() {
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    return buf.toString();
  }

  /**
   * Reindexes the graph, as {@link #evaluate(SemgrexGraphs, SemgrexMatcher)} does
   * for a sentence with only this graph.
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    return evaluate(new SemgrexGraphs(sg), sm);
  }

  /**
   * Numbers the words of every graph of the sentence from 1, in order.
   *
   * @return whether any word or empty word got a new index
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    // the index of each word in any graph, which a copy of the word shares
    Set<Integer> wordIndices = new TreeSet<>();
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph sg = graphs.get(name);
      if (sg == null) {
        continue;
      }
      for (IndexedWord vertex : sg.vertexSet()) {
        if (vertex.getEmptyIndex() == 0) {
          wordIndices.add(vertex.index());
        }
      }
    }
    Map<Integer, Integer> newIndices = new HashMap<>();
    int nextIndex = 1;
    for (Integer index : wordIndices) {
      newIndices.put(index, nextIndex++);
    }

    return SsurgeonUtils.moveNodes(graphs, sm, x -> !newIndices.get(x).equals(x), newIndices::get);
  }

  /**
   * ReindexGraph renumbers the words of every graph.
   *
   * @return true
   */
  @Override
  public boolean changesWordsInEveryGraph() {
    return true;
  }
}
