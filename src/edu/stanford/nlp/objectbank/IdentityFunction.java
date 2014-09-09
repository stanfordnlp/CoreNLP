package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.util.Function;


/** An Identity function that returns its argument.
 * 
 *  @author Jenny Finkel
 */
public class IdentityFunction<X> implements Function<X, X> {

  /**
   * @param o The Object to be returned
   * @return o
   */
  public X apply(X o) {
    return o;
  }

}
