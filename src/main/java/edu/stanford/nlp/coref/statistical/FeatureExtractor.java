package edu.stanford.nlp.coref.statistical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.coref.CorefRules;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.data.Dictionaries.Number;
import edu.stanford.nlp.coref.data.Dictionaries.Person;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.Document.DocType;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.coref.md.RuleBasedCorefMentionFinder;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.SpeakerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * A class for featurizing mention pairs and individual mentions.
 *
 * @author Kevin Clark
 */
public class FeatureExtractor {

  private static final int MIN_WORD_COUNT = 20;
  private static final int BIN_EXACT = 10;
  private static final double BIN_EXPONENT = 1.5;
  private static final Map<Integer, String> SINGLETON_FEATURES = new HashMap<>();
  static {
    SINGLETON_FEATURES.put(2, "animacy");
    SINGLETON_FEATURES.put(3, "person-coarse");
    SINGLETON_FEATURES.put(4, "number");
    SINGLETON_FEATURES.put(5, "position");
    SINGLETON_FEATURES.put(6, "relation");
    SINGLETON_FEATURES.put(7, "quantification");
    SINGLETON_FEATURES.put(8, "modifiers");
    SINGLETON_FEATURES.put(9, "negation");
    SINGLETON_FEATURES.put(10, "modal");
    SINGLETON_FEATURES.put(11, "attitude");
    SINGLETON_FEATURES.put(12, "coordination");
  }

  private final Dictionaries dictionaries;
  private final Set<String> vocabulary;
  private final Compressor<String> compressor;
  private final boolean useConstituencyParse;
  private final boolean useDocSource;

  public FeatureExtractor(Properties props, Dictionaries dictionaries,
      Compressor<String> compressor) {
    this(props, dictionaries, compressor, StatisticalCorefTrainer.wordCountsFile);
  }

  public FeatureExtractor(Properties props, Dictionaries dictionaries,
      Compressor<String> compressor, String wordCountsPath) {
    this(props, dictionaries, compressor, loadVocabulary(wordCountsPath));
  }

  public FeatureExtractor(Properties props, Dictionaries dictionaries,
      Compressor<String> compressor, Set<String> vocabulary) {
    this.dictionaries = dictionaries;
    this.compressor = compressor;
    this.vocabulary = vocabulary;
    this.useDocSource = CorefProperties.conll(props);
    this.useConstituencyParse = CorefProperties.useConstituencyParse(props);
  }

