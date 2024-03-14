// Stanford Dependencies - Code for producing and using Stanford dependencies.
// Copyright © 2005-2014 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    parser-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/stanford-dependencies.shtml

package edu.stanford.nlp.trees;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalRelations;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;


/**
 * {@code GrammaticalRelation} is used to define a
 * standardized, hierarchical set of grammatical relations,
 * together with patterns for identifying them in
 * parse trees.
 *
 * Each {@code GrammaticalRelation} has:
 * <ul>
 *   <li>A {@code String} short name, which should be a lowercase
 *       abbreviation of some kind (in the fure mainly Universal Dependency names).</li>
 *   <li>A {@code String} long name, which should be descriptive.</li>
 *   <li>A parent in the {@code GrammaticalRelation} hierarchy.</li>
 *   <li>A {@link Pattern {@code Pattern}} called
 *   {@code sourcePattern} which matches (parent) nodes from which
 *   this {@code GrammaticalRelation} could hold.  (Note: this is done
 *   with the Java regex Pattern {@code matches()} predicate. The pattern
 *   must match the
 *   whole node name, and {@code ^} or {@code $} aren't needed.
 *   Tregex constructions like __ do not work. Use ".*" to be applicable
 *   at all nodes. This prefiltering is used for efficiency.)</li>
 *   <li>A list of zero or more {@link TregexPattern
 *   {@code TregexPattern}s} called {@code targetPatterns},
 *   which describe the local tree structure which must hold between
 *   the source node and a target node for the
 *   {@code GrammaticalRelation} to apply. (Note: {@code tregex}
 *   regular expressions match with the {@code find()} method, while
 *   literal string label descriptions that are not regular expressions must
 *   be {@code equals()}.)</li>
 * </ul>
 *
 * The {@code targetPatterns} associated
 * with a {@code GrammaticalRelation} are designed as follows.
 * In order to recognize a grammatical relation X holding between
 * nodes A and B in a parse tree, we want to associate with
 * {@code GrammaticalRelation} X a {@link TregexPattern
 * {@code TregexPattern}} such that:
 * <ul>
 *   <li>the root of the pattern matches A, and</li>
 *   <li>the pattern includes a node labeled "target", which matches B.</li>
 * </ul>
 * For example, for the grammatical relation {@code PREDICATE}
 * which holds between a clause and its primary verb phrase, we might
 * want to use the pattern {@code "S < VP=target"}, in which the
 * root will match a clause and the node labeled {@code "target"}
 * will match the verb phrase.<p>
 *
 * For a given grammatical relation, the method {@link
 * GrammaticalRelation#getRelatedNodes {@code getRelatedNodes()}}
 * takes a {@code Tree} node as an argument and attempts to
 * return other nodes which have this grammatical relation to the
 * argument node.  By default, this method operates as follows: it
 * steps through the patterns in the pattern list, trying to match
 * each pattern against the argument node, until it finds some
 * matches.  If a pattern matches, all matching nodes (that is, each
 * node which corresponds to node label "target" in some match) are
 * returned as a list; otherwise the next pattern is tried.<p>
 *
 * For some grammatical relations, we need more sophisticated logic to
 * identify related nodes.  In such cases, {@link
 * GrammaticalRelation#getRelatedNodes {@code getRelatedNodes()}}
 * can be overridden on a per-relation basis using anonymous subclassing.<p>
 *
 * @see GrammaticalStructure
 * @see EnglishGrammaticalStructure
 * @see EnglishGrammaticalRelations
 * @see edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalRelations
 *
 * @author Bill MacCartney
 * @author Galen Andrew (refactoring English-specific stuff)
 * @author Ilya Sherman (refactoring annotation-relation pairing, which is now gone)
 */
