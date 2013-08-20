package edu.stanford.nlp.ie.hmm;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.HashIndex;


/**
 * Class to model an HMM structure.  This builds a straightforward
 * one-target-state structure, via a series of structure building operations.
 *
 * @author Jim McFadden
 * @author Christopher Manning
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public class Structure implements GeneralStructure, Serializable {
  /**
   *
   */
  private static final long serialVersionUID = 3110029489212688648L;
  protected List<List<Double>> transitions; // a List of Lists representing the
  // growable transition table
  // sensibly initialized extended internal version of transition matrix
  // for structure learning and API-driven structures
  protected transient double[][] initializedTransitions = null;
  // the index [i][j] in the vector is the transition weight from i
  // to j
  // constants for state types.  HN: TODO merge with the constants in
  // State.java
  public static final int PREFIX_TYPE = -4;
  public static final int SUFFIX_TYPE = -3;
  public static final int END_TYPE = -2;
  public static final int START_TYPE = -1;
  public static final int BACKGROUND_TYPE = 0;
  public static final int TARGET_TYPE = 1;

  // this list contains the type of each state
  protected List<Integer> stateTypes;

  // global objects to optimize references to common number objects
  public static final Double ONE = Double.valueOf(1.0);
  public static final Double ZERO = Double.valueOf(0.0);

  // mainly using these to support the legacy methods such as addPrefix,
  // lengthenPrefix, etc.
  // each list contains the state numbers for the first state in each
  // chain
  protected List<Integer> prefixes; // a prefix is a series of states leading to a target
  protected List<Integer> suffixes; // a suffix is a series of states coming from a target
  protected List<Integer> targets;  // states that emit the target words, the words we are trying to extract

  /**
   * Random used to perturb transitions. Change this to fix behavior
   * if desired.
   */
  public static Random rand = new Random();

  /**
   * Empty constructor.
   */
  public Structure() {
  }

  /**
   * Constructs a Structure using the transitions and state types
   * specified by <tt>states</tt>.  Assumes that each state knows its
   * type and that each state knows its transition  probabilities to
   * all the other states.  @see State
   */
  public Structure(State[] states) {
    transitions = new ArrayList<List<Double>>();
    stateTypes = new ArrayList<Integer>();
    for (int i = 0; i < states.length; i++) {
      if (states[i].transition.length != states.length) {
        System.err.println("State transitions do not form a square matrix");
        throw(new IllegalArgumentException());
      }
      List<Double> curTransitions = new ArrayList<Double>();
      for (int j = 0; j < states[i].transition.length; j++) {
        curTransitions.add(new Double(states[i].transition[j]));
      }
      transitions.add(curTransitions);
      stateTypes.add(Integer.valueOf(states[i].type));
    }
  }

  /**
   * Constructs a structure using the given transition and type params.
   */
  public Structure(double[][] transitions, int[] stateTypes) {
    this.transitions = new ArrayList<List<Double>>();
    this.stateTypes = new ArrayList<Integer>();
    if (transitions.length != stateTypes.length) {
      System.err.println("Dimensions of transition matrix do not match the number of states");
      return;
    }

    for (int i = 0; i < transitions.length; i++) {
      if (transitions[i].length != transitions.length) {
        System.err.println("The transitions matrix must be a square matrix");
        return;
      }
      ArrayList<Double> newTransitions = new ArrayList<Double>();
      for (int j = 0; j < transitions[i].length; j++) {
        newTransitions.add(new Double(transitions[i][j]));
      }
      this.transitions.add(newTransitions);
      this.stateTypes.add(Integer.valueOf(stateTypes[i]));
    }
  }

  /**
   * Inserts a new state of given type BEFORE the specified state.  For
   * example if we had (1 2)->3->4, insertState(3, Structure.PREFIX_TYPE) would
   * produce (1 2)->5->3->4.  Note that states that formerly went to
   * state 3 now go instead to the new state 5, and state 5 goes to 3
   * <p/>
   * the type argument is an integer type which is either a target type
   * (> 0) or one of the special types (Structure.BACKGROUND_TYPE,
   * Structure.PREFIX_TYPE, Structure.SUFFIX_TYPE)
   */
  public int insertStateBefore(int state, int type) {
    if (state == 1) {
      System.err.println("Can't insert before start state");
      return -1;
    }

    // create and add a transition array for the new state
    ArrayList<Double> newTransitions = new ArrayList<Double>();
    for (int i = 0; i < transitions.size(); i++) {
      // initialize the new array with zeros for the other states
      newTransitions.add(ZERO);
    }
    transitions.add(newTransitions);

    for (int i = 0; i < transitions.size(); i++) {
      ArrayList<Double> curTransitions = (ArrayList<Double>) transitions.get(i);
      Double tProb = curTransitions.get(state);
      // if a state previously transitioned to the specified state,
      // it now goes to the new state instead
      if (tProb.doubleValue() != 0.0 && i != state) {
        curTransitions.add(tProb);
        curTransitions.set(state, ZERO);
      } else {
        // otherwise, we just append a zero to the transition
        // table for the new state
        curTransitions.add(ZERO);
      }
    }
    // add a transition from the new state to the specified state
    newTransitions.set(state, ONE);
    // record the type of the new state
    stateTypes.add(Integer.valueOf(type));
    // return the number of the new state
    return transitions.size() - 1;
  }

  /**
   * Inserts a new state of given type AFTER the specified state.  For
   * example if we had 1->2->(3 4), insertState(2, Structure.SUFFIX_TYPE) would
   * produce 1->2->5->(3 4).  Note that state 2 formerly went to the two
   * states 3 and 4, but now goes instead to the new state 5, and state
   * 5 now goes to 3 and 4.
   * <p/>
   * the type argument is an integer type which is either a target type
   * (> 0) or one of the special types (Structure.BACKGROUND_TYPE,
   * Structure.PREFIX_TYPE, Structure.SUFFIX_TYPE)
   */
  public int insertStateAfter(int state, int type) {
    if (state == 0) {
      System.err.println("Can't insert after end state");
      return -1;
    }

    // create and add a transition array for the new state
    ArrayList<Double> newTransitions = new ArrayList<Double>();
    for (int i = 0; i < transitions.size(); i++) {
      // initialize the new array with zeros for the other states
      newTransitions.add(ZERO);
    }
    transitions.add(newTransitions);

    // extends the table by adding
    // a zero to all of the transition arrays for the new state
    for (List<Double> transition : transitions) {
      transition.add(ZERO);
    }

    ArrayList<Double> outTransitions = (ArrayList<Double>) transitions.get(state);

    for (int i = 0; i < outTransitions.size(); i++) {
      Double tProb = outTransitions.get(i);
      // if the state before transitions to a given state S, the new
      // state now goes to S instead
      if (tProb.doubleValue() != 0.0 && i != state) {
        newTransitions.set(i, tProb);
        outTransitions.set(i, ZERO);
      }
    }
    // the state before transitions only to the new state
    outTransitions.set(outTransitions.size() - 1, ONE);

    // record the type of the new state
    stateTypes.add(Integer.valueOf(type));
    // return the number of the new state
    return transitions.size() - 1;
  }

  /**
   * Inserts a new state between the given states
   * produce from -> new state -> to.
   * <p/>
   * the type argument is an integer type which is either a target type
   * (> 0) or one of the special types (Structure.BACKGROUND_TYPE,
   * Structure.PREFIX_TYPE, Structure.SUFFIX_TYPE)
   */
  public int insertStateBetween(int from, int to, int type) {
    // create and add a transition array for the new state
    List<Double> newTransitions = new ArrayList<Double>();
    for (int i = 0; i < transitions.size(); i++) {
      // initialize the new array with zeros for the other states
      newTransitions.add(ZERO);
    }
    // add transition from new state to "to"
    newTransitions.set(to, ONE);
    transitions.add(newTransitions);

    int stateNum = transitions.size() - 1;

    // extends the table by adding
    // a zero to all of the transition arrays for the new state
    for (int i = 0; i < transitions.size(); i++) {
      List<Double> curTransitions = transitions.get(i);
      curTransitions.add(ZERO);
      if (i == from) {
        // erase transition from "from" to "to"
        curTransitions.set(to, ZERO);
        // add transition from "from" to the new state
        curTransitions.set(stateNum, ONE);
      }
    }

    // record the type of the new state
    stateTypes.add(Integer.valueOf(type));
    // return the number of the new state
    return stateNum;
  }

  /**
   * Splits a state into two states of the same type using
   * {@link #splitState(int, int)}
   */
  public int splitState(int state) {
    Integer stateType = stateTypes.get(state);
    return splitState(state, stateType.intValue());
  }

  /**
   * Splits a state into two states with the same incoming and outgoing
   * transitions.  One new state of the given type is produced.
   * For example, if we have 1->2->3, then split(2) produces
   * 1->(2 4)->3.  Note that all of the transitions into state 2 also go to
   * the new state 4, and the transitions from state 2 are also in state 4
   */
  public int splitState(int state, int type) {
    if (state == 0 || state == 1) {
      System.err.println("Can't split start or end states");
      return -1;
    }

    // create and add a transition array for the new state
    ArrayList<Double> newTransitions = new ArrayList<Double>();
    for (int i = 0; i < transitions.size(); i++) {
      // initialize the new array with zeros for the other states
      newTransitions.add(ZERO);
    }
    transitions.add(newTransitions);

    // adds the new state to all of the transition arrays
    for (int i = 0; i < transitions.size(); i++) {
      ArrayList<Double> inTransitions = (ArrayList<Double>) transitions.get(i);

      Double tProb = inTransitions.get(state);
      // if the current state transitions into the state we are
      // splitting, then it also goes to the new state
      if (tProb.doubleValue() != 0.0 && i != state) {
        inTransitions.add(new Double(tProb.doubleValue()));
      } else {
        inTransitions.add(ZERO);
      }
    }

    List<Double> outTransitions = transitions.get(state);
    for (int i = 0; i < outTransitions.size(); i++) {
      Double tProb = outTransitions.get(i);
      // if the state we are splitting transitions to a given state
      // S, the new state also goes to S
      if (tProb.doubleValue() != 0.0) {
        newTransitions.set(i, new Double(tProb.doubleValue()));
      }
    }
    stateTypes.add(Integer.valueOf(type));

    // return the number of the new state
    return transitions.size() - 1;
  }

  /**
   * Removes the state from the structure.  State numbers following the deleted
   * state are decreased by 1
   */
  public void deleteState(int state) {
    int type = stateTypes.get(state).intValue();

    // remove from structure learning lists
    if (type == PREFIX_TYPE) {
      removeAllFromList(prefixes, Integer.valueOf(state));
    } else if (type == SUFFIX_TYPE) {
      removeAllFromList(suffixes, Integer.valueOf(state));
    } else if (type > 0) {
      removeAllFromList(targets, Integer.valueOf(state));
    }

    for (int i = 0; i < transitions.size(); i++) {
      List<Double> curTransitions = transitions.get(i);
      curTransitions.remove(state);
    }
    transitions.remove(state);
    stateTypes.remove(state);
  }

  // helper method to remove all elements .equals(obj) from list
  private void removeAllFromList(List<Integer> list, Object obj) {
    if (list == null || obj == null) {
      return;
    }
    while (list.indexOf(obj) != -1) {
      list.remove(obj);
    }
  }

  /**
   * Inserts a transition from fromState to toState
   */
  public void addTransition(int fromState, int toState) {
    List<Double> fromTransitions = transitions.get(fromState);
    fromTransitions.set(toState, ONE);
  }

  /**
   * Sets the weight for the transition from fromState to toState
   */
  public void setWeight(int fromState, int toState, double weight) {
    List<Double> fromTransitions = transitions.get(fromState);
    fromTransitions.set(toState, new Double(weight));
  }

  /**
   * @return A List containing the states that can be reached from
   *         fromState
   */
  public List<Integer> getOutgoing(int fromState) {
    List<Integer> outStates = new ArrayList<Integer>();
    List<Double> fromTransitions = transitions.get(fromState);
    for (int i = 0; i < fromTransitions.size(); i++) {
      Double weight = fromTransitions.get(i);
      if (weight.doubleValue() != 0.0) {
        outStates.add(Integer.valueOf(i));
      }
    }
    return outStates;
  }

  /**
   * @return A List containing the states that go to toState
   */
  public List<Integer> getIncoming(int toState) {
    List<Integer> inStates = new ArrayList<Integer>();
    for (int i = 0; i < transitions.size(); i++) {
      List<Double> curTransitions = transitions.get(i);
      Double weight = curTransitions.get(toState);
      if (weight.doubleValue() != 0.0) {
        inStates.add(Integer.valueOf(i));
      }
    }
    return inStates;
  }

  // clears the HMM Structure, leaving only a start and end state
  protected void giveEmpty() {
    // create a structure with only the start and end state
    transitions = new ArrayList<List<Double>>();
    stateTypes = new ArrayList<Integer>();

    // end state is state 0
    ArrayList<Double> endState = new ArrayList<Double>();
    endState.add(ONE);
    // cannot leave end state
    endState.add(ZERO);
    transitions.add(State.FINISHIDX, endState);
    stateTypes.add(Integer.valueOf(END_TYPE));

    // start state is state 1
    ArrayList<Double> startState = new ArrayList<Double>();
    startState.add(ONE);
    // always leave start state
    startState.add(ZERO);
    transitions.add(State.STARTIDX, startState);
    stateTypes.add(Integer.valueOf(START_TYPE));

    prefixes = new ArrayList<Integer>();
    suffixes = new ArrayList<Integer>();
    targets = new ArrayList<Integer>();
  }


  /**
   * Build a simple IE HMM structure, with start and finish state,
   * and one each background, prefix, suffix, and target states.
   */
  public void giveDefault() {
    giveEmpty();

    int background = insertStateAfter(State.STARTIDX, BACKGROUND_TYPE);
    int prefix = insertStateAfter(background, PREFIX_TYPE);
    prefixes.add(Integer.valueOf(prefix));
    int target = insertStateAfter(prefix, TARGET_TYPE);
    targets.add(Integer.valueOf(target));
    int suffix = insertStateAfter(target, SUFFIX_TYPE);
    suffixes.add(Integer.valueOf(suffix));
  }

  /**
   * Build a very simple IE HMM, with start and finish state,
   * and one background and target state.
   */
  public void giveSimplest() {
    giveEmpty();

    int background = insertStateAfter(State.STARTIDX, BACKGROUND_TYPE);
    int target = insertStateAfter(background, TARGET_TYPE);
    targets.add(Integer.valueOf(target));
  }


  /**
   * Builds an ergodic (fully-connected) HMM structure.  All states can
   * be reached from any other state except the end state, and all
   * states can go to any other state except the start state.  Equal
   * weights are assigned to all transitions, which can be perturbed.
   */
  public void giveErgodic(int[] stateTypes) {
    giveEmpty();
    ArrayList<Double> startTransitions = (ArrayList<Double>) transitions.get(State.STARTIDX);
    ArrayList<Double> endTransitions = (ArrayList<Double>) transitions.get(State.FINISHIDX);

    for (int i = 0; i < stateTypes.length; i++) {
      // all states can be reached from start state
      startTransitions.add(ONE);
      // end state does not go to any other state
      endTransitions.add(ZERO);

      ArrayList<Double> newTransitions = new ArrayList<Double>();
      // all states can go to end state
      newTransitions.add(ONE);
      // no states can go to start state
      newTransitions.add(ZERO);
      // add a transition from this state to all other states
      for (int j = 0; j < stateTypes.length; j++) {
        newTransitions.add(ONE);
      }
      transitions.add(newTransitions);
      this.stateTypes.add(Integer.valueOf(stateTypes[i]));
    }
  }

  public int numPrefixes() {
    return prefixes.size();
  }

  public int numTargets() {
    return targets.size();
  }

  public int numSuffixes() {
    return suffixes.size();
  }

  public int numStates() {
    return stateTypes.size();
  }

  /**
   * Smooths the current transitions with a uniform distribution.
   * Doesn't touch X->start transitions or end->X transitions.
   *
   * @param frac Mixing weight for uniform dist (0.0=none, 1.0=all)
   */
  public void addUniformSmoothing(double frac) {
    if (frac < 0 || frac > 1) {
      throw(new IllegalArgumentException("frac must be between 0.0 and 1.0; " + frac));
    }

    if (initializedTransitions == null) {
      // go through each state except the end state
      for (int i = 0; i < transitions.size(); i++) {
        if (stateTypes.get(i).intValue() == END_TYPE) {
          continue;
        }
        List<Double> curTransitions = transitions.get(i);
        int numTrans = curTransitions.size() - 1; // don't count X->start trans
        for (int j = 0; j < curTransitions.size(); j++) {
          if (stateTypes.get(j).intValue() == START_TYPE) {
            continue;
          }
          double trans = curTransitions.get(j).doubleValue();
          trans = frac / numTrans + (1.0 - frac) * trans;
          curTransitions.set(j, new Double(trans));
        }
      }
    } else {
      for (int i = 0; i < initializedTransitions.length; i++) {
        if (stateTypes.get(i).intValue() == END_TYPE) {
          continue;
        }
        int numTrans = initializedTransitions[i].length - 1; // don't count X->start trans
        for (int j = 0; j < initializedTransitions[i].length; j++) {
          if (stateTypes.get(j).intValue() == START_TYPE) {
            continue;
          }
          double trans = initializedTransitions[i][j];
          trans = frac / numTrans + (1.0 - frac) * trans;
          initializedTransitions[i][j] = trans;
        }
      }
    }
  }

  /**
   * Legacy method for modifying the structure.  Adds a new prefix
   * chain in the "list of lists" representation.  Can only be used if
   * the structure is initialized with {@link #giveDefault}
   */
  public void addPrefix(int length) {
    if (prefixes.size() == 0) {
      System.err.println("Operation not supported on this type of structure");
      return;
    }
    // create the new prefix chain by splitting off the base prefix
    Integer basePrefix = prefixes.get(0);
    int newPrefix = splitState(basePrefix.intValue());
    prefixes.add(Integer.valueOf(newPrefix));
    // lengthen the chain
    for (int i = 1; i < length; i++) {
      insertStateAfter(newPrefix, PREFIX_TYPE);
    }
  }

  /**
   * Legacy method for modifying the structure.  Adds a new suffix
   * chain in the "list of lists" representation.  Can only be used if
   * the structure is initialized with {@link #giveDefault}
   */
  public void addSuffix(int length) {
    if (prefixes.size() == 0) {
      System.err.println("Operation not supported on this type of structure");
      return;
    }

    // create the new suffix chain by splitting off the base suffix
    Integer baseSuffix = suffixes.get(0);
    int newSuffix = splitState(baseSuffix.intValue());
    // add the new chain to the suffix chain list
    suffixes.add(Integer.valueOf(newSuffix));
    // lengthen the chain
    for (int i = 1; i < length; i++) {
      insertStateAfter(newSuffix, SUFFIX_TYPE);
    }
  }

  /**
   * Legacy method for modifying the structure.  Adds a new target
   * chain in the "list of lists" representation.  Can only be used if
   * the structure is initialized with {@link #giveDefault}
   */
  public void addTarget(int length) {
    if (targets.size() == 0) {
      System.err.println("Operation not supported on this type of structure");
      return;
    }

    // create the new target chain by splitting off the base target
    Integer baseTarget = targets.get(0);

    int newTarget = splitState(baseTarget.intValue());
    // add the new chain to the target chain list
    targets.add(Integer.valueOf(newTarget));
    // lengthen the chain
    for (int i = 1; i < length; i++) {
      insertStateAfter(newTarget, TARGET_TYPE);
    }
  }

  /**
   * Legacy method for modifying the structure.  Extends the named
   * prefix chain in the "list of lists" representation.  Can only be
   * used if the structure is initialized with @see giveDefault
   */
  public void lengthenPrefix(int i) {
    Integer prefix = prefixes.get(i);
    insertStateAfter(prefix.intValue(), PREFIX_TYPE);
  }

  /**
   * Legacy method for modifying the structure.  Extends the named
   * suffix chain in the "list of lists" representation.  Can only be
   * used if the structure is initialized with @see giveDefault
   */
  public void lengthenSuffix(int i) {
    Integer suffix = suffixes.get(i);
    insertStateAfter(suffix.intValue(), SUFFIX_TYPE);
  }

  /**
   * Legacy method for modifying the structure.  Extends the named
   * target chain chain in the "list of lists" representation.  Can
   * only be used if the structure is initialized with {@link #giveDefault}
   */
  public void lengthenTarget(int i) {
    Integer target = targets.get(i);
    insertStateAfter(target.intValue(), TARGET_TYPE);
  }

  public Structure copy() {
    Structure s = new Structure();
    if (prefixes != null) {
      s.prefixes = new ArrayList<Integer>(prefixes);
    }
    if (targets != null) {
      s.targets = new ArrayList<Integer>(targets);
    }
    if (suffixes != null) {
      s.suffixes = new ArrayList<Integer>(suffixes);
    }
    if (transitions != null) {
      s.transitions = copyList(transitions);
    }

    s.stateTypes = new ArrayList<Integer>(stateTypes);

    return s;
  }

  // make a deep copy of a list of lists
  protected static <T> List<List<T>> copyList(List<List<T>> in) {
    List<List<T>> out = new ArrayList<List<T>>();
    for (int i = 0; i < in.size(); i++) {
      List<T> l = in.get(i);
      List<T> newCopy = new ArrayList<T>();
      for (int j = 0; j < l.size(); j++) {
        newCopy.add(l.get(j));
      }
      out.add(newCopy);
    }
    return out;
  }


  @SuppressWarnings("unused")
  private static void printList(List<List<?>> in) {
    for (int i = 0; i < in.size(); i++) {
      List<?> l = in.get(i);
      for (int j = 0; j < l.size(); j++) {
        System.out.print(l.get(j) + " ");
      }
      System.out.println();
    }
  }


  @SuppressWarnings("unused")
  private static int countList(List<List<?>> in) {
    int tot = 0;
    for (List<?> l : in) {
      tot += l.size();
    }
    return tot;
  }

  /**
   * adds self-loops to flesh out structure
   */
  public void addSelfLoops() {
    double[][] t = new double[transitions.size()][transitions.size()];

    // start state 1 takes you mainly to the background state, but can
    // take you to prefixes or targets, or finish (null sequence)
    for (int i = 0; i < stateTypes.size(); i++) {
      int type = stateTypes.get(i).intValue();

      // copy over existing transition
      List<Double> curTransitions = transitions.get(i);
      for (int j = 0; j < transitions.size(); j++) {
        t[i][j] = curTransitions.get(j).doubleValue();
      }

      // add self loops
      switch (type) {
        case BACKGROUND_TYPE:
          // background states have a strong self-loop
          t[i][i] = 0.8;
          break;

        case PREFIX_TYPE:
        case SUFFIX_TYPE:
          t[i][i] = 0.1;
          break;
      }

      // if it's a target type
      if (type > 0) {
        t[i][i] = 0.1;
      }
    }
    for (int i = 0; i < t.length; i++) {
      ArrayMath.normalize(t[i]);
    }
    initializedTransitions = t;
  }

  /**
   * Based on the existing transitions, makes and internally stores a
   * sensibly
   * extended version of the transition matrix with self-loops, etc.
   * Subsequent calls to {@link #getTransitions} will return this initialized
   * matrix.
   */
  public void initializeTransitions() {
    addSelfLoops();
    double[][] t = initializedTransitions;

    // start state 1 takes you mainly to the background state, but can
    // take you to prefixes or targets, or finish (null sequence)
    for (int i = 0; i < stateTypes.size(); i++) {
      int type = stateTypes.get(i).intValue();

      if (type > 0) {
        t[State.STARTIDX][i] = 0.05; // small possibility of going directly to target
      }
      if (type == PREFIX_TYPE) {
        t[State.STARTIDX][i] = 0.05; // small possibility of going directly to prefix (for near start target)
      }

      List<Double> curTransitions = transitions.get(i);
      switch (type) {
        case START_TYPE:
        case END_TYPE:
          for (int j = 0; j < curTransitions.size(); j++) {
            t[i][j] = curTransitions.get(j).doubleValue();
          }
          break;

        case BACKGROUND_TYPE:
          // background states usally loop, go to the end
          // state, or go to the start of prefix sequences
          for (int j = 0; j < curTransitions.size(); j++) {
            Double weight = curTransitions.get(j);
            if (j == State.FINISHIDX) {
              t[i][j] = 0.05; // small possibility of ending early
            }

            int curType = stateTypes.get(j).intValue();
            if (weight.doubleValue() != 0.0) {
              // background states likely to transition to other background states
              if (curType == BACKGROUND_TYPE) {
                t[i][j] = 0.2;
              }
              if (curType == PREFIX_TYPE) {
                t[i][j] = 0.02;
              } else {
                t[i][j] = 0.01; // make other transitions unlikely
              }
            }
          }
          break;

        case PREFIX_TYPE:
          // prefix states usually loop, or go to other prefixes
          // and targets
          for (int j = 0; j < curTransitions.size(); j++) {
            Double weight = curTransitions.get(j);
            // else if there is a transition already specified
            if (weight.doubleValue() != 0.0) {
              int curType = stateTypes.get(j).intValue();
              if (curType == PREFIX_TYPE) {
                t[i][j] = 0.6;
                // allow prefixes to feed forward two
                List<Integer> outgoing = getOutgoing(j);
                for (int k = 0; k < outgoing.size(); k++) {
                  int curOutgoing = outgoing.get(k).intValue();
                  int curOutType = stateTypes.get(curOutgoing).intValue();
                  if (curOutType == PREFIX_TYPE && curOutgoing != j) {
                    t[i][curOutgoing] = 0.15;
                  }
                }
              } else if (curType > 0) {
                t[i][j] = 0.8; // if it's a target type
              } else {
                t[i][j] = 0.01; // make other transitions unlikely
              }
            }
          }
          break;

        case SUFFIX_TYPE:
          // suffixes can loop, or go to other suffixes, back to the
          // background or to the end state
          for (int j = 0; j < curTransitions.size(); j++) {
            Double weight = curTransitions.get(j);
            if (j == State.FINISHIDX) {
              t[i][j] = 0.05; // small possibility of ending early
            }
            int curType = stateTypes.get(j).intValue();
            if (weight.doubleValue() != 0.0) {
              if (type == SUFFIX_TYPE) {
                // weight self transitions less
                if (i == j) {
                  t[i][j] = 0.2;
                } else {
                  t[i][j] = 0.6;
                }
                // allow suffixes to feed forward two
                List<Integer> outgoing = getOutgoing(j);
                for (int k = 0; k < outgoing.size(); k++) {
                  int curOutgoing = outgoing.get(k).intValue();
                  int curOutType = stateTypes.get(curOutgoing).intValue();
                  if (curOutType == SUFFIX_TYPE && curOutgoing != j) {
                    t[i][curOutgoing] = 0.15;
                  }
                }
              } else {
                t[i][j] = 0.01; // make other transitions unlikely
              }
            }
            // chris change: regardless of weight before
            if (curType == BACKGROUND_TYPE) {
              t[i][j] = 0.8;
            }
          }
          break;
      }
      if (type > 0) { // if it is a target type
        for (int j = 0; j < curTransitions.size(); j++) {
          Double weight = curTransitions.get(j);
          if (j == State.FINISHIDX) {
            t[i][j] = 0.05; // small possibility of ending early
          }
          // else if there is a transition already specified
          if (weight.doubleValue() != 0.0) {
            int curType = stateTypes.get(j).intValue();
            if (curType > 0) { // if it's a target type
              t[i][j] = 0.6;
            }
            if (curType == type) {
              // allow targets to feed forward two
              List<Integer> outgoing = getOutgoing(j);
              for (int k = 0; k < outgoing.size(); k++) {
                int curOutgoing = outgoing.get(k).intValue();
                int curOutType = stateTypes.get(curOutgoing).intValue();
                if (curOutType == type && // if it's the same target type
                        curOutgoing != j) {
                  t[i][curOutgoing] = 0.15;
                }
              }
            } else if (type == SUFFIX_TYPE) {
              t[i][j] = 0.88;
            } else {
              t[i][j] = 0.01; // make other transitions unlikely
            }
          }
        }
      }
    }

    for (int i = 0; i < t.length; i++) {
      ArrayMath.normalize(t[i]);
    }
    perturbTransitions(t);
  }

  /**
   * Returns the current transition weights for all the states in this
   * structure,
   * or the sensibly-initialized weights if {@link #initializeTransitions}
   * has previously been called.
   *
   * @return Matrix of transitions.  This matrix is a fresh copy which
   *         can be manipulated by the caller directly without affecting
   *         further calls to Structure.getTransitions(), and, indeed, this is
   *         what the HMM class does.
   */
  public double[][] getTransitions() {
    if (initializedTransitions == null) {
      double[][] t = new double[transitions.size()][transitions.size()];
      for (int i = 0; i < transitions.size(); i++) {
        List<Double> curTransitions = transitions.get(i);
        for (int j = 0; j < curTransitions.size(); j++) {
          Double weight = curTransitions.get(j);
          t[i][j] = weight.doubleValue();
        }
        ArrayMath.normalize(t[i]);
      }
      perturbTransitions(t);
      return (t);
    } else {
      double[][] t = new double[initializedTransitions.length][initializedTransitions.length];
      for (int i = 0; i < initializedTransitions.length; i++) {
        for (int j = 0; j < initializedTransitions.length; j++) {
          t[i][j] = initializedTransitions[i][j];
        }
      }
      return t;
    }
  }

  /**
   * Returns the list of state types for this Structure.
   */
  public int[] getStateTypes() {
    int[] types = new int[stateTypes.size()];
    for (int i = 0; i < stateTypes.size(); i++) {
      types[i] = stateTypes.get(i).intValue();
    }
    return (types);
  }

  /**
   * Takes the given transition weights and jiggles them all slightly so
   * they will converge more naturally and in different places.
   */
  public static void perturbTransitions(double[][] t) {
    double change;

    for (int i = 0; i < t.length; i++) {
      double smallest = 10.0;
      int nonZero = 0;
      for (int j = 0; j < t[i].length; j++) {
        if (t[i][j] != 0.0) {
          nonZero++;
          if (t[i][j] < smallest) {
            smallest = t[i][j];
          }
        }
      }
      if (nonZero > 0) {
        double newTotal = 0;
        for (int j = 0; j < t.length; j++) {
          if (t[i][j] != 0) {
            change = rand.nextDouble() * smallest / 10;
            t[i][j] += change;
            newTotal += t[i][j];
          }
        }
        for (int j = 0; j < t.length; j++) {
          t[i][j] = t[i][j] / newTotal;
        }
      }
    }
  }

  /* @return The number of non-zero transition parameters in the
   * structure
  */
  public int numParameters() {
    int numParams = 0;
    for (int i = 0; i < transitions.size(); i++) {
      List<Double> curTransitions = transitions.get(i);
      for (int j = 0; j < curTransitions.size(); j++) {
        Double curProb = curTransitions.get(j);
        if (curProb.doubleValue() != 0.0) {
          numParams++;
        }
      }
    }
    return numParams;
  }


  /* @return Return a State array for the states in the structure.  The
     index in the array corresponds to the number of the state in the
     structure. Transitions are perturbed slightly before being added.
  */
  public State[] getStates() {
    double[][] t = getTransitions();
    State[] s = new State[stateTypes.size()];
    for (int i = 0; i < stateTypes.size(); i++) {
      int type = stateTypes.get(i).intValue();

      // since all types > 0 are treated as target types
      // by the HMM code, need to convert prefix and suffix to
      // the general background type (0)
      if (type == PREFIX_TYPE || type == SUFFIX_TYPE) {
        type = 0;
      }

      s[i] = new State(type, null, t[i]);
    }
    return s;
  }

  @Override
  public String toString() {
    StringBuilder retVal = new StringBuilder();
    retVal.append("Structure [numStates: ").append(stateTypes.size()).append("\n");
    retVal.append("state | type | trans\n");
    retVal.append("------------\n");
    for (int i = 0; i < stateTypes.size(); i++) {
      Integer curStateType = stateTypes.get(i);
      // print formatting
      retVal.append(StringUtils.pad(Integer.valueOf(i), 8));
      retVal.append(curStateType);
      retVal.append("\n");
    }
    return retVal.toString();
  }

  public static State[] defaultStates() {
    // return jimDefaultStates();
    return chrisDefaultStates2();
  }


  public static State[] fsnlpHMM() {
    String[] vocab = {"cola", "ice_t", "lem"};
    double[] cpEmit = {0.6, 0.1, 0.3};
    double[] ipEmit = {0.1, 0.7, 0.2};
    int numStates = 4; // start, end and two real
    State[] states = new State[numStates];
    for (int i = 0; i < numStates; i++) {
      states[i] = new State(numStates);
    }
    states[State.FINISHIDX].type = State.FINISHTYPE;
    states[State.STARTIDX].type = State.STARTTYPE;

    /* Finish state -- unused */
    states[State.FINISHIDX].transition[State.FINISHIDX] = 1.0;
    /* Start state */
    states[State.STARTIDX].transition[2] = 1.0;
    /* CP */
    states[2].transition[2] = 0.7;
    states[2].transition[3] = 0.3;
    states[2].emit = new PlainEmitMap(vocab, cpEmit);
    /* IP */
    states[3].transition[2] = 0.5;
    states[3].transition[3] = 0.5;
    states[3].emit = new PlainEmitMap(vocab, ipEmit);
    return states;
  }


  /**
   * Code below assumes PRESUFFLENG >= 1.
   */
  private static final int PRESUFFLENG = 3;  // was 4;
  /**
   * Code below assumes NUMTARGETS = 4
   */
  private static final int NUMTARGETS = 4;
  private static final int BKGRND = 2;
  private static final int STARTPREFIX = 3;
  private static final int STARTTARGET = STARTPREFIX + PRESUFFLENG;
  private static final int STARTSUFFIX = STARTTARGET + NUMTARGETS;


  /**
   * Initializes a reasonable default structure, with a background,
   * prefix and suffix states and some number of target states.
   * The structure is based on a McCallum and Nigam example, but differs.
   * We add start, finish, and background states to prefix/suffix/target
   * states.  Has a triangle on prefix only with a hold state.
   */
  public static State[] jimDefaultStates() {
    int numStates = 3 + NUMTARGETS + 2 * PRESUFFLENG;
    State[] states = new State[numStates];

    for (int i = 0; i < numStates; i++) {
      states[i] = new State(numStates);
    }
    states[State.FINISHIDX].type = State.FINISHTYPE;
    states[State.STARTIDX].type = State.STARTTYPE;
    for (int j = STARTTARGET; j < STARTSUFFIX; j++) {
      states[j].type = 1;
    }

    /* Finish state */
    states[State.FINISHIDX].transition[State.FINISHIDX] = 1.0;

    /* Start state */
    states[State.STARTIDX].transition[BKGRND] = 0.95;
    states[State.STARTIDX].transition[State.FINISHIDX] = 0.01;
    states[State.STARTIDX].transition[STARTPREFIX] = 0.02;
    states[State.STARTIDX].transition[STARTTARGET] = 0.02;

    /* background state */
    states[BKGRND].transition[BKGRND] = .95;
    states[BKGRND].transition[STARTPREFIX] = .025;
    states[BKGRND].transition[State.FINISHIDX] = 0.025;

    /* prefix states */
    for (int j = STARTTARGET - 1; j > BKGRND; j--) {
      if (j == STARTTARGET - 1) {
        // asymmetry on start targets
        int k;
        for (k = STARTTARGET; k < STARTTARGET + NUMTARGETS / 2; k++) {
          states[j].transition[k] = 0.6 / (NUMTARGETS / 2);
        }
        for (; k < STARTSUFFIX; k++) {
          states[j].transition[k] = 0.4 / (NUMTARGETS - (NUMTARGETS / 2));
        }
      } else if (j == STARTTARGET - 2) {
        states[j].transition[j] = 0.5;
        states[j].transition[j + 1] = 0.5;
      } else if (j == STARTTARGET - 3) {
        states[j].transition[j + 1] = 0.5;
        for (int k = STARTTARGET; k < STARTSUFFIX; k++) {
          // there used to be asymmetry on start targets here too.
          states[j].transition[k] = 0.5 / NUMTARGETS;
        }
      } else {
        states[j].transition[j + 1] = 1.0;
      }
    }

    /* target states */
    for (int j = STARTTARGET; j < STARTSUFFIX; j++) {
      if (j == STARTTARGET) {
        states[j].transition[j] = .15;
        states[j].transition[j + 1] = .25;
        states[j].transition[j + 2] = .20;
        states[j].transition[j + 3] = .15;
        states[j].transition[j + 4] = .23;
        states[j].transition[State.FINISHIDX] = .02;
      } else if (j == STARTTARGET + 1) {
        states[j].transition[j - 1] = .15;
        states[j].transition[j] = .15;
        states[j].transition[j + 1] = .25;
        states[j].transition[j + 2] = .2;
        states[j].transition[j + 3] = .25;
      } else if (j == STARTTARGET + 2) {
        states[j].transition[j - 2] = .15;
        states[j].transition[j - 1] = .15;
        states[j].transition[j] = .2;
        states[j].transition[j + 1] = .25;
        states[j].transition[j + 2] = .25;
      } else if (j == STARTTARGET + 3) {
        states[j].transition[j - 3] = .10;
        states[j].transition[j - 2] = .15;
        states[j].transition[j - 1] = .2;
        states[j].transition[j] = .2;
        states[j].transition[j + 1] = .3;
        states[j].transition[State.FINISHIDX] = 0.05;
        // chris change 9/16/02 -- allow loopback
        // states[j].transition[State.FINISHIDX] = 0.03;
        // states[j].transition[BKGRND + 1] = 0.01;
        // states[j].transition[STARTTARGET - 1] = 0.01;
      }
    }

    /* suffix states */
    for (int j = STARTSUFFIX; j < STARTSUFFIX + PRESUFFLENG; j++) {
      if (j == STARTSUFFIX + PRESUFFLENG - 1) {
        states[j].transition[BKGRND] = 0.95;
        states[j].transition[State.FINISHIDX] = 0.05;
      } else {
        states[j].transition[j + 1] = 0.95;
        states[j].transition[State.FINISHIDX] = 0.05;
        // chris change 9/16/02 -- allow loopback
        // states[j].transition[State.FINISHIDX] = 0.04;
        // states[j].transition[STARTTARGET - 1] = 0.01;
      }
    }
    return states;
  }


  /**
   * Initializes a reasonable default structure, with a background,
   * prefix and suffix states and some number of target states.
   * The structure is based on a McCallum and Nigam example.
   * We add start, finish, and background states to prefix/suffix/target
   * states.  This just changes some transition structure: you can move
   * from START to last prefix state; eliminates moves from 1st prefix state
   * to all targets (not in Fr&amp;Mc?) but allows you to skip 1 prefix
   * state from there. Puts in a triangle structure with a self-loop in the
   * hold state in suffix.
   */
  public static State[] chrisDefaultStates() {
    int numStates = 3 + NUMTARGETS + 2 * PRESUFFLENG;
    State[] states = new State[numStates];

    for (int i = 0; i < numStates; i++) {
      states[i] = new State(numStates);
    }
    states[State.FINISHIDX].type = State.FINISHTYPE;
    states[State.STARTIDX].type = State.STARTTYPE;
    for (int j = STARTTARGET; j < STARTSUFFIX; j++) {
      states[j].type = 1;
    }

    /* Finish state */
    states[State.FINISHIDX].transition[State.FINISHIDX] = 1.0;

    /* Start state */
    states[State.STARTIDX].transition[BKGRND] = 0.93;
    states[State.STARTIDX].transition[State.FINISHIDX] = 0.01;
    states[State.STARTIDX].transition[STARTPREFIX] = 0.02;
    states[State.STARTIDX].transition[STARTTARGET - 1] = 0.02;
    states[State.STARTIDX].transition[STARTTARGET] = 0.02;

    /* background state */
    states[BKGRND].transition[BKGRND] = .95;
    states[BKGRND].transition[STARTPREFIX] = .025;
    states[BKGRND].transition[State.FINISHIDX] = 0.025;

    /* prefix states */
    for (int j = STARTTARGET - 1; j > BKGRND; j--) {
      if (j == STARTTARGET - 1) {
        // asymmetry on start targets
        int k;
        for (k = STARTTARGET; k < STARTTARGET + NUMTARGETS / 2; k++) {
          states[j].transition[k] = 0.6 / (NUMTARGETS / 2);
        }
        for (; k < STARTSUFFIX; k++) {
          states[j].transition[k] = 0.4 / (NUMTARGETS - (NUMTARGETS / 2));
        }
      } else if (j == STARTTARGET - 2) {
        states[j].transition[j] = 0.5;
        states[j].transition[j + 1] = 0.5;
      } else {
        states[j].transition[j + 1] = 0.5;
        states[j].transition[j + 2] = 0.5;
      }
    }

    /* target states */
    for (int j = STARTTARGET; j < STARTSUFFIX; j++) {
      if (j == STARTTARGET) {
        states[j].transition[j] = .15;
        states[j].transition[j + 1] = .25;
        states[j].transition[j + 2] = .20;
        states[j].transition[j + 3] = .15;
        states[j].transition[j + 4] = .23;
        states[j].transition[State.FINISHIDX] = .02;
      } else if (j == STARTTARGET + 1) {
        states[j].transition[j - 1] = .15;
        states[j].transition[j] = .15;
        states[j].transition[j + 1] = .25;
        states[j].transition[j + 2] = .2;
        states[j].transition[j + 3] = .25;
      } else if (j == STARTTARGET + 2) {
        states[j].transition[j - 2] = .15;
        states[j].transition[j - 1] = .15;
        states[j].transition[j] = .2;
        states[j].transition[j + 1] = .25;
        states[j].transition[j + 2] = .25;
      } else if (j == STARTTARGET + 3) {
        states[j].transition[j - 3] = .10;
        states[j].transition[j - 2] = .15;
        states[j].transition[j - 1] = .2;
        states[j].transition[j] = .2;
        states[j].transition[j + 1] = .3;
        states[j].transition[State.FINISHIDX] = 0.05;
        // chris change 9/16/02 -- allow loopback
        // states[j].transition[State.FINISHIDX] = 0.03;
        // states[j].transition[BKGRND + 1] = 0.01;
        // states[j].transition[STARTTARGET - 1] = 0.01;
      }
    }

    /* suffix states */
    for (int j = STARTSUFFIX; j < STARTSUFFIX + PRESUFFLENG; j++) {
      if (j == STARTSUFFIX + PRESUFFLENG - 1) {
        states[j].transition[BKGRND] = 0.95;
        states[j].transition[State.FINISHIDX] = 0.05;
      } else if (j == STARTSUFFIX) {
        states[j].transition[j + 1] = 0.5;
        states[j].transition[j + 2] = 0.45;
        states[j].transition[State.FINISHIDX] = 0.05;
      } else if (j == STARTSUFFIX + 1) {
        states[j].transition[j] = 0.5;
        states[j].transition[j + 1] = 0.45;
        states[j].transition[State.FINISHIDX] = 0.05;
      } else {
        states[j].transition[j + 1] = 0.95;
        states[j].transition[State.FINISHIDX] = 0.05;
        // chris change 9/16/02 -- allow loopback
        // states[j].transition[State.FINISHIDX] = 0.04;
        // states[j].transition[STARTTARGET - 1] = 0.01;
      }
    }

    return states;
  }

  /**
   * Initializes a reasonable default structure, with a background,
   * prefix and suffix states and some number of target states.
   * The structure is based on a McCallum and Nigam example.
   * We add start, finish, and background states to prefix/suffix/target
   * states.  Versus the Jim/Chris versions, this version just changes some
   * transition structure, in hopefully useful ways: dispenses with very
   * hand-set target-target transitions, and just uses roughly uniform with
   * small chance to terminate. Don't allow finishing from suffix triangle
   * hold state.
   */
  public static State[] chrisDefaultStates2() {
    int numStates = 3 + NUMTARGETS + 2 * PRESUFFLENG;
    State[] states = new State[numStates];

    for (int i = 0; i < numStates; i++) {
      states[i] = new State(numStates);
    }
    states[State.FINISHIDX].type = State.FINISHTYPE;
    states[State.STARTIDX].type = State.STARTTYPE;
    for (int j = STARTTARGET; j < STARTSUFFIX; j++) {
      states[j].type = 1;
    }

    /* Finish state */
    states[State.FINISHIDX].transition[State.FINISHIDX] = 1.0;

    /* Start state */
    states[State.STARTIDX].transition[BKGRND] = 0.93;
    states[State.STARTIDX].transition[State.FINISHIDX] = 0.01;
    states[State.STARTIDX].transition[STARTPREFIX] = 0.02;
    states[State.STARTIDX].transition[STARTTARGET - 1] = 0.02;
    states[State.STARTIDX].transition[STARTTARGET] = 0.02;

    /* background state */
    states[BKGRND].transition[BKGRND] = .95;
    states[BKGRND].transition[STARTPREFIX] = .025;
    states[BKGRND].transition[State.FINISHIDX] = 0.025;

    /* prefix states */
    for (int j = STARTTARGET - 1; j > BKGRND; j--) {
      if (j == STARTTARGET - 1) {
        // asymmetry on start targets
        int k;
        for (k = STARTTARGET; k < STARTTARGET + NUMTARGETS / 2; k++) {
          states[j].transition[k] = 0.6 / (NUMTARGETS / 2);
        }
        for (; k < STARTSUFFIX; k++) {
          states[j].transition[k] = 0.4 / (NUMTARGETS - (NUMTARGETS / 2));
        }
      } else if (j == STARTTARGET - 2) {
        states[j].transition[j] = 0.5;
        states[j].transition[j + 1] = 0.5;
      } else {
        states[j].transition[j + 1] = 0.5;
        states[j].transition[j + 2] = 0.5;
      }
    }

    /* target states */
    for (int j = STARTTARGET; j < STARTSUFFIX; j++) {
      for (int m = STARTTARGET; m <= STARTSUFFIX; m++) {
        states[j].transition[m] = 0.11 + ((float) m) / 100;
      }
      // this gave 0.17, 0.18, 0.19, 0.2, 0.21 = 0.95 to the 5 transitions
      states[j].transition[State.FINISHIDX] = 0.05;
    }

    /* suffix states */
    for (int j = STARTSUFFIX; j < STARTSUFFIX + PRESUFFLENG; j++) {
      if (j == STARTSUFFIX + PRESUFFLENG - 1) {
        states[j].transition[BKGRND] = 0.95;
        states[j].transition[State.FINISHIDX] = 0.05;
      } else if (j == STARTSUFFIX) {
        states[j].transition[j + 1] = 0.5;
        states[j].transition[j + 2] = 0.45;
        states[j].transition[State.FINISHIDX] = 0.05;
      } else if (j == STARTSUFFIX + 1) {
        states[j].transition[j] = 0.5;
        states[j].transition[j + 1] = 0.5;
        // states[j].transition[State.FINISHIDX] = 0.05;
      } else {
        states[j].transition[j + 1] = 0.95;
        states[j].transition[State.FINISHIDX] = 0.05;
        // chris change 9/16/02 -- allow loopback
        // states[j].transition[State.FINISHIDX] = 0.04;
        // states[j].transition[STARTTARGET - 1] = 0.01;
      }
    }

    return states;
  }

  public static Structure frmcComplexStructure() {
    // start, end, bg, 4 prefix, 4 suffix, 4 target
    double[][] t = new double[15][15];
    for (int i = 0; i < t.length; i++) {
      for (int j = 0; j < t[i].length; j++) {
        t[i][j] = 0.0;
      }
    }
    int[] s = new int[]{State.FINISHTYPE, State.STARTTYPE, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0};

    t[State.STARTIDX][State.BKGRNDIDX] = 1.0;
    t[State.FINISHIDX][State.FINISHIDX] = 1.0;

    t[State.BKGRNDIDX][State.BKGRNDIDX] = 0.95;
    t[State.BKGRNDIDX][State.FINISHIDX] = 0.025;
    t[State.BKGRNDIDX][3] = 0.025; // bg->first prefix

    for (int i = 3; i <= 5; i++) {
      t[i][i + 1] = 1.0; // prefix chain
    }
    for (int i = 11; i <= 13; i++) {
      t[i][i + 1] = 1.0; // suffix chain
    }

    t[6][7] = .3;
    t[6][8] = .3;
    t[6][9] = .2;
    t[6][10] = .2;

    t[7][7] = .15;
    t[7][8] = .25;
    t[7][9] = .20;
    t[7][10] = .15;
    t[7][11] = .25;

    t[8][7] = .15;
    t[8][8] = .15;
    t[8][9] = .25;
    t[8][10] = .2;
    t[8][11] = .25;

    t[9][7] = .15;
    t[9][8] = .15;
    t[9][9] = .2;
    t[9][10] = .25;
    t[9][11] = .25;

    t[10][7] = .10;
    t[10][8] = .15;
    t[10][9] = .2;
    t[10][10] = .2;
    t[10][11] = .35;

    t[14][State.BKGRNDIDX] = 1.0;

    return (new Structure(t, s));
  }

  public static Structure dan5ToyStructure() {
    // start, end, 3 bg, 2 targets
    double[][] t = new double[7][7];
    for (int i = 0; i < t.length; i++) {
      for (int j = 0; j < t[i].length; j++) {
        t[i][j] = 0.0;
      }
    }
    int[] s = new int[]{State.FINISHTYPE, State.STARTTYPE, 0, 0, 0, 1, 2};

    t[State.STARTIDX][2] = 0.5;
    t[State.STARTIDX][4] = 0.5;
    t[State.FINISHIDX][State.FINISHIDX] = 1.0;

    t[2][3] = .8;
    t[2][5] = .2;

    t[3][3] = 0.4;
    t[3][2] = 0.22;
    t[3][4] = 0.18;
    t[3][State.FINISHIDX] = 0.2;

    t[4][3] = .8;
    t[4][6] = .2;

    t[5][3] = 1.0;

    t[6][3] = 1.0;

    return (new Structure(t, s));
  }


  static HMM readHMMFromFile(String filename) throws IOException {
    Reader r = new FileReader(filename);
    HMM contents = readHMM(r);
    r.close();
    return contents;
  }


  static HMM readHMM(Reader r) throws IOException {
    String line;
    Index<String> typeNames = new HashIndex<String>();
    BufferedReader br = new BufferedReader(r);
    line = br.readLine();
    if (!line.equalsIgnoreCase("HMM version 1")) {
      throw new IOException("File format error");
    }
    line = br.readLine();
    if (!line.matches("States ([0-9]+)")) {
      throw new IOException("File format error");
    }
    String[] fields = line.split("\\s");
    int numStates = Integer.parseInt(fields[1]);
    State[] st = new State[numStates];
    ClassicCounter<String> vocab = new ClassicCounter<String>();
    int stateNum = -1;
    while ((line = br.readLine()) != null) {
      fields = line.split("\\s");
      if (fields[0].equalsIgnoreCase("State")) {
        stateNum = Integer.parseInt(fields[1]);
        int typeNum = 0;
        if (fields.length > 3) {
          String typeStr = fields[3];
          typeNames.add(typeStr);
          typeNum = typeNames.indexOf(typeStr);
        }
        EmitMap emit = new PlainEmitMap();
        st[stateNum] = new State(typeNum, emit, numStates);
      } else if (fields[0].equalsIgnoreCase("Transition")) {
        int toState = Integer.parseInt(fields[1]);
        double prob = Double.parseDouble(fields[3]);
        st[stateNum].transition[toState] = prob;
      } else if (fields[0].equalsIgnoreCase("Emission")) {
        String emission = fields[1];
        double prob = Double.parseDouble(fields[3]);
        st[stateNum].emit.set(emission, prob);
        vocab.incrementCount(emission);
      } else {
        throw new IOException("File format error: " + line);
      }
    }
    return new HMM(st, HMM.REGULAR_HMM, typeNames.toArray(new String[0]), vocab);
  }


  /**
   * Provides a sample exercising of the structure creation operations
   */
  public static void main(String[] args) {
    Structure s = new Structure();
    s.giveDefault();
    s.lengthenTarget(0);
    s.lengthenTarget(0);
    s.addTarget(1);
    s.addPrefix(1);
    s.addSuffix(2);
    s.addPrefix(3);
    s.getTransitions();
    State[] struct = s.getStates();
    System.err.println(s);
  }
}
