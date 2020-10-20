package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * The {@code TrieMapMatcher} provides functions to match against a trie.
 * It can be used to:
 * <ul>
 * <li> Find matches in a document (findAllMatches and findNonOverlapping) </li>
 * <li> Find approximate matches in a document (findClosestMatches) </li>
 * <li> Segment a sequence based on entries in the trie (segment) </li>
 * </ul>
 *
 * TODO: Have TrieMapMatcher implement a matcher interface
 *
 * @author Angel Chang
 */
public class TrieMapMatcher<K,V> {

  private final TrieMap<K,V> root;
  private final TrieMap<K,V> rootWithDelimiter;
  private List<K> multimatchDelimiter;

  public TrieMapMatcher(TrieMap<K, V> root) {
    this.root = root;
    this.rootWithDelimiter = root;
  }

  public TrieMapMatcher(TrieMap<K, V> root, List<K> multimatchDelimiter) {
    this.root = root;
    this.multimatchDelimiter = multimatchDelimiter;
    if (multimatchDelimiter != null && !multimatchDelimiter.isEmpty()) {
      // Create a new root that always starts with the delimiter
      rootWithDelimiter = new TrieMap<>();
      rootWithDelimiter.putChildTrie(multimatchDelimiter, root);
    } else {
      rootWithDelimiter = root;
    }
  }

  /**
   * Given a target sequence, returns the n closes matches (or sequences of matches) from the trie.
   * The cost function used is a exact match cost function (exact match has cost 0, otherwise, cost is 1)
   * @param target Target sequence to match
   * @param n Number of matches to return. The actual number of matches may be less.
   * @return List of approximate matches
   */
  public List<ApproxMatch<K,V>> findClosestMatches(K[] target, int n) {
    return findClosestMatches(Arrays.asList(target), n);
  }

  /**
   * Given a target sequence, returns the n closes matches (or sequences of matches) from the trie.
   * The cost function used is a exact match cost function (exact match has cost 0, otherwise, cost is 1)
   * @param target Target sequence to match
   * @param n Number of matches to return. The actual number of matches may be less.
   * @param multimatch If true, attempt to return matches with sequences of elements from the trie.
   *                   Otherwise, only each match will contain one element from the trie.
   * @param keepAlignments If true, alignment information is returned
   * @return List of approximate matches
   */
  public List<ApproxMatch<K,V>> findClosestMatches(K[] target, int n, boolean multimatch, boolean keepAlignments) {
    return findClosestMatches(Arrays.asList(target), n, multimatch, keepAlignments);
  }

  /**
   * Given a target sequence, returns the n closes matches (or sequences of matches) from the trie
   *  based on the cost function (lower cost mean better match).
   * @param target Target sequence to match
   * @param costFunction Cost function to use
   * @param maxCost Matches with a cost higher than this are discarded
   * @param n Number of matches to return. The actual number of matches may be less.
   * @param multimatch If true, attempt to return matches with sequences of elements from the trie.
   *                   Otherwise, only each match will contain one element from the trie.
   * @param keepAlignments If true, alignment information is returned
   * @return List of approximate matches
   */
  public List<ApproxMatch<K,V>> findClosestMatches(K[] target, MatchCostFunction<K,V> costFunction,
                                                   Double maxCost, int n, boolean multimatch, boolean keepAlignments) {
    return findClosestMatches(Arrays.asList(target), costFunction, maxCost, n, multimatch, keepAlignments);
  }

  /**
   * Given a target sequence, returns the n closes matches (or sequences of matches) from the trie.
   * The cost function used is a exact match cost function (exact match has cost 0, otherwise, cost is 1)
   * @param target Target sequence to match
   * @param n Number of matches to return. The actual number of matches may be less.
   * @return List of approximate matches
   */
  public List<ApproxMatch<K,V>> findClosestMatches(List<K> target, int n) {
    return findClosestMatches(target, TrieMapMatcher.<K,V>defaultCost(), Double.MAX_VALUE, n, false, false);
  }

