package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Pair;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author Roger Levy
 */
class TreeLocation {

  private final String relation;

  private final TsurgeonPattern child;

  public TreeLocation(String relation, TsurgeonPattern p) {
    this.relation = relation;
    this.child = p;
  }

  void setRoot(TsurgeonPatternRoot root) {
    child.setRoot(root);
  }

  private static final Pattern daughterPattern = Pattern.compile(">-?([0-9]+)");

  public LocationMatcher matcher(Map<String,Tree> newNodeNames, CoindexationGenerator coindexer) {
    return new LocationMatcher(newNodeNames, coindexer);
  }

  /** TODO: it would be nice to refactor this with TsurgeonMatcher somehow */
  class LocationMatcher {
    Map<String,Tree> newNodeNames;
    CoindexationGenerator coindexer;

    TsurgeonMatcher childMatcher;

    LocationMatcher(Map<String, Tree> newNodeNames, CoindexationGenerator coindexer) {
      this.newNodeNames = newNodeNames;
      this.coindexer = coindexer;

      this.childMatcher = child.matcher(newNodeNames, coindexer);
    }

    Pair<Tree,Integer> evaluate(Tree tree, TregexMatcher tregex) {
      int newIndex; // initialized below
      Tree parent; // initialized below
      Tree relativeNode = childMatcher.evaluate(tree, tregex);
      Matcher m = daughterPattern.matcher(relation);
      if (m.matches()) {
        newIndex = Integer.parseInt(m.group(1))-1;
        parent = relativeNode;
        if(relation.charAt(1)=='-') // backwards.
          newIndex = parent.children().length - newIndex;
      } else {
        parent = relativeNode.parent(tree);
        if (parent == null) {
          throw new RuntimeException("Error: looking for a non-existent parent in tree " + tree + " for \"" + toString() + '"');
        }
        int index = parent.objectIndexOf(relativeNode);
        switch(relation) {
          case "$+" :
            newIndex = index;
            break;
          case "$-" :
            newIndex = index+1;
            break;
          default :
            throw new RuntimeException("Error: Haven't dealt with relation " + relation + " yet.");
        }
      }
      return new Pair<>(parent, newIndex);
    }
  }

  @Override
  public String toString() {
    return relation + ' ' + child;
  }

}
