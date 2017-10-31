package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern for matching a complex data structure
 *
 * @author Angel Chang
 */
public class ComplexNodePattern<M,K> extends NodePattern<M> {
  // TODO: Change/Augment from list of class to pattern to list of conditions for matching
  //       (so we can have more flexible matches)
  private final List<Pair<K, NodePattern>> annotationPatterns;
  private final BiFunction<M,K, Object> getter;


  public ComplexNodePattern(BiFunction<M,K, Object> getter, List<Pair<K, NodePattern>> annotationPatterns) {
    this.annotationPatterns = annotationPatterns;
    this.getter = getter;
  }

  public ComplexNodePattern(BiFunction<M,K, Object> getter, Pair<K, NodePattern>... annotationPatterns) {
    this.annotationPatterns = Arrays.asList(annotationPatterns);
    this.getter = getter;
  }

  public ComplexNodePattern(BiFunction<M,K, Object> getter, K key, NodePattern pattern) {
    this(getter, Pair.makePair(key,pattern));
  }

  public List<Pair<K, NodePattern>> getAnnotationPatterns() {
    return Collections.unmodifiableList(annotationPatterns);
  }

  // TODO: make this a pattern of non special characters: [,],?,.,\,^,$,(,),*,+,{,},| ... what else?
  private static final Pattern LITERAL_PATTERN = Pattern.compile("[^\\[\\]?.\\\\^$()*+{}|]*");
  //private static final Pattern LITERAL_PATTERN = Pattern.compile("[A-Za-z0-9_\\-']*");
  public static NodePattern<String> newStringRegexPattern(String regex, int flags) {
    boolean isLiteral = ((flags & Pattern.LITERAL) != 0) || LITERAL_PATTERN.matcher(regex).matches();
    if (isLiteral) {
      boolean caseInsensitive = (flags & (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)) != 0;
      int stringMatchFlags = (caseInsensitive)? (CASE_INSENSITIVE | UNICODE_CASE):0;
      return new StringAnnotationPattern(regex, stringMatchFlags);
    } else {
      return new StringAnnotationRegexPattern(regex, flags);
    }
  }

  public static <M,K> ComplexNodePattern valueOf(
      Env env, Map<String, String> attributes, BiFunction<M,K,Object> getter, Function<Pair<Env,String>,K> getKey)
  {
    ComplexNodePattern<M,K> p = new ComplexNodePattern<>(getter, new ArrayList<>(attributes.size()));
    p.populate(env, attributes, getKey);
    return p;
  }

