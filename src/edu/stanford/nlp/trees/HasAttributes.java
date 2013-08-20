package edu.stanford.nlp.trees;

import java.util.Map;

/**
 * Something that implements the <code>HasAttributes</code> interface
 * knows about attributes stored in a <code>Map</code>.
 *
 * @author Christopher Manning
 */
public interface HasAttributes {

  /**
   * Manipulate attributes for a Label.
   *
   * @return a map of attribute-value pairs.  This may be
   *         <code>null</code>, if no map is defined in an implementing class.
   */
  public Map attributes();

}
