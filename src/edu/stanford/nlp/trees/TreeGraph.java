package edu.stanford.nlp.trees;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

/**
 * A <code>TreeGraph</code> is a tree with additional directed,
 * labeled arcs between arbitrary pairs of nodes.  (So, it's a graph
 * with a tree skeleton.)  This class is a container for the complete
 * TreeGraph structure, and does not inherit from {@link Tree
 * <code>Tree</code>}.  Individual nodes in the TreeGraph are
 * represented by {@link TreeGraphNode <code>TreeGraphNode</code>s},
 * which do inherit from <code>Tree</code>, and the additional
 * labeled arcs are represented in the <code>TreeGraphNode</code>s.
 *
 * @author Bill MacCartney
 * @see TreeGraphNode
 */
public class TreeGraph implements Serializable {

  /**
   * The root node of this treegraph.
   */
  protected TreeGraphNode root;

  /**
   * A map from arbitrary integer indices to nodes.
   */
  private Map<Integer, TreeGraphNode> indexMap = new HashMap<Integer, TreeGraphNode>();

  /**
   * Construct a new <code>TreeGraph</code> having the same tree
   * structure and label values as an existing tree (but no shared
   * storage).  This constructor also assigns integer indices to
   * all the nodes, beginning with 0 and using a preorder tree
   * traversal.
   *
   * @param t the tree to copy
   */
  public TreeGraph(Tree t) {
    root = new TreeGraphNode(t, this);
    root.indexNodes();
  }
  
  public TreeGraph(TreeGraphNode root) {
    this.root = root;
    root.indexNodes();
  }
  
  /**
   * Return the root node of this treegraph.
   *
   * @return the root node of this treegraph
   */
  public TreeGraphNode root() {
    return root;
  }

  /**
   * Store a mapping from an arbitrary integer index to a node in
   * this treegraph.  Normally a client shouldn't need to use this,
   * as the nodes are automatically indexed by the
   * <code>TreeGraph</code> constructor.
   *
   * @param index the arbitrary integer index
   * @param node  the <code>TreeGraphNode</code> to be indexed
   */
  public void addNodeToIndexMap(int index, TreeGraphNode node) {
    indexMap.put(Integer.valueOf(index), node);
  }

  /**
   * Return the node in the this treegraph corresponding to the
   * specified integer index.
   *
   * @param index the integer index of the node you want
   * @return the <code>TreeGraphNode</code> having the specified
   *         index (or <code>null</code> if such does not exist)
   */
  public TreeGraphNode getNodeByIndex(int index) {
    return indexMap.get(Integer.valueOf(index));
  }

  public Collection<TreeGraphNode> getNodes() {
    return indexMap.values();
  }

  /**
   * Return a <code>String</code> representing this treegraph.  By
   * default, the nodes of the treegraph are printed in Lispy
   * (parenthesized) format, with one node per line, indented
   * according to depth.
   *
   * @return a <code>String</code> representation of this treegraph
   */
  @Override
  public String toString() {
    return root.toPrettyString(0).substring(1);
  }

  private static final long serialVersionUID = 1L;

  /**
   * Just for testing.
   */
  public static void main(String[] args) {
    Tree t;
    try {
      t = Tree.valueOf("(S (NP (NNP Sam)) (VP (VBD died) (NP-TMP (NN today))))");
    } catch (Exception e) {
      System.err.println("Horrible error: " + e);
      e.printStackTrace();
      return;
    }

    t.pennPrint();

    System.out.println("----------------------------");
    TreeGraph tg = new TreeGraph(t);
    System.out.println(tg);

    tg.root.percolateHeads(new SemanticHeadFinder());
    System.out.println("----------------------------");
    System.out.println(tg);

//    TreeGraphNode node1 = tg.getNodeByIndex(1);
//    TreeGraphNode node4 = tg.getNodeByIndex(4);
//    node1.addArc("1to4", node4);
//    node1.addArc("1TO4", node4);
//    node4.addArc("4to1", node1);
//    System.out.println("----------------------------");
//    System.out.println("arcs from 1 to 4: " + node1.arcLabelsToNode(node4));
//    System.out.println("arcs from 4 to 1: " + node4.arcLabelsToNode(node1));
//    System.out.println("arcs from 0 to 4: " + tg.root.arcLabelsToNode(node4));
//    for (int i = 0; i <= 9; i++) {
//      System.out.println("parent of " + i + ": " + tg.getNodeByIndex(i).parent());
//      System.out.println("highest node with same head as " + i + ": " + tg.getNodeByIndex(i).highestNodeWithSameHead());
//    }
  } // end main

}
