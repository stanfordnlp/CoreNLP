package edu.stanford.nlp.parser.lexparser;

import java.util.*;

import edu.stanford.nlp.util.Index;

/** This class is currently unused.
 *  @author Dan Klein
 */
public class OutsideRuleFilter {

  private final Index<String> tagIndex;
  private int numTags;
  private int numFAs;

  private FA[] leftFA;
  private FA[] rightFA;

  protected static <A> List<A> reverse(List<A> list) {
    int sz = list.size();
    List<A> reverse = new ArrayList<>(sz);
    for (int i = sz - 1; i >= 0; i--) {
      reverse.add(list.get(i));
    }
    return reverse;
  }

  private FA buildFA(List<String> tags) {
    FA fa = new FA(tags.size() + 1, numTags);
    fa.setLoopState(0, true);
    for (int state = 1; state <= tags.size(); state++) {
      String tagO = tags.get(state - 1);
      if (tagO == null) {
        fa.setLoopState(state, true);
        for (int symbol = 0; symbol < numTags; symbol++) {
          fa.setTransition(state - 1, symbol, state);
        }
      } else {
        int tag = tagIndex.indexOf(tagO);
        fa.setTransition(state - 1, tag, state);
      }
    }
    return fa;
  }

  private void registerRule(List<String> leftTags, List<String> rightTags, int state) {
    leftFA[state] = buildFA(leftTags);
    rightFA[state] = buildFA(reverse(rightTags));
  }

  public void init() {
    for (int rule = 0; rule < numFAs; rule++) {
      leftFA[rule].init();
      rightFA[rule].init();
    }
  }

  public void advanceRight(boolean[] tags) {
    for (int tag = 0; tag < numTags; tag++) {
      if (!tags[tag]) {
        continue;
      }
      for (int rule = 0; rule < numFAs; rule++) {
        leftFA[rule].input(tag);
      }
    }
    for (int rule = 0; rule < numFAs; rule++) {
      leftFA[rule].advance();
    }
  }

  public void leftAccepting(boolean[] result) {
    for (int rule = 0; rule < numFAs; rule++) {
      result[rule] = leftFA[rule].isAccepting();
    }
  }

  public void advanceLeft(boolean[] tags) {
    for (int tag = 0; tag < numTags; tag++) {
      if (!tags[tag]) {
        continue;
      }
      for (int rule = 0; rule < numFAs; rule++) {
        rightFA[rule].input(tag);
      }
    }
    for (int rule = 0; rule < numFAs; rule++) {
      rightFA[rule].advance();
    }
  }

  public void rightAccepting(boolean[] result) {
    for (int rule = 0; rule < numFAs; rule++) {
      result[rule] = rightFA[rule].isAccepting();
    }
  }

  private void allocate(int numFAs) {
    this.numFAs = numFAs;
    leftFA = new FA[numFAs];
    rightFA = new FA[numFAs];
  }

  public OutsideRuleFilter(BinaryGrammar bg, Index<String> stateIndex, Index<String> tagIndex) {
    this.tagIndex = tagIndex;
    int numStates = stateIndex.size();
    numTags = tagIndex.size();
    allocate(numStates);
    for (int state = 0; state < numStates; state++) {
      String stateStr = stateIndex.get(state);
      List<String> left = new ArrayList<>();
      List<String> right = new ArrayList<>();
      if (!bg.isSynthetic(state)) {
        registerRule(left, right, state);
        continue;
      }
      boolean foundSemi = false;
      boolean foundDots = false;
      List<String> array = left;
      StringBuilder sb = new StringBuilder();
      for (int c = 0; c < stateStr.length(); c++) {
        if (stateStr.charAt(c) == ':') {
          foundSemi = true;
          continue;
        }
        if (!foundSemi) {
          continue;
        }
        if (stateStr.charAt(c) == ' ') {
          if (sb.length() > 0) {
            String str = sb.toString();
            if (!tagIndex.contains(str)) {
              str = null;
            }
            array.add(str);
            sb = new StringBuilder();
          }
          continue;
        }
        if (!foundDots && stateStr.charAt(c) == '.') {
          c += 3;
          foundDots = true;
          array = right;
          continue;
        }
        sb.append(stateStr.charAt(c));
      }
      registerRule(left, right, state);
    }
  }

  /** This is a simple Finite Automaton implementation. */
  static class FA {

    private boolean[] inStatePrev;
    private boolean[] inStateNext;
    private final boolean[] loopState;
    private final int acceptingState;
    private static final int initialState = 0;
    private final int numStates;
    private final int numSymbols;
    private final int[][] transition; // state x tag

    public void init() {
      Arrays.fill(inStatePrev, false);
      Arrays.fill(inStateNext, false);
      inStatePrev[initialState] = true;
    }

    public void input(int symbol) {
      for (int prevState = 0; prevState < numStates; prevState++) {
        if (inStatePrev[prevState]) {
          inStateNext[transition[prevState][symbol]] = true;
        }
      }
    }

    public void advance() {
      boolean[] temp = inStatePrev;
      inStatePrev = inStateNext;
      inStateNext = temp;
      Arrays.fill(inStateNext, false);
      for (int state = 0; state < numStates; state++) {
        if (inStatePrev[state] && loopState[state]) {
          inStateNext[state] = true;
        }
      }
    }

    public boolean isAccepting() {
      return inStatePrev[acceptingState];
    }

    public void setTransition(int state, int symbol, int result) {
      transition[state][symbol] = result;
    }

    public void setLoopState(int state, boolean loops) {
      loopState[state] = loops;
    }

    public FA(int numStates, int numSymbols) {
      this.numStates = numStates;
      this.numSymbols = numSymbols;
      acceptingState = numStates - 1;
      inStatePrev = new boolean[numStates];
      inStateNext = new boolean[numStates];
      loopState = new boolean[numStates];
      transition = new int[numStates][numSymbols];
    }

  } // end class FA

} // end class OutsideRuleFilter
