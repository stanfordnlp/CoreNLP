package edu.stanford.nlp.trees.international.spanish;

import java.util.Arrays;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;

/**
 * Normalize trees read from the AnCora Spanish corpus.
 */
public class SpanishTreeNormalizer extends TreeNormalizer {

  /**
   * Tag provided to words which are extracted from a multi-word token
   * into their own independent nodes
   */
  private static final String MW_TAG = "MW?";

  private boolean simplifiedTagset;
  private boolean aggressiveNormalization;

  public SpanishTreeNormalizer(boolean simplifiedTagset,
                               boolean aggressiveNormalization) {
    this.simplifiedTagset = simplifiedTagset;
    this.aggressiveNormalization = aggressiveNormalization;
  }

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    for (Tree t : tree) {
      if (simplifiedTagset && t.isPreTerminal()) {
        // This is a part of speech tag. Remove extra morphological
        // information.
        CoreLabel label = (CoreLabel) t.label();
        String pos = label.value();

        pos = simplifyPOSTag(pos).intern();
        label.setValue(pos);
        label.setTag(pos);
      } else if (aggressiveNormalization && t.isPrePreTerminal()) {
        normalizeForMultiWord(t, tf);
      }
    }

    return tree;
  }

  /**
   * Return a "simplified" version of an original AnCora part-of-speech
   * tag, with much morphological annotation information removed.
   */
  private String simplifyPOSTag(String pos) {
    if (pos.length() == 0)
      return pos;

    switch (pos.charAt(0)) {
    case 'd':
      // determinant (d)
      //   retain category, type
      //   drop person, gender, number, possessor
      return pos.substring(0, 2) + "0000";
    case 's':
      // preposition (s)
      //   retain category, type
      //   drop form, gender, number
      return pos.substring(0, 2) + "000";
    case 'p':
      // pronoun (p)
      //   retain category, type
      //   drop person, gender, number, case, possessor, politeness
      return pos.substring(0, 2) + "000000";
    case 'a':
      // adjective
      //   retain category, type, grade
      //   drop gender, number, function
      return pos.substring(0, 3) + "000";
    case 'n':
      // noun
      //   retain category, type, number
      //   drop type, gender, classification, other
      return pos.substring(0, 2) + '0' + pos.charAt(3) + "000";
    case 'v':
      // verb
      //   retain category, type, mood, tense
      //   drop person, number, gender
      return pos.substring(0, 4) + "000";
    default:
      // adverb
      //   retain all
      // punctuation
      //   retain all
      // numerals
      //   retain all
      // date and time
      //   retain all
      // conjunction
      //   retain all
      return pos;
    }
  }

  /**
   * Normalize a pre-pre-terminal tree node by accounting for multi-word
   * tokens.
   *
   * Detects multi-word tokens in leaves below this pre-pre-terminal and
   * expands their constituent words into separate leaves.
   */
  void normalizeForMultiWord(Tree t, TreeFactory tf) {
    Tree[] preterminals = t.children();

    // Loop accumulator: number of tokens which have been inserted to
    // the left of the current preterminal thus far
    int shift = 0;

    for (int i = 0; i < preterminals.length; i++) {
      Tree leaf = preterminals[i].firstChild();
      String leafValue = ((CoreLabel) leaf.label()).value();

      if (leafValue.indexOf('_') == -1)
        continue;

      // Leaf is a multi-word token; build new pre-terminal nodes for
      // each of its constituent words
      String[] words = leafValue.split("_");
      Tree[] newPreterminals = new Tree[words.length];
      for (int j = 0; j < words.length; j++) {
        String word = words[j];

        Tree newLeaf = tf.newLeaf(word);
        if (newLeaf.label() instanceof HasWord)
          ((HasWord) newLeaf.label()).setWord(word);

        Tree newPreterminal = tf.newTreeNode(MW_TAG, Arrays.asList(newLeaf));
        if (newPreterminal.label() instanceof HasTag)
          ((HasTag) newPreterminal.label()).setTag(MW_TAG);

        newPreterminals[j] = newPreterminal;
      }

      // Now insert each of the new preterminals and remove the old
      // multi-word token
      //
      // Current preterminal is at index `i + shift` of the preterminals
      t.removeChild(i + shift);
      for (int k = 0; k < newPreterminals.length; k++)
        t.addChild(i + shift + k, newPreterminals[k]);

      // Later preterminals must take into account the (possibly) larger
      // amount of preceding siblings
      shift += words.length - 1;
    }
  }

}
