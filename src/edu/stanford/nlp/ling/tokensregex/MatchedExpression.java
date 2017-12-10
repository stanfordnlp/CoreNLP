package edu.stanford.nlp.ling.tokensregex;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.types.Value;
import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.pipeline.CoreMapAggregator;
import edu.stanford.nlp.util.*;

/**
 * Matched Expression represents a chunk of text that was matched from an original segment of text.
 *
 * @author Angel Chang
 */
public class MatchedExpression {

  /** Text representing the matched expression */
  protected String text;

  /**
   * Character offsets (relative to original text).
   * TODO: Fix up
   *  If matched using regular text patterns,
   *     the character offsets are with respect to the annotation (usually sentence)
   *     from which the text was matched against
   *  If matched using tokens, the character offsets are with respect to the overall document
   */
  protected Interval<Integer> charOffsets;
  /**Token offsets (relative to original text tokenization) */
  protected Interval<Integer> tokenOffsets;
  /** Chunk offsets (relative to chunking on top of original text) */
  protected Interval<Integer> chunkOffsets;
  protected CoreMap annotation;

  // TODO: Should we keep some context from the source so we can perform more complex evaluation?
  /** Function indicating how to extract an value from annotation built from this expression */
  protected Object context; // Some context to help to extract value from annotation
  protected SingleAnnotationExtractor extractFunc;

  public Value value;
  //protected Map<String,String> attributes;

  // Used to disambiguate matched expressions
  double priority;
  double weight;
  int order;

  /**
   * Function that takes a CoreMap, applies an extraction function to it, to get a value.
   * Also contains information on how to construct a final annotation.
   */
  public static class SingleAnnotationExtractor implements Function<CoreMap,Value> {
    public String name;
    public double priority;      // Priority/Order in which this rule should be applied with respect to others
    public double weight;        // Weight given to the rule (how likely is this rule to fire)
//    public Class annotationField;  // Annotation field to apply rule over: text or tokens or numerizedtokens
    public Class tokensAnnotationField = CoreAnnotations.TokensAnnotation.class;  // Tokens or numerizedtokens
    public List<Class> tokensResultAnnotationField;
    public List<Class> resultAnnotationField;  // Annotation field to put new annotation
    public Class resultNestedAnnotationField; // Annotation field for child/nested annotations
    public boolean includeNested = false;
    public Function<CoreMap, Value> valueExtractor;
    public Function<MatchedExpression, Value> expressionToValue;
    public Function<MatchedExpression,?> resultAnnotationExtractor;
    public CoreMapAggregator tokensAggregator;

    @Override
    public Value apply(CoreMap in) {
      return valueExtractor.apply(in);
    }

    private static void setAnnotations(CoreMap cm, List<Class> annotationKeys, Object obj) {
      if (annotationKeys.size() > 1 && obj instanceof List) {
        // List of annotationKeys, obj also list, we should try to match the objects to annotationKeys
        List list = (List) obj;
        int n = Math.min(list.size(), annotationKeys.size());
        for (int i = 0; i < n; i++) {
          Object v = list.get(i);
          Class key = annotationKeys.get(i);
          if (key == null) {
            throw new RuntimeException("Invalid null annotation key");
          }
          if (v instanceof Value) {
            cm.set(key, ((Value) v).get());
          } else {
            cm.set(key, v);
          }
        }
      } else {
        // Only a single object, set all annotationKeys to that obj
        for (Class key:annotationKeys) {
          if (key == null) {
            throw new RuntimeException("Invalid null annotation key");
          }
          cm.set(key, obj);
        }
      }
    }

    public void annotate(MatchedExpression matchedExpression, List<? extends CoreMap> nested) {
      if (resultNestedAnnotationField != null) {
        matchedExpression.annotation.set(resultNestedAnnotationField, nested);
      }
      // NOTE: for now value must be extracted after nested annotation is in place...
      annotate(matchedExpression);
    }

