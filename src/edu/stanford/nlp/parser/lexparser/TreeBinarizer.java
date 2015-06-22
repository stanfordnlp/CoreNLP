package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.stats.ClassicCounter;

import java.util.*;
import java.io.Reader;

/**
 * Binarizes trees in such a way that head-argument structure is respected.
 * Looks only at the value of input tree nodes.
 * Produces LSTrees with CWT labels.  The input trees have to have CWT labels!
 * Although the binarizer always respects heads, you can get left or right
 * binarization by defining an appropriate HeadFinder.
 *
 * @author Dan Klein
 * @author Teg Grenager
 * @author Christopher Manning
 */
public class TreeBinarizer implements TreeTransformer {

  private static final boolean DEBUG = false;

  private HeadFinder hf;
  private TreeFactory tf;
  private TreebankLanguagePack tlp;
  private boolean insideFactor; // true: DT JJ NN -> DT "JJ NN", false: DT "DT"
  private boolean markovFactor;
  private int markovOrder;
  private boolean useWrappingLabels;
  private double selectiveSplitThreshold;
  private boolean markFinalStates;
  private boolean unaryAtTop;
  private boolean doSelectiveSplit = false;
  private ClassicCounter<String> stateCounter = new ClassicCounter<String>();
  private final boolean simpleLabels;
  private final boolean noRebinarization;


  /**
   * If this is set to true, then the binarizer will choose selectively whether or not to
   * split states based on how many counts the states had in a previous run. These counts are
   * stored in an internal counter, which will be added to when doSelectiveSplit is false.
   * If passed false, this will initialize (clear) the counts.
   * @param doSelectiveSplit Record this value and reset internal counter if false
   */
  public void setDoSelectiveSplit(boolean doSelectiveSplit) {
    this.doSelectiveSplit = doSelectiveSplit;
    if (!doSelectiveSplit) {
      stateCounter = new ClassicCounter<String>();
    }
  }

  private static String join(List<Tree> treeList) {
    StringBuilder sb = new StringBuilder();
    for (Iterator<Tree> i = treeList.iterator(); i.hasNext();) {
      Tree t = i.next();
      sb.append(t.label().value());
      if (i.hasNext()) {
        sb.append(" ");
      }
    }
    return sb.toString();
  }

  private static void localTreeString(Tree t, StringBuilder sb, int level) {
    sb.append("\n");
    for (int i = 0; i < level; i++) {
      sb.append("  ");
    }
    sb.append("(").append(t.label());
    if (level == 0 || isSynthetic(t.label().value())) {
      // if it is synthetic, recurse
      for (int c = 0; c < t.numChildren(); c++) {
        localTreeString(t.getChild(c), sb, level + 1);
      }
    }
    sb.append(")");
  }

  protected static boolean isSynthetic(String label) {
    return label.indexOf('@') > -1;
  }


  Tree binarizeLocalTree(Tree t, int headNum, TaggedWord head) {
    //System.out.println("Working on: "+headNum+" -- "+t.label());
    if (markovFactor) {
      String topCat = t.label().value();
      Label newLabel = new CategoryWordTag(topCat, head.word(), head.tag());
      t.setLabel(newLabel);
      Tree t2;
      if (insideFactor) {
        t2 = markovInsideBinarizeLocalTreeNew(t, headNum, 0, t.numChildren() - 1, true);
        //          t2 = markovInsideBinarizeLocalTree(t, head, headNum, topCat, false);
      } else {
        t2 = markovOutsideBinarizeLocalTree(t, head, headNum, topCat, new LinkedList<Tree>(), false);
      }

      if (DEBUG) {
        CategoryWordTag.printWordTag = false;
        StringBuilder sb1 = new StringBuilder();
        localTreeString(t, sb1, 0);
        StringBuilder sb2 = new StringBuilder();
        localTreeString(t2, sb2, 0);
        System.out.println("Old Local Tree: " + sb1);
        System.out.println("New Local Tree: " + sb2);
        CategoryWordTag.printWordTag = true;
      }
      return t2;
    }
    if (insideFactor) {
      return insideBinarizeLocalTree(t, headNum, head, 0, 0);
    }
    return outsideBinarizeLocalTree(t, t.label().value(), t.label().value(), headNum, head, 0, "", 0, "");
  }

