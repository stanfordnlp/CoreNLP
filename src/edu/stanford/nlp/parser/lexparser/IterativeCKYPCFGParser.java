package edu.stanford.nlp.parser.lexparser;

import java.util.regex.Matcher;
import edu.stanford.nlp.util.Index;

/** Does iterative deepening search inside the CKY algorithm for faster
 *  parsing. This is still guaranteed to find the optimal parse.  This
 *  iterative deepening is only implemented in insideScores().
 *  Implements the algorithm described in Tsuruoka and Tsujii (2004)
 *  IJCNLP.
 *
 *  @author Christopher Manning
 */
public class IterativeCKYPCFGParser extends ExhaustivePCFGParser {

  private static final float STEP_SIZE = -11.0F; // value suggested in their paper

  public IterativeCKYPCFGParser(BinaryGrammar bg, UnaryGrammar ug, Lexicon lex, Options op, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex) {
    super(bg, ug, lex, op, stateIndex, wordIndex, tagIndex);
  }

  /** Fills in the iScore array of each category over each span
   *  of length 2 or more.
   */
  @Override
  void doInsideScores() {
    float threshold = STEP_SIZE;
    while ( ! doInsideScoresHelper(threshold)) {
      threshold += STEP_SIZE;
    }
  }

  /** Fills in the iScore array of each category over each spanof length 2
   *  or more, providing
   *  a state's probability is greater than a threshold.
   *
   *  @param threshold The threshold up to which to parse as a log
   *      probability (i.e., a non-positive number)
   *  @return true iff a parse was found with this threshold or else
   *      it has been determined that no parse exists.
   */
  private boolean doInsideScoresHelper(float threshold) {
    boolean prunedSomething = false;
    for (int diff = 2; diff <= length; diff++) {
      // usually stop one short because boundary symbol only combines
      // with whole sentence span
      for (int start = 0; start < ((diff == length) ? 1: length - diff); start++) {

        if (spillGuts) {
          tick("Binaries for span " + diff + "...");
        }
        int end = start + diff;

        if (getConstraints() != null) {
          boolean skip = false;
          for (ParserConstraint c : getConstraints()) {
            if ((start > c.start && start < c.end && end > c.end) || (end > c.start && end < c.end && start < c.start)) {
              skip = true;
              break;
            }
          }
          if (skip) {
            continue;
          }
        }

        for (int leftState = 0; leftState < numStates; leftState++) {

          int narrowR = narrowRExtent[start][leftState];
          boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
          if (!iPossibleL) {
            continue;
          }
          BinaryRule[] leftRules = bg.splitRulesWithLC(leftState);
          //      if (spillGuts) System.out.println("Found " + leftRules.length + " left rules for state " + stateIndex.get(leftState));
          for (int i = 0; i < leftRules.length; i++) {
            //      if (spillGuts) System.out.println("Considering rule for " + start + " to " + end + ": " + leftRules[i]);
            BinaryRule r = leftRules[i];

            int narrowL = narrowLExtent[end][r.rightChild];
            boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
            if (!iPossibleR) {
              continue;
            }
            int min1 = narrowR;
            int min2 = wideLExtent[end][r.rightChild];
            int min = (min1 > min2 ? min1 : min2);
            if (min > narrowL) { // can this right constituent stretch far enough to reach the left constituent?
              continue;
            }
            int max1 = wideRExtent[start][leftState];
            int max2 = narrowL;
            int max = (max1 < max2 ? max1 : max2);
            if (min > max) { // can this left constituent stretch far enough to reach the right constituent?
              continue;
            }
            float pS = r.score;
            int parentState = r.parent;
            float oldIScore = iScore[start][end][parentState];
            float bestIScore = oldIScore;
            boolean foundBetter;  // always set below for this rule
            //System.out.println("Min "+min+" max "+max+" start "+start+" end "+end);

            if (!op.testOptions.lengthNormalization) {
              // find the split that can use this rule to make the max score
              for (int split = min; split <= max; split++) {

                if (getConstraints() != null) {
                  boolean skip = false;
                  for (ParserConstraint c : getConstraints()) {
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
                      String tag = stateIndex.get(r.rightChild);
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

                float lS = iScore[start][split][leftState];
                if (lS == Float.NEGATIVE_INFINITY) {
                  continue;
                }
                float rS = iScore[split][end][r.rightChild];
                if (rS == Float.NEGATIVE_INFINITY) {
                  continue;
                }
                float tot = pS + lS + rS;
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
                float lS = iScore[start][split][leftState];
                if (lS == Float.NEGATIVE_INFINITY) {

                  continue;
                }
                float rS = iScore[split][end][r.rightChild];
                if (rS == Float.NEGATIVE_INFINITY) {
                  continue;
                }
                float tot = pS + lS + rS;
                int newWordsInSpan = wordsInSpan[start][split][leftState] + wordsInSpan[split][end][r.rightChild];
                float normTot = tot / newWordsInSpan;
                if (normTot > bestNormIScore) {
                  bestIScore = tot;
                  bestNormIScore = normTot;
                  bestWordsInSpan = newWordsInSpan;
                }
              } // for split point
              foundBetter = bestNormIScore > oldNormIScore;
              if (foundBetter && bestIScore > threshold) {
                wordsInSpan[start][end][parentState] = bestWordsInSpan;
              }
            } // fi op.testOptions.lengthNormalization
            if (foundBetter) {
              if (bestIScore > threshold) {
                // this way of making "parentState" is better than previous
                // and sufficiently good to be stored on this iteration
                iScore[start][end][parentState] = bestIScore;

                //              if (spillGuts) System.out.println("Could build " + stateIndex.get(parentState) + " from " + start + " to " + end);
                if (oldIScore == Float.NEGATIVE_INFINITY) {
                  if (start > narrowLExtent[end][parentState]) {
                    narrowLExtent[end][parentState] = start;
                    wideLExtent[end][parentState] = start;
                  } else {
                    if (start < wideLExtent[end][parentState]) {
                      wideLExtent[end][parentState] = start;
                    }
                  }
                  if (end < narrowRExtent[start][parentState]) {
                    narrowRExtent[start][parentState] = end;
                    wideRExtent[start][parentState] = end;
                  } else {
                    if (end > wideRExtent[start][parentState]) {
                      wideRExtent[start][parentState] = end;
                    }
                  }
                }
              } else {
                prunedSomething = true;
              }
            } // end if foundBetter
          } // end for leftRules
        } // end for leftState
        // do right restricted rules
        for (int rightState = 0; rightState < numStates; rightState++) {
          int narrowL = narrowLExtent[end][rightState];
          boolean iPossibleR = (narrowL > start);
          if (!iPossibleR) {
            continue;
          }
          BinaryRule[] rightRules = bg.splitRulesWithRC(rightState);
          //      if (spillGuts) System.out.println("Found " + rightRules.length + " right rules for state " + stateIndex.get(rightState));
          for (int i = 0; i < rightRules.length; i++) {
            //      if (spillGuts) System.out.println("Considering rule for " + start + " to " + end + ": " + rightRules[i]);
            BinaryRule r = rightRules[i];

            int narrowR = narrowRExtent[start][r.leftChild];
            boolean iPossibleL = (narrowR <= narrowL);
            if (!iPossibleL) {
              continue;
            }
            int min1 = narrowR;
            int min2 = wideLExtent[end][rightState];
            int min = (min1 > min2 ? min1 : min2);
            if (min > narrowL) {
              continue;
            }
            int max1 = wideRExtent[start][r.leftChild];
            int max2 = narrowL;
            int max = (max1 < max2 ? max1 : max2);
            if (min > max) {
              continue;
            }
            float pS = r.score;
            int parentState = r.parent;
            float oldIScore = iScore[start][end][parentState];
            float bestIScore = oldIScore;
            boolean foundBetter; // always initialized below
            //System.out.println("Start "+start+" end "+end+" min "+min+" max "+max);
            if (!op.testOptions.lengthNormalization) {
              // find the split that can use this rule to make the max score
              for (int split = min; split <= max; split++) {

                if (getConstraints() != null) {
                  boolean skip = false;
                  for (ParserConstraint c : getConstraints()) {
                    if (((start < c.start && end >= c.end) || (start <= c.start && end > c.end)) && split > c.start && split < c.end) {
                      skip = true;
                      break;
                    }
                    if ((start == c.start && split == c.end)) {
                      String tag = stateIndex.get(r.leftChild);
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

                float lS = iScore[start][split][r.leftChild];
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
                float lS = iScore[start][split][r.leftChild];
                if (lS == Float.NEGATIVE_INFINITY) {
                  continue;
                }
                float rS = iScore[split][end][rightState];
                if (rS == Float.NEGATIVE_INFINITY) {
                  continue;
                }
                float tot = pS + lS + rS;
                int newWordsInSpan = wordsInSpan[start][split][r.leftChild] + wordsInSpan[split][end][rightState];
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
              if (bestIScore > threshold) {
                iScore[start][end][parentState] = bestIScore;
                //              if (spillGuts) System.out.println("Could build " + stateIndex.get(parentState) + " from " + start + " to " + end);
                if (oldIScore == Float.NEGATIVE_INFINITY) {
                  if (start > narrowLExtent[end][parentState]) {
                    narrowLExtent[end][parentState] = start;
                    wideLExtent[end][parentState] = start;
                  } else {
                    if (start < wideLExtent[end][parentState]) {
                      wideLExtent[end][parentState] = start;
                    }
                  }
                  if (end < narrowRExtent[start][parentState]) {
                    narrowRExtent[start][parentState] = end;
                    wideRExtent[start][parentState] = end;
                  } else {
                    if (end > wideRExtent[start][parentState]) {
                      wideRExtent[start][parentState] = end;
                    }
                  }
                }
              } else {
                prunedSomething = true;
              }
            } // end if foundBetter
          } // for rightRules
        } // for rightState
        if (spillGuts) {
          tick("Unaries for span " + diff + "...");
        }
        // do unary rules -- one could promote this loop and put start inside
        for (int state = 0; state < numStates; state++) {
          float iS = iScore[start][end][state];
          if (iS == Float.NEGATIVE_INFINITY) {
            continue;
          }
          UnaryRule[] unaries = ug.closedRulesByChild(state);
          for (int r = 0; r < unaries.length; r++) {

            UnaryRule ur = unaries[r];

            if (getConstraints() != null) {
              boolean skip = false;
              for (ParserConstraint c : getConstraints()) {
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
            float cur = iScore[start][end][parentState];
            boolean foundBetter;  // always set below
            if (op.testOptions.lengthNormalization) {
              int totWordsInSpan = wordsInSpan[start][end][state];
              float normTot = tot / totWordsInSpan;
              int curWordsInSpan = wordsInSpan[start][end][parentState];
              float normCur = cur / curWordsInSpan;
              foundBetter = normTot > normCur;
              if (foundBetter && tot > threshold) {
                wordsInSpan[start][end][parentState] = wordsInSpan[start][end][state];
              }
            } else {
              foundBetter = (tot > cur);
            }
            if (foundBetter) {
              //              if (spillGuts) System.out.println("Could build " + stateIndex.get(parentState) + " from " + start + " to " + end);
              if (tot > threshold) {
                iScore[start][end][parentState] = tot;
                if (cur == Float.NEGATIVE_INFINITY) {
                  if (start > narrowLExtent[end][parentState]) {
                    narrowLExtent[end][parentState] = start;
                    wideLExtent[end][parentState] = start;
                  } else {
                    if (start < wideLExtent[end][parentState]) {
                      wideLExtent[end][parentState] = start;
                    }
                  }
                  if (end < narrowRExtent[start][parentState]) {
                    narrowRExtent[start][parentState] = end;
                    wideRExtent[start][parentState] = end;
                  } else {
                    if (end > wideRExtent[start][parentState]) {
                      wideRExtent[start][parentState] = end;
                    }
                  }
                }
              } else {
                prunedSomething = true;
              }
            } // end if foundBetter
          } // for UnaryRule r
        } // for unary rules
      } // for start
    } // for diff (i.e., span)
    int goal = stateIndex.indexOf(goalStr);
    // return true if found the goal, or nothing was pruned (i.e., sentence has no parse)
    return iScore[0][length][goal] > Float.NEGATIVE_INFINITY || ! prunedSomething;
  } // end doInsideScoresHelper()

}
