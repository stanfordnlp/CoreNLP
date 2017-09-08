package old.edu.stanford.nlp.trees;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import old.edu.stanford.nlp.ling.CoreAnnotation;
import old.edu.stanford.nlp.ling.CyclicCoreLabel;
import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.LabelFactory;
import old.edu.stanford.nlp.trees.GrammaticalRelation.GrammaticalRelationAnnotation;
import old.edu.stanford.nlp.util.Filter;
import old.edu.stanford.nlp.util.Generics;


/**
 * A "treegraph" is a tree with additional directed, labeled arcs
 * between arbitrary pairs of nodes.  (So, it's a graph with a tree
 * skeleton.)  A <code>TreeGraphNode</code> represents any node in a
 * treegraph.  The additional labeled arcs are represented by using
 * {@link CyclicCoreLabel <code>CyclicCoreLabel</code>} labels at each node, which
 * contain <code>Map</code>s from arc label strings to
 * <code>Set</code>s of <code>TreeGraphNode</code>s.  Each
 * <code>TreeGraphNode</code> should contain a reference to a {@link
 * TreeGraph <code>TreeGraph</code>} object, which is a container for
 * the complete treegraph structure.<p>
 *
 * <p>This class makes the horrible mistake of changing the semantics of
 * equals and hashcode to go back to "==" and System.identityHashCode,
 * despite the semantics of the superclasse's equality.</p>
 *
 * @author Bill MacCartney
 * @see TreeGraph
 */
public class TreeGraphNode extends Tree {

  /**
   * Label for this node.
   */
  protected CyclicCoreLabel label;

  /**
   * Parent of this node.
   */
  protected TreeGraphNode parent; // = null;


  /**
   * Children of this node.
   */
  protected TreeGraphNode[] children = ZERO_TGN_CHILDREN;

  /**
   * The {@link TreeGraph <code>TreeGraph</code>} of which this
   * node is part.
   */
  protected TreeGraph tg;

  /**
   * A leaf node should have a zero-length array for its
   * children. For efficiency, subclasses can use this array as a
   * return value for children() for leaf nodes if desired. Should
   * this be public instead?
   */
  protected static final TreeGraphNode[] ZERO_TGN_CHILDREN = new TreeGraphNode[0];

  private static LabelFactory mlf = CyclicCoreLabel.factory();

  /**
   * Create a new empty <code>TreeGraphNode</code>.
   */
  public TreeGraphNode() {
  }

  /**
   * Create a new <code>TreeGraphNode</code> with the supplied
   * label.
   *
   * @param label the label for this node.
   */
  public TreeGraphNode(Label label) {
    this.label = (CyclicCoreLabel) mlf.newLabel(label);
  }

  /**
   * Create a new <code>TreeGraphNode</code> with the supplied
   * label and list of child nodes.
   *
   * @param label    the label for this node.
   * @param children the list of child <code>TreeGraphNode</code>s
   *                 for this node.
   */
  public TreeGraphNode(Label label, List<Tree> children) {
    this(label);
    setChildren(children);
  }

  /**
   * Create a new <code>TreeGraphNode</code> having the same tree
   * structure and label values as an existing tree (but no shared
   * storage).
   * @param t     the tree to copy
   * @param graph the graph of which this node is a part
   */
  public TreeGraphNode(Tree t, TreeGraph graph) {
    this(t, (TreeGraphNode) null);
    this.setTreeGraph(graph);
  }

  // XXX TODO it's not really clear what graph the copy should be a part of
  public TreeGraphNode(TreeGraphNode t) {
    this(t, t.parent);
    this.setTreeGraph(t.treeGraph());
  }

  /**
   * Create a new <code>TreeGraphNode</code> having the same tree
   * structure and label values as an existing tree (but no shared
   * storage).  Operates recursively to construct an entire
   * subtree.
   *
   * @param t      the tree to copy
   * @param parent the parent node
   */
  protected TreeGraphNode(Tree t, TreeGraphNode parent) {
    this.parent = parent;
    Tree[] tKids = t.children();
    int numKids = tKids.length;
    children = new TreeGraphNode[numKids];
    for (int i = 0; i < numKids; i++) {
      children[i] = new TreeGraphNode(tKids[i], this);
      if (t.isPreTerminal()) { // add the tags to the leaves
        children[i].label.setTag(t.label().value());
      }
    }
    this.label = (CyclicCoreLabel) mlf.newLabel(t.label());
  }

