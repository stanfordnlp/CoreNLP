package edu.stanford.nlp.semgraph.semgrex;

/**
 * How an attribute in a node description relates to the node's value.
 *<br>
 * The three modes differ chiefly in what they do about an attribute the
 * node doesn't have at all, which for a map attribute such as
 * morphofeatures is a common and interesting case:
 *<table>
 *  <caption>Modes</caption>
 *  <tr><th>          <th>missing      <th>present, matches <th>present, doesn't match
 *  <tr><td>REQUIRED  <td>no           <td>yes              <td>no
 *  <tr><td>NEGATED   <td>yes          <td>no               <td>yes
 *  <tr><td>OPTIONAL  <td>yes          <td>yes              <td>no
 *</table>
 * So {@code {morphofeatures:{PronType!:Prs}}} matches a node with no
 * PronType at all, whereas {@code {morphofeatures:{PronType?:Prs}}}
 * matches a node with no PronType or one which is Prs, but not one which
 * is Dem.
 *<br>
 * OPTIONAL is mostly useful with a variable group and a value of
 * {@code __}: {@code {morphofeatures:{PronType?:__#0%pron}}} matches
 * every node and binds pron only when the feature is there, which is
 * what counting over a feature wants so that the nodes without it are
 * still in the denominator.
 *
 * @author John Bauer
 */
public enum AttributeMode {
  REQUIRED(":"),
  NEGATED("!:"),
  OPTIONAL("?:");

  private final String separator;

  AttributeMode(String separator) {
    this.separator = separator;
  }

  /**
   * How this mode is written between an attribute's key and its value
   */
  public String separator() {
    return separator;
  }

  public static AttributeMode fromSeparator(String separator) {
    for (AttributeMode mode : values()) {
      if (mode.separator.equals(separator)) {
        return mode;
      }
    }
    throw new SemgrexParseException("Unknown attribute separator '" + separator + "'");
  }

  /**
   * Whether a node which doesn't have this attribute at all still matches
   */
  public boolean matchesMissing() {
    return this != REQUIRED;
  }

  /**
   * Whether the sense of a match against a present value is reversed
   */
  public boolean negated() {
    return this == NEGATED;
  }

  @Override
  public String toString() {
    return separator;
  }
}
