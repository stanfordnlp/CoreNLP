package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
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
  private List<Triple<String, String, Boolean>> attributes;
  private Set<String> positiveAttributes;

  public NodeAttributes() {
    root = false;
    empty = false;
    attributes = new ArrayList<>();
    positiveAttributes = new HashSet<>();
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

  public List<Triple<String, String, Boolean>> attributes() {
    return attributes;
  }
}
