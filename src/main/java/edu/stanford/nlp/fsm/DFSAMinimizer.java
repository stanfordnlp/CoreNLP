package edu.stanford.nlp.fsm; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.util.DisjointSet;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.FastDisjointSet;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.UnorderedPair;

import java.util.*;

/**
 * DFSAMinimizer minimizes (unweighted) deterministic finite state
 * automata.
 *
 * @author Dan Klein
 * @version 12/14/2000
 */
public final class DFSAMinimizer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DFSAMinimizer.class);

  static boolean debug = false;


  private DFSAMinimizer() {} // static methods class


  static class IntPair {
    int i;
    int j;

    IntPair(int i, int j) {
      this.i = i;
      this.j = j;
    }
  }

  public static <T, S> void unweightedMinimize(DFSA<T, S> dfsa) {
    Set<DFSAState<T, S>> states = dfsa.states();
    long time = System.currentTimeMillis();
    if (debug) {
      time = System.currentTimeMillis();
      log.info("\nStarting on " + dfsa.dfsaID);
      log.info(" -- " + states.size() + " states.");
    }
    int numStates = states.size();
    // assign ids
    int id = 0;
    DFSAState<T, S>[] state = ErasureUtils.<DFSAState<T, S>[]>uncheckedCast(new DFSAState[numStates]);
    Map<DFSAState<T, S>, Integer> stateToID = Generics.newHashMap();
    for (DFSAState<T, S> state1 : states) {
      state[id] = state1;
      stateToID.put(state1, Integer.valueOf(id));
      id++;
    }
    // initialize grid
    boolean[][] distinct = new boolean[numStates][numStates];
    List<IntPair>[][] dependentList = ErasureUtils.<List<IntPair>[][]>uncheckedCast(new List[numStates][numStates]);
    for (int i = 0; i < numStates; i++) {
      for (int j = i + 1; j < numStates; j++) {
        distinct[i][j] = state[i].isAccepting() != state[j].isAccepting();
      }
    }
    if (debug) {
      log.info("Initialized: " + (System.currentTimeMillis() - time));
      time = System.currentTimeMillis();
    }
    // visit all non-distinct
    for (int i = 0; i < numStates; i++) {
      for (int j = i + 1; j < numStates; j++) {
        if (!distinct[i][j]) {
          DFSAState<T, S> state1 = state[i];
          DFSAState<T, S> state2 = state[j];
          IntPair ip = new IntPair(i, j);
          // check if some input distinguishes this pair
          Set<T> inputs = Generics.newHashSet();
          inputs.addAll(state1.continuingInputs());
          inputs.addAll(state2.continuingInputs());
          boolean distinguishable = false;
          Set<IntPair> pendingIPairs = Generics.newHashSet();
          Iterator<T> inputI = inputs.iterator();
          while (inputI.hasNext() && !distinguishable) {
            T input = inputI.next();
            DFSATransition<T, S> transition1 = state1.transition(input);
            DFSATransition<T, S> transition2 = state2.transition(input);
            if ((transition1 == null) != (transition2 == null)) {
              distinguishable = true;
            }
            if (transition1 != null && transition2 != null) {
              DFSAState<T, S> target1 = transition1.getTarget();
              DFSAState<T, S> target2 = transition2.getTarget();
              int num1 = stateToID.get(target1).intValue();
              int num2 = stateToID.get(target2).intValue();
              IntPair targetIPair = new IntPair(num1, num2);
              if (num1 != num2) {
                if (distinct[num1][num2]) {
                  distinguishable = true;
                } else {
                  pendingIPairs.add(targetIPair);
                }
              }
            }
          }
          if (distinguishable) {
            // if the pair is distinguishable, record that
            List<IntPair> markStack = new ArrayList<>();
            markStack.add(ip);
            while (!markStack.isEmpty()) {
              IntPair ipToMark = markStack.get(markStack.size() - 1);
              markStack.remove(markStack.size() - 1);
              distinct[ipToMark.i][ipToMark.j] = true;
              List<IntPair> addList = dependentList[ipToMark.i][ipToMark.j];
              if (addList != null) {
                markStack.addAll(addList);
              }
            }
          } else {
            // otherwise add it to any pending pairs
            for (IntPair pendingIPair : pendingIPairs) {
              List<IntPair> dependentList1 = dependentList[pendingIPair.i][pendingIPair.j];
              if (dependentList1 == null) {
                dependentList1 = new ArrayList<>();
                dependentList[pendingIPair.i][pendingIPair.j] = dependentList1;
              }
              dependentList1.add(ip);
            }
          }
        }
      }
    }
    if (debug) {
      log.info("All pairs marked: " + (System.currentTimeMillis() - time));
      time = System.currentTimeMillis();
    }
    // decide what canonical state each state will map to...
    DisjointSet<DFSAState<T, S>> stateClasses = new FastDisjointSet<>(states);
    for (int i = 0; i < numStates; i++) {
      for (int j = i + 1; j < numStates; j++) {
        if (!distinct[i][j]) {
          DFSAState<T, S> state1 = state[i];
          DFSAState<T, S> state2 = state[j];
          stateClasses.union(state1, state2);
        }
      }
    }
    Map<DFSAState<T, S>, DFSAState<T, S>> stateToRep = Generics.newHashMap();
    for (DFSAState<T, S> state1 : states) {
      DFSAState<T, S> rep = stateClasses.find(state1);
      stateToRep.put(state1, rep);
    }
    if (debug) {
      log.info("Canonical states chosen: " + (System.currentTimeMillis() - time));
      time = System.currentTimeMillis();
    }
    // reduce the DFSA by replacing transition targets with their reps
    for (DFSAState<T, S> state1 : states) {
      if (!state1.equals(stateToRep.get(state1))) {
        continue;
      }
      for (DFSATransition<T, S> transition : state1.transitions()) {
        //if (!transition.target.equals(stateToRep.get(transition.target)))
        //  System.out.println(Utils.pad(transition.target.toString(),30)+stateToRep.get(transition.target));
        transition.target = stateToRep.get(transition.target);
      }
    }
    dfsa.initialState = stateToRep.get(dfsa.initialState);
    if (debug) {
      log.info("Done: " + (System.currentTimeMillis() - time));
    }
    // done!
  }


  static <T, S> void unweightedMinimizeOld(DFSA<T, S> dfsa) {
    Set<DFSAState<T, S>> states = dfsa.states();
    Map<UnorderedPair<DFSAState<T, S>, DFSAState<T, S>>, List<UnorderedPair<DFSAState<T, S>, DFSAState<T, S>>>> stateUPairToDependentUPairList = Generics.newHashMap(states.size() * states.size() / 2 + 1);
    Map<UnorderedPair<DFSAState<T, S>, DFSAState<T, S>>, Boolean> stateUPairToDistinguished = Generics.newHashMap(states.size() * states.size() / 2 + 1);
    int[] c = new int[states.size() * states.size() / 2 + 1];
    int streak = 0;
    int collisions = 0;
    int entries = 0;
    long time = System.currentTimeMillis();
    if (debug) {
      time = System.currentTimeMillis();
      log.info("Starting on " + dfsa.dfsaID);
      log.info(" -- " + states.size() + " states.");
    }
    // initialize grid
    int numDone = 0;
    for (DFSAState<T, S> state1 : states) {
      for (DFSAState<T, S> state2 : states) {
        UnorderedPair<DFSAState<T, S>, DFSAState<T, S>> up = new UnorderedPair<>(state1, state2);
        if (state1.equals(state2)) {
          continue;
        }
        if (stateUPairToDistinguished.containsKey(up)) {
          continue;
        }
        int bucket = (up.hashCode() & 0x7FFFFFFF) % (states.size() * states.size() / 2 + 1);
        c[bucket]++;
        entries++;
        if (c[bucket] > 1) {
          collisions++;
          streak = 0;
        } else {
          streak++;
        }
        if (state1.isAccepting() != state2.isAccepting()) {
          //log.info(Utils.pad((String)state1.stateID, 20)+" "+state2.stateID);
          stateUPairToDistinguished.put(up, Boolean.TRUE);
        } else {
          stateUPairToDistinguished.put(up, Boolean.FALSE);
          //stateUPairToDependentUPairList.put(up, new ArrayList());
        }
      }
      numDone++;
      if (numDone % 20 == 0) {
        log.info("\r" + numDone + "  " + ((double) collisions / (double) entries));
      }
    }
    if (debug) {
      log.info("\nInitialized: " + (System.currentTimeMillis() - time));
      time = System.currentTimeMillis();
    }
    // visit each undistinguished pair
    for (UnorderedPair<DFSAState<T, S>, DFSAState<T, S>> up : stateUPairToDistinguished.keySet()) {

      DFSAState<T, S> state1 = up.first;
      DFSAState<T, S> state2 = up.second;
      if (stateUPairToDistinguished.get(up).equals(Boolean.TRUE)) {
        continue;
      }
      // check if some input distinguishes this pair
      Set<T> inputs = Generics.newHashSet(state1.continuingInputs());
      inputs.addAll(state2.continuingInputs());
      boolean distinguishable = false;
      Set<UnorderedPair<DFSAState<T, S>, DFSAState<T, S>>> pendingUPairs = Generics.newHashSet();
      Iterator<T> inputI = inputs.iterator();
      while (inputI.hasNext() && !distinguishable) {
        T input = inputI.next();
        DFSATransition<T, S> transition1 = state1.transition(input);
        DFSATransition<T, S> transition2 = state2.transition(input);
        if ((transition1 == null) != (transition2 == null)) {
          distinguishable = true;
        }
        if (transition1 != null && transition2 != null) {
          DFSAState<T, S> target1 = transition1.getTarget();
          DFSAState<T, S> target2 = transition2.getTarget();
          UnorderedPair<DFSAState<T, S>, DFSAState<T, S>> targetUPair = new UnorderedPair<>(target1, target2);
          if (!target1.equals(target2)) {
            if (stateUPairToDistinguished.get(targetUPair).equals(Boolean.TRUE)) {
              distinguishable = true;
            } else {
              pendingUPairs.add(targetUPair);
            }
          }
        }
      }
      // if the pair is distinguishable, record that
      if (distinguishable) {
        List<UnorderedPair<DFSAState<T, S>, DFSAState<T, S>>> markStack = new ArrayList<>();
        markStack.add(up);
        while (!markStack.isEmpty()) {
          UnorderedPair<DFSAState<T, S>, DFSAState<T, S>> upToMark = markStack.get(markStack.size() - 1);
          markStack.remove(markStack.size() - 1);
          stateUPairToDistinguished.put(upToMark, Boolean.TRUE);
          List<UnorderedPair<DFSAState<T, S>, DFSAState<T, S>>> addList = stateUPairToDependentUPairList.get(upToMark);
          if (addList != null) {
            markStack.addAll(addList);
            stateUPairToDependentUPairList.get(upToMark).clear();
          }
        }
      } else {
        // otherwise add it to any pending pairs
        for (UnorderedPair<DFSAState<T, S>, DFSAState<T, S>> pendingUPair : pendingUPairs) {
          List<UnorderedPair<DFSAState<T, S>, DFSAState<T, S>>> dependentList = stateUPairToDependentUPairList.get(pendingUPair);
          if (dependentList == null) {
            dependentList = new ArrayList<>();
            stateUPairToDependentUPairList.put(pendingUPair, dependentList);
          }
          dependentList.add(up);
        }
      }
    }
    if (debug) {
      log.info("All pairs marked: " + (System.currentTimeMillis() - time));
      time = System.currentTimeMillis();
    }
    // decide what canonical state each state will map to...
    DisjointSet<DFSAState<T, S>> stateClasses = new FastDisjointSet<>(states);
    for (UnorderedPair<DFSAState<T, S>, DFSAState<T, S>> up : stateUPairToDistinguished.keySet()) {
      if (stateUPairToDistinguished.get(up).equals(Boolean.FALSE)) {
        DFSAState<T, S> state1 = up.first;
        DFSAState<T, S> state2 = up.second;
        stateClasses.union(state1, state2);
      }
    }
    Map<DFSAState<T, S>, DFSAState<T, S>> stateToRep = Generics.newHashMap();
    for (DFSAState<T, S> state : states) {
      DFSAState<T, S> rep = stateClasses.find(state);
      stateToRep.put(state, rep);
    }
    if (debug) {
      log.info("Canonical states chosen: " + (System.currentTimeMillis() - time));
      time = System.currentTimeMillis();
    }
    // reduce the DFSA by replacing transition targets with their reps
    for (DFSAState<T, S> state : states) {
      if (!state.equals(stateToRep.get(state))) {
        continue;
      }
      for (DFSATransition<T, S> transition : state.transitions()) {
        transition.target = stateClasses.find(transition.target);
      }
    }
    dfsa.initialState = stateClasses.find(dfsa.initialState);
    if (debug) {
      log.info("Done: " + (System.currentTimeMillis() - time));
    }
    // done!
  }

}
