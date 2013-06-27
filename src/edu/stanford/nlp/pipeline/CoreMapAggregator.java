package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Function that aggregates several core maps into one
 *
 * @author Angel Chang
 */
public class CoreMapAggregator implements Function<List<? extends CoreMap>, CoreMap> {
  public static final CoreMapAggregator DEFAULT_AGGREGATOR = getAggregator(CoreMapAttributeAggregator.getDefaultAggregators());
  Map<Class, CoreMapAttributeAggregator> aggregators;
  Class mergedKey = null;  // Keeps chunks that were merged to form this one

  public CoreMapAggregator(Map<Class, CoreMapAttributeAggregator> aggregators) {
    this.aggregators = aggregators;
  }

  public CoreMapAggregator(Map<Class, CoreMapAttributeAggregator> aggregators, Class mergedKey) {
    this.aggregators = aggregators;
    this.mergedKey = mergedKey;
  }

  public CoreMap merge(List<? extends CoreMap> in, int start, int end)
  {
    CoreMap merged = ChunkAnnotationUtils.getMergedChunk(in, start, end, aggregators);
    if (mergedKey != null) {
      merged.set(mergedKey, new ArrayList<CoreMap>(in.subList(start, end)));
    }
    return merged;
  }

  public CoreMap apply(List<? extends CoreMap> in) {
    return merge(in, 0, in.size());
  }

  public static CoreMapAggregator getDefaultAggregator()
  {
    return DEFAULT_AGGREGATOR;
  }

  public static CoreMapAggregator getAggregator(Map<Class, CoreMapAttributeAggregator> aggregators)
  {
    return new CoreMapAggregator(aggregators);
  }

  public static CoreMapAggregator getAggregator(Map<Class, CoreMapAttributeAggregator> aggregators, Class key)
  {
    return new CoreMapAggregator(aggregators, key);
  }

  public List<CoreMap> merge(List<? extends CoreMap> list, List<? extends HasInterval<Integer>> matched)
  {
    return CollectionUtils.mergeList(list, matched, this);
  }

  public <M> List<CoreMap> merge(List<? extends CoreMap> list, List<M> matched, Function<M, Interval<Integer>> toIntervalFunc)
  {
    return CollectionUtils.mergeList(list, matched, toIntervalFunc, this);
  }
}
