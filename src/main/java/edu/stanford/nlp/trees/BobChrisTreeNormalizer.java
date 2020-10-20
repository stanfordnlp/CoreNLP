package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

import java.io.Serializable;
import java.util.function.Predicate;


/**
 * Normalizes trees in the way used in Manning and Carpenter 1997.
 * NB: This implementation is still incomplete!
 * The normalizations performed are: (i) terminals are interned, (ii)
 * nonterminals are stripped of alternants, functional tags and
 * cross-reference codes, and then interned, (iii) empty
 * elements (ones with nonterminal label "-NONE-") are deleted from the
 * tree, (iv) the null label at the root node is replaced with the label
 * "ROOT". <br>
 * 17 Apr 2001: This was fixed to work with different kinds of labels,
 * by making proper use of the Label interface, after it was moved into
 * the trees module.
 * <p>
 * The normalizations of the original (Prolog) BobChrisNormalize were:
 * 1. Remap the root node to be called 'ROOT'
 * 2. Truncate all nonterminal labels before characters introducing
 * annotations according to TreebankLanguagePack
 * (traditionally, -, =, | or # (last for BLLIP))
 * 3. Remap the representation of certain leaf symbols (brackets etc.)
 * 4. Map to lowercase all leaf nodes
 * 5. Delete empty/trace nodes (ones marked '-NONE-')
 * 6. Recursively delete any nodes that do not dominate any words
 * 7. Delete A over A nodes where the top A dominates nothing else
 * 8. Remove backslashes from lexical items
 * (the Treebank inserts them to escape slashes (/) and stars (*)).
 * 4 is deliberately omitted, and a few things are purely aesthetic.
 * <p>
 * 14 June 2002: It now deletes unary A over A if both nodes' labels are equal
 * (7), and (6) was always part of the Tree.prune() functionality...
 * 30 June 2005: Also splice out an EDITED node, just in case you're parsing
 * the Brown corpus.
 *
 * @author Christopher Manning
 */
public class BobChrisTreeNormalizer extends TreeNormalizer implements TreeTransformer {

  protected final TreebankLanguagePack tlp;


  public BobChrisTreeNormalizer() {
    this(new PennTreebankLanguagePack());
  }

  public BobChrisTreeNormalizer(TreebankLanguagePack tlp) {
    this.tlp = tlp;
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
   * end of a node label.  This version always just returns the phrase
   * structure category, or "ROOT" if the label was {@code null}.
   *
   * @param label The label from the treebank
   * @return The cleaned up label (phrase structure category)
   */
  protected String cleanUpLabel(final String label) {
    if (label == null || label.isEmpty()) {
      return "ROOT";
      // String constants are always interned
    } else {
      return tlp.basicCategory(label);
    }
  }


  /**
   * Normalize a whole tree -- one can assume that this is the
   * root.  This implementation deletes empty elements (ones with
   * nonterminal tag label '-NONE-') from the tree, and splices out
   * unary A over A nodes.  It assumes that it is not given a
   * null tree, but it may return one if there are no real words.
   */
  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    Tree middle = tree.prune(emptyFilter, tf);
    if (middle == null) {
      return null;
    } else {
      return middle.spliceOut(aOverAFilter, tf);
    }
  }

  @Override
  public Tree transformTree(Tree tree) {
    return normalizeWholeTree(tree, tree.treeFactory());
  }


  @SuppressWarnings("serial")
  protected Predicate<Tree> emptyFilter = new EmptyFilter();

  @SuppressWarnings("serial")
  protected Predicate<Tree> aOverAFilter = new AOverAFilter();

  private static final long serialVersionUID = -1005188028979810143L;


  public static class EmptyFilter implements Predicate<Tree>, Serializable {

    private static final long serialVersionUID = 8914098359495987617L;

    /** Doesn't accept nodes that only cover an empty. */
    @Override
    public boolean test(Tree t) {
      Tree[] kids = t.children();
      Label l = t.label();
      // Delete (return false for) empty/trace nodes (ones marked '-NONE-')
      return ! ((l != null) && "-NONE-".equals(l.value()) && !t.isLeaf() && kids.length == 1 && kids[0].isLeaf());
    }

  } // end class EmptyFilter


  public static class AOverAFilter implements Predicate<Tree>, Serializable {

    /** Doesn't accept nodes that are A over A nodes (perhaps due to
     *  empty removal or are EDITED nodes).
     */
    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean test(Tree t) {
      if (t.isLeaf() || t.isPreTerminal()) {
        return true;
      }
      // The special switchboard non-terminals clause
      if ("EDITED".equals(t.label().value()) || "CODE".equals(t.label().value())) {
        return false;
      }
      if (t.numChildren() != 1) {
        return true;
      }
      return ! (t.label() != null && t.label().value() != null && t.label().value().equals(t.getChild(0).label().value()));
    }

    private static final long serialVersionUID = 1L;

  } // end static class AOverAFilter

}
