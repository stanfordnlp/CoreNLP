package edu.stanford.nlp.trees.international.pennchinese; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.parser.lexparser.AbstractCollinizer;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TreeFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Performs collinization operations on Chinese trees similar to
 * those for English Namely: <ul>
 * <li> strips all functional &amp; automatically-added tags
 * <li> strips all punctuation
 * <li> merges PRN and ADVP
 * <li> eliminates ROOT (note that there are a few non-unary ROOT nodes;
 * these are not eliminated)
 * </ul>
 *
 * @author Roger Levy
 * @author Christopher Manning
 */
public class ChineseCollinizer implements AbstractCollinizer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseCollinizer.class);

  private final static boolean VERBOSE = false;

  private final boolean deletePunct;
  ChineseTreebankLanguagePack ctlp;

  protected TreeFactory tf = new LabeledScoredTreeFactory();


  public ChineseCollinizer(ChineseTreebankLanguagePack ctlp) {
    this(ctlp, true);
  }

  public ChineseCollinizer(ChineseTreebankLanguagePack ctlp, boolean deletePunct) {
    this.deletePunct = deletePunct;
    this.ctlp = ctlp;
  }


  public Tree transformTree(Tree guess, Tree gold) {
    return transformTree(guess, gold, true);
  }

  private Tree transformTree(Tree guess, Tree gold, boolean isRoot) {
    if (guess == null || gold == null) return null;
    if (guess.yield().size() != gold.yield().size()) {
      return null;
    }

    return transformTree(guess, Trees.preTerminals(gold).iterator(), isRoot);
  }

  private Tree transformTree(Tree guess, Iterator<Tree> goldPreterminals, boolean isRoot) {
    String label = guess.label().value();

    // log.info("ChineseCollinizer: Node label is " + label);

    // Eliminate unwanted (in terms of evaluation) punctuation
    // by comparing the gold punctuation, not the guess tree
    // This way, retagging does not change the results
    if (guess.isPreTerminal() && deletePunct) {
      Tree goldPT = goldPreterminals.next();
      if (ctlp.isPunctuationTag(goldPT.label().value()) ||
          ctlp.isPunctuationWord(goldPT.firstChild().label().value())) {
        // System.out.println("Deleting punctuation");
        return null;
      }
    }

    if (guess.isLeaf()) {
      return tf.newLeaf(new StringLabel(label));
    }

    List<Tree> children = new ArrayList<>();

    if (label.matches("ROOT.*") && guess.numChildren() == 1) { // keep non-unary roots for now
      return transformTree(guess.children()[0], goldPreterminals, true);
    }

    //System.out.println("Enhanced label is " + label);

    // remove all functional and machine-generated annotations
    label = label.replaceFirst("[^A-Z].*$", "");
    // merge parentheticals with adverb phrases
    label = label.replaceFirst("PRN", "ADVP");

    //System.out.println("New label is " + label);

    for (int cNum = 0; cNum < guess.children().length; cNum++) {
      Tree child = guess.children()[cNum];
      Tree newChild = transformTree(child, goldPreterminals, false);
      if (newChild != null) {
        children.add(newChild);
      }
    }
    // We don't delete the root because there are trees in the
    // Chinese treebank that only have punctuation in them!!!
    if (children.isEmpty() && ! isRoot) {
      if (VERBOSE) {
        log.info("ChineseCollinizer: all children of " + label +
                           " deleted; returning null");
      }
      return null;
    }
    return tf.newTreeNode(new StringLabel(label), children);
  }

}
