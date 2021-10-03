// Stanford Parser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002-2006 The Board of Trustees of
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

import java.util.*;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.HasContext;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * An exhaustive O(n<sup>4</sup>t<sup>2</sup>) time and O(n<sup>2</sup>t)
 * space dependency parser.
 * This follows the general
 * picture of the Eisner and Satta dependency parsing papers, but without the
 * tricks in defining items that they use to get an O(n<sup>3</sup>)
 * dependency parser.  The parser is as described in:
 * <br>
 * Dan Klein and Christopher D. Manning. 2003. Fast Exact Inference with a
 * Factored Model for Natural Language Parsing. In Suzanna Becker, Sebastian
 * Thrun, and Klaus Obermayer (eds), Advances in Neural Information Processing
 * Systems 15 (NIPS 2002). Cambridge, MA: MIT Press, pp. 3-10.
 * http://nlp.stanford.edu/pubs/lex-parser.pdf
 *
 * @author Dan Klein
 */
public class ExhaustiveDependencyParser implements Scorer, KBestViterbiParser  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ExhaustiveDependencyParser.class);

  private static final boolean DEBUG = false;
  private static final boolean DEBUG_MORE = false;

  private final Index<String> tagIndex;
  private final Index<String> wordIndex;

  private TreeFactory tf;

  private DependencyGrammar dg;
  private Lexicon lex;
  private Options op;
  private TreebankLanguagePack tlp;

  private List sentence;
  private int[] words;

  /**
   * Max log inner probability score.
   *
   * Indices:
   * 1. headPos - index of head word (one side of subtree)
   * 2. headTag - which tag assigned
   * 3. cornerPosition - other end of span, i.e. "corner" of right triangle
   */
  private float[][][] iScoreH; // headPos, headTag, cornerPosition (non-head)

  /**
   * Max log outer probability score.  Same indices as iScoreH.
   */
  private float[][][] oScoreH; // headPos, headTag, cornerPosition (non-head)

  /**
   * Total log inner probability score.  Same indices as iScoreH.  Designed for
   * producing summed total probabilities.  Unfinished.
   */
  private float[][][] iScoreHSum;

  /** If true, compute iScoreHSum */
  private static final boolean doiScoreHSum = false;

  private int[][] rawDistance;
  int[][] binDistance;       // reused in other class, so can't be private
  float[][][][][] headScore;
  float[][][] headStop; // headPos, headTag, split
  private boolean[][][] oPossibleByL;
  private boolean[][][] oPossibleByR;
  private boolean[][][] iPossibleByL;
  private boolean[][][] iPossibleByR;
  private int arraySize = 0;
  private int myMaxLength = -0xDEADBEEF;

  float oScore(int start, int end, int head, int tag) {
    return oScoreH[head][dg.tagBin(tag)][start] + oScoreH[head][dg.tagBin(tag)][end];
  }

  /**
   * Probability of *most likely* parse having word (at head) with given POS
   * tag as marker on tree over start (inclusive) ... end (exclusive).  Found
   * by summing (product done in log space) the log probabilities in the two
   * half-triangles.  The indices of iScoreH are: (1) head word index,
   * (2) head tag assigned, and (3) other corner that ends span.
   */
  float iScore(int start, int end, int head, int tag) {
    return iScoreH[head][dg.tagBin(tag)][start] + iScoreH[head][dg.tagBin(tag)][end];
  }

  /**
   * Total probability of all parses having word (at head) with given POS tag
   * as marker on tree over start (inclusive) .. end (exclusive).
   *
   * TODO: CURRENTLY UNTESTED!
   */
  float iScoreTotal(int start, int end, int head, int tag) {
    if (!doiScoreHSum) {
      throw new RuntimeException("Summed inner scores not computed");
    }
    // log scores: so + => * and exploiting independence of left and right choices
    return iScoreHSum[head][dg.tagBin(tag)][start] + iScoreHSum[head][dg.tagBin(tag)][end];
  }

  @Override
  public double oScore(Edge edge) {
    return oScore(edge.start, edge.end, edge.head, edge.tag);
  }

  @Override
  public double iScore(Edge edge) {
    return iScore(edge.start, edge.end, edge.head, edge.tag);
  }

  @Override
  public boolean oPossible(Hook hook) {
    return (hook.isPreHook() ? oPossibleByR[hook.end][hook.head][dg.tagBin(hook.tag)] : oPossibleByL[hook.start][hook.head][dg.tagBin(hook.tag)]);
  }

  @Override
  public boolean iPossible(Hook hook) {
    return (hook.isPreHook() ? iPossibleByR[hook.start][hook.head][dg.tagBin(hook.tag)] : iPossibleByL[hook.end][hook.head][dg.tagBin(hook.tag)]);
  }

  @Override
  public boolean parse(List<? extends HasWord> sentence) {
    if (op.testOptions.verbose) {
      Timing.tick("Starting dependency parse.");
    }
    this.sentence = sentence;
    int length = sentence.size();
    if (length > arraySize) {
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
              throw new RuntimeException("CANNOT EVEN CREATE ARRAYS OF ORIGINAL SIZE!!! " + arraySize);
            }
          }
          throw e;
        }
        arraySize = length + 1;
        if (op.testOptions.verbose) {
          log.info("Created dparser arrays of size " + arraySize);
        }
      }
    }
    if (op.testOptions.verbose) {
      log.info("Initializing...");
    }

    // map to words
    words = new int[length];
    int numTags = dg.numTagBins();//tagIndex.size();
    //System.out.println("\nNumTags: "+numTags);
    //System.out.println(tagIndex);
    boolean[][] hasTag = new boolean[length][numTags];
    for (int i = 0; i < length; i++) {
      //if (wordIndex.contains(sentence.get(i).toString()))
      words[i] = wordIndex.addToIndex(sentence.get(i).word());
      //else
      //words[i] = wordIndex.indexOf(Lexicon.UNKNOWN_WORD);
    }
    for (int head = 0; head < length; head++) {
      for (int tag = 0; tag < numTags; tag++) {
        Arrays.fill(iScoreH[head][tag], Float.NEGATIVE_INFINITY);
        Arrays.fill(oScoreH[head][tag], Float.NEGATIVE_INFINITY);
        if (doiScoreHSum) {
          Arrays.fill(iScoreHSum[head][tag], Float.NEGATIVE_INFINITY);
        }
      }
    }
    for (int head = 0; head < length; head++) {
      for (int loc = 0; loc <= length; loc++) {
        rawDistance[head][loc] = (head >= loc ? head - loc : loc - head - 1);
        binDistance[head][loc] = dg.distanceBin(rawDistance[head][loc]);
      }
    }

    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }

    // do tags
    for (int start = 0; start + 1 <= length; start++) {

      //Force tags
      String trueTagStr = null;
      if (sentence.get(start) instanceof HasTag) {
        trueTagStr = ((HasTag) sentence.get(start)).tag();
        if ("".equals(trueTagStr)) {
          trueTagStr = null;
        }
      }

      //Word context (e.g., morphosyntactic info)
      String wordContextStr = null;
      if(sentence.get(start) instanceof HasContext) {
        wordContextStr = ((HasContext) sentence.get(start)).originalText();
        if("".equals(wordContextStr))
          wordContextStr = null;
      }

      int word = words[start];
      for (Iterator<IntTaggedWord> taggingI = lex.ruleIteratorByWord(word, start, wordContextStr); taggingI.hasNext();) {
        IntTaggedWord tagging = taggingI.next();
        if (trueTagStr != null) {
          if (!tlp.basicCategory(tagging.tagString(tagIndex)).equals(trueTagStr)) {
            continue;
          }
        }
        float score = lex.score(tagging, start, wordIndex.get(tagging.word), wordContextStr);
        //iScoreH[start][tag][start] = (op.dcTags ? (float)op.testOptions.depWeight*score : 0.0f);
        if (score > Float.NEGATIVE_INFINITY) {
          int tag = tagging.tag;
          iScoreH[start][dg.tagBin(tag)][start] = 0.0f;
          iScoreH[start][dg.tagBin(tag)][start + 1] = 0.0f;
          if (doiScoreHSum) {
            iScoreHSum[start][dg.tagBin(tag)][start] = 0.0f;
            iScoreHSum[start][dg.tagBin(tag)][start+1] = 0.0f;
          }
          if (DEBUG) log.info("DepParser accepted tagging: " + wordIndex.get(tagging.word)+"|"+tagIndex.get(tagging.tag) + ", got score " + score);
        }
      }
    }
    for (int hWord = 0; hWord < length; hWord++) {
      for (int hTag = 0; hTag < numTags; hTag++) {
        hasTag[hWord][hTag] = (iScoreH[hWord][hTag][hWord] + iScoreH[hWord][hTag][hWord + 1] > Float.NEGATIVE_INFINITY);
        Arrays.fill(headStop[hWord][hTag], Float.NEGATIVE_INFINITY);
        for (int aWord = 0; aWord < length; aWord++) {
          for (int dist = 0; dist < dg.numDistBins(); dist++) {
            Arrays.fill(headScore[dist][hWord][hTag][aWord], Float.NEGATIVE_INFINITY);
          }
        }
      }
    }
    // score and cache all pairs -- headScores and stops
    //int hit = 0;
    for (int hWord = 0; hWord < length; hWord++) {
      for (int hTag = 0; hTag < numTags; hTag++) {
        //Arrays.fill(headStopL[hWord][hTag], Float.NEGATIVE_INFINITY);
        //Arrays.fill(headStopR[hWord][hTag], Float.NEGATIVE_INFINITY);
        //Arrays.fill(headStop[hWord][hTag], Float.NEGATIVE_INFINITY);
        if (!hasTag[hWord][hTag]) {
          continue;
        }
        for (int split = 0; split <= length; split++) {
          if (split <= hWord) {
            headStop[hWord][hTag][split] = (float) dg.scoreTB(words[hWord], hTag, -2, -2, false, hWord - split);
            //System.out.println("headstopL " + hWord +" " + hTag + " " + split + " " + headStopL[hWord][hTag][split]); // debugging
          } else {
            headStop[hWord][hTag][split] = (float) dg.scoreTB(words[hWord], hTag, -2, -2, true, split - hWord - 1);
            //System.out.println("headstopR " + hWord +" " + hTag + " " + split + " " + headStopR[hWord][hTag][split]); // debugging
          }
          //hit++;
        }
        //Timing.tick("hWord: "+hWord+" hTag: "+hTag+" piddle count: "+hit);
        for (int aWord = 0; aWord < length; aWord++) {
          if (aWord == hWord) {
            continue;  // can't be argument of yourself
          }
          boolean leftHeaded = hWord < aWord;
          int start;
          int end;
          if (leftHeaded) {
            start = hWord + 1;
            end = aWord + 1;
          } else {
            start = aWord + 1;
            end = hWord + 1;
          }
          for (int aTag = 0; aTag < numTags; aTag++) {
            if ( ! hasTag[aWord][aTag]) {
              continue;
            }
            for (int split = start; split < end; split++) {
              // Moved this stuff out two loops- GMA
              //              for (int split = 0; split <= length; split++) {
              // if leftHeaded, go from hWord+1 to aWord
              // else go from aWord+1 to hWord
              //              if ((leftHeaded && (split <= hWord || split > aWord)) ||
              //                      ((!leftHeaded) && (split <= aWord || split > hWord)))
              //                continue;
              int headDistance = rawDistance[hWord][split];
              int binDist = binDistance[hWord][split];
              headScore[binDist][hWord][hTag][aWord][aTag] = (float) dg.scoreTB(words[hWord], hTag, words[aWord], aTag, leftHeaded, headDistance);
              //hit++;
              if (DEBUG) {
                log.info("Dep score head -> dep: " + wordIndex.get(words[hWord]) + "/" + tagIndex.get(hTag) + "[" + hWord + "] -> " + wordIndex.get(words[aWord]) + "/" + tagIndex.get(aTag) + "[" + aWord + "] split [" + split + "] = " + headScore[binDist][hWord][hTag][aWord][aTag]);
              }
              // skip other splits with same binDist
              while (split + 1 < end && binDistance[hWord][split + 1] == binDist) {
                split++;
              }
            } // end split
          } // end aTag
        } // end aWord
      } // end hTag
    } // end hWord
    if (op.testOptions.verbose) {
      Timing.tick("done.");
      // displayHeadScores();
      log.info("Starting insides...");
    }
    // do larger spans
    for (int diff = 2; diff <= length; diff++) {
      if (Thread.interrupted()) {
        throw new RuntimeInterruptedException();
      }
      if (DEBUG_MORE) log.info("SPAN " + diff + ": score = headPrev + argLeft + argRight + dep + argLStop + argRStop");
      for (int start = 0; start + diff <= length; start++) {
        int end = start + diff;


        // left extension
        int endHead = end - 1;
        for (int endTag = 0; endTag < numTags; endTag++) {
          if ( ! hasTag[endHead][endTag]) {
            continue;
          }
          // bestScore is max for iScoreH
          float bestScore = Float.NEGATIVE_INFINITY;

          for (int argHead = start; argHead < endHead; argHead++) {
            for (int argTag = 0; argTag < numTags; argTag++) {
              if (!hasTag[argHead][argTag]) {
                continue;
              }
              float argLeftScore = iScoreH[argHead][argTag][start];
              if (argLeftScore == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float stopLeftScore = headStop[argHead][argTag][start];
              if (stopLeftScore == Float.NEGATIVE_INFINITY) {
                continue;
              }
              for (int split = argHead + 1; split < end; split++) {
                // short circuit if dependency is impossible
                float depScore = headScore[binDistance[endHead][split]][endHead][endTag][argHead][argTag];
                if (depScore == Float.NEGATIVE_INFINITY) {
                  continue;
                }
                float score = iScoreH[endHead][endTag][split] + argLeftScore + iScoreH[argHead][argTag][split] + depScore + stopLeftScore + headStop[argHead][argTag][split];
                if (DEBUG_MORE) {
                  log.info("Left extend " + wordIndex.get(words[endHead]) + "/" + tagIndex.get(endTag) + "[" + endHead + "] -> " + wordIndex.get(words[argHead]) + "/" + tagIndex.get(argTag) + "[" + argHead + "](" + start + "," + split + ")");
                  log.info("  " + score + " = SUM " + iScoreH[endHead][endTag][split] + " " + argLeftScore + " " + iScoreH[argHead][argTag][split] + " " + depScore + " " + headStop[argHead][argTag][start] + " " + headStop[argHead][argTag][split]);
                }
                if (score > bestScore) {
                  bestScore = score;
                }
              } // end for split
              // sum for iScoreHSum
              if (doiScoreHSum) {
                double p = Math.exp(iScoreHSum[endHead][endTag][start]);
                for (int split = argHead + 1; split < end; split++) {
                  p += Math.exp(iScoreH[argHead][argTag][start] +
                                iScoreH[argHead][argTag][split] +
                                headScore[binDistance[endHead][split]][endHead][endTag][argHead][argTag] +
                                headStop[argHead][argTag][start] +
                                headStop[argHead][argTag][split]);
                }
                iScoreHSum[endHead][endTag][start] = (float)Math.log(p);
              }
            } // end for argTag : tags
          } // end for argHead

          iScoreH[endHead][endTag][start] = bestScore;

        } // end for endTag : tags
        // right extension
        int startHead = start;
        for (int startTag = 0; startTag < numTags; startTag++) {
          if ( ! hasTag[startHead][startTag]) {
            continue;
          }
          // bestScore is max for iScoreH
          float bestScore = Float.NEGATIVE_INFINITY;

          for (int argHead = start + 1; argHead < end; argHead++) {
            for (int argTag = 0; argTag < numTags; argTag++) {
              if (!hasTag[argHead][argTag]) {
                continue;
              }
              float argRightScore = iScoreH[argHead][argTag][end];
              if (argRightScore == Float.NEGATIVE_INFINITY) {
                continue;
              }
              float stopRightScore = headStop[argHead][argTag][end];
              if (stopRightScore == Float.NEGATIVE_INFINITY) {
                continue;
              }
              for (int split = start + 1; split <= argHead; split++) {
                // short circuit if dependency is impossible
                float depScore = headScore[binDistance[startHead][split]][startHead][startTag][argHead][argTag];
                if (depScore == Float.NEGATIVE_INFINITY) {
                  continue;
                }
                float score = iScoreH[startHead][startTag][split] + iScoreH[argHead][argTag][split] + argRightScore + depScore + stopRightScore + headStop[argHead][argTag][split];
                if (DEBUG_MORE) {
                  log.info("Right extend " + wordIndex.get(words[startHead]) + "/" + tagIndex.get(startTag) + "[" + startHead + "] -> " + wordIndex.get(words[argHead]) + "/" + tagIndex.get(argTag) + "[" + argHead + "](" + split + "," + end + ")");
                  log.info("  " + score + " = SUM " + iScoreH[startHead][startTag][split] + " " + iScoreH[argHead][argTag][split] + " " + argRightScore + " " + depScore + " " + headStop[argHead][argTag][end] + " " + headStop[argHead][argTag][split]);
                }
                if (score > bestScore) {
                  bestScore = score;
                }
              }

              // sum for iScoreHSum
              if (doiScoreHSum) {
                double p = Math.exp(iScoreHSum[startHead][startTag][end]);
                for (int split = argHead + 1; split < end; split++) {
                  p += Math.exp(iScoreH[startHead][startTag][split] +
                      iScoreH[argHead][argTag][split] +
                      iScoreH[argHead][argTag][end] +
                      headScore[binDistance[startHead][split]][startHead][startTag][argHead][argTag] +
                      headStop[argHead][argTag][end] +
                      headStop[argHead][argTag][split]);
                }
                iScoreHSum[startHead][startTag][end] = (float)Math.log(p);
              }

            } // end for argTag: tags
          } // end for argHead

          iScoreH[startHead][startTag][end] = bestScore;

        } // end for startTag: tags
      } // end for start
    } // end for diff (i.e., span)
    int goalTag = dg.tagBin(tagIndex.indexOf(Lexicon.BOUNDARY_TAG));
    if (op.testOptions.verbose) {
      Timing.tick("done.");
      log.info("Dep  parsing " + length + " words (incl. stop): insideScore " + (iScoreH[length - 1][goalTag][0] + iScoreH[length - 1][goalTag][length]));
    }
    if ( ! op.doPCFG) {
      return hasParse();
    }
    if (op.testOptions.verbose) {
      log.info("Starting outsides...");
    }
    oScoreH[length - 1][goalTag][0] = 0.0f;
    oScoreH[length - 1][goalTag][length] = 0.0f;
    for (int diff = length; diff > 1; diff--) {
      if (Thread.interrupted()) {
        throw new RuntimeInterruptedException();
      }
      for (int start = 0; start + diff <= length; start++) {
        int end = start + diff;
        // left half
        int endHead = end - 1;
        for (int endTag = 0; endTag < numTags; endTag++) {
          if (!hasTag[endHead][endTag]) {
            continue;
          }
          for (int argHead = start; argHead < endHead; argHead++) {
            for (int argTag = 0; argTag < numTags; argTag++) {
              if (!hasTag[argHead][argTag]) {
                continue;
              }
              for (int split = argHead; split <= endHead; split++) {
                float subScore = (oScoreH[endHead][endTag][start] + headScore[binDistance[endHead][split]][endHead][endTag][argHead][argTag] + headStop[argHead][argTag][start] + headStop[argHead][argTag][split]);
                float scoreRight = (subScore + iScoreH[argHead][argTag][start] + iScoreH[argHead][argTag][split]);
                float scoreMid = (subScore + iScoreH[argHead][argTag][start] + iScoreH[endHead][endTag][split]);
                float scoreLeft = (subScore + iScoreH[argHead][argTag][split] + iScoreH[endHead][endTag][split]);
                if (scoreRight > oScoreH[endHead][endTag][split]) {
                  oScoreH[endHead][endTag][split] = scoreRight;
                }
                if (scoreMid > oScoreH[argHead][argTag][split]) {
                  oScoreH[argHead][argTag][split] = scoreMid;
                }
                if (scoreLeft > oScoreH[argHead][argTag][start]) {
                  oScoreH[argHead][argTag][start] = scoreLeft;
                }
              }
            }
          }
        }
        // right half
        int startHead = start;
        for (int startTag = 0; startTag < numTags; startTag++) {
          if (!hasTag[startHead][startTag]) {
            continue;
          }
          for (int argHead = startHead + 1; argHead < end; argHead++) {
            for (int argTag = 0; argTag < numTags; argTag++) {
              if (!hasTag[argHead][argTag]) {
                continue;
              }
              for (int split = startHead + 1; split <= argHead; split++) {
                float subScore = (oScoreH[startHead][startTag][end] + headScore[binDistance[startHead][split]][startHead][startTag][argHead][argTag] + headStop[argHead][argTag][split] + headStop[argHead][argTag][end]);
                float scoreLeft = (subScore + iScoreH[argHead][argTag][split] + iScoreH[argHead][argTag][end]);
                float scoreMid = (subScore + iScoreH[startHead][startTag][split] + iScoreH[argHead][argTag][end]);
                float scoreRight = (subScore + iScoreH[startHead][startTag][split] + iScoreH[argHead][argTag][split]);
                if (scoreLeft > oScoreH[startHead][startTag][split]) {
                  oScoreH[startHead][startTag][split] = scoreLeft;
                }
                if (scoreMid > oScoreH[argHead][argTag][split]) {
                  oScoreH[argHead][argTag][split] = scoreMid;
                }
                if (scoreRight > oScoreH[argHead][argTag][end]) {
                  oScoreH[argHead][argTag][end] = scoreRight;
                }
              }
            }
          }
        }
      }
    }
    if (op.testOptions.verbose) {
      Timing.tick("done.");
      log.info("Starting half-filters...");
    }
    for (int loc = 0; loc <= length; loc++) {
      for (int head = 0; head < length; head++) {
        Arrays.fill(iPossibleByL[loc][head], false);
        Arrays.fill(iPossibleByR[loc][head], false);
        Arrays.fill(oPossibleByL[loc][head], false);
        Arrays.fill(oPossibleByR[loc][head], false);
      }
    }
    if (Thread.interrupted()) {
      throw new RuntimeInterruptedException();
    }
    for (int head = 0; head < length; head++) {
      for (int tag = 0; tag < numTags; tag++) {
        if (!hasTag[head][tag]) {
          continue;
        }
        for (int start = 0; start <= head; start++) {
          for (int end = head + 1; end <= length; end++) {
            if (iScoreH[head][tag][start] + iScoreH[head][tag][end] > Float.NEGATIVE_INFINITY && oScoreH[head][tag][start] + oScoreH[head][tag][end] > Float.NEGATIVE_INFINITY) {
              iPossibleByR[end][head][tag] = true;
              iPossibleByL[start][head][tag] = true;
              oPossibleByR[end][head][tag] = true;
              oPossibleByL[start][head][tag] = true;
            }
          }
        }
      }
    }
    if (op.testOptions.verbose) {
      Timing.tick("done.");
    }
    return hasParse();
  }

  @Override
  public boolean hasParse() {
    return getBestScore() > Float.NEGATIVE_INFINITY;
  }

  @Override
  public double getBestScore() {
    if (sentence == null) {
      return Float.NEGATIVE_INFINITY;
    }
    int length = sentence.size();
    if (length > arraySize) {
      return Float.NEGATIVE_INFINITY;
    }
    int goalTag = tagIndex.indexOf(Lexicon.BOUNDARY_TAG);
    return iScore(0, length, length - 1, goalTag);
  }

  /**
   * This displays a headScore matrix, which will be valid after parsing
   * a sentence.  Unclear yet whether this is valid/useful [cdm].
   */
  public void displayHeadScores() {
    int numTags = tagIndex.size();
    System.out.println("---- headScore matrix (head x dep, best tags) ----");
    System.out.print(StringUtils.padOrTrim("", 6));
    for (int word : words) {
      System.out.print(" " + StringUtils.padOrTrim(wordIndex.get(word), 2));
    }
    System.out.println();
    for (int hWord = 0; hWord < words.length; hWord++) {
      System.out.print(StringUtils.padOrTrim(wordIndex.get(words[hWord]), 6));
      int bigBD = -1, bigHTag = -1, bigATag = -1;
      for (int aWord = 0; aWord < words.length; aWord++) {
        // we basically just max of all the variables, but for distance > 0, we
        // include a factor for generating something at distance 0, or else
        // the result is too whacked out to be useful
        float biggest = Float.NEGATIVE_INFINITY;
        for (int bd = 0; bd < dg.numDistBins(); bd++) {
          for (int hTag = 0; hTag < numTags; hTag++) {
            /*
            float penalty = 0.0f;
            if (bd != 0) {
              penalty = (float) dg.score(words[hWord], hTag, -2, -2, aWord > hWord, 0);
              penalty = (float) Math.log(1.0 - Math.exp(penalty));
            }
            for (int aTag = 0; aTag < numTags; aTag++) {
              if (headScore[bd][hWord][hTag][aWord][aTag] + penalty > biggest) {
                biggest = headScore[bd][hWord][hTag][aWord][aTag] + penalty;
            */
            for (int aTag = 0; aTag < numTags; aTag++) {
              if (headScore[bd][hWord][dg.tagBin(hTag)][aWord][dg.tagBin(aTag)] > biggest) {
                biggest = headScore[bd][hWord][dg.tagBin(hTag)][aWord][dg.tagBin(aTag)];
                bigBD = bd;
                bigHTag = hTag;
                bigATag = aTag;
              }
            }
          }
        }
        if (Float.isInfinite(biggest)) {
          System.out.print(" " + StringUtils.padOrTrim("in", 2));
        } else {
          int score = Math.round(Math.abs(headScore[bigBD][hWord][dg.tagBin(bigHTag)][aWord][dg.tagBin(bigATag)]));
          System.out.print(" " + StringUtils.padOrTrim(Integer.toString(score), 2));
        }
      }
      System.out.println();
    }
  }

  private static final double TOL = 1e-5;

  private static boolean matches(double x, double y) {
    return (Math.abs(x - y) / (Math.abs(x) + Math.abs(y) + 1e-10) < TOL);
  }

  /** Find the best (partial) parse within the parameter constraints.
   *  @param start Sentence index of start of span (fenceposts, from 0 up)
   *  @param end   Sentence index of end of span (right side fencepost)
   *  @param hWord Sentence index of head word (left side fencepost)
   *  @param hTag  Tag assigned to hWord
   *  @return The best parse tree within the parameter constraints
   */
  private Tree extractBestParse(int start, int end, int hWord, int hTag) {
    if (DEBUG) {
      log.info("Span "+start+" to "+end+" word "+wordIndex.get(words[hWord])+"/"+hWord+" tag "+tagIndex.get(hTag)+"/"+hTag+" score "+iScore(start, end, hWord, hTag));
    }
    String headWordStr = wordIndex.get(words[hWord]);
    String headTagStr = tagIndex.get(hTag);
    Label headLabel = new CategoryWordTag(headWordStr, headWordStr, headTagStr);
    int numTags = tagIndex.size();

    // deal with span 1
    if (end - start == 1) {
      Tree leaf = tf.newLeaf(new Word(headWordStr));
      return tf.newTreeNode(headLabel, Collections.singletonList(leaf));
    }
    // find backtrace
    List<Tree> children = new ArrayList<>();
    double bestScore = iScore(start, end, hWord, hTag);
    for (int split = start + 1; split < end; split++) {
      int binD = binDistance[hWord][split];
      if (hWord < split) {
        for (int aWord = split; aWord < end; aWord++) {
          for (int aTag = 0; aTag < numTags; aTag++) {
            if (matches(iScore(start, split, hWord, hTag) + iScore(split, end, aWord, aTag) + headScore[binD][hWord][dg.tagBin(hTag)][aWord][dg.tagBin(aTag)] + headStop[aWord][dg.tagBin(aTag)][split] + headStop[aWord][dg.tagBin(aTag)][end], bestScore)) {
              if (DEBUG) {
                String argWordStr = wordIndex.get(words[aWord]);
                String argTagStr = tagIndex.get(aTag);
                log.info(headWordStr+"|"+headTagStr+" -> "+argWordStr+"|"+argTagStr+" "+bestScore);
              }
              // build it
              children.add(extractBestParse(start, split, hWord, hTag));
              children.add(extractBestParse(split, end, aWord, aTag));
              return tf.newTreeNode(headLabel, children);
            }
          }
        }
      } else {
        for (int aWord = start; aWord < split; aWord++) {
          for (int aTag = 0; aTag < numTags; aTag++) {
            if (matches(iScore(start, split, aWord, aTag) + iScore(split, end, hWord, hTag) + headScore[binD][hWord][dg.tagBin(hTag)][aWord][dg.tagBin(aTag)] + headStop[aWord][dg.tagBin(aTag)][start] + headStop[aWord][dg.tagBin(aTag)][split], bestScore)) {
              if (DEBUG) {
                String argWordStr = wordIndex.get(words[aWord]);
                String argTagStr = tagIndex.get(aTag);
                log.info(headWordStr+"|"+headTagStr+" -> "+argWordStr+"|"+argTagStr+" "+bestScore);
              }
              children.add(extractBestParse(start, split, aWord, aTag));
              children.add(extractBestParse(split, end, hWord, hTag));
              // build it
              return tf.newTreeNode(headLabel, children);
            }
          }
        }
      }
    }
    log.info("Problem in ExhaustiveDependencyParser::extractBestParse");
    return null;
  }

  private Tree flatten(Tree tree) {
    if (tree.isLeaf() || tree.isPreTerminal()) {
      return tree;
    }
    List<Tree> newChildren = new ArrayList<>();
    Tree[] children = tree.children();
    for (Tree child : children) {
      Tree newChild = flatten(child);
      if (!newChild.isPreTerminal() && newChild.label().toString().equals(tree.label().toString())) {
        newChildren.addAll(newChild.getChildrenAsList());
      } else {
        newChildren.add(newChild);
      }
    }
    return tf.newTreeNode(tree.label(), newChildren);
  }


  /** Return the best dependency parse for a sentence.  You must call
   *  {@code parse()} before a call to this method.
   *  <p>
   *  <i>Implementation note:</i> the best parse is recalculated from the chart
   *  each time this method is called.  It isn't cached.
   *
   *  @return The best dependency parse for a sentence or {@code null}.
   *    The returned tree will begin with a binary branching node, the
   *    left branch of which is the dependency tree proper, and the right
   *    side of which contains a boundary word .$. which heads the
   *    sentence.
   */
  @Override
  public Tree getBestParse() {
    if ( ! hasParse()) {
      return null;
    }
    return flatten(extractBestParse(0, words.length, words.length - 1, tagIndex.indexOf(Lexicon.BOUNDARY_TAG)));
  }

  public ExhaustiveDependencyParser(DependencyGrammar dg, Lexicon lex, Options op, Index<String> wordIndex, Index<String> tagIndex) {
    this.dg = dg;
    this.lex = lex;
    this.op = op;
    this.tlp = op.langpack();
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    tf = new LabeledScoredTreeFactory();
  }

  private void createArrays(int length) {
    iScoreH = oScoreH = headStop = iScoreHSum = null;
    iPossibleByL = iPossibleByR = oPossibleByL = oPossibleByR = null;
    headScore = null;
    rawDistance = binDistance = null;

    int tagNum = dg.numTagBins(); //tagIndex.size();

    iScoreH = new float[length + 1][tagNum][length + 1];
    oScoreH = new float[length + 1][tagNum][length + 1];
    if (doiScoreHSum) {
      iScoreHSum = new float[length + 1][tagNum][length + 1];
    }
    iPossibleByL = new boolean[length + 1][length + 1][tagNum];
    iPossibleByR = new boolean[length + 1][length + 1][tagNum];
    oPossibleByL = new boolean[length + 1][length + 1][tagNum];
    oPossibleByR = new boolean[length + 1][length + 1][tagNum];
    headScore = new float[dg.numDistBins()][length][tagNum][length][tagNum];
    headStop = new float[length + 1][tagNum][length + 1];
    rawDistance = new int[length + 1][length + 1];
    binDistance = new int[length + 1][length + 1];
  }

  /** Get the exact k best parses for the sentence.
   *
   *  @param k The number of best parses to return
   *  @return The exact k best parses for the sentence, with
   *         each accompanied by its score (typically a
   *         negative log probability).
   */
  @Override
  public List<ScoredObject<Tree>> getKBestParses(int k) {
    throw new UnsupportedOperationException("Doesn't do k best yet");
  }

  /** Get a complete set of the maximally scoring parses for a sentence,
   *  rather than one chosen at random.  This set may be of size 1 or larger.
   *
   *  @return All the equal best parses for a sentence, with each
   *         accompanied by its score
   */
  @Override
  public List<ScoredObject<Tree>> getBestParses() {
    throw new UnsupportedOperationException("Doesn't do best parses yet");
  }

  /** Get k good parses for the sentence.  It is expected that the
   *  parses returned approximate the k best parses, but without any
   *  guarantee that the exact list of k best parses has been produced.
   *  If a class really provides k best parses functionality, it is
   *  reasonable to also return this output as the k good parses.
   *
   *  @param k The number of good parses to return
   *  @return A list of k good parses for the sentence, with
   *         each accompanied by its score
   */
  @Override
  public List<ScoredObject<Tree>> getKGoodParses(int k) {
    throw new UnsupportedOperationException("Doesn't do k good yet");
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
    throw new UnsupportedOperationException("Doesn't do k sampled yet");
  }

}
