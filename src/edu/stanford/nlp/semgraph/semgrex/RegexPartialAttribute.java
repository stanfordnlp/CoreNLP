package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A constraint on a Map valued node annotation where the key is a regex
 * (or {@code __}), such as {@code morphofeatures:{/Number|Person/:Sing}}.
 * The constraint is checked against every entry whose key matches.
 */
public class RegexPartialAttribute implements Serializable {
  /** The name of the Map valued annotation to check */
  final String annotation;
  /** Pattern which a map key must fully match */
  final Pattern key;

  // TODO: separate these into two different classes?
  /** Pattern for the value when matching case sensitively, or null if the value is an exact string */
  final Pattern casedPattern;
  /** Pattern for the value when matching case insensitively, or null if the value is an exact string */
  final Pattern caselessPattern;
  /** The exact string the value must equal, or null if the value is a regex or {@code __} */
  final String exactMatch;

  /** Whether the constraint is required, negated, or optional */
  final AttributeMode mode;

  RegexPartialAttribute(String annotation, String key, String value, AttributeMode mode) {
    this.annotation = annotation;
    //System.out.println(annotation + " " + key + " " + value + " " + mode);
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

    this.mode = mode;
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
      // we treat an empty map as failing to match, so a negated or an
      // optional attribute passes
      return mode.matchesMissing();
    }

    // whether any key matched, regardless of its value.  needed to tell
    // "the map has no such key" from "it has one and the value is wrong",
    // which OPTIONAL treats differently and the other two do not
    boolean keyFound = false;

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      //System.out.println(key + " " + entry.getKey().toString() + " " + key.matcher(entry.getKey().toString()).matches());
      if (key.matcher(entry.getKey().toString()).matches()) {
        keyFound = true;
        String value = entry.getValue().toString();
        if (valueMatches(ignoreCase, value)) {
          return !mode.negated();
        }
      }
    }

    if (mode == AttributeMode.OPTIONAL) {
      return !keyFound;
    }
    return mode.negated();
  }

  private static final long serialVersionUID = 378257698196124612L;
}
