package edu.stanford.nlp.trees.ud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.pipeline.CoNLLUReader;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.EnglishPatterns;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.NPTmpRetainingTreeNormalizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations;
import edu.stanford.nlp.trees.UniversalPOSMapper;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;


/**
 *
 * Adds lemmata and features to an English CoNLL-U dependencies
 * treebank.
 *
 * @author Sebastian Schuster
 *
 */

public class UniversalDependenciesFeatureAnnotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(UniversalDependenciesFeatureAnnotator.class);


  private static final String FEATURE_MAP_FILE = "edu/stanford/nlp/models/ud/feature_map.txt";
  private HashMap<String,CoNLLUFeatures> posFeatureMap;
  private Map<String,CoNLLUFeatures> wordPosFeatureMap;

  private final Morphology morphology = new Morphology();


  public UniversalDependenciesFeatureAnnotator() {
    loadFeatureMap();
  }


  private void loadFeatureMap() {
    try (Reader r = IOUtils.readerFromString(FEATURE_MAP_FILE)) {
      BufferedReader br = new BufferedReader(r);

      posFeatureMap = new HashMap<>();
      wordPosFeatureMap = new HashMap<>();

      String line;
      while ((line = br.readLine()) != null) {
        String[] parts = line.split("\\s+");

        if (parts.length < 3) continue;

        if (parts[0].equals("*")) {
          posFeatureMap.put(parts[1], new CoNLLUFeatures(parts[2]));
        } else {
          wordPosFeatureMap.put(parts[0] + '_' + parts[1], new CoNLLUFeatures(parts[2]));
        }
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }


  private HashMap<String,String> getPOSFeatures(String word, String pos) {
    HashMap<String, String> features = new HashMap<>();
    String wordPos = word.toLowerCase() + '_' + pos;
    if (wordPosFeatureMap.containsKey(wordPos)) {
       features.putAll(wordPosFeatureMap.get(wordPos));
    } else if (posFeatureMap.containsKey(pos)) {
      features.putAll(posFeatureMap.get(pos));
    }

    if (isOrdinal(word, pos)) {
      features.put("NumType", "Ord");
    }

    if (isMultiplicative(word, pos)) {
      features.put("NumType", "Mult");
    }

    String polarity = POLARITY.get(word.toLowerCase() + '_' + pos);
    if (polarity != null) {
      features.put("Polarity", polarity);
    }

    return features;
  }

  private static final String ORDINAL_UNITS = "first|second|third|fourth|fifth|sixth|seventh|eigth|eighth|ninth";

  /**
   * Ordinals written as words up to ninety-ninth, or with digits.
   * "Hundredth" and above are left out, since they are usually fractions.
   */
  private static final String ORDINAL_EXPRESSION =
    "^((twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)-(" + ORDINAL_UNITS + ")|" +
    ORDINAL_UNITS + "|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|" +
    "seventeenth|eighteenth|nineteenth|twentieth|thirtieth|fortieth|fiftieth|sixtieth|" +
    "seventieth|eightieth|ninetieth|([0-9,.]+(th|st|nd|rd)))$";

  private static final String MULTIPLICATIVE_EXPRESSION = "^(once|twice)$";

  /**
   * The Polarity of a word with a given tag, keyed as word_TAG, as in EWT and
   * GUM.  Negation is not, n't (with a straight or curly apostrophe) and nt
   * as adverbs, nor and neither as conjunctions, and no as an interjection;
   * yes as an interjection is positive.  Neither as a determiner or adverb
   * has no Polarity.
   */
  private static final Map<String, String> POLARITY = Map.of(
    "not_RB", "Neg",
    "n't_RB", "Neg",
    "n\u2019t_RB", "Neg",
    "nt_RB", "Neg",
    "nor_CC", "Neg",
    "neither_CC", "Neg",
    "no_UH", "Neg",
    "yes_UH", "Pos");

  /** Roman numerals, in either case */
  private static final String ROMAN_EXPRESSION = "(?i)^[ivxlcdm]+$";

  /**
   * The NumForm of a word with a NumType, as in EWT and GUM: Digit for numbers
   * without letters ("7:30", "+1", "'72", "½"), Combi for numbers with
   * letters ("10th", "1990s"), Roman for Roman numerals ("XIV", "iv"), and
   * Word for the rest ("two", "million", "first", "once").  Roman letters
   * right after a number are a multiplier rather than a numeral, as in
   * "5 M" or "10 mm" for millions, so they are Word.
   */
  static String numForm(String word, SemanticGraph sg, int index) {
    if (word.matches(".*\\p{N}.*")) {
      return word.matches(".*\\p{L}.*") ? "Combi" : "Digit";
    }
    if (word.matches(ROMAN_EXPRESSION) && !followsNumber(sg, index)) {
      return "Roman";
    }
    return "Word";
  }

  /** Whether the word before this one is a number written with digits */
  private static boolean followsNumber(SemanticGraph sg, int index) {
    IndexedWord previous = sg.getNodeByIndexSafe(index - 1);
    return previous != null && "CD".equals(previous.tag()) && previous.value().matches(".*\\p{N}.*");
  }

  /**
   * Ordinal adjectives and adverbs, as in EWT and GUM.  An ordinal noun is
   * only counted when it is written with digits, as in "Dec. 10th": "a
   * second" and "a third" are usually a unit of time and a fraction.
   */
  private static boolean isOrdinal(String word, String pos) {
    String lower = word.toLowerCase();
    if ( ! lower.matches(ORDINAL_EXPRESSION))
      return false;

    if (pos.equals("JJ") || pos.equals("RB"))
      return true;

    return pos.equals("NN") && lower.matches(".*[0-9].*");
  }

  private static boolean isMultiplicative(String word, String pos) {
    if ( ! pos.equals("RB"))
      return false;

    return word.toLowerCase().matches(MULTIPLICATIVE_EXPRESSION);
  }

  private static String SELF_REGEX = EnglishPatterns.selfRegex.replace("/", "");

  private static HashMap<String, String> getGraphFeatures(SemanticGraph sg, IndexedWord word) {
    HashMap<String, String> features = new HashMap<>();

    /* Determine the case of "you". */
    if (word.tag().equals("PRP") &&
        (word.value().equalsIgnoreCase("you") ||
         word.value().equalsIgnoreCase("it"))) {
      features.put("Case", pronounCase(sg, word));
    }

    /* Determine the person of "was". */
    if (word.tag().equals("VBD") &&
        word.value().equalsIgnoreCase("was")) {
      String person = wasPerson(sg, word);
      if (person != null) {
        features.put("Person", person);
      }
    }

    /* Determine features of relative and interrogative pronouns. */
    features.putAll(getRelAndIntPronFeatures(sg, word));

    /* Determine features of gerunds and present participles. */
    if (word.tag().equals("VBG")) {
      if (hasBeAux(sg, word)) {
        features.put("VerbForm", "Part");
        features.put("Tense", "Pres");
      } else {
        features.put("VerbForm", "Ger");
      }
    }

    /* Determine whether reflexive pronoun is reflexive or intensive. */
    if (word.value().matches(SELF_REGEX) && word.tag().equals("PRP")) {
      IndexedWord parent = sg.getParent(word);
      if (parent != null) {
        SemanticGraphEdge edge = sg.getEdge(parent, word);
        if (edge.getRelation() != UniversalEnglishGrammaticalRelations.NP_ADVERBIAL_MODIFIER) {
          features.put("Case", "Acc");
          features.put("Reflex", "Yes");
        }
      }
    }

    /* Voice feature. */
    if (word.tag().equals("VBN")) {
      if (sg.hasChildWithReln(word, UniversalEnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER)) {
        features.put("Voice", "Pass");
      }
    }

    return features;
  }


  /**
   * Determine the case of the pronoun "you" or "it".
   */
  private static String pronounCase(SemanticGraph sg, IndexedWord word) {

    word = sg.getNodeByIndex(word.index());

    IndexedWord parent = sg.getParent(word);
    if (parent != null) {
      SemanticGraphEdge edge = sg.getEdge(parent, word);
      if (edge != null) {
        if (edge.getRelation().equals(UniversalEnglishGrammaticalRelations.DIRECT_OBJECT) ||
            edge.getRelation().equals(UniversalEnglishGrammaticalRelations.INDIRECT_OBJECT)) {
          /* "you" is an object. */
          return "Acc";
        } else if (UniversalEnglishGrammaticalRelations.NOMINAL_MODIFIER.isAncestor(edge.getRelation())
            || edge.getRelation() == GrammaticalRelation.ROOT) {
          if (sg.hasChildWithReln(word, UniversalEnglishGrammaticalRelations.CASE_MARKER)) {
            /* "you" is the head of a prepositional phrase. */
            return "Acc";
          }
        }
      }
    }
    return "Nom";
  }


  /**
   * Determine the person of "was".
   */
  private static String wasPerson(SemanticGraph sg, IndexedWord word) {
    IndexedWord subj = sg.getChildWithReln(word, UniversalEnglishGrammaticalRelations.NOMINAL_SUBJECT);

    if (subj == null) {
      subj = sg.getChildWithReln(word, UniversalEnglishGrammaticalRelations.NOMINAL_PASSIVE_SUBJECT);
    }

    /* A subject of its own settles the person: only "I" is first person. */
    if (subj != null) {
      return subj.word().equalsIgnoreCase("i") ? "1" : "3";
    }

    /* So does a clausal subject or an expletive, which are third person. */
    for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(word)) {
      GrammaticalRelation rel = edge.getRelation();
      if (UniversalEnglishGrammaticalRelations.SUBJECT.isAncestor(rel) ||
          UniversalEnglishGrammaticalRelations.EXPLETIVE.equals(rel)) {
        return "3";
      }
    }

    /* Without a subject, look for one on the head of an auxiliary,
       a copula or a conjunct. */
    IndexedWord parent = sg.getParent(word);
    if (parent == null) {
      return null;
    }

    SemanticGraphEdge edge = sg.getEdge(parent, word);
    if (edge == null) {
      return null;
    }

    if (UniversalEnglishGrammaticalRelations.AUX_MODIFIER.equals(edge.getRelation()) ||
        UniversalEnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER.equals(edge.getRelation())) {
      return wasPerson(sg, parent);
    }

    /* As a copula, "was" shares the subject of the predicate.  A predicate
       with a clausal subject or no subject leaves it third person, as in
       "What triggered the clash was a skirmish". */
    if (UniversalEnglishGrammaticalRelations.COPULA.equals(edge.getRelation())) {
      String person = wasPerson(sg, parent);
      return person != null ? person : "3";
    }

    if (UniversalEnglishGrammaticalRelations.CONJUNCT.isAncestor(edge.getRelation())) {
      /* Check if the subject of the head of a conjunction is "I". */
      return wasPerson(sg, parent);
    }

    return "3";
  }


  /**
   * Extracts features from relative and interrogative pronouns.
   */
  private static HashMap<String, String> getRelAndIntPronFeatures(SemanticGraph sg, IndexedWord word) {
    HashMap<String, String> features = new HashMap<>();

    if (word.tag().startsWith("W")) {
      boolean isRel = false;

      IndexedWord parent = sg.getParent(word);
      if (parent != null) {
        IndexedWord parentParent = sg.getParent(parent);
        if (parentParent != null) {
          SemanticGraphEdge edge = sg.getEdge(parentParent, parent);
          isRel = edge.getRelation().equals(UniversalEnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER);
        }
      }


      if (isRel) {
        features.put("PronType", "Rel");
      } else {
        if (word.value().equalsIgnoreCase("that")) {
          features.put("PronType", "Dem");
        } else {
          features.put("PronType", "Int");
        }
      }
    }

    return features;
  }


  private static Iterator<Tree> treebankIterator(String path) {
    /* Remove empty nodes and strip indices from internal nodes but keep
       functional tags. */
    Treebank tb = new MemoryTreebank(new NPTmpRetainingTreeNormalizer(0, false, 1, false));
    tb.loadPath(path);
    return tb.iterator();
  }

  /**
   * A VB under an S-IMP, with no other S in between.  A VB whose VP is the
   * complement of another VB is skipped, so in {@code (S-IMP (VP (VB Do) (RB n't) (VP (VB sleep))))}
   * only "Do" is imperative and "sleep" stays an infinitive.
   */
  private static final TregexPattern IMPERATIVE_PATTERN =
    TregexPattern.compile("__ > (VB !> (VP > (VP < VB))) >+(/^[^S]/) S-IMP");

  /**
   * Returns the indices of all imperative verbs in the
   * tree t, as marked by the S-IMP function tag.
   */
  private static Set<Integer> getImperatives(Tree t) {
    Set<Integer> imps = new HashSet<>();

    TregexMatcher matcher = IMPERATIVE_PATTERN.matcher(t);

    while (matcher.find()) {
      List<Label> verbs = matcher.getMatch().yield();
      CoreLabel cl = (CoreLabel) verbs.get(0);
      imps.add(cl.index());
    }

    return imps;

  }

  /**
   * Returns the indices of the imperative verbs in {@code sg}, found from the
   * dependency structure.  This works on parser output, which has no S-IMP
   * function tags.
   *<br>
   * The head of the sentence is imperative if it has no subject, no
   * infinitival "to", no "why", and no auxiliary or copula other than a bare
   * VB.  "Why not compromise?" is a bare infinitive, as in EWT and GUM.
   * So is a question without "please", which is usually an elided
   * "do you": "Remember those refrigerators?", "Sound familiar?".
   * The word that carries the imperative is the first bare VB among the head,
   * its auxiliaries and its copula: "Go home", "Do n't sleep", "Be careful",
   * "Do n't be late".  Conjuncts and parataxis of an imperative are checked the
   * same way ("Copy and paste"), except under a do-support auxiliary, which
   * the conjuncts share: in "Do n't sleep or eat", "eat" stays an infinitive.
   */
  private static Set<Integer> getGraphImperatives(SemanticGraph sg) {
    Set<Integer> imps = new HashSet<>();
    for (IndexedWord root : sg.getRoots()) {
      addGraphImperatives(sg, root, imps);
    }
    return imps;
  }

  private static void addGraphImperatives(SemanticGraph sg, IndexedWord head, Set<Integer> imps) {
    IndexedWord imperative = "VB".equals(head.tag()) ? head : null;
    boolean question = false;
    boolean please = false;
    for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(head)) {
      GrammaticalRelation rel = edge.getRelation();
      IndexedWord child = edge.getDependent();
      if (UniversalEnglishGrammaticalRelations.SUBJECT.isAncestor(rel) ||
          UniversalEnglishGrammaticalRelations.EXPLETIVE.equals(rel)) {
        return;
      }
      if (UniversalEnglishGrammaticalRelations.MARKER.equals(rel) && child.value().equalsIgnoreCase("to")) {
        return;
      }
      if (child.value().equalsIgnoreCase("why")) {
        return;
      }
      if (".".equals(child.tag()) && child.value().contains("?")) {
        question = true;
      }
      if (child.value().equalsIgnoreCase("please")) {
        please = true;
      }
      if (UniversalEnglishGrammaticalRelations.AUX_MODIFIER.equals(rel) ||
          UniversalEnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER.equals(rel) ||
          UniversalEnglishGrammaticalRelations.COPULA.equals(rel)) {
        // a modal or an inflected auxiliary means this is not an imperative
        if (!"VB".equals(child.tag())) {
          return;
        }
        if (imperative == null || child.index() < imperative.index()) {
          imperative = child;
        }
      }
    }
    if (imperative == null || (question && !please)) {
      return;
    }
    imps.add(imperative.index());

    boolean doSupport = imperative != head && imperative.value().equalsIgnoreCase("do");
    if (doSupport) {
      return;
    }
    for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(head)) {
      GrammaticalRelation rel = edge.getRelation();
      if (UniversalEnglishGrammaticalRelations.CONJUNCT.isAncestor(rel) ||
          UniversalEnglishGrammaticalRelations.PARATAXIS.equals(rel)) {
        addGraphImperatives(sg, edge.getDependent(), imps);
      }
    }
  }


  /**
   * Returns true if {@code word} has an auxiliary verb attached to it.
   *
   */
  @SuppressWarnings("unused")
  private static boolean hasAux(SemanticGraph sg, IndexedWord word) {
   if (sg.hasChildWithReln(word, UniversalEnglishGrammaticalRelations.AUX_MODIFIER)) {
     return true;
   }

   IndexedWord gov = sg.getParent(word);
   if (gov != null) {
     SemanticGraphEdge edge = sg.getEdge(gov, word);
     if (UniversalEnglishGrammaticalRelations.CONJUNCT.isAncestor(edge.getRelation()) ||
         UniversalEnglishGrammaticalRelations.COPULA.equals(edge.getRelation())) {
       return hasAux(sg, gov);
     }

   }

   return false;

  }

  /**
   * Returns true if {@code word} has an infinitival "to" attached to it.
   */
  @SuppressWarnings("unused")
  private static boolean hasTo(SemanticGraph sg, IndexedWord word) {
    /* Check for infinitival to. */
    if (sg.hasChildWithReln(word, UniversalEnglishGrammaticalRelations.MARKER)) {
      for (IndexedWord marker : sg.getChildrenWithReln(word, UniversalEnglishGrammaticalRelations.MARKER)) {
        if (marker.value().equalsIgnoreCase("to")) {
          return true;
        }
      }
    }

    return false;
  }

  private static final String BE_REGEX = EnglishPatterns.beAuxiliaryRegex.replace("/", "");

  /**
   * Returns true if {@code word} has an inflection of "be" as an auxiliary.
   */
  private static boolean hasBeAux(SemanticGraph sg, IndexedWord word) {

    for (IndexedWord aux : sg.getChildrenWithReln(word, UniversalEnglishGrammaticalRelations.AUX_MODIFIER)) {
      if (aux.value().matches(BE_REGEX)) {
        return true;
      }
    }

    /* Check if head of conjunction has an auxiliary in case the word is part of a conjunction */
    IndexedWord gov = sg.getParent(word);
    if (gov != null) {
      SemanticGraphEdge edge = sg.getEdge(gov, word);
      if (UniversalEnglishGrammaticalRelations.CONJUNCT.isAncestor(edge.getRelation())) {
        return hasBeAux(sg, gov);
      }
    }

    return false;
  }

  /**
   * True if this word carries on one which was split in two
   *<br>
   * The lemma of such a word belongs to the first piece, so the rest are
   * left without one rather than being given a lemma of their own.
   */
  private static boolean isGoesWith(SemanticGraph sg, IndexedWord word) {
    for (SemanticGraphEdge edge : sg.incomingEdgeIterable(word)) {
      if (edge.getRelation().getShortName().equals("goeswith")) {
        return true;
      }
    }
    return false;
  }

  /** The words of a sentence separated by spaces, for an error message */
  private static String sentenceText(SemanticGraph sg) {
    StringBuilder text = new StringBuilder();
    for (IndexedWord word : sg.vertexListSorted()) {
      if (text.length() > 0) {
        text.append(' ');
      }
      text.append(word.get(CoreAnnotations.TextAnnotation.class));
    }
    return text.toString();
  }

  public void addFeatures(SemanticGraph sg, Tree tree, boolean addLemma, boolean addUPOS) {

    Set<Integer> imperatives = tree != null ? getImperatives(tree) : new HashSet<>();
    imperatives.addAll(getGraphImperatives(sg));

    for (IndexedWord word : sg.vertexListSorted()) {
      String posTag = word.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      String token = word.get(CoreAnnotations.TextAnnotation.class);
      Integer index = word.get(CoreAnnotations.IndexAnnotation.class);
      CoNLLUFeatures wordFeatures = word.get(CoreAnnotations.CoNLLUFeats.class);

      // the features are worked out from the PTB tag, so a word without one
      // cannot be featurized.  an empty XPOS column reaches here as null or
      // as "_" depending on which reader the sentence came through
      if (posTag == null || posTag.equals("_")) {
        throw new IllegalArgumentException("Cannot add features to word " + index + ", " + token +
                                           ": it has no XPOS, and the features are worked out from" +
                                           " the PTB tag.  Sentence: " + sentenceText(sg));
      }

      if (wordFeatures == null) {
        wordFeatures = new CoNLLUFeatures();
        word.set(CoreAnnotations.CoNLLUFeats.class, wordFeatures);
      }

        /* Features that only depend on the word and the PTB POS tag. */
      wordFeatures.putAll(getPOSFeatures(token, posTag));

        /* The form of a number, which for Roman letters depends on the word before. */
      if (wordFeatures.containsKey("NumType")) {
        wordFeatures.put("NumForm", numForm(token, sg, index));
      }

        /* Semantic graph features. */
      wordFeatures.putAll(getGraphFeatures(sg, word));

        /* Handle VBs. */
      if (imperatives.contains(index)) {
          /* Imperative */
        wordFeatures.put("VerbForm", "Fin");
        wordFeatures.put("Mood", "Imp");
      } else if (posTag.equals("VB")) {
             /* Infinitive */
        wordFeatures.put("VerbForm", "Inf");

          /* Subjunctive detection too unreliable. */
        //} else {
        //  /* Present subjunctive */
        //  wordFeatures.put("VerbForm", "Fin");
        //  wordFeatures.put("Tense", "Pres");
        //  wordFeatures.put("Mood", "Subj");
        //}
      }



      String lemma = word.get(CoreAnnotations.LemmaAnnotation.class);
      if (addLemma && (lemma == null || lemma.equals("_")) && !isGoesWith(sg, word)) {
        word.set(CoreAnnotations.LemmaAnnotation.class, morphology.lemma(token, posTag));
      }
    }

    if (addUPOS && tree != null) {
      tree = UniversalPOSMapper.mapTree(tree);
      List<Label> uPOSTags = tree.preTerminalYield();
      List<IndexedWord> yield = sg.vertexListSorted();
      // int len = yield.size();
      for (IndexedWord word : yield) {
        Label uPOSTag = uPOSTags.get(word.index() - 1);
        word.set(CoreAnnotations.CoarseTagAnnotation.class, uPOSTag.value());
      }
    }
  }

  public static void help() {
      log.info("Usage: ");
      log.info("java " +
               UniversalDependenciesFeatureAnnotator.class.getCanonicalName() +
               " -conlluFile conllu_file (-treeFile tree_file | -noTrees) [-addUPOS -escapeParenthesis]");
      log.info("  -noTrees annotates without constituency trees.  The trees are how an imperative is");
      log.info("  told apart from an infinitive, so without them every VB is marked as an infinitive.");
  }

  /**
   * Add features to the CoNLL-U file the properties name, writing it to out
   *<br>
   * conlluFile is the treebank and treeFile holds the constituency trees of
   * the same sentences in the same order.  The trees are how an imperative
   * is told apart from an infinitive, which the PTB tags alone do not do.
   * noTrees annotates without them instead, marking every VB as an
   * infinitive; it is a flag of its own so that leaving out treeFile by
   * accident is not taken as asking for that.  addUPOS takes the UPOS tags
   * from the trees as well, and escapeParenthesis leaves parentheses
   * escaped in the output.
   */
  public static void annotate(Properties props, PrintStream out) throws IOException {
    String treeFile = props.getProperty("treeFile");
    String coNLLUFile = props.getProperty("conlluFile");
    boolean noTrees = PropertiesUtils.getBool(props, "noTrees", false);
    boolean addUPOS = PropertiesUtils.getBool(props, "addUPOS", false);
    boolean escapeParens = PropertiesUtils.getBool(props, "escapeParenthesis", false);

    if (treeFile != null && noTrees) {
      throw new IllegalArgumentException("Give either -treeFile or -noTrees, not both");
    }
    if (noTrees && addUPOS) {
      throw new IllegalArgumentException("-addUPOS takes the UPOS tags from the trees, so it cannot be used with -noTrees");
    }
    if (coNLLUFile == null || (treeFile == null && !noTrees)) {
      help();
      return;
    }

    UniversalDependenciesFeatureAnnotator featureAnnotator = new UniversalDependenciesFeatureAnnotator();

    CoNLLUDocumentWriter depWriter = new CoNLLUDocumentWriter();
    Iterator<Tree> treeIt = noTrees ? null : treebankIterator(treeFile);

    try (CoNLLUReader.SentenceIterator sentences = new CoNLLUReader().sentenceIterator(coNLLUFile)) {
      while (sentences.hasNext()) {
        CoreMap sentence = sentences.next();
        SemanticGraph sg = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        // the enhanced graph is written back out as it was read, and is null
        // for a sentence which had no DEPS column
        SemanticGraph enhanced = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
        Tree t = noTrees ? null : treeIt.next();

        if (!noTrees && (t == null || t.yield().size() != sg.size())) {

          StringBuilder sentenceSb = new StringBuilder();
          for (IndexedWord word : sg.vertexListSorted()) {
            sentenceSb.append(word.get(CoreAnnotations.TextAnnotation.class));
            sentenceSb.append(' ');
          }

          throw new RuntimeException("CoNLL-U file and tree file are not aligned. \n"
                  + "Sentence: " + sentenceSb + '\n'
                  + "Tree: " + ((t == null) ? "null" : t.pennString()));
        }

        featureAnnotator.addFeatures(sg, t, true, addUPOS);

        // the rows are written from the enhanced graph whenever there is
        // one, so it is only used if every word of the sentence is in it.  a
        // word with no enhanced dependencies of its own is not in it, and
        // would be left out of the file
        if (enhanced != null && !enhanced.vertexSet().containsAll(sg.vertexSet())) {
          enhanced = null;
        }

        out.print(depWriter.printSemanticGraph(sg, enhanced, !escapeParens,
                                               sentence.get(CoreAnnotations.CommentsAnnotation.class)));
      }
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      help();
      return;
    }
    annotate(StringUtils.argsToProperties(args), System.out);
  }

}