    public void annotate(MatchedExpression matchedExpression) {
      Value ev = null;
      if (expressionToValue != null) {
        ev = expressionToValue.apply(matchedExpression);
      }
      matchedExpression.value = (ev != null)? ev : valueExtractor.apply(matchedExpression.annotation);

      if (resultAnnotationField != null) {
        if (resultAnnotationExtractor != null) {
          Object result = resultAnnotationExtractor.apply(matchedExpression);
          setAnnotations(matchedExpression.annotation, resultAnnotationField, result);
        } else {
          // TODO: Should default result be the matchedExpression, value, object???
          //matchedExpression.annotation.set(resultAnnotationField, matchedExpression);
          Value v = matchedExpression.getValue();
          setAnnotations(matchedExpression.annotation, resultAnnotationField, (v != null)? v.get():null);
        }
      }

      if (tokensResultAnnotationField != null) {
        List<? extends CoreMap> tokens = (List<? extends CoreMap>) matchedExpression.annotation.get(tokensAnnotationField);
        if (resultAnnotationExtractor != null) {
          Object result = resultAnnotationExtractor.apply(matchedExpression);
          for (CoreMap cm:tokens) {
            setAnnotations(cm, tokensResultAnnotationField, result);
          }
        } else {
          // TODO: Should default result be the matchedExpression, value, object???
          //matchedExpression.annotation.set(resultAnnotationField, matchedExpression);
          Value v = matchedExpression.getValue();
          for (CoreMap cm:tokens) {
            setAnnotations(cm, tokensResultAnnotationField, (v != null)? v.get():null);
          }
        }
      }
    }

    public MatchedExpression createMatchedExpression(Interval<Integer> charOffsets, Interval<Integer> tokenOffsets) {
      return new MatchedExpression(charOffsets, tokenOffsets, this, priority, weight);
    }

  } // end static class SingleAnnotationExtractor


  public MatchedExpression(MatchedExpression me) {
    this.annotation = me.annotation;
    this.extractFunc = me.extractFunc;
    this.text = me.text;
    this.value = me.value;
    //this.attributes = me.attributes;
    this.priority = me.priority;
    this.weight = me.weight;
    this.order = me.order;
    this.charOffsets = me.charOffsets;
    this.tokenOffsets = me.tokenOffsets;
    this.chunkOffsets = me.tokenOffsets;
  }

  public MatchedExpression(Interval<Integer> charOffsets, Interval<Integer> tokenOffsets,
                           SingleAnnotationExtractor extractFunc, double priority, double weight) {
    this.charOffsets = charOffsets;
    this.tokenOffsets = tokenOffsets;
    this.chunkOffsets = tokenOffsets;
    this.extractFunc = extractFunc;
    this.priority = priority;
    this.weight = weight;
  }

  public boolean extractAnnotation(Env env, CoreMap sourceAnnotation) {
    return extractAnnotation(sourceAnnotation, extractFunc.tokensAggregator);
  }

  private boolean extractAnnotation(CoreMap sourceAnnotation,
                                    CoreMapAggregator aggregator) {
    Class<TypesafeMap.Key<List<? extends CoreMap>>> tokensAnnotationKey = extractFunc.tokensAnnotationField;
    if (chunkOffsets != null) {
      annotation = aggregator.merge((List<? extends CoreMap>) sourceAnnotation.get(tokensAnnotationKey),
              chunkOffsets.getBegin(), chunkOffsets.getEnd());
      if (sourceAnnotation.containsKey(CoreAnnotations.TextAnnotation.class)) {
        ChunkAnnotationUtils.annotateChunkText(annotation, sourceAnnotation);
      }
      if (tokenOffsets != null) {
        if (annotation.get(CoreAnnotations.TokenBeginAnnotation.class) == null) {
          annotation.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffsets.getBegin());
        }
        if (annotation.get(CoreAnnotations.TokenEndAnnotation.class) == null) {
          annotation.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffsets.getEnd());
        }
      }

