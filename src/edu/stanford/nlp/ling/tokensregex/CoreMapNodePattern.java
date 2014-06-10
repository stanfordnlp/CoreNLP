package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.ArrayMap;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern for matching a CoreMap
 *
 * @author Angel Chang
 */
public class CoreMapNodePattern extends NodePattern<CoreMap> {
  // TODO: Change/Augment from map of class to pattern to list of conditions for matching
  //       (so we can do matches over multiple fields)
  private final Map<Class, NodePattern> annotationPatterns;


  public CoreMapNodePattern(Map<Class, NodePattern> annotationPatterns) {
    this.annotationPatterns = annotationPatterns;
  }

  public static CoreMapNodePattern valueOf(String textAnnotationPattern) {
    return valueOf(null, textAnnotationPattern);
  }

  public static CoreMapNodePattern valueOf(Env env, String textAnnotationPattern) {
    CoreMapNodePattern p = new CoreMapNodePattern(new ArrayMap<Class, NodePattern>(1));
    p.annotationPatterns.put(CoreAnnotations.TextAnnotation.class,
            new StringAnnotationRegexPattern(textAnnotationPattern, (env != null)? env.defaultStringPatternFlags: 0));
    return p;
  }

  public static CoreMapNodePattern valueOf(Map<String, String> attributes) {
    return valueOf(null, attributes);
  }

