package edu.stanford.nlp.util;

import java.io.Serializable;


/**
 * Filter is an interface for predicate objects which respond to the
 * <code>accept</code> method.
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 */
public interface Filter <T> extends Serializable {

  /**
   * Checks if the given object passes the filter.
   *
   * @param obj an object to test
   * @return Whether the object should be accepted (for some processing)
   */
  public boolean accept(T obj);

}
