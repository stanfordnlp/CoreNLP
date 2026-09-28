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

  /** Creates an empty set of attributes: not root, not empty, no attribute or map constraints. */
  public NodeAttributes() {
    root = false;
    empty = false;
    attributes = new ArrayList<>();
    positiveAttributes = new HashSet<>();
    contains = new ArrayList<>();
  }

  /**
   * Sets whether the node must be a root of the graph ({@code $} in the pattern).
   *
   * @param root whether the node must be a root
   */
  public void setRoot(boolean root) {
    this.root = root;
  }

  /**
   * Returns whether the node must be a root of the graph.
   *
   * @return whether {@code $} was given for this node
   */
  public boolean root() {
    return root;
  }

  /**
   * Sets whether the node must be the empty node {@link edu.stanford.nlp.ling.IndexedWord#NO_WORD} ({@code #} in the pattern).
   *
   * @param empty whether the node must be the empty node
   */
  public void setEmpty(boolean empty) {
    this.empty = empty;
  }

  /**
   * Returns whether the node must be the empty node {@link edu.stanford.nlp.ling.IndexedWord#NO_WORD}.
   *
   * @return whether {@code #} was given for this node
   */
  public boolean empty() {
    return empty;
  }

  /**
   * Adds a constraint on a single node attribute, such as {@code word:foo}.
   * The case insensitive flag is kept only for the {@code word} attribute and dropped for any other key.
   *
   * @param key the attribute name
   * @param value the value to match, as written in the pattern (a string, a regex, or {@code __})
   * @param mode how the value is matched: required, negated, or optional
   * @param varGroups the named variable groups captured from a regex value, as (group number, name) pairs
   * @param caseInsensitive whether to match the value ignoring case
   * @throws SemgrexParseException if mode is required and a required constraint on the same key was already added
   */
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

  /**
   * Adds a constraint on one key of a Map valued annotation, such as {@code morphofeatures:{Number:Sing}}.
   *
   * @param annotation the name of the Map valued annotation
   * @param key the key in that map (a string, a regex, or {@code __})
   * @param value the value to match for that key (a string, a regex, or {@code __})
   * @param mode how the value is matched: required, negated, or optional
   * @param varGroups the named variable groups captured from a regex value; the list is copied
   */
  public void addContains(String annotation, String key, String value, AttributeMode mode,
                          List<Pair<Integer, String>> varGroups) {
    contains.add(new Quintuple(annotation, key, value, mode, new ArrayList<>(varGroups)));
  }

  /**
   * Returns the single attribute constraints in the order they were added.
   *
   * @return an unmodifiable view of (key, value, mode, variable groups, case insensitive) entries
   */
  public List<Quintuple<String, String, AttributeMode, List<Pair<Integer, String>>, Boolean>> attributes() {
    return Collections.unmodifiableList(attributes);
  }

  /**
   * Returns the Map valued annotation constraints in the order they were added.
   *
   * @return an unmodifiable view of (annotation, key, value, mode, variable groups) entries
   */
  public List<Quintuple<String, String, String, AttributeMode, List<Pair<Integer, String>>>> contains() {
    return Collections.unmodifiableList(contains);
  }
}
