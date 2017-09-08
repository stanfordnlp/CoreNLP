package old.edu.stanford.nlp.trees;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

import old.edu.stanford.nlp.ling.CoreLabel;
import old.edu.stanford.nlp.ling.CyclicCoreLabel;
import old.edu.stanford.nlp.ling.HasTag;
import old.edu.stanford.nlp.ling.HasWord;
import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.LabelFactory;
import old.edu.stanford.nlp.ling.LabeledWord;
import old.edu.stanford.nlp.ling.Sentence;
import old.edu.stanford.nlp.ling.TaggedWord;
import old.edu.stanford.nlp.ling.Word;
import old.edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import old.edu.stanford.nlp.ling.CoreAnnotations.SpanAnnotation;
import old.edu.stanford.nlp.ling.CoreAnnotations.WordAnnotation;
import old.edu.stanford.nlp.util.CoreMap;
import old.edu.stanford.nlp.util.Filter;
import old.edu.stanford.nlp.util.Filters;
import old.edu.stanford.nlp.util.IntPair;
import old.edu.stanford.nlp.util.MutableInteger;
import old.edu.stanford.nlp.util.Scored;
import old.edu.stanford.nlp.util.StringUtils;

/**
 * The abstract class <code>Tree</code> is used to collect all of the
 * tree types, and acts as a generic extendable type.  This is the
 * standard implementation of inheritance-based polymorphism.
 * All <code>Tree</code> objects support accessors for their children (a
 * <code>Tree[]</code>), their label (a <code>Label</code>), and their
 * score (a <code>double</code>).  However, different concrete
 * implementations may or may not include the latter two, in which
 * case a default value is returned.  The class Tree defines no data
 * fields.  The two abstract methods that must be implemented are:
 * <code>children()</code>, and <code>treeFactory()</code>.  Notes
 * that <code>setChildren(Tree[])</code> is now an optional
 * operation, whereas it was previously required to be
 * implemented. There is now support for finding the parent of a
 * tree.  This may be done by search from a tree root, or via a
 * directly stored parent.  The <code>Tree</code> class now
 * implements the <code>Collection</code> interface: in terms of
 * this, each <i>node</i> of the tree is an element of the
 * collection; hence one can explore the tree by using the methods of
 * this interface.  A <code>Tree</code> is regarded as a read-only
 * <code>Collection</code> (even though the <code>Tree</code> class
 * has various methods that modify trees).  Moreover, the
 * implementation is <i>not</i> thread-safe: no attempt is made to
 * detect and report concurrent modifications.
 *
 * @author Christopher Manning
 * @author Dan Klein
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - filled in types
 */
public abstract class Tree extends AbstractCollection<Tree> implements Label, Labeled, Scored, Serializable {

  private static final long serialVersionUID = 5441849457648722744L;

  private double score = Double.NaN;

  /**
   * A leaf node should have a zero-length array for its
   * children. For efficiency, classes can use this array as a
   * return value for children() for leaf nodes if desired.
   * This can also be used elsewhere when you want an empty Tree array.
   */
  public static final Tree[] EMPTY_TREE_ARRAY = new Tree[0];

  public Tree() {
  }

  /**
   * Says whether a node is a leaf.  Can be used on an arbitrary
   * <code>Tree</code>.  Being a leaf is defined as having no
   * children.  This must be implemented as returning a zero-length
   * Tree[] array for children().  This is the preferred
   * alternative to running meta checks on types, as it works
   * independent of Tree implementation.
   *
   * @return true if this object is a leaf
   */
  public boolean isLeaf() {
    Tree[] kids = children();
    return kids.length == 0;
  }


  /**
   * Says how many children a tree node has in its local tree.
   * Can be used on an arbitrary <code>Tree</code>.  Being a leaf is defined
   * as having no children.
   *
   * @return The number of direct children of the tree node
   */
  public int numChildren() {
    return children().length;
  }


  /**
   * Says whether the current node has only one child.
   * Can be used on an arbitrary <code>Tree</code>.
   *
   * @return Whether the node heads a unary rewrite
   */
  public boolean isUnaryRewrite() {
    return numChildren() == 1;
  }


  /**
   * Return whether this node is a preterminal or not.  A preterminal is
   * defined to be a node with one child which is itself a leaf.
   *
   * @return true if the node is a preterminal; false otherwise
   */
  public boolean isPreTerminal() {
    Tree[] kids = children();
    return (kids.length == 1) && (kids[0].isLeaf());
  }


  /**
   * Return whether all the children of this node are preterminals or not.
   * A preterminal is
   * defined to be a node with one child which is itself a leaf.
   * Considered false if the node has no children
   *
   * @return true if the node is a prepreterminal; false otherwise
   */
  public boolean isPrePreTerminal() {
    Tree[] kids = children();
    if (kids.length == 0) {
      return false;
    }
    for (Tree kid : kids) {
      if ( ! kid.isPreTerminal()) {
        return false;
      }
    }
    return true;
  }


  /**
   * Return whether this node is a phrasal node or not.  A phrasal node
   * is defined to be a node which is not a leaf or a preterminal.
   * Worded positively, this means that it must have two or more children,
   * or one child that is not a leaf.
   *
   * @return <code>true</code> if the node is phrasal;
   *         <code>false</code> otherwise
   */
  public boolean isPhrasal() {
    Tree[] kids = children();
    return !(kids == null || kids.length == 0 || (kids.length == 1 && kids[0].isLeaf()));
  }


