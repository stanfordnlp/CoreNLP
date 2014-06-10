package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.*;

import java.util.*;

/**
 * Functions to match using a trie map
 * TODO: Have TrieMapMatcher implement a matcher interface
 *
 * @author Angel Chang
 */
public class TrieMapMatcher<K,V> {
  TrieMap<K,V> root;
  TrieMap<K,V> rootWithDelimiter;
  List<K> multimatchDelimiter;

  public TrieMapMatcher(TrieMap<K, V> root) {
    this.root = root;
  }

  public TrieMapMatcher(TrieMap<K, V> root, List<K> multimatchDelimiter) {
    this.root = root;
    this.multimatchDelimiter = multimatchDelimiter;
    if (multimatchDelimiter != null && !multimatchDelimiter.isEmpty()) {
      // Create a new root that always starts with the delimiter
      rootWithDelimiter = new TrieMap<K, V>();
      rootWithDelimiter.putChildTrie(multimatchDelimiter, root);
    } else {
      rootWithDelimiter = root;
    }
  }

  public List<ApproxMatch<K,V>> findClosestMatches(K[] target, int n) {
    return findClosestMatches(Arrays.asList(target), n);
  }

  public List<ApproxMatch<K,V>> findClosestMatches(K[] target, int n, boolean multimatch) {
    return findClosestMatches(Arrays.asList(target), n, multimatch);
  }

  public List<ApproxMatch<K,V>> findClosestMatches(K[] target, MatchCostFunction<K,V> costFunction,
                                                   Double maxCost, int n, boolean multimatch) {
    return findClosestMatches(Arrays.asList(target), costFunction, maxCost, n, multimatch);
  }

  public List<ApproxMatch<K,V>> findClosestMatches(List<K> target, int n) {
    return findClosestMatches(target, TrieMapMatcher.<K,V>defaultCost(), Double.MAX_VALUE, n, false);
  }

  public List<ApproxMatch<K,V>> findClosestMatches(List<K> target, int n, boolean multimatch) {
    return findClosestMatches(target, TrieMapMatcher.<K,V>defaultCost(), Double.MAX_VALUE, n, multimatch);
  }

  public List<ApproxMatch<K,V>> findClosestMatches(List<K> target, MatchCostFunction<K,V> costFunction,
                                                   double maxCost, int n, boolean multimatch) {
    if (root.isEmpty()) return null;
    int extra = 3;
    // Find the closest n options to the key in the trie based on the given cost function for substitution
    // matches[i][j] stores the top n partial matches for i elements from the target
    //   and j elements from the partial matches from trie keys
    MatchQueue<K,V> best = new MatchQueue<K, V>(n, maxCost);
    List<PartialApproxMatch<K,V>>[][] matches = new List[target.size()+1][];
    for (int i = 0; i <= target.size(); i++) {
      for (int j = 0; j <= target.size()+extra; j++) {
        if (j > 0) {
          boolean complete = (i == target.size()) && (j >= target.size());
          // Try to pick best match from trie
          K t = (i > 0 && i <= target.size())? target.get(i-1):null;
          // Look at the top n choices we saved away and pick n new options
          MatchQueue<K,V> queue = (multimatch)? new MultiMatchQueue<K, V>(n, maxCost):new MatchQueue<K, V>(n, maxCost);
          if (i > 0) {
            for (PartialApproxMatch<K,V> pam:matches[i-1][j-1]) {
              if (pam.trie != null) {
                if (pam.trie.children != null) {
                  for (K k:pam.trie.children.keySet()) {
                    addToQueue(queue, best, costFunction, pam, t, k, multimatch, complete);
                  }
                }
              }
            }
          }
          for (PartialApproxMatch<K,V> pam:matches[i][j-1]) {
            if (pam.trie != null) {
              if (pam.trie.children != null) {
                for (K k:pam.trie.children.keySet()) {
                  addToQueue(queue, best, costFunction, pam, null, k, multimatch, complete);
                }
              }
            }
          }
          if (i > 0) {
            for (PartialApproxMatch<K,V> pam:matches[i-1][j]) {
              addToQueue(queue, best, costFunction, pam, t, null, multimatch, complete);
            }
          }
          matches[i][j] = queue.toSortedList();
        } else {
          matches[i] = new List[target.size()+1+extra];
          matches[i][0] = new ArrayList<PartialApproxMatch<K,V>>();
          if (i > 0) {
            K t = (i < target.size())? target.get(i-1):null;
            for (PartialApproxMatch<K,V> pam:matches[i-1][0]) {
              PartialApproxMatch<K,V> npam = pam.withMatch(costFunction, costFunction.cost(t, null), t, null);
              if (npam.cost <= maxCost) {
                matches[i][0].add(npam);
              }
            }
          } else {
            matches[i][0].add(new PartialApproxMatch(0, root));
          }
        }
//        System.out.println("i=" + i + ",j=" + j + "," + matches[i][j]);
      }
    }
    // Get the best matches
    List<ApproxMatch<K,V>> res = new ArrayList<ApproxMatch<K,V>>();
    for (ApproxMatch<K,V> m:best.toSortedList()) {
      res.add(m);
    }
    return res;
  }

