package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class CoindexationGenerator {

  /**
   * We require at least one character before the - so that negative
   * numbers do not get treated as indexed nodes.  This seems more
   * likely than a node having an index on an otherwise blank label.
   */
  private static final Pattern coindexationPattern = Pattern.compile(".+?-([0-9]+)$");

  private int lastIndex;

  public void setLastIndex(Tree t) {
    lastIndex = 0;
    for (Tree node : t) {
      String value = node.label().value();
      if (value != null) {
        Matcher m = coindexationPattern.matcher(value);
        if (m.find()) {
          int thisIndex = 0;
          try {
            thisIndex = Integer.parseInt(m.group(1));
          } catch (NumberFormatException e) {
            // Ignore this exception.  This kind of exception can
            // happen if there are nodes that happen to have the
            // indexing character attached, even despite the attempt
            // to ignore those nodes in the pattern above.
          }
          lastIndex = Math.max(thisIndex, lastIndex);
        }
      }
    }
  }

  public int generateIndex() {
    lastIndex = lastIndex+1;
    return lastIndex;
  }

}
