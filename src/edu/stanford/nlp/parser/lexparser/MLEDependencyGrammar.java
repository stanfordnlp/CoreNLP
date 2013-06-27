package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import static edu.stanford.nlp.parser.lexparser.IntTaggedWord.ANY_WORD_INT;
import static edu.stanford.nlp.parser.lexparser.IntTaggedWord.ANY_TAG_INT;
import static edu.stanford.nlp.parser.lexparser.IntTaggedWord.STOP_WORD_INT;
import static edu.stanford.nlp.parser.lexparser.IntTaggedWord.STOP_TAG_INT;
import static edu.stanford.nlp.parser.lexparser.IntDependency.ANY_DISTANCE_INT;

import java.io.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class MLEDependencyGrammar extends AbstractDependencyGrammar {

  final boolean useSmoothTagProjection;
  final boolean useUnigramWordSmoothing;

  static final boolean DEBUG = false;

  protected int numWordTokens;

  /** Stores all the counts for dependencies (with and without the word
   *  being a wildcard) in the reduced tag space.
   */
  protected ClassicCounter<IntDependency> argCounter;
  protected ClassicCounter<IntDependency> stopCounter;  // reduced tag space

  /** Bayesian m-estimate prior for aT given hTWd against base distribution
   *  of aT given hTd.
   *  TODO: Note that these values are overwritten in the constructor. Find what is best and then maybe remove these defaults!
   */
  public double smooth_aT_hTWd = 32.0;
  /** Bayesian m-estimate prior for aTW given hTWd against base distribution
   *  of aTW given hTd.
   */
  public double smooth_aTW_hTWd = 16.0;
  public double smooth_stop = 4.0;
  /** Interpolation between model that directly predicts aTW and model
   *  that predicts aT and then aW given aT.  This percent of the mass
   *  is on the model directly predicting aTW.
   */
  public double interp = 0.6;
  //  public double distanceDecay = 0.0;

  // extra smoothing hyperparameters for tag projection backoff.  Only used if useSmoothTagProjection is true.
  public double smooth_aTW_aT = 96.0;  // back off Bayesian m-estimate of aTW given aT to aPTW given aPT
  public double smooth_aTW_hTd = 32.0; // back off Bayesian m-estimate of aTW_hTd to aPTW_hPTd (?? guessed, not tuned)
  public double smooth_aT_hTd = 32.0;  // back off Bayesian m-estimate of aT_hTd to aPT_hPTd (?? guessed, not tuned)
  public double smooth_aPTW_aPT = 16.0;  // back off word prediction from tag to projected tag (only used if useUnigramWordSmoothing is true)



  public MLEDependencyGrammar(TreebankLangParserParams tlpParams, boolean directional, boolean distance, boolean coarseDistance, boolean basicCategoryTagsInDependencyGrammar, Options op, Index<String> wordIndex, Index<String> tagIndex) {
    this(basicCategoryTagsInDependencyGrammar ? new BasicCategoryTagProjection(tlpParams.treebankLanguagePack()) : new TestTagProjection(), tlpParams, directional, distance, coarseDistance, op, wordIndex, tagIndex);
  }

  public MLEDependencyGrammar(TagProjection tagProjection, TreebankLangParserParams tlpParams, boolean directional, boolean useDistance, boolean useCoarseDistance, Options op, Index<String> wordIndex, Index<String> tagIndex) {
    super(tlpParams.treebankLanguagePack(), tagProjection, directional, useDistance, useCoarseDistance, op, wordIndex, tagIndex);
    useSmoothTagProjection = op.useSmoothTagProjection;
    useUnigramWordSmoothing = op.useUnigramWordSmoothing;
    argCounter = new ClassicCounter<IntDependency>();
    stopCounter = new ClassicCounter<IntDependency>();
    double[] smoothParams = tlpParams.MLEDependencyGrammarSmoothingParams();
    smooth_aT_hTWd = smoothParams[0];
    smooth_aTW_hTWd = smoothParams[1];
    smooth_stop = smoothParams[2];
    interp = smoothParams[3];

    // cdm added Jan 2007 to play with dep grammar smoothing.  Integrate this better if we keep it!
    smoothTP = new BasicCategoryTagProjection(tlpParams.treebankLanguagePack());
  }

  @Override
  public String toString() {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(2);
    StringBuilder sb = new StringBuilder(2000);
    String cl = getClass().getName();
    sb.append(cl.substring(cl.lastIndexOf('.') + 1)).append("[tagbins=");
    sb.append(numTagBins).append(",wordTokens=").append(numWordTokens).append("; head -> arg\n");
//    for (Iterator dI = coreDependencies.keySet().iterator(); dI.hasNext();) {
//      IntDependency d = (IntDependency) dI.next();
//      double count = coreDependencies.getCount(d);
//      sb.append(d + " count " + nf.format(count));
//      if (dI.hasNext()) {
//        sb.append(",");
//      }
//      sb.append("\n");
//    }
    sb.append("]");
    return sb.toString();
  }

  public boolean pruneTW(IntTaggedWord argTW) {
    String[] punctTags = tlp.punctuationTags();
    for (String punctTag : punctTags) {
      if (argTW.tag == tagIndex.indexOf(punctTag)) {
        return true;
      }
    }
    return false;
  }

  static class EndHead {
    public int end;
    public int head;
  }

  /** Adds dependencies to list depList.  These are in terms of the original
   *  tag set not the reduced (projected) tag set.
   */
  protected static EndHead treeToDependencyHelper(Tree tree, List<IntDependency> depList, int loc, Index<String> wordIndex, Index<String> tagIndex) {
    //       try {
    // 	PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out,"GB18030"),true);
    // 	tree.pennPrint(pw);
    //       }
    //       catch (UnsupportedEncodingException e) {}

    if (tree.isLeaf() || tree.isPreTerminal()) {
      EndHead tempEndHead = new EndHead();
      tempEndHead.head = loc;
      tempEndHead.end = loc + 1;
      return tempEndHead;
    }
    Tree[] kids = tree.children();
    if (kids.length == 1) {
      return treeToDependencyHelper(kids[0], depList, loc, wordIndex, tagIndex);
    }
    EndHead tempEndHead = treeToDependencyHelper(kids[0], depList, loc, wordIndex, tagIndex);
    int lHead = tempEndHead.head;
    int split = tempEndHead.end;
    tempEndHead = treeToDependencyHelper(kids[1], depList, tempEndHead.end, wordIndex, tagIndex);
    int end = tempEndHead.end;
    int rHead = tempEndHead.head;
    String hTag = ((HasTag) tree.label()).tag();
    String lTag = ((HasTag) kids[0].label()).tag();
    String rTag = ((HasTag) kids[1].label()).tag();
    String hWord = ((HasWord) tree.label()).word();
    String lWord = ((HasWord) kids[0].label()).word();
    String rWord = ((HasWord) kids[1].label()).word();
    boolean leftHeaded = hWord.equals(lWord);
    String aTag = (leftHeaded ? rTag : lTag);
    String aWord = (leftHeaded ? rWord : lWord);
    int hT = tagIndex.indexOf(hTag);
    int aT = tagIndex.indexOf(aTag);
    int hW = (wordIndex.contains(hWord) ? wordIndex.indexOf(hWord) : wordIndex.indexOf(Lexicon.UNKNOWN_WORD));
    int aW = (wordIndex.contains(aWord) ? wordIndex.indexOf(aWord) : wordIndex.indexOf(Lexicon.UNKNOWN_WORD));
    int head = (leftHeaded ? lHead : rHead);
    int arg = (leftHeaded ? rHead : lHead);
    IntDependency dependency = new IntDependency(hW, hT, aW, aT, leftHeaded, (leftHeaded ? split - head - 1 : head - split));
    depList.add(dependency);
    IntDependency stopL = new IntDependency(aW, aT, STOP_WORD_INT, STOP_TAG_INT, false, (leftHeaded ? arg - split : arg - loc));
    depList.add(stopL);
    IntDependency stopR = new IntDependency(aW, aT, STOP_WORD_INT, STOP_TAG_INT, true, (leftHeaded ? end - arg - 1 : split - arg - 1));
    depList.add(stopR);
    //System.out.println("Adding: "+dependency+" at "+tree.label());
    tempEndHead.head = head;
    return tempEndHead;
  }


  public void dumpSizes() {
//    System.out.println("core dep " + coreDependencies.size());
    System.out.println("arg counter " + argCounter.size());
    System.out.println("stop counter " + stopCounter.size());
  }

  /** Returns the List of dependencies for a binarized Tree.
   *  In this tree, one of the two children always equals the head.
   *  The dependencies are in terms of
   *  the original tag set not the reduced (projected) tag set.
   *
   *  @param tree A tree to be analyzed as dependencies
   *  @return The list of dependencies in the tree (int format)
   */
  public static List<IntDependency> treeToDependencyList(Tree tree, Index<String> wordIndex, Index<String> tagIndex) {
    List<IntDependency> depList = new ArrayList<IntDependency>();
    treeToDependencyHelper(tree, depList, 0, wordIndex, tagIndex);
    if (DEBUG) {
      System.out.println("----------------------------");
      tree.pennPrint();
      System.out.println(depList);
    }
    return depList;
  }

  public double scoreAll(Collection<IntDependency> deps) {
    double totalScore = 0.0;
    for (IntDependency d : deps) {
      //if (d.head.word == wordIndex.indexOf("via") ||
      //          d.arg.word == wordIndex.indexOf("via"))
      //System.out.println(d+" at "+score(d));
      double score = score(d);
      if (score > Double.NEGATIVE_INFINITY) {
        totalScore += score;
      }
    }
    return totalScore;
  }

  /** Tune the smoothing and interpolation parameters of the dependency
   *  grammar based on a tuning treebank.
   *
   *  @param trees A Collection of Trees for setting parameters
   */
  @Override
  public void tune(Collection<Tree> trees) {
    List<IntDependency> deps = new ArrayList<IntDependency>();
    for (Tree tree : trees) {
      deps.addAll(treeToDependencyList(tree, wordIndex, tagIndex));
    }

    double bestScore = Double.NEGATIVE_INFINITY;
    double bestSmooth_stop = 0.0;
    double bestSmooth_aTW_hTWd = 0.0;
    double bestSmooth_aT_hTWd = 0.0;
    double bestInterp = 0.0;

    System.err.println("Tuning smooth_stop...");
    for (smooth_stop = 1.0/100.0; smooth_stop < 100.0; smooth_stop *= 1.25) {
      double totalScore = 0.0;
      for (IntDependency dep : deps) {
        if (!rootTW(dep.head)) {
          double stopProb = getStopProb(dep);
          if (!dep.arg.equals(stopTW)) {
            stopProb = 1.0 - stopProb;
          }
          if (stopProb > 0.0) {
            totalScore += Math.log(stopProb);
          }
        }
      }
      if (totalScore > bestScore) {
        bestScore = totalScore;
        bestSmooth_stop = smooth_stop;
      }
    }
    smooth_stop = bestSmooth_stop;
    System.err.println("Tuning selected smooth_stop: " + smooth_stop);

    for (Iterator<IntDependency> iter = deps.iterator(); iter.hasNext(); ) {
      IntDependency dep = iter.next();
      if (dep.arg.equals(stopTW)) {
        iter.remove();
      }
    }

    System.err.println("Tuning other parameters...");

    if ( ! useSmoothTagProjection) {
      bestScore = Double.NEGATIVE_INFINITY;
      for (smooth_aTW_hTWd = 0.5; smooth_aTW_hTWd < 100.0; smooth_aTW_hTWd *= 1.25) {
        System.err.print(".");
        for (smooth_aT_hTWd = 0.5; smooth_aT_hTWd < 100.0; smooth_aT_hTWd *= 1.25) {
          for (interp = 0.02; interp < 1.0; interp += 0.02) {
            double totalScore = 0.0;
            for (IntDependency dep : deps) {
              double score = score(dep);
              if (score > Double.NEGATIVE_INFINITY) {
                totalScore += score;
              }
            }
            if (totalScore > bestScore) {
              bestScore = totalScore;
              bestInterp = interp;
              bestSmooth_aTW_hTWd = smooth_aTW_hTWd;
              bestSmooth_aT_hTWd = smooth_aT_hTWd;
              System.err.println("Current best interp: " + interp + " with score " + totalScore);
            }
          }
        }
      }
      smooth_aTW_hTWd = bestSmooth_aTW_hTWd;
      smooth_aT_hTWd = bestSmooth_aT_hTWd;
      interp = bestInterp;
    } else {
      // for useSmoothTagProjection
      double bestSmooth_aTW_aT = 0.0;
      double bestSmooth_aTW_hTd = 0.0;
      double bestSmooth_aT_hTd = 0.0;

      bestScore = Double.NEGATIVE_INFINITY;
      for (smooth_aTW_hTWd = 1.125; smooth_aTW_hTWd < 100.0; smooth_aTW_hTWd *= 1.5) {
        System.err.print("#");
        for (smooth_aT_hTWd = 1.125; smooth_aT_hTWd < 100.0; smooth_aT_hTWd *= 1.5) {
          System.err.print(":");
          for (smooth_aTW_aT = 1.125; smooth_aTW_aT < 200.0; smooth_aTW_aT *= 1.5) {
            System.err.print(".");
            for (smooth_aTW_hTd = 1.125; smooth_aTW_hTd < 100.0; smooth_aTW_hTd *= 1.5) {
              for (smooth_aT_hTd = 1.125; smooth_aT_hTd < 100.0; smooth_aT_hTd *= 1.5) {
                for (interp = 0.2; interp <= 0.8; interp += 0.02) {
                  double totalScore = 0.0;
                  for (IntDependency dep : deps) {
                    double score = score(dep);
                    if (score > Double.NEGATIVE_INFINITY) {
                      totalScore += score;
                    }
                  }
                  if (totalScore > bestScore) {
                    bestScore = totalScore;
                    bestInterp = interp;
                    bestSmooth_aTW_hTWd = smooth_aTW_hTWd;
                    bestSmooth_aT_hTWd = smooth_aT_hTWd;
                    bestSmooth_aTW_aT = smooth_aTW_aT;
                    bestSmooth_aTW_hTd = smooth_aTW_hTd;
                    bestSmooth_aT_hTd = smooth_aT_hTd;
                    System.err.println("Current best interp: " + interp + " with score " + totalScore);
                  }
                }
              }
            }
          }
        }
        System.err.println();
      }
      smooth_aTW_hTWd = bestSmooth_aTW_hTWd;
      smooth_aT_hTWd = bestSmooth_aT_hTWd;
      smooth_aTW_aT = bestSmooth_aTW_aT;
      smooth_aTW_hTd = bestSmooth_aTW_hTd;
      smooth_aT_hTd = bestSmooth_aT_hTd;
      interp = bestInterp;
    }

    System.err.println("\nTuning selected smooth_aTW_hTWd: " + smooth_aTW_hTWd + " smooth_aT_hTWd: " + smooth_aT_hTWd + " interp: " + interp + " smooth_aTW_aT: " + smooth_aTW_aT + " smooth_aTW_hTd: " + smooth_aTW_hTd + " smooth_aT_hTd: " + smooth_aT_hTd);
  }


  /** Add this dependency with the given count to the grammar.
   *  This is the main entry point of MLEDependencyGrammarExtractor.
   *  This is a dependency represented in the full tag space.
   */
  public void addRule(IntDependency dependency, double count) {
    if ( ! directional) {
      dependency = new IntDependency(dependency.head, dependency.arg, false, dependency.distance);
    }
    if (verbose) System.err.println("Adding dep " + dependency);
    //    coreDependencies.incrementCount(dependency, count);
    /*new IntDependency(dependency.head.word,
                                        dependency.head.tag,
                                        dependency.arg.word,
                                        dependency.arg.tag,
                                        dependency.leftHeaded,
                                        dependency.distance), count);
    */
    expandDependency(dependency, count);
    // System.err.println("stopCounter: " + stopCounter);
    // System.err.println("argCounter: " + argCounter);
  }

  /** The indices of this list are in the tag binned space. */
  protected transient List<IntTaggedWord> tagITWList = null; //new ArrayList();


  /** This maps from a tag to a cached IntTagWord that represents the
   *  tag by having the wildcard word ANY_WORD_INT and  the tag in the
   *  reduced tag space.
   *  The argument is in terms of the full tag space; internally this
   *  function maps to the reduced space.
   *  @param tag short representation of tag in full tag space
   *  @return an IntTaggedWord in the reduced tag space
   */
  private IntTaggedWord getCachedITW(short tag) {
    // The +2 below is because -1 and -2 are used with special meanings (see IntTaggedWord).
    if (tagITWList == null) {
      tagITWList = new ArrayList<IntTaggedWord>(numTagBins + 2);
      for (int i=0; i<numTagBins + 2; i++) {
        tagITWList.add(i, null);
      }
    }
    IntTaggedWord headT = tagITWList.get(tagBin(tag) + 2);
    if (headT == null) {
      headT = new IntTaggedWord(ANY_WORD_INT, tagBin(tag));
      tagITWList.set(tagBin(tag) + 2, headT);
    }
    return headT;
  }

  /** The dependency arg is still in the full tag space.
   *
   *  @param dependency An opbserved dependency
   *  @param count The weight of the dependency
   */
  protected void expandDependency(IntDependency dependency, double count) {
    //if (Test.prunePunc && pruneTW(dependency.arg))
    //  return;
    if (dependency.head == null || dependency.arg == null) {
      return;
    }

    if (dependency.arg.word != STOP_WORD_INT) {
      expandArg(dependency, valenceBin(dependency.distance), count);
    }
    expandStop(dependency, distanceBin(dependency.distance), count, true);
  }

  private TagProjection smoothTP;
  private Index<String> smoothTPIndex;
  private static final String TP_PREFIX = ".*TP*.";

  private short tagProject(short tag) {
    if (smoothTPIndex == null) {
      smoothTPIndex = new HashIndex<String>(tagIndex);
    }
    if (tag < 0) {
      return tag;
    } else {
      String tagStr = smoothTPIndex.get(tag);
      String binStr = TP_PREFIX + smoothTP.project(tagStr);
      return (short) smoothTPIndex.indexOf(binStr, true);
    }
  }


  /** Collect counts for a non-STOP dependent.
   *  The dependency arg is still in the full tag space.
   *
   *  @param dependency A non-stop dependency
   *  @param valBinDist A binned distance
   *  @param count The weight with which to add this dependency
   */
  private void expandArg(IntDependency dependency, short valBinDist, double count) {
    IntTaggedWord headT = getCachedITW(dependency.head.tag);
    IntTaggedWord argT = getCachedITW(dependency.arg.tag);
    IntTaggedWord head = new IntTaggedWord(dependency.head.word, tagBin(dependency.head.tag)); //dependency.head;
    IntTaggedWord arg = new IntTaggedWord(dependency.arg.word, tagBin(dependency.arg.tag)); //dependency.arg;
    boolean leftHeaded = dependency.leftHeaded;

    // argCounter stores stuff in both the original and the reduced tag space???
    argCounter.incrementCount(intern(head, arg, leftHeaded, valBinDist), count);
    argCounter.incrementCount(intern(headT, arg, leftHeaded, valBinDist), count);
    argCounter.incrementCount(intern(head, argT, leftHeaded, valBinDist), count);
    argCounter.incrementCount(intern(headT, argT, leftHeaded, valBinDist), count);

    argCounter.incrementCount(intern(head, wildTW, leftHeaded, valBinDist), count);
    argCounter.incrementCount(intern(headT, wildTW, leftHeaded, valBinDist), count);

    // the WILD head stats are always directionless and not useDistance!
    argCounter.incrementCount(intern(wildTW, arg, false, (short) -1), count);
    argCounter.incrementCount(intern(wildTW, argT, false, (short) -1), count);

    if (useSmoothTagProjection) {
      // added stuff to do more smoothing.  CDM Jan 2007
      IntTaggedWord headP = new IntTaggedWord(dependency.head.word, tagProject(dependency.head.tag));
      IntTaggedWord headTP = new IntTaggedWord(ANY_WORD_INT, tagProject(dependency.head.tag));
      IntTaggedWord argP = new IntTaggedWord(dependency.arg.word, tagProject(dependency.arg.tag));
      IntTaggedWord argTP = new IntTaggedWord(ANY_WORD_INT, tagProject(dependency.arg.tag));

      argCounter.incrementCount(intern(headP, argP, leftHeaded, valBinDist), count);
      argCounter.incrementCount(intern(headTP, argP, leftHeaded, valBinDist), count);
      argCounter.incrementCount(intern(headP, argTP, leftHeaded, valBinDist), count);
      argCounter.incrementCount(intern(headTP, argTP, leftHeaded, valBinDist), count);

      argCounter.incrementCount(intern(headP, wildTW, leftHeaded, valBinDist), count);
      argCounter.incrementCount(intern(headTP, wildTW, leftHeaded, valBinDist), count);

      // the WILD head stats are always directionless and not useDistance!
      argCounter.incrementCount(intern(wildTW, argP, false, (short) -1), count);
      argCounter.incrementCount(intern(wildTW, argTP, false, (short) -1), count);
      argCounter.incrementCount(intern(wildTW, new IntTaggedWord(dependency.head.word, ANY_TAG_INT), false, (short) -1), count);
    }
    numWordTokens++;
  }

  private void expandStop(IntDependency dependency, short distBinDist, double count, boolean wildForStop) {
    IntTaggedWord headT = getCachedITW(dependency.head.tag);
    IntTaggedWord head = new IntTaggedWord(dependency.head.word, tagBin(dependency.head.tag)); //dependency.head;
    IntTaggedWord arg = new IntTaggedWord(dependency.arg.word, tagBin(dependency.arg.tag));//dependency.arg;

    boolean leftHeaded = dependency.leftHeaded;

    if (arg.word == STOP_WORD_INT) {
      stopCounter.incrementCount(intern(head, arg, leftHeaded, distBinDist), count);
      stopCounter.incrementCount(intern(headT, arg, leftHeaded, distBinDist), count);
    }
    if (wildForStop || arg.word != STOP_WORD_INT) {
      stopCounter.incrementCount(intern(head, wildTW, leftHeaded, distBinDist), count);
      stopCounter.incrementCount(intern(headT, wildTW, leftHeaded, distBinDist), count);
    }
  }

  public double countHistory(IntDependency dependency) {
    IntDependency temp = new IntDependency(dependency.head.word, tagBin(dependency.head.tag), wildTW.word, wildTW.tag, dependency.leftHeaded, valenceBin(dependency.distance));

    return argCounter.getCount(temp);
  }

  /** Score a tag binned dependency. */
  public double scoreTB(IntDependency dependency) {
    return op.testOptions.depWeight * Math.log(probTB(dependency));
  }

  private static final boolean verbose = false;

  protected static final double MIN_PROBABILITY = 1e-40;

  /** Calculate the probability of a dependency as a real probability between
   *  0 and 1 inclusive.
   *  @param dependency The dependency for which the probability is to be
   *       calculated.   The tags in this dependency are in the reduced
   *       TagProjection space.
   *  @return The probability of the dependency
   */
  protected double probTB(IntDependency dependency) {
    if (verbose) {
      // System.out.println("tagIndex: " + tagIndex);
      System.err.println("Generating " + dependency);
    }

    boolean leftHeaded = dependency.leftHeaded && directional;

    int hW = dependency.head.word;
    int aW = dependency.arg.word;
    short hT = dependency.head.tag;
    short aT = dependency.arg.tag;

    IntTaggedWord aTW = dependency.arg;
    IntTaggedWord hTW = dependency.head;

    boolean isRoot = rootTW(dependency.head);
    double pb_stop_hTWds;
    if (isRoot) {
      pb_stop_hTWds = 0.0;
    } else {
      pb_stop_hTWds = getStopProb(dependency);
    }

    if (dependency.arg.word == STOP_WORD_INT) {
      // did we generate stop?
      return pb_stop_hTWds;
    }

    double pb_go_hTWds = 1.0 - pb_stop_hTWds;

    // generate the argument

    short binDistance = valenceBin(dependency.distance);

    // KEY:
    // c_     count of (read as joint count of first and second)
    // p_     MLE prob of (or MAP if useSmoothTagProjection)
    // pb_    MAP prob of (read as prob of first given second thing)
    // a      arg
    // h      head
    // T      tag
    // PT     projected tag
    // W      word
    // d      direction
    // ds     distance (implicit: there when direction is mentioned!)

    IntTaggedWord anyHead = new IntTaggedWord(ANY_WORD_INT, dependency.head.tag);
    IntTaggedWord anyArg = new IntTaggedWord(ANY_WORD_INT, dependency.arg.tag);
    IntTaggedWord anyTagArg = new IntTaggedWord(dependency.arg.word, ANY_TAG_INT);

    IntDependency temp = new IntDependency(dependency.head, dependency.arg, leftHeaded, binDistance);
    double c_aTW_hTWd = argCounter.getCount(temp);
    temp = new IntDependency(dependency.head, anyArg, leftHeaded, binDistance);
    double c_aT_hTWd = argCounter.getCount(temp);
    temp = new IntDependency(dependency.head, wildTW, leftHeaded, binDistance);
    double c_hTWd = argCounter.getCount(temp);

    temp = new IntDependency(anyHead, dependency.arg, leftHeaded, binDistance);
    double c_aTW_hTd = argCounter.getCount(temp);
    temp = new IntDependency(anyHead, anyArg, leftHeaded, binDistance);
    double c_aT_hTd = argCounter.getCount(temp);
    temp = new IntDependency(anyHead, wildTW, leftHeaded, binDistance);
    double c_hTd = argCounter.getCount(temp);

    // for smooth tag projection
    short aPT = Short.MIN_VALUE;
    double c_aPTW_hPTd = Double.NaN;
    double c_aPT_hPTd = Double.NaN;
    double c_hPTd = Double.NaN;
    double c_aPTW_aPT = Double.NaN;
    double c_aPT = Double.NaN;

    if (useSmoothTagProjection) {
      aPT = tagProject(dependency.arg.tag);
      short hPT = tagProject(dependency.head.tag);
      
      IntTaggedWord projectedArg = new IntTaggedWord(dependency.arg.word, aPT);
      IntTaggedWord projectedAnyHead = new IntTaggedWord(ANY_WORD_INT, hPT);
      IntTaggedWord projectedAnyArg = new IntTaggedWord(ANY_WORD_INT, aPT);

      temp = new IntDependency(projectedAnyHead, projectedArg, leftHeaded, binDistance);
      c_aPTW_hPTd = argCounter.getCount(temp);
      temp = new IntDependency(projectedAnyHead, projectedAnyArg, leftHeaded, binDistance);
      c_aPT_hPTd = argCounter.getCount(temp);
      temp = new IntDependency(projectedAnyHead, wildTW, leftHeaded, binDistance);
      c_hPTd = argCounter.getCount(temp);

      temp = new IntDependency(wildTW, projectedArg, false, ANY_DISTANCE_INT);
      c_aPTW_aPT = argCounter.getCount(temp);
      temp = new IntDependency(wildTW, projectedAnyArg, false, ANY_DISTANCE_INT);
      c_aPT = argCounter.getCount(temp);
    }

    // wild head is always directionless and no use distance
    temp = new IntDependency(wildTW, dependency.arg, false, ANY_DISTANCE_INT);
    double c_aTW = argCounter.getCount(temp);
    temp = new IntDependency(wildTW, anyArg, false, ANY_DISTANCE_INT);
    double c_aT = argCounter.getCount(temp);
    temp = new IntDependency(wildTW, anyTagArg, false, ANY_DISTANCE_INT);
    double c_aW = argCounter.getCount(temp);

    // do the Bayesian magic
    // MLE probs
    double p_aTW_hTd;
    double p_aT_hTd;
    double p_aTW_aT;
    double p_aW;
    double p_aPTW_aPT;
    double p_aPTW_hPTd;
    double p_aPT_hPTd;

    // backoffs either mle or themselves bayesian smoothed depending on useSmoothTagProjection
    if (useSmoothTagProjection) {
      if (useUnigramWordSmoothing) {
        p_aW = c_aW > 0.0 ? (c_aW / numWordTokens) : 1.0;  // NEED this 1.0 for unknown words!!!
        p_aPTW_aPT = (c_aPTW_aPT + smooth_aPTW_aPT * p_aW) / (c_aPT + smooth_aPTW_aPT);
      } else {
        p_aPTW_aPT = c_aPTW_aPT > 0.0 ? (c_aPTW_aPT / c_aPT) : 1.0;  // NEED this 1.0 for unknown words!!!
      }
      p_aTW_aT = (c_aTW + smooth_aTW_aT * p_aPTW_aPT) / (c_aT + smooth_aTW_aT);

      p_aPTW_hPTd = c_hPTd > 0.0 ? (c_aPTW_hPTd / c_hPTd): 0.0;
      p_aTW_hTd = (c_aTW_hTd + smooth_aTW_hTd * p_aPTW_hPTd) / (c_hTd + smooth_aTW_hTd);

      p_aPT_hPTd = c_hPTd > 0.0 ? (c_aPT_hPTd / c_hPTd) : 0.0;
      p_aT_hTd = (c_aT_hTd + smooth_aT_hTd * p_aPT_hPTd) / (c_hTd + smooth_aT_hTd);
    } else {
      // here word generation isn't smoothed - can't get previously unseen word with tag.  Ugh.
      if (op.testOptions.useLexiconToScoreDependencyPwGt) {
        // We don't know the position.  Now -1 means average over 0 and 1.
        p_aTW_aT = dependency.leftHeaded ? Math.exp(lex.score(dependency.arg, 1, wordIndex.get(dependency.arg.word), null)): Math.exp(lex.score(dependency.arg, -1, wordIndex.get(dependency.arg.word), null));
        // double oldScore = c_aTW > 0.0 ? (c_aTW / c_aT) : 1.0;
        // if (oldScore == 1.0) {
        //  System.err.println("#### arg=" + dependency.arg + " score=" + p_aTW_aT +
        //                      " oldScore=" + oldScore + " c_aTW=" + c_aTW + " c_aW=" + c_aW);
        // }
      } else {
        p_aTW_aT = c_aTW > 0.0 ? (c_aTW / c_aT) : 1.0;
      }
      p_aTW_hTd = c_hTd > 0.0 ? (c_aTW_hTd / c_hTd) : 0.0;
      p_aT_hTd = c_hTd > 0.0 ? (c_aT_hTd / c_hTd) : 0.0;
    }

    double pb_aTW_hTWd = (c_aTW_hTWd + smooth_aTW_hTWd * p_aTW_hTd) / (c_hTWd + smooth_aTW_hTWd);
    double pb_aT_hTWd = (c_aT_hTWd + smooth_aT_hTWd * p_aT_hTd) / (c_hTWd + smooth_aT_hTWd);

    double score = (interp * pb_aTW_hTWd + (1.0 - interp) * p_aTW_aT * pb_aT_hTWd) * pb_go_hTWds;

    if (verbose) {
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMaximumFractionDigits(2);
      if (useSmoothTagProjection) {
        if (useUnigramWordSmoothing) {
          System.err.println("  c_aW=" + c_aW + ", numWordTokens=" + numWordTokens + ", p(aW)=" + nf.format(p_aW));
        }
        System.err.println("  c_aPTW_aPT=" + c_aPTW_aPT + ", c_aPT=" + c_aPT + ", smooth_aPTW_aPT=" + smooth_aPTW_aPT + ", p(aPTW|aPT)=" + nf.format(p_aPTW_aPT));
      }
      System.err.println("  c_aTW=" + c_aTW + ", c_aT=" + c_aT + ", smooth_aTW_aT=" + smooth_aTW_aT +", ## p(aTW|aT)=" + nf.format(p_aTW_aT));

      if (useSmoothTagProjection) {
        System.err.println("  c_aPTW_hPTd=" + c_aPTW_hPTd + ", c_hPTd=" + c_hPTd + ", p(aPTW|hPTd)=" + nf.format(p_aPTW_hPTd));
      }
      System.err.println("  c_aTW_hTd=" + c_aTW_hTd + ", c_hTd=" + c_hTd + ", smooth_aTW_hTd=" + smooth_aTW_hTd +", p(aTW|hTd)=" + nf.format(p_aTW_hTd));

      if (useSmoothTagProjection) {
        System.err.println("  c_aPT_hPTd=" + c_aPT_hPTd + ", c_hPTd=" + c_hPTd + ", p(aPT|hPTd)=" + nf.format(p_aPT_hPTd));
      }
      System.err.println("  c_aT_hTd=" + c_aT_hTd + ", c_hTd=" + c_hTd + ", smooth_aT_hTd=" + smooth_aT_hTd +", p(aT|hTd)=" + nf.format(p_aT_hTd));

      System.err.println("  c_aTW_hTWd=" + c_aTW_hTWd + ", c_hTWd=" + c_hTWd + ", smooth_aTW_hTWd=" + smooth_aTW_hTWd +", ## p(aTW|hTWd)=" + nf.format(pb_aTW_hTWd));
      System.err.println("  c_aT_hTWd=" + c_aT_hTWd + ", c_hTWd=" + c_hTWd + ", smooth_aT_hTWd=" + smooth_aT_hTWd +", ## p(aT|hTWd)=" + nf.format(pb_aT_hTWd));

      System.err.println("  interp=" + interp + ", prescore=" + nf.format(interp * pb_aTW_hTWd + (1.0 - interp) * p_aTW_aT * pb_aT_hTWd) +
                         ", P(go|hTWds)=" + nf.format(pb_go_hTWds) + ", score=" + nf.format(score));
    }

    if (op.testOptions.prunePunc && pruneTW(aTW)) {
      return 1.0;
    }

    if (Double.isNaN(score)) {
      score = 0.0;
    }

    //if (op.testOptions.rightBonus && ! dependency.leftHeaded)
    //  score -= 0.2;

    if (score < MIN_PROBABILITY) {
      score = 0.0;
    }

    return score;
  }


  /** Return the probability (as a real number between 0 and 1) of stopping
   *  rather than generating another argument at this position.
   *  @param dependency The dependency used as the basis for stopping on.
   *     Tags are assumed to be in the TagProjection space.
   *  @return The probability of generating this stop probability
   */
  protected double getStopProb(IntDependency dependency) {
    short binDistance = distanceBin(dependency.distance);
    IntTaggedWord unknownHead = new IntTaggedWord(-1, dependency.head.tag);
    IntTaggedWord anyHead = new IntTaggedWord(ANY_WORD_INT, dependency.head.tag);

    IntDependency temp = new IntDependency(dependency.head, stopTW, dependency.leftHeaded, binDistance);
    double c_stop_hTWds = stopCounter.getCount(temp);
    temp = new IntDependency(unknownHead, stopTW, dependency.leftHeaded, binDistance);
    double c_stop_hTds = stopCounter.getCount(temp);
    temp = new IntDependency(dependency.head, wildTW, dependency.leftHeaded, binDistance);
    double c_hTWds = stopCounter.getCount(temp);
    temp = new IntDependency(anyHead, wildTW, dependency.leftHeaded, binDistance);
    double c_hTds = stopCounter.getCount(temp);

    double p_stop_hTds = (c_hTds > 0.0 ? c_stop_hTds / c_hTds : 1.0);

    double pb_stop_hTWds = (c_stop_hTWds + smooth_stop * p_stop_hTds) / (c_hTWds + smooth_stop);

    if (verbose) {
      System.out.println("  c_stop_hTWds: " + c_stop_hTWds + "; c_hTWds: " + c_hTWds + "; c_stop_hTds: " + c_stop_hTds + "; c_hTds: " + c_hTds);
      System.out.println("  Generate STOP prob: " + pb_stop_hTWds);
    }
    return pb_stop_hTWds;
  }

  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
//    System.err.println("Before decompression:");
//    System.err.println("arg size: " + argCounter.size() + "  total: " + argCounter.totalCount());
//    System.err.println("stop size: " + stopCounter.size() + "  total: " + stopCounter.totalCount());

    ClassicCounter<IntDependency> compressedArgC = argCounter;
    argCounter = new ClassicCounter<IntDependency>();
    ClassicCounter<IntDependency> compressedStopC = stopCounter;
    stopCounter = new ClassicCounter<IntDependency>();
    for (IntDependency d : compressedArgC.keySet()) {
      double count = compressedArgC.getCount(d);
      expandArg(d, d.distance, count);
    }

    for (IntDependency d : compressedStopC.keySet()) {
      double count = compressedStopC.getCount(d);
      expandStop(d, d.distance, count, false);
    }

//    System.err.println("After decompression:");
//    System.err.println("arg size: " + argCounter.size() + "  total: " + argCounter.totalCount());
//    System.err.println("stop size: " + stopCounter.size() + "  total: " + stopCounter.totalCount());

    expandDependencyMap = null;
  }

  private void writeObject(ObjectOutputStream stream) throws IOException {
//    System.err.println("\nBefore compression:");
//    System.err.println("arg size: " + argCounter.size() + "  total: " + argCounter.totalCount());
//    System.err.println("stop size: " + stopCounter.size() + "  total: " + stopCounter.totalCount());

    ClassicCounter<IntDependency> fullArgCounter = argCounter;
    argCounter = new ClassicCounter<IntDependency>();
    for (IntDependency dependency : fullArgCounter.keySet()) {
      if (dependency.head != wildTW && dependency.arg != wildTW &&
              dependency.head.word != -1 && dependency.arg.word != -1) {
        argCounter.incrementCount(dependency, fullArgCounter.getCount(dependency));
      }
    }

    ClassicCounter<IntDependency> fullStopCounter = stopCounter;
    stopCounter = new ClassicCounter<IntDependency>();
    for (IntDependency dependency : fullStopCounter.keySet()) {
      if (dependency.head.word != -1) {
        stopCounter.incrementCount(dependency, fullStopCounter.getCount(dependency));
      }
    }

//    System.err.println("After compression:");
//    System.err.println("arg size: " + argCounter.size() + "  total: " + argCounter.totalCount());
//    System.err.println("stop size: " + stopCounter.size() + "  total: " + stopCounter.totalCount());

    stream.defaultWriteObject();

    argCounter = fullArgCounter;
    stopCounter = fullStopCounter;
  }

  /**
   * Populates data in this DependencyGrammar from the character stream
   * given by the Reader r.
   */
  @Override
  public void readData(BufferedReader in) throws IOException {
    final String LEFT = "left";
    int lineNum = 1;
    // all lines have one rule per line
    boolean doingStop = false;

    for (String line = in.readLine(); line != null && line.length() > 0; line = in.readLine()) {
      try {
        if (line.equals("BEGIN_STOP")) {
          doingStop = true;
          continue;
        }
        String[] fields = StringUtils.splitOnCharWithQuoting(line, ' ', '\"', '\\'); // split on spaces, quote with doublequote, and escape with backslash
        //        System.out.println("fields:\n" + fields[0] + "\n" + fields[1] + "\n" + fields[2] + "\n" + fields[3] + "\n" + fields[4] + "\n" + fields[5]);

        
        short distance = (short)Integer.parseInt(fields[4]);
        IntTaggedWord tempHead = new IntTaggedWord(fields[0], '/', wordIndex, tagIndex);
        IntTaggedWord tempArg = new IntTaggedWord(fields[2], '/', wordIndex, tagIndex);
        IntDependency tempDependency = new IntDependency(tempHead, tempArg, fields[3].equals(LEFT), distance);

        double count = Double.parseDouble(fields[5]);
        if (doingStop) {
          expandStop(tempDependency, distance, count, false);
        } else {
          expandArg(tempDependency, distance, count);
        }
      } catch (Exception e) {
        IOException ioe = new IOException("Error on line " + lineNum + ": " + line);
        ioe.initCause(e);
        throw ioe;
      }
      //      System.out.println("read line " + lineNum + ": " + line);
      lineNum++;
    }
  }

  /**
   * Writes out data from this Object to the Writer w.
   */
  @Override
  public void writeData(PrintWriter out) throws IOException {
    // all lines have one rule per line

    for (IntDependency dependency : argCounter.keySet()) {
      if (dependency.head != wildTW && dependency.arg != wildTW &&
              dependency.head.word != -1 && dependency.arg.word != -1) {
        double count = argCounter.getCount(dependency);
        out.println(dependency.toString(wordIndex, tagIndex) + " " + count);
      }
    }

    out.println("BEGIN_STOP");

    for (IntDependency dependency : stopCounter.keySet()) {
      if (dependency.head.word != -1) {
        double count = stopCounter.getCount(dependency);
        out.println(dependency.toString(wordIndex, tagIndex) + " " + count);
      }
    }

    out.flush();
  }

  private static final long serialVersionUID = 1L;

} // end class DependencyGrammar

