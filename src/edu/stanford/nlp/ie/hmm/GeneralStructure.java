package edu.stanford.nlp.ie.hmm;


/**
 * A simple interface for anything that has a <code>State</code> array.
 *
 * @author Jim McFadden
 */
public interface GeneralStructure {
  public State[] getStates();
}
