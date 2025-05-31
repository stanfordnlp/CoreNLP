package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  final List<String> names;
  final Map<String, String> attributes;

  public MergeNodes(List<String> names, Map<String, String> attributes) {
    this.names = new ArrayList<>(names);
    this.attributes = new TreeMap<>(attributes);
  }

  /**
   * Emits a parseable instruction string.
   */
  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    for (String name : names) {
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
   * If the named nodes are next to each other, and the edges of
   * the graph allow for it, squish those words into one word
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    List<IndexedWord> nodes = new ArrayList<>();
    for (String name : names) {
      IndexedWord node = sm.getNode(name);
      if (node == null) {
        return false;
      }
      nodes.add(node);
    }
    Collections.sort(nodes);

    IndexedWord head = null;
    for (IndexedWord candidate : nodes) {
      if (sg.hasChildren(candidate)) {
        // if multiple nodes have children inside the graph,
        // perhaps we could merge them all,
        // but the easiest thing to do is just abort
        // TODO: an alternate approach would be to look for nodes with a head
        // outside the nodes in the phrase to merge
        if (head != null) {
          return false;
        }
        head = candidate;
      }
    }

    Set<Integer> depIndices = new HashSet<Integer>();
    for (IndexedWord other : nodes) {
      if (other == head) {
        continue;
      }
      Set<IndexedWord> parents = sg.getParents(other);
      // this shouldn't happen
      if (parents.size() == 0) {
        return false;
      }
      // iterate instead of just do the first in case
      // this one day is doing an graph with extra dependencies
      for (IndexedWord parent : parents) {
        if (parent != head) {
          return false;
        }
      }
      depIndices.add(other.index());
    }

    CoreLabel newLabel = AddDep.fromCheapStrings(attributes);
    // CoreLabel.setWord wipes out the lemma for some reason
    // we may eventually change that, but for now, we compensate for that here
    String lemma = newLabel.lemma();

    if (newLabel.word() == null) {
      StringBuilder newWord = new StringBuilder();
      for (IndexedWord node : nodes) {
        newWord.append(node.word());
      }
      newLabel.setWord(newWord.toString());
    }
    if (newLabel.value() == null) {
      newLabel.setValue(newLabel.word());
    }

    newLabel.setLemma(lemma);
    if (newLabel.lemma() == null) {
      StringBuilder newLemma = new StringBuilder();
      for (IndexedWord node : nodes) {
        if (node.lemma() != null) {
          newLemma.append(node.lemma());
        }
      }
      lemma = newLemma.length() > 0 ? newLemma.toString() : null;
      newLabel.setLemma(lemma);
    }

    // after() and before() return "" if null, so we need to use the CoreAnnotations directly
    if (newLabel.get(CoreAnnotations.AfterAnnotation.class) == null) {
      newLabel.setAfter(nodes.get(nodes.size() - 1).after());
    }
    if (newLabel.get(CoreAnnotations.BeforeAnnotation.class) == null) {
      newLabel.setBefore(nodes.get(0).before());
    }

    // find the head, and replace all the existing annotations on the head
    // with the new annotations (including word and lemma)
    // from the newly built CoreLabel
    // TODO: should avoid messing with empty nodes
    // doing extra nodes would be good
    for (IndexedWord vertex : sg.vertexSet()) {
      if (vertex.index() == head.index()) {
        for (Class key : newLabel.keySet()) {
          Object value = newLabel.get(key);
          vertex.set(key, value);
        }
      }
    }

    // delete the dependency
    // copy the list so that deletion doesn't hurt the iterator
    // TODO: super fancy would be implementing iterator.remove()
    // on the Set returned by the SemanticGraph
    for (IndexedWord vertex : sg.vertexListSorted()) {
      // TODO: again, don't delete empty nodes
      if (depIndices.contains(vertex.index())) {
        sg.removeVertex(vertex);
      }
    }

    // reindex everyone
    List<Integer> sortedIndices = new ArrayList<>(depIndices);
    Collections.sort(sortedIndices);
    Collections.reverse(sortedIndices);
    for (Integer depIndex : sortedIndices) {
      SsurgeonUtils.moveNodes(sg, sm, x -> (x >= depIndex), x -> x-1, false);
    }

    return true;
  }

}