  public List<Match<K,V>> findAllMatches(K ... list) {
    return findAllMatches(Arrays.asList(list));
  }

  public List<Match<K,V>> findAllMatches(List<K> list) {
    return findAllMatches(list, 0, list.size());
  }

  public List<Match<K,V>> findAllMatches(List<K> list, int start, int end) {
    List<Match<K,V>> allMatches = new ArrayList<Match<K,V>>();
    updateAllMatches(root, allMatches, new ArrayList<K>(), list, start, end);
    return allMatches;
  }

  public List<Match<K,V>> findNonOverlapping(K ... list) {
    return findNonOverlapping(Arrays.asList(list));
  }

  public List<Match<K,V>> findNonOverlapping(List<K> list) {
    return findNonOverlapping(list, 0, list.size());
  }

  public final static Comparator<Match> MATCH_LENGTH_ENDPOINTS_COMPARATOR = Interval.<Match>lengthEndpointsComparator();

  public List<Match<K,V>> findNonOverlapping(List<K> list, int start, int end) {
    return findNonOverlapping(list, start, end, MATCH_LENGTH_ENDPOINTS_COMPARATOR);
  }

  public List<Match<K,V>> findNonOverlapping(List<K> list, int start, int end, Comparator<? super Match<K,V>> compareFunc) {
    List<Match<K,V>> allMatches = findAllMatches(list, start, end);
    return getNonOverlapping(allMatches, compareFunc);
  }

  public List<Match<K,V>> segment(K ... list) {
    return segment(Arrays.asList(list));
  }

  public List<Match<K,V>> segment(List<K> list) {
    return segment(list, 0, list.size());
  }

  public List<Match<K,V>> segment(List<K> list, int start, int end) {
    return segment(list, start, end, MATCH_LENGTH_ENDPOINTS_COMPARATOR);
  }

  public List<Match<K,V>> segment(List<K> list, int start, int end, Comparator<? super Match<K,V>> compareFunc) {
    List<Match<K,V>> nonOverlapping = findNonOverlapping(list, start, end, compareFunc);
    List<Match<K,V>> segments = new ArrayList<Match<K,V>>(nonOverlapping.size());
    int last = 0;
    for (Match<K,V> match:nonOverlapping) {
      if (match.begin > last) {
        // Create empty match and add to segments
        Match<K,V> empty = new Match<K,V>(list.subList(last, match.begin), null, last, match.begin);
        segments.add(empty);
      }
      segments.add(match);
      last = match.end;
    }
    if (list.size() > last) {
      Match<K,V> empty = new Match<K,V>(list.subList(last, list.size()), null, last, list.size());
      segments.add(empty);
    }
    return segments;
  }

  public List<Match<K,V>> getNonOverlapping(List<Match<K,V>> allMatches) {
    return getNonOverlapping(allMatches, MATCH_LENGTH_ENDPOINTS_COMPARATOR);
  }

  public List<Match<K,V>> getNonOverlapping(List<Match<K,V>> allMatches, Comparator<? super Match<K,V>> compareFunc) {
    if (allMatches.size() > 1) {
      List<Match<K,V>> nonOverlapping = IntervalTree.getNonOverlapping(allMatches, compareFunc);
      Collections.sort(nonOverlapping, HasInterval.ENDPOINTS_COMPARATOR);
      return nonOverlapping;
    } else {
      return allMatches;
    }
  }

