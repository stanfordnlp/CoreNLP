package edu.stanford.nlp.parser.lexparser;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.HasCategory;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;

/**
 * Performs non-language specific annotation of Trees.
 *
 * @author Dan Klein
 * @author Christopher Manning
 */
public class TreeAnnotator implements TreeTransformer {

  private TreeFactory tf;
  private TreebankLangParserParams tlpParams;
  private HeadFinder hf;
  private TrainOptions trainOptions;

  public TreeAnnotator(HeadFinder hf, TreebankLangParserParams tlpp,
                       Options op) {
    this.tlpParams = tlpp;
    this.hf = hf;
    this.tf = new LabeledScoredTreeFactory();
    this.trainOptions = op.trainOptions;
  }

  /** Do the category splitting of the tree passed in.
   *  This method defensively copies its argument, which is not changed.
   *
   *  @param t The tree to be annotated.  This can be any tree with a
   *     {@code value()} stored in Labels.  The tree is assumed to have
   *     preterminals that are parts of speech.
   *  @return The annotated version of the Tree (which is a completely
   *     separate Tree with new tree structure and new labels).  The
   *     non-leaf nodes of the tree will be CategoryWordTag objects.
   */
  @Override
  public Tree transformTree(Tree t) {
    // make a defensive copy which the helper method can then mangle
    Tree copy = t.deepCopy(tf);
    if (trainOptions.markStrahler) {
      markStrahler(copy);
    }
    return transformTreeHelper(copy, copy);
  }

  /**
   * Do the category splitting of the tree passed in.
   * This is initially called on the root node of a tree, and it recursively
   * calls itself on children.  A depth first left-to-right traversal is
   * done whereby a tree node's children are first transformed and then
   * the parent is transformed.  At the time of calling, the original root
   * always sits above the current node.  This routine can be assumed to,
   * and does, change the tree passed in: it destructively modifies tree nodes,
   * and makes new tree structure when it needs to.
   *
   * @param t The tree node to subcategorize.
   * @param root The root of the tree.  It must contain {@code t} or
   *     this code will throw a NullPointerException.
   * @return The annotated tree.
   */
  private Tree transformTreeHelper(Tree t, Tree root) {
    if (t == null) {
      // handle null
      return null;
    }
    if (t.isLeaf()) {
      //No need to change the label
      return t;
    }

    String cat = t.label().value();
    Tree parent;
    String parentStr;
    String grandParentStr;
    if (root == null || t.equals(root)) {
      parent = null;
      parentStr = "";
    } else {
      parent = t.parent(root);
      parentStr = parent.label().value();
    }
    if (parent == null || parent.equals(root)) {
      grandParentStr = "";
    } else {
      grandParentStr = parent.parent(root).label().value();
    }
    String baseParentStr = tlpParams.treebankLanguagePack().basicCategory(parentStr);
    String baseGrandParentStr = tlpParams.treebankLanguagePack().basicCategory(grandParentStr);
    //System.out.println(t.label().value() + " " + parentStr + " " + grandParentStr);

    if (t.isPreTerminal()) {
      // handle tags
      Tree childResult = transformTreeHelper(t.children()[0], null); // recurse
      String word = childResult.value();  // would be nicer if Word/CWT ??

      if ( ! trainOptions.noTagSplit) {
        if (trainOptions.tagPA) {
          String test = cat + "^" + baseParentStr;
          if (!trainOptions.tagSelectiveSplit || trainOptions.splitters.contains(test)) {
            cat = test;
          }
        }
        if (trainOptions.markUnaryTags && parent.numChildren() == 1) {
          cat = cat + "^U";
        }
      } // otherwise, leave the tags alone!

      // Label label = new CategoryWordTag(cat, word, cat);
      Label label = t.label().labelFactory().newLabel(t.label());
      label.setValue(cat);
      if(label instanceof HasCategory)
        ((HasCategory) label).setCategory(cat);
      if(label instanceof HasWord)
        ((HasWord) label).setWord(word);
      if(label instanceof HasTag)
        ((HasTag) label).setTag(cat);


      t.setLabel(label);
      t.setChild(0, childResult);  // just in case word is changed
      if (trainOptions.noTagSplit) {
        return t;
      } else {
        // language-specific transforms
        return tlpParams.transformTree(t, root);
      }
    } // end isPreTerminal()

    // handle phrasal categories
    Tree[] kids = t.children();
    for (int childNum = 0; childNum < kids.length; childNum++) {
      Tree child = kids[childNum];
      Tree childResult = transformTreeHelper(child, root); // recursive call
      t.setChild(childNum, childResult);
    }

    Tree headChild = hf.determineHead(t);
    if(headChild == null || headChild.label() == null) {
      throw new RuntimeException("TreeAnnotator: null head found for tree [suggesting incomplete/wrong HeadFinder]:\n" + t);
    }

    Label headLabel = headChild.label();

    if( ! (headLabel instanceof HasWord))
      throw new RuntimeException("TreeAnnotator: Head label lacks a Word annotation!");
    if( ! (headLabel instanceof HasTag))
      throw new RuntimeException("TreeAnnotator: Head label lacks a Tag annotation!");

    String word = ((HasWord) headLabel).word();
    String tag = ((HasTag) headLabel).tag();

    // String baseTag = tlpParams.treebankLanguagePack().basicCategory(tag);
    String baseCat = tlpParams.treebankLanguagePack().basicCategory(cat);

    /* Sister annotation. Potential problem: if multiple sisters are
     * strong indicators for a single category's expansions.  This
     * happens concretely in the Chinese Treebank when NP (object)
     * has left sisters VV and AS.  Could lead to too much
     * sparseness.  The ideal solution would be to give the
     * splitting list an ordering, and take only the highest (~most
     * informative/reliable) sister annotation.
     */
    if (trainOptions.sisterAnnotate && !trainOptions.smoothing && baseParentStr.length() > 0) {
      List<String> leftSis = listBasicCategories(SisterAnnotationStats.leftSisterLabels(t, parent));
      List<String> rightSis = listBasicCategories(SisterAnnotationStats.rightSisterLabels(t, parent));

      List<String> leftAnn = new ArrayList<>();
      List<String> rightAnn = new ArrayList<>();

      for (String s : leftSis) {
        //s = baseCat+"=l="+tlpParams.treebankLanguagePack().basicCategory(s);
        leftAnn.add(baseCat + "=l=" + tlpParams.treebankLanguagePack().basicCategory(s));
        //System.out.println("left-annotated test string " + s);
      }
      for (String s : rightSis) {
        //s = baseCat+"=r="+tlpParams.treebankLanguagePack().basicCategory(s);
        rightAnn.add(baseCat + "=r=" + tlpParams.treebankLanguagePack().basicCategory(s));
      }
      for (Iterator<String> j = rightAnn.iterator(); j.hasNext();) {
        //System.out.println("new rightsis " + (String)j.next()); //debugging
      }
      for (String annCat : trainOptions.sisterSplitters) {
        //System.out.println("annotated test string " + annCat);
        if (leftAnn.contains(annCat) || rightAnn.contains(annCat)) {
          cat = cat + annCat.replaceAll("^" + baseCat, "");
          break;
        }
      }
    }

    if (trainOptions.PA && !trainOptions.smoothing && baseParentStr.length() > 0) {
      String cat2 = baseCat + "^" + baseParentStr;
      if (!trainOptions.selectiveSplit || trainOptions.splitters.contains(cat2)) {
        cat = cat + "^" + baseParentStr;
      }
    }
    if (trainOptions.gPA && !trainOptions.smoothing && grandParentStr.length() > 0) {
      if (trainOptions.selectiveSplit) {
        String cat2 = baseCat + "^" + baseParentStr + "~" + baseGrandParentStr;
        if (cat.contains("^") && trainOptions.splitters.contains(cat2)) {
          cat = cat + "~" + baseGrandParentStr;
        }
      } else {
        cat = cat + "~" + baseGrandParentStr;
      }
    }
    if (trainOptions.markUnary > 0) {
      if (trainOptions.markUnary == 1 && kids.length == 1 && kids[0].depth() >= 2) {
        cat = cat + "-U";
      } else if (trainOptions.markUnary == 2 && parent != null && parent.numChildren() == 1 && t.depth() >= 2) {
        cat = cat + "-u";
      }
    }
    if (trainOptions.rightRec && rightRec(t, baseCat)) {
      cat = cat + "-R";
    }
    if (trainOptions.leftRec && leftRec(t, baseCat)) {
      cat = cat + "-L";
    }
    if (trainOptions.splitPrePreT && t.isPrePreTerminal()) {
      cat = cat + "-PPT";
    }

//    Label label = new CategoryWordTag(cat, word, tag);
    Label label = t.label().labelFactory().newLabel(t.label());
    label.setValue(cat);
    if(label instanceof HasCategory)
      ((HasCategory) label).setCategory(cat);
    if(label instanceof HasWord)
      ((HasWord) label).setWord(word);
    if(label instanceof HasTag)
      ((HasTag) label).setTag(tag);

    t.setLabel(label);

    return tlpParams.transformTree(t, root);
  }


