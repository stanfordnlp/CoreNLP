package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.ArrayMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

import java.util.*;

/**
* Functions for aggregating token attributes.
*
* @author Angel Chang
*/
public abstract class CoreMapAttributeAggregator
{
  public static Map<Class, CoreMapAttributeAggregator> getDefaultAggregators()
  {
    return DEFAULT_AGGREGATORS;
  }

  public static CoreMapAttributeAggregator getAggregator(String str)
  {
    return AGGREGATOR_LOOKUP.get(str);
  }

  public abstract Object aggregate(Class key, List<? extends CoreMap> in);

  public static final CoreMapAttributeAggregator FIRST_NON_NIL = new CoreMapAttributeAggregator() {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
        if (in == null) return null;
        for (CoreMap cm:in) {
          Object obj = cm.get(key);
          if (obj != null) {
            return obj;
          }
        }
        return null;
      }
    };

  public static final CoreMapAttributeAggregator FIRST = new CoreMapAttributeAggregator() {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
        if (in == null) return null;
        for (CoreMap cm:in) {
          Object obj = cm.get(key);
          return obj;
        }
        return null;
      }
    };

  public static final CoreMapAttributeAggregator LAST_NON_NIL = new CoreMapAttributeAggregator() {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
        if (in == null) return null;
        for (int i = in.size()-1; i >= 0; i--) {
          CoreMap cm = in.get(i);
          Object obj = cm.get(key);
          if (obj != null) {
            return obj;
          }
        }
        return null;
      }
    };

  public static final CoreMapAttributeAggregator LAST = new CoreMapAttributeAggregator() {
      public Object aggregate(Class key, List<? extends CoreMap> in) {
        if (in == null) return null;
        for (int i = in.size()-1; i >= 0; i--) {
          CoreMap cm = in.get(i);
          return cm.get(key);
        }
        return null;
      }
    };

  public static final class ConcatListAggregator<T> extends CoreMapAttributeAggregator {
    public ConcatListAggregator()
    {
    }
    @Override
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      List<T> res = new ArrayList<>();
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (obj instanceof List) {
            res.addAll( (List<T>) obj);
          }
        }
      }
      return res;
    }
  }
  public static final class ConcatCoreMapListAggregator<T extends CoreMap> extends CoreMapAttributeAggregator {
    boolean concatSelf = false;
    public ConcatCoreMapListAggregator()
    {
    }
    public ConcatCoreMapListAggregator(boolean concatSelf)
    {
      this.concatSelf = concatSelf;
    }
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      List<T> res = new ArrayList<>();
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        boolean added = false;
        if (obj != null) {
          if (obj instanceof List) {
            res.addAll( (List<T>) obj);
            added = true;
          }
        }
        if (!added && concatSelf) {
          res.add((T) cm);
        }
      }
      return res;
    }
  }
  public static final ConcatCoreMapListAggregator<CoreLabel> CONCAT_TOKENS = new ConcatCoreMapListAggregator<>(true);
  public static final ConcatCoreMapListAggregator<CoreMap> CONCAT_COREMAP = new ConcatCoreMapListAggregator<>(true);

  public static final class ConcatAggregator extends CoreMapAttributeAggregator {
    String delimiter;
    public ConcatAggregator(String delimiter)
    {
      this.delimiter = delimiter;
    }
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      StringBuilder sb = new StringBuilder();
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (sb.length() > 0) {
            sb.append(delimiter);
          }
          sb.append(obj);
        }
      }
      return sb.toString();
    }
  }
  public static final class ConcatTextAggregator extends CoreMapAttributeAggregator {
    String delimiter;
    public ConcatTextAggregator(String delimiter)
    {
      this.delimiter = delimiter;
    }
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      String text = ChunkAnnotationUtils.getTokenText(in, key);
      return text;
    }
  }
  public static final CoreMapAttributeAggregator CONCAT = new ConcatAggregator(" ");
  public static final CoreMapAttributeAggregator CONCAT_TEXT = new ConcatTextAggregator(" ");
  public static final CoreMapAttributeAggregator COUNT = new CoreMapAttributeAggregator() {
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      return in.size();
    }
  };
  public static final CoreMapAttributeAggregator SUM = new CoreMapAttributeAggregator() {
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      double sum = 0;
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (obj instanceof Number) {
            sum += ((Number) obj).doubleValue();
          } else if (obj instanceof String) {
            sum += Double.parseDouble((String) obj);
          } else {
            throw new RuntimeException("Cannot sum attribute " + key + ", object of type: " + obj.getClass());
          }
        }
      }
      return sum;
    }
  };
  public static final CoreMapAttributeAggregator MIN = new CoreMapAttributeAggregator() {
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      Comparable min = null;
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (obj instanceof Comparable) {
            Comparable c = (Comparable) obj;
            if (min == null) {
              min = c;
            } else if (c.compareTo(min) < 0) {
              min = c;
            }
          } else {
            throw new RuntimeException("Cannot get min of attribute " + key + ", object of type: " + obj.getClass());
          }
        }
      }
      return min;
    }
  };
  public static final CoreMapAttributeAggregator MAX = new CoreMapAttributeAggregator() {
    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      Comparable max = null;
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null) {
          if (obj instanceof Comparable) {
            Comparable c = (Comparable) obj;
            if (max == null) {
              max = c;
            } else if (c.compareTo(max) > 0) {
              max = c;
            }
          } else {
            throw new RuntimeException("Cannot get max of attribute " + key + ", object of type: " + obj.getClass());
          }
        }
      }
      return max;
    }
  };

  public static final class MostFreqAggregator extends CoreMapAttributeAggregator {
    Set<Object> ignoreSet;
    public MostFreqAggregator()
    {
    }

    public MostFreqAggregator(Set<Object> set)
    {
      ignoreSet = set;
    }

    public Object aggregate(Class key, List<? extends CoreMap> in) {
      if (in == null) return null;
      IntCounter<Object> counter = new IntCounter<>();
      for (CoreMap cm:in) {
        Object obj = cm.get(key);
        if (obj != null && (ignoreSet == null || !ignoreSet.contains(obj))) {
          counter.incrementCount(obj);
        }
      }
      if (counter.size() > 0) {
        return counter.argmax();
      } else {
        return null;
      }
    }
  }
  public static final CoreMapAttributeAggregator MOST_FREQ = new MostFreqAggregator();

  private static final Map<String, CoreMapAttributeAggregator> AGGREGATOR_LOOKUP = Generics.newHashMap();
  static {
    AGGREGATOR_LOOKUP.put("FIRST", FIRST);
    AGGREGATOR_LOOKUP.put("FIRST_NON_NIL", FIRST_NON_NIL);
    AGGREGATOR_LOOKUP.put("LAST", LAST);
    AGGREGATOR_LOOKUP.put("LAST_NON_NIL", LAST_NON_NIL);
    AGGREGATOR_LOOKUP.put("MIN", MIN);
    AGGREGATOR_LOOKUP.put("MAX", MAX);
    AGGREGATOR_LOOKUP.put("COUNT", COUNT);
    AGGREGATOR_LOOKUP.put("SUM", SUM);
    AGGREGATOR_LOOKUP.put("CONCAT", CONCAT);
    AGGREGATOR_LOOKUP.put("CONCAT_TEXT", CONCAT_TEXT);
    AGGREGATOR_LOOKUP.put("CONCAT_TOKENS", CONCAT_TOKENS);
    AGGREGATOR_LOOKUP.put("MOST_FREQ", MOST_FREQ);
  }

  public static final Map<Class, CoreMapAttributeAggregator> DEFAULT_AGGREGATORS;
  public static final Map<Class, CoreMapAttributeAggregator> DEFAULT_NUMERIC_AGGREGATORS;
  public static final Map<Class, CoreMapAttributeAggregator> DEFAULT_NUMERIC_TOKENS_AGGREGATORS;

  static {
    Map<Class, CoreMapAttributeAggregator> defaultAggr = new ArrayMap<>();
    defaultAggr.put(CoreAnnotations.TextAnnotation.class, CoreMapAttributeAggregator.CONCAT_TEXT);
    defaultAggr.put(CoreAnnotations.CharacterOffsetBeginAnnotation.class, CoreMapAttributeAggregator.FIRST);
    defaultAggr.put(CoreAnnotations.CharacterOffsetEndAnnotation.class, CoreMapAttributeAggregator.LAST);
    defaultAggr.put(CoreAnnotations.TokenBeginAnnotation.class, CoreMapAttributeAggregator.FIRST);
    defaultAggr.put(CoreAnnotations.TokenEndAnnotation.class, CoreMapAttributeAggregator.LAST);
    defaultAggr.put(CoreAnnotations.TokensAnnotation.class, CoreMapAttributeAggregator.CONCAT_TOKENS);
    defaultAggr.put(CoreAnnotations.BeforeAnnotation.class, CoreMapAttributeAggregator.FIRST);
    defaultAggr.put(CoreAnnotations.AfterAnnotation.class, CoreMapAttributeAggregator.LAST);
    DEFAULT_AGGREGATORS = Collections.unmodifiableMap(defaultAggr);

    Map<Class, CoreMapAttributeAggregator> defaultNumericAggr = new ArrayMap<>(DEFAULT_AGGREGATORS);
    defaultNumericAggr.put(CoreAnnotations.NumericCompositeTypeAnnotation.class, CoreMapAttributeAggregator.FIRST_NON_NIL);
    defaultNumericAggr.put(CoreAnnotations.NumericCompositeValueAnnotation.class, CoreMapAttributeAggregator.FIRST_NON_NIL);
    defaultNumericAggr.put(CoreAnnotations.NamedEntityTagAnnotation.class, CoreMapAttributeAggregator.FIRST_NON_NIL);
    defaultNumericAggr.put(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, CoreMapAttributeAggregator.FIRST_NON_NIL);
    DEFAULT_NUMERIC_AGGREGATORS = Collections.unmodifiableMap(defaultNumericAggr);

    Map<Class, CoreMapAttributeAggregator> defaultNumericTokensAggr = new ArrayMap<>(DEFAULT_NUMERIC_AGGREGATORS);
    defaultNumericTokensAggr.put(CoreAnnotations.NumerizedTokensAnnotation.class, CoreMapAttributeAggregator.CONCAT_COREMAP);
    DEFAULT_NUMERIC_TOKENS_AGGREGATORS = Collections.unmodifiableMap(defaultNumericTokensAggr);
  }


}
