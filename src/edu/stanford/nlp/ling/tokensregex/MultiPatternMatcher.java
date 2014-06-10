package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.IntervalTree;

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

  public List<SequenceMatchResult<T>> findNonOverlapping(List<? extends T> elements)
  {
    return findNonOverlapping(elements, SequenceMatchResult.DEFAULT_COMPARATOR);
  }

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


}
