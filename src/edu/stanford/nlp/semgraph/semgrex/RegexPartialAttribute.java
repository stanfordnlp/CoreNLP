package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;
import java.util.Map;
import java.util.regex.Pattern;

public class RegexPartialAttribute implements Serializable {
  final String annotation;
  final Pattern key;

  // TODO: separate these into two different classes?
  final Pattern casedPattern;
  final Pattern caselessPattern;
  final String exactMatch;

  final boolean negated;

  RegexPartialAttribute(String annotation, String key, String value, boolean negated) {
    this.annotation = annotation;
    //System.out.println(annotation + " " + key + " " + value + " " + negated);
    String keyContent = key.substring(1, key.length() - 1);
    this.key = Pattern.compile(keyContent);

    if (value.equals("__")) {
      casedPattern = Pattern.compile(".*");
      caselessPattern = Pattern.compile(".*");
      exactMatch = null;
    } else if (value.matches("/.*/")) {
      String patternContent = value.substring(1, value.length() - 1);
      casedPattern = Pattern.compile(patternContent);
      caselessPattern = Pattern.compile(patternContent, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
      exactMatch = null;
    } else {
      casedPattern = null;
      caselessPattern = null;
      exactMatch = value;
    }

    this.negated = negated;
  }

  boolean valueMatches(boolean ignoreCase, String value) {
    if (ignoreCase) {
      return caselessPattern == null ? value.equalsIgnoreCase(exactMatch.toString()) : caselessPattern.matcher(value).matches();
    } else {
      return casedPattern == null ? value.equals(exactMatch.toString()) : casedPattern.matcher(value).matches();
    }
  }

  boolean checkMatches(Map<?, ?> map, boolean ignoreCase) {
    //System.out.println("CHECKING MATCHES");
    //System.out.println(map);
    if (map == null) {
      // we treat an empty map as failing to match
      // so if the attribute is negated, that means this attribute passes
      return negated;
    }

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      //System.out.println(key + " " + entry.getKey().toString() + " " + key.matcher(entry.getKey().toString()).matches());
      if (key.matcher(entry.getKey().toString()).matches()) {
        String value = entry.getValue().toString();
        if (valueMatches(ignoreCase, value)) {
          return !negated;
        }
      }
    }

    return negated;
  }

  private static final long serialVersionUID = 378257698196124612L;
}
