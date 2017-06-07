package edu.stanford.nlp.trees.international.spanish;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.spanish.process.AnCoraPronounDisambiguator;
import edu.stanford.nlp.international.spanish.SpanishVerbStripper;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import java.util.function.Predicate;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * Normalize trees read from the AnCora Spanish corpus.
 */
public class SpanishTreeNormalizer extends BobChrisTreeNormalizer {

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

  private static final Map<String, String> spellingFixes = new HashMap<>();
  static {
    spellingFixes.put("embargp", "embargo"); // 18381_20000322.tbf-4
    spellingFixes.put("jucio", "juicio"); // 4800_2000406.tbf-5
    spellingFixes.put("méxico", "México"); // 111_C-3.tbf-17
    spellingFixes.put("reirse", "reírse"); // 140_20011102.tbf-13
    spellingFixes.put("tambien", "también"); // 41_19991002.tbf-8

    spellingFixes.put("Intitute", "Institute"); // 22863_20001129.tbf-16

    // Hack: these aren't exactly spelling mistakes, but we need to
    // run a search-and-replace across the entire corpus with them, so
    // they should be treated just like spelling mistakes for our
    // purposes
    spellingFixes.put("(", LEFT_PARENTHESIS);
    spellingFixes.put(")", RIGHT_PARENTHESIS);
  }

  /**
   * A filter which rejects preterminal nodes that contain "empty" leaf
   * nodes.
   */
  private static final Predicate<Tree> emptyFilter = new Predicate<Tree>() {
    public boolean test(Tree tree) {
      if (tree.isPreTerminal()
          && tree.firstChild().value().equals(EMPTY_LEAF_VALUE))
        return false;
      return true;
    }
  };

  /**
   * Resolves some inconsistencies in constituent naming:
   *
   * - "sa" and "s.a" are equivalent -- merge to "s.a"
   */
  private static final TreeTransformer constituentRenamer = new TreeTransformer() {
    @Override
    public Tree transformTree(Tree t) {
      if (t.isLeaf())
        return t;

      String value = t.value();
      if (value == null)
        return t;

      if (value.equals("sa"))
        t.setValue("s.a");

      return t;
    }
  };

  @SuppressWarnings("unchecked")
  private static final Pair<String, String>[] cleanupStrs = new Pair[] {
    new Pair("sp < (sp=sp <: prep=prep)", "replace sp prep"),

    // Left and right parentheses should be at same depth
    new Pair("fpa > __=grandparent $++ (__=ancestor <<` fpt=fpt >` =grandparent)",
      "move fpt $- ancestor"),

    // Nominal groups where adjectival groups belong
    new Pair("/^s\\.a$/ <: (/^grup\\.nom$/=gn <: /^a/)",
      "relabel gn /grup.a/"),

    // Adverbial phrases should always have adverb group children
    // -- we see about 50 exceptions in the corpus..
    new Pair("sadv !< /^grup\\.adv$/ <: /^(rg|neg)$/=adv",
      "adjoinF (grup.adv foot@) adv"),

    // 'z' tag should be 'z0'
    new Pair("z=z <: (__ !< __)", "relabel z z0"),

    // Conjunction groups aren't necessary if they head single
    // prepositional phrases (we already see a `conj < sp` pattern;
    // replicate that
    new Pair("/^grup\\.c/=grup > conj <: sp=sp", "replace grup sp"),

    // "Lift up" sentence-final periods which have been nested within
    // constituents (convention in AnCora is to have sentence-final
    // periods as final right children of the `sentence` constituent)
    new Pair("__=N <<` (fp|fs=fp <: (/^\\.$/ !. __)) > sentence=sentence",
             "move fp $- N"),

    // AnCora has a few weird parses of "nada que ver" and related
    // phrases. Normalize them:
    //
    //     (grup.nom (pi000000 X) (S (relatiu (pr000000 que))
    //                               (infinitiu (vmn0000 Y))))
    new Pair("(pi000000 <: __ !$+ S >` (/^grup\\.nom/=gn >` sn=sn))" +
               ". ((que >: (__=queTag $- =sn)) . (__=vb !< __ >>: (__=vbContainer $- =queTag)))",

             "[insert (S (relatiu (pr000000 que)) (infinitiu vmn0000=vbFoot)) >-1 gn]" +
               "[move vb >0 vbFoot]" +
               "[delete queTag]" +
               "[delete vbContainer]"),

    // One more bizarre "nada que ver"
    new Pair("sn=sn <: (/^grup\\.nom/=gn <<: Nada)" +
               "$+ (infinitiu=inf <<, que=que <<` (ver , =que) $+ sp=sp)",

             "[delete inf] [insert (S (relatiu (pr000000 que)) (infinitiu (vmn0000 ver))) >-1 gn]" +
               "[move sp >-1 sn]"),

    // Remove date lead-ins
    new Pair("sentence <<, (sn=sn <, (/^grup\\.w$/ $+ fp))",
             "delete sn"),

    // Shed "conj" parents of periods in the middle of trees so that
    // our splitter can identify sentence boundaries properly
    new Pair("conj=conj <: fp=fp", "replace conj fp"),

    // Fix mis-tagging of inverted question mark
    new Pair("fit=fit <: ¿", "relabel fit fia"),
  };