  private Tree markovOutsideBinarizeLocalTree(Tree t, TaggedWord head, int headLoc, String topCat, LinkedList<Tree> ll, boolean doneLeft) {
    String word = head.word();
    String tag = head.tag();
    List<Tree> newChildren = new ArrayList<Tree>(2);
    // call with t, headNum, head, topCat, false
    if (headLoc == 0) {
      if (!doneLeft) {
        // insert a unary to separate the sides
        if (tlp.isStartSymbol(topCat)) {
          return markovOutsideBinarizeLocalTree(t, head, headLoc, topCat, new LinkedList<Tree>(), true);
        }
        String subLabelStr;
        if (simpleLabels) {
          subLabelStr = "@" + topCat;
        } else {
          String headStr = t.getChild(headLoc).label().value();
          subLabelStr = "@" + topCat + ": " + headStr + " ]";
        }
        Label subLabel = new CategoryWordTag(subLabelStr, word, tag);
        Tree subTree = tf.newTreeNode(subLabel, t.getChildrenAsList());
        newChildren.add(markovOutsideBinarizeLocalTree(subTree, head, headLoc, topCat, new LinkedList<Tree>(), true));
        return tf.newTreeNode(t.label(), newChildren);

      }
      int len = t.numChildren();
      // len = 1
      if (len == 1) {
        return tf.newTreeNode(t.label(), Collections.singletonList(t.getChild(0)));
      }
      ll.addFirst(t.getChild(len - 1));
      if (ll.size() > markovOrder) {
        ll.removeLast();
      }
      // generate a right
      String subLabelStr;
      if (simpleLabels) {
        subLabelStr = "@" + topCat;
      } else {
        String headStr = t.getChild(headLoc).label().value();
        String rightStr = (len > markovOrder - 1 ? "... " : "") + join(ll);
        subLabelStr = "@" + topCat + ": " + headStr + " " + rightStr;
      }
      Label subLabel = new CategoryWordTag(subLabelStr, word, tag);
      Tree subTree = tf.newTreeNode(subLabel, t.getChildrenAsList().subList(0, len - 1));
      newChildren.add(markovOutsideBinarizeLocalTree(subTree, head, headLoc, topCat, ll, true));
      newChildren.add(t.getChild(len - 1));
      return tf.newTreeNode(t.label(), newChildren);
    }
    if (headLoc > 0) {
      ll.addLast(t.getChild(0));
      if (ll.size() > markovOrder) {
        ll.removeFirst();
      }
      // generate a left
      String subLabelStr;
      if (simpleLabels) {
        subLabelStr = "@" + topCat;
      } else {
        String headStr = t.getChild(headLoc).label().value();
        String leftStr = join(ll) + (headLoc > markovOrder - 1 ? " ..." : "");
        subLabelStr = "@" + topCat + ": " + leftStr + " " + headStr + " ]";
      }
      Label subLabel = new CategoryWordTag(subLabelStr, word, tag);
      Tree subTree = tf.newTreeNode(subLabel, t.getChildrenAsList().subList(1, t.numChildren()));
      newChildren.add(t.getChild(0));
      newChildren.add(markovOutsideBinarizeLocalTree(subTree, head, headLoc - 1, topCat, ll, false));
      return tf.newTreeNode(t.label(), newChildren);
    }
    return t;
  }

