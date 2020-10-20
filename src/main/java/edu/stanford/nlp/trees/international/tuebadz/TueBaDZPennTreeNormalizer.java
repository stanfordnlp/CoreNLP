package edu.stanford.nlp.trees.international.tuebadz;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreebankLanguagePack;


/**
 * Tree normalizer for the TueBaDZ treebank.
 *
 * (An adaptation of Roger Levy's NegraPennTreeNormalizer.)
 *
 * @author Wolfgang Maier (wmaier@sfs.uni-tuebingen.de)
 */
public class TueBaDZPennTreeNormalizer extends TreeNormalizer {

  /** How to clean up node labels: 0 = do nothing, 1 = keep category and
   *  function, 2 = just category.
   */
  private final int nodeCleanup;
  private final String root;
  protected final TreebankLanguagePack tlp;
  private List<TreeNormalizer> tns = new ArrayList<>();

  public String rootSymbol() {
    return root;
  }

//  public TueBaDZPennTreeNormalizer() {
//    this(new TueBaDZLanguagePack(), 0);
//  }

  public TueBaDZPennTreeNormalizer(TreebankLanguagePack tlp, int nodeCleanup) {
    this.tlp = tlp;
    this.nodeCleanup = nodeCleanup;
    root = tlp.startSymbol();
  }
  
  public TueBaDZPennTreeNormalizer(TreebankLanguagePack tlp, int nodeCleanup, List<TreeNormalizer> tns) {
    this.tlp = tlp;
    this.nodeCleanup = nodeCleanup;
    root = tlp.startSymbol();
    this.tns.addAll(tns);
  }

  /**
   * Normalizes a leaf contents.
   * This implementation interns the leaf.
   */
  @Override
  public String normalizeTerminal(String leaf) {
    // We could unquote * and / with backslash \ in front of them
    return leaf.intern();
  }


  /**
   * Normalizes a nonterminal contents.
   * This implementation strips functional tags, etc. and interns the
   * nonterminal.
   */
  @Override
  public String normalizeNonterminal(String category) {
    return cleanUpLabel(category).intern();
  }

  /**
   * Remove things like hyphened functional tags and equals from the
   * end of a node label.
   */
  protected String cleanUpLabel(String label) {
    if (label == null) {
      return root;
    } else if (nodeCleanup == 1) {
      return tlp.categoryAndFunction(label);
    } else if (nodeCleanup == 2) {
      return tlp.basicCategory(label);
    } else {
      return label;
    }
  }


  /**
   * Normalize a whole tree.
   * TueBa-D/Z adaptation. Fixes trees with non-unary roots, does nothing else.
   */
  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    if (tree.label().value().equals(root) && tree.children().length > 1) {
      Tree underRoot = tree.treeFactory().newTreeNode(root, tree.getChildrenAsList());
      tree.setChildren(new Tree[1]);
      tree.setChild(0, underRoot);

    }
    // we just want the non-unary root fixed.
    return tree;
  }

  private static final long serialVersionUID = 8009544230321390490L;

}