  private static final List<Pair<TregexPattern, TsurgeonPattern>> cleanup
    = compilePatterns(cleanupStrs);

  /**
   * If one of the constituents in this set has a single child has a
   * multi-word token, it should be replaced by a node heading the
   * expanded word leaves rather than simply receive that node as a
   * child.
   *
   * Note that this is only the case for constituents with a *single*
   * child which is a multi-word token.
   */
  private static final Set<String> mergeWithConstituentWhenPossible = new HashSet<>(
          Arrays.asList(
                  "grup.adv",
                  "grup.nom",
                  "grup.nom.loc",
                  "grup.nom.org",
                  "grup.nom.otros",
                  "grup.nom.pers",
                  "grup.verb",
                  "spec"
          ));

  // Customization
  private boolean simplifiedTagset;
  private boolean aggressiveNormalization;
  private boolean retainNER;

  public SpanishTreeNormalizer() {
    this(true, false, false);
  }

  public SpanishTreeNormalizer(boolean simplifiedTagset,
                               boolean aggressiveNormalization,
                               boolean retainNER) {
    super(new SpanishTreebankLanguagePack());

    if (retainNER && !simplifiedTagset)
      throw new IllegalArgumentException("retainNER argument only valid when " +
                                         "simplified tagset is used");

    this.simplifiedTagset = simplifiedTagset;
    this.aggressiveNormalization = aggressiveNormalization;
    this.retainNER = retainNER;
  }

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    // Begin with some basic transformations
    tree = tree.prune(emptyFilter).spliceOut(aOverAFilter)
      .transform(constituentRenamer);

    // Now start some simple cleanup
    tree = Tsurgeon.processPatternsOnTree(cleanup, tree);

    // That might've produced some more A-over-As
    tree = tree.spliceOut(aOverAFilter);

    // Find all named entities which are not multi-word tokens and nest
    // them within named entity NP groups
    if (retainNER)
      markSimpleNamedEntities(tree);

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

    // More tregex-powered fixes
    tree = expandElisions(tree);
    tree = expandConmigo(tree);
    tree = expandCliticPronouns(tree);

    // Make sure the tree has a top-level unary rewrite; the root
    // should have a proper root label
    String rootLabel = tlp.startSymbol();
    if (!tree.value().equals(rootLabel))
      tree = tf.newTreeNode(rootLabel, Collections.singletonList(tree));

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
   * Matches a verb with attached pronouns; used in several following
   * Tregex expressions
   */
  private static final String VERB_LEAF_WITH_PRONOUNS_TREGEX =
    // Match a leaf that looks like it has a clitic pronoun

    // Match suffixes of regular forms which may carry attached
    // pronouns (imperative, gerund, infinitive)
    "/(?:(?:[aeiáéí]r|[áé]ndo|[aeáé]n?|[aeiáéí](?:d(?!os)|(?=os)))" +
      // Match irregular imperative stems
      "|^(?:d[ií]|h[aá]z|v[eé]|p[oó]n|s[aá]l|sé|t[eé]n|v[eé]n|(?:id(?=os$))))" +
      // Match attached pronouns
      "(?:(?:(?:[mts]e|n?os|les?)(?:l[oa]s?)?)|l[oa]s?)$/=vb " +
      // It should actually be a verb (gerund, imperative or
      // infinitive)
      //
      // (Careful: other code that uses this pattern requires that this
      // node be at the end, with parens so that it can be named /
      // modified. See e.g. #verbWithCliticPronounAndSiblings)
      "> (/^vm[gmn]0000$/";

