package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

/**
 * A <code>ConstituentFactory</code> is a factory for creating objects
 * of class <code>Constituent</code>, or some descendent class.
 * An interface.
 *
 * @author Christopher Manning
 */
public interface ConstituentFactory {

  /**
   * Build a constituent with this start and end.
   *
   * @param start Start position
   * @param end   End position
   */
  public Constituent newConstituent(int start, int end);

  /**
   * Build a constituent with this start and end.
   *
   * @param start Start position
   * @param end   End position
   * @param label Label
   * @param score Score
   */
  public Constituent newConstituent(int start, int end, Label label, double score);
}
