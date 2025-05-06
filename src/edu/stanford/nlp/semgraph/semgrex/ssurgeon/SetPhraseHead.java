package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Build a new phrase out of the matched words.
 * <br>
 * All of the words must currently be connected to themselves.  Eg, there would be one head which points to a different word, and the other words all point to that head.
 * <br>
 * If that condition is matched, then existing internal edges are replaced with edges to the new head, with the given reln <br>
 * If the head is changed, the edge out of the phrase (if it is not root) is changed to come from the new head <br>
 * Edges in to the phrase are also changed to point to the new head.
 * The purpose of that change is so for a noun phrase, for example, modifiers of that noun phrase such as nmod or nmod:desc now modify the new head
 */
public class SetPhraseHead extends SsurgeonEdit {
  public static final String LABEL = "setPhraseHead";

  final List<String> phrase;
  final int headIndex;
  final GrammaticalRelation relation;
  final double weight;

  public SetPhraseHead(List<String> nodes, Integer headIndex, GrammaticalRelation relation, double weight) {
    if (headIndex == null) {
      throw new SsurgeonParseException("SetPhraseHead expected a -headIndex, 0-indexed for the node to use as the new head");
    }
    if (headIndex < 0 || headIndex >= nodes.size()) {
      throw new SsurgeonParseException("-headIndex of " + headIndex + " is out of bounds for a phrase with " + nodes.size() + " words");
    }

    if (relation == null) {
      throw new SsurgeonParseException("SetPhraseHead expected a -reln to represent the dependency to use for the new phrase");
    }

    this.phrase = new ArrayList<>(nodes);
    this.headIndex = headIndex;
    this.relation = relation;
    this.weight = weight;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    buf.write("\t");
    for (String node : phrase) {
      buf.write("-node " + node + "\t");
    }
    buf.write("-headIndex " + headIndex + "\t");
    buf.write("-reln " + relation.toString());
    return buf.toString();
  }


  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    List<IndexedWord> matchedNodes = new ArrayList<>();
    IndexedWord newHead = null;
    int idx = 0;
    for (String word : phrase) {
      IndexedWord node = sm.getNode(word);
      if (node == null) {
        return false;
      }
      matchedNodes.add(node);

      if (idx == headIndex) {
        newHead = node;
      }
      ++idx;
    }

    SemanticGraphEdge edgeOut = null;
    List<SemanticGraphEdge> deleteEdges = new ArrayList<>();
    List<SemanticGraphEdge> relocateEdges = new ArrayList<>();
    for (IndexedWord node : matchedNodes) {
      for (SemanticGraphEdge edge : sg.incomingEdgeIterable(node)) {
        if (matchedNodes.contains(edge.getSource())) {
          // TODO: not sure keeping extra edges is correct
          if (edge.getSource() != newHead && !edge.isExtra()) {
            deleteEdges.add(edge);
          }
        } else if (edgeOut == null) {
          edgeOut = edge;
        } else {
          // oops, this wasn't a self-contained phrase.  guess we don't try to rearrange it after all
          // TODO: if the heads are the same, we could make it a phrase
          return false;
        }
      }
      for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(node)) {
        // edges which point outside the phrase will be set to have the source be the new head
        if (!matchedNodes.contains(edge.getTarget())) {
          if (edge.getSource() != newHead) {
            relocateEdges.add(edge);
          }
        }
      }
    }

    boolean modified = false;
    if (edgeOut == null) {
      // the newHead should be the root now
      Set<IndexedWord> roots = new HashSet<>(sg.getRoots());
      if (!roots.contains(newHead)) {
        modified = true;
        for (IndexedWord other : matchedNodes) {
          roots.remove(other);
        }
        roots.add(newHead);
      }
      sg.setRoots(roots);
    } else if (edgeOut.getTarget() != newHead) {
      SemanticGraphEdge newEdge = new SemanticGraphEdge(edgeOut.getSource(),
                                                        newHead,
                                                        edgeOut.getRelation(),
                                                        edgeOut.getWeight(),
                                                        edgeOut.isExtra());
      boolean success = sg.removeEdge(edgeOut);
      if (!success) {
        throw new RuntimeException("Between when the outgoing edge was found and now, the edge was somehow deleted");
      }
      sg.addEdge(newEdge);
      modified = true;
    }

    for (SemanticGraphEdge edge : relocateEdges) {
      SemanticGraphEdge newEdge = new SemanticGraphEdge(newHead,
                                                        edge.getTarget(),
                                                        edge.getRelation(),
                                                        edge.getWeight(),
                                                        edge.isExtra());
      boolean success = sg.removeEdge(edge);
      if (!success) {
        throw new RuntimeException("Between when the incoming edge was found and now, the edge was somehow deleted");
      }
      sg.addEdge(newEdge);
      modified = true;
    }

    for (SemanticGraphEdge edge : deleteEdges) {
      boolean success = sg.removeEdge(edge);
      if (!success) {
        throw new RuntimeException("Between when the internal phrase edge was found and now, the edge was somehow deleted");
      }
      modified = true;
    }
    for (IndexedWord other : matchedNodes) {
      if (other == newHead)
        continue;

      found: {
        for (SemanticGraphEdge existingEdge : sg.getAllEdges(newHead, other)) {
          if (existingEdge.getRelation().equals(relation)) {
            break found;
          }
        }
        SemanticGraphEdge newEdge = new SemanticGraphEdge(newHead,
                                                          other,
                                                          relation,
                                                          weight,
                                                          false);
        sg.addEdge(newEdge);
        modified = true;
      }
    }

    return modified;
  }

}
