package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.stats.Counters;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;


/**
 * @author Galen Andrew
 */
public class MaxentDependencyGrammar extends AbstractDependencyGrammar {
  /**
   *
   */
  private static final long serialVersionUID = -8751155308803440889L;
  private ClassicCounter<IntDependency> stopCounter;
  private ClassicCounter<IntDependency> depCounter;
  private ClassicCounter<IntDependency> nonDepCounter;
  private ClassicCounter<Pair<IntTaggedWord, IntTaggedWord>> wordPairCounter;
  private ClassicCounter<IntTaggedWord> wordCounter;

  private WordFeatureExtractor largeWordFeatExtractor;
  private WordFeatureExtractor smallWordFeatExtractor;
  private Index<String> largeWordFeatIndex = new HashIndex<String>();
  private Index<String> smallWordFeatIndex = new HashIndex<String>();

  private LogisticClassifier<String, IntFeature> depClassifier;
  private double stopSmooth;
  private double depSmooth;
  private double wordSmooth;

  private static final String posLabel = "DEP";
  private static final String negLabel = "NOT-DEP";
  private static final int numNegExamplesPerWord = 1;
  private int jointThresh = 2;
  private int wordFeatThresh = 2;

  private transient Interner<Pair<IntTaggedWord, IntTaggedWord>> pairInterner = new Interner<Pair<IntTaggedWord, IntTaggedWord>>();

  public MaxentDependencyGrammar(TreebankLangParserParams tlpParams, WordFeatureExtractor largeWordFeatExtractor, WordFeatureExtractor smallWordFeatExtractor, boolean directional, boolean useDistance, boolean useCoarseDistance, Options op, Index<String> wordIndex, Index<String> tagIndex) {
    super(tlpParams.treebankLanguagePack(), /*new BasicCategoryTagProjection(tlpParams.treebankLanguagePack())*/null, directional, useDistance, useCoarseDistance, op, wordIndex, tagIndex);
    this.smallWordFeatExtractor = smallWordFeatExtractor;
    this.largeWordFeatExtractor = largeWordFeatExtractor;
    stopSmooth = 7.0;
    depSmooth = 1.0;
    wordSmooth = 1.5;
  }

  static final boolean makeDepDump = false;
  static final PrintWriter depdump;

  static {
    if (makeDepDump) {
      try {
        depdump = new PrintWriter(new OutputStreamWriter(new FileOutputStream("depdump.chi"), "UTF-8"), true);
      } catch (Exception e) {
        System.err.println("Couldn't open file: depdump.chi");
      }
    } else {
      depdump = null;
    }
  }

  private static void depDumpPrintln(String s) {
    if (makeDepDump) {
      depdump.println(s);
    }
  }

  private static void depDumpPrintf(String s, Object... args) {
    if (makeDepDump) {
      depdump.print(s + Arrays.asList(args));
    }
  }

  public double scoreTB(IntDependency dep) {
    depDumpPrintln("Scoring dependency: " + dep);

    double pb_stop = getStopProb(dep);

    if (dep.arg.word == -2) {
      // did we generate stop?
      if (rootTW(dep.head)) {
        return 0.0;
      }
      depDumpPrintf("Stop score: %5g\n", Math.log(pb_stop));
      return op.testOptions.depWeight * Math.log(pb_stop);
    }

    double pb_go = rootTW(dep.head) ? 1.0 : 1.0 - pb_stop;
    depDumpPrintf("Go score: %5g\n", Math.log(pb_go));

    double smoothProb = getDepProb(dep);

    double score = op.testOptions.depWeight * Math.log(smoothProb * pb_go);
    depDumpPrintf("Smooth score: %5g\n", score);
    return score;
  }

  private double getDepProb(IntDependency dep) {
    IntDependency tempDep = new IntDependency(dep.head, dep.arg, dep.leftHeaded, valenceBin(dep.distance));
    double c_dep = depCounter.getCount(tempDep);
    double c_words = nonDepCounter.getCount(tempDep) + c_dep;

    depDumpPrintln("c_dep / c_words: " + (int) c_dep + "/" + (int) c_words);

    double maxentProb = getMaxentDepProb(dep);

    return (c_dep + depSmooth * maxentProb) / (c_words + depSmooth);
  }

