package edu.stanford.nlp.fsm;

import edu.stanford.nlp.util.Scored;

/**
 * DFSATransition represents a transition in a weighted finite state
 * transducer.  For now, just null out fields that may not apply.
 * This should really be FSATransition as there's nothing
 * deterministic-specific.  If FSA is ever made, this should be
 * abstracted.  The ID is a descriptor, not a unique ID.
 *
 * @author Dan Klein
 * @version 12/14/00
 */
public final class DFSATransition<T,S> implements Scored {

  private Object transitionID;
  private DFSAState<T,S> source;
  protected DFSAState<T,S> target; // used directly in DFSAMinimizer (only)
  private double score;
  private T input;
  private Object output;

  public DFSATransition(Object transitionID, DFSAState<T,S> source, DFSAState<T,S> target, T input, Object output, double score) {
    this.transitionID = transitionID;
    this.source = source;
    this.target = target;
    this.input = input;
    this.output = output;
    this.score = score;
  }

  public DFSAState<T,S> getSource() {
    return source;
  }

  public DFSAState<T,S> source() {
    return source;
  }

  public DFSAState<T,S> getTarget() {
    return target;
  }

  public DFSAState<T,S> target() {
    return target;
  }

  public Object getID() {
    return transitionID;
  }

  public double score() {
    return score;
  }

  public T getInput() {
    return input;
  }

  public T input() {
    return input;
  }

  public Object getOutput() {
    return output;
  }

  public Object output() {
    return output;
  }

  @Override
  public String toString() {
    return "[" + transitionID + "]" + source + " -" + input + ":" + output + "-> " + target;
  }

}
