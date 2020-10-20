package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Extracts raw Nary rules from a treebank. They are returned as a Map from
 * passive constituents to Lists of right-hand side rule "paths", each of which is a List.
 */
class PathExtractor extends AbstractTreeExtractor<Map<String, List<List<String>>>> {
  private static final String END = "END";
  //protected final Index<String> stateIndex;
  private Map<String, List<List<String>>> allPaths = Generics.newHashMap();
  private HeadFinder hf;

  public PathExtractor(HeadFinder hf, Options op) {
    super(op);
    this.hf = hf;
  }

  private List<List<String>> getList(String key) {
    List<List<String>> result = allPaths.get(key);
    if (result == null) {
      result = new ArrayList<>();
      allPaths.put(key, result);
    }
    return result;
  }

  @Override
  protected void tallyInternalNode(Tree lt, double weight) {
    Tree[] children = lt.children();
    Tree headChild = hf.determineHead(lt);
    if (children.length == 1) {
      return;
    }
    List<String> path = new ArrayList<>();

    // determine which is the head
    int headLoc = -1;
    for (int i = 0; i < children.length; i++) {
      if (children[i] == headChild) {
        headLoc = i;
      }
    }

    path.add(children[headLoc].label().value());
    if (headLoc == 0) {
      // we are finishing on the right
      for (int i = headLoc + 1; i < children.length - 1; i++) {
        path.add(children[i].label().value() + ">");
      }
      if (op.trainOptions.markFinalStates) {
        path.add(children[children.length - 1].label().value() + "]");
      } else {
        path.add(children[children.length - 1].label().value() + ">");
      }
    } else {
      // we are finishing on the left
      for (int i = headLoc + 1; i < children.length; i++) {
        path.add(children[i].label().value() + ">");
      }
      for (int i = headLoc - 1; i > 0; i--) {
        path.add(children[i].label().value() + "<");
      }
      if (op.trainOptions.markFinalStates) {
        path.add(children[0].label().value() + "[");
      } else {
        path.add(children[0].label().value() + "<");
      }
    }
    path.add(END); // add epsilon at the end
    String label = lt.label().value();
    List<List<String>> l = getList(label);
    l.add(path);
  }

  @Override
  public Map<String, List<List<String>>> formResult() {
    return allPaths;
  }

}

