package edu.stanford.nlp.fsm;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Scored;

import java.util.*;

/**
 * DFSAState
 * <p/>
 * Class for representing the state of a deterministic finite state
 * automaton without epsilon transitions.
 *
 * @author Dan Klein
 * @version 12/14/2000
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in types
 * @param <S> stateID type
 * @param <T> transition type
 */
public final class DFSAState<T,S> implements Scored {

  private S stateID;
  private Map<T,DFSATransition<T,S>> inputToTransition;
  public boolean accepting;
  private DFSA<T,S> dfsa;

  public double score;

  public double score() {
    return score;
  }

  public void setScore(double score) {
    this.score = score;
  }


  public DFSA<T, S> dfsa() {
    return dfsa;
  }

  public void setStateID(S stateID) {
    this.stateID = stateID;
  }

  public S stateID() {
    return stateID;
  }

  public void addTransition(DFSATransition<T,S> transition) {
    inputToTransition.put(transition.input(), transition);
  }

  public DFSATransition<T,S> transition(T input) {
    return inputToTransition.get(input);
  }

  public Collection<DFSATransition<T,S>> transitions() {
    return inputToTransition.values();
  }

  public Set<T> continuingInputs() {
    return inputToTransition.keySet();
  }

  public Set<DFSAState<T,S>> successorStates() {
    Set<DFSAState<T,S>> successors = Generics.newHashSet();
    Collection<DFSATransition<T, S>> transitions = inputToTransition.values();
    for (DFSATransition<T,S> transition : transitions) {
      successors.add(transition.getTarget());
    }
    return successors;
  }

  public void setAccepting(boolean accepting) {
    this.accepting = accepting;
  }

  public boolean isAccepting() {
    return accepting;
  }

  public boolean isContinuable() {
    return !inputToTransition.isEmpty();
  }

  @Override
  public String toString() {
    return stateID.toString();
  }

  private int hashCodeCache; // = 0;

  @Override
  public int hashCode() {
    if (hashCodeCache == 0) {
      hashCodeCache = stateID.hashCode() ^ dfsa.hashCode();
    }
    return hashCodeCache;
  }

  // equals
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DFSAState)) {
      return false;
    }
    DFSAState s = (DFSAState) o;
    // historically also checked: accepting == s.accepting &&
    //inputToTransition.equals(s.inputToTransition))
    return dfsa.equals(s.dfsa) && stateID.equals(s.stateID);
  }

  public Set<DFSAState<T, S>> statesReachable() {
    Set<DFSAState<T, S>> visited = Generics.newHashSet();
    List<DFSAState<T, S>> toVisit = new ArrayList<DFSAState<T, S>>();
    toVisit.add(this);
    exploreStates(toVisit, visited);
    return visited;
  }

  private void exploreStates(List<DFSAState<T, S>> toVisit, Set<DFSAState<T, S>> visited) {
    while (!toVisit.isEmpty()) {
      DFSAState<T, S> state = toVisit.get(toVisit.size() - 1);
      toVisit.remove(toVisit.size() - 1);
      if (!visited.contains(state)) {
        toVisit.addAll(state.successorStates());
        visited.add(state);
      }
    }
  }

  public DFSAState(S id, DFSA<T,S> dfsa) {
    this.dfsa = dfsa;
    this.stateID = id;
    this.accepting = false;
    this.inputToTransition = Generics.newHashMap();
    this.score = Double.NEGATIVE_INFINITY;
  }

  public DFSAState(S id, DFSA<T,S> dfsa, double score) {
    this(id,dfsa);
    setScore(score);
  }



}
