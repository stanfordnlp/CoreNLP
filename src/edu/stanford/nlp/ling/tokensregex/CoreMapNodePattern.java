package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Pattern for matching a CoreMap
 *
 * @author Angel Chang
 */
public class CoreMapNodePattern extends ComplexNodePattern<CoreMap, Class> {

  private static BiFunction<CoreMap, Class, Object> createGetter() {
    return new BiFunction<CoreMap, Class, Object>() {
      @Override
      public Object apply(CoreMap m, Class k) {
        return m.get(k);
      }
    };
  }

  public CoreMapNodePattern(List<Pair<Class, NodePattern>> annotationPatterns) {
    super(createGetter(), annotationPatterns);
  }

  public CoreMapNodePattern(Pair<Class, NodePattern>... annotationPatterns) {
    super(createGetter(), annotationPatterns);
  }

  public CoreMapNodePattern(Class key, NodePattern pattern) {
    this(Pair.makePair(key,pattern));
  }

  public static CoreMapNodePattern valueOf(String textAnnotationPattern) {
    return valueOf(null, textAnnotationPattern);
  }

  public static CoreMapNodePattern valueOf(String textAnnotationPattern, int flags) {
    CoreMapNodePattern p = new CoreMapNodePattern(new ArrayList<Pair<Class, NodePattern>>(1));
    p.add(CoreAnnotations.TextAnnotation.class,
            newStringRegexPattern(textAnnotationPattern, flags));
    return p;
  }

  public static CoreMapNodePattern valueOf(Env env, String textAnnotationPattern) {
    CoreMapNodePattern p = new CoreMapNodePattern(new ArrayList<Pair<Class, NodePattern>>(1));
    p.add(CoreAnnotations.TextAnnotation.class,
            newStringRegexPattern(textAnnotationPattern, (env != null)? env.defaultStringPatternFlags: 0));
    return p;
  }

  public static CoreMapNodePattern valueOf(Pattern textAnnotationPattern) {
    CoreMapNodePattern p = new CoreMapNodePattern(new ArrayList<Pair<Class, NodePattern>>(1));
    p.add(CoreAnnotations.TextAnnotation.class,
            new StringAnnotationRegexPattern(textAnnotationPattern));
    return p;
  }

  public static CoreMapNodePattern valueOf(Map<String, String> attributes) {
    return valueOf(null, attributes);
  }

  public static CoreMapNodePattern valueOf(Env env, Map<String, String> attributes) {
    CoreMapNodePattern p = new CoreMapNodePattern(new ArrayList<Pair<Class,NodePattern>>(attributes.size()));
    p.populate(env, attributes, envAttrPair -> EnvLookup.lookupAnnotationKey(envAttrPair.first, envAttrPair.second));
    return p;
  }

  public static final AttributesEqualMatchChecker TEXT_ATTR_EQUAL_CHECKER =
          new AttributesEqualMatchChecker(CoreAnnotations.TextAnnotation.class);
}
