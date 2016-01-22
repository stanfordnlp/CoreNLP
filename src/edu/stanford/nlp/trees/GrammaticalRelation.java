package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;


/**
 * <code>GrammaticalRelation</code> is used to define a
 * standardized, hierarchical set of grammatical relations,
 * together with patterns for identifying them in
 * parse trees.<p>
 *
 * Each <code>GrammaticalRelation</code> has:
 * <ul>
 *   <li>A <code>String</code> short name, which should be a lowercase
 *       abbreviation of some kind.</li>
 *   <li>A <code>String</code> long name, which should be descriptive.</li>
 *   <li>A parent in the <code>GrammaticalRelation</code> hierarchy.</li>
 *   <li>A {@link Pattern <code>Pattern</code>} called
 *   <code>sourcePattern</code> which matches (parent) nodes from which
 *   this <code>GrammaticalRelation</code> could hold.  (Note: this is done
 *   with the Java regex Pattern <code>matches()</code> predicate: the pattern
 *   must match the
 *   whole node name, and <code>^</code> or <code>$</code> aren't needed.
 *   Tregex constructions like __ do not work. Use ".*" to be applicable
 *   at all nodes.)</li>
 *   <li>A list of zero or more {@link TregexPattern
 *   <code>TregexPattern</code>s} called <code>targetPatterns</code>,
 *   which describe the local tree structure which must hold between
 *   the source node and a target node for the
 *   <code>GrammaticalRelation</code> to apply. (Note <code>tregex</code>
 *   regular expressions match with the <code>find()</code> method - though
 *   literal string label descriptions that are not regular expressions must
 *   be <code>equals()</code>.)</li>
 * </ul>
 *
 * The <code>targetPatterns</code> associated
 * with a <code>GrammaticalRelation</code> are designed as follows.
 * In order to recognize a grammatical relation X holding between
 * nodes A and B in a parse tree, we want to associate with
 * <code>GrammaticalRelation</code> X a {@link TregexPattern
 * <code>TregexPattern</code>} such that:
 * <ul>
 *   <li>the root of the pattern matches A, and</li>
 *   <li>the pattern includes a special node label, "target", which matches B.</li>
 * </ul>
 * For example, for the grammatical relation <code>PREDICATE</code>
 * which holds between a clause and its primary verb phrase, we might
 * want to use the pattern <code>"S &lt; VP=target"</code>, in which the
 * root will match a clause and the node labeled <code>"target"</code>
 * will match the verb phrase.<p>
 *
 * For a given grammatical relation, the method {@link
 * GrammaticalRelation#getRelatedNodes <code>getRelatedNodes()</code>}
 * takes a <code>Tree</code> node as an argument and attempts to
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
 * GrammaticalRelation#getRelatedNodes <code>getRelatedNodes()</code>}
 * can be overridden on a per-relation basis using anonymous subclassing.<p>
 *
 * @see GrammaticalStructure
 * @see EnglishGrammaticalStructure
 * @see EnglishGrammaticalRelations
 * @see edu.stanford.nlp.trees.international.pennchinese.ChineseGrammaticalRelations
 *
 * @author Bill MacCartney
 * @author Galen Andrew (refactoring English-specific stuff)
 * @author Ilya Sherman (refactoring annotation-relation pairing)
 */
public class GrammaticalRelation implements Comparable<GrammaticalRelation>, Serializable {

  private static final long serialVersionUID = 892618003417550128L;

  private static final boolean DEBUG = false;

  public abstract static class GrammaticalRelationAnnotation implements CoreAnnotation<Set<TreeGraphNode>> {
    @SuppressWarnings({"unchecked", "RedundantCast"})
    public Class<Set<TreeGraphNode>> getType() {  return (Class) Set.class; }
  }

  private static Map<Class<? extends GrammaticalRelationAnnotation>, GrammaticalRelation>
    annotationsToRelations = Generics.newHashMap();
  private static Map<GrammaticalRelation, Class<? extends GrammaticalRelationAnnotation>>
    relationsToAnnotations = Generics.newHashMap();
  private static EnumMap<Language, Map<String, GrammaticalRelation>>
    stringsToRelations = new EnumMap<Language, Map<String, GrammaticalRelation>>(Language.class);

  /**
   * The "governor" grammatical relation, which is the inverse of "dependent".<p>
   * <p/>
   * Example: "the red car" &rarr; <code>gov</code>(red, car)
   */
  public static final GrammaticalRelation GOVERNOR =
    new GrammaticalRelation(Language.Any, "gov", "governor", GovernorGRAnnotation.class, null);