  /**
   * Given a target sequence, returns the n closes matches (or sequences of matches) from the trie.
   * The cost function used is a exact match cost function (exact match has cost 0, otherwise, cost is 1)
   * @param target Target sequence to match
   * @param n Number of matches to return. The actual number of matches may be less.
   * @param multimatch If true, attempt to return matches with sequences of elements from the trie.
   *                   Otherwise, only each match will contain one element from the trie.
   * @param keepAlignments If true, alignment information is returned
   * @return List of approximate matches
   */
  public List<ApproxMatch<K,V>> findClosestMatches(List<K> target, int n, boolean multimatch, boolean keepAlignments) {
    return findClosestMatches(target, TrieMapMatcher.<K,V>defaultCost(), Double.MAX_VALUE, n, multimatch, keepAlignments);
  }

  /**
   * Given a target sequence, returns the n closes matches (or sequences of matches) from the trie
   *  based on the cost function (lower cost mean better match).
   * @param target Target sequence to match
   * @param costFunction Cost function to use
   * @param maxCost Matches with a cost higher than this are discarded
   * @param n Number of matches to return. The actual number of matches may be less.
   * @param multimatch If true, attempt to return matches with sequences of elements from the trie.
   *                   Otherwise, only each match will contain one element from the trie.
   * @param keepAlignments If true, alignment information is returned
   * @return List of approximate matches
   */
  public List<ApproxMatch<K,V>> findClosestMatches(List<K> target, MatchCostFunction<K,V> costFunction,
                                                   double maxCost, int n, boolean multimatch, boolean keepAlignments) {
    if (root.isEmpty()) return null;
    int extra = 3;
    // Find the closest n options to the key in the trie based on the given cost function for substitution
    // matches[i][j] stores the top n partial matches for i elements from the target
    //   and j elements from the partial matches from trie keys

    // At any time, we only keep track of the last two rows
    // (prevMatches (matches[i-1][j]), curMatches (matches[i][j]) that we are working on
    MatchQueue<K,V> best = new MatchQueue<>(n, maxCost);
    List<PartialApproxMatch<K,V>>[] prevMatches = null;
    for (int i = 0; i <= target.size(); i++) {
      List<PartialApproxMatch<K, V>>[] curMatches = new List[target.size() + 1 + extra];
      for (int j = 0; j <= target.size()+extra; j++) {
        if (j > 0) {
          boolean complete = (i == target.size());
          // Try to pick best match from trie
          K t = (i > 0 && i <= target.size())? target.get(i-1):null;
          // Look at the top n choices we saved away and pick n new options
          MatchQueue<K,V> queue = (multimatch)? new MultiMatchQueue<>(n, maxCost): new MatchQueue<>(n, maxCost);
          if (i > 0) {
            for (PartialApproxMatch<K,V> pam:prevMatches[j-1]) {
              if (pam.trie != null) {
                if (pam.trie.children != null) {
                  for (K k:pam.trie.children.keySet()) {
                    addToQueue(queue, best, costFunction, pam, t, k, multimatch, complete);
                  }
                }
              }
            }
          }
          for (PartialApproxMatch<K,V> pam:curMatches[j-1]) {
            if (pam.trie != null) {
              if (pam.trie.children != null) {
                for (K k:pam.trie.children.keySet()) {
                  addToQueue(queue, best, costFunction, pam, null, k, multimatch, complete);
                }
              }
            }
          }
          if (i > 0) {
            for (PartialApproxMatch<K,V> pam:prevMatches[j]) {
              addToQueue(queue, best, costFunction, pam, t, null, multimatch, complete);
            }
          }
          curMatches[j] = queue.toSortedList();
        } else {
          curMatches[0] = new ArrayList<>();
          if (i > 0) {
            K t = (i < target.size())? target.get(i-1):null;
            for (PartialApproxMatch<K,V> pam:prevMatches[0]) {
              PartialApproxMatch<K,V> npam = pam.withMatch(costFunction, costFunction.cost(t, null, pam.getMatchedLength()), t, null);
              if (npam.cost <= maxCost) {
                curMatches[0].add(npam);
              }
            }
          } else {
            curMatches[0].add(new PartialApproxMatch<>(0, root, keepAlignments ? target.size() : 0));
          }
        }
//        System.out.println("i=" + i + ",j=" + j + "," + matches[i][j]);
      }
      prevMatches = curMatches;
    }
    // Get the best matches
    List<ApproxMatch<K,V>> res = new ArrayList<>();
    for (PartialApproxMatch<K,V> m : best.toSortedList()) {
      res.add(m.toApproxMatch());
    }
    return res;
  }

