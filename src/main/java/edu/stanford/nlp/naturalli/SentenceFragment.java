package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A representation of a sentence fragment.
 *
 * @author Gabor Angeli
 */
public class SentenceFragment {

  /**
   * The words in this sentence fragment (e.g., for use as the gloss of the fragment).
   */
  public final List<CoreLabel> words = new ArrayList<>();

  /**
   * The parse tree for this sentence fragment.
   */
  public final SemanticGraph parseTree;

  /**
   * The assumed truth of this fragment; this is relevant for what entailments are supported
   */
  public final boolean assumedTruth;

  /**
   * A score for this fragment. This is 1.0 by default.
   */
  public double score = 1.0;


  public SentenceFragment(SemanticGraph tree, boolean assumedTruth, boolean copy) {
    if (copy) {
      this.parseTree = new SemanticGraph(tree);
    } else {
      this.parseTree = tree;
    }
    this.assumedTruth = assumedTruth;
    words.addAll(this.parseTree.vertexListSorted().stream().map(IndexedWord::backingLabel).collect(Collectors.toList()));
  }

  /** The length of this fragment, in words */
  public int length() {
    return words.size();
  }

  /**
   * Changes the score of this fragment in place.
   * @param score The new score of the fragment
   * @return This sentence fragment.
   */
  public SentenceFragment changeScore(double score) {
    this.score = score;
    return this;
  }

  /**
   * Return the tokens in this fragment, but padded with null so that the index in this
   * sentence matches the index of the parse tree.
   */
  public List<CoreLabel> paddedWords() {
    int maxIndex = -1;
    for (IndexedWord vertex : parseTree.vertexSet()) {
      maxIndex = Math.max(maxIndex, vertex.index());
    }
    List<CoreLabel> tokens = new ArrayList<>(maxIndex);
    for (int i = 0; i < maxIndex; ++i) { tokens.add(null); }
    for (CoreLabel token : this.words) {
      tokens.set(token.index() - 1, token);
    }
    return tokens;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SentenceFragment)) return false;
    SentenceFragment that = (SentenceFragment) o;
    return this.parseTree.vertexSet().equals((that.parseTree.vertexSet()));

  }

  @Override
  public int hashCode() {
    return this.parseTree.vertexSet().hashCode();
  }

  @Override
  public String toString() {
    List<Pair<String, Integer>> glosses = new ArrayList<>();
    for (CoreLabel word : words) {
      // Add the word itself
      glosses.add(Pair.makePair(word.word(), word.index() - 1));
      String addedConnective = null;
      // Find additional connectives
      for (SemanticGraphEdge edge : parseTree.incomingEdgeIterable(new IndexedWord(word))) {
        String rel = edge.getRelation().toString();
        if (rel.contains("_")) {  // for Stanford dependencies only
          addedConnective = rel.substring(rel.indexOf('_') + 1);
        }
      }
      if (addedConnective != null) {
        // Found a connective (e.g., a preposition or conjunction)
        Pair<Integer, Integer> yield = parseTree.yieldSpan(new IndexedWord(word));
        glosses.add(Pair.makePair(addedConnective.replaceAll("_", " "), yield.first - 1));
      }
    }
    // Sort the sentence
    Collections.sort(glosses, (a, b) -> a.second - b.second);
    // Return the sentence
    return StringUtils.join(glosses.stream().map(Pair::first), " ");
  }

}
