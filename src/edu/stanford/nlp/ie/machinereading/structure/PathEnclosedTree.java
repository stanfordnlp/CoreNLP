package edu.stanford.nlp.ie.machinereading.structure;

import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;

public class PathEnclosedTree {

  private final Tree theTree;

  /**
   * Constructs the smallest syntactic tree that includes all arguments of the given relation
   * This works ONLY for EntityMention arguments!
   * @param rel
   */
  public PathEnclosedTree(RelationMention rel) {
    Tree sentenceTree = rel.getSentence().get(TreeAnnotation.class);
    EntityMention leftArg = null;
    EntityMention rightArg = null;
    List<EntityMention> args = rel.getEntityMentionArgs();

    // Find left and right arguments
    for (EntityMention arg: args) {
      if (leftArg == null || arg.getSyntacticHeadTokenPosition() < leftArg.getSyntacticHeadTokenPosition()) {
        leftArg = arg;
      }
      if (rightArg == null || arg.getSyntacticHeadTokenPosition() > rightArg.getSyntacticHeadTokenPosition()) {
        rightArg = arg;
      }
    }

    Tree minCommonTree = sentenceTree.joinNode(leftArg.getSyntacticHeadTree(), rightArg.getSyntacticHeadTree());
    this.theTree = minCommonTree.treeSkeletonCopy();
    Tree leftNodeCopy = theTree.getNodeNumber(leftArg.getSyntacticHeadTree().nodeNumber(minCommonTree));
    Tree rightNodeCopy = theTree.getNodeNumber(rightArg.getSyntacticHeadTree().nodeNumber(minCommonTree));
    List<Tree> leftPath = theTree.dominationPath(leftNodeCopy);
    List<Tree> rightPath = theTree.dominationPath(rightNodeCopy);
    leftPath.remove(theTree);
    rightPath.remove(theTree);
    for (Tree node : leftPath) {
      while (node.parent().firstChild() != node) {
        node.parent().removeChild(0);
      }
    }
    for (Tree node : rightPath) {
      while (node.parent().lastChild() != node) {
        node.parent().removeChild(node.parent().numChildren()-1);
      }
    }

  }

}