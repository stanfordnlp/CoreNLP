package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.StringUtils;

import java.io.Serializable;
import java.util.List;

/**
 * Matches a Node (i.e a Token).
 *
 * @author Angel Chang
 */
public abstract class NodePattern<T> implements Serializable{

  public static final NodePattern ANY_NODE = new AnyNodePattern();

  // Flags for string annotations
  public static final int CASE_INSENSITIVE = 0x02;
  public static final int NORMALIZE = 0x04;
  public static final int UNICODE_CASE = 0x40;

  /**
   * Returns true if the input node matches this pattern
   * @param node - node to match
   * @return true if the node matches the pattern, false otherwise
   */
  public abstract boolean match(T node);

  /**
   * Returns result associated with the match
   * @param node node to match
   * @return null if not matched, TRUE if there is a match but no other result associated with the match.
   *         Any other value is treated as the result value of the match.
   */
  public Object matchWithResult(T node) {
    if (match(node)) return Boolean.TRUE;
    else return null;
  }

  /**
   * Matches any node
   * @param <T>
   */
  public static class AnyNodePattern<T> extends NodePattern<T> {
    protected AnyNodePattern() {
    }

    @Override
    public boolean match(T node) {
      return true;
    }

    public String toString() {
      return "*";
    }
  }

  /**
   * Matches a constant value of type T using equals()
   * @param <T>
   */
  public static class EqualsNodePattern<T> extends NodePattern<T> {
    T t;

    public EqualsNodePattern(T t) {
      this.t = t;
    }

    public boolean match(T node)
    {
      return t.equals(node);
    }

    public String toString() {
      return "[" + t + "]";
    }
  }

  /**
   * Given a node pattern p, a node x matches if p does not match x
   * @param <T>
   */
  public static class NegateNodePattern<T> extends NodePattern<T> {
    NodePattern<T> p;

    public NegateNodePattern(NodePattern<T> p) {
      this.p = p;
    }

    @Override
    public boolean match(T node)
    {
      return !p.match(node);
    }

    public String toString() {
      return "!" + p;
    }
  }

  /**
   * Given a list of patterns p1,...,pn, matches if all patterns p1,...,pn matches
   * @param <T>
   */
  public static class ConjNodePattern<T> extends NodePattern<T> {
    List<NodePattern<T>> nodePatterns;

    public ConjNodePattern(List<NodePattern<T>> nodePatterns) {
      this.nodePatterns = nodePatterns;
    }

    @Override
    public boolean match(T node)
    {
      boolean matched = true;
      for (NodePattern<T> p:nodePatterns) {
        if (!p.match(node)) {
          matched = false;
          break;
        }
      }
      return matched;
    }

    public String toString() {
      return StringUtils.join(nodePatterns, " & ");
    }
  }

  /**
   * Given a list of patterns p1,...,pn, matches if one of the patterns p1,...,pn matches
   * @param <T>
   */
  public static class DisjNodePattern<T> extends NodePattern<T> {
    List<NodePattern<T>> nodePatterns;

    public DisjNodePattern(List<NodePattern<T>> nodePatterns) {
      this.nodePatterns = nodePatterns;
    }

    @Override
    public boolean match(T node)
    {
      boolean matched = false;
      for (NodePattern<T> p:nodePatterns) {
        if (p.match(node)) {
          matched = true;
          break;
        }
      }
      return matched;
    }

    public String toString() {
      return StringUtils.join(nodePatterns, " | ");
    }
  }

}