  private double getMaxentDepProb(IntDependency dep) {
    Collection<IntFeature> features = makeFeatures(dep.head.word, dep.head.tag, dep.arg.word, dep.arg.tag, dep.leftHeaded, distanceBin(dep.distance));

    double maxentProb = depClassifier.probabilityOf(features, posLabel);
    depDumpPrintf("Maxent score: %5g\n", Math.log(maxentProb));

    Pair<IntTaggedWord, IntTaggedWord> p = new Pair<IntTaggedWord, IntTaggedWord>(dep.head, dep.arg);
    double pairCount = wordPairCounter.getCount(p);
    p.first = dep.arg;
    p.second = dep.head;
    pairCount += wordPairCounter.getCount(p);

    double headCount = wordCounter.getCount(dep.head);

    depDumpPrintln("pairCount / headCount: " + (int) pairCount + "/" + (int) headCount);

    double wordProb = (pairCount + wordSmooth) / (headCount + wordSmooth * wordCounter.size());
    depDumpPrintf("MLE arg|head score: %5g\n", Math.log(wordProb));

    return maxentProb * wordProb;
  }

  private double getStopProb(IntDependency dep) {
    short distBin = distanceBin(dep.distance);

    IntTaggedWord unknownHead = new IntTaggedWord(-1, dep.head.tag);
    IntDependency temp = new IntDependency(dep.head, stopTW, dep.leftHeaded, distBin);
    double c_stop_hTWds = stopCounter.getCount(temp);
    temp = new IntDependency(unknownHead, stopTW, dep.leftHeaded, distBin);
    double c_stop_hTds = stopCounter.getCount(temp);
    temp = new IntDependency(unknownHead, wildTW, dep.leftHeaded, distBin);
    double c_continue_hTds = stopCounter.getCount(temp);
    temp = new IntDependency(dep.head, wildTW, dep.leftHeaded, distBin);
    double c_continue_hTWds = stopCounter.getCount(temp);

    double c_hTds = c_stop_hTds + c_continue_hTds;
    double c_hTWds = c_stop_hTWds + c_continue_hTWds;

    depDumpPrintln("c_stop_hT / c_hT: " + (int) c_stop_hTds + "/" + (int) c_hTds);
    depDumpPrintln("c_stop_hTW / c_hTW: " + (int) c_stop_hTWds + "/" + (int) c_hTWds);

    double p_stop_hTds = (c_hTds > 0.0 ? c_stop_hTds / c_hTds : 1.0);
    return (c_stop_hTWds + stopSmooth * p_stop_hTds) / (c_hTWds + stopSmooth);
  }

  @Override
  public void tune(Collection<Tree> trees) {
    tabulateTrees(trees);
    negExamples = collapseNegExamples(false);

    double bestS = 0;
    double bestScore = Double.NEGATIVE_INFINITY;
    System.err.println("Tuning stopSmooth");
    for (stopSmooth = 0.1; stopSmooth < 10; stopSmooth *= 1.25) {
      double total = 0.0;
      for (IntDependency dep : stopExamples.keySet()) {
        double stopProb = getStopProb(dep);
        if (stopProb > 0.0) {
          total += Math.log(stopProb) * stopExamples.getCount(dep);
        }
      }
      for (IntDependency dep : posExamples.keySet()) {
        if (!rootTW(dep.head)) {
          double continueProb = 1.0 - getStopProb(dep);
          if (continueProb > 0.0) {
            total += Math.log(continueProb) * posExamples.getCount(dep);
          }
        }
      }
      if (total > bestScore) {
        bestS = stopSmooth;
        bestScore = total;
      }
    }
    stopSmooth = bestS;
    System.err.println("Tuning selected stopSmooth: " + stopSmooth);

    System.err.println("Tuning other params...");
    bestScore = Double.NEGATIVE_INFINITY;
    double bestW = 0;
    double bestD = 0;
    for (depSmooth = 0.1; depSmooth < 100; depSmooth *= 1.25) {
      for (wordSmooth = 0.1; wordSmooth < 100; wordSmooth *= 1.25) {
        double total = 0.0;
        for (IntDependency dep : posExamples.keySet()) {
          double depProb = getDepProb(dep);
          if (depProb > 0.0) {
            total += Math.log(depProb) * posExamples.getCount(dep);
          }
        }
        for (IntDependency dep : negExamples.keySet()) {
          double depProb = 1.0 - getDepProb(dep);
          if (depProb > 0.0) {
            total += Math.log(depProb) * negExamples.getCount(dep);
          }
        }
        if (total > bestScore) {
          bestD = depSmooth;
          bestW = wordSmooth;
          bestScore = total;
        }
      }
    }
    depSmooth = bestD;
    wordSmooth = bestW;

    System.out.println("\nTuning selected depSmooth: " + depSmooth + " wordSmooth: " + wordSmooth);
  }

