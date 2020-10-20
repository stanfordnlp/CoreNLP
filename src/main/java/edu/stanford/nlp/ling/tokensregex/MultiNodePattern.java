package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.Interval;

import java.util.Collection;
import java.util.List;

/**
 * Matches potentially multiple node (i.e does match across multiple tokens)
 *
 * @author Angel Chang
 */
public abstract class MultiNodePattern<T> {
  int minNodes = 1;
  int maxNodes = -1;   // Set the max number of nodes this pattern can match 
  boolean greedyMatch = true;

  /**
   * Tries to match sequence of nodes starting of start
   * Returns intervals (token offsets) of when the nodes matches
   * @param nodes
   * @param start
   */
  protected abstract Collection<Interval<Integer>> match(List<? extends T> nodes, int start);

  public int getMinNodes() {
    return minNodes;
  }

  public void setMinNodes(int minNodes) {
    this.minNodes = minNodes;
  }

  public int getMaxNodes() {
    return maxNodes;
  }

  public void setMaxNodes(int maxNodes) {
    this.maxNodes = maxNodes;
  }

  public boolean isGreedyMatch() {
    return greedyMatch;
  }

  public void setGreedyMatch(boolean greedyMatch) {
    this.greedyMatch = greedyMatch;
  }

  protected static class IntersectMultiNodePattern<T> extends MultiNodePattern<T> {
    List<MultiNodePattern<T>> nodePatterns;

    protected IntersectMultiNodePattern(List<MultiNodePattern<T>> nodePatterns) {
      this.nodePatterns = nodePatterns;
    }

    protected Collection<Interval<Integer>> match(List<? extends T> nodes, int start)
    {
      Collection<Interval<Integer>> matched = null;
      for (MultiNodePattern<T> p:nodePatterns) {
        Collection<Interval<Integer>> m = p.match(nodes, start);
        if (m == null || m.size() == 0) {
          return null;
        }
        if (matched == null) {
          matched = m;
        } else {
          matched.retainAll(m);
          if (m.size() == 0) {
            return null;
          }
        }
      }
      return matched;
    }
  }

  protected static class UnionMultiNodePattern<T> extends MultiNodePattern<T> {
    List<MultiNodePattern<T>> nodePatterns;

    protected UnionMultiNodePattern(List<MultiNodePattern<T>> nodePatterns) {
      this.nodePatterns = nodePatterns;
    }

    protected Collection<Interval<Integer>> match(List<? extends T> nodes, int start)
    {
      Collection<Interval<Integer>> matched = null;
      for (MultiNodePattern<T> p:nodePatterns) {
        Collection<Interval<Integer>> m = p.match(nodes, start);
        if (m != null && m.size() > 0) {
          if (matched == null) {
            matched = m;
          } else {
            for (Interval<Integer> i:m) {
              if (!matched.contains(i)) {
                matched.add(i);
              }
            }
          }
        }
      }
      return matched;
    }
  }

}