  /**
   * Uses tail recursion. The Tree t that is passed never changes, only the indices left and right do.
   */
  private Tree markovInsideBinarizeLocalTreeNew(Tree t, int headLoc, int left, int right, boolean starting) {
    Tree result;
    Tree[] children = t.children();
    if (starting) {
      // this local tree is a unary and doesn't need binarizing so just return it
      if (left == headLoc && right == headLoc) {
        return t;
      }
      // this local tree started off as a binary and the option to not
      // rebinarized such trees is set
      if (noRebinarization && children.length == 2) {
        return t;
      }
      if (unaryAtTop) {
        // if we're doing grammar compaction, we add the unary at the top
        result = tf.newTreeNode(t.label(), Collections.singletonList(markovInsideBinarizeLocalTreeNew(t, headLoc, left, right, false)));
        return result;
      }
    }
    // otherwise, we're going to make a new tree node
    List<Tree> newChildren = null;
    // left then right top down, this means we generate right then left on the way up
    if (left == headLoc && right == headLoc) {
      // base case, we're done, just make a unary
      newChildren = Collections.singletonList(children[headLoc]);
    } else if (left < headLoc) {
      // generate a left if we can
      newChildren = new ArrayList<Tree>(2);
      newChildren.add(children[left]);
      newChildren.add(markovInsideBinarizeLocalTreeNew(t, headLoc, left + 1, right, false));
    } else if (right > headLoc) {
      // generate a right if we can
      newChildren = new ArrayList<Tree>(2);
      newChildren.add(markovInsideBinarizeLocalTreeNew(t, headLoc, left, right - 1, false));
      newChildren.add(children[right]);
    } else {
      // this shouldn't happen, should have been caught above
      System.err.println("UHOH, bad parameters passed to markovInsideBinarizeLocalTree");
    }
    // newChildren should be set up now with two children
    // make our new label
    Label label;
    if (starting) {
      label = t.label();
    } else {
      label = makeSyntheticLabel(t, left, right, headLoc, markovOrder);
    }
    if (doSelectiveSplit) {
      double stateCount = stateCounter.getCount(label.value());
      if (stateCount < selectiveSplitThreshold) { // too sparse, so
        if (starting && !unaryAtTop) {
          // if we're not compacting grammar, this is how we make sure the top state has the passive symbol
          label = t.label();
        } else {
          label = makeSyntheticLabel(t, left, right, headLoc, markovOrder - 1); // lower order
        }
      }
    } else {
      // otherwise, count up the states
      stateCounter.incrementCount(label.value(), 1.0); // we only care about the category
    }
    // finished making new label
    result = tf.newTreeNode(label, newChildren);
    return result;
  }


  private Label makeSyntheticLabel(Tree t, int left, int right, int headLoc, int markovOrder) {
    Label result;
    if (simpleLabels) {
      result = makeSimpleSyntheticLabel(t);
    } else if (useWrappingLabels) {
      result = makeSyntheticLabel2(t, left, right, headLoc, markovOrder);
    } else {
      result = makeSyntheticLabel1(t, left, right, headLoc, markovOrder);
    }
    //      System.out.println("order " + markovOrder + " yielded " + result);
    return result;
  }

  /**
   * Do nothing other than decorate the label with @
   */
  private static Label makeSimpleSyntheticLabel(Tree t) {
    String topCat = t.label().value();
    String labelStr = "@" + topCat;
    String word = ((HasWord) t.label()).word();
    String tag = ((HasTag) t.label()).tag();
    return new CategoryWordTag(labelStr, word, tag);
  }

  /**
   * For a dotted rule VP^S -> RB VP NP PP . where VP is the head
   * makes label of the form: @VP^S| [ RB [VP] ... PP ]
   * where the constituent after the @ is the passive that we are building
   * and  the constituent in brackets is the head
   * and the brackets on the left and right indicate whether or not there
   * are more constituents to add on those sides.
   */
  private static Label makeSyntheticLabel1(Tree t, int left, int right, int headLoc, int markovOrder) {
    String topCat = t.label().value();
    Tree[] children = t.children();
    String leftString;
    if (left == 0) {
      leftString = "[ ";
    } else {
      leftString = " ";
    }
    String rightString;
    if (right == children.length - 1) {
      rightString = " ]";
    } else {
      rightString = " ";
    }
    for (int i = 0; i < markovOrder; i++) {
      if (left < headLoc) {
        leftString = leftString + children[left].label().value() + " ";
        left++;
      } else if (right > headLoc) {
        rightString = " " + children[right].label().value() + rightString;
        right--;
      } else {
        break;
      }
    }
    if (right > headLoc) {
      rightString = "..." + rightString;
    }
    if (left < headLoc) {
      leftString = leftString + "...";
    }
    String labelStr = "@" + topCat + "| " + leftString + "[" + t.getChild(headLoc).label().value() + "]" + rightString; // the head in brackets
    String word = ((HasWord) t.label()).word();
    String tag = ((HasTag) t.label()).tag();
    return new CategoryWordTag(labelStr, word, tag);
  }

