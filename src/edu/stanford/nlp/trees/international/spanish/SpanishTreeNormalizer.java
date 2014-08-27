package edu.stanford.nlp.trees.international.spanish;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Pair;

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

  public static final String EMPTY_LEAF_VALUE = "=NONE=";
  public static final String LEFT_PARENTHESIS = "=LRB=";
  public static final String RIGHT_PARENTHESIS = "=RRB=";

  private static final Map<String, String> spellingFixes = new HashMap<String, String>() {{
      put("jucio", "juicio"); // 4800_2000406.tbf-5
      put("tambien", "también"); // 41_19991002.tbf-8

      // Hack: these aren't exactly spelling mistakes, but we need to
      // run a search-and-replace across the entire corpus with them, so
      // they should be treated just like spelling mistakes for our
      // purposes
      put("(", LEFT_PARENTHESIS);
      put(")", RIGHT_PARENTHESIS);
    }};

  /**
   * A filter which rejects preterminal nodes that contain "empty" leaf
   * nodes.
   */
  private static final Filter<Tree> emptyFilter = new Filter<Tree>() {
    public boolean accept(Tree tree) {
      if (tree.isPreTerminal()
          && tree.firstChild().value().equals(EMPTY_LEAF_VALUE))
        return false;
      return true;
    }
  };

  /**
   * If one of the constituents in this set has a single child has a
   * multi-word token, it should be replaced by a node heading the
   * expanded word leaves rather than simply receive that node as a
   * child.
   *
   * Note that this is only the case for constituents with a *single*
   * child which is a multi-word token.
   */
  private static final Set<String> mergeWithConstituentWhenPossible =
    new HashSet<String>() {{
      add("grup.adv");
      add("grup.nom");
      add("spec");
    }};

  // Customization
  private boolean simplifiedTagset;
  private boolean aggressiveNormalization;
  private boolean retainNER;

  public SpanishTreeNormalizer(boolean simplifiedTagset,
                               boolean aggressiveNormalization,
                               boolean retainNER) {
    if (retainNER && !simplifiedTagset)
      throw new IllegalArgumentException("retainNER argument only valid when " +
                                         "simplified tagset is used");

    this.simplifiedTagset = simplifiedTagset;
    this.aggressiveNormalization = aggressiveNormalization;
    this.retainNER = retainNER;
  }

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    // First filter out nodes we don't like
    tree = tree.prune(emptyFilter);

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

    // Now attack elisions: 'al' and 'del'
    expandElisions(tree, tf);

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

      char ner = retainNER && pos.length() == 7 ? pos.charAt(6) : '0';
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

      // Leaf is a multi-word token; build new nodes for each of its
      // constituent words
      List<Tree> newNodes = new ArrayList<Tree>(words.length);
      for (int j = 0; j < words.length; j++) {
        String word = normalizeTerminal(words[j]);

        Tree newLeaf = tf.newLeaf(word);
        if (newLeaf.label() instanceof HasWord)
          ((HasWord) newLeaf.label()).setWord(word);

        Tree newNode = tf.newTreeNode(MW_TAG, Arrays.asList(newLeaf));
        if (newNode.label() instanceof HasTag)
          ((HasTag) newNode.label()).setTag(MW_TAG);

        newNodes.add(newNode);
      }

      // Value of the phrase which should head these preterminals. Mark
      // that this was created from a multiword token, and also retain
      // the original parts of speech.
      String phraseValue = MW_PHRASE_TAG + "_"
        + simplifyPOSTag(preterminals[i].value());

      // Should we insert these new nodes as children of the parent `t`
      // (i.e., "merge" the multi-word token phrase into its parent), or
      // head them with a new node and set that as a child of the
      // parent?
      boolean shouldMerge = preterminals.length == 1
        && mergeWithConstituentWhenPossible.contains(t.value());

      if (shouldMerge) {
        t.setChildren(newNodes);
        t.setValue(phraseValue);
      } else {
        Tree newHead = tf.newTreeNode(phraseValue, newNodes);
        t.setChild(i, newHead);
      }
    }
  }

  private static final Pattern pQuoted = Pattern.compile("\"(.+)\"");

  /**
   * Characters which may separate words in a single token.
   */
  private static final String WORD_SEPARATORS = ",-_¡!¿?()";

  /**
   * Word separators which should not be treated as separate "words" and
   * dropped from a multi-word token.
   */
  private static final String WORD_SEPARATORS_DROP = "_";

  /**
   * These bound morphemes should not be separated from the words with
   * which they are joined by hyphen.
   */
  // TODO how to handle clitics? chino-japonés
  private static final Set<String> hyphenBoundMorphemes = new HashSet<String>() {{
      add("anti"); // anti-Gil
      add("co"); // co-promotora
      add("ex"); // ex-diputado
      add("meso"); // meso-americano
      add("neo"); // neo-proteccionismo
      add("pre"); // pre-presidencia
      add("pro"); // pro-indonesias
      add("quasi"); // quasi-unidimensional
      add("re"); // re-flotamiento
      add("semi"); // semi-negro
      add("sub"); // sub-18
    }};

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
    int remainingTokens = splitter.countTokens();

    List<String> words = new ArrayList<String>();

    while (splitter.hasMoreTokens()) {
      String word = splitter.nextToken();
      remainingTokens--;

      if (word.length() == 1
          && WORD_SEPARATORS_DROP.indexOf(word.charAt(0)) != -1)
        // This is a delimiter that we should drop
        continue;

      if (remainingTokens >= 2 && hyphenBoundMorphemes.contains(word)) {
        String hyphen = splitter.nextToken();
        remainingTokens--;

        if (!hyphen.equals("-")) {
          // Ouch. We expected a hyphen here. Clean things up and keep
          // moving.
          words.add(word);
          words.add(hyphen);
          continue;
        }

        String freeMorpheme = splitter.nextToken();
        remainingTokens--;

        words.add(word + hyphen + freeMorpheme);
        continue;
      }

      // Otherwise..
      words.add(word);
    }

    return words.toArray(new String[words.size()]);
  }

  /**
   * Expand grandchild tokens which are elided forms of multi-word
   * expressions ('al,' 'del').
   *
   * We perform this expansion separately from multi-word expansion
   * because we follow special rules about where the expanded tokens
   * should be placed in the case of elision.
   *
   * @param t Tree representing an entire sentence
   * @param tf
   */
  private void expandElisions(Tree t, TreeFactory tf) {
    Tsurgeon.processPatternsOnTree(elisionExpansions, t);
  }

  @SuppressWarnings("unchecked")
  private static final Pair<String, String>[] elisionExpansionStrs = new Pair[] {
    // Elided forms with an ancestor which has an `sn` phrase as a right
    // sibling
    new Pair(// Search for right-hand `sn` ancestor sibling
             "sp000 < /^(del|al)$/=elided >> (__=ancestor $+ sn=sn) " +
             // Make sure this is the deepest ancestor
             // sibling possible
             ": (=ancestor !<< (__ << =elided $+ sn))",

             // Insert the 'el' specifier as a constituent in adjacent
             // noun phrase
             "[relabel elided /l//] [insert (spec (da0000 el)) >0 sn]"),

    // Prepositional forms with a `prep` grandparent which has a
    // `grup.nom` phrase as a right sibling
    new Pair("prep < (sp000 < /^(del|al)$/=elided) $+ /grup\\.nom/=target",

             "[relabel elided /l//] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // Elided forms with a `prep` ancestor which has an adjectival
    // phrase as a right sibling ('al segundo', etc.)
    new Pair("prep < (sp000 < /^(del|al)$/=elided) $+ /s\\.a/=target",

             "[relabel elided /l//] " +
             // Turn neighboring adjectival phrase into a noun phrase,
             // adjoining original adj phrase beneath a `grup.nom`
             "[adjoinF (sn (spec (da0000 el)) (grup.nom foot@)) target]"),

    // "del que golpea:" insert 'el' as specifier into adjacent relative
    // phrase
    new Pair("sp < (prep=prep < (sp000 < del=elided)) " +
             ": (__ $- prep) << relatiu=relatiu",

             // Build a noun phrase in the neighboring relative clause
             // containing the 'el' specifier
             "[relabel elided /l//] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) relatiu]"),

    // "al" + infinitive phrase
    new Pair("prep < (sp000 < /^(al|del)$/=elided) $+ " +
             // Looking for an infinitive directly to the right of the
             // "al" token, nested within one or more clause
             // constituents
             "(S=target <+(S) infinitiu=inf <<, =inf)",

             "[relabel elided /l//] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // "al no" + infinitive phrase
    new Pair("prep < (sp000 < al=elided) $+ (S=target <, neg <2 infinitiu)",

             "[relabel elided a] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // "al que quisimos tanto"
    new Pair("prep < (sp000 < al=elided) $+ relatiu=target",

             "[relabel elided a] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // "al de" etc.
    new Pair("prep < (sp000 < al=elided) $+ (sp=target <, prep)",

             "[relabel elided a] " +
             "[adjoinF (sn (spec (da0000 el)) (grup.nom foot@)) target]"),

    // leading adjective in sibling: "al chileno Fernando"
    new Pair("prep < (sp000 < /^(del|al)$/=elided) $+ " +
             "(/grup\\.nom/=target <, /s\\.a/ <2 /sn|nc0[sp]000/)",

             "[relabel elided /l//] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // "al" + phrase begun by participle -> "a lo <participle>"
    // e.g. "al conseguido" -> "a lo conseguido"
    new Pair("prep < (sp000 < /^(al|del)$/=elided) $+ (S=target < participi)",

             "[relabel elided /l//] " +
             "[adjoinF (sn (spec (da0000 lo)) foot@) target]"),

    // "del" used within specifier; e.g. "más del 30 por ciento"
    new Pair("spec < (sp000 < del=elided) > sn $+ /grup\\.nom/=target",

             "[relabel elided /l//] " +
             "[insert (spec (da0000 el)) >0 target]"),
  };

  private static final List<Pair<TregexPattern, TsurgeonPattern>> elisionExpansions;

  static {
    elisionExpansions =
      new ArrayList<Pair<TregexPattern, TsurgeonPattern>>(elisionExpansionStrs.length);
    for (Pair<String, String> expansion : elisionExpansionStrs)
      elisionExpansions.add(new Pair<TregexPattern, TsurgeonPattern>(TregexPattern.compile(expansion.first()),
                                                                     Tsurgeon.parseOperation(expansion.second())));
  }

}
