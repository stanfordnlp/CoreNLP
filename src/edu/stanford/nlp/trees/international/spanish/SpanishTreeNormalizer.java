package edu.stanford.nlp.trees.international.spanish;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
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
  public static final String MW_TAG = "MW?";

  /**
   * Tag provided to constituents which contain words from MW tokens
   */
  public static final String MW_PHRASE_TAG = "MW_PHRASE?";

  private static Map<String, String> spellingFixes = new HashMap<String, String>() {{
      put("jucio", "juicio"); // 4800_2000406.tbf-5
    }}

  private boolean simplifiedTagset;
  private boolean aggressiveNormalization;

  public SpanishTreeNormalizer(boolean simplifiedTagset,
                               boolean aggressiveNormalization) {
    this.simplifiedTagset = simplifiedTagset;
    this.aggressiveNormalization = aggressiveNormalization;
  }

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    // Counter for part-of-speech statistics
    TwoDimensionalCounter<String, String> unigramTagger =
      new TwoDimensionalCounter<String, String>();

    for (Tree t : tree) {
      if (simplifiedTagset && t.isPreTerminal()) {
        // This is a part of speech tag. Remove extra morphological
        // information.
        CoreLabel label = (CoreLabel) t.label();
        String pos = label.value();

        pos = simplifyPOSTag(pos).intern();
        label.setValue(pos);
        label.setTag(pos);
      } else if (aggressiveNormalization && isMultiWordCandidate(t)) {
        // Expand multi-word token if necessary
        normalizeForMultiWord(t, tf);
      }
    }

    return tree;
  }

  @Override
  public String normalizeTerminal(String word) {
    if (spellingFixes.containsKey(word))
      return spellingFixes.get(word);
    return word;
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
      //   retain category, type, number, NER label
      //   drop type, gender, classification

      // Some tags are randomly missing the NER label..
      char ner = pos.length() == 7 ? pos.charAt(6) : '0';
      return pos.substring(0, 2) + '0' + pos.charAt(3) + "00" + ner;
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
   * Determine whether the given tree node is a multi-word token
   * expansion candidate. (True if the node has at least one grandchild
   * which is a leaf node.)
   */
  boolean isMultiWordCandidate(Tree t) {
    for (Tree child : t.children())
      for (Tree grandchild : child.children())
        if (grandchild.isLeaf())
          return true;

    return false;
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

    for (int i = 0; i < preterminals.length; i++) {
      // This particular child is not actually a preterminal --- skip
      if (!preterminals[i].isPreTerminal())
        continue;

      Tree leaf = preterminals[i].firstChild();
      String leafValue = ((CoreLabel) leaf.label()).value();

      String[] words = getMultiWords(leafValue);
      if (words.length == 1)
        continue;

      // Leaf is a multi-word token; build new pre-terminal nodes for
      // each of its constituent words
      List<Tree> newPreterminals = new ArrayList<Tree>(words.length);
      for (int j = 0; j < words.length; j++) {
        String word = words[j];

        Tree newLeaf = tf.newLeaf(word);
        if (newLeaf.label() instanceof HasWord)
          ((HasWord) newLeaf.label()).setWord(word);

        Tree newPreterminal = tf.newTreeNode(MW_TAG, Arrays.asList(newLeaf));
        if (newPreterminal.label() instanceof HasTag)
          ((HasTag) newPreterminal.label()).setTag(MW_TAG);

        newPreterminals.add(newPreterminal);
      }

      // Now create a dummy phrase containing the new preterminals.
      // Maintain the value of the old preterminal in its label value.
      String phraseType = MW_PHRASE_TAG + "_" + preterminals[i].value();
      Tree newPrePreTerminal = tf.newTreeNode(phraseType, newPreterminals);
      t.setChild(i, newPrePreTerminal);
    }
  }

  private static final Pattern pQuoted = Pattern.compile("\"(.+)\"");

  /**
   * Characters which may separate words in a single token.
   */
  private static final String WORD_SEPARATORS = ",-_";

  /**
   * Word separators which should not be treated as separate "words" and
   * dropped from a multi-word token.
   */
  private static final String WORD_SEPARATORS_DROP = "_";

  /**
   * Return the (single or multiple) words which make up the given
   * token.
   */
  private String[] getMultiWords(String token) {
    Matcher quoteMatcher = pQuoted.matcher(token);
    if (quoteMatcher.matches()) {
      String[] ret = new String[3];
      ret[0] = "\"";
      ret[1] = quoteMatcher.group(1);
      ret[2] = "\"";

      return ret;
    }

    // Confusing: we are using a tokenizer to split a token into its
    // constituent words
    StringTokenizer splitter = new StringTokenizer(token, WORD_SEPARATORS,
                                                   true);

    List<String> words = new ArrayList<String>();
    while (splitter.hasMoreTokens()) {
      String word = splitter.nextToken();
      if (word.length() == 1
          && WORD_SEPARATORS_DROP.indexOf(word.charAt(0)) != -1)
        // This is a delimiter that we should drop
        continue;

      words.add(word);
    }

    return words.toArray(new String[words.size()]);
  }

}