  public static class GovernorGRAnnotation extends GrammaticalRelationAnnotation { }

  /**
   * The "dependent" grammatical relation, which is the inverse of "governor".<p>
   * <p/>
   * Example: "the red car" &rarr; <code>dep</code>(car, red)
   */
  public static final GrammaticalRelation DEPENDENT =
    new GrammaticalRelation(Language.Any, "dep", "dependent", DependentGRAnnotation.class, null);

  public static class DependentGRAnnotation extends GrammaticalRelationAnnotation{ }

  /**
   *  The "root" grammatical relation between a faked "ROOT" node, and the root of the sentence.
   */
  public static final GrammaticalRelation ROOT =
    new GrammaticalRelation(Language.Any, "root", "root", RootGRAnnotation.class, null);

  public static class RootGRAnnotation extends GrammaticalRelationAnnotation{ }

  /**
   * Dummy relation, used while collapsing relations, in English &amp; Chinese GrammaticalStructure
   */
  public static final GrammaticalRelation KILL =
    new GrammaticalRelation(Language.Any, "KILL", "dummy relation kill", KillGRAnnotation.class, null);

  public static class KillGRAnnotation extends GrammaticalRelationAnnotation { }

  public static Class<? extends GrammaticalRelationAnnotation>
  getAnnotationClass(GrammaticalRelation relation) {
    return relationsToAnnotations.get(relation);
  }

  public static GrammaticalRelation
  getRelation(Class<? extends GrammaticalRelationAnnotation> annotation) {
    return annotationsToRelations.get(annotation);
  }

