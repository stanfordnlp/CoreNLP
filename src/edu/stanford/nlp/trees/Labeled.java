package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

import java.util.Collection;

/**
 * Interface for Objects which have a <code>Label</code>.
 * For instance, they may be hand-classified with one or more tags.
 * Note that it is for things that possess
 * a label via composition, rather than for things that implement
 * the <code>Label</code> interface.
 * An implementor might choose to be read-only and throw an
 * UnsupportedOperationException on the setLabel(s)() commands, but should
 * minimally implement both commands to return Label(s).
 *
 * @author Sep Kamvar
 * @author Christopher Manning
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - filled in types
 */

public interface Labeled {

  /**
   * Returns the Object's label.
   *
   * @return One of the labels of the object (if there are multiple labels,
   *         preferably the primary label, if it exists).
   *         Returns null if there is no label.
   */

  public Label label();


  /**
   * Sets the label associated with this object.
   *
   * @param label The Label value
   */

  public void setLabel(final Label label);


  /**
   * Gives back all labels for this thing.
   *
   * @return A Collection of the Object's labels.  Returns an empty
   *         Collection if there are no labels.
   */

  public Collection<Label> labels();


  /**
   * Sets the labels associated with this object.
   *
   * @param labels The set of Label values
   */

  public void setLabels(final Collection<Label> labels);

}
