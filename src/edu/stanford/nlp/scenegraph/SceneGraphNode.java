package edu.stanford.nlp.scenegraph;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel.OutputFormat;
import edu.stanford.nlp.ling.IndexedWord;


/**
 *
 * @author Sebastian Schuster
 *
 */

public class SceneGraphNode implements Comparable<SceneGraphNode> {

  private IndexedWord value;
  private Set<SceneGraphAttribute> attributes;


  public SceneGraphNode(IndexedWord value) {
    this.value = value;
    this.attributes = new HashSet<SceneGraphAttribute>();

  }

  public IndexedWord value() {
    return value;
  }


  public void addAttribute(IndexedWord value) {
    SceneGraphAttribute attribute = new SceneGraphAttribute(value);
    this.addAttribute(attribute);
  }

  public void addAttribute(SceneGraphAttribute attribute) {
    this.attributes.add(attribute);
  }

  public boolean hasAttribute(SceneGraphAttribute attribute) {
    return this.attributes.contains(attribute);
  }

  public void removeAttribute(SceneGraphAttribute attribute) {
    this.attributes.remove(attribute);
  }

  public Set<SceneGraphAttribute> getAttributes() {
    return this.attributes;
  }

  @Override
  public int compareTo(SceneGraphNode o) {
   if (o == null) {
     return 1;
   }

   return this.value.compareTo(o.value);
  }

  @Override
  public int hashCode() {
    return this.value.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    if ( ! (o instanceof SceneGraphNode)) {
      return false;
    }

    SceneGraphNode otherNode = (SceneGraphNode) o;

    return this.value.equals(otherNode.value);
  }

  @Override
  public String toString() {
    return this.value().toString(OutputFormat.LEMMA_INDEX);
  }

  public String toJSONString() {
    if (this.value.containsKey(SceneGraphCoreAnnotations.PredictedEntityAnnotation.class)) {
      return this.value.getString(SceneGraphCoreAnnotations.PredictedEntityAnnotation.class);
    } else if (this.value.containsKey(SceneGraphCoreAnnotations.CompoundLemmaAnnotation.class)) {
      return this.value.getString(SceneGraphCoreAnnotations.CompoundLemmaAnnotation.class);
    } else {
      return this.value.lemma();
    }
  }

}