  protected void populate(Env env, Map<String, String> attributes, Function<Pair<Env,String>,K> getKey) {
    ComplexNodePattern<M,K> p = this;
    for (String attr:attributes.keySet()) {
      String value = attributes.get(attr);
      K c = getKey.apply(Pair.makePair(env, attr));
      if (c != null) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
          value = value.substring(1, value.length() - 1);
          value = value.replaceAll("\\\\\"", "\""); // Unescape quotes...
          p.add(c, new StringAnnotationPattern(value, env.defaultStringMatchFlags));
        } else if (value.startsWith("/") && value.endsWith("/")) {
          value = value.substring(1, value.length() - 1);
          value = value.replaceAll("\\\\/", "/"); // Unescape forward slash
          String regex = (env != null) ? env.expandStringRegex(value) : value;
          int flags = (env != null) ? env.defaultStringPatternFlags : 0;
          p.add(c, newStringRegexPattern(regex, flags));
        } else if (value.startsWith("::")) {
          switch (value) {
            case "::IS_NIL":
            case "::NOT_EXISTS":
              p.add(c, new NilAnnotationPattern());
              break;
            case "::EXISTS":
            case "::NOT_NIL":
              p.add(c, new NotNilAnnotationPattern());
              break;
            case "::IS_NUM":
              p.add(c, new NumericAnnotationPattern(0, NumericAnnotationPattern.CmpType.IS_NUM));
              break;
            default:
              boolean ok = false;
              if (env != null) {
                Object custom = env.get(value);
                if (custom != null) {
                  p.add(c, (NodePattern) custom);
                  ok = true;
                }
              }
              if (!ok) {
                throw new IllegalArgumentException("Invalid value " + value + " for key: " + attr);
              }
              break;
          }
        } else if (value.startsWith("<=")) {
          Double v = Double.parseDouble(value.substring(2));
          p.add(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.LE));
        } else if (value.startsWith(">=")) {
          Double v = Double.parseDouble(value.substring(2));
          p.add(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.GE));
        } else if (value.startsWith("==")) {
          Double v = Double.parseDouble(value.substring(2));
          p.add(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.EQ));
        } else if (value.startsWith("!=")) {
          Double v = Double.parseDouble(value.substring(2));
          p.add(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.NE));
        } else if (value.startsWith(">")) {
          Double v = Double.parseDouble(value.substring(1));
          p.add(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.GT));
        } else if (value.startsWith("<")) {
          Double v = Double.parseDouble(value.substring(1));
          p.add(c, new NumericAnnotationPattern(v, NumericAnnotationPattern.CmpType.LT));
        } else if (value.matches("[A-Za-z0-9_+-.]+")) {
          p.add(c, new StringAnnotationPattern(value, env.defaultStringMatchFlags));
        } else {
          throw new IllegalArgumentException("Invalid value " + value + " for key: " + attr);
        }
      } else {
        throw new IllegalArgumentException("Unknown annotation key: " + attr);
      }
    }
  }

  public void add(K c, NodePattern pattern) {
    annotationPatterns.add(Pair.makePair(c, pattern));
  }

  @Override
  public boolean match(M token)
  {
    boolean matched = true;
    for (Pair<K,NodePattern> entry:annotationPatterns) {
      NodePattern annoPattern = entry.second;
      Object anno = getter.apply(token, entry.first);
      if (!annoPattern.match(anno)) {
        matched = false;
        break;
      }
    }
    return matched;
  }

  @Override
  public Object matchWithResult(M token) {
    Map<K,Object> matchResults = new HashMap<>();//Generics.newHashMap();
    if (match(token, matchResults)) {
      return matchResults;
    } else {
      return null;
    }
  }

  // Does matching, returning match results
  protected boolean match(M token, Map<K,Object> matchResults)
  {
    boolean matched = true;
    for (Pair<K,NodePattern> entry:annotationPatterns) {
      NodePattern annoPattern = entry.second;
      Object anno = getter.apply(token, entry.first);
      Object matchResult = annoPattern.matchWithResult(anno);
      if (matchResult != null) {
        matchResults.put(entry.first, matchResult);
      } else {
        matched = false;
        break;
      }
    }
    return matched;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Pair<K,NodePattern> entry:annotationPatterns) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(entry.first).append(entry.second);
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
      if (str == null) return null;
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

  public static abstract class AbstractStringAnnotationPattern extends NodePattern<String> {
    int flags;

    public boolean ignoreCase() {
      return (flags & (CASE_INSENSITIVE | UNICODE_CASE)) != 0;
    }

    public boolean normalize() {
      return (flags & NORMALIZE) != 0;
    }

    public String getNormalized(String str) {
      if (normalize()) {
        str = StringUtils.normalize(str);
      }
      if (ignoreCase()) {
        str = str.toLowerCase();
      }
      return str;
    }
  }

  public static class StringAnnotationPattern extends AbstractStringAnnotationPattern {
    String target;

    public StringAnnotationPattern(String str, int flags) {
      this.target = str;
      this.flags = flags;
    }

    public StringAnnotationPattern(String str) {
      this.target = str;
    }

    public String getString() {
      return target;
    }

    public boolean match(String str) {
      if (normalize()) {
        str = getNormalized(str);
      }
      if (ignoreCase()) {
        return target.equalsIgnoreCase(str);
      } else {
        return target.equals(str);
      }
    }

    public String toString() {
      return ":" + target;
    }
  }

  public static class StringInSetAnnotationPattern extends AbstractStringAnnotationPattern {
    Set<String> targets;

    public StringInSetAnnotationPattern(Set<String> targets, int flags) {
      this.flags = flags;
      // if ignoreCase/normalize is true - convert targets to lowercase/normalized
      this.targets = new HashSet<>(targets.size());
      for (String target:targets) {
        this.targets.add(getNormalized(target));
      }
    }

    public StringInSetAnnotationPattern(Set<String> targets) {
      this(targets, 0);
    }

    public Set<String> getTargets() {
      return targets;
    }

    public boolean match(String str) {
      return targets.contains(getNormalized(str));
    }

    public String toString() {
      return ":" + targets;
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

  public static class AttributesEqualMatchChecker<K> implements SequencePattern.NodesMatchChecker<Map<K,Object>> {
    Collection<K> keys;

    public AttributesEqualMatchChecker(K... keys) {
      this.keys = CollectionUtils.asSet(keys);
    }

    public boolean matches(Map<K,Object> o1, Map<K,Object> o2) {
      for (K key : keys) {
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

  //For exact matching integers. Presumably faster than NumericAnnotationPattern
  //TODO : add this in the valueOf function of MapNodePattern
  public static class IntegerAnnotationPattern extends NodePattern<Integer>{

    int value;
    public IntegerAnnotationPattern(int v){
      this.value = v;
    }

    @Override
    public boolean match(Integer node) {
      return value == node;
    }

    public int getValue() {
      return value;
    }
  }

}
