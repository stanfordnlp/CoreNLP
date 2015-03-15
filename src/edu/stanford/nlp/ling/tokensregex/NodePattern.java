package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.StringUtils;

import java.util.List;

/**
 * Matches a Node (i.e a Token)
 *
 * @author Angel Chang
 */
public abstract class NodePattern<T> {
  public final static NodePattern ANY_NODE = new AnyNodePattern();

  public abstract boolean match(T node);
  public Object matchWithResult(T node) {
    if (match(node)) return Boolean.TRUE;
    else return null;
  }

  public static class AnyNodePattern<T> extends NodePattern<T> {
    protected AnyNodePattern() {
    }

    public boolean match(T node) {
      return true;
    }
    
    public String toString() {
      return "*";
    }
  }

  public static class NegateNodePattern<T> extends NodePattern<T> {
    NodePattern<T> p;

    public NegateNodePattern(NodePattern<T> p) {
      this.p = p;
    }

    public boolean match(T node)
    {
      return !p.match(node);
    }

    public String toString() {
      return "!" + p;
    }
  }

  public static class ConjNodePattern<T> extends NodePattern<T> {
    List<NodePattern<T>> nodePatterns;

    public ConjNodePattern(List<NodePattern<T>> nodePatterns) {
      this.nodePatterns = nodePatterns;
    }

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

  public static class DisjNodePattern<T> extends NodePattern<T> {
    List<NodePattern<T>> nodePatterns;

    public DisjNodePattern(List<NodePattern<T>> nodePatterns) {
      this.nodePatterns = nodePatterns;
    }

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