  /**
   * Matches verbs (infinitives, gerunds and imperatives) which have
   * attached pronouns, and the clauses which contain them
   */
  private static final TregexPattern verbWithCliticPronouns =
    TregexPattern.compile(VERB_LEAF_WITH_PRONOUNS_TREGEX +
                          // Verb tag should not have siblings in verb
                          // phrase
                          " !$ __)" +
                          // Locate the clause which contains it, and
                          // the child just below that clause
                          ">+(/^[^S]/) (/^(infinitiu|gerundi|grup\\.verb)$/=target " +
                          "> /^(sentence|S|grup\\.verb|infinitiu|gerundi)$/=clause << =vb " +
                          // Make sure we're not up too far in the tree:
                          // there should be no infinitive / gerund /
                          // verb phrase between the located ancestor
                          // and the verb
                          "!<< (/^(infinitiu|gerundi|grup\\.verb)$/ << =vb))");

  /**
   * Matches verbs (infinitives, gerunds and imperatives) which have
   * attached pronouns and siblings within their containing verb
   * phrases
   */
  private static final TregexPattern verbWithCliticPronounsAndSiblings =
    TregexPattern.compile(VERB_LEAF_WITH_PRONOUNS_TREGEX +
        // Name the matched verb tag as the target for insertion;
        // require that it have siblings
        "=target $ __) " +
        // Locate the clause which contains it, and
        // the child just below that clause
        ">+(/^[^S]/) (/^(infinitiu|gerundi|grup\\.verb)$/ " +
        "> /^(sentence|S|grup\\.verb|infinitiu|gerundi)$/=clause << =vb " +
        // Make sure we're not up too far in the tree:
        // there should be no infinitive / gerund /
        // verb phrase between the located ancestor
        // and the verb
        "!<< (/^(infinitiu|gerundi|grup\\.verb)$/ << =vb))");

  /**
   * Matches verbs which really should be in a clause, but were
   * squeezed into an infinitive constituent (because the pronoun was
   * attached to the verb, we could just pretend it wasn't a clause..
   * not anymore!)
   */
  private static final TregexPattern clauselessVerbWithCliticPronouns = TregexPattern.compile(
    VERB_LEAF_WITH_PRONOUNS_TREGEX +
      ") > (/^vmn/ > (/^infinitiu$/=target > /^sp$/))"
  );
  private static final TsurgeonPattern clausifyVerbWithCliticPronouns =
    Tsurgeon.parseOperation("adjoinF (S foot@) target");

  private static final SpanishVerbStripper verbStripper = SpanishVerbStripper.getInstance();

  /**
   * Separate clitic pronouns into their own tokens in the given tree.
   * (The clitic pronouns are attached under new `grup.nom` constituents
   * which follow the verbs to which they were formerly attached.)
   */
  private static Tree expandCliticPronouns(Tree t) {
    // Perform some cleanup first -- we want to match as many
    // clitic-attached verbs as possible..
    t = Tsurgeon.processPattern(clauselessVerbWithCliticPronouns,
      clausifyVerbWithCliticPronouns, t);

    // Run two separate stages: one for only-child VPs, then another
    // for VP children which have siblings
    t = expandCliticPronounsInner(t, verbWithCliticPronouns);
    t = expandCliticPronounsInner(t, verbWithCliticPronounsAndSiblings);

    return t;
  }

