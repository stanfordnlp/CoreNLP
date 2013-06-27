package edu.stanford.nlp.fsm;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Scored;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * DFSA: A class for representing a deterministic finite state automaton
 * without epsilon transitions.
 *
 * @author Dan Klein
 * @author Michel Galley (AT&T FSM library format printing)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in types
 */
public final class DFSA<T,S> implements Scored {

  Object dfsaID;
  DFSAState<T,S> initialState;

  public DFSA(DFSAState<T,S> initialState, double score) {
    this.initialState = initialState;
    this.score = score;
  }

  public DFSA(DFSAState<T,S> initialState) {
    this.initialState = initialState;
    this.score = Double.NaN;
  }

  public double score;

  public double score() {
    return score;
  }

  public DFSAState<T,S> initialState() {
    return initialState;
  }

  public void setInitialState(DFSAState<T,S> initialState) {
    this.initialState = initialState;
  }

  public Set<DFSAState<T, S>> states() {
    Set<DFSAState<T, S>> visited = Generics.newHashSet();
    List<DFSAState<T,S>> toVisit = new ArrayList<DFSAState<T,S>>();
    toVisit.add(initialState());
    exploreStates(toVisit, visited);
    return visited;
  }

  private static <T, S> void exploreStates(List<DFSAState<T, S>> toVisit, Set<DFSAState<T, S>> visited) {
    while (!toVisit.isEmpty()) {
      DFSAState<T, S> state = toVisit.get(toVisit.size() - 1);
      toVisit.remove(toVisit.size() - 1);
      if (!visited.contains(state)) {
        toVisit.addAll(state.successorStates());
        visited.add(state);
      }
    }
  }

  public DFSA(Object dfsaID) {
    this.dfsaID = dfsaID;
    this.score = 0;
  }


  private static <T, S> void printTrieDFSAHelper(DFSAState<T, S> state, int level) {
    if (state.isAccepting()) {
      return;
    }
    Set<T> inputs = state.continuingInputs();
    for (T input : inputs) {
      DFSATransition<T, S> transition = state.transition(input);
      System.out.print(level);
      System.out.print(input);
      for (int i = 0; i < level; i++) {
        System.out.print("   ");
      }
      System.out.print(transition.score());
      System.out.print(" ");
      System.out.println(input);
      printTrieDFSAHelper(transition.target(), level + 1);
    }
  }

  public static <T, S> void printTrieDFSA(DFSA<T, S> dfsa) {
    System.err.println("DFSA: " + dfsa.dfsaID);
    printTrieDFSAHelper(dfsa.initialState(), 2);
  }

  public void printAttFsmFormat(Writer w) throws IOException {
    Queue<DFSAState<T,S>> q = new LinkedList<DFSAState<T,S>>();
    Set<DFSAState<T,S>> visited = Generics.newHashSet();
    q.offer(initialState);
    while(q.peek() != null) {
      DFSAState<T, S> state = q.poll();
      if(state == null || visited.contains(state))
        continue;
      visited.add(state);
      if (state.isAccepting()) {
        w.write(state.toString()+"\t"+state.score()+"\n");
        continue;
      }
      TreeSet<T> inputs = new TreeSet<T>(state.continuingInputs());
      for (T input : inputs) {
        DFSATransition<T, S> transition = state.transition(input);
        DFSAState<T,S> target = transition.target();
        if(!visited.contains(target))
          q.add(target);
        w.write(state.toString()+"\t"+target.toString()+"\t"+transition.getInput()+"\t"+transition.score()+"\n");
      }
    }
  }

  private static <T, S> void printTrieAsRulesHelper(DFSAState<T, S> state, String prefix, Writer w) throws IOException {
    if (state.isAccepting()) {
      return;
    }
    Set<T> inputs = state.continuingInputs();
    for (T input : inputs) {
      DFSATransition<T, S> transition = state.transition(input);
      DFSAState<T, S> target = transition.target();
      Set<T> inputs2 = target.continuingInputs();
      boolean allTerminate = true;
      for (T input2 : inputs2) {
        DFSATransition<T, S> transition2 = target.transition(input2);
        DFSAState<T, S> target2 = transition2.target();
        if (target2.isAccepting()) {
          // it's a binary end rule.  Print it.
          w.write(prefix + " --> " + input + " " + input2 + "\n");
        } else {
          allTerminate = false;
        }
      }
      if (!allTerminate) {
        // there are some longer continuations.  Print continuation rule
        String newPrefix = prefix + "_" + input;
        w.write(prefix + " --> " + input + " " + newPrefix + "\n");
        printTrieAsRulesHelper(transition.target(), newPrefix, w);
      }
    }
  }

  public static <T, S> void printTrieAsRules(DFSA<T, S> dfsa, Writer w) throws IOException {
    printTrieAsRulesHelper(dfsa.initialState(), dfsa.dfsaID.toString(), w);
  }
}