  /**
   * Implements equality for <code>TreeGraphNode</code>s.  Unlike
   * <code>Tree</code>s, <code>TreeGraphNode</code>s should be
   * considered equal only if they are ==.  <i>Implementation note:</i>
   * TODO: This should be changed via introducing a Tree interface with the current Tree and this class implementing it, since what is done here breaks the equals() contract.
   *
   * @param o The object to compare with
   * @return Whether two things are equal
   */
  @Override
  public boolean equals(Object o) {
    return o == this;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  /**
   * Returns the label associated with the current node, or null
   * if there is no label.
   *
   * @return the label of the node
   */
  @Override
  public CyclicCoreLabel label() {
    return label;
  }

  /**
   * Sets the label associated with the current node.
   *
   * @param label the new label to use.
   */
  public void setLabel(final CyclicCoreLabel label) {
    this.label = label;
  }

  /**
   * Get the index for the current node.
   */
  public int index() {
    return label.index();
  }

  /**
   * Set the index for the current node.
   */
  protected void setIndex(int index) {
    label.setIndex(index);
  }

  /**
   * Assign sequential integer indices to the leaves of the subtree
   * rooted at this <code>TreeGraphNode</code>, beginning with
   * <code>startIndex</code>, and traversing the leaves from left
   * to right. If node is already indexed, then it uses the existing index.
   *
   * @param startIndex index for this node
   * @return the next index still unassigned
   */
  private int indexLeaves(int startIndex) {
    if (isLeaf()) {
      int oldIndex = index();
      if (oldIndex>=0) {
        startIndex = oldIndex;
      } else {
        setIndex(startIndex);
      }
      if (tg != null) {
        tg.addNodeToIndexMap(startIndex, this);
      }
      startIndex++;
    } else {
      for (int i = 0; i < children.length; i++) {
        startIndex = children[i].indexLeaves(startIndex);
      }
    }
    return startIndex;
  }

  /**
   * Assign sequential integer indices to all nodes of the subtree
   * rooted at this <code>TreeGraphNode</code>, beginning with
   * <code>startIndex</code>, and doing a pre-order tree traversal.
   * Any node which already has an index will not be re-indexed
   * &mdash; this is so that we can index the leaves first, and
   * then index the rest.
   *
   * @param startIndex index for this node
   * @return the next index still unassigned
   */
  private int indexNodes(int startIndex) {
    if (index() < 0) {		// if this node has no index
      if (tg != null) {
        tg.addNodeToIndexMap(startIndex, this);
      }
      setIndex(startIndex++);
    }
    if (!isLeaf()) {
      for (int i = 0; i < children.length; i++) {
        startIndex = children[i].indexNodes(startIndex);
      }
    }
    return startIndex;
  }

  /**
   * Assign sequential integer indices (starting with 0) to all
   * nodes of the subtree rooted at this
   * <code>TreeGraphNode</code>.  The leaves are indexed first,
   * from left to right.  Then the internal nodes are indexed,
   * using a pre-order tree traversal.
   */
  protected void indexNodes() {
    indexNodes(indexLeaves(1));
  }

  /**
   * Get the parent for the current node.
   */
  @Override
  public Tree parent() {
    return parent;
  }

  /**
   * Set the parent for the current node.
   */
  public void setParent(TreeGraphNode parent) {
    this.parent = parent;
  }

  /**
   * Returns an array of the children of this node.
   */
  @Override
  public TreeGraphNode[] children() {
    return children;
  }

  /**
   * Sets the children of this <code>TreeGraphNode</code>.  If
   * given <code>null</code>, this method sets
   * the node's children to the canonical zero-length Tree[] array.
   *
   * @param children an array of child trees
   */
  @Override
  public void setChildren(Tree[] children) {
    if (children == null) {
      this.children = ZERO_TGN_CHILDREN;
    } else {
      this.children = (TreeGraphNode[]) children;
    }
  }

  /**
   * Get the <code>TreeGraph</code> of which this node is a
   * part.
   */
  protected TreeGraph treeGraph() {
    return tg;
  }

  /**
   * Set pointer to the <code>TreeGraph</code> of which this node
   * is a part.  Operates recursively to set pointer for all
   * descendants too.
   */
  protected void setTreeGraph(TreeGraph tg) {
    this.tg = tg;
    for (TreeGraphNode child : children) {
      child.setTreeGraph(tg);
    }
  }

  /**
   * Add a labeled arc from this node to the argument node.
   *
   * @param arcLabel the <code>Class&lt;? extends GrammaticalRelationAnnotation&gt;</code> with which the new arc
   *                 is to be labeled.
   * @param node     the <code>TreeGraphNode</code> to which the new
   *                 arc should point.
   * @return <code>true</code> iff the arc did not already exist.
   */
  public boolean addArc(Class<? extends GrammaticalRelationAnnotation> arcLabel, TreeGraphNode node) {
    if (node == null) {
      return false;
    }
    if (!treeGraph().equals(node.treeGraph())) {
      System.err.println("Warning: you are trying to add an arc from node " + this + " to node " + node + ", but they do not belong to the same TreeGraph!");
    }
    if (!label.containsKey(arcLabel)) {
      label.set(arcLabel, new HashSet<TreeGraphNode>());
    }
    return label.get(arcLabel).add(node);
  }

  /**
   * Returns the <code>Set</code> of <code>TreeGraphNode</code>s to
   * which there exist arcs bearing the specified label from this
   * node, or <code>null</code> if no such nodes exist.
   *
   * @param arcLabel the <code>Object</code> which labels the
   *                 arc(s) to be followed.
   * @return a <code>Set</code> containing only and all the
   *         <code>TreeGraphNode</code>s to which there exist arcs bearing
   *         the specified label from this node.
   */
  public Set<TreeGraphNode> followArcToSet(Class<? extends GrammaticalRelationAnnotation> arcLabel) {
    return label().get(arcLabel);
  }

  /**
   * Returns a single <code>TreeGraphNode</code> to which there
   * exists an arc bearing the specified label from this node, or
   * <code>null</code> if no such node exists.  If more than one
   * such node exists, this method will return an arbitrary node
   * from among them; if this is a possibility, you might want to
   * use {@link TreeGraphNode#followArcToSet
   * <code>followArcToSet</code>} instead.
   *
   * @param arcLabel a <code>Object</code> containing the label of
   *                 the arc(s) to be followed
   * @return a <code>TreeGraphNode</code> to which there exists an
   *         arc bearing the specified label from this node
   */
  public TreeGraphNode followArcToNode(Class<? extends GrammaticalRelationAnnotation> arcLabel) {
    Set<TreeGraphNode> valueSet = followArcToSet(arcLabel);
    if (valueSet == null) {
      return null;
    }
    return valueSet.iterator().next();
  }

  /**
   * Finds all arcs between this node and <code>destNode</code>,
   * and returns the <code>Set</code> of <code>Object</code>s which
   * label those arcs.  If no such arcs exist, returns an empty
   * <code>Set</code>.
   *
   * @param destNode the destination node
   * @return the <code>Set</code> of <code>Object</code>s which
   *         label arcs between this node and <code>destNode</code>
   */
  public Set<Class<? extends GrammaticalRelationAnnotation>> arcLabelsToNode(TreeGraphNode destNode) {
    Set<Class<? extends GrammaticalRelationAnnotation>> arcLabels = Generics.newHashSet();
    CyclicCoreLabel cl = label();
    for (Iterator<Class<?>> it = cl.keySet().iterator(); it.hasNext();) {
      Class<? extends CoreAnnotation> key = (Class<? extends CoreAnnotation>) it.next();//javac doesn't compile properly if generics are fully specified (but eclipse does...)
      Object val = cl.get(key);
      if (val != null && val instanceof Set) {
        if (((Set) val).contains(destNode)) {
          if (key != null) {
            arcLabels.add((Class<? extends GrammaticalRelationAnnotation>) key);
          }
        }
      }
    }
    return arcLabels;
  }

  /**
   * Returns the label of a single arc between this node and <code>destNode</code>,
   * or <code>null</code> if no such arc exists.  If more than one
   * such arc exists, this method will return an arbitrary arc label
   * from among them; if this is a possibility, you might want to
   * use {@link TreeGraphNode#arcLabelsToNode
   * <code>arcLabelsToNode</code>} instead.
   *
   * @param destNode the destination node
   * @return the <code>Object</code> which
   *         labels one arc between this node and <code>destNode</code>
   */
  public Class<? extends GrammaticalRelationAnnotation> arcLabelToNode(TreeGraphNode destNode) {
    Set<Class<? extends GrammaticalRelationAnnotation>> arcLabels = arcLabelsToNode(destNode);
    if (arcLabels == null) {
      return null;
    }
    return (new ArrayList<Class<? extends GrammaticalRelationAnnotation>>(arcLabels)).get(0);
  }

  /**
   * Uses the specified {@link HeadFinder <code>HeadFinder</code>}
   * to determine the heads for this node and all its descendants,
   * and to store references to the head word node and head tag node
   * in this node's {@link CyclicCoreLabel <code>CyclicCoreLabel</code>} and the
   * <code>CyclicCoreLabel</code>s of all its descendants.<p>
   * <p/>
   * Note that, in contrast to {@link Tree#percolateHeads
   * <code>Tree.percolateHeads()</code>}, which assumes {@link
   * edu.stanford.nlp.ling.CategoryWordTag
   * <code>CategoryWordTag</code>} labels and therefore stores head
   * words and head tags merely as <code>String</code>s, this
   * method stores references to the actual nodes.  This mitigates
   * potential problems in sentences which contain the same word
   * more than once.
   *
   * @param hf The headfinding algorithm to use
   */
  @Override
  public void percolateHeads(HeadFinder hf) {
    if (isLeaf()) {
      TreeGraphNode hwn = headWordNode();
      if (hwn == null) {
        setHeadWordNode(this);
      }
    } else {
      for (Tree child : children()) {
        child.percolateHeads(hf);
      }
      TreeGraphNode head = safeCast(hf.determineHead(this,parent));
      if (head != null) {

        TreeGraphNode hwn = head.headWordNode();
        if (hwn == null && head.isLeaf()) { // below us is a leaf
          setHeadWordNode(head);
        } else {
          setHeadWordNode(hwn);
        }

        TreeGraphNode htn = head.headTagNode();
        if (htn == null && head.isLeaf()) { // below us is a leaf
          setHeadTagNode(this);
        } else {
          setHeadTagNode(htn);
        }

      } else {
        System.err.println("Head is null: " + this);
      }
    }
  }

  /**
   * Return a set of node-node dependencies, represented as Dependency
   * objects, for the Tree.
   *
   * @param hf The HeadFinder to use to identify the head of constituents.
   *           If this is <code>null</code>, then nodes are assumed to already
   *           be marked with their heads.
   * @return Set of dependencies (each a <code>Dependency</code>)
   */
  @Override
  public Set<Dependency<Label, Label, Object>> dependencies(Filter<Dependency<Label, Label, Object>> f, HeadFinder hf) {
    Set<Dependency<Label, Label, Object>> deps = Generics.newHashSet();
    for (Iterator<Tree> it = subTrees().iterator(); it.hasNext();) {

      TreeGraphNode node = safeCast(it.next());
      if (node == null || node.isLeaf() || node.children().length < 2) {
        continue;
      }

      TreeGraphNode headWordNode;
      if (hf != null) {
        headWordNode = safeCast(node.headTerminal(hf));
      } else {
        headWordNode = node.headWordNode();
      }

      Tree[] kids = node.children();
      for (int i = 0; i < kids.length; i++) {
        TreeGraphNode kid = safeCast(kids[i]);
        if (kid == null) {
          continue;
        }
        TreeGraphNode kidHeadWordNode;
        if (hf != null) {
          kidHeadWordNode = safeCast(kid.headTerminal(hf));
        } else {
          kidHeadWordNode = kid.headWordNode();
        }

        if (headWordNode != null && headWordNode != kidHeadWordNode) {
          Dependency<Label, Label, Object> d = new UnnamedDependency(headWordNode, kidHeadWordNode);
          if (f.accept(d)) {
            deps.add(d);
          }
        }
      }
    }
    return deps;
  }

  /**
   * Return the node containing the head word for this node (or
   * <code>null</code> if none), as recorded in this node's {@link
   * CyclicCoreLabel <code>CyclicCoreLabel</code>}.  (In contrast to {@link
   * edu.stanford.nlp.ling.CategoryWordTag
   * <code>CategoryWordTag</code>}, we store head words and head
   * tags as references to nodes, not merely as
   * <code>String</code>s.)
   *
   * @return the node containing the head word for this node
   */
  public TreeGraphNode headWordNode() {
    TreeGraphNode hwn = safeCast(label.get(TreeCoreAnnotations.HeadWordAnnotation.class));
    if (hwn == null || (hwn.treeGraph() != null && !(hwn.treeGraph().equals(this.treeGraph())))) {
      return null;
    }
    return hwn;
  }

  /**
   * Store the node containing the head word for this node by
   * storing it in this node's {@link CyclicCoreLabel
   * <code>CyclicCoreLabel</code>}.  (In contrast to {@link
   * edu.stanford.nlp.ling.CategoryWordTag
   * <code>CategoryWordTag</code>}, we store head words and head
   * tags as references to nodes, not merely as
   * <code>String</code>s.)
   *
   * @param hwn the node containing the head word for this node
   */
  private void setHeadWordNode(final TreeGraphNode hwn) {
    label.set(TreeCoreAnnotations.HeadWordAnnotation.class, hwn);
  }

  /**
   * Return the node containing the head tag for this node (or
   * <code>null</code> if none), as recorded in this node's {@link
   * CyclicCoreLabel <code>CyclicCoreLabel</code>}.  (In contrast to {@link
   * edu.stanford.nlp.ling.CategoryWordTag
   * <code>CategoryWordTag</code>}, we store head words and head
   * tags as references to nodes, not merely as
   * <code>String</code>s.)
   *
   * @return the node containing the head tag for this node
   */
  public TreeGraphNode headTagNode() {
    TreeGraphNode htn = safeCast(label.get(TreeCoreAnnotations.HeadTagAnnotation.class));
    if (htn == null || (htn.treeGraph() != null && !(htn.treeGraph().equals(this.treeGraph())))) {
      return null;
    }
    return htn;
  }

  /**
   * Store the node containing the head tag for this node by
   * storing it in this node's {@link CyclicCoreLabel
   * <code>CyclicCoreLabel</code>}.  (In contrast to {@link
   * edu.stanford.nlp.ling.CategoryWordTag
   * <code>CategoryWordTag</code>}, we store head words and head
   * tags as references to nodes, not merely as
   * <code>String</code>s.)
   *
   * @param htn the node containing the head tag for this node
   */
  private void setHeadTagNode(final TreeGraphNode htn) {
    label.set(TreeCoreAnnotations.HeadTagAnnotation.class, htn);
  }

  /**
   * Safely casts an <code>Object</code> to a
   * <code>TreeGraphNode</code> if possible, else returns
   * <code>null</code>.
   *
   * @param t any <code>Object</code>
   * @return <code>t</code> if it is a <code>TreeGraphNode</code>;
   *         <code>null</code> otherwise
   */
  private static TreeGraphNode safeCast(Object t) {
    if (t == null || !(t instanceof TreeGraphNode)) {
      return null;
    }
    return (TreeGraphNode) t;
  }


  public TreeGraphNode highestNodeWithSameHead() {
    TreeGraphNode node = this;
    while (true) {
      TreeGraphNode parent = safeCast(node.parent());
      if (parent == null || parent.headWordNode() != node.headWordNode()) {
        return node;
      }
      node = parent;
    }
  }

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class TreeFactoryHolder {

    static final TreeGraphNodeFactory tgnf = new TreeGraphNodeFactory();

    private TreeFactoryHolder() {
    }

  }

  /**
   * Returns a <code>TreeFactory</code> that produces
   * <code>TreeGraphNode</code>s.  The <code>Label</code> of
   * <code>this</code> is examined, and providing it is not
   * <code>null</code>, a <code>LabelFactory</code> which will
   * produce that kind of <code>Label</code> is supplied to the
   * <code>TreeFactory</code>.  If the <code>Label</code> is
   * <code>null</code>, a
   * <code>CyclicCoreLabel.factory()</code> will be used.  The factories
   * returned on different calls are different: a new one is
   * allocated each time.
   *
   * @return a factory to produce treegraphs
   */
  @Override
  public TreeFactory treeFactory() {
    LabelFactory lf;
    if (label() != null) {
      lf = label().labelFactory();
    } else {
      lf = CyclicCoreLabel.factory();
    }
    return new TreeGraphNodeFactory(lf);
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of type
   * <code>TreeGraphNode</code>.  The factory returned is always
   * the same one (a singleton).
   *
   * @return a factory to produce treegraphs
   */
  public static TreeFactory factory() {
    return TreeFactoryHolder.tgnf;
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of type
   * <code>TreeGraphNode</code>, with the <code>Label</code> made
   * by the supplied <code>LabelFactory</code>.  The factory
   * returned is a different one each time.
   *
   * @param lf The <code>LabelFactory</code> to use
   * @return a factory to produce treegraphs
   */
  public static TreeFactory factory(LabelFactory lf) {
    return new TreeGraphNodeFactory(lf);
  }

  /**
   * Returns a <code>String</code> representation of this node and
   * its subtree with one node per line, indented according to
   * <code>indentLevel</code>.
   *
   * @param indentLevel how many levels to indent (0 for root node)
   * @return <code>String</code> representation of this subtree
   */
  public String toPrettyString(int indentLevel) {
    StringBuilder buf = new StringBuilder("\n");
    for (int i = 0; i < indentLevel; i++) {
      buf.append("  ");
    }
    if (children == null || children.length == 0) {
      buf.append(label.toString("value-index{map}"));
    } else {
      buf.append('(').append(label.toString("value-index{map}"));
      for (TreeGraphNode child : children) {
        buf.append(' ').append(child.toPrettyString(indentLevel + 1));
      }
      buf.append(')');
    }
    return buf.toString();
  }

  /**
   * Returns a <code>String</code> representation of this node and
   * its subtree as a one-line parenthesized list.
   *
   * @return <code>String</code> representation of this subtree
   */
  public String toOneLineString() {
    StringBuilder buf = new StringBuilder();
    if (children == null || children.length == 0) {
      buf.append(label);
    } else {
      buf.append('(').append(label);
      for (TreeGraphNode child : children) {
        buf.append(' ').append(child.toString());
      }
      buf.append(')');
    }
    return buf.toString();
  }

  @Override
  public String toString() {
    return label.toString();
  }

  /**
   * Just for testing.
   */
  public static void main(String[] args) {
    try {
      TreeReader tr = new PennTreeReader(new StringReader("(S (NP (NNP Sam)) (VP (VBD died) (NP (NN today))))"), new LabeledScoredTreeFactory());
      Tree t = tr.readTree();
      System.out.println(t);
      TreeGraphNode tgn = new TreeGraphNode(t, (TreeGraphNode) null);
      System.out.println(tgn.toPrettyString(0));
      tgn.indexNodes();
      System.out.println(tgn.toPrettyString(0));
      tgn.percolateHeads(new SemanticHeadFinder());
      System.out.println(tgn.toPrettyString(0));
    } catch (Exception e) {
      System.err.println("Horrible error: " + e);
      e.printStackTrace();
    }
  }

  // Automatically generated by Eclipse
  private static final long serialVersionUID = 5080098143617475328L;
}