  /**
   * Expand clitic pronouns on verbs matching the given pattern.
   */
  private static Tree expandCliticPronounsInner(Tree t, TregexPattern pattern) {
    TregexMatcher matcher = pattern.matcher(t);
    while (matcher.find()) {
      Tree verbNode = matcher.getNode("vb");
      String verb = verbNode.value();

      if (!SpanishVerbStripper.isStrippable(verb))
        continue;

      SpanishVerbStripper.StrippedVerb split = verbStripper.separatePronouns(verb);
      if (split == null)
        continue;

      // Retrieve some context for the pronoun disambiguator: take the
      // matched clause and walk (at most) two constituents up
      StringBuilder clauseYieldBuilder = new StringBuilder();
      for (Label label : matcher.getNode("clause").yield())
        clauseYieldBuilder.append(label.value()).append(" ");
      String clauseYield = clauseYieldBuilder.toString();
      clauseYield = clauseYield.substring(0, clauseYield.length() - 1);

      // Insert clitic pronouns as leaves of pronominal phrases which are
      // siblings of `target`. Iterate in reverse order since pronouns are
      // attached to immediate right of `target`
      List<String> pronouns = split.getPronouns();
      for (int i = pronouns.size() - 1; i >= 0; i--) {
        String pronoun = pronouns.get(i);

        String newTreeStr = null;
        if (AnCoraPronounDisambiguator.isAmbiguous(pronoun)) {
          AnCoraPronounDisambiguator.PersonalPronounType type =
            AnCoraPronounDisambiguator.disambiguatePersonalPronoun(split, i, clauseYield);

          switch (type) {
            case OBJECT:
              newTreeStr = "(sn (grup.nom (pp000000 %s)))"; break;
            case REFLEXIVE:
              newTreeStr = "(morfema.pronominal (pp000000 %s))"; break;
            case UNKNOWN:
              // Mark for manual disambiguation
              newTreeStr = "(PRONOUN? (pp000000 %s))"; break;
          }
        } else {
          // Unambiguous clitic pronouns are all indirect / direct
          // object pronouns.. convenient!
          newTreeStr = "(sn (grup.nom (pp000000 %s)))";
        }

        String patternString = "[insert " + String.format(newTreeStr, pronoun) + " $- target]";
        TsurgeonPattern insertPattern = Tsurgeon.parseOperation(patternString);
        t = insertPattern.matcher().evaluate(t, matcher);
      }

      TsurgeonPattern relabelOperation =
        Tsurgeon.parseOperation(String.format("[relabel vb /%s/]", split.getStem()));
      t = relabelOperation.matcher().evaluate(t, matcher);
    }

    return t;
  }

  private static final List<Pair<TregexPattern, TsurgeonPattern>> markSimpleNEs;

  // Generate some reusable patterns for four different NE groups
  static {
    @SuppressWarnings("unchecked")
    Pair<String, String>[] patternTemplates = new Pair[] {
      // NE as only child of a `grup.nom`
      new Pair("/^grup\\.nom$/=target <: (/np0000%c/ < __)",
               "[relabel target /grup.nom.%s/]"),

      // NE as child with a right sibling in a `grup.nom`
      new Pair("/^grup\\.nom$/ < ((/np0000%c/=target < __) $+ __)",
               "[adjoinF (grup.nom.%s foot@) target]"),

      // NE as child with a left sibling in a `grup.nom`
      new Pair("/^grup\\.nom$/ < ((/np0000%c/=target < __) $- __)",
               "[adjoinF (grup.nom.%s foot@) target]")
    };

    // Pairs tagset annotation codes with the annotations used in our
    // constituents
    @SuppressWarnings("unchecked")
    Pair<Character, String>[] namedEntityTypes = new Pair[] {
      new Pair('0', "otros"), // other
      new Pair('l', "lug"), // place
      new Pair('o', "org"), // location
      new Pair('p', "pers"), // person
    };

    markSimpleNEs =
            new ArrayList<>(patternTemplates.length * namedEntityTypes.length);
    for (Pair<String, String> template : patternTemplates) {
      for (Pair<Character, String> namedEntityType : namedEntityTypes) {
        String tregex = String.format(template.first(), namedEntityType.first());
        String tsurgeon = String.format(template.second(), namedEntityType.second());

        markSimpleNEs.add(new Pair<>(TregexPattern.compile(tregex),
                Tsurgeon.parseOperation(tsurgeon)));
      }
    }
  };

