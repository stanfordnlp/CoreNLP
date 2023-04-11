package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * Combines two words into one word
 *<br>
 * This requires one of the nodes to be the head of a phrase of the words,
 * and the dependent words can't have any extra edges in or out of that subgraph
 *<br>
 * The word and lemma will be the combination of the words, squished together.
 * Before and after will be updated to use the before and after of the endpoints of the subgraph
 *
 * @author John Bauer
 */
public class MergeNodes extends SsurgeonEdit {
  public static final String LABEL = "mergeNodes";
  final List<String> nodes;
  final Map<String, String> attributes;

  public MergeNodes(List<String> nodes, Map<String, String> attributes) {
    if (nodes.size() > 2) {
      throw new SsurgeonParseException("Cannot support MergeNodes of size " + nodes.size() + " yet... please file an issue on github if you need this feature");
    }
    this.nodes = new ArrayList<>(nodes);
    this.attributes = new TreeMap<>(attributes);
  }

  /**
   * Emits a parseable instruction string.
   */
  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    for (String name : nodes) {
      buf.write("\t");
      buf.write(Ssurgeon.NODENAME_ARG + " " + name);
    }

    // TODO: some attributes might need to be escaped!
    for (String key : attributes.keySet()) {
      buf.write("\t-");
      buf.write(key);
      buf.write(" ");
      buf.write(attributes.get(key));
    }

    return buf.toString();
  }

  /**
   * If the two named nodes are next to each other, and the edges of
   * the graph allow for it, squish the two words into one word
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    String name1 = nodes.get(0);
    String name2 = nodes.get(1);

    IndexedWord node1 = sm.getNode(name1);
    IndexedWord node2 = sm.getNode(name2);

    if (node1 == null || node2 == null) {
      return false;
    }

    List<SemanticGraphEdge> n1_to_n2 = sg.getAllEdges(node1, node2);
    List<SemanticGraphEdge> n2_to_n1 = sg.getAllEdges(node2, node1);
    if (n1_to_n2.size() == 0 && n2_to_n1.size() == 0) {
      return false;
    }

    // TODO: what about the case where the dep is or has copies?
    final IndexedWord head;
    final IndexedWord dep;

    if (n1_to_n2.size() > 0) {
      head = node1;
      dep = node2;
    } else {
      head = node2;
      dep = node1;
    }

    // If the dep has any edges that aren't between dep & head, abort
    // TODO: we could probably make it adjust edges with "dep" as source, instead
    for (SemanticGraphEdge e : sg.outgoingEdgeIterable(dep)) {
      if (e.getTarget() != head) {
        return false;
      }
    }
    for (SemanticGraphEdge e : sg.incomingEdgeIterable(dep)) {
      if (e.getSource() != head) {
        return false;
      }
    }

    IndexedWord left;
    IndexedWord right;
    if (node1.index() < node2.index()) {
      left = node1;
      right = node2;
    } else {
      left = node2;
      right = node1;
    }

    CoreLabel newLabel = AddDep.fromCheapStrings(attributes);
    // CoreLabel.setWord wipes out the lemma for some reason
    // we may eventually change that, but for now, we compensate for that here
    String lemma = newLabel.lemma();
    if (newLabel.word() == null) {
      String newWord = left.word() + right.word();
      newLabel.setWord(newWord);
    }
    if (newLabel.value() == null) {
      newLabel.setValue(newLabel.word());
    }

    newLabel.setLemma(lemma);
    if (newLabel.lemma() == null) {
      String newLemma = left.lemma() != null && right.lemma() != null ? left.lemma() + right.lemma() : null;
      newLabel.setLemma(newLemma);
    }
    // after() and before() return "" if null, so we need to use the CoreAnnotations directly
    if (newLabel.get(CoreAnnotations.AfterAnnotation.class) == null) {
      newLabel.setAfter(right.after());
    }
    if (newLabel.get(CoreAnnotations.BeforeAnnotation.class) == null) {
      newLabel.setBefore(right.before());
    }

    for (IndexedWord vertex : sg.vertexSet()) {
      if (vertex.index() == head.index()) {
        for (Class key : newLabel.keySet()) {
          Object value = newLabel.get(key);
          vertex.set(key, value);
        }
      }
    }

    // copy the list so that deletion doesn't hurt the iterator
    // TODO: super fancy would be implementing iterator.remove()
    // on the Set returned by the SemanticGraph
    for (IndexedWord vertex : sg.vertexListSorted()) {
      if (vertex.index() == dep.index()) {
        sg.removeVertex(vertex);
      }
    }

    // reindex everyone
    AddDep.moveNodes(sg, sm, x -> (x >= dep.index()), x -> x-1, false);

    return true;
  }

}