  private List<String> listBasicCategories(List<String> l) {
    List<String> l1 = new ArrayList<>();
    for (String str : l) {
      l1.add(tlpParams.treebankLanguagePack().basicCategory(str));
    }
    return l1;
  }


  private static boolean rightRec(Tree t, String baseCat) {
    if (//! baseCat.equals("S") &&
            !baseCat.equals("NP")) {
      return false;
    }
    while (!t.isLeaf()) {
      t = t.lastChild();
      String str = t.label().value();
      if (str.startsWith(baseCat)) {
        return true;
      }
    }
    return false;
  }

  private static boolean leftRec(Tree t, String baseCat) {
    while (!t.isLeaf()) {
      t = t.firstChild();
      String str = t.label().value();
      if (str.startsWith(baseCat)) {
        return true;
      }
    }
    return false;
  }

  private static int markStrahler(Tree t) {
    if (t.isLeaf()) {
      // don't annotate the words at leaves!
      return 1;
    } else {
      String cat = t.label().value();
      int maxStrahler = -1;
      int maxMultiplicity = 0;
      for (int i = 0; i < t.numChildren(); i++) {
        int strahler = markStrahler(t.getChild(i));
        if (strahler > maxStrahler) {
          maxStrahler = strahler;
          maxMultiplicity = 1;
        } else if (strahler == maxStrahler) {
          maxMultiplicity++;
        }
      }
      if (maxMultiplicity > 1) {
        maxStrahler++;  // this is the one case where it grows
      }
      cat = cat + '~' + maxStrahler;
      Label label = t.label().labelFactory().newLabel(t.label());
      label.setValue(cat);
      t.setLabel(label);
      return maxStrahler;
    }
  }

}
