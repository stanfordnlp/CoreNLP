package edu.stanford.nlp.trees.ud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.semgraph.SemanticGraph;
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
  private HashMap<String,HashMap<String,String>> posFeatureMap;
  private HashMap<String,HashMap<String,String>> wordPosFeatureMap;

  private final Morphology morphology = new Morphology();


  public UniversalDependenciesFeatureAnnotator() throws IOException {
    loadFeatureMap();
  }


  private void loadFeatureMap() throws IOException {
    Reader r = IOUtils.readerFromString(FEATURE_MAP_FILE);
    BufferedReader br = new BufferedReader(r);

    posFeatureMap = new HashMap<>();
    wordPosFeatureMap = new HashMap<>();

    String line;
    while ((line = br.readLine()) != null) {
      String[] parts = line.split("\\s+");

      if (parts.length < 3) continue;

      if (parts[0].equals("*")) {
        posFeatureMap.put(parts[1], CoNLLUUtils.parseFeatures(parts[2]));
      } else {
        wordPosFeatureMap.put(parts[0] + '_' + parts[1], CoNLLUUtils.parseFeatures(parts[2]));
      }
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

    return features;
  }

  private static final String ORDINAL_EXPRESSION = "^(first|second|third|fourth|fifth|sixth|seventh|eigth|ninth|tenth|([0-9,.]+(th|st|nd|rd)))$";

  private static final String MULTIPLICATIVE_EXPRESSION = "^(once|twice)$";

  private static boolean isOrdinal(String word, String pos) {

    if ( ! pos.equals("JJ"))
      return false;

    return word.toLowerCase().matches(ORDINAL_EXPRESSION);
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
        if (UniversalEnglishGrammaticalRelations.OBJECT.isAncestor(edge.getRelation())) {
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

    if (subj != null) {
      if (subj.word().equalsIgnoreCase("i")) {
        /* "I" is the subject of "was". */
        return "1";
      }
    }

    IndexedWord parent = sg.getParent(word);
    if (parent == null) {
      return subj != null ? "3" : null;
    }

    SemanticGraphEdge edge = sg.getEdge(parent, word);
    if (edge == null) {
      return subj != null ? "3" : null;
    }

    if (UniversalEnglishGrammaticalRelations.AUX_MODIFIER.equals(edge.getRelation()) ||
        UniversalEnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER.equals(edge.getRelation())) {
      return wasPerson(sg, parent);
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

  private static TregexPattern IMPERATIVE_PATTERN = TregexPattern.compile("__ > VB >+(/^[^S]/) S-IMP");

  /**
   * Returns the indices of all imperative verbs in the
   * tree t.
   *
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

  private static String BE_REGEX = EnglishPatterns.beAuxiliaryRegex.replace("/", "");

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

  public void addFeatures(SemanticGraph sg, Tree t, boolean addLemma, boolean addUPOS) {

    Set<Integer> imperatives = t != null ? getImperatives(t) : new HashSet<>();

    for (IndexedWord word : sg.vertexListSorted()) {
      String posTag = word.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      String token = word.get(CoreAnnotations.TextAnnotation.class);
      Integer index = word.get(CoreAnnotations.IndexAnnotation.class);
      HashMap<String, String> wordFeatures = word.get(CoreAnnotations.CoNLLUFeats.class);

      if (wordFeatures == null) {
        wordFeatures = new HashMap<>();
        word.set(CoreAnnotations.CoNLLUFeats.class, wordFeatures);
      }

        /* Features that only depend on the word and the PTB POS tag. */
      wordFeatures.putAll(getPOSFeatures(token, posTag));

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
      if (addLemma && (lemma == null || lemma.equals("_"))) {
        word.set(CoreAnnotations.LemmaAnnotation.class, morphology.lemma(token, posTag));
      }
    }

    if (addUPOS && t != null) {
      t = UniversalPOSMapper.mapTree(t);
      List<Label> uPOSTags = t.preTerminalYield();
      List<IndexedWord> yield = sg.vertexListSorted();
      int len = yield.size();
      for (IndexedWord word : yield) {
        Label uPOSTag = uPOSTags.get(word.index() - 1);
        word.set(CoreAnnotations.CoarseTagAnnotation.class, uPOSTag.value());
      }
    }
  }


  public static void main(String[] args) throws IOException {

    if (args.length < 2) {
      log.info("Usage: ");
      log.info("java ");
      log.info(UniversalDependenciesFeatureAnnotator.class.getCanonicalName());
      log.info(" CoNLL-U_file tree_file [-addUPOS -escapeParenthesis]");
      return;
    }

    String coNLLUFile = args[0];
    String treeFile = args[1];

    boolean addUPOS = false;
    boolean escapeParens = false;

    for (int i = 2; i < args.length; i++) {
      if (args[i].equals("-addUPOS")) {
        addUPOS = true;
      } else if (args[i].equals("-escapeParenthesis")) {
        escapeParens = true;
      }
    }

    UniversalDependenciesFeatureAnnotator featureAnnotator = new UniversalDependenciesFeatureAnnotator();

    Reader r = IOUtils.readerFromString(coNLLUFile);
    CoNLLUDocumentReader depReader = new CoNLLUDocumentReader();
    CoNLLUDocumentWriter depWriter = new CoNLLUDocumentWriter();
    Iterator<SemanticGraph> it = depReader.getIterator(r);

    Iterator<Tree> treeIt = treebankIterator(treeFile);

    while (it.hasNext()) {
      SemanticGraph sg = it.next();
      Tree t = treeIt.next();

      if (t == null || t.yield().size() != sg.size()) {

        StringBuilder sentenceSb = new StringBuilder();
        for (IndexedWord word : sg.vertexListSorted()) {
          sentenceSb.append(word.get(CoreAnnotations.TextAnnotation.class));
          sentenceSb.append(' ');
        }

        throw new RuntimeException("CoNLL-U file and tree file are not aligned. \n"
                + "Sentence: " + sentenceSb + '\n'
                + "Tree: " + t.pennString());
      }

      featureAnnotator.addFeatures(sg, t, true, addUPOS);

      System.out.print(depWriter.printSemanticGraph(sg, !escapeParens));

    }
  }

}