  /**
   * for a dotted rule VP^S -> RB VP NP PP . where VP is the head
   * makes label of the form: @VP^S| VP_ ... PP> RB[
   */
  private Label makeSyntheticLabel2(Tree t, int left, int right, int headLoc, int markovOrder) {
    String topCat = t.label().value();
    Tree[] children = t.children();
    String finalPiece;
    int i = 0;
    if (markFinalStates) {
      // figure out which one is final
      if (headLoc != 0 && left == 0) {
        // we are finishing on the left
        finalPiece = " " + children[left].label().value() + "[";
        left++;
        i++;
      } else if (headLoc == 0 && right > headLoc && right == children.length - 1) {
        // we are finishing on the right
        finalPiece = " " + children[right].label().value() + "]";
        right--;
        i++;
      } else {
        finalPiece = "";
      }
    } else {
      finalPiece = "";
    }

    String middlePiece = "";
    for (; i < markovOrder; i++) {
      if (left < headLoc) {
        middlePiece = " " + children[left].label().value() + "<" + middlePiece;
        left++;
      } else if (right > headLoc) {
        middlePiece = " " + children[right].label().value() + ">" + middlePiece;
        right--;
      } else {
        break;
      }
    }
    if (right > headLoc || left < headLoc) {
      middlePiece = " ..." + middlePiece;
    }
    String headStr = t.getChild(headLoc).label().value();
    // Optimize memory allocation for this next line, since these are the
    // String's that linger.
    // String labelStr = "@" + topCat + "| " + headStr + "_" + middlePiece + finalPiece;
    int leng = 1 + 2 + 1 + topCat.length() + headStr.length() + middlePiece.length() + finalPiece.length();
    StringBuilder sb = new StringBuilder(leng);
    sb.append("@").append(topCat).append("| ").append(headStr).append("_").append(middlePiece).append(finalPiece);
    String labelStr = sb.toString();
    // System.err.println("makeSyntheticLabel2: " + labelStr);

    String word = ((HasWord) t.label()).word();
    String tag = ((HasTag) t.label()).tag();
    return new CategoryWordTag(labelStr, word, tag);
  }