  /**
   * Given a sequence to search through (e.g. piece of text would be a sequence of words),
   * finds all matching sub-sequences that matches entries in the trie.
   *
   * @param list Sequence to search through
   * @return List of matches
   */
  @SafeVarargs
  public final List<Match<K,V>> findAllMatches(K... list) {
    return findAllMatches(Arrays.asList(list));
  }

  /**
   * Given a sequence to search through (e.g. piece of text would be a sequence of words),
   * finds all matching sub-sequences that matches entries in the trie.
   *
   * @param list Sequence to search through
   * @return List of matches
   */
  public List<Match<K,V>> findAllMatches(List<K> list) {
    return findAllMatches(list, 0, list.size());
  }

  /**
   * Given a sequence to search through (e.g. piece of text would be a sequence of words),
   * finds all matching sub-sequences that matches entries in the trie.
   *
   * @param list Sequence to search through
   * @param start start index to start search at
   * @param end end index (exclusive) to end search at
   * @return List of matches
   */
  public List<Match<K,V>> findAllMatches(List<K> list, int start, int end) {
    List<Match<K,V>> allMatches = new ArrayList<>();
    updateAllMatches(root, allMatches, new ArrayList<>(), list, start, end);
    return allMatches;
  }

  /**
   * Given a sequence to search through (e.g. piece of text would be a sequence of words),
   * finds all non-overlapping matching sub-sequences that matches entries in the trie.
   * Sub-sequences that are longer are preferred, then sub-sequences that starts earlier.
   *
   * @param list Sequence to search through
   * @return List of matches sorted by start position
   */
  @SafeVarargs
  public final List<Match<K,V>> findNonOverlapping(K... list) {
    return findNonOverlapping(Arrays.asList(list));
  }

  /**
   * Given a sequence to search through (e.g. piece of text would be a sequence of words),
   * finds all non-overlapping matching sub-sequences that matches entries in the trie.
   * Sub-sequences that are longer are preferred, then sub-sequences that starts earlier.
   * @param list Sequence to search through
   * @return List of matches sorted by start position
   */
  public List<Match<K,V>> findNonOverlapping(List<K> list) {
    return findNonOverlapping(list, 0, list.size());
  }

  public static final Comparator<Match> MATCH_LENGTH_ENDPOINTS_COMPARATOR = Interval.lengthEndpointsComparator();
  public static final ToDoubleFunction<Match> MATCH_LENGTH_SCORER = Interval.lengthScorer();

  /**
   * Given a sequence to search through (e.g. piece of text would be a sequence of words),
   * finds all non-overlapping matching sub-sequences that matches entries in the trie.
   * Sub-sequences that are longer are preferred, then sub-sequences that starts earlier.
   *
   * @param list Sequence to search through
   * @param start start index to start search at
   * @param end end index (exclusive) to end search at
   * @return List of matches sorted by start position
   */
  public List<Match<K,V>> findNonOverlapping(List<K> list, int start, int end) {
    return findNonOverlapping(list, start, end, MATCH_LENGTH_ENDPOINTS_COMPARATOR);
  }

  /**
   * Given a sequence to search through (e.g. piece of text would be a sequence of words),
   * finds all non-overlapping matching sub-sequences that matches entries in the trie.
   *
   * @param list Sequence to search through
   * @param start start index to start search at
   * @param end end index (exclusive) to end search at
   * @param compareFunc Comparison function to use for evaluating which overlapping sub-sequence to keep.
   *                    Earlier sub-sequences based on the comparison function are favored.
   * @return List of matches sorted by start position
   */
  public List<Match<K,V>> findNonOverlapping(List<K> list, int start, int end, Comparator<? super Match<K,V>> compareFunc) {
    List<Match<K,V>> allMatches = findAllMatches(list, start, end);
    return getNonOverlapping(allMatches, compareFunc);
  }

