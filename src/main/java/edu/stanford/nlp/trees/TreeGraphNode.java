package edu.stanford.nlp.trees; 

import java.io.StringReader;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * A {@code TreeGraphNode} is simply a {@link Tree {@code Tree}}
 * with some additional functionality.  For example, the
 * {@code parent()} method works without searching from the root.
 * Labels are always assumed to be {@link CoreLabel {@code CoreLabel}}.
 *
 * This class makes the horrible mistake of changing the semantics of
 * equals and hashCode to go back to "==" and System.identityHashCode,
 * despite the semantics of the superclass's equality.
 *
 * @author Bill MacCartney
 */
public class TreeGraphNode extends Tree implements HasParent  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TreeGraphNode.class);

  /**
   * Label for this node.
   */
  private CoreLabel label;

  /**
   * Parent of this node.
   */
  protected TreeGraphNode parent; // = null;


  /**
   * Children of this node.
   */
  protected TreeGraphNode[] children = ZERO_TGN_CHILDREN;

  /**
   * For internal nodes, the head word of this subtree.
   */
  private TreeGraphNode headWordNode;

  /**
   * A leaf node should have a zero-length array for its
   * children. For efficiency, subclasses can use this array as a
   * return value for children() for leaf nodes if desired. Should
   * this be public instead?
   */
  protected static final TreeGraphNode[] ZERO_TGN_CHILDREN = new TreeGraphNode[0];

  private static final LabelFactory mlf = CoreLabel.factory();


  /**
   * Create a new {@code TreeGraphNode} with the supplied
   * label.
   *
   * @param label the label for this node.
   */
  public TreeGraphNode(Label label) {
    this.label = (CoreLabel) mlf.newLabel(label);
  }

  /**
   * Create a new {@code TreeGraphNode} with the supplied
   * label and list of child nodes.
   *
   * @param label    the label for this node.
   * @param children the list of child {@code TreeGraphNode}s
   *                 for this node.
   */
  public TreeGraphNode(Label label, List<Tree> children) {
    this(label);
    setChildren(children);
  }

  /**
   * Create a new {@code TreeGraphNode} having the same tree
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
    this.label = (CoreLabel) mlf.newLabel(t.label());
  }

  /**
   * Implements equality for {@code TreeGraphNode}s.  Unlike
   * {@code Tree}s, {@code TreeGraphNode}s should be
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
  public CoreLabel label() {
    return label;
  }

  @Override
  public void setLabel(Label label) {
    if (label instanceof CoreLabel) {
      this.setLabel((CoreLabel) label);
    } else {
      this.setLabel((CoreLabel) mlf.newLabel(label));
    }
  }

  /**
   * Sets the label associated with the current node.
   *
   * @param label the new label to use.
   */
  public void setLabel(final CoreLabel label) {
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
   * Get the parent for the current node.
   */
  @Override
  public TreeGraphNode parent() {
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
   * Sets the children of this {@code TreeGraphNode}.  If
   * given {@code null}, this method sets
   * the node's children to the canonical zero-length Tree[] array.
   *
   * @param children an array of child trees
   */
  @Override
  public void setChildren(Tree[] children) {
    if (children == null || children.length == 0) {
      this.children = ZERO_TGN_CHILDREN;
    } else {
      if (children instanceof TreeGraphNode[]) {
        this.children = (TreeGraphNode[]) children;
        for (TreeGraphNode child : this.children) {
          child.setParent(this);
        }
      } else {
        this.children = new TreeGraphNode[children.length];
        for (int i = 0; i < children.length; i++) {
          this.children[i] = (TreeGraphNode)children[i];
          this.children[i].setParent(this);
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setChildren(List<? extends Tree> childTreesList) {
    if (childTreesList == null || childTreesList.isEmpty()) {
      setChildren(ZERO_TGN_CHILDREN);
    } else {
      int leng = childTreesList.size();
      TreeGraphNode[] childTrees = new TreeGraphNode[leng];
      childTreesList.toArray(childTrees);
      setChildren(childTrees);
    }
  }

  @Override
  public Tree setChild(int i, Tree t) {
    if (!(t instanceof TreeGraphNode)) {
      throw new IllegalArgumentException("Horrible error");
    }
    ((TreeGraphNode) t).setParent(this);
    return super.setChild(i, t);
  }

  /**
   * Adds a child in the ith location.  Does so without overwriting
   * the parent pointers of the rest of the children, which might be
   * relevant in case there are add and remove operations mixed
   * together.
   */
  @Override
  public void addChild(int i, Tree t) {
    if (!(t instanceof TreeGraphNode)) {
      throw new IllegalArgumentException("Horrible error");
    }
    ((TreeGraphNode) t).setParent(this);
    TreeGraphNode[] kids = this.children;
    TreeGraphNode[] newKids = new TreeGraphNode[kids.length + 1];
    if (i != 0) {
      System.arraycopy(kids, 0, newKids, 0, i);
    }
    newKids[i] = (TreeGraphNode) t;
    if (i != kids.length) {
      System.arraycopy(kids, i, newKids, i + 1, kids.length - i);
    }
    this.children = newKids;
  }

  /**
   * Removes the ith child from the TreeGraphNode.  Needs to override
   * the parent removeChild so it can avoid setting the parent
   * pointers on the remaining children.  This is useful if you want
   * to add and remove children from one node to another node; this way,
   * it won't matter what order you do the add and remove operations.
   */
  @Override
  public Tree removeChild(int i) {
    TreeGraphNode[] kids = children();
    TreeGraphNode kid = kids[i];
    TreeGraphNode[] newKids = new TreeGraphNode[kids.length - 1];
    for (int j = 0; j < newKids.length; j++) {
      if (j < i) {
        newKids[j] = kids[j];
      } else {
        newKids[j] = kids[j + 1];
      }
    }
    this.children = newKids;
    return kid;
  }

  /**
   * Uses the specified {@link HeadFinder {@code HeadFinder}}
   * to determine the heads for this node and all its descendants,
   * and to store references to the head word node and head tag node
   * in this node's {@link CoreLabel {@code CoreLabel}} and the
   * {@code CoreLabel}s of all its descendants.<p>
   * <br>
   * Note that, in contrast to {@link Tree#percolateHeads
   * {@code Tree.percolateHeads()}}, which assumes {@link
   * edu.stanford.nlp.ling.CategoryWordTag
   * {@code CategoryWordTag}} labels and therefore stores head
   * words and head tags merely as {@code String}s, this
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
      } else {
        log.info("Head is null: " + this);
      }
    }
  }

  /**
   * Return the node containing the head word for this node (or
   * {@code null} if none), as recorded in this node's {@link
   * CoreLabel {@code CoreLabel}}.  (In contrast to {@link
   * edu.stanford.nlp.ling.CategoryWordTag
   * {@code CategoryWordTag}}, we store head words and head
   * tags as references to nodes, not merely as
   * {@code String}s.)
   *
   * @return the node containing the head word for this node
   */
  public TreeGraphNode headWordNode() {
    return headWordNode;
   }

  /**
   * Store the node containing the head word for this node by
   * storing it in this node's {@link CoreLabel
   * {@code CoreLabel}}.  (In contrast to {@link
   * edu.stanford.nlp.ling.CategoryWordTag
   * {@code CategoryWordTag}}, we store head words and head
   * tags as references to nodes, not merely as
   * {@code String}s.)
   *
   * @param hwn the node containing the head word for this node
   */
  private void setHeadWordNode(final TreeGraphNode hwn) {
    this.headWordNode = hwn;
  }

  /**
   * Safely casts an {@code Object} to a
   * {@code TreeGraphNode} if possible, else returns
   * {@code null}.
   *
   * @param t any {@code Object}
   * @return {@code t} if it is a {@code TreeGraphNode};
   *         {@code null} otherwise
   */
  private static TreeGraphNode safeCast(Object t) {
    if (t == null || !(t instanceof TreeGraphNode)) {
      return null;
    }
    return (TreeGraphNode) t;
  }

  /**
   * Checks the node's ancestors to find the highest ancestor with the
   * same {@code headWordNode} as this node.
   */
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
   * Returns a {@code TreeFactory} that produces
   * {@code TreeGraphNode}s.  The {@code Label} of
   * {@code this} is examined, and providing it is not
   * {@code null}, a {@code LabelFactory} which will
   * produce that kind of {@code Label} is supplied to the
   * {@code TreeFactory}.  If the {@code Label} is
   * {@code null}, a
   * {@code CoreLabel.factory()} will be used.  The factories
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
      lf = CoreLabel.factory();
    }
    return new TreeGraphNodeFactory(lf);
  }

  /**
   * Return a {@code TreeFactory} that produces trees of type
   * {@code TreeGraphNode}.  The factory returned is always
   * the same one (a singleton).
   *
   * @return a factory to produce treegraphs
   */
  public static TreeFactory factory() {
    return TreeFactoryHolder.tgnf;
  }

  /**
   * Return a {@code TreeFactory} that produces trees of type
   * {@code TreeGraphNode}, with the {@code Label} made
   * by the supplied {@code LabelFactory}.  The factory
   * returned is a different one each time.
   *
   * @param lf The {@code LabelFactory} to use
   * @return a factory to produce treegraphs
   */
  public static TreeFactory factory(LabelFactory lf) {
    return new TreeGraphNodeFactory(lf);
  }

  /**
   * Returns a {@code String} representation of this node and
   * its subtree with one node per line, indented according to
   * {@code indentLevel}.
   *
   * @param indentLevel how many levels to indent (0 for root node)
   * @return {@code String} representation of this subtree
   */
  public String toPrettyString(int indentLevel) {
    StringBuilder buf = new StringBuilder("\n");
    for (int i = 0; i < indentLevel; i++) {
      buf.append("  ");
    }
    if (children == null || children.length == 0) {
      buf.append(label.toString(CoreLabel.OutputFormat.VALUE_INDEX_MAP));
    } else {
      buf.append('(').append(label.toString(CoreLabel.OutputFormat.VALUE_INDEX_MAP));
      for (TreeGraphNode child : children) {
        buf.append(' ').append(child.toPrettyString(indentLevel + 1));
      }
      buf.append(')');
    }
    return buf.toString();
  }

  /**
   * Returns a {@code String} representation of this node and
   * its subtree as a one-line parenthesized list.
   *
   * @return {@code String} representation of this subtree
   */
  public String toOneLineString() {
    StringBuilder buf = new StringBuilder();
    if (children == null || children.length == 0) {
      buf.append(label);
    } else {
      buf.append('(').append(label);
      for (TreeGraphNode child : children) {
        buf.append(' ').append(child.toOneLineString());
      }
      buf.append(')');
    }
    return buf.toString();
  }


  @Override
  public String toString() {
    return toString(CoreLabel.DEFAULT_FORMAT);
  }

  public String toString(CoreLabel.OutputFormat format) {
    return label.toString(format);
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
      EnglishGrammaticalStructure gs = new EnglishGrammaticalStructure(tgn);
      System.out.println(tgn.toPrettyString(0));
      tgn.percolateHeads(new SemanticHeadFinder());
      System.out.println(tgn.toPrettyString(0));
    } catch (Exception e) {
      log.error("Horrible error: " + e);
      log.error(e);
    }
  }

  private static final long serialVersionUID = 5080098143617475328L;

}
