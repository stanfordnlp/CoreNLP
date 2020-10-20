// MaxentTagger -- StanfordMaxEnt, A Maximum Entropy Toolkit
// Copyright (c) 2002-2016 Leland Stanford Junior University

// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

// For more information, bug reports, fixes, contact:
// Christopher Manning
// Dept of Computer Science, Gates 2A
// Stanford CA 94305-9020
// USA
// Support/Questions: stanford-nlp on SO or java-nlp-user@lists.stanford.edu
// Licensing: java-nlp-support@lists.stanford.edu
// http://nlp.stanford.edu/software/tagger.html

package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.PrintFile;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.sequences.BestSequenceFinder;
import edu.stanford.nlp.sequences.ExactBestSequenceFinder;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.tagger.common.Tagger;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.util.stream.IntStream;


/**
 * @author Kristina Toutanova
 * @author Michel Galley
 * @version 1.0
 */
public class TestSentence implements SequenceModel  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TestSentence.class);

  protected final boolean VERBOSE;
  protected static final String naTag = "NA";
  private static final String[] naTagArr = { naTag };
  protected static final boolean DBG = false;
  protected static final int kBestSize = 1;

  protected final String tagSeparator;
  protected final String encoding;
  protected final int minWordsLockTags;
  protected final PairsHolder pairs = new PairsHolder();
  protected List<String> sent;
  private List<String> originalTags;
  // origWords is only set when run with a list of HasWords; when run
  // with a list of strings, this will be null
  protected List<HasWord> origWords;
  protected int size; // TODO this always has the value of sent.size(). Remove it? [cdm 2008]
  // protected double[][][] probabilities;
  private String[] correctTags;
  protected String[] finalTags;
  int numRight;
  int numWrong;
  int numUnknown;
  int numWrongUnknown;
  private int endSizePairs; // = 0;

  private volatile History history;
  private volatile Map<String,double[]> localScores = Generics.newHashMap();
  private volatile double[][] localContextScores;

  protected final MaxentTagger maxentTagger;

  public TestSentence(MaxentTagger maxentTagger) {
    assert(maxentTagger != null);
    assert(maxentTagger.getLambdaSolve() != null);
    this.maxentTagger = maxentTagger;
    if (maxentTagger.config != null) {
      tagSeparator = maxentTagger.config.getTagSeparator();
      encoding = maxentTagger.config.getEncoding();
      VERBOSE = maxentTagger.config.getVerbose();
      minWordsLockTags = maxentTagger.config.getMinWordsLockTags();
    } else {
      tagSeparator = TaggerConfig.getDefaultTagSeparator();
      encoding = "utf-8";
      VERBOSE = false;
      minWordsLockTags = 1;
    }
    history = new History(pairs, maxentTagger.extractors);
  }

  public void setCorrectTags(List<? extends HasTag> sentence) {
    int len = sentence.size();
    correctTags = new String[len];
    for (int i = 0; i < len; i++) {
      correctTags[i] = sentence.get(i).tag();
    }
  }

  /**
   * Tags the sentence s by running maxent model.  Returns a sentence (List) of
   * TaggedWord objects.
   *
   * @param s Input sentence (List).  This isn't changed.
   * @return Tagged sentence
   */
  public ArrayList<TaggedWord> tagSentence(List<? extends HasWord> s,
                                           boolean reuseTags) {
    this.origWords = new ArrayList<>(s);
    int sz = s.size();
    this.sent = new ArrayList<>(sz + 1);
    for (HasWord value1 : s) {
      if (maxentTagger.wordFunction != null) {
        sent.add(maxentTagger.wordFunction.apply(value1.word()));
      } else {
        sent.add(value1.word());
      }
    }
    sent.add(Tagger.EOS_WORD);
    if (reuseTags) {
      this.originalTags = new ArrayList<>(sz + 1);
      for (HasWord value : s) {
        if (value instanceof HasTag) {
          originalTags.add(((HasTag) value).tag());
        } else {
          originalTags.add(null);
        }
      }
      originalTags.add(Tagger.EOS_TAG);
    }
    size = sz + 1;
    if (VERBOSE) {
      log.info("Sentence: " + SentenceUtils.listToString(sent, false, tagSeparator));
    }
    init();
    ArrayList<TaggedWord> result = testTagInference();
    if (maxentTagger.wordFunction != null) {
      for (int j = 0; j < sz; ++j) {
        result.get(j).setWord(s.get(j).word());
      }
    }
    return result;
  }


  protected void revert(int prevSize) {
    endSizePairs = prevSize;
  }

  protected void init() {
    //the eos are assumed already there
    localContextScores = new double[size][];
    for (int i = 0; i < size - 1; i++) {
      if (maxentTagger.dict.isUnknown(sent.get(i))) {
        numUnknown++;
      }
    }
  }

  /**
   * Returns a string representation of the sentence.
   * @return tagged sentence
   */
  String getTaggedNice() {
    StringBuilder sb = new StringBuilder();
    // size - 1 means to exclude the EOS (end of string) symbol
    for (int i = 0; i < size - 1; i++) {
      sb.append(toNice(sent.get(i))).append(tagSeparator).append(toNice(finalTags[i]));
      sb.append(' ');
    }
    return sb.toString();
  }


  private ArrayList<TaggedWord> getTaggedSentence() {
    final boolean hasOffset;
    hasOffset = origWords != null && ! origWords.isEmpty() && (origWords.get(0) instanceof HasOffset);
    ArrayList<TaggedWord> taggedSentence = new ArrayList<>();
    for (int j = 0; j < size - 1; j++) {
      String tag = finalTags[j];
      TaggedWord w = new TaggedWord(sent.get(j), tag);
      if (hasOffset) {
        HasOffset offset = (HasOffset) origWords.get(j);
        w.setBeginPosition(offset.beginPosition());
        w.setEndPosition(offset.endPosition());
      }
      taggedSentence.add(w);
    }
    return taggedSentence;
  }

  static String toNice(String s) {
    if (s == null) {
      return naTag;
    } else {
      return s;
    }
  }

  /** calculateProbs puts log probs of taggings in the probabilities array.
   *
   *  @param probabilities Array with indices sent size, k best size, numTags
   */
  protected void calculateProbs(double[][][] probabilities) {
    ArrayUtils.fill(probabilities, Double.NEGATIVE_INFINITY);
    for (int hyp = 0; hyp < kBestSize; hyp++) {
      // put the whole thing in pairs, give its beginning and end
      pairs.setSize(size);
      for (int i = 0; i < size; i++) {
        pairs.setWord(i,sent.get(i));
        pairs.setTag(i,finalTags[i]);
        //pairs.add(new WordTag(sent.get(i),finalTags[i]));
        // TODO: if kBestSize > 1, use KBestSequenceFinder and save
        // k-best hypotheses into finalTags:
        //pairs.setTag(i,finalTags[i]);
      }
      int start = endSizePairs;
      int end = endSizePairs + size - 1;
      endSizePairs = endSizePairs + size;
      // iterate over the sentence
      for (int current = 0; current < size; current++) {
        History h = new History(start, end, current + start, pairs, maxentTagger.extractors);
        String[] tags = stringTagsAt(h.current - h.start + leftWindow());
        double[] probs = getHistories(tags, h);
        ArrayMath.logNormalize(probs);

        for (int j = 0; j < tags.length; j++) {
          // score the j-th tag
          int tagindex = maxentTagger.hasApproximateScoring() ? maxentTagger.tags.getIndex(tags[j]) : j;
          // log.info("Mapped from j="+ j + " " + tags[j] + " to " + tagindex);
          probabilities[current][hyp][tagindex] = probs[j];
        }
      } // for current
    } // for hyp
    // clean up the stuff in PairsHolder (added by cdm in Aug 2008)
    revert(0);
  } // end calculateProbs()


  /** Write the tagging and note any errors (if pf != null) and accumulate
   *  global statistics.
   *
   *  @param finalTags Chosen tags for sentence
   *  @param pf File to write tagged output to (can be null, then no output;
   *               at present it is non-null iff the debug property is set)
   */
  protected void writeTagsAndErrors(String[] finalTags, PrintFile pf, boolean verboseResults) {
    StringWriter sw = new StringWriter(200);
    for (int i = 0; i < correctTags.length; i++) {
      sw.write(toNice(sent.get(i)));
      sw.write(tagSeparator);
      sw.write(finalTags[i]);
      sw.write(' ');
      if (pf != null) {
        pf.print(toNice(sent.get(i)));
        pf.print(tagSeparator);
        pf.print(finalTags[i]);
      }
      if ((correctTags[i]).equals(finalTags[i])) {
        numRight++;
      } else {
        numWrong++;
        if (pf != null) pf.print('|' + correctTags[i]);
        if (verboseResults) {
          log.info((maxentTagger.dict.isUnknown(sent.get(i)) ? "Unk" : "") + "Word: " + sent.get(i) + "; correct: " + correctTags[i] + "; guessed: " + finalTags[i]);
        }

        if (maxentTagger.dict.isUnknown(sent.get(i))) {
          numWrongUnknown++;
          if (pf != null) pf.print("*");
        }// if
      }// else
      if (pf != null) pf.print(' ');
    }// for
    if (pf != null) pf.println();

    if (verboseResults) {
      PrintWriter pw;
      try {
        pw = new PrintWriter(new OutputStreamWriter(System.out, encoding), true);
      } catch (UnsupportedEncodingException uee) {
        pw = new PrintWriter(new OutputStreamWriter(System.out), true);
      }
      pw.println(sw);
    }
  }

  /**
   * Update a confusion matrix with the errors from this sentence.
   *
   * @param finalTags Chosen tags for sentence
   * @param confusionMatrix Confusion matrix to write to
   */
  protected void updateConfusionMatrix(String[] finalTags,
                                       ConfusionMatrix<String> confusionMatrix) {
    for (int i = 0; i < correctTags.length; i++)
      confusionMatrix.add(finalTags[i], correctTags[i]);
  }


  /**
   * Test using (exact Viterbi) TagInference.
   *
   * @return The tagged sentence
   */
  private ArrayList<TaggedWord> testTagInference() {
    runTagInference();
    return getTaggedSentence();
  }

  private void runTagInference() {
    this.initializeScorer();
    if (Thread.interrupted()) {  // Allow interrupting
      throw new RuntimeInterruptedException();
    }

    BestSequenceFinder ti = new ExactBestSequenceFinder();
      //new BeamBestSequenceFinder(50);
      //new KBestSequenceFinder()
    int[] bestTags = ti.bestSequence(this);
    finalTags = new String[bestTags.length];
    for (int j = 0; j < size; j++) {
      finalTags[j] = maxentTagger.tags.getTag(bestTags[j + leftWindow()]);
    }

    if (Thread.interrupted()) {  // Allow interrupting
      throw new RuntimeInterruptedException();
    }
    cleanUpScorer();
  }


  // This is used for Dan's tag inference methods.
  // current is the actual word number + leftW
  private void setHistory(int current, History h, int[] tags) {
    //writes over the tags in the last thing in pairs

    int left = leftWindow();
    int right = rightWindow();
    final TTags ttags = maxentTagger.tags;

    // Loop range, with guards:
    final int s = Math.max(current - left, left);
    final int e = Math.min(current + right, size + left - 1);
    for (int j = s; j <= e; j++) {
      h.setTag(j - left, ttags.getTag(tags[j]));
    }
  }

  // do initializations for the TagScorer interface
  private void initializeScorer() {
    pairs.setSize(size);
    for (int i = 0; i < size; i++)
      pairs.setWord(i,sent.get(i));
    endSizePairs += size;
  }


  /**
   * clean-up after the scorer
   */
  private void cleanUpScorer() {
    revert(0);
  }

  // This scores the current assignment in PairsHolder at
  // current position h.current (returns normalized scores)
  private double[] getScores(History h) {
    return maxentTagger.hasApproximateScoring() ? getApproximateScores(h) : getExactScores(h);
  }

  private double[] getExactScores(History h) {
    String[] tags = stringTagsAt(h.current - h.start + leftWindow());
    double[] histories = getHistories(tags, h); // log score for each tag
    ArrayMath.logNormalize(histories);
    double[] scores = new double[tags.length];
    for (int j = 0; j < tags.length; j++) {
      // score the j-th tag
      String tag = tags[j];
      int tagindex = maxentTagger.tags.getIndex(tag);
      scores[j] = histories[tagindex];
    }
    return scores;
  }

  // In this method, each tag that is incompatible with the current word
  // (e.g., apple_CC) gets a default (constant) score instead of its exact score.
  // The scores of all other tags are computed exactly.
  private double[] getApproximateScores(History h) {
    String[] tags = stringTagsAt(h.current - h.start + leftWindow());
    double[] scores = getHistories(tags, h); // log score for each active tag, unnormalized

    // Number of tags that get assigned a default score:
    int nDefault = maxentTagger.ySize - tags.length;
    double logScore = ArrayMath.logSum(scores);
    double logScoreInactiveTags = maxentTagger.getInactiveTagDefaultScore(nDefault);
    double logTotal = SloppyMath.logAdd(logScore, logScoreInactiveTags);
    ArrayMath.addInPlace(scores, -logTotal);

    return scores;
  }

  // this is for the VERBOSE debugging code
  private double[][] fullScores; // = null;

  /** This computes scores of tags at a position in a sentence (the so called "History").
   *  Usually, it precomputes scores of local features (localScores).
   *  This is turned off if VERBOSE is set to make printing feature weights simpler....
   *
   * @param tags
   * @param h
   * @return
   */
  protected double[] getHistories(String[] tags, History h) {
    boolean rare = maxentTagger.isRare(ExtractorFrames.cWord.extract(h));
    Extractors ex = maxentTagger.extractors;
    Extractors exR = maxentTagger.extractorsRare;
    String w = pairs.getWord(h.current);
    // if (DBG) { System.err.printf("%s: loc %s lc %s dy %s; rloc %s rlc %s rdy %s%n", w, ex.local, ex.localContext, ex.dynamic, exR.local, exR.localContext, exR.dynamic); }

    if (VERBOSE) {
      // Good options to print out what is calculated here are: -debug -verbose -verboseResults false -approximate false
      int extractorsSize = rare ? ex.size() + exR.size() : ex.size();
      fullScores = new double[extractorsSize][maxentTagger.ySize];

      List<Pair<Integer, Extractor>> allEx = new ArrayList<>(ex.local);
      allEx.addAll(ex.localContext);
      allEx.addAll(ex.dynamic);
      List<Pair<Integer, Extractor>> allExR = new ArrayList<>();
      if (rare) {
        allExR.addAll(exR.local);
        allExR.addAll(exR.localContext);
        allExR.addAll(exR.dynamic);
      }

      // = null;
      ArrayList<String> extractorVals = new ArrayList<>();
      for (int i = 0; i < extractorsSize; i++) {
        extractorVals.add("foo");
      }
      for (Pair<Integer, Extractor> pair : allEx) {
        int kf = pair.first();
        Extractor e = pair.second();
        String val = e.extract(h);
        extractorVals.set(kf, e + " " + val);
      }
      for (Pair<Integer, Extractor> pair : allExR) {
        int kf = pair.first();
        Extractor e = pair.second();
        String val = e.extract(h);
        extractorVals.set(kf + ex.size(), e + " " + val);
      }

      double[] totalS = getHistories(tags, h, allEx, rare ? allExR : null);

      NumberFormat nf = new DecimalFormat("0.00");
      Object[] colNames = IntStream.range(0, maxentTagger.ySize).mapToObj(k -> maxentTagger.tags.getTag(k)).toArray();
      System.err.println(ArrayMath.toString(fullScores, 6, extractorVals.toArray(), colNames,
              48, nf, false, true, w));
      return totalS;
    } // end if (VERBOSE) case

    double[] lS = localScores.get(w);
    if (lS == null) {
      lS = getHistories(tags, h, ex.local, rare ? exR.local : null);
      localScores.put(w, lS);
    } else if (lS.length != tags.length) {
      // This case can occur when a word was given a specific forced
      // tag, and then later it shows up without the forced tag.
      // TODO: if a word is given a forced tag, we should always get
      // its features rather than use the cache, just in case the tag
      // given is not the same tag as before
      lS = getHistories(tags, h, ex.local, rare ? exR.local : null);
      if (tags.length > 1) {
        localScores.put(w, lS);
      }
    }
    double[] lcS = localContextScores[h.current];
    if (lcS == null) {
      lcS = getHistories(tags, h, ex.localContext, rare ? exR.localContext : null);
      localContextScores[h.current] = lcS;
      ArrayMath.pairwiseAddInPlace(lcS, lS);
    }
    double[] totalS = getHistories(tags, h, ex.dynamic, rare ? exR.dynamic : null);
    ArrayMath.pairwiseAddInPlace(totalS, lcS);
    return totalS;
  }

  private double[] getHistories(String[] tags, History h, List<Pair<Integer,Extractor>> extractors, List<Pair<Integer,Extractor>> extractorsRare) {
    return maxentTagger.hasApproximateScoring() ? getApproximateHistories(tags, h, extractors, extractorsRare) : getExactHistories(h, extractors, extractorsRare);
  }

  private double[] getExactHistories(History h, List<Pair<Integer,Extractor>> extractors, List<Pair<Integer,Extractor>> extractorsRare) {
    double[] scores = new double[maxentTagger.ySize];
    if (VERBOSE) { System.err.println("Calling exact histories"); }
    double[] lambda = maxentTagger.getLambdaSolve().lambda;
    for (Pair<Integer,Extractor> e : extractors) {
      runExactExtractor(e.first(), e.second(), h, scores, lambda);
    }
    if (extractorsRare != null) {
      int szCommon = maxentTagger.extractors.size();  // needs to be full size list of extractors not subset of some type
      for (Pair<Integer,Extractor> e : extractorsRare) {
        runExactExtractor(szCommon + e.first(), e.second(), h, scores, lambda);
      }
    }
    return scores;
  }

  private void runExactExtractor(int kf, Extractor ex, History h, double[] scores, double[] lambda) {
    int[] fAssociations = maxentTagger.fAssociations.get(kf).get(ex.extract(h));
    if (fAssociations != null) {
      for (int j = 0; j < maxentTagger.ySize; j++) {
        int fNum = fAssociations[j];
        if (fNum >= 0) {
          scores[j] += lambda[fNum];
          if (VERBOSE) { fullScores[kf][j] = lambda[fNum]; }
        }
      }
    }
  }

  /** Returns an unnormalized score (in log space) for each tag. */
  private double[] getApproximateHistories(String[] tags, History h, List<Pair<Integer,Extractor>> extractors, List<Pair<Integer,Extractor>> extractorsRare) {
    double[] scores = new double[tags.length];
    if (VERBOSE) { System.err.println("Calling approx histories"); }
    double[] lambda = maxentTagger.getLambdaSolve().lambda;

    for (Pair<Integer, Extractor> e : extractors) {
      runApproximateExtractor(e.first(), e.second(), h, tags, scores, lambda);
    }
    if (extractorsRare != null) {
      int szCommon = maxentTagger.extractors.size();
      for (Pair<Integer, Extractor> e : extractorsRare) {
        runApproximateExtractor(szCommon + e.first(), e.second(), h, tags, scores, lambda);
      }
    }
    return scores;
  }

  private void runApproximateExtractor(int kf, Extractor extractor, History h, String[] tags, double[] scores, double[] lambda) {
    int[] fAssociations = maxentTagger.fAssociations.get(kf).get(extractor.extract(h));
    if (fAssociations == null) return;
    final TTags ttags = maxentTagger.tags;
    for (int j = 0; j < tags.length; j++) {
      int fNum = fAssociations[ttags.getIndex(tags[j])];
      if (fNum >= 0) {
        scores[j] += lambda[fNum];
        if (VERBOSE) { fullScores[kf][j] = lambda[fNum]; }
      }
    }
  }

  /**
   * This method should be called after the sentence has been tagged.
   * For every unknown word, this method prints the 3 most probable tags
   * to the file pfu.
   *
   * @param numSent The sentence number
   * @param pfu The file to print the probable tags to
   */
  void printUnknown(int numSent, PrintFile pfu) {
    NumberFormat nf = new DecimalFormat("0.0000");
    int numTags = maxentTagger.numTags();
    double[][][] probabilities = new double[size][kBestSize][numTags];
    calculateProbs(probabilities);
    for (int current = 0; current < size; current++) {
      if (maxentTagger.dict.isUnknown(sent.get(current))) {
        pfu.print(sent.get(current));
        pfu.print(':');
        pfu.print(numSent);
        double[] probs = new double[3];
        String[] tag3 = new String[3];
        getTop3(probabilities, current, probs, tag3);
        for (int i = 0; i < 3; i++) {
          if (probs[i] > Double.NEGATIVE_INFINITY) {
            pfu.print('\t');
            pfu.print(tag3[i]);
            pfu.print(' ');
            pfu.print(nf.format(Math.exp(probs[i])));
          }
        }
        int rank;
        String correctTag = toNice(this.correctTags[current]);
        for (rank = 0; rank < 3; rank++) {
          if (correctTag.equals(tag3[rank])) {
            break;
          } //if
        }
        pfu.print('\t');
        switch (rank) {
          case 0:
            pfu.print("Correct");
            break;
          case 1:
            pfu.print("2nd");
            break;
          case 2:
            pfu.print("3rd");
            break;
          default:
            pfu.print("Not top 3");
        }
        pfu.println();
      }// if
    }// for
  }

  // This method should be called after a sentence has been tagged.
  // For every word token, this method prints the 3 most probable tags
  // to the file pfu except for
  void printTop(PrintFile pfu) {
    NumberFormat nf = new DecimalFormat("0.0000");
    int numTags = maxentTagger.numTags();
    double[][][] probabilities = new double[size][kBestSize][numTags];
    calculateProbs(probabilities);

    for (int current = 0; current < correctTags.length; current++) {
      pfu.print(sent.get(current));
      double[] probs = new double[3];
      String[] tag3 = new String[3];
      getTop3(probabilities, current, probs, tag3);
      for (int i = 0; i < 3; i++) {
        if (probs[i] > Double.NEGATIVE_INFINITY) {
          pfu.print('\t');
          pfu.print(tag3[i]);
          pfu.print(' ');
          pfu.print(nf.format(Math.exp(probs[i])));
        }
      }
      int rank;
      String correctTag = toNice(this.correctTags[current]);
      for (rank = 0; rank < 3; rank++) {
        if (correctTag.equals(tag3[rank])) {
          break;
        } //if
      }
      pfu.print('\t');
      switch (rank) {
      case 0:
        pfu.print("Correct");
        break;
      case 1:
        pfu.print("2nd");
        break;
      case 2:
        pfu.print("3rd");
        break;
      default:
        pfu.print("Not top 3");
      }
      pfu.println();
    } // for
  }

  /** probs and tags should be passed in as arrays of size 3!
   *  If probs[i] == Double.NEGATIVE_INFINITY, then the entry should be ignored.
   */
  private void getTop3(double[][][] probabilities, int current, double[] probs, String[] tags) {
    int[] topIds = new int[3];
    double[] probTags = probabilities[current][0];
    Arrays.fill(probs, Double.NEGATIVE_INFINITY);
    for (int i = 0; i < probTags.length; i++) {
      if (probTags[i] > probs[0]) {
        probs[2] = probs[1];
        probs[1] = probs[0];
        probs[0] = probTags[i];
        topIds[2] = topIds[1];
        topIds[1] = topIds[0];
        topIds[0] = i;
      } else if (probTags[i] > probs[1]) {
        probs[2] = probs[1];
        probs[1] = probTags[i];
        topIds[2] = topIds[1];
        topIds[1] = i;
      } else if (probTags[i] > probs[2]) {
        probs[2] = probTags[i];
        topIds[2] = i;
      }
    }
    for (int j = 0; j < 3; j++) {
      tags[j] = toNice(maxentTagger.tags.getTag(topIds[j]));
    }
  }

  /*
   * Implementation of the TagScorer interface follows
   */

  @Override
  public int length() {
    return sent.size();
  }

  @Override
  public int leftWindow() {
    return maxentTagger.leftContext; //hard-code for now
  }

  @Override
  public int rightWindow() {
    return maxentTagger.rightContext; //hard code for now
  }


  @Override
  public int[] getPossibleValues(int pos) {
    String[] arr1 = stringTagsAt(pos);
    int[] arr = new int[arr1.length];
    for (int i = 0; i < arr.length; i++) {
      arr[i] = maxentTagger.tags.getIndex(arr1[i]);
    }

    return arr;
  }

  @Override
  public double scoreOf(int[] tags, int pos) {
    double[] scores = scoresOf(tags, pos);
    double score = Double.NEGATIVE_INFINITY;
    int[] pv = getPossibleValues(pos);
    final int search = tags[pos];
    for (int i = 0; i < scores.length; i++) {
      if (pv[i] == search) {
        score = scores[i];
      }
    }
    return score;
  }

  @Override
  public double scoreOf(int[] sequence) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double[] scoresOf(int[] tags, int pos) {
    if (DBG) {
      log.info("scoresOf(): length of tags is " + tags.length + "; position is " + pos + "; endSizePairs = " + endSizePairs + "; size is " + size + "; leftWindow is " + leftWindow());
      log.info("  History h = new History(" + (endSizePairs - size) + ", " + (endSizePairs - 1) + ", " + (endSizePairs - size + pos - leftWindow()) + ')');
    }
    history.init(endSizePairs - size, endSizePairs - 1, endSizePairs - size + pos - leftWindow());
    setHistory(pos, history, tags);
    return getScores(history);
  }

  // todo [cdm 2013]: Tagging could be sped up quite a bit here if we cached int
  // arrays of tags by index, not Strings
  protected String[] stringTagsAt(int pos) {
    pos -= leftWindow(); // Adjust once, for everything below:
    if ((pos < 0) || (pos >= size)) {
      return naTagArr;
    }

    if (originalTags != null) {
      final String orig = originalTags.get(pos);
      if (orig != null) {
        return new String[] { orig };
      }
    }

    String[] arr1;
    String word = sent.get(pos);
    int count = maxentTagger.dict.sum(word);
    if (count == 0 || count < minWordsLockTags) {
      arr1 = maxentTagger.tags.getOpenTagsArray();
    } else {
      arr1 = maxentTagger.dict.getTags(word);
    }
    return maxentTagger.tags.deterministicallyExpandTags(arr1);
  }

}
