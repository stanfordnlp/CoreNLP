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
    List<Interval<Integer>> matched = new ArrayList<Interval<Integer>>();
    int minEnd = start + minNodes;
    int maxEnd = nodes.size();
    if (maxNodes >= 0 && maxNodes + start < nodes.size()) {
      maxEnd = maxNodes + start;
    }
    for (int end = minEnd; end <= maxEnd; end++) {
      CoreMap chunk = ChunkAnnotationUtils.getMergedChunk(nodes, start, end, aggregators);
      if (nodePattern.match(chunk)) {
        matched.add(Interval.toInterval(start, end));
      }
    }
    return matched;
  }



}