public class GrammaticalRelation implements Comparable<GrammaticalRelation>, Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(GrammaticalRelation.class);

  private static final long serialVersionUID = 892618003417550128L;

  private static final boolean DEBUG = System.getProperty("GrammaticalRelation", null) != null;

  private static final EnumMap<Language, Map<String, GrammaticalRelation>>
    stringsToRelations = new EnumMap<>(Language.class);

  /**
   * The "governor" grammatical relation, which is the inverse of "dependent".<p>
   * <br>
   * Example: "the red car" &rarr; {@code gov}(red, car)
   */
  public static final GrammaticalRelation GOVERNOR =
    new GrammaticalRelation(Language.Any, "gov", "governor", null);


  /**
   * The "dependent" grammatical relation, which is the inverse of "governor".<p>
   * <br>
   * Example: "the red car" &rarr; {@code dep}(car, red)
   */
  public static final GrammaticalRelation DEPENDENT =
    new GrammaticalRelation(Language.Any, "dep", "dependent", null);


  /**
   *  The "root" grammatical relation between a faked "ROOT" node, and the root of the sentence.
   */
  public static final GrammaticalRelation ROOT =
    new GrammaticalRelation(Language.Any, "root", "root", null);


  /**
   * Dummy relation, used while collapsing relations, e.g., in English &amp; Chinese GrammaticalStructure
   */
  public static final GrammaticalRelation KILL =
    new GrammaticalRelation(Language.Any, "KILL", "dummy relation kill", null);


  /**
   * Returns the GrammaticalRelation having the given string
   * representation (e.g. "nsubj"), or null if no such is found.
   *
   * @param s The short name of the GrammaticalRelation
   * @param values The set of GrammaticalRelations to look for it among.
   * @return The GrammaticalRelation with that name
   */
  public static GrammaticalRelation valueOf(String s, Collection<GrammaticalRelation> values, Lock readValuesLock) {
    readValuesLock.lock();
    try {
      for (GrammaticalRelation reln : values) {
        if (reln.toString().equals(s)) return reln;
      }
    } finally {
      readValuesLock.unlock();
    }

    return null;
  }

  /**
   * Returns the GrammaticalRelation having the given string
   * representation (e.g. "nsubj"), or null if no such is found.
   *
   * @param s The short name of the GrammaticalRelation
   * @param map The map from string to GrammaticalRelation
   * @return The GrammaticalRelation with that name
   */
  public static GrammaticalRelation valueOf(String s, Map<String, GrammaticalRelation> map) {
    if (map.containsKey(s)) {
      return map.get(s);
    }
    return null;
  }

  /** Convert from a String representation of a GrammaticalRelation to a
   *  GrammaticalRelation.  Where possible, you should avoid using this
   *  method and simply work with true GrammaticalRelations rather than
   *  String representations.  Correct behavior of this method depends
   *  on the underlying data structure resources used being kept in sync
   *  with the toString() and equals() methods.  However, there is really
   *  no choice but to use this method when storing GrammaticalRelations
   *  to text files and then reading them back in, so this method is not
   *  deprecated.
   *
   *  @param s The String representation of a GrammaticalRelation
   *  @return The grammatical relation represented by this String
   */
  public static GrammaticalRelation valueOf(Language language, String s) {
    GrammaticalRelation reln;
    synchronized (stringsToRelations) {
      reln = (stringsToRelations.get(language) != null ? valueOf(s, stringsToRelations.get(language)) : null);
    }
    if (reln == null) {
      // TODO this breaks the hierarchical structure of the classes,
      //      but it makes English relations that much likelier to work.
      reln = UniversalEnglishGrammaticalRelations.valueOf(s);
    }
    if (reln == null) {
      // the block below fails when 'specific' includes underscores.
      // this is possible on weird web text, which generates relations such as prep______
      /*
      String[] names = s.split("_");
      String specific = names.length > 1? names[1] : null;
      reln = new GrammaticalRelation(language, names[0], null, null, null, specific);
      */
      String name;
      String specific;
      char separator = (language == Language.UniversalEnglish || language == Language.Unknown) ? ':' : '_';
      int underscorePosition = s.indexOf(separator);
      if (underscorePosition > 0) {
        name = s.substring(0, underscorePosition);
        specific = s.substring(underscorePosition + 1);
      } else {
        name = s;
        specific = null;
      }
      reln = new GrammaticalRelation(language, name, null, null, specific);

    }
    return reln;
  }

  public static GrammaticalRelation valueOf(String s) {
    return valueOf(Language.Any, s);
  }

  /**
   * This function is used to determine whether the GrammaticalRelation in
   * question is one that was created to be a thin wrapper around a String
   * representation by valueOf(String), or whether it is a full-fledged
   * GrammaticalRelation created by direct invocation of the constructor.
   *
   * @return Whether this relation is just a wrapper created by valueOf(String)
   */
  public boolean isFromString() {
    return longName == null;
  }

  /* Non-static stuff */
  private final Language language;
  private final String shortName;
  private final String longName;
  private final GrammaticalRelation parent;
  private final List<GrammaticalRelation> children = new ArrayList<>();
  // a regexp for node values at which this relation can hold
  private final Pattern sourcePattern;
  private final List<TregexPattern> targetPatterns = new ArrayList<>();
  private final String specific; // to hold the specific prep or conjunction associated with the grammatical relation

  // TODO document constructor
  // TODO change to put specificString after longName, and then use String... for targetPatterns
  private GrammaticalRelation(Language language,
                             String shortName,
                             String longName,
                             GrammaticalRelation parent,
                             String sourcePattern,
                             TregexPatternCompiler tregexCompiler,
                             String[] targetPatterns,
                             String specificString) {
    this.language = language;
    this.shortName = shortName;
    this.longName = longName;
    this.parent = parent;
    this.specific = specificString; // this can be null!

    if (parent != null) {
      parent.addChild(this);
    }

    if (sourcePattern != null) {
      try {
        this.sourcePattern = Pattern.compile(sourcePattern);
      } catch (java.util.regex.PatternSyntaxException e) {
        throw new RuntimeException("Bad pattern: " + sourcePattern);
      }
    } else {
      this.sourcePattern = null;
    }

    for (String pattern : targetPatterns) {
      try {
        TregexPattern p = tregexCompiler.compile(pattern);
        this.targetPatterns.add(p);
      } catch (edu.stanford.nlp.trees.tregex.TregexParseException pe) {
        throw new RuntimeException("Bad pattern: " + pattern, pe);
      }
    }

    GrammaticalRelation previous;
    synchronized (stringsToRelations) {
      Map<String, GrammaticalRelation> sToR = stringsToRelations.get(language);
      if (sToR == null) {
        sToR = Generics.newHashMap();
        stringsToRelations.put(language, sToR);
      }
      previous = sToR.put(toString(), this);
    }
    if (previous != null) {
      if ( ! previous.isFromString() && ! isFromString()) {
        throw new IllegalArgumentException("There is already a relation named " + this + '!');
      } else {
        /* We get here if we previously just built a fake relation from a string
         * we previously read in from a file.
         */
        // TODO is it worth copying all of the information from this real
        //      relation into the old fake one?
      }
    }
  }

  // This is the main constructor used
  public GrammaticalRelation(Language language,
                             String shortName,
                             String longName,
                             GrammaticalRelation parent,
                             String sourcePattern,
                             TregexPatternCompiler tregexCompiler,
                             String... targetPatterns) {
    this(language, shortName, longName, parent, sourcePattern, tregexCompiler, targetPatterns, null);
  }

  // Used for non-leaf relations with no patterns
  public GrammaticalRelation(Language language,
                             String shortName,
                             String longName,
                             GrammaticalRelation parent) {
    this(language, shortName, longName, parent, null, null, StringUtils.EMPTY_STRING_ARRAY, null);
  }

  // used to create collapsed relations with specificString
  public GrammaticalRelation(Language language,
                             String shortName,
                             String longName,
                             GrammaticalRelation parent,
                             String specificString) {
    this(language, shortName, longName, parent, null, null, StringUtils.EMPTY_STRING_ARRAY, specificString);
  }

  private void addChild(GrammaticalRelation child) {
    children.add(child);
  }

  public List<TregexPattern> targetPatterns() {
    return Collections.unmodifiableList(targetPatterns);
  }

  /** Given a {@code Tree} node {@code t}, attempts to
   *  return a list of nodes to which node {@code t} has this
   *  grammatical relation, with {@code t} as the governor.
   *
   *  @param t Target for finding dependents of t related by this GR
   *  @param root The root of the Tree
   *  @return A Collection of dependent nodes to which t bears this GR
   */
  public Collection<TreeGraphNode> getRelatedNodes(TreeGraphNode t, TreeGraphNode root, HeadFinder headFinder) {
    Set<TreeGraphNode> nodeList = new ArraySet<>();
    for (TregexPattern p : targetPatterns) {    // cdm: I deleted: && nodeList.isEmpty()
      // Initialize the TregexMatcher with the HeadFinder so that we
      // can use the same HeadFinder through the entire process of
      // building the dependencies
      TregexMatcher m = p.matcher(root, headFinder);
      while (m.findAt(t)) {
        TreeGraphNode target = (TreeGraphNode) m.getNode("target");
        if (target == null) {
          throw new AssertionError("Expression has no target: " + p);
        }
        nodeList.add(target);
        if (DEBUG) {
          log.info("found " + this + "(" + t + "-" + t.headWordNode() + ", " + m.getNode("target") + "-" + ((TreeGraphNode) m.getNode("target")).headWordNode() + ") using pattern " + p);
          for (String nodeName : m.getNodeNames()) {
            if (nodeName.equals("target"))
              continue;
            log.info("  node " + nodeName + ": " + m.getNode(nodeName));
          }
        }
      }
    }
    return nodeList;
  }

  /** Returns {@code true} iff the value of {@code Tree}
   *  node {@code t} matches the {@code sourcePattern} for
   *  this {@code GrammaticalRelation}, indicating that this
   *  {@code GrammaticalRelation} is one that could hold between
   *  {@code Tree} node {@code t} and some other node.
   */
  public boolean isApplicable(Tree t) {
    // log.info("Testing whether " + sourcePattern + " matches " + ((TreeGraphNode) t).toOneLineString());
    return (sourcePattern != null) && (t.value() != null) &&
             sourcePattern.matcher(t.value()).matches();
  }

  /** Returns whether this is equal to or an ancestor of gr in the grammatical relations hierarchy. */
  public boolean isAncestor(GrammaticalRelation gr) {
    while (gr != null) {
      // Changed this test from this == gr (mrsmith)
      if (this.equals(gr)) { return true; }
      gr = gr.parent;
    }
    return false;
  }

  /**
   * Returns short name (abbreviation) for this
   * {@code GrammaticalRelation}.  toString() for collapsed
   * relations will include the word that was collapsed.
   * <br>
   * <i>Implementation note:</i> Note that this method must be synced with
   * the equals() and valueOf(String) methods
   */
  @Override
  public final String toString() {
    if (specific == null) {
      return shortName;
    } else {
      char sep = (language == Language.English || language == Language.Chinese )? '_' : ':';
      return shortName + sep + specific;
    }
  }

  /**
   * Returns a {@code String} representation of this
   * {@code GrammaticalRelation} and the hierarchy below
   * it, with one node per line, indented according to level.
   *
   * @return {@code String} representation of this
   *         {@code GrammaticalRelation}
   */
  public String toPrettyString() {
    StringBuilder buf = new StringBuilder("\n");
    toPrettyString(0, buf);
    return buf.toString();
  }

  /**
   * Returns a {@code String} representation of this
   * {@code GrammaticalRelation} and the hierarchy below
   * it, with one node per line, indented according to
   * {@code indentLevel}.
   *
   * @param indentLevel how many levels to indent (0 for root node)
   */
  private void toPrettyString(int indentLevel, StringBuilder buf) {
    for (int i = 0; i < indentLevel; i++) {
      buf.append("  ");
    }
    buf.append(shortName).append(" (").append(longName).append("): ").append(targetPatterns);
    for (GrammaticalRelation child : children) {
      buf.append('\n');
      child.toPrettyString(indentLevel + 1, buf);
    }
  }

  /** Grammatical relations are equal with other grammatical relations if they
   *  have the same shortName and specific (if present).
   *  <i>Implementation note:</i> Note that this method must be synced with
   *  the toString() and valueOf(String) methods
   *
   *  @param o Object to be compared
   *  @return Whether equal
   */
  @SuppressWarnings({"StringEquality", "ThrowableInstanceNeverThrown"})
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof String) {
      // TODO: Remove this. It's broken but was meant to cover legacy code. It would be correct to just return false.
      new Throwable("Warning: comparing GrammaticalRelation to String").printStackTrace();
      return this.toString().equals(o);
    }
    if (!(o instanceof GrammaticalRelation)) return false;

    final GrammaticalRelation gr = (GrammaticalRelation) o;
    // == okay for language as enum!
    // TODO(gabor) perhaps Language.Any shouldn't be equal to any language? This is a bit of a hack around some dependencies caring about language and others not.
    return (this.language.compatibleWith(gr.language)) &&
             this.shortName.equals(gr.shortName) &&
             (Objects.equals(this.specific, gr.specific));
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 29 * result + (language != null ? language.toString().hashCode() : 0);
    result = 29 * result + (shortName != null ? shortName.hashCode() : 0);
    result = 29 * result + (specific != null ? specific.hashCode() : 0);
    return result;
  }

  @Override
  public int compareTo(GrammaticalRelation o) {
    String thisN = this.toString();
    String oN = o.toString();
    return thisN.compareTo(oN);
  }

  public String getLongName() {
    return longName;
  }

  public String getShortName() {
    return shortName;
  }

  /**
   * Get the language of the grammatical relation.
   */
  public Language getLanguage() {
    return this.language;
  }

  public String getSpecific() {
    return specific;
  }

  /**
   * When deserializing a GrammaticalRelation, it needs to be matched
   * up with the existing singleton relation of the same type.
   *
   * TODO: there are a bunch of things wrong with this.  For one
   * thing, it's crazy slow, since it goes through all the existing
   * relations in an array.  For another, it would be cleaner to have
   * subclasses for the English and Chinese relations
   */
  protected Object readResolve() throws ObjectStreamException {
    switch (language) {
    case Any: {
      if (shortName.equals(GOVERNOR.shortName)) {
        return GOVERNOR;
      } else if (shortName.equals(DEPENDENT.shortName)) {
        return DEPENDENT;
      } else if (shortName.equals(ROOT.shortName)) {
        return ROOT;
      } else if (shortName.equals(KILL.shortName)) {
        return KILL;
      } else {
        throw new RuntimeException("Unknown general relation " + shortName);
      }
    }
    case English: {
      GrammaticalRelation rel = EnglishGrammaticalRelations.valueOf(toString());
      if (rel == null) {
        switch (shortName) {
          case "conj":
            return EnglishGrammaticalRelations.getConj(specific);
          case "prep":
            return EnglishGrammaticalRelations.getPrep(specific);
          case "prepc":
            return EnglishGrammaticalRelations.getPrepC(specific);
          default:
            // TODO: we need to figure out what to do with relations
            // which were serialized and then deprecated.  Perhaps there
            // is a good way to make them singletons
            return this;
          //throw new RuntimeException("Unknown English relation " + this);
        }
      } else {
        return rel;
      }
    }
    case Chinese: {
      GrammaticalRelation rel = ChineseGrammaticalRelations.valueOf(toString());
      if (rel == null) {
        // TODO: we need to figure out what to do with relations
        // which were serialized and then deprecated.  Perhaps there
        // is a good way to make them singletons
        return this;
        //throw new RuntimeException("Unknown Chinese relation " + this);
      }
      return rel;
    }
    case UniversalEnglish:
      GrammaticalRelation rel = UniversalEnglishGrammaticalRelations.valueOf(toString());
      if (rel == null) {
        switch (shortName) {
          case "conj":
            return UniversalEnglishGrammaticalRelations.getConj(specific);
          case "nmod":
            return UniversalEnglishGrammaticalRelations.getNmod(specific);
          case "acl":
            return UniversalEnglishGrammaticalRelations.getAcl(specific);
          case "advcl":
            return UniversalEnglishGrammaticalRelations.getAdvcl(specific);
          default:
            // TODO: we need to figure out what to do with relations
            // which were serialized and then deprecated.  Perhaps there
            // is a good way to make them singletons
            return this;
          //throw new RuntimeException("Unknown English relation " + this);
        }
      } else {
        return rel;
      }

    default: {
      throw new RuntimeException("Unknown language " + language);
    }
    }
  }

  /**
   * Returns the parent of this {@code GrammaticalRelation}.
   */
  public GrammaticalRelation getParent() {
    return parent;
  }

  public static void main(String[] args) {
    final String[] names = {"dep", "pred", "prep_to","rcmod"};
    for (String name : names) {
      GrammaticalRelation reln = valueOf(Language.English, name);
      System.out.println("Data for GrammaticalRelation loaded as valueOf(\"" + name + "\"):");
      System.out.println("\tShort name:    " + reln.getShortName());
      System.out.println("\tLong name:     " + reln.getLongName());
      System.out.println("\tSpecific name: " + reln.getSpecific());
    }
  }

}
