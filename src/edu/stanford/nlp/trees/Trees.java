package edu.stanford.nlp.trees;

import edu.stanford.nlp.io.IOUtils;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.ling.*;

import java.util.*;
import java.io.*;

/**
 * Various static utilities for the {@code Tree} class.
 *
 * @author Roger Levy
 * @author Dan Klein
 * @author Aria Haghighi (tree path methods)
 */
public class Trees {

  private static final LabeledScoredTreeFactory defaultTreeFactory = new LabeledScoredTreeFactory();

  private Trees() {}

  /**
   * Recursively calculate the max height of the given tree
   */
  public static int height(Tree t) {
    if (t.isLeaf()) {
      return 1;
    }
    int maxHeight = 0;
    for (Tree child : t.children()) {
      maxHeight = Math.max(maxHeight, height(child));
    }
    return maxHeight + 1;
  }
  
  /**
   * Returns the positional index of the left edge of a tree <i>t</i>
   * within a given root, as defined by the size of the yield of all
   * material preceding <i>t</i>.
   */
  public static int leftEdge(Tree t, Tree root) {
    MutableInteger i = new MutableInteger(0);
    if (leftEdge(t, root, i)) {
      return i.intValue();
    } else {
      throw new RuntimeException("Tree is not a descendant of root.");
//      return -1;
    }
  }

  /**
   * Returns the positional index of the left edge of a tree <i>t</i>
   * within a given root, as defined by the size of the yield of all
   * material preceding <i>t</i>.
   * This method returns -1 if no path is found, rather than exceptioning.
   *
   * @see Trees#leftEdge(Tree, Tree)
   */
  public static int leftEdgeUnsafe(Tree t, Tree root) {
    MutableInteger i = new MutableInteger(0);
    if (leftEdge(t, root, i)) {
      return i.intValue();
    } else {
      return -1;
    }
  }

