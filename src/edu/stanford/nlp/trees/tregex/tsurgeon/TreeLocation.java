package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Pair;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author Roger Levy
 */
class TreeLocation {

  private final String relation;

  private final TsurgeonPattern p;

  public TreeLocation(String relation, TsurgeonPattern p) {
    this.relation = relation;
    this.p = p;
  }

  void setRoot(TsurgeonPatternRoot root) {
    p.setRoot(root);
  }

  private static final Pattern daughterPattern = Pattern.compile(">-?([0-9]+)");

  Pair<Tree,Integer> evaluate(Tree t, TregexMatcher tm) {
    int newIndex = -1;
    Tree parent = null;
    Tree relativeNode = p.evaluate(t,tm);
    Matcher m = daughterPattern.matcher(relation);
    if (m.matches()) {
      newIndex = Integer.parseInt(m.group(1))-1;
      parent = relativeNode;
      if(relation.charAt(1)=='-') // backwards.
        newIndex = parent.children().length - newIndex;
    } else {
      parent = relativeNode.parent(t);
      if (parent == null) {
        throw new RuntimeException("Error: looking for a non-existent parent in tree " + t + " for \"" + toString() + "\"");
      }
      int index = parent.objectIndexOf(relativeNode);
      if (relation.equals("$+")) {
        newIndex = index;
      } else if (relation.equals("$-")) {
        newIndex = index+1;
      } else {
        throw new RuntimeException("Error: Haven't dealt with relation " + relation + " yet.");
      }
    }
    return new Pair<Tree,Integer>(parent,newIndex);
  }

  @Override
  public String toString() {
    return relation + " " + p;
  }

}
