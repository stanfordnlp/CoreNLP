package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.util.Pair;
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
  // String, String, AttributeMode, List, Boolean: key, value, how the value is matched, named variable groups, case insensitive
  private List<Quintuple<String, String, AttributeMode, List<Pair<Integer, String>>, Boolean>> attributes;
  private Set<String> positiveAttributes;
  // Some annotations, especially morpho freatures (CoreAnnotations.CoNLLUFeats)
  // are represented by Maps.  In some cases it will be easier to search
  // for individual elements of that map rather than turn the map into a string
  // and search on its contents that way.  This is especially true since there
  // is no guarantee the map will be in a consistent order.
  // String, String, String, AttributeMode, List: node attribute for a map (such as CoNLLUFeats),
  // key in that map, value to match, how the value is matched, named variable groups
  private List<Quintuple<String, String, String, AttributeMode, List<Pair<Integer, String>>>> contains;

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

  public void setAttribute(String key, String value, AttributeMode mode, List<Pair<Integer, String>> varGroups, boolean caseInsensitive) {
    // only a required attribute can conflict with another of the same
    // key.  two negated or two optional constraints are both satisfiable
    if (mode == AttributeMode.REQUIRED) {
      if (positiveAttributes.contains(key)) {
        throw new SemgrexParseException("Duplicate attribute " + key + " found in semgrex expression");
      }
      positiveAttributes.add(key);
    }
    if (!"word".equals(key)) {
      caseInsensitive = false;
    }
    attributes.add(new Quintuple<>(key, value, mode, varGroups, caseInsensitive));
  }

  public void addContains(String annotation, String key, String value, AttributeMode mode,
                          List<Pair<Integer, String>> varGroups) {
    contains.add(new Quintuple(annotation, key, value, mode, new ArrayList<>(varGroups)));
  }

  public List<Quintuple<String, String, AttributeMode, List<Pair<Integer, String>>, Boolean>> attributes() {
    return Collections.unmodifiableList(attributes);
  }

  public List<Quintuple<String, String, String, AttributeMode, List<Pair<Integer, String>>>> contains() {
    return Collections.unmodifiableList(contains);
  }
}
