package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
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
   * A score for this fragment. This is 1.0 by default.
   */
  public double score = 1.0;

  public SentenceFragment(SemanticGraph tree, boolean copy) {
    if (copy) {
      this.parseTree = new SemanticGraph(tree);
    } else {
      this.parseTree = tree;
    }
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SentenceFragment)) return false;
    if (!super.equals(o)) return false;
    SentenceFragment that = (SentenceFragment) o;
    return parseTree.equals(that.parseTree);

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + parseTree.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return StringUtils.join(words.stream().map(CoreLabel::word), " ");
  }
}
