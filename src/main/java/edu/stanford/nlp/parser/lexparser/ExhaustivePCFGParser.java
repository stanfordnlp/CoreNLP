// Stanford Parser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002, 2003, 2004, 2005 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    parser-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/lex-parser.html

package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasContext;
import edu.stanford.nlp.ling.HasOffset;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.parser.common.ParserAnnotations;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;
import java.util.regex.Matcher;

/** An exhaustive generalized CKY PCFG parser.
 *  Fairly carefully optimized to be fast.
 *
 *  If reusing this object for multiple parses, remember to correctly
 *  set any options such as the constraints field.
 *
 *  @author Dan Klein
 *  @author Christopher Manning (I seem to maintain it....)
 *  @author Jenny Finkel (N-best and sampling code, former from Liang/Chiang)
 */
public class ExhaustivePCFGParser implements Scorer, KBestViterbiParser  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ExhaustivePCFGParser.class);

  // public static long insideTime = 0;  // for profiling
  // public static long outsideTime = 0;

  protected final String goalStr;
  protected final Index<String> stateIndex;
  protected final Index<String> wordIndex;
  protected final Index<String> tagIndex;

  protected final TreeFactory tf;

  protected final BinaryGrammar bg;
  protected final UnaryGrammar ug;
  protected final Lexicon lex;
  protected final Options op;
  protected final TreebankLanguagePack tlp;

  protected OutsideRuleFilter orf;

  // inside scores
  protected float[][][] iScore;  // start idx, end idx, state -> logProb (ragged; null for end <= start)
  // outside scores
  protected float[][][] oScore;  // start idx, end idx, state -> logProb
  protected float bestScore;

  protected int[][][] wordsInSpan; // number of words in span with this state

  protected boolean[][] oFilteredStart; // [start][state]; only used by unused outsideRuleFilter
  protected boolean[][] oFilteredEnd; // [end][state]; only used by unused outsideRuleFilter

  protected boolean[][] iPossibleByL; // [start][state]
  protected boolean[][] iPossibleByR; // [end][state]
  protected boolean[][] oPossibleByL; // [start][state]
  protected boolean[][] oPossibleByR; // [end][state]

  protected int[] words;  // words of sentence being parsed as word Numberer ints
  private int[] beginOffsets;
  private int[] endOffsets;
  private CoreLabel[] originalCoreLabels;
  private HasTag[] originalTags;
  protected int length; // one larger than true length of sentence; includes boundary symbol in count
  protected boolean[][] tags;
  protected int myMaxLength = -0xDEADBEEF;

  protected final int numStates;
  protected int arraySize = 0;

  /**
   * When you want to force the parser to parse a particular
   * subsequence into a particular state.  Parses will only be made
   * where there is a constituent over the given span which matches
   * (as regular expression) the state Pattern given.  See the
   * documentation of the ParserConstraint class for information on
   * specifying a ParserConstraint.
   *
   * Implementation note: It would be cleaner to make this a
   * Collections.emptyList, but that actually significantly slows down
   * the processing in the case of empty lists.  Checking for null
   * saves quite a bit of time.
   */
  protected List<ParserConstraint> constraints = null;

  private CoreLabel getCoreLabel(int labelIndex) {
    if (originalCoreLabels[labelIndex] != null) {
      CoreLabel terminalLabel = originalCoreLabels[labelIndex];
      if (terminalLabel.value() == null && terminalLabel.word() != null) {
        terminalLabel.setValue(terminalLabel.word());
      }
      return terminalLabel;
    }

    String wordStr = wordIndex.get(words[labelIndex]);
    CoreLabel terminalLabel = new CoreLabel();
    terminalLabel.setValue(wordStr);
    terminalLabel.setWord(wordStr);
    terminalLabel.setBeginPosition(beginOffsets[labelIndex]);
    terminalLabel.setEndPosition(endOffsets[labelIndex]);
    if (originalTags[labelIndex] != null) {
      terminalLabel.setTag(originalTags[labelIndex].tag());
    }
    return terminalLabel;
  }

  @Override
  public double oScore(Edge edge) {
    double oS = oScore[edge.start][edge.end][edge.state];
    if (op.testOptions.pcfgThreshold) {
      double iS = iScore[edge.start][edge.end][edge.state];
      if (iS + oS - bestScore < op.testOptions.pcfgThresholdValue) {
        return Double.NEGATIVE_INFINITY;
      }
    }
    return oS;
  }

  @Override
  public double iScore(Edge edge) {
    return iScore[edge.start][edge.end][edge.state];
  }

  @Override
  public boolean oPossible(Hook hook) {
    return (hook.isPreHook() ? oPossibleByR[hook.end][hook.state] : oPossibleByL[hook.start][hook.state]);
  }

  @Override
  public boolean iPossible(Hook hook) {
    return (hook.isPreHook() ? iPossibleByR[hook.start][hook.subState] : iPossibleByL[hook.end][hook.subState]);
  }


  public boolean oPossibleL(int state, int start) {
    return oPossibleByL[start][state];
  }

  public boolean oPossibleR(int state, int end) {
    return oPossibleByR[end][state];
  }

  public boolean iPossibleL(int state, int start) {
    return iPossibleByL[start][state];
  }

  public boolean iPossibleR(int state, int end) {
    return iPossibleByR[end][state];
  }

  protected void buildOFilter() {
    oFilteredStart = new boolean[length][numStates];
    oFilteredEnd = new boolean[length + 1][numStates];
    orf.init();
    for (int start = 0; start < length; start++) {
      orf.leftAccepting(oFilteredStart[start]);
      orf.advanceRight(tags[start]);
    }
    for (int end = length; end > 0; end--) {
      orf.rightAccepting(oFilteredEnd[end]);
      orf.advanceLeft(tags[end - 1]);
    }
  }


  public double validateBinarizedTree(Tree tree, int start) {
    if (tree.isLeaf()) {
      return 0.0;
    }
    float epsilon = 0.0001f;
    if (tree.isPreTerminal()) {
      String wordStr = tree.children()[0].label().value();
      int tag = tagIndex.indexOf(tree.label().value());
      int word = wordIndex.indexOf(wordStr);
      IntTaggedWord iTW = new IntTaggedWord(word, tag);
      float score = lex.score(iTW, start, wordStr, null);
      float bound = iScore[start][start + 1][stateIndex.indexOf(tree.label().value())];
      if (score > bound + epsilon) {
        System.out.println("Invalid tagging:");
        System.out.println("  Tag: " + tree.label().value());
        System.out.println("  Word: " + tree.children()[0].label().value());
        System.out.println("  Score: " + score);
        System.out.println("  Bound: " + bound);
      }
      return score;
    }
    int parent = stateIndex.indexOf(tree.label().value());
    int firstChild = stateIndex.indexOf(tree.children()[0].label().value());
    if (tree.numChildren() == 1) {
      UnaryRule ur = new UnaryRule(parent, firstChild);
      double score = SloppyMath.max(ug.scoreRule(ur), -10000.0) + validateBinarizedTree(tree.children()[0], start);
      double bound = iScore[start][start + tree.yield().size()][parent];
      if (score > bound + epsilon) {
        System.out.println("Invalid unary:");
        System.out.println("  Parent: " + tree.label().value());
        System.out.println("  Child: " + tree.children()[0].label().value());
        System.out.println("  Start: " + start);
        System.out.println("  End: " + (start + tree.yield().size()));
        System.out.println("  Score: " + score);
        System.out.println("  Bound: " + bound);
      }
      return score;
    }
    int secondChild = stateIndex.indexOf(tree.children()[1].label().value());
    BinaryRule br = new BinaryRule(parent, firstChild, secondChild);
    double score = SloppyMath.max(bg.scoreRule(br), -10000.0) + validateBinarizedTree(tree.children()[0], start) + validateBinarizedTree(tree.children()[1], start + tree.children()[0].yield().size());
    double bound = iScore[start][start + tree.yield().size()][parent];
    if (score > bound + epsilon) {
      System.out.println("Invalid binary:");
      System.out.println("  Parent: " + tree.label().value());
      System.out.println("  LChild: " + tree.children()[0].label().value());
      System.out.println("  RChild: " + tree.children()[1].label().value());
      System.out.println("  Start: " + start);
      System.out.println("  End: " + (start + tree.yield().size()));
      System.out.println("  Score: " + score);
      System.out.println("  Bound: " + bound);
    }
    return score;
  }

  // needs to be set up so that uses same Train options...
  public Tree scoreNonBinarizedTree(Tree tree) {
    TreeAnnotatorAndBinarizer binarizer = new TreeAnnotatorAndBinarizer(op.tlpParams, op.forceCNF, !op.trainOptions.outsideFactor(), true, op);
    tree = binarizer.transformTree(tree);
    scoreBinarizedTree(tree, 0);
    return op.tlpParams.subcategoryStripper().transformTree(new Debinarizer(op.forceCNF).transformTree(tree));
    //    return debinarizer.transformTree(t);
  }

  //
  public double scoreBinarizedTree(Tree tree, int start) {
    if (tree.isLeaf()) {
      return 0.0;
    }
    if (tree.isPreTerminal()) {
      String wordStr = tree.children()[0].label().value();
      int tag = tagIndex.indexOf(tree.label().value());
      int word = wordIndex.indexOf(wordStr);
      IntTaggedWord iTW = new IntTaggedWord(word, tag);
      // if (lex.score(iTW,(leftmost ? 0 : 1)) == Double.NEGATIVE_INFINITY) {
      //   System.out.println("NO SCORE FOR: "+iTW);
      // }
      float score = lex.score(iTW, start, wordStr, null);
      tree.setScore(score);
      return score;
    }
    int parent = stateIndex.indexOf(tree.label().value());
    int firstChild = stateIndex.indexOf(tree.children()[0].label().value());
    if (tree.numChildren() == 1) {
      UnaryRule ur = new UnaryRule(parent, firstChild);
      //+ DEBUG
      // if (ug.scoreRule(ur) < -10000) {
      //        System.out.println("Grammar doesn't have rule: " + ur);
      // }
      //      return SloppyMath.max(ug.scoreRule(ur), -10000.0) + scoreBinarizedTree(tree.children()[0], leftmost);
      double score = ug.scoreRule(ur) + scoreBinarizedTree(tree.children()[0], start);
      tree.setScore(score);
      return score;
    }
    int secondChild = stateIndex.indexOf(tree.children()[1].label().value());
    BinaryRule br = new BinaryRule(parent, firstChild, secondChild);
    //+ DEBUG
    // if (bg.scoreRule(br) < -10000) {
    //  System.out.println("Grammar doesn't have rule: " + br);
    // }
    //    return SloppyMath.max(bg.scoreRule(br), -10000.0) +
    //            scoreBinarizedTree(tree.children()[0], leftmost) +
    //            scoreBinarizedTree(tree.children()[1], false);
    double score = bg.scoreRule(br) + scoreBinarizedTree(tree.children()[0], start) + scoreBinarizedTree(tree.children()[1], start + tree.children()[0].yield().size());
    tree.setScore(score);
    return score;
  }


  static final boolean spillGuts = false;
  static final boolean dumpTagging = false;
  private long time = System.currentTimeMillis();

  protected void tick(String str) {
    long time2 = System.currentTimeMillis();
    long diff = time2 - time;
    time = time2;
    log.info("done.  " + diff + "\n" + str);
  }

  protected boolean floodTags = false;
  protected List sentence = null;
  protected Lattice lr = null;

  protected int[][] narrowLExtent; // = null; // [end][state]: the rightmost left extent of state s ending at position i
  protected int[][] wideLExtent; // = null; // [end][state] the leftmost left extent of state s ending at position i
  protected int[][] narrowRExtent; // = null; // [start][state]: the leftmost right extent of state s starting at position i
  protected int[][] wideRExtent; // = null; // [start][state] the rightmost right extent of state s starting at position i

  protected final boolean[] isTag; // this records whether grammar states (stateIndex) correspond to POS tags


  public boolean parse(List<? extends HasWord> sentence) {
    lr = null; // better nullPointer exception than silent error
    //System.out.println("is it a taggedword?" + (sentence.get(0) instanceof TaggedWord)); //debugging
    if (sentence != this.sentence) {
      this.sentence = sentence;
      floodTags = false;
    }
    if (op.testOptions.verbose) {
      Timing.tick("Starting pcfg parse.");
    }
    if (spillGuts) {
      tick("Starting PCFG parse...");
    }
    length = sentence.size();
    if (length > arraySize) {
      considerCreatingArrays(length);
    }
    int goal = stateIndex.indexOf(goalStr);
    if (op.testOptions.verbose) {
      // System.out.println(numStates + " states, " + goal + " is the goal state.");
      // log.info(new ArrayList(ug.coreRules.keySet()));
      log.info("Initializing PCFG...");
    }
    // map input words to words array (wordIndex ints)
    words = new int[length];
    beginOffsets = new int[length];
    endOffsets = new int[length];
    originalCoreLabels = new CoreLabel[length];
    originalTags = new HasTag[length];
    int unk = 0;
    StringBuilder unkWords = new StringBuilder("[");
    // int unkIndex = wordIndex.size();

    for (int i = 0; i < length; i++) {
      String s = sentence.get(i).word();

      if (sentence.get(i) instanceof HasOffset) {
        HasOffset word = (HasOffset) sentence.get(i);
        beginOffsets[i] = word.beginPosition();
        endOffsets[i] = word.endPosition();
      } else {
        //Storing the positions of the word interstices
        //Account for single space between words
        beginOffsets[i] = ((i == 0) ? 0 : endOffsets[i - 1] + 1);
        endOffsets[i] = beginOffsets[i] + s.length();
      }

      if (sentence.get(i) instanceof CoreLabel) {
        originalCoreLabels[i] = (CoreLabel) sentence.get(i);
      }
      if (sentence.get(i) instanceof HasTag) {
        HasTag tag = (HasTag) sentence.get(i);
        if (tag.tag() != null) {
          originalTags[i] = tag;
        }
      }

      if (op.testOptions.verbose && (!wordIndex.contains(s) || !lex.isKnown(wordIndex.indexOf(s)))) {
        unk++;
        unkWords.append(' ');
        unkWords.append(s);
        unkWords.append(" { ");
        for (int jj = 0; jj < s.length(); jj++) {
          char ch = s.charAt(jj);
          unkWords.append(Character.getType(ch)).append(" ");
        }
        unkWords.append("}");
      }
      // TODO: really, add a new word?
      //words[i] = wordIndex.indexOf(s, unkIndex);
      //if (words[i] == unkIndex) {
      //  ++unkIndex;
      //}
      words[i] = wordIndex.addToIndex(s);
      //if (wordIndex.contains(s)) {
      //  words[i] = wordIndex.indexOf(s);
      //} else {
      //  words[i] = wordIndex.indexOf(Lexicon.UNKNOWN_WORD);
      //}
    }

    // initialize inside and outside score arrays
    if (spillGuts) {
      tick("Wiping arrays...");
    }
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    for (int start = 0; start < length; start++) {
      for (int end = start + 1; end <= length; end++) {
        Arrays.fill(iScore[start][end], Float.NEGATIVE_INFINITY);
        if (op.doDep && ! op.testOptions.useFastFactored) {
          Arrays.fill(oScore[start][end], Float.NEGATIVE_INFINITY);
        }
        if (op.testOptions.lengthNormalization) {
          Arrays.fill(wordsInSpan[start][end], 1);
        }
      }
    }
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    for (int loc = 0; loc <= length; loc++) {
      Arrays.fill(narrowLExtent[loc], -1); // the rightmost left with state s ending at i that we can get is the beginning
      Arrays.fill(wideLExtent[loc], length + 1); // the leftmost left with state s ending at i that we can get is the end
    }
    for (int loc = 0; loc < length; loc++) {
      Arrays.fill(narrowRExtent[loc], length + 1); // the leftmost right with state s starting at i that we can get is the end
      Arrays.fill(wideRExtent[loc], -1); // the rightmost right with state s starting at i that we can get is the beginning
    }
    // int puncTag = stateIndex.indexOf(".");
    // boolean lastIsPunc = false;
    if (op.testOptions.verbose) {
      Timing.tick("done.");
      unkWords.append(" ]");
      op.tlpParams.pw(System.err).println("Unknown words: " + unk + " " + unkWords);
      log.info("Starting filters...");
    }
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    // do tags
    if (spillGuts) {
      tick("Tagging...");
    }
    initializeChart(sentence);
    //if (op.testOptions.outsideFilter)
    // buildOFilter();
    if (op.testOptions.verbose) {
      Timing.tick("done.");
      log.info("Starting insides...");
    }
    // do the inside probabilities
    doInsideScores();
    if (op.testOptions.verbose) {
      // insideTime += Timing.tick("done.");
      Timing.tick("done.");
      System.out.println("PCFG parsing " + length + " words (incl. stop): insideScore = " + iScore[0][length][goal]);
    }
    bestScore = iScore[0][length][goal];
    boolean succeeded = hasParse();
    if (op.testOptions.doRecovery && !succeeded && !floodTags) {
      floodTags = true; // sentence will try to reparse
      // ms: disabled message. this is annoying and it doesn't really provide much information
      //log.info("Trying recovery parse...");
      return parse(sentence);
    }
    if ( ! op.doDep || op.testOptions.useFastFactored) {
      return succeeded;
    }
    if (op.testOptions.verbose) {
      log.info("Starting outsides...");
    }
    // outside scores
    oScore[0][length][goal] = 0.0f;
    doOutsideScores();
    //System.out.println("State rate: "+((int)(1000*ohits/otries))/10.0);
    //System.out.println("Traversals: "+ohits);
    if (op.testOptions.verbose) {
      // outsideTime += Timing.tick("Done.");
      Timing.tick("done.");
    }

    if (op.doDep) {
      initializePossibles();
    }

    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }

    return succeeded;
  }

  public boolean parse(HTKLatticeReader lr) {
    //TODO wsg 20-jan-2010
    // There are presently 2 issues with HTK lattice parsing:
    //   (1) The initializeChart() method present in rev. 19820 did not properly initialize
    //         lattices (or sub-lattices) like this (where A,B,C are nodes, and NN is the POS tag arc label):
    //
    //              --NN--> B --NN--
    //             /                \
    //            A ------NN-------> C
    //
    //   (2) extractBestParse() was not implemented properly.
    //
    //   To re-implement support for HTKLatticeReader it is necessary to create an interface
    //   for the two different lattice implementations and then modify initializeChart() and
    //   extractBestParse() as appropriate. Another solution would be to duplicate these two
    //   methods and make the necessary changes for HTKLatticeReader. In both cases, the
    //   acoustic model score provided by the HTK lattices should be included in the weighting.
    //
    //   Note that I never actually tested HTKLatticeReader, so I am uncertain if this facility
    //   actually worked in the first place.
    //
    System.err.printf("%s: HTK lattice parsing presently disabled.\n", this.getClass().getName());
    return false;
  }

  public boolean parse(Lattice lr) {
    sentence = null; // better nullPointer exception than silent error
    if (lr != this.lr) {
      this.lr = lr;
      floodTags = false;
    }

    if (op.testOptions.verbose)
      Timing.tick("Doing lattice PCFG parse...");


    // The number of whitespace nodes in the lattice
    length = lr.getNumNodes() - 1; //Subtract 1 since considerCreatingArrays will add the final interstice
    if (length > arraySize)
      considerCreatingArrays(length);


    int goal = stateIndex.indexOf(goalStr);
//    if (op.testOptions.verbose) {
//      log.info("Unaries: " + ug.rules());
//      log.info("Binaries: " + bg.rules());
//      log.info("Initializing PCFG...");
//      log.info("   " + numStates + " states, " + goal + " is the goal state.");
//    }

//    log.info("Tagging states");
//    for(int i = 0; i < numStates; i++) {
//      if(isTag[i]) {
//        int tagId = Numberer.translate(stateSpace, "tags", i);
//        String tag = (String) tagNumberer.object(tagId);
//        System.err.printf(" %d: %s\n",i,tag);
//      }
//    }

    // Create a map of all words in the lattice
    //
//    int numEdges = lr.getNumEdges();
//    words = new int[numEdges];
//    offsets = new IntPair[numEdges];
//
//    int unk = 0;
//    int i = 0;
//    StringBuilder unkWords = new StringBuilder("[");
//    for (LatticeEdge edge : lr) {
//      String s = edge.word;
//      if (op.testOptions.verbose && !lex.isKnown(wordNumberer.number(s))) {
//        unk++;
//        unkWords.append(" " + s);
//      }
//      words[i++] = wordNumberer.number(s);
//    }

    for (int start = 0; start < length; start++) {
    	for (int end = start + 1; end <= length; end++) {
    		Arrays.fill(iScore[start][end], Float.NEGATIVE_INFINITY);
    		if (op.doDep) Arrays.fill(oScore[start][end], Float.NEGATIVE_INFINITY);
    	}
    }

    for (int loc = 0; loc <= length; loc++) {
      Arrays.fill(narrowLExtent[loc], -1); // the rightmost left with state s ending at i that we can get is the beginning
      Arrays.fill(wideLExtent[loc], length + 1); // the leftmost left with state s ending at i that we can get is the end
    }
    for (int loc = 0; loc < length; loc++) {
      Arrays.fill(narrowRExtent[loc], length + 1); // the leftmost right with state s starting at i that we can get is the end
      Arrays.fill(wideRExtent[loc], -1); // the rightmost right with state s starting at i that we can get is the beginning
    }

    initializeChart(lr);

    doInsideScores();
    bestScore = iScore[0][length][goal];

    if (op.testOptions.verbose) {
      Timing.tick("done.");
      log.info("PCFG " + length + " words (incl. stop) iScore " + bestScore);
    }

    boolean succeeded = hasParse();

    // Try a recovery parse
    if (!succeeded && op.testOptions.doRecovery && !floodTags) {
      floodTags = true;
      System.err.printf(this.getClass().getName() + ": Parse failed. Trying recovery parse...");
      succeeded = parse(lr);
      if(!succeeded) return false;
    }

    oScore[0][length][goal] = 0.0f;
    doOutsideScores();

    if (op.testOptions.verbose) {
      Timing.tick("done.");
    }

    if (op.doDep) {
      initializePossibles();
    }

    return succeeded;
  }

  /** These arrays are used by the factored parser (only) during edge combination.
   *  The method assumes that the iScore and oScore arrays have been initialized.
   */
  protected void initializePossibles() {
    for (int loc = 0; loc < length; loc++) {
      Arrays.fill(iPossibleByL[loc], false);
      Arrays.fill(oPossibleByL[loc], false);
    }
    for (int loc = 0; loc <= length; loc++) {
      Arrays.fill(iPossibleByR[loc], false);
      Arrays.fill(oPossibleByR[loc], false);
    }
    for (int start = 0; start < length; start++) {
      for (int end = start + 1; end <= length; end++) {
        for (int state = 0; state < numStates; state++) {
          if (iScore[start][end][state] > Float.NEGATIVE_INFINITY && oScore[start][end][state] > Float.NEGATIVE_INFINITY) {
            iPossibleByL[start][state] = true;
            iPossibleByR[end][state] = true;
            oPossibleByL[start][state] = true;
            oPossibleByR[end][state] = true;
          }
        }
      }
    }
  }

  private void doOutsideScores() {
    for (int diff = length; diff >= 1; diff--) {
      if (Thread.interrupted()) {
        throw new RuntimeInterruptedException();
      }

      for (int start = 0; start + diff <= length; start++) {
        int end = start + diff;
        // do unaries
        for (int s = 0; s < numStates; s++) {
          float oS = oScore[start][end][s];
          if (oS == Float.NEGATIVE_INFINITY) {
            continue;
          }
          UnaryRule[] rules = ug.closedRulesByParent(s);
          for (UnaryRule ur : rules) {
            float pS = ur.score;
            float tot = oS + pS;
            if (tot > oScore[start][end][ur.child] && iScore[start][end][ur.child] > Float.NEGATIVE_INFINITY) {
              oScore[start][end][ur.child] = tot;
            }
          }
        }
        // do binaries
        for (int s = 0; s < numStates; s++) {
          int min1 = narrowRExtent[start][s];
          if (end < min1) {
            continue;
          }
          BinaryRule[] rules = bg.splitRulesWithLC(s);
          for (BinaryRule br  : rules) {
            float oS = oScore[start][end][br.parent];
            if (oS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            int max1 = narrowLExtent[end][br.rightChild];
            if (max1 < min1) {
              continue;
            }
            int min = min1;
            int max = max1;
            if (max - min > 2) {
              int min2 = wideLExtent[end][br.rightChild];
              min = (min1 > min2 ? min1 : min2);
              if (max1 < min) {
                continue;
              }
              int max2 = wideRExtent[start][br.leftChild];
              max = (max1 < max2 ? max1 : max2);
              if (max < min) {
                continue;
              }
            }
            float pS = br.score;
            for (int split = min; split <= max; split++) {
              float lS = iScore[start][split][br.leftChild];
              if (lS == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float rS = iScore[split][end][br.rightChild];
              if (rS == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float totL = pS + rS + oS;
              if (totL > oScore[start][split][br.leftChild]) {
                oScore[start][split][br.leftChild] = totL;
              }
              float totR = pS + lS + oS;
              if (totR > oScore[split][end][br.rightChild]) {
                oScore[split][end][br.rightChild] = totR;
              }
            }
          }
        }
        for (int s = 0; s < numStates; s++) {
          int max1 = narrowLExtent[end][s];
          if (max1 < start) {
            continue;
          }
          BinaryRule[] rules = bg.splitRulesWithRC(s);
          for (BinaryRule br : rules) {
            float oS = oScore[start][end][br.parent];
            if (oS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            int min1 = narrowRExtent[start][br.leftChild];
            if (max1 < min1) {
              continue;
            }
            int min = min1;
            int max = max1;
            if (max - min > 2) {
              int min2 = wideLExtent[end][br.rightChild];
              min = (min1 > min2 ? min1 : min2);
              if (max1 < min) {
                continue;
              }
              int max2 = wideRExtent[start][br.leftChild];
              max = (max1 < max2 ? max1 : max2);
              if (max < min) {
                continue;
              }
            }
            float pS = br.score;
            for (int split = min; split <= max; split++) {
              float lS = iScore[start][split][br.leftChild];
              if (lS == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float rS = iScore[split][end][br.rightChild];
              if (rS == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float totL = pS + rS + oS;
              if (totL > oScore[start][split][br.leftChild]) {
                oScore[start][split][br.leftChild] = totL;
              }
              float totR = pS + lS + oS;
              if (totR > oScore[split][end][br.rightChild]) {
                oScore[split][end][br.rightChild] = totR;
              }
            }
          }
        }
        /*
          for (int s = 0; s < numStates; s++) {
          float oS = oScore[start][end][s];
          //if (iScore[start][end][s] == Float.NEGATIVE_INFINITY ||
          //             oS == Float.NEGATIVE_INFINITY)
          if (oS == Float.NEGATIVE_INFINITY)
          continue;
          BinaryRule[] rules = bg.splitRulesWithParent(s);
          for (int r=0; r<rules.length; r++) {
            BinaryRule br = rules[r];
            int min1 = narrowRExtent[start][br.leftChild];
            if (end < min1)
              continue;
            int max1 = narrowLExtent[end][br.rightChild];
            if (max1 < min1)
              continue;
            int min2 = wideLExtent[end][br.rightChild];
            int min = (min1 > min2 ? min1 : min2);
            if (max1 < min)
              continue;
            int max2 = wideRExtent[start][br.leftChild];
            int max = (max1 < max2 ? max1 : max2);
            if (max < min)
              continue;
float pS = (float) br.score;
for (int split = min; split <= max; split++) {
float lS = iScore[start][split][br.leftChild];
if (lS == Float.NEGATIVE_INFINITY)
          continue;
float rS = iScore[split][end][br.rightChild];
              if (rS == Float.NEGATIVE_INFINITY)
continue;
float totL = pS+rS+oS;
if (totL > oScore[start][split][br.leftChild]) {
oScore[start][split][br.leftChild] = totL;
}
float totR = pS+lS+oS;
if (totR > oScore[split][end][br.rightChild]) {
oScore[split][end][br.rightChild] = totR;
}
}
}
}
        */
      }
    }
  }

  /** Fills in the iScore array of each category over each span
   *  of length 2 or more.
   */
  void doInsideScores() {
    for (int diff = 2; diff <= length; diff++) {
      if (Thread.interrupted()) {
        throw new RuntimeInterruptedException();
      }

      // usually stop one short because boundary symbol only combines
      // with whole sentence span. So for 3 word sentence + boundary = 4,
      // length == 4, and do [0,2], [1,3]; [0,3]; [0,4]
      for (int start = 0; start < ((diff == length) ? 1: length - diff); start++) {
        doInsideChartCell(diff, start);
      } // for start
    } // for diff (i.e., span)
  } // end doInsideScores()


  private void doInsideChartCell(final int diff, final int start) {
    final boolean lengthNormalization = op.testOptions.lengthNormalization;
    if (spillGuts) {
      tick("Binaries for span " + diff + " start " + start + " ...");
    }
    int end = start + diff;

    final List<ParserConstraint> constraints = getConstraints();
    if (constraints != null) {
      for (ParserConstraint c : constraints) {
        if ((start > c.start && start < c.end && end > c.end) || (end > c.start && end < c.end && start < c.start)) {
          return;
        }
      }
    }

    // 2011-11-26 jdk1.6: caching/hoisting a bunch of variables gives you about 15% speed up!
    // caching this saves a bit of time in the inner loop, maybe 1.8%
    int[] narrowRExtent_start = narrowRExtent[start];
    // caching this saved 2% in the inner loop
    int[] wideRExtent_start = wideRExtent[start];
    int[] narrowLExtent_end = narrowLExtent[end];
    int[] wideLExtent_end = wideLExtent[end];
    float[][] iScore_start = iScore[start];
    float[] iScore_start_end = iScore_start[end];

    for (int leftState = 0; leftState < numStates; leftState++) {
      int narrowR = narrowRExtent_start[leftState];
      if (narrowR >= end) {  // can this left constituent leave space for a right constituent?
        continue;
      }
      BinaryRule[] leftRules = bg.splitRulesWithLC(leftState);
      //      if (spillGuts) System.out.println("Found " + leftRules.length + " left rules for state " + stateIndex.get(leftState));
      for (BinaryRule rule : leftRules) {
        int rightChild = rule.rightChild;
        int narrowL = narrowLExtent_end[rightChild];
        if (narrowL < narrowR) { // can this right constituent fit next to the left constituent?
          continue;
        }
        int min2 = wideLExtent_end[rightChild];
        int min = (narrowR > min2 ? narrowR : min2);
        // Erik Frey 2009-12-17: This is unnecessary: narrowR is <= narrowL (established in previous check) and wideLExtent[e][r] is always <= narrowLExtent[e][r] by design, so the check will never evaluate true.
        // if (min > narrowL) { // can this right constituent stretch far enough to reach the left constituent?
        //   continue;
        // }
        int max1 = wideRExtent_start[leftState];
        int max = (max1 < narrowL ? max1 : narrowL);
        if (min > max) { // can this left constituent stretch far enough to reach the right constituent?
          continue;
        }
        float pS = rule.score;
        int parentState = rule.parent;
        float oldIScore = iScore_start_end[parentState];
        float bestIScore = oldIScore;
        boolean foundBetter;  // always set below for this rule
        //System.out.println("Min "+min+" max "+max+" start "+start+" end "+end);

        if ( ! lengthNormalization) {
          // find the split that can use this rule to make the max score
          for (int split = min; split <= max; split++) {

            if (constraints != null) {
              boolean skip = false;
              for (ParserConstraint c : constraints) {
                if (((start < c.start && end >= c.end) || (start <= c.start && end > c.end)) && split > c.start && split < c.end) {
                  skip = true;
                  break;
                }
                if ((start == c.start && split == c.end)) {
                  String tag = stateIndex.get(leftState);
                  Matcher m = c.state.matcher(tag);
                  if (!m.matches()) {
                    skip = true;
                    break;
                  }
                }
                if ((split == c.start && end == c.end)) {
                  String tag = stateIndex.get(rightChild);
                  Matcher m = c.state.matcher(tag);
                  if (!m.matches()) {
                    skip = true;
                    break;
                  }
                }
              }
              if (skip) {
                continue;
              }
            }

            float lS = iScore_start[split][leftState];
            if (lS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            float rS = iScore[split][end][rightChild];
            if (rS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            float tot = pS + lS + rS;
            if (spillGuts) { log.info("Rule " + rule + " over [" + start + "," + end + ") has log score " + tot + " from L[" + stateIndex.get(leftState) + "=" + leftState + "] = "+ lS  + " R[" + stateIndex.get(rightChild) + "=" + rightChild + "] =  " + rS); }
            if (tot > bestIScore) {
              bestIScore = tot;
            }
          } // for split point
          foundBetter = bestIScore > oldIScore;
        } else {
          // find split that uses this rule to make the max *length normalized* score
          int bestWordsInSpan = wordsInSpan[start][end][parentState];
          float oldNormIScore = oldIScore / bestWordsInSpan;
          float bestNormIScore = oldNormIScore;

          for (int split = min; split <= max; split++) {
            float lS = iScore_start[split][leftState];
            if (lS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            float rS = iScore[split][end][rightChild];
            if (rS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            float tot = pS + lS + rS;
            int newWordsInSpan = wordsInSpan[start][split][leftState] + wordsInSpan[split][end][rightChild];
            float normTot = tot / newWordsInSpan;
            if (normTot > bestNormIScore) {
              bestIScore = tot;
              bestNormIScore = normTot;
              bestWordsInSpan = newWordsInSpan;
            }
          } // for split point
          foundBetter = bestNormIScore > oldNormIScore;
          if (foundBetter) {
            wordsInSpan[start][end][parentState] = bestWordsInSpan;
          }
        } // fi op.testOptions.lengthNormalization
        if (foundBetter) { // this way of making "parentState" is better than previous
          iScore_start_end[parentState] = bestIScore;

          if (spillGuts) log.info("Could build " + stateIndex.get(parentState) + " from " + start + " to " + end + " score " + bestIScore);
          if (oldIScore == Float.NEGATIVE_INFINITY) {
            if (start > narrowLExtent_end[parentState]) {
              narrowLExtent_end[parentState] = wideLExtent_end[parentState] = start;
            } else if (start < wideLExtent_end[parentState]) {
              wideLExtent_end[parentState] = start;
            }
            if (end < narrowRExtent_start[parentState]) {
              narrowRExtent_start[parentState] = wideRExtent_start[parentState] = end;
            } else if (end > wideRExtent_start[parentState]) {
              wideRExtent_start[parentState] = end;
            }
          }
        } // end if foundBetter
      } // end for leftRules
    } // end for leftState
    // do right restricted rules
    for (int rightState = 0; rightState < numStates; rightState++) {
      int narrowL = narrowLExtent_end[rightState];
      if (narrowL <= start) {
        continue;
      }
      BinaryRule[] rightRules = bg.splitRulesWithRC(rightState);
      //      if (spillGuts) System.out.println("Found " + rightRules.length + " right rules for state " + stateIndex.get(rightState));
      for (BinaryRule rule : rightRules) {
        //      if (spillGuts) System.out.println("Considering rule for " + start + " to " + end + ": " + rightRules[i]);

        int leftChild = rule.leftChild;
        int narrowR = narrowRExtent_start[leftChild];
        if (narrowR > narrowL) {
          continue;
        }
        int min2 = wideLExtent_end[rightState];
        int min = (narrowR > min2 ? narrowR : min2);
        // Erik Frey 2009-12-17: This is unnecessary: narrowR is <= narrowL (established in previous check) and wideLExtent[e][r] is always <= narrowLExtent[e][r] by design, so the check will never evaluate true.
        // if (min > narrowL) {
        //   continue;
        // }
        int max1 = wideRExtent_start[leftChild];
        int max = (max1 < narrowL ? max1 : narrowL);
        if (min > max) {
          continue;
        }
        float pS = rule.score;
        int parentState = rule.parent;
        float oldIScore = iScore_start_end[parentState];
        float bestIScore = oldIScore;
        boolean foundBetter; // always initialized below
        //System.out.println("Start "+start+" end "+end+" min "+min+" max "+max);
        if ( ! lengthNormalization) {
          // find the split that can use this rule to make the max score
          for (int split = min; split <= max; split++) {

            if (constraints != null) {
              boolean skip = false;
              for (ParserConstraint c : constraints) {
                if (((start < c.start && end >= c.end) || (start <= c.start && end > c.end)) && split > c.start && split < c.end) {
                  skip = true;
                  break;
                }
                if ((start == c.start && split == c.end)) {
                  String tag = stateIndex.get(leftChild);
                  Matcher m = c.state.matcher(tag);
                  if (!m.matches()) {
                    //if (!tag.startsWith(c.state+"^")) {
                    skip = true;
                    break;
                  }
                }
                if ((split == c.start && end == c.end)) {
                  String tag = stateIndex.get(rightState);
                  Matcher m = c.state.matcher(tag);
                  if (!m.matches()) {
                    //if (!tag.startsWith(c.state+"^")) {
                    skip = true;
                    break;
                  }
                }
              }
              if (skip) {
                continue;
              }
            }

            float lS = iScore_start[split][leftChild];
            // cdm [2012]: Test whether removing these 2 tests might speed things up because less branching?
            // jab [2014]: oddly enough, removing these tests helps the chinese parser but not the english parser.
            if (lS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            float rS = iScore[split][end][rightState];
            if (rS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            float tot = pS + lS + rS;
            if (tot > bestIScore) {
              bestIScore = tot;
            }
          } // end for split
          foundBetter = bestIScore > oldIScore;
        } else {
          // find split that uses this rule to make the max *length normalized* score
          int bestWordsInSpan = wordsInSpan[start][end][parentState];
          float oldNormIScore = oldIScore / bestWordsInSpan;
          float bestNormIScore = oldNormIScore;
          for (int split = min; split <= max; split++) {
            float lS = iScore_start[split][leftChild];
            if (lS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            float rS = iScore[split][end][rightState];
            if (rS == Float.NEGATIVE_INFINITY) {
              continue;
            }
            float tot = pS + lS + rS;
            int newWordsInSpan = wordsInSpan[start][split][leftChild] + wordsInSpan[split][end][rightState];
            float normTot = tot / newWordsInSpan;
            if (normTot > bestNormIScore) {
              bestIScore = tot;
              bestNormIScore = normTot;
              bestWordsInSpan = newWordsInSpan;
            }
          } // end for split
          foundBetter = bestNormIScore > oldNormIScore;
          if (foundBetter) {
            wordsInSpan[start][end][parentState] = bestWordsInSpan;
          }
        } // end if lengthNormalization
        if (foundBetter) { // this way of making "parentState" is better than previous
          iScore_start_end[parentState] = bestIScore;
          if (spillGuts) log.info("Could build " + stateIndex.get(parentState) + " from " + start + " to " + end + " with score " + bestIScore);
          if (oldIScore == Float.NEGATIVE_INFINITY) {
            if (start > narrowLExtent_end[parentState]) {
              narrowLExtent_end[parentState] = wideLExtent_end[parentState] = start;
            } else if (start < wideLExtent_end[parentState]) {
              wideLExtent_end[parentState] = start;
            }
            if (end < narrowRExtent_start[parentState]) {
              narrowRExtent_start[parentState] = wideRExtent_start[parentState] = end;
            } else if (end > wideRExtent_start[parentState]) {
              wideRExtent_start[parentState] = end;
            }
          }
        } // end if foundBetter
      } // for rightRules
    } // for rightState
    if (spillGuts) {
      tick("Unaries for span " + diff + "...");
    }
    // do unary rules -- one could promote this loop and put start inside
    for (int state = 0; state < numStates; state++) {
      float iS = iScore_start_end[state];
      if (iS == Float.NEGATIVE_INFINITY) {
        continue;
      }

      UnaryRule[] unaries = ug.closedRulesByChild(state);
      for (UnaryRule ur : unaries) {

        if (constraints != null) {
          boolean skip = false;
          for (ParserConstraint c : constraints) {
            if ((start == c.start && end == c.end)) {
              String tag = stateIndex.get(ur.parent);
              Matcher m = c.state.matcher(tag);
              if (!m.matches()) {
                //if (!tag.startsWith(c.state+"^")) {
                skip = true;
                break;
              }
            }
          }
          if (skip) {
            continue;
          }
        }

        int parentState = ur.parent;
        float pS = ur.score;
        float tot = iS + pS;
        float cur = iScore_start_end[parentState];
        boolean foundBetter;  // always set below
        if (lengthNormalization) {
          int totWordsInSpan = wordsInSpan[start][end][state];
          float normTot = tot / totWordsInSpan;
          int curWordsInSpan = wordsInSpan[start][end][parentState];
          float normCur = cur / curWordsInSpan;
          foundBetter = normTot > normCur;
          if (foundBetter) {
            wordsInSpan[start][end][parentState] = wordsInSpan[start][end][state];
          }
        } else {
          foundBetter = (tot > cur);
        }
        if (foundBetter) {
          if (spillGuts) log.info("Could build " + stateIndex.get(parentState) + " from " + start + " to " + end + " with score " + tot);
          iScore_start_end[parentState] = tot;
          if (cur == Float.NEGATIVE_INFINITY) {
            if (start > narrowLExtent_end[parentState]) {
              narrowLExtent_end[parentState] = wideLExtent_end[parentState] = start;
            } else if (start < wideLExtent_end[parentState]) {
              wideLExtent_end[parentState] = start;
            }
            if (end < narrowRExtent_start[parentState]) {
              narrowRExtent_start[parentState] = wideRExtent_start[parentState] = end;
            } else if (end > wideRExtent_start[parentState]) {
              wideRExtent_start[parentState] = end;
            }
          }
        } // end if foundBetter
      } // for UnaryRule r
    } // for unary rules
  }


  private void initializeChart(Lattice lr) {
    for (LatticeEdge edge : lr) {
      int start = edge.start;
      int end = edge.end;
      String word = edge.word;

      // Add pre-terminals, augmented with edge weights
      for (int state = 0; state < numStates; state++) {
        if (isTag[state]) {
          IntTaggedWord itw = new IntTaggedWord(word, stateIndex.get(state), wordIndex, tagIndex);

          float newScore = lex.score(itw, start, word, null) + (float) edge.weight;
          if (newScore > iScore[start][end][state]) {
            iScore[start][end][state] = newScore;
            narrowRExtent[start][state] = Math.min(end, narrowRExtent[start][state]);
            narrowLExtent[end][state] = Math.max(start, narrowLExtent[end][state]);
            wideRExtent[start][state] = Math.max(end, wideRExtent[start][state]);
            wideLExtent[end][state] = Math.min(start, wideLExtent[end][state]);
          }
        }
      }

      // Give scores to all tags if the parse fails (more flexible tagging)
      if (floodTags && (!op.testOptions.noRecoveryTagging)) {
        for (int state = 0; state < numStates; state++) {
          float iS = iScore[start][end][state];
          if (isTag[state] && iS == Float.NEGATIVE_INFINITY) {
            iScore[start][end][state] = -1000.0f + (float) edge.weight;
            narrowRExtent[start][state] = end;
            narrowLExtent[end][state] = start;
            wideRExtent[start][state] = end;
            wideLExtent[end][state] = start;
          }
        }
      }

      // Add unary rules (possibly chains) that terminate in POS tags
      for (int state = 0; state < numStates; state++) {
        float iS = iScore[start][end][state];
        if (iS == Float.NEGATIVE_INFINITY) {
          continue;
        }
        UnaryRule[] unaries = ug.closedRulesByChild(state);
        for (UnaryRule ur : unaries) {
          int parentState = ur.parent;
          float pS = ur.score;
          float tot = iS + pS;
          if (tot > iScore[start][end][parentState]) {
            iScore[start][end][parentState] = tot;
            narrowRExtent[start][parentState] = Math.min(end, narrowRExtent[start][parentState]);
            narrowLExtent[end][parentState] = Math.max(start, narrowLExtent[end][parentState]);
            wideRExtent[start][parentState] = Math.max(end, wideRExtent[start][parentState]);
            wideLExtent[end][parentState] = Math.min(start, wideLExtent[end][parentState]);
//            narrowRExtent[start][parentState] = start + 1; //end
//            narrowLExtent[end][parentState] = end - 1; //start
//            wideRExtent[start][parentState] = start + 1; //end
//            wideLExtent[end][parentState] = end - 1; //start
          }
        }
      }
    }
  }


  private void initializeChart(List<? extends HasWord>  sentence) {
    int boundary = wordIndex.indexOf(Lexicon.BOUNDARY);

    for (int start = 0; start < length; start++) {
      if (op.testOptions.maxSpanForTags > 1) { // only relevant for parsing single words as multiple input tokens.
        // todo [cdm 2012]: This case seems buggy in never doing unaries over span 1 items
        // note we don't look for "words" including the end symbol!
        for (int end = start + 1; (end < length - 1 && end - start <= op.testOptions.maxSpanForTags) || (start + 1 == end); end++) {
          StringBuilder word = new StringBuilder();
          //wsg: Feb 2010 - Appears to support character-level parsing
          for (int i = start; i < end; i++) {
            if (sentence.get(i) instanceof HasWord) {
              HasWord cl = sentence.get(i);
              word.append(cl.word());
            } else {
              word.append(sentence.get(i).toString());
            }
          }
          for (int state = 0; state < numStates; state++) {
            float iS = iScore[start][end][state];
            if (iS == Float.NEGATIVE_INFINITY && isTag[state]) {
              IntTaggedWord itw = new IntTaggedWord(word.toString(), stateIndex.get(state), wordIndex, tagIndex);
              iScore[start][end][state] = lex.score(itw, start, word.toString(), null);
              if (iScore[start][end][state] > Float.NEGATIVE_INFINITY) {
                narrowRExtent[start][state] = start + 1;
                narrowLExtent[end][state] = end - 1;
                wideRExtent[start][state] = start + 1;
                wideLExtent[end][state] = end - 1;
              }
            }
          }
        }

      } else { // "normal" chart initialization of the [start,start+1] cell

        int word = words[start];
        int end = start + 1;
        Arrays.fill(tags[start], false);

        float[] iScore_start_end = iScore[start][end];
        int[] narrowRExtent_start = narrowRExtent[start];
        int[] narrowLExtent_end = narrowLExtent[end];
        int[] wideRExtent_start = wideRExtent[start];
        int[] wideLExtent_end = wideLExtent[end];

        //Force tags
        String trueTagStr = null;
        if (sentence.get(start) instanceof HasTag) {
          trueTagStr = ((HasTag) sentence.get(start)).tag();
          if ("".equals(trueTagStr)) {
            trueTagStr = null;
          }
        }

        // Another option for forcing tags: supply a regex
        String candidateTagRegex = null;
        if (sentence.get(start) instanceof CoreLabel) {
          candidateTagRegex = ((CoreLabel) sentence.get(start)).get(ParserAnnotations.CandidatePartOfSpeechAnnotation.class);
          if ("".equals(candidateTagRegex)) {
            candidateTagRegex = null;
          }
        }

        //Word context (e.g., morphosyntactic info)
        String wordContextStr = null;
        if(sentence.get(start) instanceof HasContext) {
          wordContextStr = ((HasContext) sentence.get(start)).originalText();
          if("".equals(wordContextStr))
            wordContextStr = null;
        }

        boolean assignedSomeTag = false;

        if ( ! floodTags || word == boundary) {
          // in this case we generate the taggings in the lexicon,
          // which may itself be tagging flexibly or using a strict lexicon.
          if (dumpTagging) {
            EncodingPrintWriter.err.println("Normal tagging " + wordIndex.get(word) + " [" + word + "]", "UTF-8");
          }
          for (Iterator<IntTaggedWord> taggingI = lex.ruleIteratorByWord(word, start, wordContextStr); taggingI.hasNext(); ) {
            IntTaggedWord tagging = taggingI.next();
            int state = stateIndex.indexOf(tagIndex.get(tagging.tag));
            // if word was supplied with a POS tag, skip all taggings
            // not basicCategory() compatible with supplied tag.
            if (trueTagStr != null) {
              if ((!op.testOptions.forceTagBeginnings && !tlp.basicCategory(tagging.tagString(tagIndex)).equals(trueTagStr)) ||
                  (op.testOptions.forceTagBeginnings &&  !tagging.tagString(tagIndex).startsWith(trueTagStr))) {
                if (dumpTagging) {
                  EncodingPrintWriter.err.println("  Skipping " + tagging + " as it doesn't match trueTagStr: " + trueTagStr, "UTF-8");
                }
                continue;
              }
            }
            if (candidateTagRegex != null) {
              if ((!op.testOptions.forceTagBeginnings && !tlp.basicCategory(tagging.tagString(tagIndex)).matches(candidateTagRegex)) ||
                  (op.testOptions.forceTagBeginnings &&  !tagging.tagString(tagIndex).matches(candidateTagRegex))) {
                if (dumpTagging) {
                  EncodingPrintWriter.err.println("  Skipping " + tagging + " as it doesn't match candidateTagRegex: " + candidateTagRegex, "UTF-8");
                }
                continue;
              }
            }
            // try {
            float lexScore = lex.score(tagging, start, wordIndex.get(tagging.word), wordContextStr); // score the cell according to P(word|tag) in the lexicon
            if (lexScore > Float.NEGATIVE_INFINITY) {
              assignedSomeTag = true;
              iScore_start_end[state] = lexScore;
              narrowRExtent_start[state] = end;
              narrowLExtent_end[state] = start;
              wideRExtent_start[state] = end;
              wideLExtent_end[state] = start;
            }
            // } catch (Exception e) {
            // e.printStackTrace();
            // System.out.println("State: " + state + " tags " + Numberer.getGlobalNumberer("tags").object(tagging.tag));
            // }
            int tag = tagging.tag;
            tags[start][tag] = true;
            if (dumpTagging) {
              EncodingPrintWriter.err.println("Word pos " + start + " tagging " + tagging + " score " + iScore_start_end[state] + " [state " + stateIndex.get(state) + " = " + state + "]", "UTF-8");
            }
            //if (start == length-2 && tagging.parent == puncTag)
            //  lastIsPunc = true;
          }
        } // end if ( ! floodTags || word == boundary)

        if ( ! assignedSomeTag) {
          // If you got here, either you were using forceTags (gold tags)
          // and the gold tag was not seen with that word in the training data
          // or we are in floodTags=true (recovery parse) mode
          // Here, we give words all tags for
          // which the lexicon score is not -Inf, not just seen or
          // specified taggings
          if (dumpTagging) {
            EncodingPrintWriter.err.println("Forced FlexiTagging " + wordIndex.get(word), "UTF-8");
          }
          for (int state = 0; state < numStates; state++) {
            if (isTag[state] && iScore_start_end[state] == Float.NEGATIVE_INFINITY) {
              if (trueTagStr != null) {
                String tagString = stateIndex.get(state);
                if ( ! tlp.basicCategory(tagString).equals(trueTagStr)) {
                  continue;
                }
              }

              float lexScore = lex.score(new IntTaggedWord(word, tagIndex.indexOf(stateIndex.get(state))), start, wordIndex.get(word), wordContextStr);
              if (candidateTagRegex != null) {
                String tagString = stateIndex.get(state);
                if (!tlp.basicCategory(tagString).matches(candidateTagRegex)) {
                  continue;
                }
              }

              if (lexScore > Float.NEGATIVE_INFINITY) {
                iScore_start_end[state] = lexScore;
                narrowRExtent_start[state] = end;
                narrowLExtent_end[state] = start;
                wideRExtent_start[state] = end;
                wideLExtent_end[state] = start;
              }
              if (dumpTagging) {
                EncodingPrintWriter.err.println("Word pos " + start + " tagging " + (new IntTaggedWord(word, tagIndex.indexOf(stateIndex.get(state)))) + " score " + iScore_start_end[state]  + " [state " + stateIndex.get(state) + " = " + state + "]", "UTF-8");
              }
            }
          }
        } // end if ! assignedSomeTag

        // tag multi-counting
        if (op.dcTags) {
          for (int state = 0; state < numStates; state++) {
            if (isTag[state]) {
              iScore_start_end[state] *= (1.0 + op.testOptions.depWeight);
            }
          }
        }

        if (floodTags && (!op.testOptions.noRecoveryTagging) && ! (word == boundary)) {
          // if parse failed because of tag coverage, we put in all tags with
          // a score of -1000, by fiat.  You get here from the invocation of
          // parse(ls) inside parse(ls) *after* floodTags has been turned on.
          // Search above for "floodTags = true".
          if (dumpTagging) {
            EncodingPrintWriter.err.println("Flooding tags for " + wordIndex.get(word), "UTF-8");
          }
          for (int state = 0; state < numStates; state++) {
            if (isTag[state] && iScore_start_end[state] == Float.NEGATIVE_INFINITY) {
              iScore_start_end[state] = -1000.0f;
              narrowRExtent_start[state] = end;
              narrowLExtent_end[state] = start;
              wideRExtent_start[state] = end;
              wideLExtent_end[state] = start;
            }
          }
        }

        // Apply unary rules in diagonal cells of chart
        if (spillGuts) {
          tick("Terminal Unary...");
        }
        for (int state = 0; state < numStates; state++) {
          float iS = iScore_start_end[state];
          if (iS == Float.NEGATIVE_INFINITY) {
            continue;
          }
          UnaryRule[] unaries = ug.closedRulesByChild(state);
          for (UnaryRule ur : unaries) {
            int parentState = ur.parent;
            float pS = ur.score;
            float tot = iS + pS;
            if (tot > iScore_start_end[parentState]) {
              iScore_start_end[parentState] = tot;
              narrowRExtent_start[parentState] = end;
              narrowLExtent_end[parentState] = start;
              wideRExtent_start[parentState] = end;
              wideLExtent_end[parentState] = start;
            }
          }
        }
        if (spillGuts) {
          tick("Next word...");
        }
      }
    } // end for start
  } // end initializeChart(List sentence)


  @Override
  public boolean hasParse() {
    return getBestScore() > Double.NEGATIVE_INFINITY;
  }


  private static final double TOL = 1e-5;

  protected static boolean matches(double x, double y) {
    return (Math.abs(x - y) / (Math.abs(x) + Math.abs(y) + 1e-10) < TOL);
  }


  @Override
  public double getBestScore() {
    return getBestScore(goalStr);
  }

  public double getBestScore(String stateName) {
    if (length > arraySize) {
      return Double.NEGATIVE_INFINITY;
    }
    if (!stateIndex.contains(stateName)) {
      return Double.NEGATIVE_INFINITY;
    }
    int goal = stateIndex.indexOf(stateName);
    if (iScore == null || iScore.length == 0 || iScore[0].length <= length || iScore[0][length].length <= goal) {
      return Double.NEGATIVE_INFINITY;
    }
    return iScore[0][length][goal];
  }


  @Override
  public Tree getBestParse() {
    Tree internalTree = extractBestParse(goalStr, 0, length);
    //System.out.println("Got internal best parse...");
    if (internalTree == null) {
      log.info("Warning: no parse found in ExhaustivePCFGParser.extractBestParse");
    } // else {
      // restoreUnaries(internalTree);
    // }
    // System.out.println("Restored unaries...");
    return internalTree;
    //TreeTransformer debinarizer = BinarizerFactory.getDebinarizer();
    //return debinarizer.transformTree(internalTree);
  }

  /** Return the best parse of some category/state over a certain span. */
  protected Tree extractBestParse(String goalStr, int start, int end) {
    return extractBestParse(stateIndex.indexOf(goalStr), start, end);
  }

  private Tree extractBestParse(int goal, int start, int end) {
    // find source of inside score
    // no backtraces so we can speed up the parsing for its primary use
    double bestScore = iScore[start][end][goal];
    double normBestScore = op.testOptions.lengthNormalization ? (bestScore / wordsInSpan[start][end][goal]) : bestScore;
    String goalStr = stateIndex.get(goal);

    // check tags
    if (end - start <= op.testOptions.maxSpanForTags && tagIndex.contains(goalStr)) {
      if (op.testOptions.maxSpanForTags > 1) {
        Tree wordNode = null;
        if (sentence != null) {
          StringBuilder word = new StringBuilder();
          for (int i = start; i < end; i++) {
            if (sentence.get(i) instanceof HasWord) {
              HasWord cl = (HasWord) sentence.get(i);
              word.append(cl.word());
            } else {
              word.append(sentence.get(i).toString());
            }
          }
          wordNode = tf.newLeaf(word.toString());

        } else if (lr != null) {
          List<LatticeEdge> latticeEdges = lr.getEdgesOverSpan(start, end);
          for (LatticeEdge edge : latticeEdges) {
            IntTaggedWord itw = new IntTaggedWord(edge.word, stateIndex.get(goal), wordIndex, tagIndex);

            float tagScore = (floodTags) ? -1000.0f : lex.score(itw, start, edge.word, null);
            if (matches(bestScore, tagScore + (float) edge.weight)) {
              wordNode = tf.newLeaf(edge.word);
              if(wordNode.label() instanceof CoreLabel) {
              	CoreLabel cl = (CoreLabel) wordNode.label();
              	cl.setBeginPosition(start);
              	cl.setEndPosition(end);
              }
              break;
            }
          }
          if (wordNode == null) {
            throw new RuntimeException("could not find matching word from lattice in parse reconstruction");
          }

        } else {
          throw new RuntimeException("attempt to get word when sentence and lattice are null!");
        }
        Tree tagNode = tf.newTreeNode(goalStr, Collections.singletonList(wordNode));
        tagNode.setScore(bestScore);
        if (originalTags[start] != null) {
          tagNode.label().setValue(originalTags[start].tag());
        }
        return tagNode;
      } else {  // normal lexicon is single words case
        IntTaggedWord tagging = new IntTaggedWord(words[start], tagIndex.indexOf(goalStr));
        String contextStr = getCoreLabel(start).originalText();
        float tagScore = lex.score(tagging, start, wordIndex.get(words[start]), contextStr);
        if (tagScore > Float.NEGATIVE_INFINITY || floodTags) {
          // return a pre-terminal tree
          CoreLabel terminalLabel = getCoreLabel(start);

          Tree wordNode = tf.newLeaf(terminalLabel);
          Tree tagNode = tf.newTreeNode(goalStr, Collections.singletonList(wordNode));
          tagNode.setScore(bestScore);
          if (terminalLabel.tag() != null) {
            tagNode.label().setValue(terminalLabel.tag());
          }
          if (tagNode.label() instanceof HasTag) {
            ((HasTag) tagNode.label()).setTag(tagNode.label().value());
          }
          return tagNode;
        }
      }
    }
    // check binaries first
    for (int split = start + 1; split < end; split++) {
      for (Iterator<BinaryRule> binaryI = bg.ruleIteratorByParent(goal); binaryI.hasNext(); ) {
        BinaryRule br = binaryI.next();
        double score = br.score + iScore[start][split][br.leftChild] + iScore[split][end][br.rightChild];
        boolean matches;
        if (op.testOptions.lengthNormalization) {
          double normScore = score / (wordsInSpan[start][split][br.leftChild] + wordsInSpan[split][end][br.rightChild]);
          matches = matches(normScore, normBestScore);
        } else {
          matches = matches(score, bestScore);
        }
        if (matches) {
          // build binary split
          Tree leftChildTree = extractBestParse(br.leftChild, start, split);
          Tree rightChildTree = extractBestParse(br.rightChild, split, end);
          List<Tree> children = new ArrayList<>();
          children.add(leftChildTree);
          children.add(rightChildTree);
          Tree result = tf.newTreeNode(goalStr, children);
          result.setScore(score);
          // log.info("    Found Binary node: "+result);
          return result;
        }
      }
    }
    // check unaries
    // note that even though we parse with the unary-closed grammar, we can
    // extract the best parse with the non-unary-closed grammar, since all
    // the intermediate states in the chain must have been built, and hence
    // we can exploit the sparser space and reconstruct the full tree as we go.
    // for (Iterator<UnaryRule> unaryI = ug.closedRuleIteratorByParent(goal); unaryI.hasNext(); ) {
    for (Iterator<UnaryRule> unaryI = ug.ruleIteratorByParent(goal); unaryI.hasNext(); ) {
      UnaryRule ur = unaryI.next();
      // log.info("  Trying " + ur + " dtr score: " + iScore[start][end][ur.child]);
      double score = ur.score + iScore[start][end][ur.child];
      boolean matches;
      if (op.testOptions.lengthNormalization) {
        double normScore = score / wordsInSpan[start][end][ur.child];
        matches = matches(normScore, normBestScore);
      } else {
        matches = matches(score, bestScore);
      }
      if (ur.child != ur.parent && matches) {
        // build unary
        Tree childTree = extractBestParse(ur.child, start, end);
        Tree result = tf.newTreeNode(goalStr, Collections.singletonList(childTree));
        // log.info("    Matched!  Unary node: "+result);
        result.setScore(score);
        return result;
      }
    }
    log.info("Warning: no parse found in ExhaustivePCFGParser.extractBestParse: failing on: [" + start + ", " + end + "] looking for " + goalStr);
    return null;
  }


  /* -----------------------
  // No longer needed: extracBestParse restores unaries as it goes
  protected void restoreUnaries(Tree t) {
    //System.out.println("In restoreUnaries...");
    for (Tree node : t) {
      log.info("Doing node: "+node.label());
      if (node.isLeaf() || node.isPreTerminal() || node.numChildren() != 1) {
        //System.out.println("Skipping node: "+node.label());
        continue;
      }
      //System.out.println("Not skipping node: "+node.label());
      Tree parent = node;
      Tree child = node.children()[0];
      List path = ug.getBestPath(stateIndex.indexOf(parent.label().value()), stateIndex.indexOf(child.label().value()));
      log.info("Got path: "+path);
      int pos = 1;
      while (pos < path.size() - 1) {
        int interState = ((Integer) path.get(pos)).intValue();
        Tree intermediate = tf.newTreeNode(new StringLabel(stateIndex.get(interState)), parent.getChildrenAsList());
        parent.setChildren(Collections.singletonList(intermediate));
        pos++;
      }
      //System.out.println("Done with node: "+node.label());
    }
  }
  ---------------------- */


  /**
   * Return all best parses (except no ties allowed on POS tags?).
   * Even though we parse with the unary-closed grammar, since all the
   * intermediate states in a chain must have been built, we can
   * reconstruct the unary chain as we go using the non-unary-closed grammar.
   */
  protected List<Tree> extractBestParses(int goal, int start, int end) {
    // find sources of inside score
    // no backtraces so we can speed up the parsing for its primary use
    double bestScore = iScore[start][end][goal];
    String goalStr = stateIndex.get(goal);
    //System.out.println("Searching for "+goalStr+" from "+start+" to "+end+" scored "+bestScore);
    // check tags
    if (end - start == 1 && tagIndex.contains(goalStr)) {
      IntTaggedWord tagging = new IntTaggedWord(words[start], tagIndex.indexOf(goalStr));
      String contextStr = getCoreLabel(start).originalText();
      float tagScore = lex.score(tagging, start, wordIndex.get(words[start]), contextStr);
      if (tagScore > Float.NEGATIVE_INFINITY || floodTags) {
        // return a pre-terminal tree
        String wordStr = wordIndex.get(words[start]);
        Tree wordNode = tf.newLeaf(wordStr);
        Tree tagNode = tf.newTreeNode(goalStr, Collections.singletonList(wordNode));
        if (originalTags[start] != null) {
          tagNode.label().setValue(originalTags[start].tag());
        }
        //System.out.println("Tag node: "+tagNode);
        return Collections.singletonList(tagNode);
      }
    }
    // check binaries first
    List<Tree> bestTrees = new ArrayList<>();
    for (int split = start + 1; split < end; split++) {
      for (Iterator<BinaryRule> binaryI = bg.ruleIteratorByParent(goal); binaryI.hasNext(); ) {
        BinaryRule br = binaryI.next();
        double score = br.score + iScore[start][split][br.leftChild] + iScore[split][end][br.rightChild];
        if (matches(score, bestScore)) {
          // build binary split
          List<Tree> leftChildTrees = extractBestParses(br.leftChild, start, split);
          List<Tree> rightChildTrees = extractBestParses(br.rightChild, split, end);
          // System.out.println("Found a best way to build " + goalStr + "(" +
          //                 start + "," + end + ") with " +
          //                 leftChildTrees.size() + "x" +
          //                 rightChildTrees.size() + " ways to build.");
          for (Tree leftChildTree : leftChildTrees) {
            for (Tree rightChildTree : rightChildTrees) {
              List<Tree> children = new ArrayList<>();
              children.add(leftChildTree);
              children.add(rightChildTree);
              Tree result = tf.newTreeNode(goalStr, children);
              //System.out.println("Binary node: "+result);
              bestTrees.add(result);
            }
          }
        }
      }
    }
    // check unaries
    for (Iterator<UnaryRule> unaryI = ug.ruleIteratorByParent(goal); unaryI.hasNext(); ) {
      UnaryRule ur = unaryI.next();
      double score = ur.score + iScore[start][end][ur.child];
      if (ur.child != ur.parent && matches(score, bestScore)) {
        // build unary
        List<Tree> childTrees = extractBestParses(ur.child, start, end);
        for (Tree childTree : childTrees) {
          Tree result = tf.newTreeNode(goalStr, Collections.singletonList(childTree));
          //System.out.println("Unary node: "+result);
          bestTrees.add(result);
        }
      }
    }
    if (bestTrees.isEmpty()) {
      log.info("Warning: no parse found in ExhaustivePCFGParser.extractBestParse: failing on: [" + start + ", " + end + "] looking for " + goalStr);
    }
    return bestTrees;
  }


  /** Get k good parses for the sentence.  It is expected that the
   *  parses returned approximate the k best parses, but without any
   *  guarantee that the exact list of k best parses has been produced.
   *
   *  @param k The number of good parses to return
   *  @return A list of k good parses for the sentence, with
   *         each accompanied by its score
   */
  @Override
  public List<ScoredObject<Tree>> getKGoodParses(int k) {
    return getKBestParses(k);
  }

  /** Get k parse samples for the sentence.  It is expected that the
   *  parses are sampled based on their relative probability.
   *
   *  @param k The number of sampled parses to return
   *  @return A list of k parse samples for the sentence, with
   *         each accompanied by its score
   */
  @Override
  public List<ScoredObject<Tree>> getKSampledParses(int k) {
    throw new UnsupportedOperationException("ExhaustivePCFGParser doesn't sample.");
  }


  //
  // BEGIN K-BEST STUFF
  // taken straight out of "Better k-best Parsing" by Liang Huang and David
  // Chiang
  //

  /** Get the exact k best parses for the sentence.
   *
   *  @param k The number of best parses to return
   *  @return The exact k best parses for the sentence, with
   *         each accompanied by its score (typically a
   *         negative log probability).
   */
  @Override
  public List<ScoredObject<Tree>> getKBestParses(int k) {

    cand = Generics.newHashMap();
    dHat = Generics.newHashMap();

    int start = 0;
    int end = length;
    int goal = stateIndex.indexOf(goalStr);

    Vertex v = new Vertex(goal, start, end);
    List<ScoredObject<Tree>> kBestTrees = new ArrayList<>();
    for (int i = 1; i <= k; i++) {
      Tree internalTree = getTree(v, i, k);
      if (internalTree == null) { break; }
      // restoreUnaries(internalTree);
      kBestTrees.add(new ScoredObject<>(internalTree, dHat.get(v).get(i - 1).score));
    }
    return kBestTrees;
  }

  /** Get the kth best, when calculating kPrime best (e.g. 2nd best of 5). */
  private Tree getTree(Vertex v, int k, int kPrime) {
    lazyKthBest(v, k, kPrime);
    String goalStr = stateIndex.get(v.goal);
    int start = v.start;
    // int end = v.end;

    List<Derivation> dHatV = dHat.get(v);

    if (isTag[v.goal] && v.start + 1 == v.end) {
      IntTaggedWord tagging = new IntTaggedWord(words[start], tagIndex.indexOf(goalStr));
      String contextStr = getCoreLabel(start).originalText();
      float tagScore = lex.score(tagging, start, wordIndex.get(words[start]), contextStr);
      if (tagScore > Float.NEGATIVE_INFINITY || floodTags) {
        // return a pre-terminal tree
        CoreLabel terminalLabel = getCoreLabel(start);

        Tree wordNode = tf.newLeaf(terminalLabel);
        Tree tagNode = tf.newTreeNode(goalStr, Collections.singletonList(wordNode));
        if (originalTags[start] != null) {
          tagNode.label().setValue(originalTags[start].tag());
        }
        if (tagNode.label() instanceof HasTag) {
          ((HasTag) tagNode.label()).setTag(tagNode.label().value());
        }
        return tagNode;
      } else {
        assert false;
      }
    }

    if (k-1 >= dHatV.size()) {
      return null;
    }

    Derivation d = dHatV.get(k-1);

    List<Tree> children = new ArrayList<>();
    for (int i = 0; i < d.arc.size(); i++) {
      Vertex child = d.arc.tails.get(i);
      Tree t = getTree(child, d.j.get(i), kPrime);
      assert (t != null);
      children.add(t);
    }

    return tf.newTreeNode(goalStr,children);
  }

  private static class Vertex {
    public final int goal;
    public final int start;
    public final int end;

    public Vertex(int goal, int start, int end) {
      this.goal = goal;
      this.start = start;
      this.end = end;
    }

    public boolean equals(Object o) {
      if (!(o instanceof Vertex)) { return false; }
      Vertex v = (Vertex)o;
      return (v.goal == goal && v.start == start && v.end == end);
    }

    private int hc = -1;

    public int hashCode() {
      if (hc == -1) {
        hc = goal + (17 * (start + (17 * end)));
      }
      return hc;
    }

    public String toString() {
      return goal+"["+start+","+end+"]";
    }
  }

  private static class Arc {
    public final List<Vertex> tails;
    public final Vertex head;
    public final double ruleScore; // for convenience

    public Arc(List<Vertex> tails, Vertex head, double ruleScore) {
      this.tails = Collections.unmodifiableList(tails);
      this.head = head;
      this.ruleScore = ruleScore;
      // TODO: add check that rule is compatible with head and tails!
    }

    public boolean equals(Object o) {
      if (!(o instanceof Arc)) { return false; }
      Arc a = (Arc) o;
      return a.head.equals(head) && a.tails.equals(tails);
    }

    private int hc = -1;

    public int hashCode() {
      if (hc == -1) {
        hc = head.hashCode() + (17 * tails.hashCode());
      }
      return hc;
    }

    public int size() { return tails.size(); }
  }

  private static class Derivation {
    public final Arc arc;
    public final List<Integer> j;
    public final double score;  // score does not affect equality (?)
    public final List<Double> childrenScores;

    public Derivation(Arc arc, List<Integer> j, double score, List<Double> childrenScores) {
      this.arc = arc;
      this.j = Collections.unmodifiableList(j);
      this.score = score;
      this.childrenScores = Collections.unmodifiableList(childrenScores);
    }

    public boolean equals(Object o) {
      if (!(o instanceof Derivation)) { return false; }
      Derivation d = (Derivation)o;
      if (arc == null && d.arc != null || arc != null && d.arc == null) { return false; }
      return ((arc == null && d.arc == null || d.arc.equals(arc)) && d.j.equals(j));
    }

    private int hc = -1;

    public int hashCode() {
      if (hc == -1) {
        hc = (arc == null ? 0 : arc.hashCode()) + (17 * j.hashCode());
      }
      return hc;
    }
  }

  private List<Arc> getBackwardsStar(Vertex v) {

    List<Arc> bs = new ArrayList<>();

    // pre-terminal??
    if (isTag[v.goal] && v.start + 1 == v.end) {
      List<Vertex> tails = new ArrayList<>();
      double score = iScore[v.start][v.end][v.goal];
      Arc arc = new Arc(tails, v, score);
      bs.add(arc);
    }

    // check binaries
    for (int split = v.start + 1; split < v.end; split++) {
      for (BinaryRule br : bg.ruleListByParent(v.goal)) {
        Vertex lChild = new Vertex(br.leftChild, v.start, split);
        Vertex rChild = new Vertex(br.rightChild, split, v.end);
        List<Vertex> tails = new ArrayList<>();
        tails.add(lChild);
        tails.add(rChild);
        Arc arc = new Arc(tails, v, br.score);
        bs.add(arc);
      }
    }

    // check unaries
    for (UnaryRule ur : ug.rulesByParent(v.goal)) {
      Vertex child = new Vertex(ur.child, v.start, v.end);
      List<Vertex> tails = new ArrayList<>();
      tails.add(child);
      Arc arc = new Arc(tails, v, ur.score);
      bs.add(arc);
    }

    return bs;
  }

  private Map<Vertex,PriorityQueue<Derivation>> cand = Generics.newHashMap();
  private Map<Vertex,LinkedList<Derivation>> dHat = Generics.newHashMap();

  private PriorityQueue<Derivation> getCandidates(Vertex v, int k) {
    PriorityQueue<Derivation> candV = cand.get(v);
    if (candV == null) {
      candV = new BinaryHeapPriorityQueue<>();
      List<Arc> bsV = getBackwardsStar(v);

      for (Arc arc : bsV) {
        int size = arc.size();
        double score = arc.ruleScore;
        List<Double> childrenScores = new ArrayList<>();
        for (int i = 0; i < size; i++) {
          Vertex child = arc.tails.get(i);
          double s = iScore[child.start][child.end][child.goal];
          childrenScores.add(s);
          score += s;
        }
        if (score == Double.NEGATIVE_INFINITY) { continue; }
        List<Integer> j = new ArrayList<>();
        for (int i = 0; i < size; i++) {
          j.add(1);
        }
        Derivation d = new Derivation(arc, j, score, childrenScores);
        candV.add(d, score);
      }
      PriorityQueue<Derivation> tmp = new BinaryHeapPriorityQueue<>();
      for (int i = 0; i < k; i++) {
        if (candV.isEmpty()) { break; }
        Derivation d = candV.removeFirst();
        tmp.add(d, d.score);
      }
      candV = tmp;
      cand.put(v, candV);
    }
    return candV;
  }

  // note: kPrime is the original k
  private void lazyKthBest(Vertex v, int k, int kPrime) {
    PriorityQueue<Derivation> candV = getCandidates(v, kPrime);

    LinkedList<Derivation> dHatV = dHat.get(v);
    if (dHatV == null) {
      dHatV = new LinkedList<>();
      dHat.put(v,dHatV);
    }
    while (dHatV.size() < k) {
      if (!dHatV.isEmpty()) {
        Derivation derivation = dHatV.getLast();
        lazyNext(candV, derivation, kPrime);
      }
      if (!candV.isEmpty()) {
        Derivation d = candV.removeFirst();
        dHatV.add(d);
      } else {
        break;
      }
    }
  }

  private void lazyNext(PriorityQueue<Derivation> candV, Derivation derivation, int kPrime) {
    List<Vertex> tails = derivation.arc.tails;
    for  (int i = 0, sz = derivation.arc.size(); i < sz; i++) {
      List<Integer> j = new ArrayList<>(derivation.j);
      j.set(i, j.get(i)+1);
      Vertex Ti = tails.get(i);
      lazyKthBest(Ti, j.get(i), kPrime);
      LinkedList<Derivation> dHatTi = dHat.get(Ti);
      // compute score for this derivation
      if (j.get(i)-1 >= dHatTi.size()) { continue; }
      Derivation d = dHatTi.get(j.get(i)-1);
      double newScore = derivation.score - derivation.childrenScores.get(i) + d.score;
      List<Double> childrenScores = new ArrayList<>(derivation.childrenScores);
      childrenScores.set(i, d.score);
      Derivation newDerivation = new Derivation(derivation.arc, j, newScore, childrenScores);
      if (!candV.contains(newDerivation) && newScore > Double.NEGATIVE_INFINITY) {
        candV.add(newDerivation, newScore);
      }
    }
  }

  //
  // END K-BEST STUFF
  //


  /** Get a complete set of the maximally scoring parses for a sentence,
   *  rather than one chosen at random.  This set may be of size 1 or larger.
   *
   *  @return All the equal best parses for a sentence, with each
   *         accompanied by its score
   */
  @Override
  public List<ScoredObject<Tree>> getBestParses() {
    int start = 0;
    int end = length;
    int goal = stateIndex.indexOf(goalStr);
    double bestScore = iScore[start][end][goal];
    List<Tree> internalTrees = extractBestParses(goal, start, end);
    //System.out.println("Got internal best parse...");
    // for (Tree internalTree : internalTrees) {
    //   restoreUnaries(internalTree);
    // }
    //System.out.println("Restored unaries...");
    List<ScoredObject<Tree>> scoredTrees = new ArrayList<>(internalTrees.size());
    for (Tree tr : internalTrees) {
      scoredTrees.add(new ScoredObject<>(tr, bestScore));
    }
    return scoredTrees;
    //TreeTransformer debinarizer = BinarizerFactory.getDebinarizer();
    //return debinarizer.transformTree(internalTree);
  }

  protected List<ParserConstraint> getConstraints() {
    return constraints;
  }

  void setConstraints(List<ParserConstraint> constraints) {
    if (constraints == null) {
      this.constraints = Collections.emptyList();
    } else {
      this.constraints = constraints;
    }
  }

  public ExhaustivePCFGParser(BinaryGrammar bg, UnaryGrammar ug, Lexicon lex, Options op, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex) {
    //    System.out.println("ExhaustivePCFGParser constructor called.");
    this.bg = bg;
    this.ug = ug;
    this.lex = lex;
    this.op = op;
    this.tlp = op.langpack();
    goalStr = tlp.startSymbol();
    this.stateIndex = stateIndex;
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    tf = new LabeledScoredTreeFactory();

    numStates = stateIndex.size();
    isTag = new boolean[numStates];
    // tag index is smaller, so we fill by iterating over the tag index
    // rather than over the state index
    for (String tag : tagIndex.objectsList()) {
      int state = stateIndex.indexOf(tag);
      if (state < 0) {
        continue;
      }
      isTag[state] = true;
    }
  }


  public void nudgeDownArraySize() {
    try {
      if (arraySize > 2) {
        considerCreatingArrays(arraySize - 2);
      }
    } catch (OutOfMemoryError oome) {
      oome.printStackTrace();
    }
  }

  private void considerCreatingArrays(int length) {
    if (length > op.testOptions.maxLength + 1 || length >= myMaxLength) {
      throw new OutOfMemoryError("Refusal to create such large arrays.");
    } else {
      try {
        createArrays(length + 1);
      } catch (OutOfMemoryError e) {
        myMaxLength = length;
        if (arraySize > 0) {
          try {
            createArrays(arraySize);
          } catch (OutOfMemoryError e2) {
            throw new RuntimeException("CANNOT EVEN CREATE ARRAYS OF ORIGINAL SIZE!!");
          }
        }
        throw e;
      }
      arraySize = length + 1;
      if (op.testOptions.verbose) {
        log.info("Created PCFG parser arrays of size " + arraySize);
      }
    }
  }

  protected void createArrays(int length) {
    // zero out some stuff first in case we recently ran out of memory and are reallocating
    clearArrays();

    int numTags = tagIndex.size();
    // allocate just the parts of iScore and oScore used (end > start, etc.)
    // todo: with some modifications to doInsideScores, we wouldn't need to allocate iScore[i,length] for i != 0 and i != length
    //    System.out.println("initializing iScore arrays with length " + length + " and numStates " + numStates);
    iScore = new float[length][length + 1][];
    for (int start = 0; start < length; start++) {
      for (int end = start + 1; end <= length; end++) {
        iScore[start][end] = new float[numStates];
      }
    }
    //    System.out.println("finished initializing iScore arrays");
    if (op.doDep && !op.testOptions.useFastFactored) {
      //      System.out.println("initializing oScore arrays with length " + length + " and numStates " + numStates);
      oScore = new float[length][length + 1][];
      for (int start = 0; start < length; start++) {
        for (int end = start + 1; end <= length; end++) {
          oScore[start][end] = new float[numStates];
        }
      }
      // System.out.println("finished initializing oScore arrays");
    }
    narrowRExtent = new int[length][numStates];
    wideRExtent = new int[length][numStates];
    narrowLExtent = new int[length + 1][numStates];
    wideLExtent = new int[length + 1][numStates];
    if (op.doDep && !op.testOptions.useFastFactored) {
      iPossibleByL = new boolean[length][numStates];
      iPossibleByR = new boolean[length + 1][numStates];
      oPossibleByL = new boolean[length][numStates];
      oPossibleByR = new boolean[length + 1][numStates];
    }
    tags = new boolean[length][numTags];

    if (op.testOptions.lengthNormalization) {
      wordsInSpan = new int[length][length + 1][];
      for (int start = 0; start < length; start++) {
        for (int end = start + 1; end <= length; end++) {
          wordsInSpan[start][end] = new int[numStates];
        }
      }
    }
    //    System.out.println("ExhaustivePCFGParser constructor finished.");
  }

  private void clearArrays() {
    iScore = oScore = null;
    iPossibleByL = iPossibleByR = oPossibleByL = oPossibleByR = null;
    oFilteredEnd = oFilteredStart = null;
    tags = null;
    narrowRExtent = wideRExtent = narrowLExtent = wideLExtent = null;
  }

} // end class ExhaustivePCFGParser
