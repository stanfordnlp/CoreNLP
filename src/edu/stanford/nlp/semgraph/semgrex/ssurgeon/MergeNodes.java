package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
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
 *<br>
 * The words are merged in every graph of the sentence which has them.
 * Each of those graphs has to allow the merge, and there has to be a word
 * which each of them allows as the head.  The
 * merged word takes the place of the first of the words, and the empty
 * words which followed any of them follow it.  An empty word cannot be
 * merged.
 *
 * @author John Bauer
 */
public class MergeNodes extends SsurgeonEdit {
  /** The name of this edit in an Ssurgeon script */
  public static final String LABEL = "mergeNodes";
  /** The names of the matched nodes to merge */
  final List<String> names;
  /** Attributes to set on the merged word, such as its word or lemma */
  final Map<String, String> attributes;

  /**
   * Creates an edit which merges the named nodes into one word.
   *
   * @param names The names of the nodes in the Semgrex pattern; copied
   * @param attributes Attributes to set on the merged word; copied.
   *   Word and lemma default to the concatenation of the merged words
   */
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
   *
   * @param sg The graph containing the nodes
   * @param head The node currently chosen as the head
   * @param candidate The node to compare against the head
   * @param ignoreNodes Parents in this set are not considered
   * @return true if both nodes have the same set of (parent, relation) pairs
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
   * The words which can head the phrase in one graph, in the order of the sentence, or null
   * if the edges of the graph do not allow the words to merge.
   *<br>
   * One of the words has to be the head of the phrase: the only one with
   * a parent outside the words, or the root.  Another word with exactly
   * the same parents outside the words, with the same relations, can head
   * it just as well, and whichever is not chosen merges into the other,
   * with its children outside the words moving to the chosen head.  The
   * other words can have no edges to words outside of them.
   *
   * @param sg the graph
   * @param nodes the words, in the order of the sentence
   * @param nodeSet the same words
   */
  private static List<IndexedWord> headCandidates(SemanticGraph sg, List<IndexedWord> nodes, Set<IndexedWord> nodeSet) {
    IndexedWord head = null;
    // words with the same parents as the head, any of which could be the head
    List<IndexedWord> candidates = new ArrayList<>();
    for (IndexedWord candidate : nodes) {
      Set<IndexedWord> parents = sg.getParents(candidate);
      if (parents.size() == 0) {
        // found a root
        // if something else is already the head,
        // we don't know how to handle that,
        // so we abort this operation
        if (head != null) {
          return null;
        }
        head = candidate;
        candidates.add(candidate);
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
            candidates.add(candidate);
            break;
          } else {
            // if we already have a head with different parents, give up instead
            return null;
          }
        }
        head = candidate;
        candidates.add(candidate);
        break;
      }
    }
    if (head == null) {
      return null;
    }

    // for now, only the possible heads can have edges to children outside the subtree
    // TODO: not clear we want to allow other words with different
    // heads to be merged in this manner
    for (IndexedWord node : nodes) {
      if (candidates.contains(node)) {
        continue;
      }
      for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(node)) {
        if (!nodeSet.contains(edge.getDependent())) {
          return null;
        }
      }
    }
    return candidates;
  }

  /**
   * Merges the words, as {@link #evaluate(SemgrexGraphs, SemgrexMatcher)} does
   * for a sentence with only this graph.
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    return evaluate(new SemgrexGraphs(sg), sm);
  }

  /**
   * If the edges of each graph which has the named nodes allow for it,
   * with a head which each graph allows, squish those words into one word.
   * Of the words which every graph allows as the head, the first is used.
   *<br>
   * The merged word is the head, at the place of the first of the words,
   * and the later words move down to fill the places of the others.  The
   * empty words which followed any of the words follow the merged word.
   * A name which referred to one of the other words refers to the merged word.
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    Set<IndexedWord> nodeSet = new HashSet<>();
    for (String name : names) {
      IndexedWord node = sm.getNode(name);
      if (node == null || node.getEmptyIndex() != 0) {
        return false;
      }
      nodeSet.add(node);
    }
    List<IndexedWord> nodes = new ArrayList<>(nodeSet);
    Collections.sort(nodes);
    if (nodes.size() < 2) {
      return false;
    }

    // each graph with the words has to allow the merge, and the head is
    // the first of the words which every one of those graphs allows
    List<SemgrexGraphName> withNodes = new ArrayList<>();
    List<IndexedWord> heads = new ArrayList<>(nodes);
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph sg = graphs.get(name);
      if (sg == null) {
        continue;
      }
      int present = 0;
      for (IndexedWord node : nodes) {
        if (sg.containsVertex(node)) {
          ++present;
        }
      }
      if (present == 0) {
        continue;
      }
      if (present < nodes.size()) {
        return false;
      }
      List<IndexedWord> candidates = headCandidates(sg, nodes, nodeSet);
      if (candidates == null) {
        return false;
      }
      heads.retainAll(candidates);
      withNodes.add(name);
    }
    if (withNodes.isEmpty() || heads.isEmpty()) {
      return false;
    }
    IndexedWord head = heads.get(0);

    // at this point, everything checks out and we can start manipulating the graph
    // we will start by moving the children outside the words of each
    // other possible head onto the chosen head
    for (SemgrexGraphName name : withNodes) {
      SemanticGraph sg = graphs.get(name);
      List<SemanticGraphEdge> reattachEdges = new ArrayList<>();
      for (IndexedWord node : nodes) {
        if (node == head) {
          continue;
        }
        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(node)) {
          if (!nodeSet.contains(edge.getDependent())) {
            reattachEdges.add(edge);
          }
        }
      }
      for (SemanticGraphEdge edge : reattachEdges) {
        ReattachNamedEdge.reattachEdge(sg, sm, edge, null, head, edge.getDependent());
      }
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

    // replace the existing annotations on the head with the new
    // annotations, including word and lemma.  the head's label is
    // shared by every graph, and by any copy of the head
    for (Class key : newLabel.keySet()) {
      Object value = newLabel.get(key);
      head.set(key, value);
    }

    // the places the other words leave, which the later words move into
    final int firstIndex = nodes.get(0).index();
    List<Integer> vacated = new ArrayList<>();
    for (IndexedWord node : nodes) {
      if (node.index() != firstIndex) {
        vacated.add(node.index());
      }
    }
    Collections.sort(vacated);

    // delete the other words from every graph, along with any copies of
    // them, which share their labels
    Set<CoreLabel> mergedLabels = Collections.newSetFromMap(new IdentityHashMap<>());
    for (IndexedWord node : nodes) {
      if (node != head) {
        mergedLabels.add(node.backingLabel());
      }
    }
    for (SemgrexGraphName name : withNodes) {
      SemanticGraph sg = graphs.get(name);
      for (IndexedWord vertex : sg.vertexListSorted()) {
        if (vertex.getEmptyIndex() == 0 && mergedLabels.contains(vertex.backingLabel())) {
          sg.removeVertex(vertex);
        }
      }
    }
    for (String nodeName : new ArrayList<>(sm.getNodeNames())) {
      IndexedWord named = sm.getNode(nodeName);
      if (named != null && mergedLabels.contains(named.backingLabel())) {
        sm.putNode(nodeName, head);
      }
    }

    // the merged word goes to the place of the first word, and then the
    // later words move down.  the empty words which followed any of the
    // words now have the merged word as the nearest word before them
    if (head.index() != firstIndex) {
      head = SsurgeonUtils.moveNode(graphs, sm, head, firstIndex);
    }
    SsurgeonUtils.moveNodes(graphs, sm, x -> countBelow(vacated, x) > 0, x -> x - countBelow(vacated, x));

    return true;
  }

  /** How many of the sorted indices are below x */
  private static int countBelow(List<Integer> indices, int x) {
    int count = 0;
    for (int index : indices) {
      if (index >= x) {
        break;
      }
      ++count;
    }
    return count;
  }

  /**
   * MergeNodes merges the words in every graph and renumbers the words of every graph.
   *
   * @return true
   */
  @Override
  public boolean changesWordsInEveryGraph() {
    return true;
  }
}

