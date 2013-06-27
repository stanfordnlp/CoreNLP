package edu.stanford.nlp.parser.lexparser;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeTransformer;


class NegraPennCollinizer implements TreeTransformer {

  private TreebankLangParserParams tlpp; 
  private final boolean deletePunct;

  public NegraPennCollinizer(TreebankLangParserParams tlpp) {
    this(tlpp, true);
  }

  public NegraPennCollinizer(TreebankLangParserParams tlpp, boolean deletePunct) {
    this.tlpp = tlpp;
    this.deletePunct = deletePunct;
  }

  protected TreeFactory tf = new LabeledScoredTreeFactory();

  public Tree transformTree(Tree tree) {
    Label l = tree.label();
    if (tree.isLeaf()) {
      return tf.newLeaf(l);
    }
    String s = l.value();
    s = tlpp.treebankLanguagePack().basicCategory(s);
    if (deletePunct) {
      // this is broken as it's not the right thing to do when there
      // is any tag ambiguity -- and there is for ' (POS/'').  Sentences
      // can then have more or less words.  It's also unnecessary for EVALB,
      // since it ignores punctuation anyway
      if (tree.isPreTerminal() && tlpp.treebankLanguagePack().isEvalBIgnoredPunctuationTag(s)) {
        return null;
      }
    }
    // TEMPORARY: eliminate the TOPP constituent
    if (tree.children()[0].label().value().equals("TOPP")) {
      System.err.println("Found a TOPP");
      tree.setChildren(tree.children()[0].children());
    }

    // Negra has lots of non-unary roots; delete unary roots
    if (tlpp.treebankLanguagePack().isStartSymbol(s) && tree.numChildren() == 1) {
      // NB: This deletes the boundary symbol, which is in the tree!
      return transformTree(tree.getChild(0));
    }
    List<Tree> children = new ArrayList<Tree>();
    for (int cNum = 0, numC = tree.numChildren(); cNum < numC; cNum++) {
      Tree child = tree.getChild(cNum);
      Tree newChild = transformTree(child);
      if (newChild != null) {
        children.add(newChild);
      }
    }
    if (children.isEmpty()) {
      return null;
    }
    return tf.newTreeNode(new StringLabel(s), children);
  }
}