  protected void updateAllMatches(TrieMap<K,V> trie, List<Match<K,V>> matches, List<K> matched, List<K> list, int start, int end) {
    for (int i = start; i < end; i++) {
      updateAllMatchesWithStart(trie, matches, matched, list, i, end);
    }
  }
  protected void updateAllMatchesWithStart(TrieMap<K,V> trie, List<Match<K,V>> matches, List<K> matched, List<K> list, int start, int end) {
    if (start > end) return;
    if (trie.children != null && start < end) {
      K key = list.get(start);
      TrieMap<K,V> child = trie.children.get(key);
      if (child != null) {
        List<K> p = new ArrayList<K>(matched.size() + 1);
        p.addAll(matched);
        p.add(key);
        updateAllMatchesWithStart(child, matches, p, list, start + 1, end);
      }
    }
    if (trie.isLeaf()) {
      matches.add(new Match<K,V>(matched, trie.value, start - matched.size(), start));
    }
  }

  public static class PartialApproxMatch<K,V> extends ApproxMatch<K,V> {
    TrieMap<K,V> trie;
    int lastMultimatchedStartIndex = 0;

    public PartialApproxMatch() {}

    public PartialApproxMatch(double cost, TrieMap<K,V> trie) {
      this.trie = trie;
      this.cost = cost;
      this.value = this.trie.value;
    }


    private PartialApproxMatch<K,V> withMatch(MatchCostFunction<K,V> costFunction, double deltaCost, K t, K k) {
      PartialApproxMatch<K,V> res = new PartialApproxMatch<K,V>();
      res.matched = matched;
      if (k != null) {
        if (res.matched == null) {
          res.matched = new ArrayList<K>(1);
        } else {
          res.matched = new ArrayList<K>(matched.size() + 1);
          res.matched.addAll(matched);
        }
        res.matched.add(k);
      }
      res.begin = begin;
      res.end = (t != null)? end + 1: end;
      res.cost = cost + deltaCost;
      res.trie = (k != null)? trie.getChildTrie(k):trie;
      res.value = res.trie.value;
      res.multimatched = multimatched;
      res.multivalues = multivalues;
      res.lastMultimatchedStartIndex = lastMultimatchedStartIndex;
      return res;
    }

    private PartialApproxMatch<K,V> withMatch(MatchCostFunction<K,V> costFunction, double deltaCost,
                                              K t, K k, boolean multimatch, TrieMap<K,V> root) {
      PartialApproxMatch<K,V> res = withMatch(costFunction, deltaCost, t, k);
      if (multimatch && res.matched != null && res.value != null) {
        // Update tracking of matched keys and values for multiple entry matches
        if (res.multivalues == null) {
          res.multivalues = new ArrayList<V>(1);
        } else {
          res.multivalues = new ArrayList<V>(multivalues.size()+1);
          res.multivalues.addAll(multivalues);
        }
        res.multivalues.add(res.value);
        if (res.multimatched == null) {
          res.multimatched = new ArrayList<List<K>>(1);
        } else {
          res.multimatched = new ArrayList<List<K>>(multimatched.size()+1);
          res.multimatched.addAll(multimatched);
        }
        res.multimatched.add(res.matched.subList(lastMultimatchedStartIndex, res.matched.size()));
        res.cost += costFunction.multiMatchDeltaCost(res.multimatched.get(res.multimatched.size()-1), res.value, res.multimatched.size());
        res.lastMultimatchedStartIndex = res.matched.size();
        // Reset current value/key being matched
        res.trie = root;
      }
      return res;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      PartialApproxMatch that = (PartialApproxMatch) o;

      if (lastMultimatchedStartIndex != that.lastMultimatchedStartIndex) return false;
      if (trie != null ? !trie.equals(that.trie) : that.trie != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (trie != null ? trie.hashCode() : 0);
      result = 31 * result + lastMultimatchedStartIndex;
      return result;
    }
  }

  private static class MatchQueue<K,V> {
    private final BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>> queue;
    protected final int maxSize;
    protected final double maxCost;

    public final Function<PartialApproxMatch<K,V>, Double> MATCH_COST_FUNCTION = new Function<PartialApproxMatch<K,V>, Double>() {
      @Override
      public Double apply(PartialApproxMatch<K,V> in) {
        return in.cost;
      }
    };