  /**
   * Given a sequence to search through (e.g. piece of text would be a sequence of words),
   * finds all non-overlapping matching sub-sequences that matches entries in the trie while attempting to maximize the scoreFunc.
   *
   * @param list Sequence to search through
   * @param start start index to start search at
   * @param end end index (exclusive) to end search at
   * @param scoreFunc Scoring function indicating how good the match is
   * @return List of matches sorted by start position
   */
  public List<Match<K,V>> findNonOverlapping(List<K> list, int start, int end, ToDoubleFunction<? super Match<K, V>> scoreFunc) {
    List<Match<K,V>> allMatches = findAllMatches(list, start, end);
    return getNonOverlapping(allMatches, scoreFunc);
  }

  /**
   * Segment a sequence into sequence of sub-sequences by attempting to find the longest non-overlapping
   * sub-sequences.  Non-matched parts will be included as a match with a null value.
   *
   * @param list Sequence to search through
   * @return List of segments (as matches) sorted by start position
   */
  @SafeVarargs
  public final List<Match<K,V>> segment(K... list) {
    return segment(Arrays.asList(list));
  }

  /**
   * Segment a sequence into sequence of sub-sequences by attempting to find the longest non-overlapping
   *  sub-sequences.  Non-matched parts will be included as a match with a null value.
   *
   * @param list Sequence to search through
   * @return List of segments (as matches) sorted by start position
   */
  public List<Match<K,V>> segment(List<K> list) {
    return segment(list, 0, list.size());
  }

  /**
   * Segment a sequence into sequence of sub-sequences by attempting to find the longest non-overlapping
   *  sub-sequences.  Non-matched parts will be included as a match with a null value.
   * @param list Sequence to search through
   * @param start start index to start search at
   * @param end end index (exclusive) to end search at
   * @return List of segments (as matches) sorted by start position
   */
  public List<Match<K,V>> segment(List<K> list, int start, int end) {
    return segment(list, start, end, MATCH_LENGTH_SCORER);
  }

  /**
   * Segment a sequence into sequence of sub-sequences by attempting to find the non-overlapping
   *  sub-sequences that comes earlier using the compareFunc.
   * Non-matched parts will be included as a match with a null value.
   *
   * @param list Sequence to search through
   * @param start start index to start search at
   * @param end end index (exclusive) to end search at
   * @param compareFunc Comparison function to use for evaluating which overlapping sub-sequence to keep.
   *                    Earlier sub-sequences based on the comparison function are favored.
   * @return List of segments (as matches) sorted by start position
   */
  public List<Match<K,V>> segment(List<K> list, int start, int end, Comparator<? super Match<K,V>> compareFunc) {
    List<Match<K,V>> nonOverlapping = findNonOverlapping(list, start, end, compareFunc);
    List<Match<K,V>> segments = new ArrayList<>(nonOverlapping.size());
    int last = 0;
    for (Match<K,V> match:nonOverlapping) {
      if (match.begin > last) {
        // Create empty match and add to segments
        Match<K,V> empty = new Match<>(list.subList(last, match.begin), null, last, match.begin);
        segments.add(empty);
      }
      segments.add(match);
      last = match.end;
    }
    if (list.size() > last) {
      Match<K,V> empty = new Match<>(list.subList(last, list.size()), null, last, list.size());
      segments.add(empty);
    }
    return segments;
  }

  /**
   * Segment a sequence into sequence of sub-sequences by attempting to maximize the total score
   * Non-matched parts will be included as a match with a null value.
   *
   * @param list Sequence to search through
   * @param start start index to start search at
   * @param end end index (exclusive) to end search at
   * @param scoreFunc Scoring function indicating how good the match is
   * @return List of segments (as matches) sorted by start position
   */
  public List<Match<K,V>> segment(List<K> list, int start, int end, ToDoubleFunction<? super Match<K, V>> scoreFunc) {
    List<Match<K,V>> nonOverlapping = findNonOverlapping(list, start, end, scoreFunc);
    List<Match<K,V>> segments = new ArrayList<>(nonOverlapping.size());
    int last = 0;
    for (Match<K,V> match:nonOverlapping) {
      if (match.begin > last) {
        // Create empty match and add to segments
        Match<K,V> empty = new Match<>(list.subList(last, match.begin), null, last, match.begin);
        segments.add(empty);
      }
      segments.add(match);
      last = match.end;
    }
    if (list.size() > last) {
      Match<K,V> empty = new Match<>(list.subList(last, list.size()), null, last, list.size());
      segments.add(empty);
    }
    return segments;
  }

