package edu.stanford.nlp.ie.machinereading.common;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.ModCollinsHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeVisitor;
import edu.stanford.nlp.trees.Treebank;

/**
 * Simple variant of the ModCollinsHeadFinder avoids supplying punctuation tags
 * as heads whenever possible.
 * 
 * @author David McClosky (mcclosky@stanford.edu)
 * 
 */
public class NoPunctuationHeadFinder extends ModCollinsHeadFinder {

  private static final long serialVersionUID = 1201891305937180385L;

  /**
   * Returns whether a part of speech tag is the tag for a punctuation mark (by
   * checking whether the first character is a letter.
   * 
   * @param label
   *          part of speech tag
   * @return whether the tag is (typically) assigned to punctuation
   */
  private boolean isPunctuationLabel(String label) {
    return !Character.isLetter(label.charAt(0))
        && !(label.equals("$") || label.equals("%"));
  }

  @Override
  protected int postOperationFix(int headIdx, Tree[] daughterTrees) {
    int index = super.postOperationFix(headIdx, daughterTrees);
    // if the current index is a punctuation mark, we search left until we
    // find a non-punctuation mark tag or hit the left end of the sentence
    while (index > 0) {
      String label = daughterTrees[index].label().value();
      if (isPunctuationLabel(label)) {
        index--;
      } else {
        break;
      }
    }

    return index;
  }

  public static void main(String[] args) {
    // simple testing code
    Treebank treebank = new DiskTreebank();
    CategoryWordTag.suppressTerminalDetails = true;
    treebank.loadPath(args[0]);
    final HeadFinder chf = new NoPunctuationHeadFinder();
    treebank.apply(pt -> {
      pt.percolateHeads(chf);
      pt.pennPrint();
      System.out.println();
    });
  }

}