    public MatchQueue(int maxSize, double maxCost) {
      this.maxSize = maxSize;
      this.maxCost = maxCost;
      this.queue = new BoundedCostOrderedMap<Match<K, V>, PartialApproxMatch<K, V>>(MATCH_COST_FUNCTION, maxSize, maxCost);
    }

    public void add(PartialApproxMatch<K,V> pam) {
      Match<K,V> m = new MultiMatch<K,V>(pam.matched, pam.value, pam.begin, pam.end, pam.multimatched, pam.multivalues);
      queue.put(m, pam);
    }

    public double topCost() { return queue.topCost(); }

    public int size() { return queue.size(); }

    public List<PartialApproxMatch<K,V>> toSortedList() {
      return queue.valuesList();
    }
  }

  private static class MultiMatchQueue<K,V> extends MatchQueue<K,V> {
    private final Map<Integer, BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>>> multimatchQueues;

    public MultiMatchQueue(int maxSize, double maxCost) {
      super(maxSize, maxCost);
      this.multimatchQueues = new HashMap<Integer, BoundedCostOrderedMap<Match<K, V>, PartialApproxMatch<K, V>>>();
    }

    public void add(PartialApproxMatch<K,V> pam) {
      Match<K,V> m = new MultiMatch<K,V>(
              pam.matched, pam.value, pam.begin, pam.end, pam.multimatched, pam.multivalues);
      Integer key = (pam.multimatched != null)? pam.multimatched.size():0;
      if (pam.value == null) key = key + 1;
      BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>> mq = multimatchQueues.get(key);
      if (mq == null) {
        multimatchQueues.put(key, mq = new BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>>(
                MATCH_COST_FUNCTION, maxSize, maxCost));
      }
      mq.put(m, pam);
    }

    public double topCost() {
      double cost = Double.MIN_VALUE;
      for (BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>> q:multimatchQueues.values()) {
        if (q.topCost() > cost) cost = q.topCost();
      }
      return cost;
    }

    public int size() {
      int sz = 0;
      for (BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>> q:multimatchQueues.values()) {
        sz += q.size();
      }
      return sz;
    }

    public List<PartialApproxMatch<K,V>> toSortedList() {
      List<PartialApproxMatch<K,V>> all = new ArrayList<PartialApproxMatch<K, V>>(size());
      for (BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>> q:multimatchQueues.values()) {
        all.addAll(q.valuesList());
      }
      Collections.sort(all, TrieMapMatcher.<K,V>partialMatchComparator());
      return all;
    }
  }

  private boolean addToQueue(MatchQueue<K,V> queue,
                             MatchQueue<K,V> best,
                             MatchCostFunction<K,V> costFunction,
                             PartialApproxMatch<K,V> pam, K a, K b,
                             boolean multimatch, boolean complete) {
    double deltaCost = costFunction.cost(a,b);
    double newCost = pam.cost + deltaCost;
    if (newCost > queue.maxCost) return false;
    if (best.size() >= queue.maxSize && newCost > best.topCost()) return false;

    PartialApproxMatch<K,V> npam = pam.withMatch(costFunction, deltaCost, a, b);
    if (!multimatch || npam.trie.children != null) {
      if (!multimatch && complete && npam.value != null) {
        best.add(npam);
      }
      queue.add(npam);
    }

    if (multimatch && npam.value != null) {
      npam = pam.withMatch(costFunction, deltaCost, a, b, multimatch, rootWithDelimiter);
      if (complete && npam.value != null) {
        best.add(npam);
      }
      queue.add(npam);
    }
    return true;
  }

  public static final <K,V> MatchCostFunction<K,V> defaultCost() {
    return ErasureUtils.uncheckedCast(DEFAULT_COST);
  }
  public static final <K,V> Comparator<PartialApproxMatch<K,V>> partialMatchComparator() {
    return ErasureUtils.uncheckedCast(PARTIAL_MATCH_COMPARATOR);
  }
  private static final MatchCostFunction DEFAULT_COST = new ExactMatchCost();

  private static final Comparator<PartialApproxMatch> PARTIAL_MATCH_COMPARATOR = new Comparator<PartialApproxMatch>() {
    @Override
    public int compare(PartialApproxMatch o1, PartialApproxMatch o2) {
      if (o1.cost == o2.cost) {
        return 0;
      } else return (o1.cost > o2.cost)? -1:1;
    }
  };




}