      charOffsets = Interval.toInterval(annotation.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class), annotation.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
      tokenOffsets = Interval.toInterval(annotation.get(CoreAnnotations.TokenBeginAnnotation.class),
              annotation.get(CoreAnnotations.TokenEndAnnotation.class), Interval.INTERVAL_OPEN_END);
    } else {
      Integer baseCharOffset = sourceAnnotation.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      if (baseCharOffset == null) {
        baseCharOffset = 0;
      }

      chunkOffsets = ChunkAnnotationUtils.getChunkOffsetsUsingCharOffsets((List<? extends CoreMap>) sourceAnnotation.get(tokensAnnotationKey),
              charOffsets.getBegin() + baseCharOffset, charOffsets.getEnd()  + baseCharOffset);
      CoreMap annotation2 = aggregator.merge((List<? extends CoreMap>) sourceAnnotation.get(tokensAnnotationKey),
              chunkOffsets.getBegin(), chunkOffsets.getEnd());

      annotation = ChunkAnnotationUtils.getAnnotatedChunkUsingCharOffsets(sourceAnnotation, charOffsets.getBegin(), charOffsets.getEnd());
      tokenOffsets = Interval.toInterval(annotation.get(CoreAnnotations.TokenBeginAnnotation.class),
              annotation.get(CoreAnnotations.TokenEndAnnotation.class), Interval.INTERVAL_OPEN_END);
      annotation.set(tokensAnnotationKey, annotation2.get(tokensAnnotationKey));
    }
    text = annotation.get(CoreAnnotations.TextAnnotation.class);
    extractFunc.annotate(this, (List<? extends CoreMap>) annotation.get(tokensAnnotationKey));
    return true;
  }

  public boolean extractAnnotation(Env env, List<? extends CoreMap> source) {
    return extractAnnotation(source, CoreMapAggregator.getDefaultAggregator());
  }

  protected boolean extractAnnotation(List<? extends CoreMap> source, CoreMapAggregator aggregator) {
    annotation = aggregator.merge(source, chunkOffsets.getBegin(), chunkOffsets.getEnd());
    charOffsets = Interval.toInterval(annotation.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
            annotation.get(CoreAnnotations.CharacterOffsetEndAnnotation.class), Interval.INTERVAL_OPEN_END);
    tokenOffsets = Interval.toInterval(annotation.get(CoreAnnotations.TokenBeginAnnotation.class),
              annotation.get(CoreAnnotations.TokenEndAnnotation.class), Interval.INTERVAL_OPEN_END);
    text = annotation.get(CoreAnnotations.TextAnnotation.class);
    extractFunc.annotate(this, source.subList(chunkOffsets.getBegin(), chunkOffsets.getEnd()));
    return true;
  }

  public Interval<Integer> getCharOffsets() {
    return charOffsets;
  }

  public Interval<Integer> getTokenOffsets() {
    return tokenOffsets;
  }

  public Interval<Integer> getChunkOffsets() {
    return chunkOffsets;
  }

 /* public Map<String, String> getAttributes() {
    return attributes;
  }*/

  public double getPriority() {
    return priority;
  }

  public double getWeight() {
    return weight;
  }

  public int getOrder() {
    return order;
  }

  public boolean isIncludeNested() {
    return extractFunc.includeNested;
  }

  public void setIncludeNested(boolean includeNested) {
    extractFunc.includeNested = includeNested;
  }

  public String getText() {
    return text;
  }

  public CoreMap getAnnotation() {
    return annotation;
  }

  public Value getValue() { return value; }

  public String toString()
  {
    return text;
  }

  public static List<? extends CoreMap> replaceMerged(List<? extends CoreMap> list,
                                                      List<? extends MatchedExpression> matchedExprs) {
    if (matchedExprs == null) return list;
    matchedExprs.sort(EXPR_TOKEN_OFFSET_COMPARATOR);
    List<CoreMap> merged = new ArrayList<>(list.size());   // Approximate size
    int last = 0;
    for (MatchedExpression expr:matchedExprs) {
      int start = expr.chunkOffsets.first();
      int end = expr.chunkOffsets.second();
      if (start >= last) {
        merged.addAll(list.subList(last,start));
        CoreMap m = expr.getAnnotation();
        merged.add(m);
        last = end;
      }
    }
    // Add rest of elements
    if (last < list.size()) {
      merged.addAll(list.subList(last, list.size()));
    }
    return merged;
  }

  public static List<? extends CoreMap> replaceMergedUsingTokenOffsets(List<? extends CoreMap> list,
                                                      List<? extends MatchedExpression> matchedExprs) {
    if (matchedExprs == null) return list;
    Map<Integer, Integer> tokenBeginToListIndexMap = new HashMap<>();//Generics.newHashMap();
    Map<Integer, Integer> tokenEndToListIndexMap = new HashMap<>();//Generics.newHashMap();
    for (int i = 0; i < list.size(); i++) {
      CoreMap cm = list.get(i);
      if (cm.containsKey(CoreAnnotations.TokenBeginAnnotation.class) && cm.containsKey(CoreAnnotations.TokenEndAnnotation.class)) {
        tokenBeginToListIndexMap.put(cm.get(CoreAnnotations.TokenBeginAnnotation.class), i);
        tokenEndToListIndexMap.put(cm.get(CoreAnnotations.TokenEndAnnotation.class), i+1);
      } else {
        tokenBeginToListIndexMap.put(i, i);
        tokenEndToListIndexMap.put(i+1, i+1);
      }
    }
    matchedExprs.sort(EXPR_TOKEN_OFFSET_COMPARATOR);
    List<CoreMap> merged = new ArrayList<>(list.size());   // Approximate size
    int last = 0;
    for (MatchedExpression expr:matchedExprs) {
      int start = expr.tokenOffsets.first();
      int end = expr.tokenOffsets.second();
      Integer istart = tokenBeginToListIndexMap.get(start);
      Integer iend = tokenEndToListIndexMap.get(end);
      if (istart != null && iend != null) {
        if (istart >= last) {
          merged.addAll(list.subList(last,istart));
          CoreMap m = expr.getAnnotation();
          merged.add(m);
          last = iend;
        }
      }
    }
    // Add rest of elements
    if (last < list.size()) {
      merged.addAll(list.subList(last, list.size()));
    }
    return merged;
  }

  public static <T extends MatchedExpression> List<T> removeNullValues(List<T> chunks) {
    List<T> okayChunks = new ArrayList<>(chunks.size());
    for (T chunk : chunks) {
      Value v = chunk.value;
      if (v == null || v.get() == null) {
        //skip
      } else {
        okayChunks.add(chunk);
      }
    }
    return okayChunks;
  }

  public static <T extends MatchedExpression> List<T> removeNested(List<T> chunks) {
    if (chunks.size() > 1) {
      for (int i = 0, sz = chunks.size(); i < sz; i++) {
        chunks.get(i).order = i;
      }
      return IntervalTree.getNonNested(chunks, EXPR_TO_TOKEN_OFFSETS_INTERVAL_FUNC, EXPR_LENGTH_PRIORITY_COMPARATOR);
    } else {
      return chunks;
    }
  }

  public static <T extends MatchedExpression> List<T> removeOverlapping(List<T> chunks) {
    if (chunks.size() > 1) {
      for (int i = 0, sz = chunks.size(); i < sz; i++) {
        chunks.get(i).order = i;
      }
      return IntervalTree.getNonOverlapping(chunks, EXPR_TO_TOKEN_OFFSETS_INTERVAL_FUNC, EXPR_PRIORITY_LENGTH_COMPARATOR);
    } else {
      return chunks;
    }
  }

  public static <T extends MatchedExpression> T getBestMatched(List<T> matches, ToDoubleFunction<MatchedExpression> scorer) {
    if (matches == null || matches.isEmpty()) return null;
    T best = null;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (T m : matches) {
      double s = scorer.applyAsDouble(m);
      if (best == null || s > bestScore) {
        best = m;
        bestScore = s;
      }
    }
    return best;
  }

  @SuppressWarnings("unused")
  public static final Function<CoreMap, Interval<Integer>> COREMAP_TO_TOKEN_OFFSETS_INTERVAL_FUNC =
      in -> Interval.toInterval(
            in.get(CoreAnnotations.TokenBeginAnnotation.class),
            in.get(CoreAnnotations.TokenEndAnnotation.class));

  public static final Function<CoreMap, Interval<Integer>> COREMAP_TO_CHAR_OFFSETS_INTERVAL_FUNC =
      in -> Interval.toInterval(
              in.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
              in.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));

  public static final Function<MatchedExpression, Interval<Integer>> EXPR_TO_TOKEN_OFFSETS_INTERVAL_FUNC =
          in -> in.tokenOffsets;

  public static final Comparator<MatchedExpression> EXPR_PRIORITY_COMPARATOR =
      (e1, e2) -> {
        double s1 = e1.getPriority();
        double s2 = e2.getPriority();
        if (s1 == s2) {
          return 0;
        } else {
          return (s1 > s2)? -1:1;
        }
      };

  public static final Comparator<MatchedExpression> EXPR_ORDER_COMPARATOR =
      (e1, e2) -> {
        int s1 = e1.getOrder();
        int s2 = e2.getOrder();
        if (s1 == s2) {
          return 0;
        } else {
          return (s1 < s2)? -1:1;
        }
      };

  // Compares two matched expressions.
  // Use to order matched expressions by:
  //    length (longest first), then whether it has value or not (has value first),
  // Returns -1 if e1 is longer than e2, 1 if e2 is longer
  // If e1 and e2 are the same length:
  //    Returns -1 if e1 has value, but e2 doesn't (1 if e2 has value, but e1 doesn't)
  //    Otherwise, both e1 and e2 has value or no value
  public static final Comparator<MatchedExpression> EXPR_LENGTH_COMPARATOR =
          (e1, e2) -> {
            if (e1.getValue() == null && e2.getValue() != null) {
              return 1;
            }
            if (e1.getValue() != null && e2.getValue() == null) {
              return -1;
            }
            int len1 = e1.tokenOffsets.getEnd() - e1.tokenOffsets.getBegin();
            int len2 = e2.tokenOffsets.getEnd() - e2.tokenOffsets.getBegin();
            if (len1 == len2) {
              return 0;
            } else {
              return (len1 > len2)? -1:1;
            }
          };

  public static final Comparator<MatchedExpression> EXPR_TOKEN_OFFSET_COMPARATOR =
          (e1, e2) -> (e1.tokenOffsets.compareTo(e2.tokenOffsets));

  public static final Comparator<MatchedExpression> EXPR_TOKEN_OFFSETS_NESTED_FIRST_COMPARATOR =
          (e1, e2) -> {
            Interval.RelType rel = e1.tokenOffsets.getRelation(e2.tokenOffsets);
            if (rel.equals(Interval.RelType.CONTAIN)) {
              return 1;
            } else if (rel.equals(Interval.RelType.INSIDE)) {
              return -1;
            } else {
              return (e1.tokenOffsets.compareTo(e2.tokenOffsets));
            }
          };

  // Compares two matched expressions.
  // Use to order matched expressions by:
   //   score
  //    length (longest first), then whether it has value or not (has value first),
  //    original order
  //    and then beginning token offset (smaller offset first)
  public static final Comparator<MatchedExpression> EXPR_PRIORITY_LENGTH_COMPARATOR =
          Comparators.chain(EXPR_PRIORITY_COMPARATOR, EXPR_LENGTH_COMPARATOR,
                  EXPR_ORDER_COMPARATOR, EXPR_TOKEN_OFFSET_COMPARATOR);

  public static final Comparator<MatchedExpression> EXPR_LENGTH_PRIORITY_COMPARATOR =
          Comparators.chain(EXPR_LENGTH_COMPARATOR, EXPR_PRIORITY_COMPARATOR,
                  EXPR_ORDER_COMPARATOR, EXPR_TOKEN_OFFSET_COMPARATOR);

  public static final ToDoubleFunction<MatchedExpression> EXPR_WEIGHT_SCORER = in -> in.weight;

}
