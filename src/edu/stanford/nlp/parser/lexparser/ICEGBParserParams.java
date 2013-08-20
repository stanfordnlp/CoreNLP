package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.icegb.ICEGBLabelFactory;
import edu.stanford.nlp.trees.international.icegb.ICEGBTreeReader;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Parser parameters for the ICE-GB corpus. UNDER CONSTRUCTION!
 *
 * @author Pi-Chuan Chang
 */

public class ICEGBParserParams extends AbstractTreebankParserParams {


  public ICEGBParserParams() {
    super(new edu.stanford.nlp.trees.international.icegb.ICEGBLanguagePack());
  }

  @Override
  public HeadFinder headFinder() {
    return new LeftHeadFinder();
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return headFinder();
  }

  public TreeReaderFactory treeReaderFactory() {
    return new TreeReaderFactory() {
      public TreeReader newTreeReader(Reader in) {
        return new ICEGBTreeReader(in, new LabeledScoredTreeFactory(new ICEGBLabelFactory()));
      }
    };
  }


  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory());
  }


  public DiskTreebank diskTreebank() {
    return new DiskTreebank(treeReaderFactory());
  }


  // TODO..
  /**
   * the tree transformer used to produce trees for evaluation.  Will
   * be applied both to the
   */
  @Override
  public TreeTransformer collinizer() {
    //throw new UnsupportedOperationException();
    return new ICEGBCollinizer(tlp, false);
    //return new DummyTreeTransformer();
  }

  @Override
  public TreeTransformer collinizerEvalb() {
    //throw new UnsupportedOperationException();
    return new ICEGBCollinizer(tlp, false);
    //return new DummyTreeTransformer();
  }

  /**
   * contains corpus-specific (but not parser-specific) info such
   * as what is punctuation, and also information about the structure
   * of labels
   */
  @Override
  public TreebankLanguagePack treebankLanguagePack() {
    return new edu.stanford.nlp.trees.international.icegb.ICEGBLanguagePack();
  }


  @Override
  public String[] sisterSplitters() {
    return new String[0];
  }

  /**
   * transformTree does language-specific tree transformations such
   * as splicing. Any parameterizations should be inside the specific
   * TreebankLangParserParams class
   */
  @Override
  public Tree transformTree(Tree t, Tree root) {
    Tree parent;
    Tree grandParent;
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
      grandParent = null;
      grandParentStr = "";
    } else {
      grandParent = parent.parent(root);
      grandParentStr = grandParent.label().value();
    }
    String baseParentStr = tlp.basicCategory(parentStr);
    String baseGrandParentStr = tlp.basicCategory(grandParentStr);

    if (t.isLeaf()) {
      return t;
    }

    CategoryWordTag lab = (CategoryWordTag) t.label();
    String word = lab.word();
    String tag = lab.tag();
    String baseTag = tlp.basicCategory(tag);
    String cat = lab.value();
    String baseCat = tlp.basicCategory(cat);

    if (t.isPreTerminal()) {

    } else {  // if (t.isPhrasal())

    }

    Label label = new CategoryWordTag(cat, word, tag);
    t.setLabel(label);
    return t;
  }


  /**
   * Set language-specific options according to flags.
   */
  @Override
  public int setOptionFlag(String[] args, int i) {
    // [CDM 2008: there are no generic options!] first, see if it's a generic option
    // int j = super.setOptionFlag(args, i);
    // if(i != j) return j;

    //lang. specific options
    return i;
  }


  /**
   * Return a default sentence for the language (for testing)
   */
  public List defaultTestSentence() {
    return Arrays.asList(new String[]{"This", "is", "just", "a", "test", "."});
  }

  @Override
  public void display() {
    // TODO
  }


  private static final long serialVersionUID = 2L;

}

// copy from ChineseCollinizer. TODO: clean up

class ICEGBCollinizer implements TreeTransformer {

  private final boolean deletePunct;
  private final boolean addFeatures = true;
  TreebankLanguagePack ctlp;

  protected TreeFactory tf = new LabeledScoredTreeFactory();


  public ICEGBCollinizer(TreebankLanguagePack ctlp) {
    this(ctlp, true);
  }

  public ICEGBCollinizer(TreebankLanguagePack ctlp, boolean deletePunct) {
    this.deletePunct = deletePunct;
    this.ctlp = ctlp;
  }


  public Tree transformTree(Tree tree) {
    String label = tree.label().value();

    //System.out.println("Node label is " + label);

    if (tree.isLeaf()) {
      if (deletePunct && ctlp.isPunctuationWord(label)) {
        return null;
      } else {
        return tf.newLeaf(new StringLabel(label));
      }
    }
    if (tree.isPreTerminal() && deletePunct && ctlp.isPunctuationTag(label)) {
      // System.out.println("Deleting punctuation");
      return null;
    }
    List children = new ArrayList();

    if (ctlp.isStartSymbol(label) && tree.children().length == 1) { // keep non-unary roots for now
      return transformTree(tree.children()[0]);
    }

    //System.out.println("Enhanced label is " + label);

    if (addFeatures) {
      label = label.replaceFirst("\\[.*$", "");
    }



    //System.out.println("New label is " + label);

    for (int cNum = 0; cNum < tree.children().length; cNum++) {
      Tree child = tree.children()[cNum];
      Tree newChild = transformTree(child);
      if (newChild != null) {
        children.add(newChild);
      }
    }
    if (children.size() == 0) {
      return null;
    }
    return tf.newTreeNode(new StringLabel(label), children);
  }

}