  private Tree insideBinarizeLocalTree(Tree t, int headNum, TaggedWord head, int leftProcessed, int rightProcessed) {
    String word = head.word();
    String tag = head.tag();
    List<Tree> newChildren = new ArrayList<Tree>(2);      // check done
    if (t.numChildren() <= leftProcessed + rightProcessed + 2) {
      Tree leftChild = t.getChild(leftProcessed);
      newChildren.add(leftChild);
      if (t.numChildren() == leftProcessed + rightProcessed + 1) {
        // unary ... so top level
        String finalCat = t.label().value();
        return tf.newTreeNode(new CategoryWordTag(finalCat, word, tag), newChildren);
      }
      // binary
      Tree rightChild = t.getChild(leftProcessed + 1);
      newChildren.add(rightChild);
      String labelStr = t.label().value();
      if (leftProcessed != 0 || rightProcessed != 0) {
        labelStr = ("@ " + leftChild.label().value() + " " + rightChild.label().value());
      }
      return tf.newTreeNode(new CategoryWordTag(labelStr, word, tag), newChildren);
    }
    if (headNum > leftProcessed) {
      // eat left word
      Tree leftChild = t.getChild(leftProcessed);
      Tree rightChild = insideBinarizeLocalTree(t, headNum, head, leftProcessed + 1, rightProcessed);
      newChildren.add(leftChild);
      newChildren.add(rightChild);
      String labelStr = ("@ " + leftChild.label().value() + " " + rightChild.label().value().substring(2));
      if (leftProcessed == 0 && rightProcessed == 0) {
        labelStr = t.label().value();
      }
      return tf.newTreeNode(new CategoryWordTag(labelStr, word, tag), newChildren);
    } else {
      // eat right word
      Tree leftChild = insideBinarizeLocalTree(t, headNum, head, leftProcessed, rightProcessed + 1);
      Tree rightChild = t.getChild(t.numChildren() - rightProcessed - 1);
      newChildren.add(leftChild);
      newChildren.add(rightChild);
      String labelStr = ("@ " + leftChild.label().value().substring(2) + " " + rightChild.label().value());
      if (leftProcessed == 0 && rightProcessed == 0) {
        labelStr = t.label().value();
      }
      return tf.newTreeNode(new CategoryWordTag(labelStr, word, tag), newChildren);
    }
  }

  private Tree outsideBinarizeLocalTree(Tree t, String labelStr, String finalCat, int headNum, TaggedWord head, int leftProcessed, String leftStr, int rightProcessed, String rightStr) {
    List<Tree> newChildren = new ArrayList<Tree>(2);
    Label label = new CategoryWordTag(labelStr, head.word(), head.tag());
    // check if there are <=2 children already
    if (t.numChildren() - leftProcessed - rightProcessed <= 2) {
      // done, return
      newChildren.add(t.getChild(leftProcessed));
      if (t.numChildren() - leftProcessed - rightProcessed == 2) {
        newChildren.add(t.getChild(leftProcessed + 1));
      }
      return tf.newTreeNode(label, newChildren);
    }
    if (headNum > leftProcessed) {
      // eat a left word
      Tree leftChild = t.getChild(leftProcessed);
      String childLeftStr = leftStr + " " + leftChild.label().value();
      String childLabelStr;
      if (simpleLabels) {
        childLabelStr = "@" + finalCat;
      } else {
        childLabelStr = "@" + finalCat + " :" + childLeftStr + " ..." + rightStr;
      }
      Tree rightChild = outsideBinarizeLocalTree(t, childLabelStr, finalCat, headNum, head, leftProcessed + 1, childLeftStr, rightProcessed, rightStr);
      newChildren.add(leftChild);
      newChildren.add(rightChild);
      return tf.newTreeNode(label, newChildren);
    } else {
      // eat a right word
      Tree rightChild = t.getChild(t.numChildren() - rightProcessed - 1);
      String childRightStr = " " + rightChild.label().value() + rightStr;
      String childLabelStr;
      if (simpleLabels) {
        childLabelStr = "@" + finalCat;
      } else {
        childLabelStr = "@" + finalCat + " :" + leftStr + " ..." + childRightStr;
      }
      Tree leftChild = outsideBinarizeLocalTree(t, childLabelStr, finalCat, headNum, head, leftProcessed, leftStr, rightProcessed + 1, childRightStr);
      newChildren.add(leftChild);
      newChildren.add(rightChild);
      return tf.newTreeNode(label, newChildren);
    }
  }


