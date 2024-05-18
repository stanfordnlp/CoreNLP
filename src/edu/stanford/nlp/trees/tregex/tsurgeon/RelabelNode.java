package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class RelabelNode extends TsurgeonPattern {

  // Overly complicated pattern to identify regexes surrounded by /,
  // possibly with / escaped inside the regex.  
  // The purpose of the [^/]*[^/\\\\] is to match characters that
  // aren't / and to allow escaping of other characters.
  // The purpose of the \\\\/ is to allow escaped / inside the pattern.
  // The purpose of the \\\\\\\\ is to allow escaped \ at the end of
  // the pattern, so you can match, for example, /\\/.  There need to
  // be 8x\ because both java and regexes need escaping, resulting in 4x.
  static final String regexPatternString = 
    "((?:(?:[^/]*[^/\\\\])|\\\\/)*(?:\\\\\\\\)*)";

  static final Pattern regexPattern = 
    Pattern.compile("/" + regexPatternString + "/");

  /**
   * This pattern finds relabel snippets that use a named node.
   */
  static final String nodePatternString = "(=\\{[a-zA-Z0-9_]+\\})";
  static final Pattern nodePattern = Pattern.compile(nodePatternString);
  /**
   * This pattern finds relabel snippets that use a captured variable.
   */
  static final String variablePatternString = "(%\\{[a-zA-Z0-9_]+\\})";
  static final Pattern variablePattern = 
    Pattern.compile(variablePatternString);
  /**
   * Finds one chunk of a general relabel operation, either named node
   * or captured variable
   */
  static final String oneGeneralReplacement = 
    ("(" + nodePatternString + "|" + variablePatternString + ")");
  static final Pattern oneGeneralReplacementPattern = 
    Pattern.compile(oneGeneralReplacement);

  /**
   * Identifies a node using the regex replacement strategy.
   */
  static final Pattern substPattern = 
    Pattern.compile("/" + regexPatternString + "/(.*)/");

  enum RelabelMode { FIXED, REGEX };
  private final RelabelMode mode;

  private final String newLabel;

  private final Pattern labelRegex;
  private final String replacementString;
  private final List<String> replacementPieces;

  public RelabelNode(TsurgeonPattern child, String newLabel) {
    super("relabel", new TsurgeonPattern[] { child });
    Matcher m1 = substPattern.matcher(newLabel);
    if (m1.matches()) {
      mode = RelabelMode.REGEX;
      this.labelRegex = Pattern.compile(m1.group(1));
      this.replacementString = m1.group(2);
      replacementPieces = new ArrayList<>();
      Matcher generalMatcher = 
        oneGeneralReplacementPattern.matcher(m1.group(2));
      int lastPosition = 0;
      while (generalMatcher.find()) {
        if (generalMatcher.start() > lastPosition) {
          replacementPieces.add(replacementString.substring(lastPosition, generalMatcher.start()));
        }
        lastPosition = generalMatcher.end();
        String piece = generalMatcher.group();
        if (piece.equals(""))
          continue;
        replacementPieces.add(generalMatcher.group());
      }
      if (lastPosition < replacementString.length()) {
        replacementPieces.add(replacementString.substring(lastPosition));
      }
      this.newLabel = null;
    } else {
      mode = RelabelMode.FIXED;
      Matcher m2 = regexPattern.matcher(newLabel);
      if (m2.matches()) {
        // fixed relabel but surrounded by regex slashes
        String unescapedLabel = m2.group(1);
        this.newLabel = removeEscapeSlashes(unescapedLabel);
      } else {
        // just a node name to relabel to
        this.newLabel = newLabel;
      }
      this.replacementString = null;
      this.replacementPieces = null;
      this.labelRegex = null;

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
  public TsurgeonMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new RelabelMatcher(newNodeNames, coindexer);
  }

  private class RelabelMatcher extends TsurgeonMatcher {
    public RelabelMatcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
      super(RelabelNode.this, newNodeNames, coindexer);
    }

    @Override
    public Tree evaluate(Tree tree, TregexMatcher tregex) {
      Tree nodeToRelabel = childMatcher[0].evaluate(tree, tregex);
      switch (mode) {
      case FIXED: {
        nodeToRelabel.label().setValue(newLabel);
        break;
      }
      case REGEX: {
        Matcher m = labelRegex.matcher(nodeToRelabel.label().value());
        StringBuilder label = new StringBuilder();
        for (String chunk : replacementPieces) {
          if (variablePattern.matcher(chunk).matches()) {
            String name = chunk.substring(2, chunk.length() - 1);
            label.append(Matcher.quoteReplacement(tregex.getVariableString(name)));
          } else if (nodePattern.matcher(chunk).matches()) {
            String name = chunk.substring(2, chunk.length() - 1);
            Tree node = tregex.getNode(name);
            if (node == null) {
              throw new NullPointerException("Node name " + name + " does not exist in the searched tree");
            }
            label.append(Matcher.quoteReplacement(node.value()));
          } else {
            label.append(chunk);
          }
        }
        nodeToRelabel.label().setValue(m.replaceAll(label.toString()));
        break;
      }
      default:
        throw new AssertionError("Unsupported relabel mode " + mode);
      }
      return tree;
    }
  }

  @Override
  public String toString() {
    String result;
    switch(mode) {
    case FIXED:
      return label + '(' + children[0].toString() + ',' + newLabel + ')';
    case REGEX:
      return label + '(' + children[0].toString() + ',' + labelRegex.toString() + ',' + replacementString + ')';
    default:
      throw new AssertionError("Unsupported relabel mode " + mode);
    }
  }

}
