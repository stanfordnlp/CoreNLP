package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.util.Triple;

/**
 * Stores attributes for a Semgrex NodePattern.
 *<br>
 * Refactored out of the parser itself for a couple reason:
 *<ul>
 *<li> Allows combining isRoot ($) with node restrictions (word:foo)
 *<li> Can pass this object around, allowing for more refactoring in the semgrex parser
 *<li> Easier to check for illegal operations
 *</ul>
 *
 * @author John Bauer
 */
public class NodeAttributes {
  private boolean root;
  private boolean empty;
  // String, String, Boolean: key, value, negated
  private List<Triple<String, String, Boolean>> attributes;
  private Set<String> positiveAttributes;
  // Some annotations, especially morpho freatures (CoreAnnotations.CoNLLUFeats)
  // are represented by Maps.  In some cases it will be easier to search
  // for individual elements of that map rather than turn the map into a string
  // and search on its contents that way.  This is especially true since there
  // is no guarantee the map will be in a consistent order.
  // String, String, String: node attribute for a map (such as CoNLLUFeats), key in that map, value to match
  private List<Triple<String, String, String>> contains;

  public NodeAttributes() {
    root = false;
    empty = false;
    attributes = new ArrayList<>();
    positiveAttributes = new HashSet<>();
    contains = new ArrayList<>();
  }

  public void setRoot(boolean root) {
    this.root = root;
  }

  public boolean root() {
    return root;
  }

  public void setEmpty(boolean empty) {
    this.empty = empty;
  }

  public boolean empty() {
    return empty;
  }

  public void setAttribute(String key, String value, boolean negated) {
    if (positiveAttributes.contains(key)) {
      throw new SemgrexParseException("Duplicate attribute " + key + " found in semgrex expression");
    }
    if (!negated) {
      positiveAttributes.add(key);
    }
    attributes.add(new Triple(key, value, negated));
  }

  public void addContains(String annotation, String key, String value) {
    contains.add(new Triple(annotation, key, value));
  }

  public List<Triple<String, String, Boolean>> attributes() {
    return Collections.unmodifiableList(attributes);
  }

  public List<Triple<String, String, String>> contains() {
    return Collections.unmodifiableList(contains);
  }
}
