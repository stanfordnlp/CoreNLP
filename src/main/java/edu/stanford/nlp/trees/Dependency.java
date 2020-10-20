package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

import java.io.Serializable;


/**
 * An individual dependency between a governor and a dependent.
 * The governor and dependent are represented as a Label.
 * For example, these can be a
 * Word or a WordTag.  If one wishes the dependencies to preserve positions
 * in a sentence, then each can be a LabeledConstituent or CoreLabel.
 * Dependencies support an Object naming the dependency type.  This may be
 * null.  Dependencies have factories.
 *
 * @author Christopher Manning
 */
public interface Dependency<G extends Label,D extends Label,N> extends Serializable {

  /**
   * Describes the governor (regent/head) of the dependency relation.
   * @return The governor of this dependency
   */
  public G governor();

  /**
   * Describes the dependent (argument/modifier) of
   * the dependency relation.
   * @return the dependent of this dependency
   */
  public D dependent();

  /**
   * Names the type of dependency (subject, instrument, ...).
   * This might be a String in the simplest case, but can provide for
   * arbitrary object types.
   * @return the name for this dependency type
   */
  public N name();

  /**
   * Are two dependencies equal if you ignore the dependency name.
   * @param o The thing to compare against ignoring name
   * @return true iff the head and dependent are the same.
   */
  public boolean equalsIgnoreName(Object o);

  /**
   * Provide different printing options via a String keyword.
   * The main recognized option currently is "xml".  Otherwise the
   * default toString() is used.
   * @param format A format string, either "xml" or you get the default
   * @return A String representation of the dependency
   */
  public String toString(String format);

  /**
   * Provide a factory for this kind of dependency
   * @return A DependencyFactory
   */
  public DependencyFactory dependencyFactory();

}