  public List<Match<K,V>> segment(List<K> list, ToDoubleFunction<? super Match<K, V>> scoreFunc) {
    return segment(list, 0, list.size(), scoreFunc);
  }

  /**
   * Given a list of matches, returns all non-overlapping matches.
   * Matches that are longer are preferred, then matches that starts earlier.
   *
   * @param allMatches List of matches
   * @return List of matches sorted by start position
   */
  public List<Match<K,V>> getNonOverlapping(List<Match<K,V>> allMatches) {
    return getNonOverlapping(allMatches, MATCH_LENGTH_ENDPOINTS_COMPARATOR);
  }

  /**
   * Given a list of matches, returns all non-overlapping matches.
   *
   * @param allMatches List of matches
   * @param compareFunc Comparison function to use for evaluating which overlapping sub-sequence to keep.
   *                    Earlier sub-sequences based on the comparison function are favored.
   * @return List of matches sorted by start position
   */
  public List<Match<K,V>> getNonOverlapping(List<Match<K,V>> allMatches, Comparator<? super Match<K,V>> compareFunc) {
    if (allMatches.size() > 1) {
      List<Match<K,V>> nonOverlapping = IntervalTree.getNonOverlapping(allMatches, compareFunc);
      nonOverlapping.sort(HasInterval.ENDPOINTS_COMPARATOR);
      return nonOverlapping;
    } else {
      return allMatches;
    }
  }

