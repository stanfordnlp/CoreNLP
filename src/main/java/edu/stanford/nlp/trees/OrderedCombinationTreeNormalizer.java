package edu.stanford.nlp.trees;

import java.util.ArrayList;
import java.util.List;

/**
 * This class combines multiple tree normalizers.  Given a list of tree normalizer,
 * it applies each tree normalizer from the first to the last for each of the normalize
 * nonterminal, normalize terminal, and normalize whole tree methods.
 * 
 * 
 * @author Anna Rafferty
 *
 */
public class OrderedCombinationTreeNormalizer extends TreeNormalizer {
  private static final long serialVersionUID = 326L;
  
  private List<TreeNormalizer> tns = new ArrayList<>();
  
  public OrderedCombinationTreeNormalizer() {
  }
  
  
  public OrderedCombinationTreeNormalizer(List<TreeNormalizer> tns) {
    this.tns = tns;
  }
  
  /**
   * Adds the given tree normalizer to this combination; the tree normalizers
   * are applied in the order they were added, with the first to be added being
   * the first to be applied.
   */
  public void addTreeNormalizer(TreeNormalizer tn) {
    this.tns.add(tn);
  }
  
  
  @Override
  public String normalizeNonterminal(String category) {
    for(TreeNormalizer tn : tns) {
      category = tn.normalizeNonterminal(category);
    }
    return category;
  }
  
  @Override
  public String normalizeTerminal(String leaf) {
    for(TreeNormalizer tn : tns) {
      leaf = tn.normalizeTerminal(leaf);
    }
    return leaf;
  }
  
  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    for(TreeNormalizer tn : tns) {
      tree = tn.normalizeWholeTree(tree, tf);
    }
    return tree;
    
  }
}
