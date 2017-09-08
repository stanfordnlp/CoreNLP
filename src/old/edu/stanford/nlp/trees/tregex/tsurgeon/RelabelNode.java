package old.edu.stanford.nlp.trees.tregex.tsurgeon;

import old.edu.stanford.nlp.trees.Tree;
import old.edu.stanford.nlp.trees.tregex.TregexMatcher;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class RelabelNode extends TsurgeonPattern {

  private static final Pattern regexPattern = Pattern.compile("/(.*)/");
  private static final Pattern substPattern = Pattern.compile("/(.*[^\\\\])/(.*)/");

  private final boolean fixedNewLabel; // = false;
  private String newLabel;

  private Pattern labelRegex;
  private String replacementString;

  public RelabelNode(TsurgeonPattern child, String newLabel) {
    super("relabel", new TsurgeonPattern[] { child });
    Matcher m1 = substPattern.matcher(newLabel);
    if (m1.matches()) {
      // substitution pattern
      fixedNewLabel = false;
      this.labelRegex = Pattern.compile(m1.group(1));
      this.replacementString = m1.group(2);
    } else {
      fixedNewLabel = true;
      Matcher m2 = regexPattern.matcher(newLabel);
      if (m2.matches()) {
        // fixed relabel but quoted in regex slashes
        String unescapedLabel = m2.group(1);
        this.newLabel = removeEscapeSlashes(unescapedLabel);
      } else {
        // just a node name to relabel to
        this.newLabel = newLabel;
      }
    }
  }

  private static String removeEscapeSlashes(String in) {
    StringBuilder out = new StringBuilder();
    int len = in.length();
    boolean lastIsBackslash = false;
    for (int i = 0; i < len; i++) {
      char ch = in.charAt(i);
      if (ch == '\\') {
        if (lastIsBackslash || i == len - 1 ) {
          out.append(ch);
          lastIsBackslash = false;
        } else {
          lastIsBackslash = true;
        }
      } else {
        out.append(ch);
        lastIsBackslash = false;
      }
    }
    return out.toString();
  }


  @Override
  public Tree evaluate(Tree t, TregexMatcher tm) {
    Tree nodeToRelabel = children[0].evaluate(t, tm);
    if (fixedNewLabel) {
      nodeToRelabel.label().setValue(newLabel);
    } else {
      Matcher m = labelRegex.matcher(nodeToRelabel.label().value());
      nodeToRelabel.label().setValue(m.replaceAll(replacementString));
    }
    return t;
  }

  @Override
  public String toString() {
    String result;
    if (fixedNewLabel) {
      result =  label + '(' + children[0].toString() + ',' + newLabel + ')';
    } else {
      result = label + '(' + children[0].toString() + ',' + labelRegex.toString() + ',' + replacementString + ')';
    }
    return result;
  }

}
