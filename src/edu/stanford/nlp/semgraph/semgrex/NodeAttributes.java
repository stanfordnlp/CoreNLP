package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Quadruple;
import edu.stanford.nlp.util.Quintuple;

/**
 * Stores attributes for a Semgrex NodePattern.
 *<br>
 * For example, {@code word=foo} gets its own node attribute.
 * {@code cpos=NOUN} gets it own attribute.
 * Each requested attribute gets stored in {@code attributes}.
 *<br>
 * Maps (such as MorphoFeatures) also get stored here, in {@code contains}.
 *<br>
 * Refactored out of the parser itself for a couple reasons:
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
  // String, String, Boolean, List, Boolean: key, value, negated, named variable groups, case insensitive
  private List<Quintuple<String, String, Boolean, List<Pair<Integer, String>>, Boolean>> attributes;
  private Set<String> positiveAttributes;
  // Some annotations, especially morpho freatures (CoreAnnotations.CoNLLUFeats)
  // are represented by Maps.  In some cases it will be easier to search
  // for individual elements of that map rather than turn the map into a string
  // and search on its contents that way.  This is especially true since there
  // is no guarantee the map will be in a consistent order.
  // String, String, String, Boolean: node attribute for a map (such as CoNLLUFeats), key in that map, value to match, negated?
  private List<Quadruple<String, String, String, Boolean>> contains;

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

  public void setAttribute(String key, String value, boolean negated, List<Pair<Integer, String>> varGroups, boolean caseInsensitive) {
    if (!negated) {
      if (positiveAttributes.contains(key)) {
        throw new SemgrexParseException("Duplicate attribute " + key + " found in semgrex expression");
      }
      positiveAttributes.add(key);
    }
    if (!"word".equals(key)) {
      caseInsensitive = false;
    }
    attributes.add(new Quintuple<>(key, value, negated, varGroups, caseInsensitive));
  }

  public void addContains(String annotation, String key, String value, Boolean negated) {
    contains.add(new Quadruple(annotation, key, value, negated));
  }

  public List<Quintuple<String, String, Boolean, List<Pair<Integer, String>>, Boolean>> attributes() {
    return Collections.unmodifiableList(attributes);
  }

  public List<Quadruple<String, String, String, Boolean>> contains() {
    return Collections.unmodifiableList(contains);
  }
}
