package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.WeightedDataset;
import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.*;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A Lexicon class that computes the score of word|tag according to a maxent model
 * of tag|word (divided by MLE estimate of P(tag)).
 * <br>
 * It would be nice to factor out a superclass MaxentLexicon that takes a WordFeatureExtractor
 *
 * @author Galen Andrew
 */
public class ChineseMaxentLexicon implements Lexicon  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseMaxentLexicon.class);

  private static final long serialVersionUID = 238834703409896852L;
  private static final boolean verbose = true;
  public static final boolean seenTagsOnly = false;
  private ChineseWordFeatureExtractor featExtractor;
  public static final boolean fixUnkFunctionWords = false;

  private static final Pattern wordPattern = Pattern.compile(".*-W");
  private static final Pattern charPattern = Pattern.compile(".*-.C");
  private static final Pattern bigramPattern = Pattern.compile(".*-.B");
  private static final Pattern conjPattern = Pattern.compile(".*&&.*");

  private final Pair<Pattern, Integer> wordThreshold = new Pair<>(wordPattern, 0);
  private final Pair<Pattern, Integer> charThreshold = new Pair<>(charPattern, 2);
  private final Pair<Pattern, Integer> bigramThreshold = new Pair<>(bigramPattern, 3);
  private final Pair<Pattern, Integer> conjThreshold = new Pair<>(conjPattern, 3);

  private final List<Pair<Pattern, Integer>> featureThresholds = new ArrayList<>();
  private final int universalThreshold = 0;

  private LinearClassifier scorer;
  private Map<String, String> functionWordTags = Generics.newHashMap();
  private Distribution<String> tagDist;
  private final Index<String> wordIndex;
  private final Index<String> tagIndex;
  private transient Counter<String> logProbs;
  private double iteratorCutoffFactor = 4;
  private transient int lastWord = -1;
  String initialWeightFile = null;
  boolean trainFloat = false;
  private static final String featureDir = "gbfeatures";

  private double tol = 1e-4;
  private double sigma = 0.4;

  static final boolean tuneSigma = false;
  static final int trainCountThreshold = 5;
  final int featureLevel;
  static final int DEFAULT_FEATURE_LEVEL = 2;
  private boolean trainOnLowCount = false;
  private boolean trainByType = false;
  private final TreebankLangParserParams tlpParams;
  private final TreebankLanguagePack ctlp;
  private final Options op;

  public boolean isKnown(int word) {
    return isKnown(wordIndex.get(word));
  }

  public boolean isKnown(String word) {
    return tagsForWord.containsKey(word);
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> tagSet(Function<String,String> basicCategoryFunction) {
    Set<String> tagSet = new HashSet<>();
    for (String tag : tagIndex.objectsList()) {
      tagSet.add(basicCategoryFunction.apply(tag));
    }
    return tagSet;
  }


  private void ensureProbs(int word) {
    ensureProbs(word, true);
  }

  private void ensureProbs(int word, boolean subtractTagScore) {
    if (word == lastWord) {
      return;
    }
    lastWord = word;
    if (functionWordTags.containsKey(wordIndex.get(word))) {
      logProbs = new ClassicCounter<>();
      String trueTag = functionWordTags.get(wordIndex.get(word));
      for (String tag : tagIndex.objectsList()) {
        if (ctlp.basicCategory(tag).equals(trueTag)) {
          logProbs.setCount(tag, 0);
        } else {
          logProbs.setCount(tag, Double.NEGATIVE_INFINITY);
        }
      }
      return;
    }
    Datum datum = new BasicDatum(featExtractor.makeFeatures(wordIndex.get(word)));
    logProbs = scorer.logProbabilityOf(datum);
    if (subtractTagScore) {
      Set<String> tagSet = logProbs.keySet();
      for (String tag : tagSet) {
        logProbs.incrementCount(tag, -Math.log(tagDist.probabilityOf(tag)));
      }
    }
  }

  public CollectionValuedMap<String, String> tagsForWord = new CollectionValuedMap<>();

  public Iterator<IntTaggedWord> ruleIteratorByWord(int word, int loc, String featureSpec) {
    ensureProbs(word);
    List<IntTaggedWord> rules = new ArrayList<>();
    if (seenTagsOnly) {
      String wordString = wordIndex.get(word);
      Collection<String> tags = tagsForWord.get(wordString);
      for (String tag : tags) {
        rules.add(new IntTaggedWord(wordString, tag, wordIndex, tagIndex));
      }
    } else {
      double max = Counters.max(logProbs);
      for (int tag = 0; tag < tagIndex.size(); tag++) {
        IntTaggedWord iTW = new IntTaggedWord(word, tag);
        double score = logProbs.getCount(tagIndex.get(tag));
        if (score > max - iteratorCutoffFactor) {
          rules.add(iTW);
        }
      }
    }
    return rules.iterator();
  }

  public Iterator<IntTaggedWord> ruleIteratorByWord(String word, int loc, String featureSpec) {
    return ruleIteratorByWord(wordIndex.indexOf(word), loc, featureSpec);
  }

  /** Returns the number of rules (tag rewrites as word) in the Lexicon.
   *  This method isn't yet implemented in this class.
   *  It currently just returns 0, which may or may not be helpful.
   */
  public int numRules() {
    int accumulated = 0;
    for (int w = 0, tot = wordIndex.size(); w < tot; w++) {
      Iterator<IntTaggedWord> iter = ruleIteratorByWord(w, 0, null);
      while (iter.hasNext()) {
        iter.next();
        accumulated++;
      }
    }
    return accumulated;
  }

  private String getTag(String word) {
    int iW = wordIndex.addToIndex(word);
    ensureProbs(iW, false);
    return Counters.argmax(logProbs);
  }


  private void verbose(String s) {
    if (verbose) {
      log.info(s);
    }
  }

  public ChineseMaxentLexicon(Options op, Index<String> wordIndex, Index<String> tagIndex, int featureLevel) {
    this.op = op;
    this.tlpParams = op.tlpParams;
    this.ctlp = op.tlpParams.treebankLanguagePack();;
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    this.featureLevel = featureLevel;
    if (fixUnkFunctionWords) {
      String filename = "unknown_function_word-simple.gb";
      try {
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "GB18030"));
        for (String line = in.readLine(); line != null; line = in.readLine()) {
          String[] parts = line.split("\\s+", 2);
          functionWordTags.put(parts[0], parts[1]);
        }
      } catch (IOException e) {
        throw new RuntimeException("Couldn't read function word file " + filename);
      }
    }
  }

  // only used at training time
  transient IntCounter<TaggedWord> datumCounter;

  @Override
  public void initializeTraining(double numTrees) {
    verbose("Training ChineseMaxentLexicon.");
    verbose("trainOnLowCount = " + trainOnLowCount + ", trainByType = " + trainByType + ", featureLevel = " + featureLevel + ", tuneSigma = " + tuneSigma);
    verbose("Making dataset...");

    if (featExtractor == null) {
      featExtractor = new ChineseWordFeatureExtractor(featureLevel);
    }

    this.datumCounter = new IntCounter<>();
  }

  /**
   * Add the given collection of trees to the statistics counted.  Can
   * be called multiple times with different trees.
   */
  public final void train(Collection<Tree> trees) {
    train(trees, 1.0);
  }

  /**
   * Add the given collection of trees to the statistics counted.  Can
   * be called multiple times with different trees.
   */
  @Override
  public void train(Collection<Tree> trees, double weight) {
    for (Tree tree : trees) {
      train(tree, weight);
    }
  }

  /**
   * Add the given tree to the statistics counted.  Can
   * be called multiple times with different trees.
   */
  @Override
  public void train(Tree tree, double weight) {
    train(tree.taggedYield(), weight);
  }

  /**
   * Add the given sentence to the statistics counted.  Can
   * be called multiple times with different sentences.
   */
  @Override
  public void train(List<TaggedWord> sentence, double weight) {
    featExtractor.train(sentence, weight);
    for (TaggedWord word : sentence) {
      datumCounter.incrementCount(word, weight);
      tagsForWord.add(word.word(), word.tag());
    }
  }

  @Override
  public void trainUnannotated(List<TaggedWord> sentence, double weight) {
    // TODO: for now we just punt on these
    throw new UnsupportedOperationException("This version of the parser does not support non-tree training data");
  }

  @Override
  public void incrementTreesRead(double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void train(TaggedWord tw, int loc, double weight) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void finishTraining() {
    IntCounter<String> tagCounter = new IntCounter<>();

    WeightedDataset data = new WeightedDataset(datumCounter.size());

    for (TaggedWord word : datumCounter.keySet()) {
      int count = datumCounter.getIntCount(word);
      if (trainOnLowCount && count > trainCountThreshold) {
        continue;
      }
      if (functionWordTags.containsKey(word.word())) {
        continue;
      }
      tagCounter.incrementCount(word.tag());
      if (trainByType) {
        count = 1;
      }
      data.add(new BasicDatum(featExtractor.makeFeatures(word.word()), word.tag()), count);
    }
    datumCounter = null;

    tagDist = Distribution.laplaceSmoothedDistribution(tagCounter, tagCounter.size(), 0.5);
    tagCounter = null;
    applyThresholds(data);

    verbose("Making classifier...");
    QNMinimizer minim = new QNMinimizer();//new ResultStoringMonitor(5, "weights"));
//    minim.shutUp();

    LinearClassifierFactory factory = new LinearClassifierFactory(minim);
    factory.setTol(tol);
    factory.setSigma(sigma);
    if (tuneSigma) {
      factory.setTuneSigmaHeldOut();
    }
    scorer = factory.trainClassifier(data);

    verbose("Done training.");
  }

  private void applyThresholds(WeightedDataset data) {
    if (wordThreshold.second > 0) {
      featureThresholds.add(wordThreshold);
    }
    if (featExtractor.chars && charThreshold.second > 0) {
      featureThresholds.add(charThreshold);
    }
    if (featExtractor.bigrams && bigramThreshold.second > 0) {
      featureThresholds.add(bigramThreshold);
    }
    if ((featExtractor.conjunctions || featExtractor.mildConjunctions) && conjThreshold.second > 0) {
      featureThresholds.add(conjThreshold);
    }

    int types = data.numFeatureTypes();
    if (universalThreshold > 0) {
      data.applyFeatureCountThreshold(universalThreshold);
    }
    if (featureThresholds.size() > 0) {
      data.applyFeatureCountThreshold(featureThresholds);
    }
    int numRemoved = types - data.numFeatureTypes();
    if (numRemoved > 0) {
      verbose("Thresholding removed " + numRemoved + " features.");
    }
  }

  public static void main(String[] args) {
    TreebankLangParserParams tlpParams = new ChineseTreebankParserParams();
    TreebankLanguagePack ctlp = tlpParams.treebankLanguagePack();
    Options op = new Options(tlpParams);
    TreeAnnotator ta = new TreeAnnotator(tlpParams.headFinder(), tlpParams, op);

    log.info("Reading Trees...");
    FileFilter trainFilter = new NumberRangesFileFilter(args[1], true);
    Treebank trainTreebank = tlpParams.memoryTreebank();
    trainTreebank.loadPath(args[0], trainFilter);

    log.info("Annotating trees...");
    Collection<Tree> trainTrees = new ArrayList<>();
    for (Tree tree : trainTreebank) {
      trainTrees.add(ta.transformTree(tree));
    }
    trainTreebank = null; // saves memory

    log.info("Training lexicon...");

    Index<String> wordIndex = new HashIndex<>();
    Index<String> tagIndex = new HashIndex<>();
    int featureLevel = DEFAULT_FEATURE_LEVEL;
    if (args.length > 3) {
      featureLevel = Integer.parseInt(args[3]);
    }
    ChineseMaxentLexicon lex = new ChineseMaxentLexicon(op, wordIndex, tagIndex, featureLevel);
    lex.initializeTraining(trainTrees.size());
    lex.train(trainTrees);
    lex.finishTraining();

    log.info("Testing");

    FileFilter testFilter = new NumberRangesFileFilter(args[2], true);
    Treebank testTreebank = tlpParams.memoryTreebank();
    testTreebank.loadPath(args[0], testFilter);
    List<TaggedWord> testWords = new ArrayList<>();
    for (Tree t : testTreebank) {
      for (TaggedWord tw : t.taggedYield()) {
        testWords.add(tw);
      }
      //testWords.addAll(t.taggedYield());
    }
    int[] totalAndCorrect = lex.testOnTreebank(testWords);

    log.info("done.");
    System.out.println(totalAndCorrect[1] + " correct out of " + totalAndCorrect[0] + " -- ACC: " + ((double) totalAndCorrect[1]) / totalAndCorrect[0]);
  }

  private int[] testOnTreebank(Collection<TaggedWord> testWords) {
    int[] totalAndCorrect = new int[2];
    totalAndCorrect[0] = 0;
    totalAndCorrect[1] = 0;
    for (TaggedWord word : testWords) {
      String goldTag = word.tag();
      String guessTag = ctlp.basicCategory(getTag(word.word()));
      totalAndCorrect[0]++;
      if (goldTag.equals(guessTag)) {
        totalAndCorrect[1]++;
      }
    }
    return totalAndCorrect;
  }

  public float score(IntTaggedWord iTW, int loc, String word, String featureSpec) {
    ensureProbs(iTW.word());
    double max = Counters.max(logProbs);
    double score = logProbs.getCount(iTW.tagString(tagIndex));
    if (score > max - iteratorCutoffFactor) {
      return (float) score;
    } else {
      return Float.NEGATIVE_INFINITY;
    }
  }


  public void writeData(Writer w) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void readData(BufferedReader in) throws IOException {
    throw new UnsupportedOperationException();
  }

  public UnknownWordModel getUnknownWordModel() {
    // TODO Auto-generated method stub
    return null;
  }

  public void setUnknownWordModel(UnknownWordModel uwm) {
    // TODO Auto-generated method stub

  }

  @Override
  public void train(Collection<Tree> trees, Collection<Tree> rawTrees) {
    train(trees);

  }




}
