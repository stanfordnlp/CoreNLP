package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.LabeledScoredConstituentFactory;
import edu.stanford.nlp.trees.LabeledScoredConstituent;
import edu.stanford.nlp.ling.Label;


/** Gets rid of extra NP under NP nodes.
 *  @author Dan Klein
 */
public class NodePruner  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(NodePruner.class);

  private final ExhaustivePCFGParser parser;
  private final TreeTransformer debinarizer;

  List<Tree> prune(List<Tree> treeList, Label label, int start, int end) {
    // get reference tree
    if (treeList.size() == 1) {
      return treeList;
    }
    Tree testTree = treeList.get(0).treeFactory().newTreeNode(label, treeList);
    Tree tempTree = parser.extractBestParse(label.value(), start, end);
    // parser.restoreUnaries(tempTree);
    Tree pcfgTree = debinarizer.transformTree(tempTree);
    Set<Constituent> pcfgConstituents = pcfgTree.constituents(new LabeledScoredConstituentFactory());
    // delete child labels that are not in reference but do not cross reference
    List<Tree> prunedChildren = new ArrayList<>();
    int childStart = 0;
    for (int c = 0, numCh = testTree.numChildren(); c < numCh; c++) {
      Tree child = testTree.getChild(c);
      boolean isExtra = true;
      int childEnd = childStart + child.yield().size();
      Constituent childConstituent = new LabeledScoredConstituent(childStart, childEnd, child.label(), 0);
      if (pcfgConstituents.contains(childConstituent)) {
        isExtra = false;
      }
      if (childConstituent.crosses(pcfgConstituents)) {
        isExtra = false;
      }
      if (child.isLeaf() || child.isPreTerminal()) {
        isExtra = false;
      }
      if (pcfgTree.yield().size() != testTree.yield().size()) {
        isExtra = false;
      }
      if (!label.value().startsWith("NP^NP")) {
        isExtra = false;
      }
      if (isExtra) {
        log.info("Pruning: " + child.label() + " from " + (childStart + start) + " to " + (childEnd + start));
        log.info("Was: " + testTree + " vs " + pcfgTree);
        prunedChildren.addAll(child.getChildrenAsList());
      } else {
        prunedChildren.add(child);
      }
      childStart = childEnd;
    }
    return prunedChildren;
  }

  private List<Tree> helper(List<Tree> treeList, int start) {
    List<Tree> newTreeList = new ArrayList<>(treeList.size());
    for (Tree tree : treeList) {
      int end = start + tree.yield().size();
      newTreeList.add(prune(tree, start));
      start = end;
    }
    return newTreeList;
  }

  public Tree prune(Tree tree) {
    return prune(tree, 0);
  }

  Tree prune(Tree tree, int start) {
    if (tree.isLeaf() || tree.isPreTerminal()) {
      return tree;
    }
    // check each node's children for deletion
    List<Tree> children = helper(tree.getChildrenAsList(), start);
    children = prune(children, tree.label(), start, start + tree.yield().size());
    return tree.treeFactory().newTreeNode(tree.label(), children);
  }

  public NodePruner(ExhaustivePCFGParser parser, TreeTransformer debinarizer) {
    this.parser = parser;
    this.debinarizer = debinarizer;
  }

} // end class NodePruner
