package edu.stanford.nlp.trees.international.hebrew;

import java.io.Serializable;
import java.util.Collections;

import edu.stanford.nlp.trees.BobChrisTreeNormalizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import java.util.function.Predicate;

/**
 * 
 * @author Spence Green
 *
 */
public class HebrewTreeNormalizer extends BobChrisTreeNormalizer {

  private static final long serialVersionUID = -3129547164200725933L;

  private final Predicate<Tree> hebrewEmptyFilter;

  public HebrewTreeNormalizer() {
    super(new HebrewTreebankLanguagePack());
    hebrewEmptyFilter = new HebrewEmptyFilter();
  }

  /**
   * Remove traces and pronoun deletion markers.
   */
  public static class HebrewEmptyFilter implements Predicate<Tree>, Serializable {

    private static final long serialVersionUID = -7256461296718287280L;

    public boolean test(Tree t) {
      return ! (t.isPreTerminal() && t.value().equals("-NONE-"));
    }
  }

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    tree = tree.prune(hebrewEmptyFilter, tf).spliceOut(aOverAFilter, tf);

    //Add start symbol so that the root has only one sub-state. Escape any enclosing brackets.
    //If the "tree" consists entirely of enclosing brackets e.g. ((())) then this method
    //will return null. In this case, readers e.g. PennTreeReader will try to read the next tree.
    while(tree != null && (tree.value() == null || tree.value().equals("")) && tree.numChildren() <= 1)
      tree = tree.firstChild();

    if(tree != null && !tree.value().equals(tlp.startSymbol()))
      tree = tf.newTreeNode(tlp.startSymbol(), Collections.singletonList(tree));

    return tree;
  }

}