  private static boolean leftEdge(Tree t, Tree t1, MutableInteger i) {
    if (t == t1) {
      return true;
    } else if (t1.isLeaf()) {
      int j = t1.yield().size(); // so that empties don't add size
      i.set(i.intValue() + j);
      return false;
    } else {
      for (Tree kid : t1.children()) {
        if (leftEdge(t, kid, i)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Returns the positional index of the right edge of a tree
   * <i>t</i> within a given root, as defined by the size of the yield
   * of all material preceding <i>t</i> plus all the material
   * contained in <i>t</i>.
   */
  public static int rightEdge(Tree t, Tree root) {
    MutableInteger i = new MutableInteger(root.yield().size());
    if (rightEdge(t, root, i)) {
      return i.intValue();
    } else {
      throw new RuntimeException("Tree is not a descendant of root.");
//      return root.yield().size() + 1;
    }
  }

  /**
   * Returns the positional index of the right edge of a tree
   * <i>t</i> within a given root, as defined by the size of the yield
   * of all material preceding <i>t</i> plus all the material
   * contained in <i>t</i>.
   * This method returns root.yield().size() + 1 if no path is found, rather than exceptioning.
   *
   * @see Trees#rightEdge(Tree, Tree)
   */
  public static int rightEdgeUnsafe(Tree t, Tree root) {
    MutableInteger i = new MutableInteger(root.yield().size());
    if (rightEdge(t, root, i)) {
      return i.intValue();
    } else {
      return root.yield().size() + 1;
    }
  }

  private static boolean rightEdge(Tree t, Tree t1, MutableInteger i) {
    if (t == t1) {
      return true;
    } else if (t1.isLeaf()) {
      int j = t1.yield().size(); // so that empties don't add size
      i.set(i.intValue() - j);
      return false;
    } else {
      Tree[] kids = t1.children();
      for (int j = kids.length - 1; j >= 0; j--) {
        if (rightEdge(t, kids[j], i)) {
          return true;
        }
      }
      return false;
    }
  }


  /**
   * Returns a lexicalized Tree whose Labels are CategoryWordTag
   * instances, all corresponds to the input tree.
   */
  public static Tree lexicalize(Tree t, HeadFinder hf) {
    Function<Tree,Tree> a =
      TreeFunctions.getLabeledTreeToCategoryWordTagTreeFunction();
    Tree t1 = a.apply(t);
    t1.percolateHeads(hf);
    return t1;
  }

  /**
   * returns the leaves in a Tree in the order that they're found.
   */
  public static List<Tree> leaves(Tree t) {
    List<Tree> l = new ArrayList<>();
    leaves(t, l);
    return l;
  }

  private static void leaves(Tree t, List<Tree> l) {
    if (t.isLeaf()) {
      l.add(t);
    } else {
      for (Tree kid : t.children()) {
        leaves(kid, l);
      }
    }
  }

  public static List<Tree> preTerminals(Tree t) {
    List<Tree> l = new ArrayList<>();
    preTerminals(t, l);
    return l;
  }

  private static void preTerminals(Tree t, List<Tree> l) {
    if (t.isPreTerminal()) {
      l.add(t);
    } else {
      for (Tree kid : t.children()) {
        preTerminals(kid, l);
      }
    }
  }

  public static Set<String> uniqueTags(List<Tree> trees) {
    Set<String> allTags = new HashSet<>();
    for (Tree tree : trees) {
      uniqueTags(tree, allTags);
    }
    return allTags;
  }

  public static Set<String> uniqueTags(Tree tree) {
    List<Label> labels = tree.preTerminalYield();
    return uniqueTags(tree, new HashSet<>());
  }

  public static Set<String> uniqueTags(Tree tree, Set<String> tags) {
    List<Label> labels = tree.preTerminalYield();
    for (Label label : labels) {
      tags.add(label.value());
    }
    return tags;
  }


  /**
   * returns the labels of the leaves in a Tree in the order that they're found.
   */
  public static List<Label> leafLabels(Tree t) {
    List<Label> l = new ArrayList<>();
    leafLabels(t, l);
    return l;
  }

  private static void leafLabels(Tree t, List<Label> l) {
    if (t.isLeaf()) {
      l.add(t.label());
    } else {
      for (Tree kid : t.children()) {
        leafLabels(kid, l);
      }
    }
  }

  /**
   * returns the labels of the leaves in a Tree, augmented with POS tags.  assumes that
   * the labels are CoreLabels.
   */
  public static List<CoreLabel> taggedLeafLabels(Tree t) {
    List<CoreLabel> l = new ArrayList<>();
    taggedLeafLabels(t, l);
    return l;
  }

  private static void taggedLeafLabels(Tree t, List<CoreLabel> l) {
    if (t.isPreTerminal()) {
      CoreLabel fl = (CoreLabel)t.getChild(0).label();
      fl.set(CoreAnnotations.TagLabelAnnotation.class, t.label());
      l.add(fl);
    } else {
      for (Tree kid : t.children()) {
        taggedLeafLabels(kid, l);
      }
    }
  }

  /**
   * Given a tree, set the tags on the leaf nodes if they are not
   * already set.  Do this by using the preterminal's value as a tag.
   */
  public static void setLeafTagsIfUnset(Tree tree) {
    if (tree.isPreTerminal()) {
      Tree leaf = tree.children()[0];
      if (!(leaf.label() instanceof HasTag)) {
        return;
      }
      HasTag label = (HasTag) leaf.label();
      if (label.tag() == null) {
        label.setTag(tree.value());
      }
    } else {
      for (Tree child : tree.children()) {
        setLeafTagsIfUnset(child);
      }
    }
  }

  /**
   * Replace the labels of the leaves with the given leaves.
   */
  public static void setLeafLabels(Tree tree, List<? extends Label> labels) {
    Iterator<Tree> leafIterator = tree.getLeaves().iterator();
    Iterator<? extends Label> labelIterator = labels.iterator();
    while (leafIterator.hasNext() && labelIterator.hasNext()) {
      Tree leaf = leafIterator.next();
      Label label = labelIterator.next();
      leaf.setLabel(label);
      //leafIterator.next().setLabel(labelIterator.next());
    }
    if (leafIterator.hasNext()) {
      throw new IllegalArgumentException("Tree had more leaves than the labels provided");
    }
    if (labelIterator.hasNext()) {
      throw new IllegalArgumentException("More labels provided than tree had leaves");
    }
  }


  /**
   * returns the maximal projection of {@code head} in
   * {@code root} given a {@link HeadFinder}
   */
  public static Tree maximalProjection(Tree head, Tree root, HeadFinder hf) {
    Tree projection = head;
    if (projection == root) {
      return root;
    }
    Tree parent = projection.parent(root);
    while (hf.determineHead(parent) == projection) {
      projection = parent;
      if (projection == root) {
        return root;
      }
      parent = projection.parent(root);
    }
    return projection;
  }

  /* applies a TreeVisitor to all projections (including the node itself) of a node in a Tree.
  *  Does nothing if head is not in root.
  * @return the maximal projection of head in root.
  */
  public static Tree applyToProjections(TreeVisitor v, Tree head, Tree root, HeadFinder hf) {
    Tree projection = head;
    Tree parent = projection.parent(root);
    if (parent == null && projection != root) {
      return null;
    }
    v.visitTree(projection);
    if (projection == root) {
      return root;
    }
    while (hf.determineHead(parent) == projection) {
      projection = parent;
      v.visitTree(projection);
      if (projection == root) {
        return root;
      }
      parent = projection.parent(root);
    }
    return projection;
  }

  /**
   * gets the {@code n}th terminal in {@code tree}.  The first terminal is number zero.
   */
  public static Tree getTerminal(Tree tree, int n) {
    return getTerminal(tree, new MutableInteger(0), n);
  }

  private static Tree getTerminal(Tree tree, MutableInteger i, int n) {
    if (i.intValue() == n) {
      if (tree.isLeaf()) {
        return tree;
      } else {
        return getTerminal(tree.children()[0], i, n);
      }
    } else {
      if (tree.isLeaf()) {
        i.set(i.intValue() + tree.yield().size());
        return null;
      } else {
        for (Tree kid : tree.children()) {
          Tree result = getTerminal(kid, i, n);
          if (result != null) {
            return result;
          }
        }
        return null;
      }
    }
  }

  /**
   * gets the {@code n}th preterminal in {@code tree}.  The first terminal is number zero.
   */
  public static Tree getPreTerminal(Tree tree, int n) {
    return getPreTerminal(tree, new MutableInteger(0), n);
  }

  private static Tree getPreTerminal(Tree tree, MutableInteger i, int n) {
    if (i.intValue() == n) {
      if (tree.isPreTerminal()) {
        return tree;
      } else {
        return getPreTerminal(tree.children()[0], i, n);
      }
    } else {
      if (tree.isPreTerminal()) {
        i.set(i.intValue() + tree.yield().size());
        return null;
      } else {
        for (Tree kid : tree.children()) {
          Tree result = getPreTerminal(kid, i, n);
          if (result != null) {
            return result;
          }
        }
        return null;
      }
    }
  }

  /**
   * returns the syntactic category of the tree as a list of the syntactic categories of the mother and the daughters
   */
  public static List<String> localTreeAsCatList(Tree t) {
    List<String> l = new ArrayList<>(t.children().length + 1);
    l.add(t.label().value());
    for (int i = 0; i < t.children().length; i++) {
      l.add(t.children()[i].label().value());
    }
    return l;
  }

  /**
   * Returns the index of {@code daughter} in {@code parent} by ==.
   * Returns -1 if {@code daughter} not found.
   */
  public static int objectEqualityIndexOf(Tree parent, Tree daughter) {
    for (int i = 0; i < parent.children().length; i++) {
      if (daughter == parent.children()[i]) {
        return i;
      }
    }
    return -1;
  }

  /** Returns a String reporting what kinds of Tree and Label nodes this
   *  Tree contains.
   *
   *  @param t The tree to examine.
   *  @return A human-readable String reporting what kinds of Tree and Label nodes this
   *      Tree contains.
   */
  public static String toStructureDebugString(Tree t) {
    String tCl = StringUtils.getShortClassName(t);
    String tfCl = StringUtils.getShortClassName(t.treeFactory());
    String lCl = StringUtils.getShortClassName(t.label());
    String lfCl = StringUtils.getShortClassName(t.label().labelFactory());
    Set<String> otherClasses = Generics.newHashSet();
    String leafLabels = null;
    String tagLabels = null;
    String phraseLabels = null;
    String leaves = null;
    String nodes = null;
    for (Tree st : t) {
      String stCl = StringUtils.getShortClassName(st);
      String stfCl = StringUtils.getShortClassName(st.treeFactory());
      String slCl = StringUtils.getShortClassName(st.label());
      String slfCl = StringUtils.getShortClassName(st.label().labelFactory());
      if ( ! tCl.equals(stCl)) {
        otherClasses.add(stCl);
      }
      if ( ! tfCl.equals(stfCl)) {
        otherClasses.add(stfCl);
      }
      if ( ! lCl.equals(slCl)) {
        otherClasses.add(slCl);
      }
      if ( ! lfCl.equals(slfCl)) {
        otherClasses.add(slfCl);
      }
      if (st.isPhrasal()) {
        if (nodes == null) {
          nodes = stCl;
        } else if ( ! nodes.equals(stCl)) {
          nodes = "mixed";
        }
        if (phraseLabels == null) {
          phraseLabels = slCl;
        } else if ( ! phraseLabels.equals(slCl)) {
          phraseLabels = "mixed";
        }
      } else if (st.isPreTerminal()) {
        if (nodes == null) {
          nodes = stCl;
        } else if ( ! nodes.equals(stCl)) {
          nodes = "mixed";
        }
        if (tagLabels == null) {
          tagLabels = StringUtils.getShortClassName(slCl);
        } else if ( ! tagLabels.equals(slCl)) {
          tagLabels = "mixed";
        }
      } else if (st.isLeaf()) {
        if (leaves == null) {
          leaves = stCl;
        } else if ( ! leaves.equals(stCl)) {
          leaves = "mixed";
        }
        if (leafLabels == null) {
          leafLabels = slCl;
        } else if ( ! leafLabels.equals(slCl)) {
          leafLabels = "mixed";
        }
      } else {
        throw new IllegalStateException("Bad tree state: " + t);
      }
    } // end for Tree st : this
    StringBuilder sb = new StringBuilder();
    sb.append("Tree with root of class ").append(tCl).append(" and factory ").append(tfCl);
    sb.append(" and root label class ").append(lCl).append(" and factory ").append(lfCl);
    if ( ! otherClasses.isEmpty()) {
      sb.append(" and the following classes also found within the tree: ").append(otherClasses);
      return " with " + nodes + " interior nodes and " + leaves +
        " leaves, and " + phraseLabels + " phrase labels, " +
        tagLabels + " tag labels, and " + leafLabels + " leaf labels.";
    } else {
      sb.append(" (and uniform use of these Tree and Label classes throughout the tree).");
    }
    return sb.toString();
  }


  /** Turns a sentence into a flat phrasal tree.
   *  The structure is S -&gt; tag*.  And then each tag goes to a word.
   *  The tag is either found from the label or made "WD".
   *  The tag and phrasal node have a StringLabel.
   *
   *  @param s The Sentence to make the Tree from
   *  @return The one phrasal level Tree
   */
  public static Tree toFlatTree(List<HasWord> s) {
    return toFlatTree(s, new StringLabelFactory());
  }

  /** Turns a sentence into a flat phrasal tree.
   *  The structure is S -&gt; tag*.  And then each tag goes to a word.
   *  The tag is either found from the label or made "WD".
   *  The tag and phrasal node have a StringLabel.
   *
   *  @param s The Sentence to make the Tree from
   *  @param lf The LabelFactory with which to create the new Tree labels
   *  @return The one phrasal level Tree
   */
  public static Tree toFlatTree(List<? extends HasWord> s, LabelFactory lf) {
    List<Tree> daughters = new ArrayList<>(s.size());
    for (HasWord word : s) {
      Tree wordNode = new LabeledScoredTreeNode(lf.newLabel(word.word()));
      if (word instanceof TaggedWord) {
        TaggedWord taggedWord = (TaggedWord) word;
        wordNode = new LabeledScoredTreeNode(new StringLabel(taggedWord.tag()), Collections.singletonList(wordNode));
      } else {
        wordNode = new LabeledScoredTreeNode(lf.newLabel("WD"), Collections.singletonList(wordNode));
      }
      daughters.add(wordNode);
    }
    return new LabeledScoredTreeNode(new StringLabel("S"), daughters);
  }


   public static String treeToLatex(Tree t) {
     StringBuilder connections = new StringBuilder();
     StringBuilder hierarchy = new StringBuilder();
     treeToLatexHelper(t,connections,hierarchy,0,1,0);
     return "\\tree"+hierarchy+ '\n' +connections+ '\n';
   }

  private static int treeToLatexHelper(Tree t, StringBuilder c, StringBuilder h,
                                       int n, int nextN, int indent) {
    StringBuilder sb = new StringBuilder();
    for (int i=0; i<indent; i++)
      sb.append("  ");
    h.append('\n').append(sb);
    h.append("{\\").append(t.isLeaf() ? "" : "n").append("tnode{z").append(n).append("}{").append(t.label()).append('}');
    if (!t.isLeaf()) {
      for (int k=0; k<t.children().length; k++) {
        h.append(", ");
        c.append("\\nodeconnect{z").append(n).append("}{z").append(nextN).append("}\n");
        nextN = treeToLatexHelper(t.children()[k],c,h,nextN,nextN+1,indent+1);
      }
    }
    h.append('}');
    return nextN;
  }

  public static String treeToLatexEven(Tree t) {
    StringBuilder connections = new StringBuilder();
    StringBuilder hierarchy = new StringBuilder();
    int maxDepth = t.depth();
    treeToLatexEvenHelper(t,connections,hierarchy,0,1,0,0,maxDepth);
    return "\\tree"+hierarchy+ '\n' +connections+ '\n';
  }

  private static int treeToLatexEvenHelper(Tree t, StringBuilder c, StringBuilder h, int n,
                                           int nextN, int indent, int curDepth, int maxDepth) {
    StringBuilder sb = new StringBuilder();
    for (int i=0; i<indent; i++)
      sb.append("  ");
    h.append('\n').append(sb);
    int tDepth = t.depth();
    if (tDepth == 0 && tDepth+curDepth < maxDepth) {
      for (int pad=0; pad < maxDepth-tDepth-curDepth; pad++) {
        h.append("{\\ntnode{pad}{}, ");
      }
    }
    h.append("{\\ntnode{z").append(n).append("}{").append(t.label()).append('}');
    if (!t.isLeaf()) {
      for (int k=0; k<t.children().length; k++) {
        h.append(", ");
        c.append("\\nodeconnect{z").append(n).append("}{z").append(nextN).append("}\n");
        nextN = treeToLatexEvenHelper(t.children()[k],c,h,nextN,nextN+1,indent+1,curDepth+1,maxDepth);
      }
    }
    if (tDepth == 0 && tDepth+curDepth < maxDepth) {
      for (int pad=0; pad < maxDepth-tDepth-curDepth; pad++) {
        h.append('}');
      }
    }
    h.append('}');
    return nextN;
  }

  static String texTree(Tree t) {
    return treeToLatex(t);
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i=0; i<s.length(); i++) {
      char c = s.charAt(i);
      if (c == '^')
        sb.append('\\');
      sb.append(c);
      if (c == '^')
        sb.append("{}");
    }
    return sb.toString();
  }


  public static void main(String[] args) throws IOException {
    int i = 0;
    while (i < args.length) {
      Tree tree = Tree.valueOf(args[i]);
      if (tree == null) {
        // maybe it was a filename
        tree = Tree.valueOf(IOUtils.slurpFile(args[i]));
      }
      if (tree != null) {
        System.out.println(escape(texTree(tree)));
      }
      i++;
    }
    if (i == 0) {
      Tree tree = (new PennTreeReader(new BufferedReader(new
              InputStreamReader(System.in)), new LabeledScoredTreeFactory(new
              StringLabelFactory()))).readTree();
      System.out.println(escape(texTree(tree)));
    }
  }

  public static Tree normalizeTree(Tree tree, TreeNormalizer tn, TreeFactory tf) {
    for (Tree node : tree) {
      if (node.isLeaf()) {
        node.label().setValue(tn.normalizeTerminal(node.label().value()));
      } else {
        node.label().setValue(tn.normalizeNonterminal(node.label().value()));
      }
    }
    return tn.normalizeWholeTree(tree, tf);
  }


  /**
   * Gets the <i>i</i>th leaf of a tree from the left.
   * The leftmost leaf is numbered 0.
   *
   * @return The <i>i</i><sup>th</sup> leaf as a Tree, or {@code null}
   *     if there is no such leaf.
   */
  public static Tree getLeaf(Tree tree, int i) {
    int count = -1;
    for (Tree next : tree) {
      if (next.isLeaf()) {
        count++;
      }
      if (count == i) {
        return next;
      }
    }
    return null;
  }


  /**
   * Get lowest common ancestor of all the nodes in the list with the tree rooted at root
   */
  public static Tree getLowestCommonAncestor(List<Tree> nodes, Tree root) {
    List<List<Tree>> paths = new ArrayList<>();
    int min = Integer.MAX_VALUE;
    for (Tree t : nodes) {
      List<Tree> path = pathFromRoot(t, root);
      if (path == null) return null;
      min = Math.min(min, path.size());
      paths.add(path);
    }
    Tree commonAncestor = null;
    for (int i = 0; i < min; ++i) {
      Tree ancestor = paths.get(0).get(i);
      boolean quit = false;
      for (List<Tree> path : paths) {
        if (!path.get(i).equals(ancestor)) {
          quit = true;
          break;
        }
      }
      if (quit) break;
      commonAncestor = ancestor;
    }
    return commonAncestor;
  }


  /**
   * returns a list of categories that is the path from Tree from to Tree
   * to within Tree root.  If either from or to is not in root,
   * returns null.  Otherwise includes both from and to in the list.
   */
  public static List<String> pathNodeToNode(Tree from, Tree to, Tree root) {
    List<Tree> fromPath = pathFromRoot(from, root);
    //System.out.println(treeListToCatList(fromPath));
    if (fromPath == null)
      return null;

    List<Tree> toPath = pathFromRoot(to, root);
    //System.out.println(treeListToCatList(toPath));
    if (toPath == null)
      return null;

    //System.out.println(treeListToCatList(fromPath));
    //System.out.println(treeListToCatList(toPath));

    int last = 0;
    int min = fromPath.size() <= toPath.size() ? fromPath.size() : toPath.size();

    Tree lastNode = null;
//     while((! (fromPath.isEmpty() || toPath.isEmpty())) &&  fromPath.get(0).equals(toPath.get(0))) {
//       lastNode = (Tree) fromPath.remove(0);
//       toPath.remove(0);
//     }
    while (last < min && fromPath.get(last).equals(toPath.get(last))) {
      lastNode = fromPath.get(last);
      last++;
    }

    //System.out.println(treeListToCatList(fromPath));
    //System.out.println(treeListToCatList(toPath));
    List<String> totalPath = new ArrayList<>();

    for (int i = fromPath.size() - 1; i >= last; i--) {
      Tree t = fromPath.get(i);
      totalPath.add("up-" + t.label().value());
    }

    if (lastNode != null)
      totalPath.add("up-" + lastNode.label().value());

    for (Tree t: toPath)
      totalPath.add("down-" + t.label().value());


//     for(ListIterator i = fromPath.listIterator(fromPath.size()); i.hasPrevious(); ){
//       Tree t = (Tree) i.previous();
//       totalPath.add("up-" + t.label().value());
//     }

//     if(lastNode != null)
//     totalPath.add("up-" + lastNode.label().value());

//     for(ListIterator j = toPath.listIterator(); j.hasNext(); ){
//       Tree t = (Tree) j.next();
//       totalPath.add("down-" + t.label().value());
//     }

    return totalPath;
  }


  /**
   * returns list of tree nodes to root from t.  Includes root and
   * t. Returns null if tree not found dominated by root
   */
  public static List<Tree> pathFromRoot(Tree t, Tree root) {
    if (t == root) {
      //if (t.equals(root)) {
      List<Tree> l = new ArrayList<>(1);
      l.add(t);
      return l;
    } else if (root == null) {
      return null;
    }
    return root.dominationPath(t);
  }


  /**
   * replaces all instances (by ==) of node with node1.  Doesn't affect
   * the node t itself
   */
  public static void replaceNode(Tree node, Tree node1, Tree t) {
    if (t.isLeaf())
      return;
    Tree[] kids = t.children();
    List<Tree> newKids = new ArrayList<>(kids.length);
    for (Tree kid : kids) {
      if (kid != node) {
        newKids.add(kid);
        replaceNode(node, node1, kid);
      } else {
        newKids.add(node1);
      }
    }
    t.setChildren(newKids);
  }


  /**
   * returns the node of a tree which represents the lowest common
   * ancestor of nodes t1 and t2 dominated by root. If either t1 or
   * or t2 is not dominated by root, returns null.
   */
  public static Tree getLowestCommonAncestor(Tree t1, Tree t2, Tree root) {
    List<Tree> t1Path = pathFromRoot(t1, root);
    List<Tree> t2Path = pathFromRoot(t2, root);
    if (t1Path == null || t2Path == null) return null;

    int min = Math.min(t1Path.size(), t2Path.size());
    Tree commonAncestor = null;
    for (int i = 0; i < min && t1Path.get(i).equals(t2Path.get(i)); ++i) {
      commonAncestor = t1Path.get(i);
    }

    return commonAncestor;
  }

  // todo [cdm 2015]: These next two methods duplicate the Tree.valueOf methods!
  /**
   * Simple tree reading utility method.  Given a tree formatted as a PTB string, returns a Tree made by a specific TreeFactory.
   */
  public static Tree readTree(String ptbTreeString, TreeFactory treeFactory) {
    try {
      PennTreeReader ptr = new PennTreeReader(new StringReader(ptbTreeString), treeFactory);
      return ptr.readTree();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Simple tree reading utility method.  Given a tree formatted as a PTB string, returns a Tree made by the default TreeFactory (LabeledScoredTreeFactory)
   */
  public static Tree readTree(String str) {
    return readTree(str, defaultTreeFactory);
  }

  /**
   * Outputs the labels on the trees, not just the words.
   */
  public static void outputTreeLabels(Tree tree) {
    outputTreeLabels(tree, 0);
  }

  private static void outputTreeLabels(Tree tree, int depth) {
    for (int i = 0; i < depth; ++i) {
      System.out.print(" ");
    }
    System.out.println(tree.label());
    for (Tree child : tree.children()) {
      outputTreeLabels(child, depth + 1);
    }
  }

  /**
   * Converts the tree labels to CoreLabels.
   * We need this because we store additional info in the CoreLabel, like token span.
   * @param tree Any tree
   */
  public static void convertToCoreLabels(Tree tree) {
    Label l = tree.label();
    if (!(l instanceof CoreLabel)) {
      CoreLabel cl = new CoreLabel();
      cl.setValue(l.value());
      tree.setLabel(cl);
    }

    for (Tree kid : tree.children()) {
      convertToCoreLabels(kid);
    }
  }


  /**
   * Set the sentence index of all the leaves in the tree
   * (only works on CoreLabel)
   */
  public static void setSentIndex(Tree tree, int sentIndex) {
    List<Label> leaves = tree.yield();
    for (Label leaf : leaves) {
      if (!(leaf instanceof CoreLabel)) {
        throw new IllegalArgumentException("Only works on CoreLabel");
      }
      ((CoreLabel) leaf).setSentIndex(sentIndex);
    }
  }
}
