package edu.stanford.nlp.scenegraph;

import edu.stanford.nlp.ling.IndexedWord;


/**
 *
 * @author Sebastian Schuster
 *
 */

public class SceneGraphAttribute {

  private final IndexedWord attribute;

  public SceneGraphAttribute(IndexedWord attribute) {
    this.attribute = attribute;
  }

  public IndexedWord value() {
    return this.attribute;
  }


  @Override
  public int hashCode() {
    return attribute.lemma().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    if ( ! (o instanceof SceneGraphAttribute)) {
      return false;
    }

    SceneGraphAttribute otherAttr = (SceneGraphAttribute) o;

    return this.attribute.lemma().equals(otherAttr.attribute.lemma());
  }

  @Override
  public String toString() {
    return this.attribute.lemma();
  }

}
