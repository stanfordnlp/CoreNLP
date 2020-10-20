package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.pipeline.CoreMapAttributeAggregator;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Interval;

import java.util.*;

/**
 * Pattern for matching across multiple core maps.
 *
 * <p>
 * This class allows for string matches across tokens.  It is not implemented efficiently
 * (it basically creates a big pretend token and tries to do string match on that)
 * so can be expensive to use.  Whenever possible, <code>SequencePattern</code> should be used instead.
 * </p>
 *
 * @author Angel Chang
 */
public class MultiCoreMapNodePattern extends MultiNodePattern<CoreMap> {

  Map<Class, CoreMapAttributeAggregator> aggregators = CoreMapAttributeAggregator.getDefaultAggregators();
  NodePattern nodePattern;

  public MultiCoreMapNodePattern() {}

  public MultiCoreMapNodePattern(NodePattern nodePattern) {
    this.nodePattern = nodePattern;
  }

  public MultiCoreMapNodePattern(NodePattern nodePattern, Map<Class, CoreMapAttributeAggregator> aggregators) {
    this.nodePattern = nodePattern;
    this.aggregators = aggregators;
  }

  protected Collection<Interval<Integer>> match(List<? extends CoreMap> nodes, int start)
  {
    List<Interval<Integer>> matched = new ArrayList<>();
    int minEnd = start + minNodes;
    int maxEnd = nodes.size();
    if (maxNodes >= 0 && maxNodes + start < nodes.size()) {
      maxEnd = maxNodes + start;
    }
    for (int end = minEnd; end <= maxEnd; end++) {
      CoreMap chunk = ChunkAnnotationUtils.getMergedChunk(nodes, start, end, aggregators, null);
      if (nodePattern.match(chunk)) {
        matched.add(Interval.toInterval(start, end));
      }
    }
    return matched;
  }

  public static class StringSequenceAnnotationPattern extends MultiNodePattern<CoreMap> {
    Class textKey;
    PhraseTable phraseTable;

    public StringSequenceAnnotationPattern(Class textKey, Set<List<String>> targets, boolean ignoreCase) {
      this.textKey = textKey;
      phraseTable = new PhraseTable(false, ignoreCase, false);
      for (List<String> target:targets) {
        phraseTable.addPhrase(target);
        if (maxNodes < 0 || target.size() > maxNodes) maxNodes = target.size();
      }
    }

    public StringSequenceAnnotationPattern(Class textKey, Set<List<String>> targets) {
      this(textKey, targets, false);
    }

    public StringSequenceAnnotationPattern(Class textKey, Map<List<String>, Object> targets, boolean ignoreCase) {
      this.textKey = textKey;
      phraseTable = new PhraseTable(false, ignoreCase, false);
      for (List<String> target:targets.keySet()) {
        phraseTable.addPhrase(target, null, targets.get(target));
        if (maxNodes < 0 || target.size() > maxNodes) maxNodes = target.size();
      }
    }

    public StringSequenceAnnotationPattern(Class textKey, Map<List<String>, Object> targets) {
      this(textKey, targets, false);
    }

    protected Collection<Interval<Integer>> match(List<? extends CoreMap> nodes, int start) {
      PhraseTable.WordList words = new PhraseTable.TokenList(nodes, textKey);
      List<PhraseTable.PhraseMatch> matches = phraseTable.findMatches(words, start, nodes.size(), false);
      Collection<Interval<Integer>> intervals = new ArrayList<>(matches.size());
      for (PhraseTable.PhraseMatch match:matches) {
        intervals.add(match.getInterval());
      }
      return intervals;
    }

    public String toString() {
      return ":" + phraseTable;
    }
  }


}
