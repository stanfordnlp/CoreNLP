package edu.stanford.nlp.trees;

import edu.stanford.nlp.util.Filter;

/**
 * A Tree Normalizer to prune nodes according to filter passed in constructor
 * and simultaneously strip subtags
 *
 * @author Dan Klein
 */
public class PruneNodesStripSubtagsTreeNormalizer extends TreeNormalizer {

  /**
   * 
   */
  private static final long serialVersionUID = -4140942599350599366L;

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
   * Normalize a whole tree -- one can assume that this is the root.
   * This implementation deletes empty elements (ones with nonterminal
   * labels passing the filter) from the tree
   */

  private Filter<Tree> nodeFilter;

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    return tree.prune(nodeFilter, tf);
  }


  /**
   * Remove things like hyphened functional tags and equals from the
   * end of a node label
   */
  private static String cleanUpLabel(String label) {
    if (label == null) {
      label = "ROOT";
      // String constants are always interned
    } else {
      // a '-' at the beginning of label is okay (punctuation tag!)
      int k = label.indexOf('-');
      if (k > 0) {
        label = label.substring(0, k);
      }
      k = label.indexOf('=');
      if (k > 0) {
        label = label.substring(0, k);
      }
      k = label.indexOf('|');
      if (k > 0) {
        label = label.substring(0, k);
      }
    }
    return label;
  }

  /**
   * @param filter the filter to prune nodes with
   */
  public PruneNodesStripSubtagsTreeNormalizer(Filter<Tree> filter) {
    nodeFilter = filter;
  }

}
