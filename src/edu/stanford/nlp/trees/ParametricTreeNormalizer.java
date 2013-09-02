package edu.stanford.nlp.trees;

import edu.stanford.nlp.util.Filter;

/**
 * Normalizes trees based on parameter settings.  This system can
 * do a variety of methods of tree normalizations based on parameter
 * settings..
 * The normalizations performed are: (i) terminals are interned, (ii)
 * nonterminals are stripped of alternants, functional tags and
 * cross-reference codes (on |, =, -) and then interned, (iii) empty
 * elements (ones with nonterminal label "-NONE-") are deleted from the
 * tree, (iv) the null label at the root node is replaced with the label
 * "ROOT". <br>
 * 17 Apr 2001: This was fixed to work with different kinds of labels,
 * by making proper use of the Label interface, after it was moved into
 * the trees module. <p>
 * <b>This was never finished.  You shouldn't use this.  The options of
 * NPTmpRetainingTreeNormalizer should really be put here.</b>
 *
 * @author Christopher Manning
 */
public class ParametricTreeNormalizer extends TreeNormalizer {

  /**
   * 
   */
  private static final long serialVersionUID = 3334729561543903805L;

  /**
   * If this variable is true, all strings will be interned
   */
  final private boolean internStrings;

  /**
   * If this is true, the backslash (\) in front of slashes and stars
   * will be unquoted.
   */
  final private boolean unquoteStrings;


  public ParametricTreeNormalizer(boolean internStrings, boolean unquoteStrings) {
    this.internStrings = internStrings;
    this.unquoteStrings = unquoteStrings;
  }


  private String unquoteString(String word) {
    char[] chars = new char[word.length()];
    char[] newChars = new char[word.length()];
    int upto = 0;
    word.getChars(0, word.length(), chars, 0);
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == '\\' && (i + 1 < chars.length) && (chars[i + 1] == '*' || chars[i + 1] == '/')) {
        newChars[upto++] = chars[i + 1];
        i++;
      } else {
        newChars[upto++] = chars[i];
      }
    }
    return new String(newChars, 0, upto + 1);
  }


  /**
   * Normalizes a leaf contents.  This may do nothing or may do
   * string interning depending on the setting of
   * This implementation interns the leaf.
   */
  @Override
  public String normalizeTerminal(String leaf) {
    if (unquoteStrings) {
      leaf = unquoteString(leaf);
    }
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
   * tag label '-NONE-') from the tree
   */
  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    return tree.prune(new Filter<Tree>() {
      /**
       * 
       */
      private static final long serialVersionUID = -5701825765995956678L;

      public boolean accept(Tree t) {
        Tree[] kids = t.children();
        if ((t.label() != null) && (t.label().value().equals("-NONE-")) && !t.isLeaf() && kids.length == 1 && kids[0].isLeaf()) {
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
