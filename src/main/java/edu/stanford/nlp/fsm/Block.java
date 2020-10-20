package edu.stanford.nlp.fsm;

import java.util.Set;

/**
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public interface Block<E> {

  public Set<E> getMembers();

}
