package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.international.arabic.ArabicMorphoFeatureSpecification;
import edu.stanford.nlp.international.french.FrenchMorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification.MorphoFeatureType;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalIntCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 *
 * @author Spence Green
 *
 */
public class FactoredLexicon extends BaseLexicon  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FactoredLexicon.class);

  private static final long serialVersionUID = -744693222804176489L;

  private static final boolean DEBUG = false;

  private MorphoFeatureSpecification morphoSpec;

  private static final String NO_MORPH_ANALYSIS = "xXxNONExXx";

  private Index<String> morphIndex = new HashIndex<>();

  private TwoDimensionalIntCounter<Integer,Integer> wordTag = new TwoDimensionalIntCounter<>(40000);
  private Counter<Integer> wordTagUnseen = new ClassicCounter<>(500);

  private TwoDimensionalIntCounter<Integer,Integer> lemmaTag = new TwoDimensionalIntCounter<>(40000);
  private Counter<Integer> lemmaTagUnseen = new ClassicCounter<>(500);

  private TwoDimensionalIntCounter<Integer,Integer> morphTag = new TwoDimensionalIntCounter<>(500);
  private Counter<Integer> morphTagUnseen = new ClassicCounter<>(500);

  private Counter<Integer> tagCounter = new ClassicCounter<>(300);

  public FactoredLexicon(MorphoFeatureSpecification morphoSpec, Index<String> wordIndex, Index<String> tagIndex) {
    super(wordIndex, tagIndex);
    this.morphoSpec = morphoSpec;
  }

  public FactoredLexicon(Options op, MorphoFeatureSpecification morphoSpec, Index<String> wordIndex, Index<String> tagIndex) {
    super(op, wordIndex, tagIndex);
    this.morphoSpec = morphoSpec;
  }

  /**
   * Rule table is lemmas. So isKnown() is slightly trickier.
   */
  @Override
  public Iterator<IntTaggedWord> ruleIteratorByWord(int word, int loc, String featureSpec) {

    if (word == wordIndex.indexOf(BOUNDARY)) {
      // Deterministic tagging of the boundary symbol
      return rulesWithWord[word].iterator();

    } else if (isKnown(word)) {
      // Strict lexical tagging for seen *lemma* types
      // We need to copy the word form into the rules, which currently have lemmas in them
      return rulesWithWord[word].iterator();

    } else {
      if (DEBUG) log.info("UNKNOWN WORD");
      // Unknown word signatures
      Set<IntTaggedWord> lexRules = Generics.newHashSet(10);
      List<IntTaggedWord> uwRules = rulesWithWord[wordIndex.indexOf(UNKNOWN_WORD)];
      // Inject the word into these rules instead of the UW signature
      for (IntTaggedWord iTW : uwRules) {
        lexRules.add(new IntTaggedWord(word, iTW.tag));
      }
      return lexRules.iterator();
    }
  }

  @Override
  public float score(IntTaggedWord iTW, int loc, String word, String featureSpec) {
    final int wordId = iTW.word();
    final int tagId = iTW.tag();

    // Force 1-best path to go through the boundary symbol
    // (deterministic tagging)
    final int boundaryId = wordIndex.indexOf(BOUNDARY);
    final int boundaryTagId = tagIndex.indexOf(BOUNDARY_TAG);
    if (wordId == boundaryId && tagId == boundaryTagId) {
      return 0.0f;
    }

    // Morphological features
    String tag = tagIndex.get(iTW.tag());
    Pair<String,String> lemmaMorph = MorphoFeatureSpecification.splitMorphString(word, featureSpec);
    String lemma = lemmaMorph.first();
    int lemmaId = wordIndex.indexOf(lemma);
    String richMorphTag = lemmaMorph.second();
    String reducedMorphTag = morphoSpec.strToFeatures(richMorphTag).toString().trim();
    reducedMorphTag = reducedMorphTag.length() == 0 ? NO_MORPH_ANALYSIS : reducedMorphTag;
    int morphId = morphIndex.addToIndex(reducedMorphTag);

    // Score the factors and create the rule score p_W_T
    double p_W_Tf = Math.log(probWordTag(word, loc, wordId, tagId));
//    double p_L_T = Math.log(probLemmaTag(word, loc, tagId, lemmaId));
    double p_L_T = 0.0;
    double p_M_T = Math.log(probMorphTag(tagId, morphId));
    double p_W_T = p_W_Tf + p_L_T + p_M_T;

    if (DEBUG) {
//      String tag = tagIndex.get(tagId);
      System.err.printf("WSGDEBUG: %s --> %s %s %s ||  %.10f (%.5f / %.5f / %.5f)%n", tag, word, lemma,
          reducedMorphTag, p_W_T, p_W_Tf, p_L_T, p_M_T);
    }

    // Filter low probability taggings
    return p_W_T > -100.0 ? (float) p_W_T : Float.NEGATIVE_INFINITY;
  }

  private double probWordTag(String word, int loc, int wordId, int tagId) {
    double cW = wordTag.totalCount(wordId);
    double cWT = wordTag.getCount(wordId, tagId);

    // p_L
    double p_W = cW / wordTag.totalCount();

    // p_T
    double cTseen = tagCounter.getCount(tagId);
    double p_T = cTseen / tagCounter.totalCount();

    // p_T_L
    double p_W_T = 0.0;
    if (cW > 0.0) { // Seen lemma
      double p_T_W = 0.0;
      if (cW > 100.0 && cWT > 0.0) {
        p_T_W = cWT / cW;
      } else {
        double cTunseen = wordTagUnseen.getCount(tagId);
        // TODO p_T_U is 0?
        double p_T_U = cTunseen / wordTagUnseen.totalCount();
        p_T_W = (cWT + smooth[1]*p_T_U) / (cW + smooth[1]);
      }
      p_W_T = p_T_W * p_W / p_T;

    } else { // Unseen word. Score based on the word signature (of the surface form)
      IntTaggedWord iTW = new IntTaggedWord(wordId, tagId);
      double c_T = tagCounter.getCount(tagId);
      p_W_T = Math.exp(getUnknownWordModel().score(iTW, loc, c_T, tagCounter.totalCount(), smooth[0], word));
    }

    return p_W_T;
  }

  /**
   * This method should never return 0!!
   */
  private double probLemmaTag(String word, int loc, int tagId, int lemmaId) {
    double cL = lemmaTag.totalCount(lemmaId);
    double cLT = lemmaTag.getCount(lemmaId, tagId);

    // p_L
    double p_L = cL / lemmaTag.totalCount();

    // p_T
    double cTseen = tagCounter.getCount(tagId);
    double p_T = cTseen / tagCounter.totalCount();

    // p_T_L
    double p_L_T = 0.0;
    if (cL > 0.0) { // Seen lemma
      double p_T_L = 0.0;
      if (cL > 100.0 && cLT > 0.0) {
        p_T_L = cLT / cL;
      } else {
        double cTunseen = lemmaTagUnseen.getCount(tagId);
        // TODO(spenceg): p_T_U is 0??
        double p_T_U = cTunseen / lemmaTagUnseen.totalCount();
        p_T_L = (cLT + smooth[1]*p_T_U) / (cL + smooth[1]);
      }
      p_L_T = p_T_L * p_L / p_T;

    } else { // Unseen lemma. Score based on the word signature (of the surface form)
      // Hack
      double cTunseen = lemmaTagUnseen.getCount(tagId);
      p_L_T = cTunseen / tagCounter.totalCount();

      //      int wordId = wordIndex.indexOf(word);
//      IntTaggedWord iTW = new IntTaggedWord(wordId, tagId);
//      double c_T = tagCounter.getCount(tagId);
//      p_L_T = Math.exp(getUnknownWordModel().score(iTW, loc, c_T, tagCounter.totalCount(), smooth[0], word));
    }

    return p_L_T;
  }

  /**
   * This method should never return 0!
   */
  private double probMorphTag(int tagId, int morphId) {
    double cM = morphTag.totalCount(morphId);
    double cMT = morphTag.getCount(morphId, tagId);

    // p_M
    double p_M = cM / morphTag.totalCount();

    // p_T
    double cTseen = tagCounter.getCount(tagId);
    double p_T = cTseen / tagCounter.totalCount();

    double p_M_T = 0.0;
    if (cM > 100.0 && cMT > 0.0) {
      double p_T_M = cMT / cM;

//      else {
//        double cTunseen = morphTagUnseen.getCount(tagId);
//        double p_T_U = cTunseen / morphTagUnseen.totalCount();
//        p_T_M = (cMT + smooth[1]*p_T_U) / (cM + smooth[1]);
//      }
      p_M_T = p_T_M * p_M / p_T;

    } else { // Unseen morphological analysis
      // Hack....unseen morph tags are extremely rare
      // Add+1 smoothing
      p_M_T = 1.0 / (morphTag.totalCount() + tagIndex.size() + 1.0);
    }

    return p_M_T;
  }

  /**
   * This method should populate wordIndex, tagIndex, and morphIndex.
   */
  @Override
  public void train(Collection<Tree> trees, Collection<Tree> rawTrees) {
    double weight = 1.0;
    // Train uw model on words
    uwModelTrainer.train(trees, weight);

    final double numTrees = trees.size();
    Iterator<Tree> rawTreesItr = rawTrees == null ? null : rawTrees.iterator();
    Iterator<Tree> treeItr = trees.iterator();

    // Train factored lexicon on lemmas and morph tags
    int treeId = 0;
    while (treeItr.hasNext()) {
      Tree tree = treeItr.next();
      // CoreLabels, with morph analysis in the originalText annotation
      List<Label> yield = rawTrees == null ? tree.yield() : rawTreesItr.next().yield();
      // Annotated, binarized tree for the tags (labels are usually CategoryWordTag)
      List<Label> pretermYield = tree.preTerminalYield();

      int yieldLen = yield.size();
      for (int i = 0; i < yieldLen; ++i) {
        String word = yield.get(i).value();
        int wordId = wordIndex.addToIndex(word); // Don't do anything with words
        String tag = pretermYield.get(i).value();
        int tagId = tagIndex.addToIndex(tag);

        // Use the word as backup if there is no lemma
        String featureStr = ((CoreLabel) yield.get(i)).originalText();
        Pair<String,String> lemmaMorph = MorphoFeatureSpecification.splitMorphString(word, featureStr);
        String lemma = lemmaMorph.first();
        int lemmaId = wordIndex.addToIndex(lemma);
        String richMorphTag = lemmaMorph.second();
        String reducedMorphTag = morphoSpec.strToFeatures(richMorphTag).toString().trim();
        reducedMorphTag = reducedMorphTag.isEmpty() ? NO_MORPH_ANALYSIS : reducedMorphTag;
        int morphId = morphIndex.addToIndex(reducedMorphTag);

        // Seen event counts
        wordTag.incrementCount(wordId, tagId);
        lemmaTag.incrementCount(lemmaId, tagId);
        morphTag.incrementCount(morphId, tagId);
        tagCounter.incrementCount(tagId);

        // Unseen event counts
        if (treeId > op.trainOptions.fractionBeforeUnseenCounting*numTrees) {
          if (! wordTag.firstKeySet().contains(wordId) || wordTag.getCounter(wordId).totalCount() < 2) {
            wordTagUnseen.incrementCount(tagId);
          }
          if (! lemmaTag.firstKeySet().contains(lemmaId) || lemmaTag.getCounter(lemmaId).totalCount() < 2) {
            lemmaTagUnseen.incrementCount(tagId);
          }
          if (! morphTag.firstKeySet().contains(morphId) || morphTag.getCounter(morphId).totalCount() < 2) {
            morphTagUnseen.incrementCount(tagId);
          }
        }
      }
      ++treeId;

      if (DEBUG && (treeId % 100) == 0) {
        System.err.printf("[%d]",treeId);
      }
      if (DEBUG && (treeId % 10000) == 0) {
        log.info();
      }
    }
  }

  /**
   * Rule table is lemmas!
   */
  @Override
  protected void initRulesWithWord() {
    // Add synthetic symbols to the indices
    int unkWord = wordIndex.addToIndex(UNKNOWN_WORD);
    int boundaryWordId = wordIndex.addToIndex(BOUNDARY);
    int boundaryTagId = tagIndex.addToIndex(BOUNDARY_TAG);

    // Initialize rules table
    final int numWords = wordIndex.size();
    rulesWithWord = new List[numWords];
    for (int w = 0; w < numWords; w++) {
      rulesWithWord[w] = new ArrayList<>(1);
    }

    // Collect rules, indexed by word
    Set<IntTaggedWord> lexRules = Generics.newHashSet(40000);
    for (int wordId : wordTag.firstKeySet()) {
      for (int tagId : wordTag.getCounter(wordId).keySet()) {
        lexRules.add(new IntTaggedWord(wordId, tagId));
        lexRules.add(new IntTaggedWord(nullWord, tagId));
      }
    }

    // Known words and signatures
    for (IntTaggedWord iTW : lexRules) {
      if (iTW.word() == nullWord) {
        // Mix in UW signature rules for open class types
        double types = uwModel.unSeenCounter().getCount(iTW);
        if (types > trainOptions.openClassTypesThreshold) {
          IntTaggedWord iTU = new IntTaggedWord(unkWord, iTW.tag);
          if (!rulesWithWord[unkWord].contains(iTU)) {
            rulesWithWord[unkWord].add(iTU);
          }
        }
      } else {
        // Known word
        rulesWithWord[iTW.word].add(iTW);
      }
    }

    log.info("The " + rulesWithWord[unkWord].size() + " open class tags are: [");
    for (IntTaggedWord item : rulesWithWord[unkWord]) {
      log.info(" " + tagIndex.get(item.tag()));
    }
    log.info(" ] ");

    // Boundary symbol has one tagging
    rulesWithWord[boundaryWordId].add(new IntTaggedWord(boundaryWordId, boundaryTagId));
  }

  /**
   * Convert a treebank to factored lexicon events for fast iteration in the
   * optimizer.
   */
  private static List<FactoredLexiconEvent> treebankToLexiconEvents(List<Tree> treebank,
      FactoredLexicon lexicon) {
    List<FactoredLexiconEvent> events = new ArrayList<>(70000);
    for (Tree tree : treebank) {
      List<Label> yield = tree.yield();
      List<Label> preterm = tree.preTerminalYield();
      assert yield.size() == preterm.size();
      int yieldLen = yield.size();
      for (int i = 0; i < yieldLen; ++i) {
        String tag = preterm.get(i).value();
        int tagId = lexicon.tagIndex.indexOf(tag);
        String word = yield.get(i).value();
        int wordId = lexicon.wordIndex.indexOf(word);
        // Two checks to see if we keep this example
        if (tagId < 0) {
          log.info("Discarding training example: " + word + " " + tag);
          continue;
        }
//        if (counts.probWordTag(wordId, tagId) == 0.0) {
//          log.info("Discarding low counts <w,t> pair: " + word + " " + tag);
//          continue;
//        }

        String featureStr = ((CoreLabel) yield.get(i)).originalText();
        Pair<String,String> lemmaMorph = MorphoFeatureSpecification.splitMorphString(word, featureStr);
        String lemma = lemmaMorph.first();
        String richTag = lemmaMorph.second();
        String reducedTag = lexicon.morphoSpec.strToFeatures(richTag).toString();
        reducedTag = reducedTag.length() == 0 ? NO_MORPH_ANALYSIS : reducedTag;

        int lemmaId = lexicon.wordIndex.indexOf(lemma);
        int morphId = lexicon.morphIndex.indexOf(reducedTag);
        FactoredLexiconEvent event = new FactoredLexiconEvent(wordId, tagId, lemmaId, morphId, i, word, featureStr);
        events.add(event);
      }
    }
    return events;
  }

  private static List<FactoredLexiconEvent> getTuningSet(Treebank devTreebank,
      FactoredLexicon lexicon, TreebankLangParserParams tlpp) {
    List<Tree> devTrees = new ArrayList<>(3000);
    for (Tree tree : devTreebank) {
      for (Tree subTree : tree) {
        if (!subTree.isLeaf()) {
          tlpp.transformTree(subTree, tree);
        }
      }
      devTrees.add(tree);
    }
    List<FactoredLexiconEvent> tuningSet = treebankToLexiconEvents(devTrees, lexicon);
    return tuningSet;
  }


  private static Options getOptions(Language language) {
    Options options = new Options();
    if (language.equals(Language.Arabic)) {
      options.lexOptions.useUnknownWordSignatures = 9;
      options.lexOptions.unknownPrefixSize = 1;
      options.lexOptions.unknownSuffixSize = 1;
      options.lexOptions.uwModelTrainer = "edu.stanford.nlp.parser.lexparser.ArabicUnknownWordModelTrainer";
    } else if (language.equals(Language.French)) {
      options.lexOptions.useUnknownWordSignatures = 1;
      options.lexOptions.unknownPrefixSize = 1;
      options.lexOptions.unknownSuffixSize = 2;
      options.lexOptions.uwModelTrainer = "edu.stanford.nlp.parser.lexparser.FrenchUnknownWordModelTrainer";
    } else {
      throw new UnsupportedOperationException();
    }
    return options;
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 4) {
      System.err.printf("Usage: java %s language features train_file dev_file%n", FactoredLexicon.class.getName());
      System.exit(-1);
    }
    // Command line options
    Language language = Language.valueOf(args[0]);
    TreebankLangParserParams tlpp = language.params;
    Treebank trainTreebank = tlpp.diskTreebank();
    trainTreebank.loadPath(args[2]);
    Treebank devTreebank = tlpp.diskTreebank();
    devTreebank.loadPath(args[3]);
    MorphoFeatureSpecification morphoSpec;
    Options options = getOptions(language);
    if (language.equals(Language.Arabic)) {
      morphoSpec = new ArabicMorphoFeatureSpecification();
      String[] languageOptions = {"-arabicFactored"};
      tlpp.setOptionFlag(languageOptions, 0);
    } else if (language.equals(Language.French)) {
      morphoSpec = new FrenchMorphoFeatureSpecification();
      String[] languageOptions = {"-frenchFactored"};
      tlpp.setOptionFlag(languageOptions, 0);
    } else {
      throw new UnsupportedOperationException();
    }
    String featureList = args[1];
    String[] features = featureList.trim().split(",");
    for (String feature : features) {
      morphoSpec.activate(MorphoFeatureType.valueOf(feature));
    }
    System.out.println("Language: " + language.toString());
    System.out.println("Features: " + args[1]);

    // Create word and tag indices
    // Save trees in a collection since the interface requires that....
    System.out.print("Loading training trees...");
    List<Tree> trainTrees = new ArrayList<>(19000);
    Index<String> wordIndex = new HashIndex<>();
    Index<String> tagIndex = new HashIndex<>();
    for (Tree tree : trainTreebank) {
      for (Tree subTree : tree) {
        if (!subTree.isLeaf()) {
          tlpp.transformTree(subTree, tree);
        }
      }
      trainTrees.add(tree);
    }
    System.out.printf("Done! (%d trees)%n", trainTrees.size());

    // Setup and train the lexicon.
    System.out.print("Collecting sufficient statistics for lexicon...");
    FactoredLexicon lexicon = new FactoredLexicon(options, morphoSpec, wordIndex, tagIndex);
    lexicon.initializeTraining(trainTrees.size());
    lexicon.train(trainTrees, null);
    lexicon.finishTraining();
    System.out.println("Done!");
    trainTrees = null;

    // Load the tuning set
    System.out.print("Loading tuning set...");
    List<FactoredLexiconEvent> tuningSet = getTuningSet(devTreebank, lexicon, tlpp);
    System.out.printf("...Done! (%d events)%n", tuningSet.size());

    // Print the probabilities that we obtain
    // TODO(spenceg): Implement tagging accuracy with FactLex
    int nCorrect = 0;
    Counter<String> errors = new ClassicCounter<>();
    for (FactoredLexiconEvent event : tuningSet) {
      Iterator<IntTaggedWord> itr = lexicon.ruleIteratorByWord(event.word(), event.getLoc(), event.featureStr());
      Counter<Integer> logScores = new ClassicCounter<>();
      boolean noRules = true;
      int goldTagId = -1;
      while (itr.hasNext()) {
        noRules = false;
        IntTaggedWord iTW = itr.next();
        if (iTW.tag() == event.tagId()) {
          log.info("GOLD-");
          goldTagId = iTW.tag();
        }
        float tagScore = lexicon.score(iTW, event.getLoc(), event.word(), event.featureStr());
        logScores.incrementCount(iTW.tag(), tagScore);
      }
      if (noRules) {
        System.err.printf("NO TAGGINGS: %s %s%n", event.word(), event.featureStr());
      } else {
        // Score the tagging
        int hypTagId = Counters.argmax(logScores);
        if (hypTagId == goldTagId) {
          ++nCorrect;
        } else {
          String goldTag = goldTagId < 0 ? "UNSEEN" : lexicon.tagIndex.get(goldTagId);
          errors.incrementCount(goldTag);
        }
      }
      log.info();
    }

    // Output accuracy
    double acc = (double) nCorrect / (double) tuningSet.size();
    System.err.printf("%n%nACCURACY: %.2f%n%n", acc*100.0);
    log.info("% of errors by type:");
    List<String> biggestKeys = new ArrayList<>(errors.keySet());
    Collections.sort(biggestKeys, Counters.toComparator(errors, false, true));
    Counters.normalize(errors);
    for (String key : biggestKeys) {
      System.err.printf("%s\t%.2f%n", key, errors.getCount(key)*100.0);
    }
  }

}