  /**
   * Find all named entities which are not multi-word tokens and nest
   * them in named entity NP groups (`grup.nom.{lug,org,pers,otros}`).
   *
   * Do this only for "simple" NEs: the multi-word NEs have to be done
   * at a later step in `MultiWordPreprocessor`.
   */
  void markSimpleNamedEntities(Tree t) {
    Tsurgeon.processPatternsOnTree(markSimpleNEs, t);
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
      List<Tree> newNodes = new ArrayList<>(words.length);
      for (String word1 : words) {
        String word = normalizeTerminal(word1);

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
   * Strings of punctuation which should remain a single token.
   */
  private static final Pattern pPunct = Pattern.compile("[.,!?:/'=()-]+");

  /**
   * Characters which may separate words in a single token.
   */
  private static final String WORD_SEPARATORS = ",-_¡!¿?()/%";

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
  private static final Set<String> hyphenBoundMorphemes = new HashSet<>(Arrays.asList(
          "anti", // anti-Gil
          "co", // co-promotora
          "ex", // ex-diputado
          "meso", // meso-americano
          "neo", // neo-proteccionismo
          "pre", // pre-presidencia
          "pro", // pro-indonesias
          "quasi", // quasi-unidimensional
          "re", // re-flotamiento
          "semi", // semi-negro
          "sub" // sub-18
  ));

  /**
   * Prepare the given token for multi-word detection / extraction.
   *
   * This method makes up for some various oddities in corpus annotations.
   */
  private String prepareForMultiWordExtraction(String token) {
    return token.replaceAll("-fpa-", "(").replaceAll("-fpt-", ")");
  }

  /**
   * Return the (single or multiple) words which make up the given
   * token.
   *
   * TODO can't SpanishTokenizer handle most of this?
   */
  private String[] getMultiWords(String token) {
    token = prepareForMultiWordExtraction(token);

    Matcher punctMatcher = pPunct.matcher(token);
    if (punctMatcher.matches())
      return new String[] {token};

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

    List<String> words = new ArrayList<>();

    while (splitter.hasMoreTokens()) {
      String word = splitter.nextToken();
      remainingTokens--;

      if (shouldDropWord(word))
        // This is a delimiter that we should drop
        continue;

      if (remainingTokens >= 2 && hyphenBoundMorphemes.contains(word)) {
        String hyphen = splitter.nextToken();
        remainingTokens--;

        if (!hyphen.equals("-")) {
          // Ouch. We expected a hyphen here. Clean things up and keep
          // moving.
          words.add(word);

          if (!shouldDropWord(hyphen))
            words.add(hyphen);

          continue;
        }

        String freeMorpheme = splitter.nextToken();
        remainingTokens--;

        words.add(word + hyphen + freeMorpheme);
        continue;
      } else if (word.equals(",") && remainingTokens >= 1 && words.size() > 0) {
        int prevIndex = words.size() - 1;
        String prevWord = words.get(prevIndex);

        if (StringUtils.isNumeric(prevWord)) {
          String nextWord = splitter.nextToken();
          remainingTokens--;

          if (StringUtils.isNumeric(nextWord)) {
            words.set(prevIndex, prevWord + ',' + nextWord);
          } else {
            // Expected a number here.. clean up and move on
            words.add(word);
            words.add(nextWord);
          }

          continue;
        }
      }

      // Otherwise..
      words.add(word);
    }

    return words.toArray(new String[words.size()]);
  }

  /**
   * Determine if the given "word" which is part of a multiword token
   * should be dropped.
   */
  private boolean shouldDropWord(String word) {
    return word.length() == 1
      && WORD_SEPARATORS_DROP.indexOf(word.charAt(0)) != -1;
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
   */
  private Tree expandElisions(Tree t) {
    return Tsurgeon.processPatternsOnTree(elisionExpansions, t);
  }

  @SuppressWarnings("unchecked")
  private static final Pair<String, String>[] elisionExpansionStrs = new Pair[] {
    // Elided forms with a `prep` ancestor which has an `sn` phrase as a
    // right sibling

    new Pair(// Search for `sn` which is right sibling of closest `prep`
             // ancestor to the elided node; cascade down tree to lowest `sn`
             "/^(prep|sadv|conj)$/ <+(/^(prep|grup\\.(adv|cc|prep))$/) (sp000=sp < /(?i)^(del|al)$/=elided) <<` =sp " +
               "$+ (sn > (__ <+(sn) (sn=sn !< sn) << =sn) !$- sn)",

             // Insert the 'el' specifier as a constituent in adjacent
             // noun phrase
             "[relabel elided /(?i)l//] [insert (spec (da0000 el)) >0 sn]"),

    // Prepositional forms with a `prep` grandparent which has a
    // `grup.nom` phrase as a right sibling
    new Pair("prep < (sp000 < /(?i)^(del|al)$/=elided) $+ /grup\\.nom/=target",

             "[relabel elided /(?i)l//] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // Elided forms with a `prep` ancestor which has an adjectival
    // phrase as a right sibling ('al segundo', etc.)
    new Pair("prep < (sp000 < /(?i)^(del|al)$/=elided) $+ /s\\.a/=target",

             "[relabel elided /(?i)l//] " +
             // Turn neighboring adjectival phrase into a noun phrase,
             // adjoining original adj phrase beneath a `grup.nom`
             "[adjoinF (sn (spec (da0000 el)) (grup.nom foot@)) target]"),

    // "del que golpea:" insert 'el' as specifier into adjacent relative
    // phrase
    new Pair("sp < (prep=prep < (sp000 < /(?i)^(a|de)l$/=elided) $+ " +
      "(S=S <<, relatiu))",

             // Build a noun phrase in the neighboring relative clause
             // containing the 'el' specifier
             "[relabel elided /(?i)l//] " +
             "[adjoinF (sn (spec (da0000 el)) (grup.nom foot@)) S]"),

    // "al" + infinitive phrase
    new Pair("prep < (sp000 < /(?i)^(al|del)$/=elided) $+ " +
             // Looking for an infinitive directly to the right of the
             // "al" token, nested within one or more clause
             // constituents
             "(S=target <+(S) infinitiu=inf <<, =inf)",

             "[relabel elided /(?i)l//] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // "al no" + infinitive phrase
    new Pair("prep < (sp000 < /(?i)^al$/=elided) $+ (S=target <, neg <2 infinitiu)",

             "[relabel elided a] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // "al que quisimos tanto"
    new Pair("prep < (sp000 < /(?i)^al$/=elided) $+ relatiu=target",

             "[relabel elided a] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // "al de" etc.
    new Pair("prep < (sp000 < /(?i)^al$/=elided) $+ (sp=target <, prep)",

             "[relabel elided a] " +
             "[adjoinF (sn (spec (da0000 el)) (grup.nom foot@)) target]"),

    // leading adjective in sibling: "al chileno Fernando"
    new Pair("prep < (sp000 < /(?i)^(del|al)$/=elided) $+ " +
             "(/grup\\.nom/=target <, /s\\.a/ <2 /sn|nc0[sp]000/)",

             "[relabel elided /(?i)l//] " +
             "[adjoinF (sn (spec (da0000 el)) foot@) target]"),

    // "al" + phrase begun by participle -> "a lo <participle>"
    // e.g. "al conseguido" -> "a lo conseguido"
    new Pair("prep < (sp000 < /(?i)^(al|del)$/=elided) $+ (S=target < participi)",

             "[relabel elided /(?i)l//] " +
             "[adjoinF (sn (spec (da0000 lo)) foot@) target]"),

    // "del" used within specifier; e.g. "más del 30 por ciento"
    new Pair("spec < (sp000=target < /(?i)^del$/=elided) > sn $+ /grup\\.nom/",

             "[relabel elided /(?i)l//] " +
             "[insert (da0000 el) $- target]"),

    // "del," "al" in date phrases: "1 de enero del 2001"
    new Pair("sp000=kill < /(?i)^(del|al)$/ $+ w=target",

             "[delete kill] " +
             "[adjoinF (sp (prep (sp000 de)) (sn (spec (da0000 el)) foot@)) target]"),

    // "a favor del X," "en torno al Y": very common (and somewhat
    // complex) phrase structure that we can match
    new Pair("sp000 < /(?i)^(a|de)l$/=contraction >: (prep >` (/^grup\\.prep$/ " +
      ">` (prep=prep > sp $+ (sn=sn <, /^grup\\.(nom|[wz])/))))",

      "[relabel contraction /(?i)l//] [insert (spec (da0000 el)) >0 sn]"),

    // "en vez del X": same as above, except prepositional phrase
    // functions as conjunction (and is labeled as such)
    new Pair("sp000 < /(?i)^(a|de)l$/=contraction >: (prep >` (sp >: (conj $+ (sn=sn <, /^grup\\.(nom|[wz])/))))",

      "[relabel contraction /(?i)l//] [insert (spec (da0000 el)) >0 sn]"),

    // "a favor del X," "en torno al Y" where X, Y are doubly nested
    // substantives
    new Pair("sp000 < /(?i)^(a|de)l$/=contraction >: (prep >` (/^grup\\.prep$/ " +
      ">` (prep=prep > sp $+ (sn <, (sn=sn <, /^grup\\.(nom|[wz])/)))))",

      "[relabel contraction /(?i)l//] [insert (spec (da0000 el)) >0 sn]"),

    // "a favor del X," "en torno al Y" where X, Y already have
    // leading specifiers
    new Pair("sp000 < /(?i)^(a|de)l$/=contraction >: (prep >` (/^grup\\.prep$/ " +
      ">` (prep > sp $+ (sn=sn <, spec=spec))))",

      "[relabel contraction /(?i)l//] [insert (da0000 el) >0 spec]"),

    // "a favor del X," "en torno al Y" where X, Y are nominal
    // groups (not substantives)
    new Pair("sp000 < /(?i)^(a|de)l$/=contraction >: (prep >` (/^grup\\.prep$/ " +
      ">` (prep > sp $+ /^grup\\.(nom|[wz])$/=ng)))",

      "[adjoinF (sn (spec (da0000 el)) foot@) ng] [relabel contraction /(?i)l//]"),

    // "al," "del" as part of coordinating conjunction: "frente al,"
    // "además del"
    //
    // (nearby noun phrase labeled as nominal group)
    new Pair("sp000 < /(?i)^(de|a)l$/=elided >` (/^grup\\.cc$/ >: (conj $+ /^grup\\.nom/=gn))",
      "[relabel elided /(?i)l//] [adjoinF (sn (spec (da0000 el)) foot@) gn]"),

    // "al" + participle in adverbial phrase: "al contado," "al descubierto"
    new Pair("sp000=sp < /(?i)^al$/=elided $+ /^vmp/",
      "[relabel elided /(?i)l//] [insert (da0000 el) $- sp]"),

    // über-special case: 15021_20000218.tbf-5
    //
    // intentional: article should bind all of quoted phrase, even
    // though there are multiple clauses (kind of a crazy sentence)
    new Pair("prep < (sp000 < /(?i)^(al|del)$/=elided) $+ (S=S <+(S) (/^f/=punct $+ (S <+(S) (S <, infinitiu))))",
      "[relabel elided /(?i)l//] [adjoinF (sn (spec (da0000 el)) (grup.nom foot@)) S]"),

    // special case: "del todo" -> "de el todo" (flat)
    new Pair("__=sp < del=contraction >, __=parent $+ (__ < todo >` =parent)",
      "[relabel contraction de] [insert (da0000 el) $- sp]"),
  };

  private static final List<Pair<TregexPattern, TsurgeonPattern>> elisionExpansions =
    compilePatterns(elisionExpansionStrs);

  private static TregexPattern conmigoPattern =
    TregexPattern.compile("/(?i)^con[mst]igo$/=conmigo > (/^pp/ > (/^grup\\.nom$/ > sn=sn))");

  /**
   * ¡Venga, expand conmigo!
   */
  private static Tree expandConmigo(Tree t) {
    TregexMatcher matcher = conmigoPattern.matcher(t);

    while (matcher.find()) {
      Tree conmigoNode = matcher.getNode("conmigo");
      String word = conmigoNode.value();

      String newPronoun = null;
      if (word.equalsIgnoreCase("conmigo"))
        newPronoun = "mí";
      else if (word.equalsIgnoreCase("contigo"))
        newPronoun = "ti";
      else if (word.equalsIgnoreCase("consigo"))
        newPronoun = "sí";

      if (word.charAt(0) == 'C')
        newPronoun = newPronoun.toUpperCase();

      String tsurgeon = String.format(
        "[relabel conmigo /%s/]" +
          "[adjoinF (sp (prep (sp000 con)) foot@) sn]",
        newPronoun);
      TsurgeonPattern pattern = Tsurgeon.parseOperation(tsurgeon);
      t = pattern.matcher().evaluate(t, matcher);
    }

    return t;
  }

  private static List<Pair<TregexPattern, TsurgeonPattern>> compilePatterns(Pair<String, String>[] patterns) {
    List<Pair<TregexPattern, TsurgeonPattern>> ret = new ArrayList<>(patterns.length);
    for (Pair<String, String> pattern : patterns)
      ret.add(new Pair<>(TregexPattern.compile(pattern.first()),
              Tsurgeon.parseOperation(pattern.second())));

    return ret;
  }

}