  private static Set<String> loadVocabulary(String wordCountsPath) {
    Set<String> vocabulary = new HashSet<>();
    try {
      Counter<String> counts = IOUtils.readObjectFromURLOrClasspathOrFileSystem(wordCountsPath);
      for (Map.Entry<String, Double> e : counts.entrySet()) {
        if (e.getValue() > MIN_WORD_COUNT) {
          vocabulary.add(e.getKey());
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Error loading word counts", e);
    }
    return vocabulary;
  }

  public DocumentExamples extract(int id, Document document,
      Map<Pair<Integer, Integer>, Boolean> labeledPairs) {
    return extract(id, document, labeledPairs, compressor);
  }

  public DocumentExamples extract(int id, Document document,
      Map<Pair<Integer, Integer>, Boolean> labeledPairs, Compressor<String> compressor) {
    List<Mention> mentionsList = CorefUtils.getSortedMentions(document);
    Map<Integer, List<Mention>> mentionsByHeadIndex = new HashMap<>();
    for (Mention m : mentionsList) {
      List<Mention> withIndex = mentionsByHeadIndex.get(m.headIndex);
      if (withIndex == null) {
        withIndex = new ArrayList<>();
        mentionsByHeadIndex.put(m.headIndex, withIndex);
      }
      withIndex.add(m);
    }

    Map<Integer, Mention> mentions = document.predictedMentionsByID;
    List<Example> examples = new ArrayList<>();
    Set<Integer> mentionsToExtract = new HashSet<>();
    for (Map.Entry<Pair<Integer, Integer>, Boolean> pair : labeledPairs.entrySet()) {
        Mention m1 = mentions.get(pair.getKey().first);
        Mention m2 = mentions.get(pair.getKey().second);
        mentionsToExtract.add(m1.mentionID);
        mentionsToExtract.add(m2.mentionID);
        CompressedFeatureVector features =
            compressor.compress(getFeatures(document, m1, m2));
        examples.add(new Example(id, m1, m2, pair.getValue() ? 1.0 : 0.0, features));
    }

    Map<Integer, CompressedFeatureVector> mentionFeatures = new HashMap<>();
    for (int mentionID : mentionsToExtract) {
        mentionFeatures.put(mentionID, compressor.compress(getFeatures(document,
            document.predictedMentionsByID.get(mentionID), mentionsByHeadIndex)));
    }

    return new DocumentExamples(id, examples, mentionFeatures);
  }

  private Counter<String> getFeatures(Document doc, Mention m,
      Map<Integer, List<Mention>> mentionsByHeadIndex) {
    Counter<String> features = new ClassicCounter<>();

    // type features
    features.incrementCount("mention-type=" + m.mentionType);
    features.incrementCount("gender=" + m.gender);
    features.incrementCount("person-fine=" + m.person);
    features.incrementCount("head-ne-type=" + m.nerString);
    List<String> singletonFeatures = m.getSingletonFeatures(dictionaries);
    for (Map.Entry<Integer, String> e : SINGLETON_FEATURES.entrySet()) {
      if (e.getKey() < singletonFeatures.size()) {
        features.incrementCount(e.getValue() + "=" + singletonFeatures.get(e.getKey()));
      }
    }

    // length and location features
    addNumeric(features, "mention-length", m.spanToString().length());
    addNumeric(features, "mention-words", m.originalSpan.size());
    addNumeric(features, "sentence-words", m.sentenceWords.size());
    features.incrementCount("sentence-words=" + bin(m.sentenceWords.size()));
    features.incrementCount("mention-position", m.mentionNum
        / (double) doc.predictedMentions.size());
    features.incrementCount("sentence-position", m.sentNum / (double) doc.numSentences);

    // lexical features
    CoreLabel firstWord = firstWord(m);
    CoreLabel lastWord = lastWord(m);
    CoreLabel headWord = headWord(m);
    CoreLabel prevWord = prevWord(m);
    CoreLabel nextWord = nextWord(m);
    CoreLabel prevprevWord = prevprevWord(m);
    CoreLabel nextnextWord = nextnextWord(m);
    String headPOS = getPOS(headWord);
    String firstPOS = getPOS(firstWord);
    String lastPOS = getPOS(lastWord);
    String prevPOS = getPOS(prevWord);
    String nextPOS = getPOS(nextWord);
    String prevprevPOS = getPOS(prevprevWord);
    String nextnextPOS = getPOS(nextnextWord);
    features.incrementCount("first-word=" + wordIndicator(firstWord, firstPOS));
    features.incrementCount("last-word=" + wordIndicator(lastWord, lastPOS));
    features.incrementCount("head-word=" + wordIndicator(headWord, headPOS));
    features.incrementCount("next-word=" + wordIndicator(nextWord, nextPOS));
    features.incrementCount("prev-word=" + wordIndicator(prevWord, prevPOS));
    features.incrementCount("next-bigram="
        + wordIndicator(nextWord, nextnextWord, nextPOS + "_" + nextnextPOS));
    features.incrementCount("prev-bigram="
        + wordIndicator(prevprevWord, prevWord, prevprevPOS + "_" + prevPOS));
    features.incrementCount("next-pos=" + nextPOS);
    features.incrementCount("prev-pos=" + prevPOS);
    features.incrementCount("first-pos=" + firstPOS);
    features.incrementCount("last-pos=" + lastPOS);
    features.incrementCount("next-pos-bigram=" + nextPOS + "_" + nextnextPOS);
    features.incrementCount("prev-pos-bigram=" + prevprevPOS + "_" + prevPOS);
    addDependencyFeatures(features, "parent", getDependencyParent(m), true);
    addFeature(features, "ends-with-head", m.headIndex == m.endIndex - 1);
    addFeature(features, "is-generic", m.originalSpan.size() == 1 && firstPOS.equals("NNS"));

    // syntax features
    IndexedWord w = m.headIndexedWord;
    String depPath = "";
    int depth = 0;
    while (w != null) {
      SemanticGraphEdge e = getDependencyParent(m, w);
      depth++;
      if (depth <= 3 && e != null) {
        depPath += (depPath.isEmpty() ? "" : "_") + e.getRelation().toString();
        features.incrementCount("dep-path=" + depPath);
        w = e.getSource();
      } else {
         w = null;
      }
    }
    if (useConstituencyParse) {
      int fullEmbeddingLevel = headEmbeddingLevel(m.contextParseTree, m.headIndex);
      int mentionEmbeddingLevel = headEmbeddingLevel(m.mentionSubTree, m.headIndex - m.startIndex);
      if (fullEmbeddingLevel != -1 && mentionEmbeddingLevel != -1) {
        features.incrementCount("mention-embedding-level="
            + bin(fullEmbeddingLevel - mentionEmbeddingLevel));
        features.incrementCount("head-embedding-level="
            + bin(mentionEmbeddingLevel));
      } else {
        features.incrementCount("undetermined-embedding-level");
      }
      features.incrementCount("num-embedded-nps=" + bin(numEmbeddedNps(m.mentionSubTree)));
      String syntaxPath = "";
      Tree tree = m.contextParseTree;
      Tree head = tree.getLeaves().get(m.headIndex).ancestor(1, tree);
      depth = 0;
      for (Tree node : tree.pathNodeToNode(head, tree)) {
        syntaxPath += node.value() + "-";
        features.incrementCount("syntax-path=" + syntaxPath);
        depth++;
        if (depth >= 4 || node.value().equals("S")) {
          break;
        }
      }
    }

    // mention containment features
    addFeature(features, "contained-in-other-mention",
        mentionsByHeadIndex.get(m.headIndex).stream().anyMatch(m2 -> m != m2 && m.insideIn(m2)));
    addFeature(features, "contains-other-mention",
        mentionsByHeadIndex.get(m.headIndex).stream().anyMatch(m2 -> m != m2 && m2.insideIn(m)));

    // features from dcoref rules
    addFeature(features, "bare-plural", m.originalSpan.size() == 1 && headPOS.equals("NNS"));
    addFeature(features, "quantifier-start", dictionaries.quantifiers.contains(firstWord.word().toLowerCase()));
    addFeature(features, "negative-start", firstWord.word().toLowerCase().matches("none|no|nothing|not"));
    addFeature(features, "partitive", RuleBasedCorefMentionFinder.partitiveRule(m, m.sentenceWords, dictionaries));
    addFeature(features, "adjectival-demonym", dictionaries.isAdjectivalDemonym(m.spanToString()));
    if(doc.docType != DocType.ARTICLE && m.person == Person.YOU
        && nextWord != null && nextWord.word().equalsIgnoreCase("know")) {
           features.incrementCount("generic-you");
    }

    return features;
  }

  private Counter<String> getFeatures(Document doc, Mention m1, Mention m2) {
    assert(m1.appearEarlierThan(m2));
    Counter<String> features = new ClassicCounter<>();

    // global features
    features.incrementCount("bias");
    if (useDocSource) {
      features.incrementCount("doc-type=" + doc.docType);
      if(doc.docInfo != null && doc.docInfo.containsKey("DOC_ID")) {
        features.incrementCount("doc-source=" + doc.docInfo.get("DOC_ID").split("/")[1]);
      }
    }

    // singleton feature conjunctions
    List<String> singletonFeatures1 = m1.getSingletonFeatures(dictionaries);
    List<String> singletonFeatures2 = m2.getSingletonFeatures(dictionaries);
    for (Map.Entry<Integer, String> e : SINGLETON_FEATURES.entrySet()) {
      if (e.getKey() < singletonFeatures1.size() && e.getKey() < singletonFeatures2.size()) {
        features.incrementCount(e.getValue() + "=" + singletonFeatures1.get(e.getKey()) + "_" +
                singletonFeatures2.get(e.getKey()));
      }
    }
    SemanticGraphEdge p1 = getDependencyParent(m1);
    SemanticGraphEdge p2 = getDependencyParent(m2);
    features.incrementCount("dep-relations=" + (p1 == null ? "null" : p1.getRelation()) + "_"
        + (p2 == null ? "null" : p2.getRelation()));
    features.incrementCount("roles=" + getRole(m1) + "_" + getRole(m2));
    CoreLabel headCL1 = headWord(m1);
    CoreLabel headCL2 = headWord(m2);
    String headPOS1 = getPOS(headCL1);
    String headPOS2 = getPOS(headCL2);
    features.incrementCount("head-pos-s=" + headPOS1 + "_" + headPOS2);
    features.incrementCount("head-words=" + wordIndicator("h_" + headCL1.word().toLowerCase()
            + "_" + headCL2.word().toLowerCase(), headPOS1 + "_" + headPOS2));

    // agreement features
    addFeature(features, "animacies-agree", m2.animaciesAgree(m1));
    addFeature(features, "attributes-agree", m2.attributesAgree(m1, dictionaries));
    addFeature(features, "entity-types-agree", m2.entityTypesAgree(m1, dictionaries));
    addFeature(features, "numbers-agree", m2.numbersAgree(m1));
    addFeature(features, "genders-agree", m2.gendersAgree(m1));
    addFeature(features, "ner-strings-equal", m1.nerString.equals(m2.nerString));

    // string matching features
    addFeature(features, "antecedent-head-in-anaphor", headContainedIn(m1, m2));
    addFeature(features, "anaphor-head-in-antecedent", headContainedIn(m2, m1));
    if (m1.mentionType != MentionType.PRONOMINAL && m2.mentionType != MentionType.PRONOMINAL) {
      addFeature(features, "antecedent-in-anaphor",
          m2.spanToString().toLowerCase().contains(m1.spanToString().toLowerCase()));
      addFeature(features, "anaphor-in-antecedent",
          m1.spanToString().toLowerCase().contains(m2.spanToString().toLowerCase()));
      addFeature(features, "heads-equal", m1.headString.equalsIgnoreCase(m2.headString));
      addFeature(features, "heads-agree", m2.headsAgree(m1));
      addFeature(features, "exact-match", m1.toString().trim().toLowerCase().equals(
          m2.toString().trim().toLowerCase()));
      addFeature(features, "partial-match", relaxedStringMatch(m1, m2));

      double editDistance = StringUtils.editDistance(m1.spanToString(), m2.spanToString()) /
         (double) (m1.spanToString().length() + m2.spanToString().length());
      features.incrementCount("edit-distance", editDistance);
      features.incrementCount("edit-distance=" + ((int)(editDistance * 10) / 10.0));

      double headEditDistance = StringUtils.editDistance(m1.headString, m2.headString) /
          (double) (m1.headString.length() + m2.headString.length());
      features.incrementCount("head-edit-distance", headEditDistance);
      features.incrementCount("head-edit-distance=" + ((int)(headEditDistance * 10) / 10.0));
    }

    // distance features
    addNumeric(features, "mention-distance", m2.mentionNum -  m1.mentionNum);
    addNumeric(features, "sentence-distance", m2.sentNum -  m1.sentNum);
    if (m2.sentNum == m1.sentNum) {
        addNumeric(features, "word-distance", m2.startIndex -  m1.endIndex);
        if (m1.endIndex > m2.startIndex) {
            features.incrementCount("spans-intersect");
        }
    }

    // setup for dcoref features
    Set<Mention> ms1 = new HashSet<>();
    ms1.add(m1);
    Set<Mention> ms2 = new HashSet<>();
    ms2.add(m2);
    Random r = new Random();
    CorefCluster c1 = new CorefCluster(20000 + r.nextInt(10000), ms1);
    CorefCluster c2 = new CorefCluster(10000 + r.nextInt(10000), ms2);
    String s2 = m2.lowercaseNormalizedSpanString();
    String s1 = m1.lowercaseNormalizedSpanString();

    // discourse dcoref features
    addFeature(features, "mention-speaker-PER0",
        m2.headWord.get(SpeakerAnnotation.class).equalsIgnoreCase("PER0"));
    addFeature(features, "antecedent-is-anaphor-speaker", CorefRules.antecedentIsMentionSpeaker(doc, m2, m1, dictionaries));
    addFeature(features, "same-speaker", CorefRules.entitySameSpeaker(doc, m2, m1));
    addFeature(features, "person-disagree-same-speaker", CorefRules.entityPersonDisagree(doc, m2, m1, dictionaries)
        && CorefRules.entitySameSpeaker(doc, m2, m1));
    addFeature(features, "antecedent-matches-anaphor-speaker",
        CorefRules.antecedentMatchesMentionSpeakerAnnotation(m2, m1, doc));
    addFeature(features, "discourse-you-PER0", m2.person == Person.YOU
        && doc.docType == DocType.ARTICLE
        && m2.headWord.get(CoreAnnotations.SpeakerAnnotation.class).equals("PER0"));
    addFeature(features, "speaker-match-i-i", m2.number == Number.SINGULAR
        && dictionaries.firstPersonPronouns.contains(s1)
        && m1.number == Number.SINGULAR
        && dictionaries.firstPersonPronouns.contains(s2)
        && CorefRules.entitySameSpeaker(doc, m2, m1));
    addFeature(features, "speaker-match-speaker-i", m2.number == Number.SINGULAR
        && dictionaries.firstPersonPronouns.contains(s2)
        && CorefRules.antecedentIsMentionSpeaker(doc, m2, m1, dictionaries));
    addFeature(features, "speaker-match-i-speaker", m1.number == Number.SINGULAR
        && dictionaries.firstPersonPronouns.contains(s1)
        && CorefRules.antecedentIsMentionSpeaker(doc, m1, m2, dictionaries));
    addFeature(features, "speaker-match-you-you",
        dictionaries.secondPersonPronouns.contains(s1)
        && dictionaries.secondPersonPronouns.contains(s2)
        && CorefRules.entitySameSpeaker(doc, m2, m1));
    addFeature(features, "discourse-between-two-person", ((m2.person == Person.I
        && m1.person == Person.YOU
        || (m2.person == Person.YOU && m1.person == Person.I))
        && (m2.headWord.get(CoreAnnotations.UtteranceAnnotation.class)
                - m1.headWord.get(CoreAnnotations.UtteranceAnnotation.class) == 1)
        && doc.docType == DocType.CONVERSATION));
    addFeature(features, "incompatible-not-match", m1.person != Person.I
        && m2.person != Person.I
        && (CorefRules.antecedentIsMentionSpeaker(doc, m1, m2, dictionaries)
                || CorefRules.antecedentIsMentionSpeaker(doc, m2, m1, dictionaries)));
    int utteranceDist = Math.abs(m1.headWord.get(CoreAnnotations.UtteranceAnnotation.class) -
        m2.headWord.get(CoreAnnotations.UtteranceAnnotation.class));
    if(doc.docType != DocType.ARTICLE && utteranceDist == 1
          && !CorefRules.entitySameSpeaker(doc, m2, m1)) {
      addFeature(features, "speaker-mismatch-i-i", m1.person == Person.I
          && m2.person == Person.I);
      addFeature(features, "speaker-mismatch-you-you", m1.person == Person.YOU
          && m2.person == Person.YOU);
      addFeature(features, "speaker-mismatch-we-we", m1.person == Person.WE
          && m2.person == Person.WE);
    }

    // other dcoref features
    String firstWord1 = firstWord(m1).word().toLowerCase();
    addFeature(features, "indefinite-article-np", (m1.appositions == null &&
        m1.predicateNominatives == null &&
        (firstWord1.equals("a") || firstWord1.equals("an"))));
    addFeature(features, "far-this", m2.lowercaseNormalizedSpanString().equals("this") &&
        Math.abs(m2.sentNum - m1.sentNum) > 3);
    addFeature(features, "per0-you-in-article", m2.person == Person.YOU &&
        doc.docType == DocType.ARTICLE &&
        m2.headWord.get(CoreAnnotations.SpeakerAnnotation.class).equals("PER0"));
    addFeature(features, "inside-in", m2.insideIn(m1) || m1.insideIn(m2));
    addFeature(features, "indefinite-determiners",
        dictionaries.indefinitePronouns.contains(m1.originalSpan.get(0).lemma())
            || dictionaries.indefinitePronouns.contains(m2.originalSpan.get(0).lemma()));

    addFeature(features, "entity-attributes-agree", CorefRules.entityAttributesAgree(c2, c1));
    addFeature(features, "entity-token-distance", CorefRules.entityTokenDistance(m2, m1));
    addFeature(features, "i-within-i", CorefRules.entityIWithinI(m2, m1, dictionaries));
    addFeature(features, "exact-string-match", CorefRules.entityExactStringMatch(c2, c1,dictionaries, doc.roleSet));
    addFeature(features, "entity-relaxed-heads-agree",
        CorefRules.entityRelaxedHeadsAgreeBetweenMentions(c2, c1, m2, m1));
    addFeature(features, "is-acronym", CorefRules.entityIsAcronym(doc, c2, c1));
    addFeature(features, "demonym", m2.isDemonym(m1, dictionaries));
    addFeature(features, "incompatible-modifier", CorefRules.entityHaveIncompatibleModifier(m2, m1));
    addFeature(features, "head-lemma-match", m1.headWord.lemma().equals(m2.headWord.lemma()));
    addFeature(features, "words-included", CorefRules.entityWordsIncluded(c2, c1, m2, m1));
    addFeature(features, "extra-proper-noun", CorefRules.entityHaveExtraProperNoun(m2, m1, new HashSet<>()));
    addFeature(features, "number-in-later-mentions", CorefRules.entityNumberInLaterMention(m2, m1));
    addFeature(features, "sentence-context-incompatible",
        CorefRules.sentenceContextIncompatible(m2, m1, dictionaries));

    // syntax features
    if (useConstituencyParse) {
      if (m1.sentNum == m2.sentNum) {
        int clauseCount = 0;
        Tree tree = m2.contextParseTree;
        Tree current = m2.mentionSubTree;
        while (true) {
          current = current.ancestor(1, tree);
          if (current.label().value().startsWith("S")) {
            clauseCount++;
          }
          if (current.dominates(m1.mentionSubTree)) {
            break;
          }
          if (current.label().value().equals("ROOT") || current.ancestor(1, tree) == null) {
            break;
          }
        }
        features.incrementCount("clause-count", clauseCount);
        features.incrementCount("clause-count=" + bin(clauseCount));
      }
      if (RuleBasedCorefMentionFinder.isPleonastic(m2, m2.contextParseTree)
          || RuleBasedCorefMentionFinder.isPleonastic(m1, m1.contextParseTree)) {
        features.incrementCount("pleonastic-it");
      }
      if (maximalNp(m1.mentionSubTree) == maximalNp(m2.mentionSubTree)) {
        features.incrementCount("same-maximal-np");
      }
      boolean m1Embedded = headEmbeddingLevel(
        m1.mentionSubTree, m1.headIndex - m1.startIndex) > 1;
      boolean m2Embedded = headEmbeddingLevel(
        m2.mentionSubTree, m2.headIndex - m2.startIndex) > 1;
      features.incrementCount("embedding=" + m1Embedded + "_" + m2Embedded);
    }

    return features;
  }

  private static void addNumeric(Counter<String> features, String key, int value) {
    features.incrementCount(key + "=" + bin(value));
    features.incrementCount(key, value);
  }

  public static boolean relaxedStringMatch(Mention m1, Mention m2) {
    Set<String> propers = getPropers(m1);
    propers.retainAll(getPropers(m2));
    return !propers.isEmpty();
  }

  private static final Set<String> PROPERS = new HashSet<>();
  static {
    PROPERS.add("NN");
    PROPERS.add("NNS");
    PROPERS.add("NNP");
    PROPERS.add("NNPS");
  }
  private static Set<String> getPropers(Mention m) {
    Set<String> propers = new HashSet<>();
    for (int i = m.startIndex; i < m.endIndex; i++) {
      CoreLabel cl = m.sentenceWords.get(i);
      String POS = cl.get(CoreAnnotations.PartOfSpeechAnnotation.class);
      String word = cl.word().toLowerCase();
      if (PROPERS.contains(POS)) {
        propers.add(word);
      }
    }
    return propers;
  }

  private static void addFeature(Counter<String> features, String name, boolean value) {
    if (value) {
      features.incrementCount(name);
    }
  }

  private static String bin(int value) {
    return bin(value, BIN_EXACT, BIN_EXPONENT, Integer.MAX_VALUE);
  }

  private static String bin(int value, int binExact, double binExponent, int cap) {
    if (value < 0) {
      return "-" + bin(-value);
    }
    if (value > cap) {
      return cap + "+";
    }

    String bin = String.valueOf(value);
    if (value > binExact) {
      double start = Math.pow(binExponent, (int) (Math.log(value) / Math.log(binExponent)));
      bin = (int) start + "-" + (int) (start * binExponent);
    }
    return bin;
  }

  private static String getRole(Mention m) {
    if (m.isSubject) {
      return "subject";
    } else if (m.isDirectObject) {
      return "direct-object";
    } else if (m.isIndirectObject) {
      return "indirect-object";
    } else if (m.isPrepositionObject) {
      return "preposition-object";
    }
    return "unknown";
  }

  private static SemanticGraphEdge getDependencyParent(Mention m) {
    return getDependencyParent(m, m.headIndexedWord);
  }

  private static SemanticGraphEdge getDependencyParent(Mention m, IndexedWord w) {
    Iterator<SemanticGraphEdge> iterator = m.enhancedDependency.incomingEdgeIterator(w);
    return iterator.hasNext() ? iterator.next() : null;
  }

  private void addDependencyFeatures(Counter<String> features, String prefix,
      SemanticGraphEdge e, boolean addWord) {
    if (e == null) {
      features.incrementCount("no-" + prefix);
      return;
    }
    IndexedWord parent = e.getSource();
    String parentPOS = parent.tag();
    String parentWord = parent.word();
    String parentRelation = e.getRelation().toString();
    //String parentDir = e.getSource().beginPosition() < e.getTarget().beginPosition()
    //    ? "right" : "left";
    if (addWord) {
      features.incrementCount(prefix + "-word=" + wordIndicator(parentWord, parentPOS));
    }
    features.incrementCount(prefix + "-POS=" + parentPOS);
    features.incrementCount(prefix + "-relation=" + parentRelation);
    //features.incrementCount(prefix + "-direction=" + parentDir);
  }

  public Tree maximalNp(Tree mentionSubTree) {
    Tree maximalSubtree = mentionSubTree;
    for (Tree subtree : mentionSubTree.postOrderNodeList()) {
      if (!subtree.isLeaf() && !subtree.isPreTerminal()) {
        String label = ((CoreLabel) subtree.label())
            .get(CoreAnnotations.ValueAnnotation.class);
        if (label.equals("NP")) {
          maximalSubtree = subtree;
        }
      }
    }
    return maximalSubtree;
  }

  private int numEmbeddedNps(Tree mentionSubTree) {
    int embeddedNps = 0;
    for (Tree subtree : mentionSubTree.postOrderNodeList()) {
      if (!subtree.isLeaf() && !subtree.isPreTerminal()) {
        String label = ((CoreLabel) subtree.label())
            .get(CoreAnnotations.ValueAnnotation.class);
        if (label.equals("NP")) {
          embeddedNps++;
        }
      }
    }
    return embeddedNps;
  }

  private int headEmbeddingLevel(Tree tree, int headIndex) {
    int embeddingLevel = 0;
    try {
      Tree subtree = tree.getLeaves().get(headIndex);
      while (subtree != null) {
        String label = ((CoreLabel) subtree.label()).get(CoreAnnotations.ValueAnnotation.class);
        subtree = subtree.ancestor(1, tree);
        if (label.equals("NP")) {
          embeddingLevel++;
        }
      }
    } catch (Exception e) {
      return -1;
    }
    return embeddingLevel;
  }

  private static boolean headContainedIn(Mention m1, Mention m2) {
    String head = m1.headString;
    for (CoreLabel cl : m2.originalSpan) {
      if (head.equals(cl.word().toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  private String wordIndicator(CoreLabel cl1, CoreLabel cl2, String POS) {
    String w1 = cl1 == null ? "NONE" : cl1.word().toLowerCase();
    String w2 = cl2 == null ? "NONE" : cl2.word().toLowerCase();
    return wordIndicator(w1 + "_" + w2, POS);
  }

  private String wordIndicator(CoreLabel cl, String POS) {
    if (cl == null) {
      return "NONE";
    }
    return wordIndicator(cl.word().toLowerCase(), POS);
  }

  private String wordIndicator(String word, String POS) {
    if (word == null) {
      return "NONE";
    }
    return vocabulary.contains(word) ? word : POS;
  }

  private static String getPOS(CoreLabel cl) {
    return cl == null ? "NONE" : cl.get(CoreAnnotations.PartOfSpeechAnnotation.class);
  }

  private static CoreLabel firstWord(Mention m) {
    return m.originalSpan.get(0);
  }

  private static CoreLabel headWord(Mention m) {
    return m.headWord;
  }

  private static CoreLabel lastWord(Mention m) {
    return m.originalSpan.get(m.originalSpan.size() - 1);
  }

  private static CoreLabel nextnextWord(Mention m) {
    return m.endIndex + 1 < m.sentenceWords.size() ? m.sentenceWords.get(m.endIndex + 1) : null;
  }

  private static CoreLabel nextWord(Mention m) {
    return m.endIndex < m.sentenceWords.size() ? m.sentenceWords.get(m.endIndex) : null;
  }

  private static CoreLabel prevWord(Mention m) {
    return m.startIndex > 0 ? m.sentenceWords.get(m.startIndex - 1) : null;
  }

  private static CoreLabel prevprevWord(Mention m) {
    return m.startIndex > 1 ? m.sentenceWords.get(m.startIndex - 2) : null;
  }
}
