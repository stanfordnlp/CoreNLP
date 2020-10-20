package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.pipeline.CoreMapAggregator;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Performs a action on a matched sequence
 *
 * @author Angel Chang
 */
public abstract class CoreMapSequenceMatchAction<T extends CoreMap> implements SequenceMatchAction<T> {

  public final static class AnnotateAction<T extends CoreMap> extends CoreMapSequenceMatchAction<T> {
    Map<String,String> attributes;   // TODO: Preconvert, handle when to overwrite existing attributes

    public AnnotateAction(Map<String, String> attributes) {
      this.attributes = attributes;
    }

    public SequenceMatchResult<T> apply(SequenceMatchResult<T> matchResult, int... groups) {
      for (int group:groups) {
        int groupStart = matchResult.start(group);
        if (groupStart >=0) {
          int groupEnd = matchResult.end(group);
          ChunkAnnotationUtils.annotateChunks(matchResult.elements(), groupStart, groupEnd, attributes);
        }
      }
      return matchResult;
    }

  }

  public final static MergeAction DEFAULT_MERGE_ACTION = new MergeAction();

  public final static class MergeAction extends CoreMapSequenceMatchAction<CoreMap> {
    CoreMapAggregator aggregator = CoreMapAggregator.getDefaultAggregator();

    public MergeAction() {}
    
    public MergeAction(CoreMapAggregator aggregator) {
      this.aggregator = aggregator;
    }

    public SequenceMatchResult<CoreMap> apply(SequenceMatchResult<CoreMap> matchResult, int... groups)
    {
      BasicSequenceMatchResult<CoreMap> res = matchResult.toBasicSequenceMatchResult();

      List<? extends CoreMap> elements = matchResult.elements();
      List<CoreMap> mergedElements = new ArrayList<>();
      res.elements = mergedElements;

      int last = 0;
      int mergedGroup = 0;
      int offset = 0;
      List<Integer> orderedGroups = CollectionUtils.asList(groups);
      Collections.sort(orderedGroups);
      for (int group:orderedGroups) {
        int groupStart = matchResult.start(group);
        if (groupStart >= last) {
          // Add elements from last to start of group to merged elements
          mergedElements.addAll(elements.subList(last,groupStart));
          // Fiddle with matched group indices
          for (; mergedGroup < group; mergedGroup++) {
            if (res.matchedGroups[mergedGroup] != null) {
              res.matchedGroups[mergedGroup].matchBegin -= offset;
              res.matchedGroups[mergedGroup].matchEnd -= offset;
            }
          }
          // Get merged element
          int groupEnd = matchResult.end(group);
          if (groupEnd - groupStart >= 1) {
            CoreMap merged = aggregator.merge(elements, groupStart, groupEnd);
            mergedElements.add(merged);
            last = groupEnd;

            // Fiddle with matched group indices
            res.matchedGroups[mergedGroup].matchBegin = mergedElements.size()-1;
            res.matchedGroups[mergedGroup].matchEnd = mergedElements.size();
            mergedGroup++;
            while (mergedGroup < res.matchedGroups.length)  {
              if (res.matchedGroups[mergedGroup] != null) {
                if (res.matchedGroups[mergedGroup].matchBegin == matchResult.start(group) &&
                        res.matchedGroups[mergedGroup].matchEnd == matchResult.end(group)) {
                  res.matchedGroups[mergedGroup].matchBegin = res.matchedGroups[group].matchBegin;
                  res.matchedGroups[mergedGroup].matchEnd = res.matchedGroups[group].matchEnd;
                } else if (res.matchedGroups[mergedGroup].matchEnd <= matchResult.end(group)) {
                  res.matchedGroups[mergedGroup] = null;
                } else {
                  break;
                }
              }
              mergedGroup++;
            }
            offset = matchResult.end(group) - res.matchedGroups[group].matchEnd;
          }
        }
      }
      // Add rest of elements
      mergedElements.addAll(elements.subList(last, elements.size()));
      // Fiddle with matched group indices
      for (; mergedGroup < res.matchedGroups.length; mergedGroup++) {
        if (res.matchedGroups[mergedGroup] != null) {
          res.matchedGroups[mergedGroup].matchBegin -= offset;
          res.matchedGroups[mergedGroup].matchEnd -= offset;
        }
      }
      return res;
    }
  }

}