  /** Binarizes the tree according to options set up in the constructor.
   *  Does the whole tree by calling itself recursively.
   *
   *  @param t A tree to be binarized. The non-leaf nodes must already have
   *    CategoryWordTag labels, with heads percolated.
   *  @return A binary tree.
   */
  public Tree transformTree(Tree t) {
    // handle null
    if (t == null) {
      return null;
    }

    String cat = t.label().value();
    // handle words
    if (t.isLeaf()) {
      Label label = new Word(cat);//new CategoryWordTag(cat,cat,"");
      return tf.newLeaf(label);
    }
    // handle tags
    if (t.isPreTerminal()) {
      Tree childResult = transformTree(t.getChild(0));
      String word = childResult.value();  // would be nicer if Word/CWT ??
      List<Tree> newChildren = new ArrayList<Tree>(1);
      newChildren.add(childResult);
      return tf.newTreeNode(new CategoryWordTag(cat, word, cat), newChildren);
    }
    // handle categories
    Tree headChild = hf.determineHead(t);
    /*
    System.out.println("### finding head for:");
    t.pennPrint();
    System.out.println("### its head is:");
    headChild.pennPrint();
    */
    if (headChild == null && ! t.label().value().startsWith(tlp.startSymbol())) {
      System.err.println("### No head found for:");
      t.pennPrint();
    }
    int headNum = -1;
    Tree[] kids = t.children();
    List<Tree> newChildren = new ArrayList<Tree>(kids.length);
    for (int childNum = 0; childNum < kids.length; childNum++) {
      Tree child = kids[childNum];
      Tree childResult = transformTree(child);   // recursive call
      if (child == headChild) {
        headNum = childNum;
      }
      newChildren.add(childResult);
    }
    Tree result;
    // XXXXX UPTO HERE!!!  ALMOST DONE!!!
    if (t.label().value().startsWith(tlp.startSymbol())) {
      // handle the ROOT tree properly
      /*
      //CategoryWordTag label = (CategoryWordTag) t.label();
      // binarize without the last kid and then add it back to the top tree
      Tree lastKid = (Tree)newChildren.remove(newChildren.size()-1);
      Tree tempTree = tf.newTreeNode(label, newChildren);
      tempTree = binarizeLocalTree(tempTree, headNum, result.head);
      newChildren = tempTree.getChildrenAsList();
      newChildren.add(lastKid); // add it back
      */
      result = tf.newTreeNode(t.label(), newChildren); // label shouldn't have changed
    } else {
//      CategoryWordTag headLabel = (CategoryWordTag) headChild.label();
      String word = ((HasWord) headChild.label()).word();
      String tag = ((HasTag) headChild.label()).tag();
      Label label = new CategoryWordTag(cat, word, tag);
      result = tf.newTreeNode(label, newChildren);
      // cdm Mar 2005: invent a head so I don't have to rewrite all this
      // code, but with the removal of TreeHeadPair, some of the rest of
      // this should probably be rewritten too to not use this head variable
      TaggedWord head = new TaggedWord(word, tag);
      result = binarizeLocalTree(result, headNum, head);
    }
    return result;
  }


  /** Build a custom binarizer for Trees.
   *
   * @param hf the HeadFinder to use in binarization
   * @param tlp the TreebankLanguagePack to use
   * @param insideFactor whether to do inside markovization
   * @param markovFactor whether to markovize the binary rules
   * @param markovOrder the markov order to use; only relevant with markovFactor=true
   * @param useWrappingLabels whether to use state names (labels) that allow wrapping from right to left
   * @param unaryAtTop Whether to actually materialize the unary that rewrites
   *        a passive state to the active rule at the top of an original local
   *        tree.  This is used only when compaction is happening
   * @param selectiveSplitThreshold if selective split is used, this will be the threshold used to decide which state splits to keep
   * @param markFinalStates whether or not to make the state names (labels) of the final active states distinctive
   */
  public TreeBinarizer(HeadFinder hf, TreebankLanguagePack tlp,
                       boolean insideFactor,
                       boolean markovFactor, int markovOrder,
                       boolean useWrappingLabels, boolean unaryAtTop,
                       double selectiveSplitThreshold, boolean markFinalStates,
                       boolean simpleLabels, boolean noRebinarization) {
    this.hf = hf;
    this.tlp = tlp;
    this.tf = new LabeledScoredTreeFactory(new CategoryWordTagFactory());
    this.insideFactor = insideFactor;
    this.markovFactor = markovFactor;
    this.markovOrder = markovOrder;
    this.useWrappingLabels = useWrappingLabels;
    this.unaryAtTop = unaryAtTop;
    this.selectiveSplitThreshold = selectiveSplitThreshold;
    this.markFinalStates = markFinalStates;
    this.simpleLabels = simpleLabels;
    this.noRebinarization = noRebinarization;
  }


