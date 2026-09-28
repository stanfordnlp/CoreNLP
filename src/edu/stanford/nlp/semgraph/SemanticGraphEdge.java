package edu.stanford.nlp.semgraph;

import java.io.Serializable;
import java.util.Comparator;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.GrammaticalRelation;


/**
 * Represents an edge in the dependency graph. Equal only if source, target, and relation are equal.
 *
 * @author Christopher Cox
 * @author Teg Grenager
 * @see SemanticGraph
 */
public class SemanticGraphEdge 
  implements Comparable<SemanticGraphEdge>, Serializable 
{

  /** If true, {@link #toString()} prints only the relation rather than source, target, and relation. */
  public static boolean printOnlyRelation = false; // a hack for displaying SemanticGraph in JGraph.  Should be redone better.

  /** The relation between the source and target words. */
  private final GrammaticalRelation relation;
  /** A score or weight attached to the edge. */
  private final double weight;

  /** Whether the dependency this edge represents was "extra". */
  private final boolean isExtra;

  /** The source (governor) of this edge. */
  private final IndexedWord source;
  /** The target (dependent) of this edge. */
  private final IndexedWord target;

  /**
   * Creates an edge from {@code source} to {@code target} with the given relation.
   *
   * @param source The source IndexedWord for this edge
   * @param target The target IndexedWord for this edge
   * @param relation The relation between the two words represented by this edge
   * @param weight A score or weight to attach to the edge (not often used)
   * @param isExtra Whether or not the dependency this edge represents was "extra"
   */
  public SemanticGraphEdge(IndexedWord source,
                           IndexedWord target,
                           GrammaticalRelation relation,
                           double weight, boolean isExtra) {
    this.source = source;
    this.target = target;
    this.relation = relation;
    this.weight = weight;
    this.isExtra = isExtra;
  }

  /**
   * Creates a copy of an edge.  The source and target IndexedWords are shared, not copied.
   *
   * @param e The edge to copy
   */
  public SemanticGraphEdge(SemanticGraphEdge e) {
    this(e.getSource(), e.getTarget(), e.getRelation(), e.getWeight(), e.isExtra());
  }

  @Override
  public String toString() {
    if (!printOnlyRelation) {
      return getSource() + " -> " + getTarget() + " (" + getRelation() + ")";
    } else {
      return getRelation().toString();
    }
  }

  /**
   * Returns the relation of this edge.
   *
   * @return The relation
   */
  public GrammaticalRelation getRelation() {
    return relation;
  }

  /**
   * Returns the source (governor) of this edge.
   *
   * @return The source word
   */
  public IndexedWord getSource() {
    return source;
  }

  /**
   * Returns the governor of this edge; the same as {@link #getSource()}.
   *
   * @return The governor word
   */
  public IndexedWord getGovernor() {
    return getSource();
  }

  /**
   * Returns the target (dependent) of this edge.
   *
   * @return The target word
   */
  public IndexedWord getTarget() {
    return target;
  }

  /**
   * Returns the dependent of this edge; the same as {@link #getTarget()}.
   *
   * @return The dependent word
   */
  public IndexedWord getDependent() {
    return getTarget();
  }

  /**
   * Returns the score or weight attached to this edge.
   *
   * @return The weight
   */
  public double getWeight() {
    return weight;
  }
  
  /**
   * Returns whether the dependency this edge represents was "extra".
   *
   * @return Whether this edge is extra
   */
  public boolean isExtra() {
    return isExtra;
  }
  
  /**
   * Compares only the relations of two edges, ignoring source and target.
   *
   * @param e The edge to compare to
   * @return true if the edges are of the same relation type
   */
  public boolean typeEquals(SemanticGraphEdge e) {
    return (this.relation.equals(e.relation));
  }

  private static class SemanticGraphEdgeTargetComparator implements Comparator<SemanticGraphEdge> {

    public int compare(SemanticGraphEdge o1, SemanticGraphEdge o2) {
      int targetVal = o1.getTarget().compareTo(o2.getTarget());
      if (targetVal != 0) {
        return targetVal;
      }
      int sourceVal = o1.getSource().compareTo(o2.getSource());
      if (sourceVal != 0) {
        return sourceVal;
      }
      return o1.getRelation().toString().compareTo(o2.getRelation().toString()); // todo: cdm: surely we shouldn't have to do toString() now?
    }

  }

  private static Comparator<SemanticGraphEdge> targetComparator = new SemanticGraphEdgeTargetComparator();

  /**
   * Returns a comparator which orders edges by target, then source, then the string form of the relation.
   *
   * @return A shared comparator instance
   */
  public static Comparator<SemanticGraphEdge> orderByTargetComparator() {
    return targetComparator;
  }

  /** Compares SemanticGraphEdges.
   * Warning: compares on the sources, targets, and then the STRINGS of the relations.
   * @param other Edge to compare to
   * @return Whether this is smaller, same, or larger
   */
  public int compareTo(SemanticGraphEdge other) {
    int sourceVal = getSource().compareTo(other.getSource());
    if (sourceVal != 0) {
      return sourceVal;
    }
    int targetVal = getTarget().compareTo(other.getTarget());
    if (targetVal !=0 ) {
      return targetVal;
    }
    String thisRelation = getRelation().toString();
    String thatRelation = other.getRelation().toString();
    return thisRelation.compareTo(thatRelation);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SemanticGraphEdge)) return false;

    final SemanticGraphEdge semanticGraphEdge = (SemanticGraphEdge) o;

    if (relation != null) {
      boolean retFlag = relation.equals(semanticGraphEdge.relation);
      boolean govMatch = getGovernor().equals(semanticGraphEdge.getGovernor());
      boolean depMatch = getDependent().equals(semanticGraphEdge.getDependent());
      boolean matched = retFlag && govMatch && depMatch;
      return matched;
    }

 //   if (relation != null ? !relation.equals(semanticGraphEdge.relation) : semanticGraphEdge.relation != null) return false;
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    int result;
    result = (relation != null ? relation.hashCode() : 0);
    result = 29 * result + (getSource() != null ? getSource().hashCode() : 0);
    result = 29 * result + (getTarget() != null ? getTarget().hashCode() : 0);
    return result;
  }

  private static final long serialVersionUID = 2L;

}