  public List<Match<K,V>> getNonOverlapping(List<Match<K,V>> allMatches, ToDoubleFunction<? super Match<K, V>> scoreFunc) {
    return IntervalTree.getNonOverlappingMaxScore(allMatches, scoreFunc);
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
        List<K> p = new ArrayList<>(matched.size() + 1);
        p.addAll(matched);
        p.add(key);
        updateAllMatchesWithStart(child, matches, p, list, start + 1, end);
      }
    }
    if (trie.isLeaf()) {
      matches.add(new Match<>(matched, trie.value, start - matched.size(), start));
    }
  }

  // Helper class for keeping track of partial matches with TrieMatcher
  private static class PartialApproxMatch<K,V> extends ApproxMatch<K,V> {
    TrieMap<K,V> trie;
    int lastMultimatchedMatchedStartIndex = 0;
    int lastMultimatchedOriginalStartIndex = 0;

    private PartialApproxMatch() {}

    private PartialApproxMatch(double cost, TrieMap<K,V> trie, int alignmentLength) {
      this.trie = trie;
      this.cost = cost;
      this.value = (trie != null)? this.trie.value:null;
      if (alignmentLength > 0) {
        this.alignments = new Interval[alignmentLength];
      }
    }

    private PartialApproxMatch<K,V> withMatch(MatchCostFunction<K,V> costFunction, double deltaCost, K t, K k) {
      PartialApproxMatch<K,V> res = new PartialApproxMatch<>();
      res.matched = matched;
      if (k != null) {
        if (res.matched == null) {
          res.matched = new ArrayList<>(1);
        } else {
          res.matched = new ArrayList<>(matched.size() + 1);
          res.matched.addAll(matched);
        }
        res.matched.add(k);
      }
      res.begin = begin;
      res.end = (t != null)? end + 1: end;
      res.cost = cost + deltaCost;
      res.trie = (k != null)? trie.getChildTrie(k):trie;
      res.value = (res.trie != null)? res.trie.value:null;
      res.multimatches = multimatches;
      res.lastMultimatchedMatchedStartIndex = lastMultimatchedMatchedStartIndex;
      res.lastMultimatchedOriginalStartIndex = lastMultimatchedOriginalStartIndex;
      if (res.lastMultimatchedOriginalStartIndex == end  && k == null && t != null) {
        res.lastMultimatchedOriginalStartIndex++;
      }
      // Update alignments
      if (alignments != null) {
        res.alignments = new Interval[alignments.length];
        System.arraycopy(alignments, 0, res.alignments, 0, alignments.length);
        if (k != null && res.end > 0) {
          int p = res.end-1;
          if (res.alignments[p] == null) {
            res.alignments[p] = Interval.toInterval(res.matched.size()-1, res.matched.size());
          } else {
            res.alignments[p] = Interval.toInterval(res.alignments[p].getBegin(), res.alignments[p].getEnd() + 1);
          }
        }
      }
      return res;
    }

    private ApproxMatch<K,V> toApproxMatch() {
      // Makes a copy of this partial approx match that can be returned to the caller
      return new ApproxMatch<>(matched, value, begin, end, multimatches, cost, alignments);
    }

    private PartialApproxMatch<K,V> withMatch(MatchCostFunction<K,V> costFunction, double deltaCost,
                                              K t, K k, boolean multimatch, TrieMap<K,V> root) {
      PartialApproxMatch<K,V> res = withMatch(costFunction, deltaCost, t, k);
      if (multimatch && res.matched != null && res.value != null) {
        // Update tracking of matched keys and values for multiple entry matches
        if (res.multimatches == null) {
          res.multimatches = new ArrayList<>(1);
        } else {
          res.multimatches = new ArrayList<>(multimatches.size() + 1);
          res.multimatches.addAll(multimatches);
        }
        List<K> newlyMatched = res.matched.subList(lastMultimatchedMatchedStartIndex, res.matched.size());
        res.multimatches.add(new Match<>(
                newlyMatched,
                res.value,
                lastMultimatchedOriginalStartIndex, res.end
        ));
        res.cost += costFunction.multiMatchDeltaCost(newlyMatched, res.value, multimatches, res.multimatches);
        res.lastMultimatchedMatchedStartIndex = res.matched.size();
        res.lastMultimatchedOriginalStartIndex = res.end;
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

      if (lastMultimatchedMatchedStartIndex != that.lastMultimatchedMatchedStartIndex) return false;
      if (lastMultimatchedOriginalStartIndex != that.lastMultimatchedOriginalStartIndex) return false;
      if (trie != null ? !trie.equals(that.trie) : that.trie != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + lastMultimatchedMatchedStartIndex;
      result = 31 * result + lastMultimatchedOriginalStartIndex;
      return result;
    }
  }

  private static class MatchQueue<K,V> {
    private final BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>> queue;
    protected final int maxSize;
    protected final double maxCost;

    public final ToDoubleFunction<PartialApproxMatch<K,V>> MATCH_COST_FUNCTION = in -> in.cost;

    public MatchQueue(int maxSize, double maxCost) {
      this.maxSize = maxSize;
      this.maxCost = maxCost;
      this.queue = new BoundedCostOrderedMap<>(MATCH_COST_FUNCTION, maxSize, maxCost);
    }

    public void add(PartialApproxMatch<K,V> pam) {
      List<Match<K,V>> multiMatchesWithoutOffsets = null;
      if (pam.multimatches != null) {
        multiMatchesWithoutOffsets = new ArrayList<>(pam.multimatches.size());
        for (Match<K,V> m:pam.multimatches) {
          multiMatchesWithoutOffsets.add(new Match<>(m.matched, m.value, 0, 0));
        }
      }
      Match<K,V> m = new MultiMatch<>(pam.matched, pam.value, pam.begin, pam.end, multiMatchesWithoutOffsets);
      queue.put(m, pam);
    }

    public double topCost() { return queue.topCost(); }

    public int size() { return queue.size(); }

    public boolean isEmpty() { return queue.isEmpty(); }

    public List<PartialApproxMatch<K,V>> toSortedList() {
      List<PartialApproxMatch<K,V>> res = queue.valuesList();
      res.sort(TrieMapMatcher.<K, V>partialMatchComparator());
      return res;
    }
  }

  private static class MultiMatchQueue<K,V> extends MatchQueue<K,V> {
    private final Map<Integer, BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>>> multimatchQueues;

    public MultiMatchQueue(int maxSize, double maxCost) {
      super(maxSize, maxCost);
      this.multimatchQueues = new HashMap<>();
    }

    public void add(PartialApproxMatch<K,V> pam) {
      Match<K,V> m = new MultiMatch<>(
              pam.matched, pam.value, pam.begin, pam.end, pam.multimatches);
      Integer key = (pam.multimatches != null)? pam.multimatches.size():0;
      if (pam.value == null) key = key + 1;
      BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>> mq = multimatchQueues.get(key);
      if (mq == null) {
        multimatchQueues.put(key, mq = new BoundedCostOrderedMap<>(
                MATCH_COST_FUNCTION, maxSize, maxCost));
      }
      mq.put(m, pam);
    }

    @Override
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
      List<PartialApproxMatch<K,V>> all = new ArrayList<>(size());
      for (BoundedCostOrderedMap<Match<K,V>, PartialApproxMatch<K,V>> q:multimatchQueues.values()) {
        all.addAll(q.valuesList());
      }
      all.sort(TrieMapMatcher.<K, V>partialMatchComparator());
      return all;
    }
  }

  private boolean addToQueue(MatchQueue<K,V> queue,
                             MatchQueue<K,V> best,
                             MatchCostFunction<K,V> costFunction,
                             PartialApproxMatch<K,V> pam, K a, K b,
                             boolean multimatch, boolean complete) {
    double deltaCost = costFunction.cost(a,b,pam.getMatchedLength());
    double newCost = pam.cost + deltaCost;
    if (queue.maxCost != Double.MAX_VALUE && newCost > queue.maxCost) return false;
    if (best.size() >= queue.maxSize && newCost > best.topCost()) return false;

    PartialApproxMatch<K,V> npam = pam.withMatch(costFunction, deltaCost, a, b);
    if (!multimatch || (npam.trie != null && npam.trie.children != null)) {
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

  public static <K,V> MatchCostFunction<K,V> defaultCost() {
    return ErasureUtils.uncheckedCast(DEFAULT_COST);
  }


  public static <K,V> Comparator<PartialApproxMatch<K,V>> partialMatchComparator() {
    return ErasureUtils.uncheckedCast(PARTIAL_MATCH_COMPARATOR);
  }
  private static final MatchCostFunction DEFAULT_COST = new ExactMatchCost();

  private static final Comparator<PartialApproxMatch> PARTIAL_MATCH_COMPARATOR = (o1, o2) -> {
    if (o1.cost == o2.cost) {
      if (o1.matched.size() == o2.matched.size()) {
        int m1 = (o1.multimatches != null)? o1.multimatches.size():0;
        int m2 = (o2.multimatches != null)? o2.multimatches.size():0;
        if (m1 == m2) {
          if (o1.begin == o2.begin) {
            if (o1.end == o2.end) {
              for (int i = 0; i < o1.matched.size(); i++) {
                Object x1 = o1.matched.get(i);
                Object x2 = o2.matched.get(i);
                if (x1 != null && x2 != null) {
                  if (x1 instanceof Comparable) {
                    int comp = ((Comparable) x1).compareTo(x2);
                    if (comp != 0) return comp;
                  }
                }
              }
              if (o1.multimatches != null && o2.multimatches != null) {
                for (int i = 0; i < o1.multimatches.size(); i++) {
                  Match mm1 = (Match) o1.multimatches.get(i);
                  Match mm2 = (Match) o2.multimatches.get(i);
                  return mm1.getInterval().compareTo(mm2.getInterval());
                }
              }
              return 0;
            }
            return (o1.end < o2.end)? -1:1;
          } else return (o1.begin < o2.begin)? -1:1;
        } else return (m1 < m2)? -1:1;
      } else return (o1.matched.size() < o2.matched.size())? -1:1;
    } else if (Double.isNaN(o1.cost)) {
      return -1;
    } else if (Double.isNaN(o2.cost)) {
      return 1;
    } else return (o1.cost < o2.cost)? -1:1;
  };

}
