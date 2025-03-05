package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * Go through the nodes.  Reindex them all so that the starting index is 1,
 * then all the other indices follow from there.
 *
 * Useful in cases such as when a dependency graph is split into two pieces,
 * perhaps via manual edits
 *
 * @author John Bauer
 *
 */
public class ReindexGraph extends SsurgeonEdit {
  public static final String LABEL = "reindexGraph";

  public ReindexGraph() {
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    return buf.toString();
  }

  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    // we keep indices in case there are empty nodes or copy nodes
    // for which we want to use the same index twice
    Map<Integer, Integer> knownEdits = new HashMap<>();
    boolean changed = false;

    List<IndexedWord> vertices = new ArrayList<>(sg.vertexSet());
    Collections.sort(vertices);

    int nextIndex = 1;
    for (IndexedWord vertex : vertices) {
      final int index;
      if (knownEdits.containsKey(vertex.index())) {
        index = knownEdits.get(vertex.index());
      } else {
        index = nextIndex;
        nextIndex++;
        knownEdits.put(vertex.index(), index);
      }
      if (index != vertex.index()) {
        changed = true;
        SsurgeonUtils.moveNode(sg, sm, vertex, index);
      }
    }

    return changed;
  }
}