  private transient short[][] smallWordFeatCache;

  private short[] getSmallWordFeatures(int word) {
    if (smallWordFeatCache == null) {
      smallWordFeatCache = new short[wordIndex.size()][];
    }
    if (word < smallWordFeatCache.length && smallWordFeatCache[word] != null) {
      return smallWordFeatCache[word];
    }
    short[] wordFeatures;
    Collection<String> stringFeats = smallWordFeatExtractor.makeFeatures(wordIndex.get(word));
    wordFeatures = new short[stringFeats.size()];
    int i = 0;
    for (String feat : stringFeats) {
      smallWordFeatIndex.add(feat);
      wordFeatures[i++] = (short)smallWordFeatIndex.indexOf(feat);
    }
    if (word < smallWordFeatCache.length) {
      smallWordFeatCache[word] = wordFeatures;
    }
    return wordFeatures;
  }

  private transient int[][] largeWordFeatCache;

  private int[] getLargeWordFeatures(int word) {
    if (largeWordFeatCache == null) {
      largeWordFeatCache = new int[wordIndex.size()][];
    }
    if (word < largeWordFeatCache.length && largeWordFeatCache[word] != null) {
      return largeWordFeatCache[word];
    }
    int[] wordFeatures;
    Collection<String> stringFeats = largeWordFeatExtractor.makeFeatures(wordIndex.get(word));
    wordFeatures = new int[stringFeats.size()];
    int i = 0;
    for (String feat : stringFeats) {
      largeWordFeatIndex.add(feat);
      wordFeatures[i++] = largeWordFeatIndex.indexOf(feat);
    }
    if (word < largeWordFeatCache.length) {
      largeWordFeatCache[word] = wordFeatures;
    }
    return wordFeatures;
  }

  private static int getIntFeat(int hTag, int aTag, short dir, int type) {
    return (hTag + 1) << 21 | (aTag + 1) << 10 | dir << 4 | type;
  }

  private static int setType(int feat, int type) {
    return (feat & ~16) | type;
  }

  private static String toString(int bits) {
    StringBuffer sb = new StringBuffer();
    sb.append(intFromBits(bits, 21, 31) - 1); // hTag
    sb.append("|" + (intFromBits(bits, 10, 20) - 1)); // aTag
    sb.append("|" + (intFromBits(bits, 4, 9))); // dir
    sb.append("|" + intFromBits(bits, 0, 3)); // type

    return sb.toString();
  }

  private static int intFromBits(int a, int from, int to) {
    int mask1 = -1 << from;
    int mask2 = ~(-1 << to);
    return (a & mask1 & mask2) >>> from;
  }

  private static int intFromTwoShorts(short a, short b) {
    return (a << 16) & b;
  }

  // head tag + arg tag + dir + val
  // head tag + arg tag + dir + dist

  // head features + arg tag + dir + val
  // head features + arg tag + dir + dist
  // head features + head tag + arg tag + dir + val
  // head features + head tag + arg tag + dir + dist

  // arg features + head tag + dir + val
  // arg features + head tag + dir + dist
  // arg features + head tag + arg tag + dir + val
  // arg features + head tag + arg tag + dir + dist

  // only small head features from here on
  // head features + arg features + dir + val
  // head features + arg features + head tag + dir + val
  // head features + arg features + arg tag + dir + val
  // head features + arg features + head tag + arg tag + dir + val