  /**
   * Returns the GrammaticalRelation having the given string
   * representation (e.g. "nsubj"), or null if no such is found.
   *
   * @param s The short name of the GrammaticalRelation
   * @param values The set of GrammaticalRelations to look for it among.
   * @return The GrammaticalRelation with that name
   */
  public static GrammaticalRelation valueOf(String s, Collection<GrammaticalRelation> values) {
    for (GrammaticalRelation reln : values) {
      if (reln.toString().equals(s)) return reln;
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
    GrammaticalRelation reln = (stringsToRelations.get(language) != null ? valueOf(s, stringsToRelations.get(language).values()) : null);
    if (reln == null) {
      // TODO this breaks the hierarchical structure of the classes,
      //      but it makes English relations that much likelier to work.
      reln = EnglishGrammaticalRelations.valueOf(s);
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
      int underscorePosition = s.indexOf('_');
      if (underscorePosition > 0) {
        name = s.substring(0, underscorePosition);
        specific = s.substring(underscorePosition + 1);
      } else {
        name = s;
        specific = null;
      }
      reln = new GrammaticalRelation(language, name, null, null, null, specific);

    }
    return reln;
  }

  public static GrammaticalRelation valueOf(String s) {
    return valueOf(Language.English, s);
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


  public enum Language { Any, English, Chinese }


  /* Non-static stuff */
  private final Language language;
  private final String shortName;
  private final String longName;
  private final GrammaticalRelation parent;
  private final List<GrammaticalRelation> children = new ArrayList<GrammaticalRelation>();
  // a regexp for node values at which this relation can hold
  private final Pattern sourcePattern;
  private final List<TregexPattern> targetPatterns = new ArrayList<TregexPattern>();
  private final String specific; // to hold the specific prep or conjunction associated with the grammatical relation

  // TODO document constructor
  // TODO change to put specificString earlier, and then use String... for targetPatterns
  private GrammaticalRelation(Language language,
                             String shortName,
                             String longName,
                             Class<? extends GrammaticalRelationAnnotation> annotation,
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

    if (annotation != null) {
      if (GrammaticalRelation.annotationsToRelations.put(annotation, this) != null) {
        throw new IllegalArgumentException("Annotation cannot be associated with more than one relation!");
      }
      if (GrammaticalRelation.relationsToAnnotations.put(this, annotation) != null) {
        throw new IllegalArgumentException("There should only ever be one instance of each relation!");
      }
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

    Map<String, GrammaticalRelation> sToR = stringsToRelations.get(language);
    if (sToR == null) {
      sToR = Generics.newHashMap();
      stringsToRelations.put(language, sToR);
    }
    GrammaticalRelation previous = sToR.put(toString(), this);
    if (previous != null) {
      if (!previous.isFromString() && !isFromString()) {
        throw new IllegalArgumentException("There is already a relation named " + toString() + '!');
      } else {
        /* We get here if we previously just built a fake relation from a string
         * we previously read in from a file.
         */
        // TODO is it worth copying all of the information from this real
        //      relation into the old fake one?
      }
    }
  }

  public GrammaticalRelation(Language language,
                             String shortName,
                             String longName,
                             Class<? extends GrammaticalRelationAnnotation> annotation,
                             GrammaticalRelation parent,
                             String sourcePattern,
                             TregexPatternCompiler tregexCompiler,
                             String[] targetPatterns) {
    this(language, shortName, longName, annotation, parent, sourcePattern, tregexCompiler, targetPatterns, null);
  }

  public GrammaticalRelation(Language language,
                             String shortName,
                             String longName,
                             Class<? extends GrammaticalRelationAnnotation> annotation,
                             GrammaticalRelation parent) {
    this(language, shortName, longName, annotation, parent, null, null, StringUtils.EMPTY_STRING_ARRAY, null);
  }

  public GrammaticalRelation(Language language,
                             String shortName,
                             String longName,
                             Class<? extends GrammaticalRelationAnnotation> annotation,
                             GrammaticalRelation parent,
                             String specificString) {
    this(language, shortName, longName, annotation, parent, null, null, StringUtils.EMPTY_STRING_ARRAY, specificString);
  }

  private void addChild(GrammaticalRelation child) {
    children.add(child);
  }

  /** Given a <code>Tree</code> node <code>t</code>, attempts to
   *  return a list of nodes to which node <code>t</code> has this
   *  grammatical relation.
   *
   *  @param t Target for finding governors of t related by this GR
   *  @param root The root of the Tree
   *  @return Governor nodes to which t bears this GR
   */
  public Collection<Tree> getRelatedNodes(Tree t, Tree root) {
    if (root.value() == null) {
      root.setValue("ROOT");  // todo: cdm: it doesn't seem like this line should be here
    }
    Set<Tree> nodeList = new LinkedHashSet<Tree>();
    for (TregexPattern p : targetPatterns) {    // cdm: I deleted: && nodeList.isEmpty()
      TregexMatcher m = p.matcher(root);
      while (m.findAt(t)) {
        nodeList.add(m.getNode("target"));
        if (DEBUG) {
          System.err.println("found " + this + "(" + t + ", " + m.getNode("target") + ") using pattern " + p);
          for (String nodeName : m.getNodeNames()) {
            if (nodeName.equals("target")) 
              continue;
            System.err.println("  node " + nodeName + ": " + m.getNode(nodeName));
          }
        }
      }
    }
    return nodeList;
  }

  /** Returns <code>true</code> iff the value of <code>Tree</code>
   *  node <code>t</code> matches the <code>sourcePattern</code> for
   *  this <code>GrammaticalRelation</code>, indicating that this
   *  <code>GrammaticalRelation</code> is one that could hold between
   *  <code>Tree</code> node <code>t</code> and some other node.
   */
  public boolean isApplicable(Tree t) {
    // System.err.println("Testing whether " + sourcePattern + " matches " + ((TreeGraphNode) t).toOneLineString());
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
   * <code>GrammaticalRelation</code>.
   *
   * <i>Implementation note:</i> Note that this method must be synced with
   * the equals() and valueOf(String) methods
   */
  @Override
  public final String toString() {
    if (specific == null) {
      return shortName;
    } else {
      return shortName + '_' + specific;
    }
  }

  /**
   * Returns a <code>String</code> representation of this
   * <code>GrammaticalRelation</code> and the hierarchy below
   * it, with one node per line, indented according to level.
   *
   * @return <code>String</code> representation of this
   *         <code>GrammaticalRelation</code>
   */
  public String toPrettyString() {
    StringBuilder buf = new StringBuilder("\n");
    toPrettyString(0, buf);
    return buf.toString();
  }

  /**
   * Returns a <code>String</code> representation of this
   * <code>GrammaticalRelation</code> and the hierarchy below
   * it, with one node per line, indented according to
   * <code>indentLevel</code>.
   *
   * @param indentLevel how many levels to indent (0 for root node)
   *
   */
  private void toPrettyString(int indentLevel, StringBuilder buf) {
    for (int i = 0; i < indentLevel; i++) {
      buf.append("  ");
    }
    buf.append(shortName).append(": ").append(targetPatterns);
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
    return this.language == gr.language &&
             this.shortName.equals(gr.shortName) &&
             (this.specific == gr.specific ||
              (this.specific != null && this.specific.equals(gr.specific)));
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

  public String getSpecific() {
    return specific;
  }

  /**
   * Returns the parent of this <code>GrammaticalRelation</code>.
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