  /**
   * Implements equality for Tree's.  Two Tree objects are equal if they
   * have equal Labels, the same number of children, and their children
   * are pairwise equal.
   *
   * @param o The object to compare with
   * @return Whether two things are equal
   */
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof Tree)) {
      return false;
    }
    Tree t = (Tree) o;
    if (!(label().equals(t.label()))) {
      return false;
    }
    Tree[] mykids = children();
    Tree[] theirkids = t.children();
    //if((mykids == null && (theirkids == null || theirkids.length != 0)) || (theirkids == null && mykids.length != 0) || (mykids.length != theirkids.length)){
    if (mykids.length != theirkids.length) {
      return false;
    }
    for (int i = 0; i < mykids.length; i++) {
      if (!mykids[i].equals(theirkids[i])) {
        return false;
      }
    }
    return true;
  }


  /**
   * Implements a hashCode for Tree's.  Two trees should have the same
   * hashcode if they are equal, so we hash on the label, the label and
   * the children's labels.
   *
   * @return The hash code
   */
  @Override
  public int hashCode() {
    Label l = label();
    int hc = (l == null) ? 1 : l.hashCode();
    Tree[] kids = children();
    for (int i = 0; i < kids.length; i++) {
      l = kids[i].label();
      int hc2 = (l == null) ? i : l.hashCode();
      hc ^= (hc2 << i);
    }
    return hc;
  }


  /**
   * Returns the position of a Tree in the children list, if present, or
   * -1 if it is not present.  Trees are checked for presence with
   * <code>equals()</code>.
   *
   * @param tree The tree to look for in children list
   * @return Its index in the list or -1
   */
  public int indexOf(Tree tree) {
    Tree[] kids = children();
    for (int i = 0; i < kids.length; i++) {
      if (kids[i].equals(tree)) {
        return i;
      }
    }
    return -1;
  }


  /**
   * Returns an array of children for the current node.  If there
   * are no children (if the node is a leaf), this must return a
   * Tree[] array of length 0.  A null children() value for tree
   * leaves was previously supported, but no longer is.
   * A caller may assume that either <code>isLeaf()</code> returns
   * true, or this node has a nonzero number of children.
   *
   * @return The children of the node
   * @see #getChildrenAsList()
   */
  public abstract Tree[] children();


  /**
   * Returns a List of children for the current node.  If there are no
   * children, then a (non-null) <code>List&lt;Tree&gt;</code> of size 0 will
   * be returned.  The list has new list structure but pointers to,
   * not copies of the children.  That is, the returned list is mutable,
   * and simply adding to or deleting items from it is safe, but beware
   * changing the contents of the children.
   *
   * @return The children of the node
   */
  public List<Tree> getChildrenAsList() {
    return new ArrayList<Tree>(Arrays.asList(children()));
  }


  /**
   * Set the children of this node to be the children given in the
   * array.  This is an <b>optional</b> operation; by default it is
   * unsupported.  Note for subclasses that if there are no
   * children, the children() method must return a Tree[] array of
   * length 0.  This class gives subclasses access to a protected
   * <code>ZEROCHILDREN</code> canonical zero-length Tree[] array
   * to represent zero children, but it is <i>not</i> required that
   * leaf nodes use this particular zero-length array to represent
   * a leaf node.
   *
   * @param children The array of children, each a <code>Tree</code>
   * @see #setChildren(List)
   */
  public void setChildren(Tree[] children) {
    throw new UnsupportedOperationException();
  }


  /**
   * Set the children of this tree node to the given list.  This
   * method is implemented in the <code>Tree</code> class by
   * converting the <code>List</code> into a tree array and calling
   * the array-based method.  Subclasses which use a
   * <code>List</code>-based representation of tree children should
   * override this method.  This implementation allows the case
   * that the <code>List</code> is <code>null</code>: it yields a
   * node with no children (represented by a canonical zero-length
   * children() array).
   *
   * @param childTreesList A list of trees to become children of the node.
   *          This method does not retain the List that you pass it (copying
   *          is done), but it will retain the individual children (they are
   *          not copied).
   * @see #setChildren(Tree[])
   */
  public void setChildren(List<Tree> childTreesList) {
    if (childTreesList == null || childTreesList.isEmpty()) {
      setChildren(EMPTY_TREE_ARRAY);
    } else {
      int leng = childTreesList.size();
      Tree[] childTrees = new Tree[leng];
      childTreesList.toArray(childTrees);
      setChildren(childTrees);
    }
  }


  /**
   * Returns the label associated with the current node, or null
   * if there is no label.  The default implementation always
   * returns <code>null</code>.
   *
   * @return The label of the node
   */
  public Label label() {
    return null;
  }


  /**
   * Sets the label associated with the current node, if there is one.
   *
   * @param label The label
   */
  public void setLabel(Label label) {
    // a noop
  }


  /**
   * Returns the score associated with the current node, or NaN
   * if there is no score.  The default implementation returns NaN.
   *
   * @return The score
   */
  public double score() {
    return score;
  }


  /**
   * Sets the score associated with the current node, if there is one.
   *
   * @param score The score
   */
  public void setScore(double score) {
    this.score = score;
  }


  /**
   * Returns the first child of a tree, or <code>null</code> if none.
   *
   * @return The first child
   */
  public Tree firstChild() {
    Tree[] kids = children();
    if (kids.length == 0) {
      return null;
    }
    return kids[0];
  }


  /**
   * Returns the last child of a tree, or <code>null</code> if none.
   *
   * @return The last child
   */
  public Tree lastChild() {
    Tree[] kids = children();
    if (kids.length == 0) {
      return null;
    }
    return kids[kids.length - 1];
  }

  /** Return the highest node of the (perhaps trivial) unary chain that
   *  this node is part of.
   *  In case this node is the only child of its parent, trace up the chain of
   *  unaries, and return the uppermost node of the chain (the node whose
   *  parent has multiple children, or the node that is the root of the tree).
   *
   *  @param root The root of the tree that contains this subtree
   *  @return The uppermost node of the unary chain, if this node is in a unary
   *         chain, or else the current node
   */
  public Tree upperMostUnary(Tree root) {
    Tree parent = parent(root);
    if (parent == null) {
      return this;
    }
    if (parent.numChildren() > 1) {
      return this;
    }
    return parent.upperMostUnary(root);
  }

  public void setSpans() {
    constituentsNodes(0);
  }

  public IntPair getSpan() {
    return ((CyclicCoreLabel) label()).get(SpanAnnotation.class);
  }

  /**
   * Returns the Constituents generated by the parse tree.
   *
   * @return a Set of the constituents as constituents of
   *         type <code>Constituent</code>
   */
  public Set<Constituent> constituents() {
    return constituents(new SimpleConstituentFactory());
  }


  /**
   * Returns the Constituents generated by the parse tree.
   * The Constituents of a sentence include the preterminal categories
   * but not the leaves.
   *
   * @param cf ConstituentFactory used to build the Constituent objects
   * @return a Set of the constituents as SimpleConstituent type
   *         (in the current implementation, a <code>HashSet</code>
   */
  public Set<Constituent> constituents(ConstituentFactory cf) {
    Set<Constituent> constituentsSet = new HashSet<Constituent>();
    constituents(constituentsSet, 0, cf);
    return constituentsSet;
  }

  /**
   * Same as int constituents but just puts the span as an IntPair
   * in the CyclicCoreLabel of the nodes.
   *
   * @param left The left position to begin labeling from
   * @return The index of the right frontier of the constituent
   */
  private int constituentsNodes(int left) {
    if (isLeaf()) {
      // lexical leaves do not add any Constituents
      // but increment position
      // System.err.println("In bracketing trees leaf is " + label());

      CyclicCoreLabel l = (CyclicCoreLabel) label();
      l.set(SpanAnnotation.class, new IntPair(left, left));
      return (left + 1);
    }
    int position = left;

    // System.err.println("In bracketing trees left is " + left);
    // System.err.println("  label is " + label() +
    //                       "; num daughters: " + children().length);
    // enumerate through daughter trees
    Tree[] kids = children();
    for (int i = 0; i < kids.length; i++) {
      // compute bracketings for daughter tree
      // update position to end of daughter tree
      position = kids[i].constituentsNodes(position);
      // System.err.println("  position went to " + position);
    }
    // need to wait until result position is known in order to
    // calculate span of whole tree
    CyclicCoreLabel l = (CyclicCoreLabel) label();
    l.set(SpanAnnotation.class, new IntPair(left, position - 1));

    return position;
  }

  /**
   * Adds the constituents derived from <code>this</code> tree to
   * the ordered <code>Constituent</code> <code>Set</code>, beginning
   * numbering from the second argument and returning the number of
   * the right edge.  The reason for the return of the right frontier
   * is in order to produce bracketings recursively by threading through
   * the daughters of a given tree.
   *
   * @param constituentsSet set of constituents to add results of bracketing
   *                        this tree to
   * @param left            left position to begin labeling the bracketings with
   * @param cf              ConstituentFactory used to build the Constituent objects
   * @return Index of right frontier of Constituent
   */
  private int constituents(Set<Constituent> constituentsSet, int left, ConstituentFactory cf) {
    if (isLeaf()) {
      // lexical leaves do not add any Constituents
      // but increment position
      // System.err.println("In bracketing trees leaf is " + label());
      return (left + 1);
    }
    int position = left;

    // System.err.println("In bracketing trees left is " + left);
    // System.err.println("  label is " + label() +
    //                       "; num daughters: " + children().length);
    // enumerate through daughter trees
    Tree[] kids = children();
    for (int i = 0; i < kids.length; i++) {
      // compute bracketings for daughter tree
      // update position to end of daughter tree
      position = kids[i].constituents(constituentsSet, position, cf);
      // System.err.println("  position went to " + position);
    }
    // need to wait until result position is known in order to
    // calculate span of whole tree
    constituentsSet.add(cf.newConstituent(left, position, label(), score()));
    // System.err.println("  added " + label());
    return position;
  }


  /**
   * Returns a new Tree that represents the local Tree at a certain node.
   * That is, it builds a new tree that copies the mother and daughter
   * nodes (but not their Labels), as non-Leaf nodes,
   * but zeroes out their children.
   *
   * @return A local tree
   */
  public Tree localTree() {
    Tree[] kids = children();
    Tree[] newKids = new Tree[kids.length];
    TreeFactory tf = treeFactory();
    for (int i = 0, n = kids.length; i < n; i++) {
      newKids[i] = tf.newTreeNode(kids[i].label(), Arrays.asList(EMPTY_TREE_ARRAY));
    }
    return tf.newTreeNode(label(), Arrays.asList(newKids));
  }


  /**
   * Returns a set of one level <code>Tree</code>s that ares the local trees
   * of the tree.
   * That is, it builds a new tree that copies the mother and daughter
   * nodes (but not their Labels), for each phrasal node,
   * but zeroes out their children.
   *
   * @return A set of local tree
   */
  public Set<Tree> localTrees() {
    Set<Tree> set = new HashSet<Tree>();
    for (Tree st : this) {
      if (st.isPhrasal()) {
        set.add(st.localTree());
      }
    }
    return set;
  }


  /** Returns a String reporting what kinds of Tree and Label nodes this
   *  Tree contains.
   */
  public String toStructureDebugString() {
    String leafLabels = null;
    String tagLabels = null;
    String phraseLabels = null;
    String leaves = null;
    String nodes = null;
    for (Tree st : this) {
      if (st.isPhrasal()) {
        if (nodes == null) {
          nodes = StringUtils.getShortClassName(st);
        } else if ( ! nodes.equals(StringUtils.getShortClassName(st))) {
          nodes = "mixed";
        }
        Label lab = st.label();
        if (phraseLabels == null) {
          if (lab == null) {
            phraseLabels = "null";
          } else {
            phraseLabels = StringUtils.getShortClassName(lab);
          }
        } else if ( ! phraseLabels.equals(StringUtils.getShortClassName(lab))) {
          phraseLabels = "mixed";
        }
      } else if (st.isPreTerminal()) {
        if (nodes == null) {
          nodes = StringUtils.getShortClassName(st);
        } else if ( ! nodes.equals(StringUtils.getShortClassName(st))) {
          nodes = "mixed";
        }
        Label lab = st.label();
        if (tagLabels == null) {
          if (lab == null) {
            tagLabels = "null";
          } else {
            tagLabels = StringUtils.getShortClassName(lab);
          }
        } else if ( ! tagLabels.equals(StringUtils.getShortClassName(lab))) {
          tagLabels = "mixed";
        }
      } else if (st.isLeaf()) {
        if (leaves == null) {
          leaves = StringUtils.getShortClassName(st);
        } else if ( ! leaves.equals(StringUtils.getShortClassName(st))) {
          leaves = "mixed";
        }
        Label lab = st.label();
        if (leafLabels == null) {
          if (lab == null) {
            leafLabels = "null";
          } else {
            leafLabels = StringUtils.getShortClassName(lab);
          }
        } else if ( ! leafLabels.equals(StringUtils.getShortClassName(lab))) {
          leafLabels = "mixed";
        }
      } else {
        throw new IllegalStateException("Bad tree: " + this);
      }
    } // end for Tree st : this
    return "Tree with " + nodes + " interior nodes and " + leaves +
      " leaves, and " + phraseLabels + " phrase labels, " +
      tagLabels + " tag labels, and " + leafLabels + " leaf labels.";
  }


  /**
   * Most instances of <code>Tree</code> will take a lot more than
   * than the default <code>StringBuffer</code> size of 16 to print
   * as an indented list of the whole tree, so we enlarge the default.
   */
  private static final int initialPrintStringBuilderSize = 500;


  /**
   * Appends the printed form of a parse tree (as a bracketed String)
   * to an <code>Appendable</code>, such as a <code>StringBuffer</code>.
   * The implementation of this may be more efficient than for
   * <code>toString()</code> on complex trees.
   *
   * @param sb The <code>StringBuilder</code> to which the tree will be appended
   * @return Returns the <code>StringBuilder</code> passed in with extra stuff in it
   */
  public StringBuilder toStringBuilder(StringBuilder sb) {
    sb.append('(');
    if (label() != null) {
      sb.append(label());
    }
    Tree[] kids = children();
    if (kids != null) {
      for (Tree kid : kids) {
        sb.append(' ');
        kid.toStringBuilder(sb);
      }
    }
    return sb.append(')');
  }


  /**
   * Converts parse tree to string in Penn Treebank format.
   * Get efficiency by chaining a single <code>StringBuffer</code>
   * through it all.
   *
   * @return the tree as a bracketed list on one line
   */
  @Override
  public String toString() {
    return toStringBuilder(new StringBuilder(Tree.initialPrintStringBuilderSize)).toString();
  }


  private static final int indentIncr = 2;


  private static String makeIndentString(int indent) {
    StringBuilder sb = new StringBuilder(indent);
    for (int i = 0; i < indentIncr; i++) {
      sb.append(' ');
    }
    return sb.toString();
  }


  public void printLocalTree() {
    printLocalTree(new PrintWriter(System.out, true));
  }

  public void printLocalTree(PrintWriter pw) {
    pw.print("(" + label() + ' ');
    for (Tree kid : children()) {
      pw.print("(");
      pw.print(kid.label());
      pw.print(") ");
    }
    pw.println(")");
  }


  /**
   * Indented list printing of a tree.  The tree is printed in an
   * indented list notation, with nodel labels followed by node scores.
   */
  public void indentedListPrint() {
    indentedListPrint(new PrintWriter(System.out, true), false);
  }


  /**
   * Indented list printing of a tree.  The tree is printed in an
   * indented list notation, with nodel labels followed by node scores.
   *
   * @param pw The PrintWriter to print the tree to
   * @param printScores Whether to print the scores (log probs) of tree nodes
   */
  public void indentedListPrint(PrintWriter pw, boolean printScores) {
    indentedListPrint("", makeIndentString(indentIncr), pw, printScores);
  }


  /**
   * Indented list printing of a tree.  The tree is printed in an
   * indented list notation, with nodel labels followed by node scores.
   * String parameters are used rather than integer levels for efficiency.
   *
   * @param indent The base <code>String</code> (normally just spaces)
   *               to print before each line of tree
   * @param pad    The additional <code>String</code> (normally just more
   *               spaces) to add when going to a deeper level of <code>Tree</code>.
   * @param pw     The PrintWriter to print the tree to
   * @param printScores Whether to print the scores (log probs) of tree nodes
   */
  private void indentedListPrint(String indent, String pad, PrintWriter pw, boolean printScores) {
    StringBuilder sb = new StringBuilder(indent);
    Label label = label();
    if (label != null) {
      sb.append(label.toString());
    }
    if (printScores) {
      sb.append("  ");
      sb.append(score());
    }
    pw.println(sb.toString());
    Tree[] children = children();
    String newIndent = indent + pad;
    for (int i = 0, n = children.length; i < n; i++) {
      children[i].indentedListPrint(newIndent, pad, pw, printScores);
    }
  }


  private static void displayChildren(Tree[] trChildren, int indent, boolean parentLabelNull, PrintWriter pw) {
    boolean firstSibling = true;
    boolean leftSibIsPreTerm = true;  // counts as true at beginning
    for (Tree currentTree : trChildren) {
      currentTree.display(indent, parentLabelNull, firstSibling, leftSibIsPreTerm, false, pw);
      leftSibIsPreTerm = currentTree.isPreTerminal();
      // CC is a special case
      if (currentTree.label() != null && currentTree.label().toString() != null && currentTree.label().toString().startsWith("CC")) {
        leftSibIsPreTerm = false;
      }
      firstSibling = false;
    }
  }

  /** Returns the label of a tree node as a String.  This is done by
   *  calling <code>toString()</code> on the Label, but if the label is
   *  <code>null</code> or has a <code>null</code>, value,
   *  then this method returns an empty String.
   *
   *  @return The label of a tree node as a String
   */
  public String nodeString() {
    if (label() != null && label().toString() != null) {
      return label().toString();
    }
    return "";
  }

  public static boolean DISPLAY_SCORES = true;

  /**
   * Display a node, implementing Penn Treebank style layout
   */
  private void display(int indent, boolean parentLabelNull, boolean firstSibling, boolean leftSiblingPreTerminal, boolean topLevel, PrintWriter pw) {
    // the condition for staying on the same line in Penn Treebank
    boolean suppressIndent = (parentLabelNull || (firstSibling && isPreTerminal()) || (leftSiblingPreTerminal && isPreTerminal() && (label() == null || !label().toString().startsWith("CC"))));
    if (suppressIndent) {
      pw.print(" ");
      // pw.flush();
    } else {
      if (!topLevel) {
        pw.println();
      }
      for (int i = 0; i < indent; i++) {
        pw.print("  ");
        // pw.flush();
      }
    }
    if (isLeaf() || isPreTerminal()) {
      pw.print(toString());
      pw.flush();
      return;
    }
    pw.print("(");
    pw.print(nodeString());
    // pw.flush();
    displayChildren(children(), indent + 1, label() == null || label().toString() == null, pw);
    pw.print(")");
    pw.flush();
  }


  /**
   * Print the tree as done in Penn Treebank merged files.
   * The formatting should be exactly the same, but we don't print the
   * trailing whitespace found in Penn Treebank trees.
   * The basic deviation from a bracketed indented tree is to in general
   * collapse the printing of adjacent preterminals onto one line of
   * tasg and words.  Additional complexities are that conjunctions
   * (tag CC) are not collapsed in this way, and that the unlabeled
   * outer brackets are collapsed onto the same line as the next
   * bracket down.
   *
   * @param pw The tree is printed to this <code>PrintWriter</code>
   */
  public void pennPrint(PrintWriter pw) {
    display(0, false, false, false, true, pw);
    pw.println();
    pw.flush();
  }


  /**
   * Print the tree as done in Penn Treebank merged files.
   * The formatting should be exactly the same, but we don't print the
   * trailing whitespace found in Penn Treebank trees.
   * The basic deviation from a bracketed indented tree is to in general
   * collapse the printing of adjacent preterminals onto one line of
   * tags and words.  Additional complexities are that conjunctions
   * (tag CC) are not collapsed in this way, and that the unlabeled
   * outer brackets are collapsed onto the same line as the next
   * bracket down.
   *
   * @param ps The tree is printed to this <code>PrintStream</code>
   */
  public void pennPrint(PrintStream ps) {
    pennPrint(new PrintWriter(new OutputStreamWriter(ps), true));
  }

  /**
   * Calls <code>pennPrint()</code> and saves output to a String
   *
   * @return The indent S-expression representation of a Tree
   */
  public String pennString() {
    StringWriter sw = new StringWriter();
    pennPrint(new PrintWriter(sw));
    return sw.toString();
  }

  /**
   * Print the tree as done in Penn Treebank merged files.
   * The formatting should be exactly the same, but we don't print the
   * trailing whitespace found in Penn Treebank trees.
   * The tree is printed to <code>System.out</code>. The basic deviation
   * from a bracketed indented tree is to in general
   * collapse the printing of adjacent preterminals onto one line of
   * tags and words.  Additional complexities are that conjunctions
   * (tag CC) are not collapsed in this way, and that the unlabeled
   * outer brackets are collapsed onto the same line as the next
   * bracket down.
   */
  public void pennPrint() {
    pennPrint(System.out);
  }


  /**
   * Finds the depth of the tree.  The depth is defined as the length
   * of the longest path from this node to a leaf node.  Leaf nodes
   * have depth zero.  POS tags have depth 1. Phrasal nodes have
   * depth &gt;= 2.
   *
   * @return the depth
   */
  public int depth() {
    if (isLeaf()) {
      return 0;
    }
    int maxDepth = 0;
    Tree[] kids = children();
    for (int i = 0; i < kids.length; i++) {
      int curDepth = kids[i].depth();
      if (curDepth > maxDepth) {
        maxDepth = curDepth;
      }
    }
    return maxDepth + 1;
  }

  /**
   * Finds the distance from this node to the specified node.
   * return -1 if this is not an ancestor of node.
   *
   * @param node A subtree contained in this tree
   * @return the depth
   */
  public int depth(Tree node) {
    Tree p = node.parent(this);
    if (this == node) { return 0; }
    if (p == null) { return -1; }
    int depth = 1;
    while (this != p) {
      p = p.parent(this);
      depth++;
    }
    return depth;
  }


  /**
   * Returns the tree leaf that is the head of the tree.
   *
   * @param hf The headfinding algorithm to use
   * @param parent  The parent of this tree
   * @return The head tree leaf if any, else <code>null</code>
   */
  public Tree headTerminal(HeadFinder hf, Tree parent) {
    if (isLeaf()) {
      return this;
    }
    Tree head = hf.determineHead(this, parent);
    if (head != null) {
      return head.headTerminal(hf, parent);
    }
    System.err.println("Head is null: " + this);
    return null;
  }

  /**
   * Returns the tree leaf that is the head of the tree.
   *
   * @param hf The headfinding algorithm to use
   * @return The head tree leaf if any, else <code>null</code>
   */
  public Tree headTerminal(HeadFinder hf) {
    return headTerminal(hf, null);
  }


  /**
   * Returns the preterminal tree that is the head of the tree.
   * See {@link #isPreTerminal()} for
   * the definition of a preterminal node. Beware that some tree nodes may
   * have no preterminal head.
   *
   * @param hf The headfinding algorithm to use
   * @return The head preterminal tree, if any, else <code>null</code>
   * @throws IllegalArgumentException if called on a leaf node
   */
  public Tree headPreTerminal(HeadFinder hf) {
    if (isPreTerminal()) {
      return this;
    } else if (isLeaf()) {
      throw new IllegalArgumentException("Called headPreTerminal on a leaf: " + this);
    } else {
      Tree head = hf.determineHead(this);
      if (head != null) {
        return head.headPreTerminal(hf);
      }
      System.err.println("Head preterminal is null: " + this);
      return null;
    }
  }


  /**
   * Finds the heads of the tree.  This code assumes that the label
   * does store and return sensible values for the category, word, and tag.
   * It will be a no-op otherwise.  The tree is modified.  The routine
   * assumes the Tree has word leaves and tag preterminals, and copies
   * their category to word and tag respectively, if they have a null
   * value.
   *
   * @param hf The headfinding algorithm to use
   */
  public void percolateHeads(HeadFinder hf) {
    Label cwt = label();
    if (isLeaf()) {
      if (cwt instanceof HasWord) {
        HasWord w = (HasWord) cwt;
        if (w.word() == null) {
          w.setWord(cwt.value());
        }
      }
    } else {
      Tree[] kids = children();
      for (int i = 0; i < kids.length; i++) {
        kids[i].percolateHeads(hf);
      }
      Tree head = hf.determineHead(this);
      if (head != null) {
        Label headCwt = head.label();
        String headTag = null;
        if (headCwt instanceof HasTag) {
          headTag = ((HasTag) headCwt).tag();
        }
        if (headTag == null && head.isLeaf()) {
          // below us is a leaf
          headTag = cwt.value();
        }
        String headWord = null;
        if (headCwt instanceof HasWord) {
          headWord = ((HasWord) headCwt).word();
        }
        if (headWord == null && head.isLeaf()) {
          // below us is a leaf
          // this might be useful despite case for leaf above in
          // case the leaf label type doesn't support word()
          headWord = headCwt.value();
        }
        if (cwt instanceof HasWord) {
          ((HasWord) cwt).setWord(headWord);
        }
        if (cwt instanceof HasTag) {
          ((HasTag) cwt).setTag(headTag);
        }
      } else {
        System.err.println("Head is null: " + this);
      }
    }
  }


  /**
   * Return a set of Word-Word dependencies, represented as
   * Dependency objects, for the Tree.  This will only give
   * useful results if the internal tree node labels support HasWord and
   * head percolation has already been done (see percolateHeads()).
   *
   * @return Set of dependencies (each a Dependency)
   */
  public Set<Dependency<Label, Label, Object>> dependencies() {
    return dependencies(Filters.<Dependency<Label, Label, Object>>acceptFilter());
  }

  /**
   * Return a set of Word-Word dependencies, represented as Dependency
   * objects, for the Tree.  This will only give
   * useful results if the internal tree node labels support HasWord and
   * head percolation has already been done (see percolateHeads()).
   *
   * @param f Dependencies are excluded for which the Dependency is not
   *          accepted by the Filter
   * @return Set of dependencies (each a Dependency)
   */
  public Set<Dependency<Label, Label, Object>> dependencies(Filter<Dependency<Label, Label, Object>> f) {
    return dependencies(f, null);
  }


  /**
   * Return a set of Word-Word dependencies, represented as
   * Dependency objects for the Tree.
   *
   * @param hf The HeadFinder to use to identify the head of constituents
   * @return Set of dependencies (each a <code>Dependency</code>)
   */
  public Set<Dependency<Label, Label, Object>> dependencies(HeadFinder hf) {
    return dependencies(Filters.<Dependency<Label, Label, Object>>acceptFilter(), hf);
  }


  /**
   * Return a set of Word-Word dependencies, represented as Dependency
   * objects, for the Tree.
   *
   * @param f  Dependencies are excluded for which the Dependency is not
   *           accepted by the Filter
   * @param hf The HeadFinder to use to identify the head of constituents.
   *           If this is <code>null</code>, then nodes are assumed to already
   *           be marked with their heads.
   * @return Set of dependencies (each a <code>Dependency</code>)
   */
  public Set<Dependency<Label, Label, Object>> dependencies(Filter<Dependency<Label, Label, Object>> f, HeadFinder hf) {
    Set<Dependency<Label, Label, Object>> deps = new HashSet<Dependency<Label, Label, Object>>();
    for (Tree node : subTrees()) {
      if (node.isLeaf() || node.children().length < 2) {
        continue;
      }
      // every child with a different head (or repeated) is an argument
      Label l = node.label();
      Word w = null;
      if (hf != null) {
        Tree hwt = node.headTerminal(hf);
        if (hwt != null) {
          w = new Word(hwt.label());
        }
      } else {
        if (l instanceof HasWord) {
          w = new Word(((HasWord) l).word());
        }
      }
      Tree[] kids = node.children();
      boolean seenHead = false;
      for (int cNum = 0; cNum < kids.length; cNum++) {
        Tree child = kids[cNum];
        Label dl = child.label();
        Word dw = null;
        if (hf != null) {
          Tree dwt = child.headTerminal(hf);
          if (dwt != null) {
            dw = new Word(dwt.label());
          }
        } else {
          if (dl instanceof HasWord) {
            dw = new Word(((HasWord) dl).word());
          }
        }
        // System.out.println("XX Doing pair: " + l + ", " + dl +
        //                 " gives " + w + ", " +dw);
        if (w != null && w.word() != null && dw != null && w.word().equals(dw.word()) && !seenHead) {
          seenHead = true;
        } else {
          Dependency<Label, Label, Object> p = new UnnamedDependency(w, dw);
          if (f.accept(p)) {
            deps.add(p);
          }
        }
      }
    }
    return deps;
  }


  /**
   * Return a Set of TaggedWord-TaggedWord dependencies, represented as
   * Dependency objects, for the Tree.  This will only give
   * useful results if the internal tree node labels support HasWord and
   * HasTag, and head percolation has already been done (see
   * percolateHeads()).
   *
   * @return Set of dependencies (each a Dependency)
   */
  public Set<Dependency<Label, Label, Object>> taggedDependencies() {
    return taggedDependencies(Filters.<Dependency<Label, Label, Object>>acceptFilter());
  }

  /**
   * Return a set of TaggedWord-TaggedWord dependencies, represented as
   * Dependency objects, for the Tree.  This will only give
   * useful results if the internal tree node labels support HasWord and
   * head percolation has already been done (see percolateHeads()).
   * <p><i>Implementation note:</i> It would be nice to generalize this with
   * dependencies() to use a LabelFactory, but it seems impossible with the
   * current setup.
   *
   * @param f Dependencies are excluded for which the Dependency is not
   *          accepted by the Filter
   * @return Set of dependencies (each a Dependency)
   */
  public Set<Dependency<Label, Label, Object>> taggedDependencies(Filter<Dependency<Label, Label, Object>> f) {
    Set<Dependency<Label, Label, Object>> deps = new HashSet<Dependency<Label, Label, Object>>();
    for (Tree node : this) {
      if (node.isLeaf() || node.children().length < 2) {
        continue;
      }
      // every child with a different head (or repeated) is an argument
      Label l = node.label();
      TaggedWord tw = null;
      if (l instanceof HasWord && l instanceof HasTag) {
        tw = new TaggedWord(((HasWord) l).word(), ((HasTag) l).tag());
      }

      boolean seenHead = false;
      for (Tree child : node.children()) {
        Label dl = child.label();
        TaggedWord dtw = null;
        if (dl instanceof HasWord && dl instanceof HasTag) {
          dtw = new TaggedWord(((HasWord) dl).word(), ((HasTag) dl).tag());
        }
        if (tw != null && tw.word() != null && dtw != null && tw.word().equals(dtw.word()) && tw.tag().equals(dtw.tag()) && !seenHead) {
          seenHead = true;
        } else {
          Dependency<Label, Label, Object> p = new UnnamedDependency(tw, dtw);
          if (f.accept(p)) {
            deps.add(p);
          }
        }
      }
    }
    return deps;
  }


  /**
   * Return a set of TaggedWord-TaggedWord dependencies, represented as
   * Dependency objects for the Tree.
   *
   * @param hf The HeadFinder to use to identify the head of constituents
   * @return Set of dependencies (each a <code>Dependency</code>)
   */
  public Set<Dependency<Label, Label, Object>> taggedDependencies(HeadFinder hf) {
    return taggedDependencies(Filters.<Dependency<Label, Label, Object>>acceptFilter(), hf);
  }

  /**
   * Return a set of TaggedWord-TaggedWord dependencies, represented as
   * Dependency objects, for the Tree.
   *
   * @param f  Dependencies are excluded for which the Dependency is not
   *           accepted by the Filter
   * @param hf The HeadFinder to use to identify the head of constituents.
   *           If this is <code>null</code>, then nodes are assumed to already
   *           be marked with their heads.
   * @return Set of dependencies (each a <code>Dependency</code>)
   */
  public Set<Dependency<Label, Label, Object>> taggedDependencies(Filter<Dependency<Label, Label, Object>> f, HeadFinder hf) {
    Set<Dependency<Label, Label, Object>> deps = new HashSet<Dependency<Label, Label, Object>>();
    for (Tree node : this) {
      if (node.isLeaf() || node.children().length < 2) {
        continue;
      }
      // every child with a different head (or repeated) is an argument
      Label l = node.label();
      TaggedWord w = null;
      if (hf != null) {
        Tree hwt = node.headPreTerminal(hf);
        if (hwt != null) {
          w = new TaggedWord(hwt.children()[0].label(), hwt.label());
        }
      } else {
        if (l instanceof HasWord && l instanceof HasTag) {
          w = new TaggedWord(((HasWord) l).word(), ((HasTag) l).tag());
        }
      }

      boolean seenHead = false;
      for (Tree child : node.children()) {
        Label dl = child.label();
        TaggedWord dw = null;
        if (hf != null) {
          Tree dwt = child.headPreTerminal(hf);
          if (dwt != null) {
            dw = new TaggedWord(dwt.children()[0].label(), dwt.label());
          }
        } else {
          if (dl instanceof HasWord && dl instanceof HasTag) {
            dw = new TaggedWord(((HasWord) dl).word(), ((HasTag) dl).tag());
          }
        }
        // System.out.println("XX Doing pair: " + l + ", " + dl +
        //                 " gives " + w + ", " +dw);
        if (w != null && w.word() != null && w.tag() != null && dw != null && w.word().equals(dw.word()) && w.tag().equals(dw.tag()) && !seenHead) {
          seenHead = true;
        } else {
          Dependency<Label, Label, Object> p = new UnnamedDependency(w, dw);
          if (f.accept(p)) {
            deps.add(p);
          }
        }
      }
    }
    return deps;
  }

  /**
   * Return a set of Label-Label dependencies, represented as
   * Dependency objects, for the Tree.  The Labels are the ones of the leaf
   * nodes of the tree, without mucking with them.
   *
   * @param f  Dependencies are excluded for which the Dependency is not
   *           accepted by the Filter
   * @param hf The HeadFinder to use to identify the head of constituents.
   *           The code assumes
   *           that it can use <code>headPreTerminal(hf)</code> to find a
   *           tag and word to make a CyclicCoreLabel.
   * @return Set of dependencies (each a <code>Dependency</code> between two
   *           <code>CyclicCoreLabel</code>s, which each contain a tag(), word(),
   *           and value(), the last two of which are identical).
   */
  public Set<Dependency<Label, Label, Object>> mapDependencies(Filter<Dependency<Label, Label, Object>> f, HeadFinder hf) {
    if (hf == null) {
      throw new IllegalArgumentException("mapDependencies: need headfinder");
    }
    Set<Dependency<Label, Label, Object>> deps = new HashSet<Dependency<Label, Label, Object>>();
    for (Tree node : this) {
      if (node.isLeaf() || node.children().length < 2) {
        continue;
      }
      // every child with a different head (or repeated) is an argument
      // Label l = node.label();
      // System.err.println("doing kids of label: " + l);
      //Tree hwt = node.headPreTerminal(hf);
      Tree hwt = node.headTerminal(hf);
      // System.err.println("have hf, found head preterm: " + hwt);
      if (hwt == null) {
        throw new IllegalStateException("mapDependencies: headFinder failed!");
      }

      for (Tree child : node.children()) {
        // Label dl = child.label();
        // Tree dwt = child.headPreTerminal(hf);
        Tree dwt = child.headTerminal(hf);
        if (dwt == null) {
          throw new IllegalStateException("mapDependencies: headFinder failed!");
        }
        //System.err.println("kid is " + dl);
         //System.err.println("transformed to " + dml.toString("value{map}"));
        if (dwt != hwt) {
          Dependency<Label, Label, Object> p = new UnnamedDependency(hwt.label(), dwt.label());
          if (f.accept(p)) {
            deps.add(p);
          }
        }
      }
    }
    return deps;
  }

  /**
   * Return a set of Label-Label dependencies, represented as
   * Dependency objects, for the Tree.  The Labels are the ones of the leaf
   * nodes of the tree, without mucking with them. The head of the sentence is a
   * dependent of a synthetic "root" label.
   *
   * @param f  Dependencies are excluded for which the Dependency is not
   *           accepted by the Filter
   * @param hf The HeadFinder to use to identify the head of constituents.
   *           The code assumes
   *           that it can use <code>headPreTerminal(hf)</code> to find a
   *           tag and word to make a CyclicCoreLabel.
   * @param    rootName Name of the root node.
   * @return   Set of dependencies (each a <code>Dependency</code> between two
   *           <code>CyclicCoreLabel</code>s, which each contain a tag(), word(),
   *           and value(), the last two of which are identical).
   */
  public Set<Dependency<Label, Label, Object>> mapDependencies(Filter<Dependency<Label, Label, Object>> f, HeadFinder hf, String rootName) {
    Set<Dependency<Label, Label, Object>> deps = mapDependencies(f, hf);
    if(rootName != null) {
      Label hl = headTerminal(hf).label();
      CyclicCoreLabel rl = new CyclicCoreLabel();
      rl.set(WordAnnotation.class, rootName);
      rl.set(IndexAnnotation.class, 0);
      deps.add(new NamedDependency(rl, hl, rootName));
    }
    return deps;
  }

  /**
   * Gets the yield of the tree.  The <code>Label</code> of all leaf nodes
   * is returned
   * as a list ordered by the natural left to right order of the
   * leaves.  Null values, if any, are inserted into the list like any
   * other value.
   *
   * @return a <code>List</code> of the data in the tree's leaves.
   */
  public <X extends HasWord> Sentence<X> yield() {
    return yield(new Sentence<X>());
  }

  /**
   * Gets the yield of the tree.  The <code>Label</code> of all leaf nodes
   * is returned
   * as a list ordered by the natural left to right order of the
   * leaves.  Null values, if any, are inserted into the list like any
   * other value.
   * <p><i>Implementation notes:</i> c. 2003: This has been rewritten to thread, so only one List
   * is used. 2007: This method was duplicated to start to give type safety to Sentence.
   * This method will now make a Word for any Leaf which does not itself implement HasWord, and
   * put the Word into the Sentence, so the Sentence elements MUST implement HasWord.
   *
   * @param y The list in which the yield of the tree will be placed.
   *          Normally, this will be empty when the routine is called, but
   *          if not, the new yield is added to the end of the list.
   * @return a <code>List</code> of the data in the tree's leaves.
   */
  @SuppressWarnings("unchecked")
  public <X extends HasWord> Sentence<X> yield(Sentence<X> y) {
    if (isLeaf()) {
      Label lab = label();
      // cdm: this is new hacked in stuff in Mar 2007 so we can now have a
      // well-typed version of a Sentence, whose objects MUST implement HasWord
      if (lab instanceof HasWord) {
        y.add((X) lab);
      } else {
        y.add((X) new Word(lab));
      }
    } else {
      Tree[] kids = children();
      for (int i = 0; i < kids.length; i++) {
        kids[i].yield(y);
      }
    }
    return y;
  }


  /**
   * Gets the yield of the tree.  The <code>Label</code> of all leaf nodes
   * is returned
   * as a list ordered by the natural left to right order of the
   * leaves.  Null values, if any, are inserted into the list like any
   * other value.  This has been rewritten to thread, so only one List
   * is used.
   *
   * @param y The list in which the yield of the tree will be placed.
   *          Normally, this will be empty when the routine is called, but
   *          if not, the new yield is added to the end of the list.
   * @return a <code>List</code> of the data in the tree's leaves.
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> yield(List<T> y) {
    if (isLeaf()) {
      y.add((T) label());
    } else {
      Tree[] kids = children();
      for (int i = 0; i < kids.length; i++) {
        kids[i].yield(y);
      }
    }
    return y;
  }

  /**
   * Gets the tagged yield of the tree.
   * The <code>Label</code> of all leaf nodes is returned
   * as a list ordered by the natural left to right order of the
   * leaves.  Null values, if any, are inserted into the list like any
   * other value.
   *
   * @return a <code>List</code> of the data in the tree's leaves.
   */
  public Sentence<TaggedWord> taggedYield() {
    return taggedYield(new Sentence<TaggedWord>());
  }

  public List<LabeledWord> labeledYield() {
    return labeledYield(new ArrayList<LabeledWord>());
  }

  /**
   * Gets the tagged yield of the tree -- that is, get the preterminals
   * as well as the terminals.  The <code>Label</code> of all leaf nodes
   * is returned
   * as a list ordered by the natural left to right order of the
   * leaves.  Null values, if any, are inserted into the list like any
   * other value.  This has been rewritten to thread, so only one List
   * is used.
   * <p/>
   * <i>Implementation note:</i> when we summon up enough courage, this
   * method will be changed to take and return a List<W extends TaggedWord>.
   *
   * @param ty The list in which the tagged yield of the tree will be
   *           placed. Normally, this will be empty when the routine is called,
   *           but if not, the new yield is added to the end of the list.
   * @return a <code>List</code> of the data in the tree's leaves.
   */
  public <X extends List<TaggedWord>> X taggedYield(X ty) {
    Tree[] kids = children();
    // this inlines the content of isPreTerminal()
    if (kids.length == 1 && kids[0].isLeaf()) {
      ty.add(new TaggedWord(kids[0].label(), label()));
    } else {
      for (Tree kid : kids) {
        kid.taggedYield(ty);
      }
    }
    return ty;
  }

  public List<LabeledWord> labeledYield(List<LabeledWord> ty) {
    Tree[] kids = children();
    // this inlines the content of isPreTerminal()
    if (kids.length == 1 && kids[0].isLeaf()) {
      ty.add(new LabeledWord(kids[0].label(), label()));
    } else {
      for (Tree kid : kids) {
        kid.labeledYield(ty);
      }
    }
    return ty;
  }

  /**
   * Gets the preterminal yield (i.e., tags) of the tree.  All data in
   * preleaf nodes is returned as a list ordered by the natural left to
   * right order of the tree.  Null values, if any, are inserted into the
   * list like any other value.  Pre-leaves are nodes of height 1.
   *
   * @return a <code>List</code> of the data in the tree's pre-leaves.
   */
  public List<Label> preTerminalYield() {
    return preTerminalYield(new ArrayList<Label>());
  }


  /**
   * Gets the preterminal yield (i.e., tags) of the tree.  All data in
   * preleaf nodes is returned as a list ordered by the natural left to
   * right order of the tree.  Null values, if any, are inserted into the
   * list like any other value.  Pre-leaves are nodes of height 1.
   *
   * @param y The list in which the preterminals of the tree will be
   *          placed. Normally, this will be empty when the routine is called,
   *          but if not, the new yield is added to the end of the list.
   * @return a <code>List</code> of the data in the tree's pre-leaves.
   */
  public List<Label> preTerminalYield(List<Label> y) {
    if (isPreTerminal()) {
      y.add(label());
    } else {
      Tree[] kids = children();
      for (int i = 0; i < kids.length; i++) {
        kids[i].preTerminalYield(y);
      }
    }
    return y;
  }

  /**
   * Gets the leaves of the tree.  All leaves nodes are returned as a list
   * ordered by the natural left to right order of the tree.  Null values,
   * if any, are inserted into the list like any other value.
   *
   * @return a <code>List</code> of the leaves.
   */
  public List<Tree> getLeaves() {
    return getLeaves(new ArrayList<Tree>());
  }

  /**
   * Gets the leaves of the tree.
   *
   * @param list The list in which the leaves of the tree will be
   *             placed. Normally, this will be empty when the routine is called,
   *             but if not, the new yield is added to the end of the list.
   * @return a <code>List</code> of the leaves.
   */
  public List<Tree> getLeaves(List<Tree> list) {
    if (isLeaf()) {
      list.add(this);
    } else {
      Tree[] kids = children();
      for (int i = 0; i < kids.length; i++) {
        kids[i].getLeaves(list);
      }
    }
    return list;
  }


  /**
   * Get the set of all node and leaf <code>Label</code>s,
   * null or otherwise, contained in the tree.
   *
   * @return the <code>Collection</code> (actually, Set) of all values
   *         in the tree.
   */
  public Collection<Label> labels() {
    Set<Label> n = new HashSet<Label>();
    n.add(label());
    Tree[] kids = children();
    for (int i = 0; i < kids.length; i++) {
      n.addAll(kids[i].labels());
    }
    return n;
  }


  public void setLabels(Collection<Label> c) {
    throw new UnsupportedOperationException("Can't set Tree labels");
  }


  /**
   * Return a flattened version of a tree.  In many circumstances, this
   * will just return the tree, but if the tree is something like a
   * binarized version of a dependency grammar tree, then it will be
   * flattened back to a dependency grammar tree representation.  Formally,
   * a node will be removed from the tree when: it is not a terminal or
   * preterminal, and its <code>label()</code is <code>equal()</code> to
   * the <code>label()</code> of its parent, and all its children will
   * then be promoted to become children of the parent (in the same
   * position in the sequence of daughters.
   *
   * @return A flattened version of this tree.
   */
  public Tree flatten() {
    return flatten(treeFactory());
  }

  /**
   * Return a flattened version of a tree.  In many circumstances, this
   * will just return the tree, but if the tree is something like a
   * binarized version of a dependency grammar tree, then it will be
   * flattened back to a dependency grammar tree representation.  Formally,
   * a node will be removed from the tree when: it is not a terminal or
   * preterminal, and its <code>label()</code is <code>equal()</code> to
   * the <code>label()</code> of its parent, and all its children will
   * then be promoted to become children of the parent (in the same
   * position in the sequence of daughters. <p>
   * Note: In the current implementation, the tree structure is mainly
   * duplicated, but the links between preterminals and terminals aren't.
   *
   * @param tf TreeFactory used to create tree structure for flattened tree
   * @return A flattened version of this tree.
   */
  public Tree flatten(TreeFactory tf) {
    if (isLeaf() || isPreTerminal()) {
      return this;
    }
    Tree[] kids = children();
    List<Tree> newChildren = new ArrayList<Tree>(kids.length);
    for (int c = 0; c < kids.length; c++) {
      Tree child = kids[c];
      if (child.isLeaf() || child.isPreTerminal()) {
        newChildren.add(child);
      } else {
        Tree newChild = child.flatten(tf);
        if (label().equals(newChild.label())) {
          newChildren.addAll(newChild.getChildrenAsList());
        } else {
          newChildren.add(newChild);
        }
      }
    }
    return tf.newTreeNode(label(), newChildren);
  }


  /**
   * Get the set of all subtrees inside the tree by returning a tree
   * rooted at each node.  These are <i>not</i> copies, but all share
   * structure.  The tree is regarded as a subtree of itself.
   * <p/>
   * <i>Note:</i> If you only want to form this Set so that you can
   * iterate over it, it is more efficient to simply use the Tree class's
   * own <code>iterator() method. This will iterate over the exact same
   * elements (but perhaps/probably in a different order).
   *
   * @return the <code>Set</code> of all subtrees in the tree.
   */
  public Set<Tree> subTrees() {
    return subTrees(new HashSet<Tree>());
  }

  /**
   * Get the list of all subtrees inside the tree by returning a tree
   * rooted at each node.  These are <i>not</i> copies, but all share
   * structure.  The tree is regarded as a subtree of itself.
   * <p/>
   * <i>Note:</i> If you only want to form this Collection so that you can
   * iterate over it, it is more efficient to simply use the Tree class's
   * own <code>iterator() method. This will iterate over the exact same
   * elements (but perhaps/probably in a different order).
   *
   * @return the <code>List</code> of all subtrees in the tree.
   */
  public List<Tree> subTreeList() {
    return subTrees(new ArrayList<Tree>());
  }


  /**
   * Add the set of all subtrees inside a tree (including the tree itself)
   * to the given <code>Collection</code>.
   * <p/>
   * <i>Note:</i> If you only want to form this Collection so that you can
   * iterate over it, it is more efficient to simply use the Tree class's
   * own <code>iterator() method. This will iterate over the exact same
   * elements (but perhaps/probably in a different order).
   *
   * @param n A collection of nodes to which the subtrees will be added.
   * @return The collection parameter with the subtrees added.
   */
  public <T extends Collection<Tree>> T subTrees(T n) {
    n.add(this);
    Tree[] kids = children();
    for (int i = 0; i < kids.length; i++) {
      kids[i].subTrees(n);
    }
    return n;
  }

  /**
   * Same as deepCopy but makes a copy of the labels as well.
   * Uses the TreeFactory of the root node given by treeFactory().
   * Assumes that your labels give a non-null labelFactory().
   * (Added by Aria Haghighi.)
   *
   * @return A deep copy of the tree structure and its labels
   */
  public Tree deeperCopy() {
    return deeperCopy(treeFactory());
  }


  /**
   * Same as deepCopy but makes a copy of the labels as well.
   * Each Label is copied using the labelFactory() returned
   * by the corresponding node's label.
   * It assumes that your labels give non-null labelFactory.
   * (Added by Aria Haghighi.)
   *
   * @param tf The TreeFactory used to make all nodes in the copied
   *           tree structure
   * @return A Tree that is a deep copy of the tree structure and
   *         Labels of the original tree.
   */
  public Tree deeperCopy(TreeFactory tf) {
    return deeperCopy(tf, label().labelFactory());
  }


  /**
   * Same as deepCopy but will copy the labels over as well.
   * Each tree is copied with the given TreeFactory.
   * Each Label is copied using the given LabelFactory.
   *
   * @param tf The TreeFactory used to make all nodes in the copied
   *           tree structure
   * @param lf The LabelFactory used to make all nodes in the copied
   *           tree structure
   * @return A Tree that is a deep copy of the tree structure and
   *         Labels of the original tree.
   */
  public Tree deeperCopy(TreeFactory tf, LabelFactory lf) {
    Label label = lf.newLabel(label());
    if (isLeaf()) {
      return tf.newLeaf(label);
    }
    Tree[] kids = children();
    List<Tree> newKids = new ArrayList<Tree>(kids.length);
    for (int i = 0, n = kids.length; i < n; i++) {
      newKids.add(kids[i].deeperCopy(tf, lf));
    }
    return tf.newTreeNode(label, newKids);
  }


  /**
   * Create a deep copy of the tree.  The entire structure is
   * recursively copied, but label data themselves are not cloned.
   * The copy is built using a <code>TreeFactory</code> that will
   * produce a <code>Tree</code> like the input one.
   *
   * @return a deep structural copy of the tree.
   */
  public Tree deepCopy() {
    return deepCopy(treeFactory());
  }


  /**
   * Create a deep copy of the tree.  The entire structure is
   * recursively copied, but label data themselves are not cloned.
   * By specifying an appropriate <code>TreeFactory</code>, this
   * method can be used to change the type of a <code>Tree</code>.
   *
   * @param tf The <code>TreeFactory</code> to be used for creating
   *           the returned <code>Tree</code>
   * @return a deep structural copy of the tree.
   */
  public Tree deepCopy(TreeFactory tf) {
    Tree t;
    if (isLeaf()) {
      t = tf.newLeaf(label());
    } else {
      Tree[] kids = children();
      List<Tree> newKids = new ArrayList<Tree>(kids.length);
      for (int i = 0, n = kids.length; i < n; i++) {
        newKids.add(kids[i].deepCopy(tf));
      }
      t = tf.newTreeNode(label(), newKids);
    }
    return t;
  }


  /**
   * Create a transformed Tree.  The tree is traversed in a depth-first,
   * left-to-right order, and the <code>TreeTransformer</code> is called
   * on each node.  It returns some <code>Tree</code>.  The transformed
   * tree has a new tree structure (i.e., a "deep copy" is done), but it
   * will usually share its labels with the original tree.
   *
   * @param transformer The function that transforms tree nodes or subtrees
   * @return a transformation of this <code>Tree</code>
   */
  public Tree transform(final TreeTransformer transformer) {
    return transform(transformer, treeFactory());
  }


  /**
   * Create a transformed Tree.  The tree is traversed in a depth-first,
   * left-to-right order, and the <code>TreeTransformer</code> is called
   * on each node.  It returns some <code>Tree</code>.  The transformed
   * tree has a new tree structure (i.e., a "deep copy" is done), but it
   * will usually share its labels with the original tree.
   *
   * @param transformer The function that transforms tree nodes or subtrees
   * @param tf          The <code>TreeFactory</code> which will be used for creating
   *                    new nodes for the returned <code>Tree</code>
   * @return a transformation of this <code>Tree</code>
   */
  public Tree transform(final TreeTransformer transformer, final TreeFactory tf) {
    Tree t;
    if (isLeaf()) {
      t = tf.newLeaf(label());
    } else {
      Tree[] kids = children();
      List<Tree> newKids = new ArrayList<Tree>(kids.length);
      for (Tree kid : kids) {
        newKids.add(kid.transform(transformer, tf));
      }
      t = tf.newTreeNode(label(), newKids);
    }
    return transformer.transformTree(t);
  }


  /**
   * Creates a (partial) deep copy of the tree, where all nodes that the
   * filter does not accept are spliced out.  If the result is not a tree
   * (that is, it's a forest), an empty root node is generated.
   *
   * @param nodeFilter a Filter method which returns true to mean
   *                   keep this node, false to mean delete it
   * @return a filtered copy of the tree
   */
  public Tree spliceOut(final Filter<Tree> nodeFilter) {
    return spliceOut(nodeFilter, treeFactory());
  }


  /**
   * Creates a (partial) deep copy of the tree, where all nodes that the
   * filter does not accept are spliced out.  That is, the particular
   * modes for which the <code>Filter</code> returns <code>false</code>
   * are removed from the <code>Tree</code>, but those nodes' children
   * are kept (assuming they pass the <code>Filter</code>, and they are
   * added in the appropriate left-to-right ordering as new children of
   * the parent node.  If the root node is deleted, so that the result
   * would not be a tree (that is, it's a forest), an empty root node is
   * generated.  If nothing is accepted, <code>null</code> is returned.
   *
   * @param nodeFilter a Filter method which returns true to mean
   *                   keep this node, false to mean delete it
   * @param tf         A <code>TreeFactory</code> for making new trees. Used if
   *                   the root node is deleted.
   * @return a filtered copy of the tree.
   */
  public Tree spliceOut(final Filter<Tree> nodeFilter, final TreeFactory tf) {
    List<Tree> l = spliceOutHelper(nodeFilter, tf);
    if (l.isEmpty()) {
      return null;
    } else if (l.size() == 1) {
      return l.get(0);
    }
    // for a forest, make a new root
    return tf.newTreeNode((Label) null, l);
  }


  private List<Tree> spliceOutHelper(Filter<Tree> nodeFilter, TreeFactory tf) {
    // recurse over all children first
    Tree[] kids = children();
    List<Tree> l = new ArrayList<Tree>();
    for (int i = 0; i < kids.length; i++) {
      l.addAll(kids[i].spliceOutHelper(nodeFilter, tf));
    }
    // check if this node is being spliced out
    if (nodeFilter.accept(this)) {
      // no, so add our children and return
      Tree t;
      if ( ! l.isEmpty()) {
        t = tf.newTreeNode(label(), l);
      } else {
        t = tf.newLeaf(label());
      }
      l = new ArrayList<Tree>(1);
      l.add(t);
      return l;
    }
    // we're out, so return our children
    return l;
  }


  /**
   * Creates a deep copy of the tree, where all nodes that the filter
   * does not accept and all children of such nodes are pruned.  If all '
   * of a node's children are pruned, that node is cut as well.
   * A <code>Filter</code> can assume
   * that it will not be called with a <code>null</code> argument.
   * <p/>
   * For example, the following code excises all PP nodes from a Tree:
   * <tt>
   * Filter<Tree> f = new Filter<Tree> {
   * public boolean accept(Tree t) {
   * return ! t.label().value().equals("PP");
   * }
   * }
   * tree.prune(f);
   * </tt>
   *
   * @param filter the filter to be apply
   * @return a filtered copy of the tree.
   */
  public Tree prune(final Filter<Tree> filter) {
    return prune(filter, treeFactory());
  }


  /**
   * Creates a deep copy of the tree, where all nodes that the filter
   * does not accept and all children of such nodes are pruned.  If all
   * of a node's children are pruned, that node is cut as well.
   * A <code>Filter</code> can assume
   * that it will not be called with a <code>null</code> argument.
   *
   * @param filter the filter to be apply
   * @param tf     the TreeFactory to be used to make new Tree nodes if needed
   * @return a filtered copy of the tree, including the possibility of
   *         <code>null</code> if the root node of the tree is filtered
   */
  public Tree prune(Filter<Tree> filter, TreeFactory tf) {
    // is the current node to be pruned?
    if ( ! filter.accept(this)) {
      return null;
    }
    // if not, recurse over all children
    List<Tree> l = new ArrayList<Tree>();
    Tree[] kids = children();
    for (int i = 0; i < kids.length; i++) {
      Tree prunedChild = kids[i].prune(filter, tf);
      if (prunedChild != null) {
        l.add(prunedChild);
      }
    }
    // and check if this node has lost all its children
    if (l.isEmpty() && !(kids.length == 0)) {
      return null;
    }
    // if we're still ok, copy the node
    if (isLeaf()) {
      return tf.newLeaf(label());
    }
    return tf.newTreeNode(label(), l);
  }

  /**
   * Returns first child if it is single and if the label at the current
   * node is either "ROOT" or empty.
   *
   */
  public Tree skipRoot() {
    if(!isUnaryRewrite())
      return this;
    String lab = label().value();
    return (lab == null || "ROOT".equals(lab) || "".equals(lab)) ? firstChild() : this;
  }

  /**
   * Return a <code>TreeFactory</code> that produces trees of the
   * appropriate type.
   *
   * @return A factory to produce Trees
   */
  public abstract TreeFactory treeFactory();


  /**
   * Return the parent of the tree node.  This routine may return
   * <code>null</code> meaning simply that the implementation doesn't
   * know how to determine the parent node, rather than there is no
   * such node.
   *
   * @return The parent <code>Tree</code> node or <code>null</code>
   * @see Tree#parent(Tree)
   */
  public Tree parent() {
    return null;
  }


  /**
   * Return the parent of the tree node.  This routine will traverse
   * a tree (depth first) from the given <code>root</code>, and will
   * correctly find the parent, regardless of whether the concrete
   * class stores parents.  It will only return <code>null</code> if this
   * node is the <code>root</code> node, or if this node is not
   * contained within the tree rooted at <code>root</code>.
   *
   * @param root The root node of the whole Tree
   * @return the parent <code>Tree</code> node if any;
   *         else <code>null</code>
   */
  public Tree parent(Tree root) {
    Tree[] kids = root.children();
    return parentHelper(root, kids, this);
  }


  private static Tree parentHelper(Tree parent, Tree[] kids, Tree node) {
    for (int i = 0, n = kids.length; i < n; i++) {
      if (kids[i] == node) {
        return parent;
      }
      Tree ret = node.parent(kids[i]);
      if (ret != null) {
        return ret;
      }
    }
    return null;
  }


  /**
   * Returns the number of nodes the tree contains.  This method
   * implements the <code>size()</code> function required by the
   * <code>Collections</code> interface.  The size of the tree is the
   * number of nodes it contains (of all types, including the leaf nodes
   * and the root).
   *
   * @return The size of the tree
   * @see #depth()
   */
  @Override
  public int size() {
    int size = 1;
    Tree[] kids = children();
    for (int i = 0, n = kids.length; i < n; i++) {
      size += kids[i].size();
    }
    return size;
  }

  /**
   * Return the ancestor tree node <code>height</code> nodes up from the current node.
   *
   * @param height How many nodes up to go. A parameter of 0 means return
   *               this node, 1 means to return the parent node and so on.
   * @param root The root node that this Tree is embedded under
   * @return The ancestor at height <code>height</code>.  It returns null
   *         if it does not exist or the tree implementation does not keep track
   *         of parents
   */
  public Tree ancestor(int height, Tree root) {
    if (height < 0) {
      throw new IllegalArgumentException("ancestor: height cannot be negative");
    }
    if (height == 0) {
      return this;
    }
    Tree par = parent(root);
    if (par == null) {
      return null;
    }
    return par.ancestor(height - 1, root);
  }

  private static class TreeIterator implements Iterator<Tree> {

    private List<Tree> treeStack;
    
    private boolean preorder;
    
    protected TreeIterator(Tree t, boolean preorder) {
      treeStack = new ArrayList<Tree>();
      treeStack.add(t);
      this.preorder = preorder;
    }

    protected TreeIterator(Tree t) {
      this(t, true);
    }

    public boolean hasNext() {
      return (!treeStack.isEmpty());
    }

    public Tree next() {
      int lastIndex = treeStack.size() - 1;
      if (lastIndex < 0) {
        throw new NoSuchElementException("TreeIterator exhausted");
      }
      Tree tr = treeStack.remove(lastIndex);
      Tree[] kids = tr.children();
      // so that we can efficiently use one List, we reverse them
      for (int i = kids.length - 1; i >= 0; i--) {
        treeStack.add(kids[i]);
      }
      return tr;
    }

    /**
     * Not supported
     */
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "TreeIterator";
    }

  }


  /**
   * Returns an iterator over all the nodes of the tree.  This method
   * implements the <code>iterator()</code> method required by the
   * <code>Collections</code> interface.  It does a preorder
   * (children after node) traversal of the tree.  (A possible
   * extension to the class at some point would be to allow different
   * traversal orderings via variant iterators.)
   *
   * @return An interator over the nodes of the tree
   */
  @Override
  public Iterator<Tree> iterator() {
    return new TreeIterator(this);
  }

  public List<Tree> postOrderNodeList() {
    List<Tree> nodes = new ArrayList<Tree>();
    postOrderRecurse(this, nodes);
    return nodes;
  }
  private static void postOrderRecurse(Tree t, List<Tree> nodes) {
    for (Tree c : t.children()) {
      postOrderRecurse(c, nodes);
    }
    nodes.add(t);
  }
  
  public List<Tree> preOrderNodeList() {
    List<Tree> nodes = new ArrayList<Tree>();
    preOrderRecurse(this, nodes);
    return nodes;
  }
  private static void preOrderRecurse(Tree t, List<Tree> nodes) {
    nodes.add(t);
    for (Tree c : t.children()) {
      preOrderRecurse(c, nodes);
    }
  }

  /**
   * This gives you a tree from a String representation (as a
   * bracketed Tree, of the kind produced by <code>toString()</code>,
   * <code>pennPrint()</code>, or as in the Penn Treebank).
   * It's not the most efficient thing to do for heavy duty usage.
   * The Tree returned is created by a
   * StringLabeledScoredTreeReaderFactory.
   *
   * @param str The tree as a bracketed list in a String.
   * @return The Tree
   * @throws IOException If Tree format is not valid
   */
  public static Tree valueOf(String str) throws IOException {
    return valueOf(str, new StringLabeledScoredTreeReaderFactory());
  }

  /**
   * This gives you a tree from a String representation (as a
   * bracketed Tree, of the kind produced by <code>toString()</code>,
   * <code>pennPrint()</code>, or as in the Penn Treebank.
   * It's not the most efficient thing to do for heavy duty usage.
   *
   * @param str The tree as a bracketed list in a String.
   * @param trf The TreeFactory used to make the new Tree
   * @return The Tree
   * @throws IOException If Tree format is not valid
   */
  public static Tree valueOf(String str, TreeReaderFactory trf) throws IOException {
    return trf.newTreeReader(new StringReader(str)).readTree();
  }


  /**
   * Return the child at some daughter index.  The children are numbered
   * starting with an index of 0.
   *
   * @param i The daughter index
   * @return The tree at that daughter index
   */
  public Tree getChild(int i) {
    Tree[] kids = children();
    return kids[i];
  }

  /**
   * Destructively removes the child at some daughter index and returns it.
   * Note
   * that this method will throw an {@link ArrayIndexOutOfBoundsException} if
   * the daughter index is too big for the list of daughters.
   *
   * @param i The daughter index
   * @return The tree at that daughter index
   */
  public Tree removeChild(int i) {
    Tree[] kids = children();
    Tree kid = kids[i];
    Tree[] newKids = new Tree[kids.length - 1];
    for (int j = 0; j < newKids.length; j++) {
      if (j < i) {
        newKids[j] = kids[j];
      } else {
        newKids[j] = kids[j + 1];
      }
    }
    setChildren(newKids);
    return kid;
  }

  /**
   * Adds the tree t at the index position among the daughters.  Note
   * that this method will throw an {@link ArrayIndexOutOfBoundsException} if
   * the the daughter index is too big for the list of daughters.
   *
   * @param i the index position at which to add the new daughter
   * @param t the new daughter
   */
  public void addChild(int i, Tree t) {
    Tree[] kids = children();
    Tree[] newKids = new Tree[kids.length + 1];
    System.arraycopy(kids, 0, newKids, 0, i);
    newKids[i] = t;
    System.arraycopy(kids, i, newKids, i + 1, kids.length - i);
    setChildren(newKids);
  }

  /**
   * Adds the tree t at the last index position among the daughters.
   *
   * @param t the new daughter
   */
  public void addChild(Tree t) {
    addChild(children().length, t);
  }

  /**
   * Replaces the <code>i</code>th child of <code>this</code> with the tree t.
   * Note
   * that this method will throw an {@link ArrayIndexOutOfBoundsException} if
   * the child index is too big for the list of children.
   *
   * @param i The index position at which to replace the child
   * @param t The new child
   * @return The tree that was previously the ith d
   */
  public Tree setChild(int i, Tree t) {
    Tree[] kids = children();
    Tree old = kids[i];
    kids[i] = t;
    return old;
  }

  /**
   * Returns true if <code>this</code> dominates the Tree passed in
   * as an argument.  Object equality (==) rather than .equals() is used
   * to determine domination.
   * t.dominates(t) returns false.
   */
  public boolean dominates(Tree t) {
    return !(dominationPath(t) == null);
  }

  /**
   * Returns the path of nodes leading down to a dominated node,
   * including <code>this</code> and the dominated node itself.
   * Returns null if t is not dominated by <code>this</code>.  Object
   * equality (==) is the relevant criterion.
   * t.dominationPath(t) returns null.
   */
  public List<Tree> dominationPath(Tree t) {
    //Tree[] result = dominationPathHelper(t, 0);
    Tree[] result = dominationPath(t, 0);
    if (result == null) {
      return null;
    }
    return Arrays.asList(result);
  }

  private Tree[] dominationPathHelper(Tree t, int depth) {
    Tree[] kids = children();
    for (int i = kids.length - 1; i >= 0; i--) {
      Tree t1 = kids[i];
      if (t1 == null) {
        return null;
      }
      Tree[] result;
      if ((result = t1.dominationPath(t, depth + 1)) != null) {
        result[depth] = this;
        return result;
      }
    }
    return null;
  }

  private Tree[] dominationPath(Tree t, int depth) {
    if (this == t) {
      Tree[] result = new Tree[depth + 1];
      result[depth] = this;
      return result;
    }
    return dominationPathHelper(t, depth);
  }

  /**
   * Given nodes <code>t1</code> and <code>t2</code> which are
   * dominated by this node, returns a list of all the nodes on the
   * path from t1 to t2, inclusive, or null if none found.
   */
  public List<Tree> pathNodeToNode(Tree t1, Tree t2) {
    if (!contains(t1) || !contains(t2)) {
      return null;
    }
    if (t1 == t2) {
      return Collections.singletonList(t1);
    }
    if (t1.dominates(t2)) {
      return t1.dominationPath(t2);
    }
    if (t2.dominates(t1)) {
      List<Tree> path = t2.dominationPath(t1);
      Collections.reverse(path);
      return path;
    }
    Tree joinNode = joinNode(t1, t2);
    if (joinNode == null) {
      return null;
    }
    List<Tree> t1DomPath = joinNode.dominationPath(t1);
    List<Tree> t2DomPath = joinNode.dominationPath(t2);
    if (t1DomPath == null || t2DomPath == null) {
      return null;
    }
    ArrayList<Tree> path = new ArrayList<Tree>();
    path.addAll(t1DomPath);
    Collections.reverse(path);
    path.remove(joinNode);
    path.addAll(t2DomPath);
    return path;
  }

  /**
   * Given nodes <code>t1</code> and <code>t2</code> which are
   * dominated by this node, returns their "join node": the node
   * <code>j</code> such that <code>j</code> dominates both
   * <code>t1</code> and <code>t2</code>, and every other node which
   * dominates both <code>t1</code> and <code>t2</code>
   * dominates <code>j</code>.  
   * In the special case that t1 dominates t2, return t1, and vice versa.
   * Return <code>null</code> if no such node can be found.
   */
  public Tree joinNode(Tree t1, Tree t2) {
    if (!contains(t1) || !contains(t2)) {
      return null;
    }
    if (this == t1 || this == t2) {
      return this;
    }
    Tree joinNode = null;
    List<Tree> t1DomPath = dominationPath(t1);
    List<Tree> t2DomPath = dominationPath(t2);
    if (t1DomPath == null || t2DomPath == null) {
      return null;
    }
    Iterator<Tree> it1 = t1DomPath.iterator();
    Iterator<Tree> it2 = t2DomPath.iterator();
    while (it1.hasNext() && it2.hasNext()) {
      Tree n1 = it1.next();
      Tree n2 = it2.next();
      if (n1 != n2) {
        break;
      }
      joinNode = n1;
    }
    return joinNode;
  }

  /**
   * Given nodes <code>t1</code> and <code>t2</code> which are
   * dominated by this node, returns <code>true</code> iff
   * <code>t1</code> c-commands <code>t2</code>.  (A node c-commands
   * its sister(s) and any nodes below its sister(s).)
   */
  public boolean cCommands(Tree t1, Tree t2) {
    List<Tree> sibs = t1.siblings(this);
    if (sibs == null) {
      return false;
    }
    for (Tree sib : sibs) {
      if (sib == t2 || sib.contains(t2)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the siblings of this Tree node.  The siblings are all
   * children of the parent of this node except this node.
   *
   * @param root The root within which this tree node is contained
   * @return The siblings as a list, an empty list if there are no siblings.
   *   The returned list is a modifiable new list structure, but contains
   *   the actual children.
   */
  public List<Tree> siblings(Tree root) {
    Tree parent = parent(root);
    if (parent == null) {
      return null;
    }
    List<Tree> siblings = parent.getChildrenAsList();
    siblings.remove(this);
    return siblings;
  }

  /**
   * insert <code>dtr</code> after <code>position</code> existing
   * daughters in <code>this</code>.
   */
  public void insertDtr(Tree dtr, int position) {
    Tree[] kids = children();
    if (position > kids.length) {
      throw new IllegalArgumentException("Can't insert tree after the " + position + "th daughter in " + this + "; only " + kids.length + " daughters exist!");
    }
    Tree[] newKids = new Tree[kids.length + 1];
    int i = 0;
    for (; i < position; i++) {
      newKids[i] = kids[i];
    }
    newKids[i] = dtr;
    for (; i < kids.length; i++) {
      newKids[i + 1] = kids[i];
    }
    setChildren(newKids);
  }

  // --- composition methods to implement Label interface

  public String value() {
    Label lab = label();
    if (lab == null) {
      return null;
    }
    return lab.value();
  }


  public void setValue(String value) {
    Label lab = label();
    if (lab != null) {
      lab.setValue(value);
    }
  }


  public void setFromString(String labelStr) {
    Label lab = label();
    if (lab != null) {
      lab.setFromString(labelStr);
    }
  }

  /**
   * Returns a factory that makes labels of the same type as this one.
   * May return <code>null</code> if no appropriate factory is known.
   *
   * @return the LabelFactory for this kind of label
   */
  public LabelFactory labelFactory() {
    Label lab = label();
    if (lab == null) {
      return null;
    }
    return lab.labelFactory();
  }

  /**
   * Returns the positional index of the left edge of  <i>node</i> within the tree,
   * as measured by characters.  Returns -1 if <i>node is not found.</i>
   */
  public int leftCharEdge(Tree node) {
    MutableInteger i = new MutableInteger(0);
    if (leftCharEdge(node, i)) {
      return i.intValue();
    }
    return -1;
  }

  private boolean leftCharEdge(Tree node, MutableInteger i) {
    if (this == node) {
      return true;
    } else if (isLeaf()) {
      i.set(i.intValue() + value().length());
      return false;
    } else {
      for (Tree child : children()) {
        if (child.leftCharEdge(node, i)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Returns the positional index of the right edge of  <i>node</i> within the tree,
   * as measured by characters. Returns -1 if <i>node is not found.</i>
   * 
   * rightCharEdge returns the index of the rightmost character + 1, so that
   * rightCharEdge(getLeaves().get(i)) == leftCharEdge(getLeaves().get(i+1))
   *
   * @param node The subtree to look for in this Tree
   * @return The positional index of the right edge of node
   */
  public int rightCharEdge(Tree node) {
    List<Tree> s = getLeaves();
    int length = 0;
    for (Tree leaf : s) {
      length += leaf.label().value().length();
    }
    MutableInteger i = new MutableInteger(length);
    if (rightCharEdge(node, i)) {
      return i.intValue();
    }
    return -1;
  }

  private boolean rightCharEdge(Tree node, MutableInteger i) {
    if (this == node) {
      return true;
    } else if (isLeaf()) {
      i.set(i.intValue() - label().value().length());
      return false;
    } else {
      for (int j = children().length - 1; j >= 0; j--) {
        if (children()[j].rightCharEdge(node, i)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Calculates the node's <i>number</i>, defined as the number of nodes traversed in a left-to-right, depth-first search of the
   * tree starting at <code>root</code> and ending at <code>this</code>.  Returns -1 if <code>root</code> does not contain <code>this</code>.
   * @param root the root node of the relevant tree
   * @return the number of the current node, or -1 if <code>root</code> does not contain <code>this</code>.
   */
  public int nodeNumber(Tree root) {
    MutableInteger i = new MutableInteger(1);
    if(nodeNumberHelper(root,i))
      return i.intValue();
    return -1;
  }

  private boolean nodeNumberHelper(Tree t, MutableInteger i) {
    if(this==t)
      return true;
    i.incValue(1);
    for(int j = 0; j < t.children().length; j++) {
      if(nodeNumberHelper(t.children()[j],i))
        return true;
    }
    return false;
  }

  /**
   * Fetches the <code>i</code>th node in the tree, with node numbers defined
   * as in {@link #nodeNumber(Tree)}.
   *
   * @param i the node number to fetch
   * @return the <code>i</code>th node in the tree
   * @throws IndexOutOfBoundsException if <code>i</code> is not between 1 and
   *    the number of nodes (inclusive) contained in <code>this</code>.
   */
  public Tree getNodeNumber(int i) {
    return getNodeNumberHelper(new MutableInteger(1),i);
  }

  private Tree getNodeNumberHelper(MutableInteger i, int target) {
    int i1 = i.intValue();
    if(i1 == target)
      return this;
    if(i1 > target)
      throw new IndexOutOfBoundsException("Error -- tree does not contain " + i + " nodes.");
    i.incValue(1);
    for(int j = 0; j < children().length; j++) {
      Tree temp = children()[j].getNodeNumberHelper(i, target);
      if(temp != null)
        return temp;
    }
    return null;
  }

  /**
   * Assign sequential integer indices to the leaves of the tree
   * rooted at this <code>Tree</code>, starting with 1.
   * The leaves are traversed from left
   * to right. If the node is already indexed, then it uses the existing index.
   * This will only work if the leaves extend CoreMap.
   */
  public void indexLeaves() {
    indexLeaves(1);
  }

  /**
   * Assign sequential integer indices to the leaves of the subtree
   * rooted at this <code>Tree</code>, beginning with
   * <code>startIndex</code>, and traversing the leaves from left
   * to right. If node is already indexed, then it uses the existing index.
   * This method only works if the labels of the tree implement
   * CoreMap!
   *
   * @param startIndex index for this node
   * @return the next index still unassigned
   */
  private int indexLeaves(int startIndex) {
    if (isLeaf()) {
      CoreMap afl = (CoreMap) label();
      Integer oldIndex = afl.get(IndexAnnotation.class);
      if (oldIndex != null && oldIndex >= 0) {
        startIndex = oldIndex;
      } else {
        afl.set(IndexAnnotation.class, startIndex);
      }
      startIndex++;
    } else {
      for (Tree kid : children()) {
        startIndex = kid.indexLeaves(startIndex);
      }
    }
    return startIndex;
  }

}
