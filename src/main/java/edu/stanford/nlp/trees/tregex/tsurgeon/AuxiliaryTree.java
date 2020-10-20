package edu.stanford.nlp.trees.tregex.tsurgeon; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author Roger Levy (rog@nlp.stanford.edu)
 */
public class AuxiliaryTree  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AuxiliaryTree.class);

  private final String originalTreeString;
  final Tree tree;
  Tree foot;
  private final IdentityHashMap<Tree,String> nodesToNames; // no one else should be able to get this one.
  private final Map<String,Tree> namesToNodes; // this one has a getter.


  public AuxiliaryTree(Tree tree, boolean mustHaveFoot) {
    originalTreeString = tree.toString();
    this.tree = tree;
    this.foot = findFootNode(tree);
    if (foot == null && mustHaveFoot) {
      throw new TsurgeonParseException("Error -- no foot node found for " + originalTreeString);
    }
    namesToNodes = Generics.newHashMap();
    nodesToNames = new IdentityHashMap<>();
    initializeNamesNodesMaps(tree);
  }

  private AuxiliaryTree(Tree tree, Tree foot, Map<String, Tree> namesToNodes, String originalTreeString) {
    this.originalTreeString = originalTreeString;
    this.tree = tree;
    this.foot = foot;
    this.namesToNodes = namesToNodes;
    nodesToNames = null;
  }

  public Map<String, Tree> namesToNodes() {
    return namesToNodes;
  }

  @Override
  public String toString() {
    return originalTreeString;
  }

  /**
   * Copies the Auxiliary tree.  Also, puts the new names-&gt;nodes map in the TsurgeonMatcher that called copy.
   * <br>
   * The trees and labels to use when making the copy are specified
   * with treeFactory and labelFactory.  This lets the tsurgeon script
   * produce trees which are of the same type as the input trees.
   * Each of the tsurgeon relations which copies a tree should include
   * pass in the correct factories.
   */
  public AuxiliaryTree copy(TsurgeonMatcher matcher, TreeFactory treeFactory, LabelFactory labelFactory) {
    if (labelFactory == null) {
      labelFactory = CoreLabel.factory();
    }
    Map<String,Tree> newNamesToNodes = Generics.newHashMap();
    Pair<Tree,Tree> result = copyHelper(tree, newNamesToNodes, treeFactory, labelFactory);
    //if(! result.first().dominates(result.second()))
      //log.info("Error -- aux tree copy doesn't dominate foot copy.");
    matcher.newNodeNames.putAll(newNamesToNodes);
    return new AuxiliaryTree(result.first(), result.second(), newNamesToNodes, originalTreeString);
  }

  // returns Pair<node,foot>
  private Pair<Tree,Tree> copyHelper(Tree node, Map<String,Tree> newNamesToNodes, TreeFactory treeFactory, LabelFactory labelFactory) {
    Tree clone;
    Tree newFoot = null;
    if (node.isLeaf()) {
      if (node == foot) { // found the foot node; pass it up.
        clone = treeFactory.newTreeNode(node.label(), new ArrayList<>(0));
        newFoot = clone;
      } else {
        clone = treeFactory.newLeaf(labelFactory.newLabel(node.label()));
      }
    } else {
      List<Tree> newChildren = new ArrayList<>(node.children().length);
      for (Tree child : node.children()) {
        Pair<Tree,Tree> newChild = copyHelper(child, newNamesToNodes, treeFactory, labelFactory);
        newChildren.add(newChild.first());
        if (newChild.second() != null) {
          if (newFoot != null) {
            log.info("Error -- two feet found when copying auxiliary tree " + tree.toString() + "; using last foot found.");
          }
          newFoot = newChild.second();
        }
      }
      clone = treeFactory.newTreeNode(labelFactory.newLabel(node.label()),newChildren);
    }

    if (nodesToNames.containsKey(node))
      newNamesToNodes.put(nodesToNames.get(node),clone);

    return new Pair<>(clone, newFoot);
  }



  /***********************************************************/
  /* below here is init stuff for finding the foot node.     */
  /***********************************************************/


  private static final String footNodeCharacter = "@";
  private static final Pattern footNodeLabelPattern = Pattern.compile("^(.*)" + footNodeCharacter + '$');
  private static final Pattern escapedFootNodeCharacter = Pattern.compile('\\' + footNodeCharacter);

  /**
   * Returns the foot node of the adjunction tree, which is the terminal node
   * that ends in @.  In the process, turns the foot node into a TreeNode
   * (rather than a leaf), and destructively un-escapes all the escaped
   * instances of @ in the tree.  Note that final @ in a non-terminal node is
   * ignored, and left in.
   */
  private static Tree findFootNode(Tree t) {
    Tree footNode = findFootNodeHelper(t);
    Tree result = footNode;
    if (footNode != null) {
      Tree newFootNode = footNode.treeFactory().newTreeNode(footNode.label(), new ArrayList<>());

      Tree parent = footNode.parent(t);
      if (parent != null) {
        int i = parent.objectIndexOf(footNode);
        parent.setChild(i, newFootNode);
      }

      result = newFootNode;
    }
    return result;
  }

  private static Tree findFootNodeHelper(Tree t) {
    Tree foundDtr = null;
    if (t.isLeaf()) {
      Matcher m = footNodeLabelPattern.matcher(t.label().value());
      if (m.matches()) {
        t.label().setValue(m.group(1));
        return t;
      } else {
        return null;
      }
    }
    for (Tree child : t.children()) {
      Tree thisFoundDtr = findFootNodeHelper(child);
      if (thisFoundDtr != null) {
        if (foundDtr != null) {
          throw new TsurgeonParseException("Error -- two foot nodes in subtree" + t.toString());
        } else {
          foundDtr = thisFoundDtr;
        }
      }
    }
    Matcher m = escapedFootNodeCharacter.matcher(t.label().value());
    t.label().setValue(m.replaceAll(footNodeCharacter));
    return foundDtr;
  }


  /* ******************************************************* *
   * below here is init stuff for getting node -> names maps *
   * ******************************************************* */

  // There are two ways in which you can can match the start of a name
  // expression.
  // The first is if you have any number of non-escaping characters
  // preceding an "=" and a name.  This is the ([^\\\\]*) part.
  // The second is if you have any number of any characters, followed
  // by a non-"\" character, as "\" is used to escape the "=".  After
  // that, any number of pairs of "\" are allowed, as we let "\" also
  // escape itself.  After that comes "=" and a name.
  static final Pattern namePattern = Pattern.compile("^((?:[^\\\\]*)|(?:(?:.*[^\\\\])?)(?:\\\\\\\\)*)=([^=]+)$");

  /**
   * Looks for new names, destructively strips them out.
   * Destructively unescapes escaped chars, including "=", as well.
   */
  private void initializeNamesNodesMaps(Tree t) {
    for (Tree node : t.subTreeList()) {
      Matcher m = namePattern.matcher(node.label().value());
      if (m.find()) {
        namesToNodes.put(m.group(2), node);
        nodesToNames.put(node, m.group(2));
        node.label().setValue(m.group(1));
      }
      node.label().setValue(unescape(node.label().value()));
    }
  }

  static String unescape(String input) {
    return input.replaceAll("\\\\(.)", "$1");
  }

}
