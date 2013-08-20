package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Category;
import edu.stanford.nlp.ling.Word;

import java.util.Iterator;
import java.util.Set;

/**
 * Normalizes trees roughly the way used in Manning and Carpenter 1997.
 * NB: This implementation is still incomplete!
 * The normalizations performed are: (i) terminals are interned, (ii)
 * nonterminals are stripped of alternants, functional tags and
 * cross-reference codes (on |, =, -) and then interned, (iii) empty
 * elements (ones with nonterminal label "-NONE-") are deleted from the
 * tree, (iv) the null label at the root node is replaced with the label
 * "ROOT".
 * <p/>
 * Dan's mod: -NONE- are all made into Word.EMPTY but left in.
 * <p/>
 * <i>Implementation note: This class should probably be removed and
 * made a constructor option of BobChrisTreeNormalizer, which should
 * in turn be folded into the ParametricTreeNormalizer.
 *
 * @author Dan Klein
 */
public class DanBobChrisTreeNormalizer extends TreeNormalizer {

  /**
   * 
   */
  private static final long serialVersionUID = 2401719826865123880L;

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
   * Dan's mod: -NONE- are all made into Word.EMPTY but left in.
   * (Chris: rather than the way this is done, I wonder if using
   * the transform() method of the Tree class wouldn't be much more
   * efficient.)
   * This tries to be robust to different Tree implementations in
   * detecting
   */
  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    if (tree == null) {
      return null;
    }
    //System.out.println(tree.label());
    Set subTrees = tree.subTrees();
    for (Iterator iter = subTrees.iterator(); iter.hasNext();) {
      Tree t = (Tree) iter.next();
      Tree[] kids = t.children();
      if ((t.label() != null) && (t.label().value().equals(Category.EMPTYSTRING)) && !t.isLeaf() && kids.length == 1 && kids[0].isLeaf()) {
        // Normalize empty/trace nodes (ones marked '-NONE-')
        //kids[0].setLabel(tf.newLeaf(Word.EMPTY.toString()).label());
        kids[0].setLabel(tf.newLeaf(Word.EMPTY).label());
      }
    }
    return tree;
  }


  /**
   * Remove things like hyphened functional tags and equals from the
   * end of a node label
   */
  private static String cleanUpLabel(String label) {
    if (label == null) {
      label = "ROOT";
      // String constants are always interned (Dan: not necessarily!)
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

  public DanBobChrisTreeNormalizer() {
    super();
  }

}
