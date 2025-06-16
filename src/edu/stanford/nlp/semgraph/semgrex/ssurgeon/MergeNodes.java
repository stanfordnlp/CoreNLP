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
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;

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
   * Test if two nodes have the same parents with the same relations.
   * If so, then the two nodes can be treated as equivalent when merging nodes.
   * Otherwise, since there are two different heads, we can't pick a node
   * to treat as the head of the phrase, and we will have to abort
   */
  public static boolean hasSameParents(SemanticGraph sg, IndexedWord head, IndexedWord candidate, Set<IndexedWord> ignoreNodes) {
    Set<Pair<IndexedWord, GrammaticalRelation>> headParents = new HashSet<>();
    Set<Pair<IndexedWord, GrammaticalRelation>> candidateParents = new HashSet<>();

    for (SemanticGraphEdge edge : sg.incomingEdgeIterable(head)) {
      // iterating all parents is relevant for enhanced graphs, for example
      if (ignoreNodes.contains(edge.getGovernor()))
        continue;
      headParents.add(new Pair<>(edge.getGovernor(), edge.getRelation()));
    }
    for (SemanticGraphEdge edge : sg.incomingEdgeIterable(candidate)) {
      // iterating all parents is relevant for enhanced graphs, for example
      if (ignoreNodes.contains(edge.getGovernor()))
        continue;
      candidateParents.add(new Pair<>(edge.getGovernor(), edge.getRelation()));
    }
    return headParents.equals(candidateParents);
  }

  /**
   * If the named nodes are next to each other, and the edges of
   * the graph allow for it, squish those words into one word
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    Set<IndexedWord> nodeSet = new HashSet<>();
    for (String name : names) {
      IndexedWord node = sm.getNode(name);
      if (node == null) {
        return false;
      }
      nodeSet.add(node);
    }

    IndexedWord head = null;
    // Words who share the same parents will go in this set
    // Therefore, we can later remap any edges going to that word
    // to point to the chosen head instead
    // This will let us process phrases where two words could have
    // been the head and both have edges coming in to them
    Set<IndexedWord> equivalentHeads = new HashSet<>();
    for (IndexedWord candidate : nodeSet) {
      Set<IndexedWord> parents = sg.getParents(candidate);
      if (parents.size() == 0) {
        // found a root
        // if something else is already the head,
        // we don't know how to handle that,
        // so we abort this operation
        if (head != null) {
          return false;
        }
        head = candidate;
        continue;
      }
      for (IndexedWord parent : parents) {
        if (nodeSet.contains(parent)) {
          continue;
        }
        // parent is outside this subtree
        // therefore, we can use this word as the head of the subtree
        if (head != null) {
          if (hasSameParents(sg, head, candidate, nodeSet)) {
            // if the parents *and relations* of the other node are the same, we can keep going
            // since the nodes are about to merge anyway
            equivalentHeads.add(candidate);
            break;
          } else {
            // if we already have a head with different parents, give up instead
            return false;
          }
        }
        head = candidate;
        break;
      }
    }
    if (head == null) {
      return false;
    }

    // for now, only allow the head to have edges to children outside the subtree
    // also, words with the same parents as the new head can have outgoing edges
    // TODO: not clear we want to allow other words with different
    // heads to be merged in this manner
    List<SemanticGraphEdge> reattachEdges = new ArrayList<>();
    for (IndexedWord candidate : nodeSet) {
      if (candidate == head) {
        continue;
      }
      for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(candidate)) {
        IndexedWord gov = edge.getGovernor();
        if (gov != candidate) {
          throw new AssertionError();
        }
        IndexedWord dep = edge.getDependent();
        if (!nodeSet.contains(dep)) {
          if (equivalentHeads.contains(candidate)) {
            reattachEdges.add(edge);
          } else {
            return false;
          }
        }
      }
    }

    // at this point, everything checks out and we can start manipulating the graph
    // we will start by reattaching incoming edges to the chosen head
    for (SemanticGraphEdge edge : reattachEdges) {
      ReattachNamedEdge.reattachEdge(sg, sm, edge, null, head, edge.getDependent());
    }

    ArrayList<IndexedWord> nodes = new ArrayList<>(nodeSet);
    Collections.sort(nodes);

    Set<Integer> depIndices = new HashSet<Integer>();
    for (IndexedWord other : nodes) {
      if (other == head) {
        continue;
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