  private transient Collection<IntFeature> tempFeatures = new ArrayList<IntFeature>();

  private static class IntFeature {
    int wordFeat;
    int tagAndDepFeats;

    IntFeature(int tagAndDepFeats) {
      this(0, tagAndDepFeats);
    }

    IntFeature(int wordFeat, int tagAndDepFeats) {
      this.wordFeat = wordFeat;
      this.tagAndDepFeats = tagAndDepFeats;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof IntFeature)) {
        return false;
      }

      final IntFeature intFeature = (IntFeature) o;

      if (tagAndDepFeats != intFeature.tagAndDepFeats) {
        return false;
      }
      if (wordFeat != intFeature.wordFeat) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = wordFeat;
      result = 29 * result + tagAndDepFeats;
      return result;
    }
  }

  private Collection<IntFeature> makeFeatures(int hWord, int hTag, int aWord, int aTag, boolean leftHeaded, int distBin) {
    short signedDist = (short) ((leftHeaded) ? distBin : (numDistBins() + distBin));
    short signedValence = (short) ((leftHeaded ? 0 : 2) + valenceBin(distBin));

    tempFeatures.clear();

    // I assume there are no more than 2000 tags -- 11 bits
    // 11 bits of hTag, 11 bits of aTag, 6 bits of (signed)signedDist, 4 bits of type

    int type = 0;

    int hTaTD = getIntFeat(hTag, aTag, signedValence, type++);
    int hTaTDs = getIntFeat(hTag, aTag, signedDist, type++);

    tempFeatures.add(new IntFeature(hTaTD));
    if (useDistance) tempFeatures.add(new IntFeature(hTaTDs));

    //    System.err.println(toString(hTaTD));
    //    System.err.println(toString(hTaTDs));

    int aTD = getIntFeat(-1, aTag, signedValence, type++);
    int aTDs = getIntFeat(-1, aTag, signedDist, type++);
    hTaTD = setType(hTaTD, type++);
    hTaTDs = setType(hTaTDs, type++);
    // these are the larger type of feats, including word id
    int[] largeHeadFeats = getLargeWordFeatures(hWord);

    for (int hF : largeHeadFeats) {
      tempFeatures.add(new IntFeature(hF, aTD));
      tempFeatures.add(new IntFeature(hF, hTaTD));
      if (useDistance) tempFeatures.add(new IntFeature(hF, aTDs));
      if (useDistance) tempFeatures.add(new IntFeature(hF, hTaTDs));
    }

    int hTD = getIntFeat(-1, hTag, signedValence, type++);
    int hTDs = getIntFeat(-1, hTag, signedDist, type++);
    hTaTD = setType(hTaTD, type++);
    hTaTDs = setType(hTaTDs, type++);
    // these are the larger type of feats, including word id
    int[] largeArgFeats = getLargeWordFeatures(aWord);

    for (int aF : largeArgFeats) {
      tempFeatures.add(new IntFeature(aF, hTD));
      tempFeatures.add(new IntFeature(aF, hTaTD));
      if (useDistance) tempFeatures.add(new IntFeature(aF, hTDs));
      if (useDistance) tempFeatures.add(new IntFeature(aF, hTaTDs));
    }

    // these are the more restricted feats
    short[] smallHeadFeats = getSmallWordFeatures(hWord);
    short[] smallArgFeats = getSmallWordFeatures(aWord);

    int D = getIntFeat(-1, -1, signedValence, type++);
    hTD = setType(hTD, type++);
    aTD = setType(aTD, type++);
    hTaTD = setType(hTaTD, type++);
    for (short hF : smallHeadFeats) {
      for (short aF : smallArgFeats) {
        int F = intFromTwoShorts(hF, aF);
        tempFeatures.add(new IntFeature(F, D));
        tempFeatures.add(new IntFeature(F, hTD));
        tempFeatures.add(new IntFeature(F, aTD));
        tempFeatures.add(new IntFeature(F, hTaTD));
      }
    }

    return tempFeatures;
  }

  private ClassicCounter<IntDependency> collapseNegExamples(boolean valenceBin) {
    ClassicCounter<IntDependency> negExamples = new ClassicCounter<IntDependency>();
    for (List<List<IntDependency>> llDep : negExamplesPerSentence) {
      for (List<IntDependency> lDep : llDep) {
        for (IntDependency dep : lDep) {
          short dist = valenceBin ? valenceBin(dep.distance) : dep.distance;
          negExamples.incrementCount(intern(dep.head, dep.arg, dep.leftHeaded, dist));
        }
      }
    }
    negExamplesPerSentence = null;
    return negExamples;
  }

  private transient ClassicCounter<IntDependency> distCounter;
  private transient ClassicCounter<IntDependency> posExamples;
  private transient ClassicCounter<IntDependency> negExamples;
  private transient ClassicCounter<IntDependency> stopExamples;

  public void train(Collection<Tree> trees) {
    tabulateTrees(trees);

    negExamples = sampleNegExamples(false);

    depDumpPrintln(posExamples.toString());
    depDumpPrintln(negExamples.toString());

    doInitialThresholding();

    Dataset<String, IntFeature> data = makeDataset();
    doJointThresholding(data);

    System.err.println("Training classifier");
    depClassifier = new LogisticClassifierFactory<String,IntFeature>().trainClassifier(data);

    // data = null; // probably isn't necessary

    constructCounters();
    posExamples = null;
  }

  private void doJointThresholding(Dataset<String, IntFeature> data) {
    System.err.println("Running thresholding.");

    int types = data.numFeatureTypes();
    data.applyFeatureCountThreshold(jointThresh);
    int numRemoved = types - data.numFeatureTypes();
    if (numRemoved > 0) {
      System.out.println("Thresholding (" + jointThresh + ") removed " + numRemoved + " features.");
    }
  }

  private Dataset<String, IntFeature> makeDataset() {
    int totalExamples = posExamples.size() + negExamples.size();

    //    WeightedDataset data = new WeightedDataset(totalExamples);
    Dataset<String, IntFeature> data = new Dataset<String, IntFeature>(totalExamples);

    System.err.println("Compiling examples");

    for (IntDependency dep : posExamples.keySet()) {
      Collection<IntFeature> features = makeFeatures(dep.head.word, dep.head.tag, dep.arg.word, dep.arg.tag, dep.leftHeaded, dep.distance);
      data.add(features, posLabel);
      //      data.add(features, posLabel, (float) posExamples.getCount(dep));
      if (data.size() % 1000 == 0) {
        System.err.println(data.size() + "/" + totalExamples + " examples processed.  joint features: " + data.numFeatureTypes());
      }
    }

    // we'll need these later to make counters
    //    posExamples = null;

    for (IntDependency dep : negExamples.keySet()) {
      Collection<IntFeature> features = makeFeatures(dep.head.word, dep.head.tag, dep.arg.word, dep.arg.tag, dep.leftHeaded, dep.distance);
      data.add(features, negLabel);
      //      data.add(features, negLabel, (float) negExamples.getCount(dep));
      if (data.size() % 1000 == 0) {
        System.err.println(data.size() + "/" + totalExamples + " examples processed.  joint features: " + data.numFeatureTypes());
      }
    }

    negExamples = null;
    return data;
  }

  private void doInitialThresholding() {
    Collection<String> words = new ArrayList<String>();
    for (Iterator<IntDependency> iter = posExamples.keySet().iterator(); iter.hasNext();) {
      IntDependency dep = iter.next();
      words.add(dep.head.wordString(wordIndex));
      words.add(dep.arg.wordString(wordIndex));
    }
    for (Iterator<IntDependency> iter = negExamples.keySet().iterator(); iter.hasNext();) {
      IntDependency dep = iter.next();
      words.add(dep.head.wordString(wordIndex));
      words.add(dep.arg.wordString(wordIndex));
    }
    System.err.println("Doing word feature thresholding on small feature extractor.");
    smallWordFeatExtractor.applyFeatureCountThreshold(words, wordFeatThresh);
    System.err.println("Doing word feature thresholding on large feature extractor.");
    largeWordFeatExtractor.applyFeatureCountThreshold(words, wordFeatThresh);
  }

  private void constructCounters() {
    System.err.println("Making counters.");
    stopCounter = new ClassicCounter<IntDependency>();
    for (IntDependency dep : stopExamples.keySet()) {
      double count = stopExamples.getCount(dep);
      stopCounter.incrementCount(dep, count);
      IntTaggedWord hT = new IntTaggedWord(-1, dep.head.tag);
      stopCounter.incrementCount(intern(hT, stopTW, dep.leftHeaded, dep.distance), count);
    }

    depCounter = new ClassicCounter<IntDependency>();
    nonDepCounter = collapseNegExamples(true);
    negExamplesPerSentence = null;
    for (IntDependency dep : posExamples.keySet()) {
      double count = posExamples.getCount(dep);
      depCounter.incrementCount(intern(dep.head, dep.arg, dep.leftHeaded, valenceBin(dep.distance)), count);
      stopCounter.incrementCount(intern(dep.head, wildTW, dep.leftHeaded, dep.distance), count);
      IntTaggedWord hT = new IntTaggedWord(-1, dep.head.tag);
      stopCounter.incrementCount(intern(hT, wildTW, dep.leftHeaded, dep.distance), count);
    }

    System.err.println("stopCounter has " + stopCounter.size() + " elements.");
    System.err.println("depCounter has " + depCounter.size() + " elements.");
    System.err.println("nonDepCounter has " + nonDepCounter.size() + " elements.");

    posExamples = null;
  }

  private ClassicCounter<IntDependency> sampleNegExamples(boolean valenceBin) {
    System.err.println("Sampling from negative examples.");
    double maxCount = Counters.max(distCounter);
    double[] lScores = new double[numDistBins()];
    double[] rScores = new double[numDistBins()];
    for (IntDependency dep : distCounter.keySet()) {
      (dep.leftHeaded ? lScores : rScores)[dep.distance] = (distCounter.getCount(dep) / maxCount);
    }

    ClassicCounter<IntDependency> negExamples = new ClassicCounter<IntDependency>();
    for (Iterator<List<List<IntDependency>>> iterator = negExamplesPerSentence.iterator(); iterator.hasNext();) {
      List<List<IntDependency>> wordPairsPerWord = iterator.next();
      for (List<IntDependency> wordPairs : wordPairsPerWord) {
        float negWeight = (float) wordPairs.size() / numNegExamplesPerWord;
        for (int i = 0; i < numNegExamplesPerWord; i++) {
          while (wordPairs.size() > 0) {
            int index = (int) (Math.random() * wordPairs.size());
            IntDependency negDep = wordPairs.get(index);
            if (Math.random() < (negDep.leftHeaded ? lScores : rScores)[negDep.distance]) {
              short dist = valenceBin ? valenceBin(negDep.distance) : negDep.distance;
              negExamples.incrementCount(intern(negDep.head, negDep.arg, negDep.leftHeaded, dist), negWeight);
              wordPairs.remove(index);
              break;
            }
          }
        }
      }
    }

    return negExamples;
  }

  private transient List<List<List<IntDependency>>> negExamplesPerSentence; // this is insane

  private void tabulateTrees(Collection<Tree> trees) {
    System.err.print("Getting examples...");
    stopExamples = new ClassicCounter<IntDependency>();
    negExamplesPerSentence = new ArrayList<List<List<IntDependency>>>();
    distCounter = new ClassicCounter<IntDependency>();
    posExamples = new ClassicCounter<IntDependency>();

    wordPairCounter = new ClassicCounter<Pair<IntTaggedWord, IntTaggedWord>>();
    wordCounter = new ClassicCounter<IntTaggedWord>();

    int count = 0;
    for (Tree tree : trees) {
      tabulateTree(tree);
      if (++count % 1000 == 0) {
        System.err.print('.');//"Tabulated " + count + " trees.");
      }
    }
    System.err.println();
    // this oughta save a lot of memory without losing much
    wordPairCounter.removeAll(Counters.keysBelow(wordPairCounter, 1.5));
  }

  private transient int lengthOfCurrentSentence = 0;
  List<List<IntDependency>> negExamplesForSentence;

  private transient IntTaggedWord[] ITWs;

  private void tabulateTree(Tree tree) {
    List taggedWords = tree.taggedYield();

    lengthOfCurrentSentence = taggedWords.size();
    if (ITWs == null || ITWs.length < lengthOfCurrentSentence) {
      ITWs = new IntTaggedWord[lengthOfCurrentSentence];
    }
    for (int i = 0; i < lengthOfCurrentSentence; i++) {
      TaggedWord taggedWord = (TaggedWord) taggedWords.get(i);
      //      int tag = tagIndex.indexOf(taggedWord.tag(), true);
      int tag = tagBin(tagIndex.indexOf(taggedWord.tag(), true));
      String wordString = taggedWord.word();
      int word = wordIndex.indexOf(wordString, true);
      ITWs[i] = itwInterner.intern(new IntTaggedWord(word, tag));
      wordCounter.incrementCount(ITWs[i]);
      for (int j = 0; j < i; j++) {
        wordPairCounter.incrementCount(pairInterner.intern(new Pair<IntTaggedWord, IntTaggedWord>(ITWs[i], ITWs[j])));
      }
    }
    negExamplesForSentence = new ArrayList<List<IntDependency>>();
    tabulateTreeHelper(tree, 0);

    negExamplesPerSentence.add(negExamplesForSentence);
  }

  private static class EndHead {
    public int end;
    public int head;
  }

  private transient EndHead tempEndHead = new EndHead();

  private EndHead tabulateTreeHelper(Tree tree, int loc) {
    if (tree.isLeaf() || tree.isPreTerminal()) {
      tempEndHead.head = loc;
      tempEndHead.end = loc + 1;
      return tempEndHead;
    }
    if (tree.children().length == 1) {
      return tabulateTreeHelper(tree.children()[0], loc);
    }
    tempEndHead = tabulateTreeHelper(tree.children()[0], loc);
    int lHead = tempEndHead.head;
    int split = tempEndHead.end;
    tempEndHead = tabulateTreeHelper(tree.children()[1], tempEndHead.end);
    int end = tempEndHead.end;
    int rHead = tempEndHead.head;
    String hWord = ((HasWord) tree.label()).word();
    String lWord = ((HasWord) tree.children()[0].label()).word();
    boolean leftHeaded = hWord.equals(lWord);
    int head = (leftHeaded ? lHead : rHead);
    int arg = (leftHeaded ? rHead : lHead);

    IntTaggedWord hTW = ITWs[head];
    IntTaggedWord aTW = ITWs[arg];

    IntDependency dependency = intern(hTW, aTW, leftHeaded, distanceBin(leftHeaded ? split - head - 1 : head - split));
    posExamples.incrementCount(dependency);
    IntDependency stopL = intern(aTW, stopTW, false, distanceBin(leftHeaded ? arg - split : arg - loc));
    stopExamples.incrementCount(stopL);
    IntDependency stopR = intern(aTW, stopTW, true, distanceBin(leftHeaded ? end - arg - 1 : split - arg - 1));
    stopExamples.incrementCount(stopR);

    distCounter.incrementCount(intern(wildTW, wildTW, leftHeaded, distanceBin(leftHeaded ? split - head - 1 : head - split)));

    List<IntDependency> negExamples = new ArrayList<IntDependency>();
    int beginArg = (leftHeaded ? split : loc);
    int endArg = (leftHeaded ? end : split);
    for (int notHead = 0; notHead < beginArg; notHead++) {
      if (notHead == head) {
        continue;
      }
      int thisDist = beginArg - notHead - 1;
      negExamples.add(intern(ITWs[notHead], aTW, true, distanceBin(thisDist)));
    }

    for (int notHead = endArg + 1; notHead < lengthOfCurrentSentence; notHead++) {
      if (notHead == head) {
        continue;
      }
      int thisDist = notHead - endArg;
      negExamples.add(intern(ITWs[notHead], aTW, false, distanceBin(thisDist)));
    }

    negExamplesForSentence.add(negExamples);

    tempEndHead.head = head;
    return tempEndHead;
  }

  private void readObject(ObjectInputStream ois)
    throws IOException, ClassNotFoundException
  {
    ois.defaultReadObject();
    pairInterner = new Interner<Pair<IntTaggedWord, IntTaggedWord>>();
  }

}