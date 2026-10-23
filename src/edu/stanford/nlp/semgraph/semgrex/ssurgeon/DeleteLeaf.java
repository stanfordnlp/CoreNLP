package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * This action deletes the given node, along with its incoming edges,
 * if it is a leaf (has no outgoing edges).  The indices of the later
 * words are then shifted down by one.
 *<br>
 * The node is deleted from every graph of the sentence which has it.
 * It has to be a leaf in each of them, or with "-graph", in that graph
 * alone, in which case its edges in the other graphs, outgoing as well
 * as incoming, are deleted along with it.
 *<br>
 * An empty word stays after the word it follows, so deleting word 5
 * with 4.1 and 5.1 present makes 5.1 into 4.2, and deleting empty word
 * 2.1 makes 2.2 into 2.1.  Deleting a copy of a word, or a word which
 * has a copy left, moves no other word, as the index is still in use.
 *<br>
 * A graph's last root is never deleted: if the node is the only root of
 * any graph which has it, nothing is deleted.  A root which is deleted
 * stops being a root.
 *
 * @author lumberjack
 *
 */
public class DeleteLeaf extends SsurgeonEdit {
  /** The name of this edit in an Ssurgeon script */
  public static final String LABEL = "deleteLeaf";
  /** The name of the matched node to delete */
  protected String nodeName; // name of this node
  /** The graph in which the node has to be a leaf, or null for every graph which has it */
  protected final SemgrexGraphName graph;

  /**
   * Creates an edit which deletes the named node if it is a leaf in every graph which has it.
   *
   * @param nodeName The name of the node in the Semgrex pattern
   */
  public DeleteLeaf(String nodeName) {
    this(nodeName, null);
  }

  /**
   * Creates an edit which deletes the named node if it is a leaf in the given graph.
   *
   * @param nodeName The name of the node in the Semgrex pattern
   * @param graph The graph in which the node has to be a leaf, or null for every graph which has it
   */
  public DeleteLeaf(String nodeName, SemgrexGraphName graph) {
    this.nodeName = nodeName;
    this.graph = graph;
  }

  /**
   * Deletes the node from every graph which has it, if it is a leaf in
   * each of them, or in the graph this edit names, and it is not the only
   * root of any of them.  The words after it move down in every graph.
   *<br>
   * If executed twice on the same node, the second time there
   * will be no further updates
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    IndexedWord tgtNode = sm.getNode(nodeName);
    if (tgtNode == null) {
      return false;
    }
    List<SemanticGraph> withNode = new ArrayList<>();
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph sg = graphs.get(name);
      if (sg != null && sg.containsVertex(tgtNode)) {
        withNode.add(sg);
      }
    }
    if (withNode.isEmpty()) {
      return false;
    }
    if (graph != null) {
      SemanticGraph sg = graphs.get(graph);
      if (sg == null || !sg.containsVertex(tgtNode) || sg.hasChildren(tgtNode)) {
        return false;
      }
    } else {
      for (SemanticGraph sg : withNode) {
        if (sg.hasChildren(tgtNode)) {
          return false;
        }
      }
    }
    for (SemanticGraph sg : withNode) {
      if (sg.isRoot(tgtNode) && sg.getRoots().size() == 1) {
        // deleting it would leave this graph with no root
        return false;
      }
    }

    // removeVertex leaves the roots alone, so a deleted root is taken
    // out of the roots here
    for (SemanticGraph sg : withNode) {
      sg.removeVertex(tgtNode);
      sg.getRoots().remove(tgtNode);
    }

    // renumber the indices.  the words after the deleted one move down,
    // unless another node, such as a copy, still holds its index.  the
    // empty words are renumbered after the words they follow either way,
    // which closes the gap a deleted empty word leaves
    final int deletedIndex = tgtNode.index();
    final boolean freesIndex = tgtNode.getEmptyIndex() == 0 && !hasWordAt(graphs, deletedIndex);
    SsurgeonUtils.moveNodes(graphs, sm, x -> freesIndex && x > deletedIndex, x -> x - 1);
    return true;
  }

  /** Whether any graph has a word, rather than an empty word, at the index */
  private static boolean hasWordAt(SemgrexGraphs graphs, int index) {
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph sg = graphs.get(name);
      if (sg != null) {
        for (IndexedWord word : sg.vertexSet()) {
          if (word.index() == index && word.getEmptyIndex() == 0) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * The graph this edit checks for a leaf, if it names one other than the basic graph.
   */
  @Override
  public Set<SemgrexGraphName> requiredGraphs() {
    return graph == null || graph == SemgrexGraphName.BASIC ? Collections.emptySet() : Collections.singleton(graph);
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.NODENAME_ARG); buf.write("\t"); buf.write(nodeName);
    if (graph != null) {
      buf.write("\t");
      buf.write(Ssurgeon.GRAPH_ARG); buf.write("\t"); buf.write(graph.lowerName);
    }
    return buf.toString();
  }

}
