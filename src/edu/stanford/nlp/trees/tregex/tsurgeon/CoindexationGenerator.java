package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
class CoindexationGenerator {

  private static final Pattern coindexationPattern = Pattern.compile("-([0-9]+)$");

  private int lastIndex;

  public void setLastIndex(Tree t) {
    lastIndex = 0;
    for (Tree node : t) {
      String value = node.label().value();
      if (value != null) {
        Matcher m = coindexationPattern.matcher(value);
        if (m.find()) {
          int thisIndex = Integer.parseInt(m.group(1));
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
