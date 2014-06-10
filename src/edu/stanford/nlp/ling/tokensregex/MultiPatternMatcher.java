package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.ConcatenationIterator;
import edu.stanford.nlp.util.IntervalTree;
import edu.stanford.nlp.util.Iterables;

import java.util.*;

/**
 * Matcher that takes in multiple patterns
 *
 * @author Angel Chang
 */
public class MultiPatternMatcher<T> {
  List<SequencePattern<T>> patterns;


  public MultiPatternMatcher(List<SequencePattern<T>> patterns)
  {
    this.patterns = patterns;
  }

  public MultiPatternMatcher(SequencePattern<T>... patterns)
  {
    this.patterns = new ArrayList<SequencePattern<T>>(patterns.length);
    for (SequencePattern<T> p:patterns) {
      this.patterns.add(p);
    }
  }

  /**
   * Given a sequence, applies our patterns over the sequence and returns
   *   all non overlapping matches.  When multiple patterns overlaps,
   *   matched patterns are selected by
   *     the highest priority/score is selected,
   *     then the longest pattern,
   *     then the starting offset
   * @param elements input sequence to match against
   * @return list of match results that are nonoverlapping
   */
  public List<SequenceMatchResult<T>> findNonOverlapping(List<? extends T> elements)
  {
    return findNonOverlapping(elements, SequenceMatchResult.DEFAULT_COMPARATOR);
  }

  /**
   * Given a sequence, applies our patterns over the sequence and returns
   *   all non overlapping matches.  When multiple patterns overlaps,
   *   matched patterns are selected by order specified by the comparator
   * @param elements input sequence to match against
   * @param cmp comparator indicating order that overlapped sequences should be selected.
   * @return list of match results that are nonoverlapping
   */
  public List<SequenceMatchResult<T>> findNonOverlapping(List<? extends T> elements,
                                                         Comparator<? super SequenceMatchResult> cmp)
  {
    List<SequenceMatchResult<T>> all = new ArrayList<SequenceMatchResult<T>>();
    int i = 0;
    for (SequencePattern<T> p:patterns) {
      SequenceMatcher<T> m = p.getMatcher(elements);
      m.setOrder(i);
      while (m.find()) {
        all.add(m.toBasicSequenceMatchResult());
      }
      i++;
    }
    List<SequenceMatchResult<T>> res = IntervalTree.getNonOverlapping( all, SequenceMatchResult.TO_INTERVAL, cmp);
    Collections.sort(res, SequenceMatchResult.OFFSET_COMPARATOR);

    return res;
  }

  /**
   * Given a sequence, applies each of our patterns over the sequence and returns
   *   for each pattern, the list of matched sequences
   * @param elements input sequence to match against
   * @return iterable of match results that are nonoverlapping
   */
  public Iterable<SequenceMatchResult<T>> findAllNonOverlappingMatchesPerPattern(List<? extends T> elements)
  {
    List<Iterable<SequenceMatchResult<T>>> allMatches = new ArrayList<Iterable<SequenceMatchResult<T>>>(elements.size());
    for (SequencePattern<T> p:patterns) {
      Iterable<SequenceMatchResult<T>> matches = p.getMatcher(elements).findAllNonOverlapping();
      allMatches.add(matches);
    }
    return Iterables.chain(allMatches);
  }



}
