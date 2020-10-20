package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Function that aggregates several core maps into one
 *
 * @author Angel Chang
 */
public class CoreMapAggregator implements Function<List<? extends CoreMap>, CoreMap> {
  public static final CoreMapAggregator DEFAULT_AGGREGATOR = getAggregator(CoreMapAttributeAggregator.getDefaultAggregators());
  public static final CoreMapAggregator DEFAULT_NUMERIC_TOKENS_AGGREGATOR = getAggregator(CoreMapAttributeAggregator.DEFAULT_NUMERIC_TOKENS_AGGREGATORS);

  Map<Class, CoreMapAttributeAggregator> aggregators;
  Class mergedKey = null;  // Keeps chunks that were merged to form this one
  CoreLabelTokenFactory tokenFactory = null; // Should we be creating tokens?

  public CoreMapAggregator(Map<Class, CoreMapAttributeAggregator> aggregators) {
    this.aggregators = aggregators;
  }

  public CoreMapAggregator(Map<Class, CoreMapAttributeAggregator> aggregators, Class mergedKey, CoreLabelTokenFactory tokenFactory) {
    this.aggregators = aggregators;
    this.mergedKey = mergedKey;
    this.tokenFactory = tokenFactory;
  }

  public CoreMap merge(List<? extends CoreMap> in, int start, int end)
  {
    CoreMap merged = ChunkAnnotationUtils.getMergedChunk(in, start, end, aggregators, tokenFactory);
    if (mergedKey != null) {
      merged.set(mergedKey, new ArrayList<>(in.subList(start, end)));
    }
    return merged;
  }

  public CoreMap merge(List<? extends CoreMap> in) {
    return merge(in, 0, in.size());
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
    return new CoreMapAggregator(aggregators, key, null);
  }

  public static CoreMapAggregator getAggregator(Map<Class, CoreMapAttributeAggregator> aggregators, Class key, CoreLabelTokenFactory tokenFactory)
  {
    return new CoreMapAggregator(aggregators, key, tokenFactory);
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
