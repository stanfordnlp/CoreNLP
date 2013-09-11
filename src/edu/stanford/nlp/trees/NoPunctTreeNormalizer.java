package edu.stanford.nlp.trees;

import edu.stanford.nlp.util.Filter;

/**
 * Normalizes trees roughly the way used in Manning and Carpenter 1997.
 * NB: This implementation is still incomplete!
 * The normalizations performed are: (i) terminals are interned, (ii)
 * nonterminals are stripped of alternants, functional tags and
 * cross-reference codes (on |, =, -) and then interned, (iii) empty
 * elements (ones with nonterminal label "-NONE-") are deleted from the
 * tree, (iv) the null label at the root node is replaced with the label
 * "ROOT". <br>
 * 17 Apr 2001: This was fixed to work with different kinds of labels,
 * by making proper use of the Label interface, after it was moved into
 * the trees module.
 * <p/>
 * <i>Implementation note:</i> This should really be rewritten now to make
 * use of TreebankLanguagePack functionality.
 *
 * @author Christopher Manning
 */
public class NoPunctTreeNormalizer extends TreeNormalizer {

  /**
   * 
   */
  private static final long serialVersionUID = -6097556118989005285L;
  TreebankLanguagePack tlp = new PennTreebankLanguagePack();

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
   * tag label '-NONE-') from the tree, and punctuation.
   */
  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    return tree.prune(new Filter<Tree>() {
      /**
       * 
       */
      private static final long serialVersionUID = 722395824663294192L;

      public boolean accept(Tree t) {
        Tree[] kids = t.children();
        if ((t.label() != null) && !t.isLeaf() && kids.length == 1 && kids[0].isLeaf() && t.label().value() != null && (t.label().value().equals("-NONE-") || // At one point the below were included too, but dubious
                // t.label().value().equals("$") ||
                // t.label().value().equals("LS")
                tlp.isPunctuationTag(t.label().value()))) {
          // Delete empty/trace nodes (ones marked '-NONE-')
          return false;
        }
        return true;
      }
    }, tf);
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

}
