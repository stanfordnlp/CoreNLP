package edu.stanford.nlp.ling.tokensregex;

import java.util.function.Predicate;

import java.util.Arrays;
import java.util.List;

/**
 * Performs action on a sequence
 *
 * @author Angel Chang
 */
public interface SequenceMatchAction<T> {
  public SequenceMatchResult<T> apply(SequenceMatchResult<T> matchResult, int... groups);

  public final static class BoundAction<T> {
    SequenceMatchAction<T> action;
    int[] groups;

    public SequenceMatchResult<T> apply(SequenceMatchResult<T> seqMatchResult) {
      return action.apply(seqMatchResult, groups);
    }
  }

  public final static class StartMatchAction<T> implements SequenceMatchAction<T> {
    SequencePattern<T> pattern;

    public StartMatchAction(SequencePattern<T> pattern) {
      this.pattern = pattern;
    }

    public SequenceMatchResult<T> apply(SequenceMatchResult<T> seqMatchResult, int... groups) {
      SequenceMatcher<T> matcher = pattern.getMatcher(seqMatchResult.elements());
      if (matcher.find()) {
        return matcher;
      } else {
        return null;
      }
    }
  }

  public final static class NextMatchAction<T> implements SequenceMatchAction<T> {
    public SequenceMatchResult<T> apply(SequenceMatchResult<T> seqMatchResult, int... groups) {
      if (seqMatchResult instanceof SequenceMatcher) {
        SequenceMatcher<T> matcher = (SequenceMatcher<T>) seqMatchResult;
        if (matcher.find()) {
          return matcher;
        } else {
          return null;
        }
      } else {
        return null;
      }
    }
  }

  public final static class BranchAction<T> implements SequenceMatchAction<T> {
    Predicate<SequenceMatchResult<T>> filter;
    SequenceMatchAction<T> acceptBranch;
    SequenceMatchAction<T> rejectBranch;

    public BranchAction(Predicate<SequenceMatchResult<T>> filter, SequenceMatchAction<T> acceptBranch, SequenceMatchAction<T> rejectBranch) {
      this.filter = filter;
      this.acceptBranch = acceptBranch;
      this.rejectBranch = rejectBranch;
    }

    public SequenceMatchResult<T> apply(SequenceMatchResult<T> seqMatchResult, int... groups) {
      if (filter.test(seqMatchResult)) {
        return (acceptBranch != null)? acceptBranch.apply(seqMatchResult):null;
      } else {
        return (rejectBranch != null)? rejectBranch.apply(seqMatchResult):null;
      }
    }
  }

  public final static class SeriesAction<T> implements SequenceMatchAction<T> {
    List<SequenceMatchAction<T>> actions;

    public SeriesAction(SequenceMatchAction<T>... actions) {
      this.actions = Arrays.asList(actions);
    }

    public SeriesAction(List<SequenceMatchAction<T>> actions) {
      this.actions = actions;
    }

    public SequenceMatchResult<T> apply(SequenceMatchResult<T> seqMatchResult, int... groups) {
      SequenceMatchResult<T> res = seqMatchResult;
      for (SequenceMatchAction<T> a:actions) {
        res = a.apply(res, groups);
      }
      return res;
    }
  }

}
