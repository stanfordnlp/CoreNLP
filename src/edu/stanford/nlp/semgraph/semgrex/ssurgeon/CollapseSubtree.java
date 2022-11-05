package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 *
 * Collapses a subtree into a single node.
 * The new node has the POS tag and index of the root node
 * and the value and the lemma of the concatenation of the subnodes.
 *
 * One intended use is to collapse multi-word expressions into one node
 * to facilitate relation extraction and related tasks.
 *
 * @author Sebastian Schuster
 *
 */

public class CollapseSubtree extends SsurgeonEdit {

  public static final String LABEL="collapseSubtree";
  protected String rootName; // Name of the root node in match


  public CollapseSubtree(String rootNodeName) {
    this.rootName = rootNodeName;
  }


  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord rootNode = this.getNamedNode(rootName, sm);
    Set<IndexedWord> subgraphNodeSet = sg.getSubgraphVertices(rootNode);
    if (subgraphNodeSet.size() == 1) {
      // our work here is done
      return false;
    }

    // TODO: this doesn't do a full search for cycles.  Is that relevant?
    // Why does this even matter?  Perhaps the only thing we care about
    // is that the root of the whole graph isn't collapsed
    // unless it stays the root
    if ( ! sg.isDag(rootNode)) {
      /* Check if there is a cycle going back to the root. */
      for (IndexedWord child : sg.getChildren(rootNode)) {
        Set<IndexedWord> reachableSet = sg.getSubgraphVertices(child);
        if (reachableSet.contains(rootNode)) {
          throw new IllegalArgumentException("Subtree cannot contain cycle leading back to root node!");
        }
      }
    }

    List<IndexedWord> sortedSubgraphNodes = Generics.newArrayList(subgraphNodeSet);
    Collections.sort(sortedSubgraphNodes);

    IndexedWord newNode = new IndexedWord(rootNode.docID(), rootNode.sentIndex(), rootNode.index());
    /* Copy all attributes from rootNode. */
    for (Class key : newNode.backingLabel().keySet()) {
      newNode.set(key, rootNode.get(key));
    }

    newNode.setValue(StringUtils.join(sortedSubgraphNodes.stream().map(IndexedWord::value), " "));
    newNode.setWord(StringUtils.join(sortedSubgraphNodes.stream().map(IndexedWord::word), " "));
    newNode.setLemma(StringUtils.join(sortedSubgraphNodes.stream().map(x -> x.lemma() == null ? x.word() : x.lemma()), " "));

    if (sg.getRoots().contains(rootNode)) {
      sg.getRoots().remove(rootNode);
      sg.addRoot(rootNode);
    }

    for (SemanticGraphEdge edge : sg.incomingEdgeIterable(rootNode)) {
      sg.addEdge(edge.getGovernor(), newNode, edge.getRelation(), edge.getWeight(), edge.isExtra());
    }

    for (IndexedWord node : sortedSubgraphNodes) {
      sg.removeVertex(node);
    }

    return true;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.NODENAME_ARG);buf.write(" ");
    buf.write(rootName);
    return buf.toString();
  }



}
