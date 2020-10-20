package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.types.Value;
import edu.stanford.nlp.pipeline.CoreMapAggregator;
import edu.stanford.nlp.pipeline.CoreMapAttributeAggregator;
import java.util.function.Function;

import java.util.List;
import java.util.Map;

/**
 * Provides lookup functions using an Env
 *
 * @author Angel Chang
 */
public class EnvLookup {

  private EnvLookup() {} // static methods

  // TODO: For additional keys, read map of name to Class from file???
  public static Class lookupAnnotationKey(Env env, String name)
  {
    if (env != null) {
      Object obj = env.get(name);
      if (obj != null) {
        if (obj instanceof Class) {
          return (Class) obj;
        } else if (obj instanceof Value) {
          obj = ((Value) obj).get();
          if (obj instanceof Class) {
            return (Class) obj;
          }
        }
      }
    }
    return AnnotationLookup.toCoreKey(name);
  }

  public static Class lookupAnnotationKeyWithClassname(Env env, String name) {
    Class annotationKey = lookupAnnotationKey(env, name);
    if (annotationKey == null) {
      try {
        Class clazz = Class.forName(name);
        return clazz;
      } catch (ClassNotFoundException ex) {
      }
      return null;
    } else {
      return annotationKey;
    }
  }

  public static Map<Class, CoreMapAttributeAggregator> getDefaultTokensAggregators(Env env)
  {
    if (env != null) {
      Map<Class, CoreMapAttributeAggregator> obj = env.getDefaultTokensAggregators();
      if (obj != null) {
        return obj;
      }
    }
    return CoreMapAttributeAggregator.DEFAULT_NUMERIC_TOKENS_AGGREGATORS;
  }

  public static CoreMapAggregator getDefaultTokensAggregator(Env env)
  {
    if (env != null) {
      CoreMapAggregator obj = env.getDefaultTokensAggregator();
      if (obj != null) {
        return obj;
      }
    }
    return CoreMapAggregator.DEFAULT_NUMERIC_TOKENS_AGGREGATOR;
  }

  public static List<Class> getDefaultTokensResultAnnotationKey(Env env)
  {
    if (env != null) {
      List<Class> obj = env.getDefaultTokensResultAnnotationKey();
      if (obj != null) {
        return obj;
      }
    }
    return null;
  }

  public static List<Class> getDefaultResultAnnotationKey(Env env)
  {
    if (env != null) {
      List<Class> obj = env.getDefaultResultAnnotationKey();
      if (obj != null) {
        return obj;
      }
    }
    return null;
  }

  public static Function<MatchedExpression,?> getDefaultResultAnnotationExtractor(Env env)
  {
    if (env != null) {
      Function<MatchedExpression,?> obj = env.getDefaultResultsAnnotationExtractor();
      if (obj != null) {
        return obj;
      }
    }
    return null;
  }

  public static Class getDefaultNestedResultsAnnotationKey(Env env)
  {
    if (env != null) {
      Class obj = env.getDefaultNestedResultsAnnotationKey();
      if (obj != null) {
        return obj;
      }
    }
    return null;
  }

  public static Class getDefaultTextAnnotationKey(Env env)
  {
    if (env != null) {
      Class obj = env.getDefaultTextAnnotationKey();
      if (obj != null) {
        return obj;
      }
    }
    return CoreAnnotations.TextAnnotation.class;
  }

  public static Class getDefaultTokensAnnotationKey(Env env)
  {
    if (env != null) {
      Class obj = env.getDefaultTokensAnnotationKey();
      if (obj != null) {
        return obj;
      }
    }
    return CoreAnnotations.TokensAnnotation.class;
  }

}
