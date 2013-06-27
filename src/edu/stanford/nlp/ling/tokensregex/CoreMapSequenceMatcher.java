package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.pipeline.CoreMapAttributeAggregator;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CoreMap Sequence Matcher for regular expressions for sequences over coremaps
 *
 * @author Angel Chang
 */
public class CoreMapSequenceMatcher<T extends CoreMap> extends SequenceMatcher<T> {
  protected static final Function<List<? extends CoreMap>, String> COREMAP_LIST_TO_STRING_CONVERTER =
          new Function<List<? extends CoreMap>, String>() {
            public String apply(List<? extends CoreMap> in) {
              return (in != null)? ChunkAnnotationUtils.getTokenText(in, CoreAnnotations.TextAnnotation.class): null;
            }
          };

 public CoreMapSequenceMatcher(SequencePattern pattern, List<? extends T> tokens) {
    super(pattern, tokens);
//    this.nodesToStringConverter = COREMAP_LIST_TO_STRING_CONVERTER;
  }

  public static class BasicCoreMapSequenceMatcher extends CoreMapSequenceMatcher<CoreMap> {
    CoreMap annotation;
    public BasicCoreMapSequenceMatcher(SequencePattern pattern, CoreMap annotation) {
      super(pattern, annotation.get(CoreAnnotations.TokensAnnotation.class));
      this.annotation = annotation;
      this.nodesToStringConverter = COREMAP_LIST_TO_STRING_CONVERTER;
    }
  }

  public void annotateGroup(Map<String,String> attributes)
  {
    annotateGroup(0, attributes);
  }

  public void annotateGroup(int group, Map<String,String> attributes)
  {
    int groupStart = start(group);
    if (groupStart >=0) {
      int groupEnd = end(group);
      ChunkAnnotationUtils.annotateChunks(elements, groupStart, groupEnd, attributes);
    }
  }

  public List<CoreMap> getMergedList()
  {
    return getMergedList(0);
  }

  public List<CoreMap> getMergedList(int... groups)
  {
    List<CoreMap> res = new ArrayList<CoreMap>();
    int last = 0;
    List<Integer> orderedGroups = CollectionUtils.asList(groups);
    Collections.sort(orderedGroups);
    for (int group:orderedGroups) {
      int groupStart = start(group);
      if (groupStart >= last) {
        res.addAll(elements.subList(last,groupStart));
        int groupEnd = end(group);
        if (groupEnd - groupStart >= 1) {
          CoreMap merged = createMergedChunk(groupStart, groupEnd);
          res.add(merged);
          last = groupEnd;
        }
      }
    }
    res.addAll(elements.subList(last, elements.size()));
    return res;
  }

  public CoreMap mergeGroup()
  {
    return mergeGroup(0);
  }

  private CoreMap createMergedChunk(int groupStart, int groupEnd)
  {
    CoreMap merged = null;
  /*  if (annotation != null) {
      // Take start and end
      merged = ChunkAnnotationUtils.getMergedChunk(elements, annotation.get(CoreAnnotations.TextAnnotation.class), groupStart, groupEnd);
    }  */
    if (merged == null) {
      // Okay, have to go through these one by one and merge them
      merged = ChunkAnnotationUtils.getMergedChunk(elements, groupStart, groupEnd, CoreMapAttributeAggregator.getDefaultAggregators());
    }
    return merged;
  }

  public CoreMap mergeGroup(int group)
  {
    int groupStart = start(group);
    if (groupStart >=0) {
      int groupEnd = end(group);
      if (groupEnd - groupStart >= 1) {
        return createMergedChunk(groupStart, groupEnd);
      }
    }
    return null;
  }

}
