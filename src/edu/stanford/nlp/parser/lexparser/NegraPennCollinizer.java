package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TreeFactory;


public class NegraPennCollinizer implements AbstractCollinizer {

  /** A logger for this class */
  Redwood.RedwoodChannels log = Redwood.channels(NegraPennCollinizer.class);

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

  public Tree transformTree(Tree guess, Tree gold) {
    if (guess == null || gold == null) return null;
    if (guess.yield().size() != gold.yield().size()) {
      return null;
    }

    return transformTree(guess, Trees.preTerminals(gold).iterator());
  }

  private Tree transformTree(Tree guess, Iterator<Tree> goldPreterminals) {
    Label l = guess.label();
    if (guess.isLeaf()) {
      return tf.newLeaf(l);
    }
    String s = l.value();
    s = tlpp.treebankLanguagePack().basicCategory(s);
    if (deletePunct && guess.isPreTerminal()) {
      // Eliminate unwanted (in terms of evaluation) punctuation
      // by comparing the gold punctuation, not the guess tree
      // This way, retagging does not change the results
      Tree goldPT = goldPreterminals.next();
      String goldTag = tlpp.treebankLanguagePack().basicCategory(goldPT.value());
      if (tlpp.treebankLanguagePack().isEvalBIgnoredPunctuationTag(goldTag)) {
        return null;
      }
    }
    // TEMPORARY: eliminate the TOPP constituent
    if (guess.children()[0].label().value().equals("TOPP")) {
      log.info("Found a TOPP");
      guess.setChildren(guess.children()[0].children());
    }

    // Negra has lots of non-unary roots; delete unary roots
    if (tlpp.treebankLanguagePack().isStartSymbol(s) && guess.numChildren() == 1) {
      // NB: This deletes the boundary symbol, which is in the tree!
      return transformTree(guess.getChild(0), goldPreterminals);
    }
    List<Tree> children = new ArrayList<>();
    for (int cNum = 0, numC = guess.numChildren(); cNum < numC; cNum++) {
      Tree child = guess.getChild(cNum);
      Tree newChild = transformTree(child, goldPreterminals);
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
