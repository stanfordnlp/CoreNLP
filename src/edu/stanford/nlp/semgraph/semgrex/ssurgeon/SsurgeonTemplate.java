package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;

/**
 * A value in an Ssurgeon edit which may refer to the variables of the
 * match, such as {@code ${stem}ar}.
 *<br>
 * {@code ${name}} is replaced with the variable's string, which comes
 * from an edit earlier in the script, or from a regex group the semgrex
 * pattern captured, such as {@code {lemma:/(.*)ir/#1%stem}}.
 * {@code $$} is a literal {@code $}, so {@code $${name}} is the text
 * {@code ${name}}.  Any other {@code $} is literal as well, so a value
 * such as {@code US$} needs no escaping.
 *<br>
 * The value is parsed once, when the edit is read, so a value with no
 * variables costs nothing when the edit is applied.
 */
final class SsurgeonTemplate {
  /** The value as written in the edit, escapes and all */
  final String text;
  /** The literal pieces, one more than the variables, with escapes removed */
  private final List<String> literals;
  /** The variables, each between two literal pieces */
  private final List<String> variables;

  /**
   * Parses a value which may refer to variables.
   *
   * @param text the value as written in the edit
   * @throws SsurgeonParseException if a {@code ${} has no closing brace, or names no variable
   */
  SsurgeonTemplate(String text) {
    this.text = text;
    List<String> literals = new ArrayList<>();
    List<String> variables = new ArrayList<>();
    StringBuilder literal = new StringBuilder();
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '$') {
        literal.append('$');
        i += 2;
      } else if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
        int close = text.indexOf('}', i + 2);
        if (close < 0) {
          throw new SsurgeonParseException("No closing } for the variable at position " + i + " of |" + text + "|.  Write $$ for a literal $");
        }
        String name = text.substring(i + 2, close);
        if (name.isEmpty()) {
          throw new SsurgeonParseException("Empty variable name ${} in |" + text + "|");
        }
        literals.add(literal.toString());
        literal.setLength(0);
        variables.add(name);
        i = close + 1;
      } else {
        literal.append(c);
        ++i;
      }
    }
    literals.add(literal.toString());
    this.literals = Collections.unmodifiableList(literals);
    this.variables = Collections.unmodifiableList(variables);
  }

  /** Whether the value refers to any variables */
  boolean hasVariables() {
    return !variables.isEmpty();
  }

  /**
   * The value with its escapes removed, for a value with no variables.
   *
   * @throws IllegalStateException if the value refers to variables
   */
  String literal() {
    if (hasVariables()) {
      throw new IllegalStateException("|" + text + "| refers to variables, so it has no fixed value");
    }
    return literals.get(0);
  }

  /**
   * The value with each variable replaced by its string in the current match.
   *
   * @param sm the match, with its named nodes, edges, and variables
   * @return the value, or null if any variable it refers to is not set
   */
  String expand(SemgrexMatch sm) {
    if (!hasVariables()) {
      return literals.get(0);
    }
    StringBuilder value = new StringBuilder(literals.get(0));
    for (int i = 0; i < variables.size(); ++i) {
      String variable = sm.getVariableString(variables.get(i));
      if (variable == null) {
        return null;
      }
      value.append(variable);
      value.append(literals.get(i + 1));
    }
    return value.toString();
  }

  /**
   * Parses each attribute value of an edit, and checks the attributes
   * the way {@link AddDep#checkIllegalAttributes} does.  An attribute
   * whose value refers to variables only has its name checked, as its
   * value is not known until the edit is applied.
   *
   * @param attributes the attribute names and values as written in the edit
   * @return the parsed values, by attribute name
   * @throws SsurgeonParseException if a value is malformed, or an attribute cannot be set
   */
  static Map<String, SsurgeonTemplate> attributes(Map<String, String> attributes) {
    Map<String, SsurgeonTemplate> templates = new TreeMap<>();
    Map<String, String> literals = new TreeMap<>();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      SsurgeonTemplate template = new SsurgeonTemplate(entry.getValue());
      templates.put(entry.getKey(), template);
      if (template.hasVariables()) {
        AddDep.checkAttributeNames(Collections.singleton(entry.getKey()));
        if (AnnotationLookup.toCoreKey(entry.getKey()) == null) {
          throw new SsurgeonParseException("Unknown attribute |" + entry.getKey() + "| for the value |" + entry.getValue() + "|");
        }
      } else {
        literals.put(entry.getKey(), template.literal());
      }
    }
    AddDep.checkIllegalAttributes(literals);
    return templates;
  }

  /**
   * Each attribute's value in the current match.
   *
   * @param templates the parsed values, by attribute name
   * @param sm the match, with its named nodes, edges, and variables
   * @return the values, by attribute name, or null if any value refers to a variable which is not set
   */
  static Map<String, String> expand(Map<String, SsurgeonTemplate> templates, SemgrexMatch sm) {
    Map<String, String> values = new TreeMap<>();
    for (Map.Entry<String, SsurgeonTemplate> entry : templates.entrySet()) {
      String value = entry.getValue().expand(sm);
      if (value == null) {
        return null;
      }
      values.put(entry.getKey(), value);
    }
    return values;
  }
}