  public static CoreMapNodePattern valueOf(Env env, Map<String, String> attributes) {
    CoreMapNodePattern p = new CoreMapNodePattern(new ArrayMap<Class,NodePattern>(attributes.size()));
    for (String attr:attributes.keySet()) {
      String value = attributes.get(attr);
      Class c = EnvLookup.lookupAnnotationKey(env, attr);
      if (c != null) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
          value = value.substring(1, value.length()-1);
          value = value.replaceAll("\\\\\"", "\""); // Unescape quotes...
          p.annotationPatterns.put(c, new StringAnnotationPattern(value));
        } else if (value.startsWith("/") && value.endsWith("/")) {
          value = value.substring(1, value.length()-1);
          value = value.replaceAll("\\\\/", "/"); // Unescape forward slash
//          p.annotationPatterns.put(c, new StringAnnotationRegexPattern(value, (env != null)? env.defaultStringPatternFlags: 0));
          p.annotationPatterns.put(c, new StringAnnotationRegexPattern((env != null)? env.getStringPattern(value): Pattern.compile(value)));
        } else if (value.startsWith("::")) {
          if (value.equals("::IS_NIL") || value.equals("::NOT_EXISTS")) {
            p.annotationPatterns.put(c, new NilAnnotationPattern());
          } else if (value.equals("::EXISTS") || value.equals("::NOT_NIL")) {
            p.annotationPatterns.put(c, new NotNilAnnotationPattern());
          } else if (value.equals("::IS_NUM")) {
            p.annotationPatterns.put(c, new NumericAnnotationPattern(0, NumericAnnotationPattern.CmpType.IS_NUM));
          } else {
            boolean ok = false;
            if (env != null) {
              Object custom = env.get(value);
              if (custom != null) {
                p.annotationPatterns.put(c, (NodePattern) custom);
                ok = true;
              }
            }
            if (!ok) {
              throw new IllegalArgumentException("Invalid value " + value + " for key: " + attr);
            }
          }
        } else if (value.startsWith("<=")) {
          Double v = Double.parseDouble(value.substring(2));
          p.annotationPatterns.put(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.LE));
        } else if (value.startsWith(">=")) {
          Double v = Double.parseDouble(value.substring(2));
          p.annotationPatterns.put(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.GE));
        } else if (value.startsWith("==")) {
          Double v = Double.parseDouble(value.substring(2));
          p.annotationPatterns.put(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.EQ));
        } else if (value.startsWith("!=")) {
          Double v = Double.parseDouble(value.substring(2));
          p.annotationPatterns.put(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.NE));
        } else if (value.startsWith(">")) {
          Double v = Double.parseDouble(value.substring(1));
          p.annotationPatterns.put(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.GT));
        } else if (value.startsWith("<")) {
          Double v = Double.parseDouble(value.substring(1));
          p.annotationPatterns.put(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.LT));
        } else if (value.matches("[A-Za-z0-9_+-.]+")) {
          p.annotationPatterns.put(c, new StringAnnotationPattern(value));
        } else {
          throw new IllegalArgumentException("Invalid value " + value + " for key: " + attr);
        }
      } else {
        throw new IllegalArgumentException("Unknown annotation key: " + attr);
      }
    }
    return p;
  }

  @Override
  public boolean match(CoreMap token)
  {
    boolean matched = true;
    for (Map.Entry<Class,NodePattern> entry:annotationPatterns.entrySet()) {
      NodePattern annoPattern = entry.getValue();
      Object anno = token.get(entry.getKey());
      if (!annoPattern.match(anno)) {
        matched = false;
        break;
      }
    }
    return matched;
  }

  @Override
  public Object matchWithResult(CoreMap token) {
    Map<Class,Object> matchResults = Generics.newHashMap();
    if (match(token, matchResults)) {
      return matchResults;
    } else {
      return null;
    }
  }

  // Does matching, returning match results
  protected boolean match(CoreMap token, Map<Class,Object> matchResults)
  {
    boolean matched = true;
    for (Map.Entry<Class,NodePattern> entry:annotationPatterns.entrySet()) {
      NodePattern annoPattern = entry.getValue();
      Object anno = token.get(entry.getKey());
      Object matchResult = annoPattern.matchWithResult(anno);
      if (matchResult != null) {
        matchResults.put(entry.getKey(), matchResult);
      } else {
        matched = false;
        break;
      }
    }
    return matched;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Class k:annotationPatterns.keySet()) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(k).append(annotationPatterns.get(k));
    }
    return sb.toString();
  }

  public static class NilAnnotationPattern extends NodePattern<Object> {
    public boolean match(Object obj) {
      return obj == null;
    }
    public String toString() {
      return "::IS_NIL";
    }
  }

  public static class NotNilAnnotationPattern extends NodePattern<Object> {
    public boolean match(Object obj) {
      return obj != null;
    }
    public String toString() {
      return "::NOT_NIL";
    }
  }

  public static class SequenceRegexPattern<T> extends NodePattern<List<T>> {
    SequencePattern<T> pattern;

    public SequenceRegexPattern(SequencePattern<T> pattern) {
      this.pattern = pattern;
    }

    public SequencePattern<T> getPattern() {
      return pattern;
    }

    public SequenceMatcher<T> matcher(List<T> list) {
      return pattern.getMatcher(list);
    }

    public boolean match(List<T> list) {
      return pattern.getMatcher(list).matches();
    }

    public Object matchWithResult(List<T> list) {
      SequenceMatcher<T> m = pattern.getMatcher(list);
      if (m.matches()) {
        return m.toBasicSequenceMatchResult();
      } else {
        return null;
      }
    }

    public String toString() {
      return ":" + pattern.toString();
    }
  }

  public static class StringAnnotationRegexPattern extends NodePattern<String> {
    Pattern pattern;

    public StringAnnotationRegexPattern(Pattern pattern) {
      this.pattern = pattern;
    }

    public StringAnnotationRegexPattern(String regex, int flags) {
      this.pattern = Pattern.compile(regex, flags);
    }

    public Pattern getPattern() {
      return pattern;
    }

    public Matcher matcher(String str) {
      return pattern.matcher(str);
    }

    public boolean match(String str) {
      if (str == null) {
        return false;
      } else {
        return pattern.matcher(str).matches();
      }
    }

    public Object matchWithResult(String str) {
      Matcher m = pattern.matcher(str);
      if (m.matches()) {
        return m.toMatchResult();
      } else {
        return null;
      }
    }

    public String toString() {
      return ":/" + pattern.pattern() + "/";
    }
  }

  public static class StringAnnotationPattern extends NodePattern<String> {
    String target;
    boolean ignoreCase;

    public StringAnnotationPattern(String str, boolean ignoreCase) {
      this.target = str;
      this.ignoreCase = ignoreCase;
    }

    public StringAnnotationPattern(String str) {
      this.target = str;
    }

    public String getString() {
      return target;
    }

    public boolean match(String str) {
      if (ignoreCase) {
        return target.equalsIgnoreCase(str);
      } else {
        return target.equals(str);
      }
    }

    public String toString() {
      return ":" + target;
    }
  }

  public static class NumericAnnotationPattern extends NodePattern<Object> {
    static enum CmpType {
      IS_NUM { boolean accept(double v1, double v2) { return true; } },
      EQ { boolean accept(double v1, double v2) { return v1 == v2; } },   // TODO: equal with doubles is not so good
      NE { boolean accept(double v1, double v2) { return v1 != v2; } },   // TODO: equal with doubles is not so good
      GT { boolean accept(double v1, double v2) { return v1 > v2; } },
      GE { boolean accept(double v1, double v2) { return v1 >= v2; } },
      LT { boolean accept(double v1, double v2) { return v1 < v2; } },
      LE { boolean accept(double v1, double v2) { return v1 <= v2; } };
      boolean accept(double v1, double v2) { return false; }
    }
    CmpType cmpType;
    double value;

    public NumericAnnotationPattern(double value, CmpType cmpType) {
      this.value = value;
      this.cmpType = cmpType;
    }

    @Override
    public boolean match(Object node) {
      if (node instanceof String) {
        return match((String) node);
      } else if (node instanceof Number) {
        return match((Number) node);
      } else {
        return false;
      }
    }

    public boolean match(Number number) {
      if (number != null) {
        return cmpType.accept(number.doubleValue(), value);
      } else {
        return false;
      }
    }

    public boolean match(String str) {
      if (str != null) {
        try {
          double v = Double.parseDouble(str);
          return cmpType.accept(v, value);
        } catch (NumberFormatException ex) {
        }
      }
      return false;
    }

    public String toString() {
      return " " + cmpType + " " + value;
    }
  }

  public static class AttributesEqualMatchChecker implements SequencePattern.NodesMatchChecker<CoreMap> {
    Collection<Class> keys;

    public AttributesEqualMatchChecker(Class... classes) {
      keys = CollectionUtils.asSet(classes);
    }

    public boolean matches(CoreMap o1, CoreMap o2) {
      for (Class key : keys) {
        Object v1 = o1.get(key);
        Object v2 = o2.get(key);
        if (v1 != null) {
          if (!v1.equals(v2)) {
            return false;
          }
        } else {
          if (v2 != null) return false;
        }
      }
      return true;
    }
  }

  public static final AttributesEqualMatchChecker TEXT_ATTR_EQUAL_CHECKER =
          new AttributesEqualMatchChecker(CoreAnnotations.TextAnnotation.class);

}
