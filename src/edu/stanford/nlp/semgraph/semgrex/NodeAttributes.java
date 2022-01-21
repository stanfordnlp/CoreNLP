package edu.stanford.nlp.semgraph.semgrex;

import java.util.LinkedHashMap;
import java.util.Map;

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
  private Map<String, String> attributes;

  public NodeAttributes() {
    root = false;
    empty = false;
    attributes = new LinkedHashMap<>();
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

  public void setAttribute(String key, String value) {
    if (attributes.containsKey(key)) {
      throw new SemgrexParseException("Duplicate attribute " + key + " found in semgrex expression");
    }
    attributes.put(key, value);
  }

  public Map<String, String> attributes() {
    return attributes;
  }
}