  /** Let's you test out the TreeBinarizer on the command line.
   *  This main method doesn't yet handle as many flags as one would like.
   *  But it does have:
   *  <ul>
   *  <li> -tlp TreebankLanguagePack
   *  <li>-tlpp TreebankLangParserParams
   *  <li>-insideFactor
   *  <li>-markovOrder
   *  </ul>
   *
   *  @param args Command line arguments: flags as above, as above followed by
   *     treebankPath
   */
  public static void main(String[] args) {
    TreebankLangParserParams tlpp = null;
    // TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    // TreeReaderFactory trf = new LabeledScoredTreeReaderFactory();
    // Looks like it must build CategoryWordTagFactory!!
    TreeReaderFactory trf = new TreeReaderFactory() {
	public TreeReader newTreeReader(Reader in) {
	  return new PennTreeReader(in,
				    new LabeledScoredTreeFactory(
					      new CategoryWordTagFactory()),
				    new BobChrisTreeNormalizer());
	}
      };

    String fileExt = "mrg";
    HeadFinder hf = new ModCollinsHeadFinder();
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    boolean insideFactor = false;
    boolean mf = false;
    int mo = 1;
    boolean uwl = false;
    boolean uat = false;
    double sst = 20.0;
    boolean mfs = false;
    boolean simpleLabels = false;
    boolean noRebinarization = false;

    int i = 0;
    while (i < args.length && args[i].startsWith("-")) {
      if (args[i].equalsIgnoreCase("-tlp") && i + 1 < args.length) {
	try {
	  tlp = (TreebankLanguagePack) Class.forName(args[i+1]).newInstance();
	} catch (Exception e) {
	  System.err.println("Couldn't instantiate: " + args[i+1]);
          throw new RuntimeException(e);
	}
	i++;
      } else if (args[i].equalsIgnoreCase("-tlpp") && i + 1 < args.length) {
	try {
	  tlpp = (TreebankLangParserParams) Class.forName(args[i+1]).newInstance();
	} catch (Exception e) {
	  System.err.println("Couldn't instantiate: " + args[i+1]);
          throw new RuntimeException(e);
	}
	i++;
      } else if (args[i].equalsIgnoreCase("-insideFactor")) {
	insideFactor = true;
      } else if (args[i].equalsIgnoreCase("-markovOrder") && i + 1 < args.length) {
        i++;
        mo = Integer.parseInt(args[i]);
      } else if (args[i].equalsIgnoreCase("-simpleLabels")) {
        simpleLabels = true;
      } else if (args[i].equalsIgnoreCase("-noRebinarization")) {
        noRebinarization = true;
      } else {
        System.err.println("Unknown option:" + args[i]);
      }
      i++;
    }
    if (i >= args.length) {
      System.err.println("usage: java TreeBinarizer [-tlpp class|-markovOrder int|...] treebankPath");
      System.exit(0);
    }
    Treebank treebank;
    if (tlpp != null) {
      treebank = tlpp.memoryTreebank();
      tlp = tlpp.treebankLanguagePack();
      fileExt = tlp.treebankFileExtension();
      hf = tlpp.headFinder();
    } else {
      treebank = new DiskTreebank(trf);
    }
    treebank.loadPath(args[i], fileExt, true);

    TreeTransformer tt = new TreeBinarizer(hf, tlp, insideFactor, mf, mo,
					   uwl, uat, sst, mfs, 
                                           simpleLabels, noRebinarization);

    for (Tree t : treebank) {
      Tree newT = tt.transformTree(t);
      System.out.println("Original tree:");
      t.pennPrint();
      System.out.println("Binarized tree:");
      newT.pennPrint();
      System.out.println();
    }
  } // end main

}
