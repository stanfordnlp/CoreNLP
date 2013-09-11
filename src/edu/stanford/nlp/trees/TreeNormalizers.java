package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.Filter;

/**
 * A collection of static methods that return a TreeNormalizer.
 *
 * @author Dan Klein
 */
public class TreeNormalizers {

  private TreeNormalizers() {
    // prevent instantiation of class
  }


  public static TreeNormalizer internOnly() {
    return new InternOnlyTreeNormalizer();
  }


  public static TreeNormalizer parentTransformedBobChris() {
    return new InternOnlyTreeNormalizer() {
      /**
       * 
       */
      private static final long serialVersionUID = -3725320400344773969L;

      /**
       * Normalize a whole tree -- one can assume that this is the
       * root.  This implementation deletes empty elements (ones
       * with nonterminal tag label '-NONE-') from the tree.  It
       * does work for a null tree.
       */
      @Override
      public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
        Tree t = basicBobChris(tree, tf);
        johnsonParentTransform(t);
        return t;
      }
    };
  }


  public static TreeNormalizer prepTransformedBobChris() {
    return new InternOnlyTreeNormalizer() {
      /**
       * 
       */
      private static final long serialVersionUID = -897357543967405488L;

      /**
       * Normalize a whole tree -- one can assume that this is the
       * root.  This implementation deletes empty elements (ones
       * with nonterminal tag label '-NONE-') from the tree.  It
       * does work for a null tree.
       */
      @Override
      public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
        Tree t = basicBobChris(tree, tf);
        return t.transform(new SubordConjTreeTransformer(), tf);
      }
    };
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


  final static Word emptyWord = new Word("");

  private static void johnsonParentTransform(Tree tree) {
    johnsonParentTransformHelper(tree, emptyWord);
  }


  private static void johnsonParentTransformHelper(Tree tree, Label parentLabel) {
    if (tree == null || tree.isLeaf()) {
      return;
    }
    Label label = tree.label();
    StringBuffer newLabelSB = new StringBuffer();
    newLabelSB.append(label.toString());
    newLabelSB.append("^");
    newLabelSB.append(parentLabel.toString());
    tree.setLabel(new Word(newLabelSB.toString()));
    Tree[] kids = tree.children();
    for (int i = 0; i < kids.length; i++) {
      Tree child = kids[i];
      johnsonParentTransformHelper(child, label);
    }
  }


  /**
   * This prunes out nodes with empty children
   */
  private static Tree basicBobChris(Tree tree, TreeFactory tf) {
    return tree.prune(new Filter<Tree>() {
      /**
       * 
       */
      private static final long serialVersionUID = 5879323112204306123L;

      public boolean accept(Tree t) {
        Tree[] kids = t.children();
        Label l = t.label();  // XXX
        if ((t.label() != null) && t.label().value() != null && (t.label().value().equals("-NONE-")) && !t.isLeaf() && kids.length == 1 && kids[0].isLeaf()) {
          // Delete empty/trace nodes
          // (ones marked '-NONE-')
          return false;
        }
        return true;
      }
    }, tf);
  }


  public static TreeNormalizer prpTransformedBobChris() {
    return new InternOnlyTreeNormalizer() {

      /**
       * 
       */
      private static final long serialVersionUID = 2588862077007333833L;

      /**
       * Normalize a whole tree -- one can assume that this is the
       * root.  This implementation deletes empty elements (ones
       * with nonterminal tag label '-NONE-') from the tree.  It
       * does work for a null tree.
       */
      @Override
      public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
        Tree t = basicBobChris(tree, tf);
        return t.transform(new PRPNPTreeTransformer(), tf);
      }
    };
  }


  public static TreeNormalizer ctrlSubjTransformedBobChris() {
    return new InternOnlyTreeNormalizer() {

      /**
       * 
       */
      private static final long serialVersionUID = -7845851485702977942L;

      /**
       * Normalize a whole tree -- one can assume that this is the
       * root.  This implementation deletes empty elements (ones
       * with nonterminal tag label '-NONE-') from the tree.  It
       * does work for a null tree.
       */
      @Override
      public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
        Tree t = basicBobChris(tree, tf);
        return t.transform(new SctrlTreeTransformer(), tf);
      }
    };
  }


  public static TreeNormalizer verbAuxTransformedBobChris() {
    return new InternOnlyTreeNormalizer() {

      /**
       * 
       */
      private static final long serialVersionUID = 2266361077454307702L;

      /**
       * Normalize a whole tree -- one can assume that this is the
       * root.  This implementation deletes empty elements (ones
       * with nonterminal tag label '-NONE-') from the tree.  It
       * does work for a null tree.
       */
      @Override
      public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
        Tree t = basicBobChris(tree, tf);
        return t.transform(new VerbAuxTreeTransformer(), tf);
      }
    };
  }


  public static TreeNormalizer kitchenSinkTransformedBobChris() {
    return new InternOnlyTreeNormalizer() {

      /**
       * 
       */
      private static final long serialVersionUID = -8876855643326243254L;

      /**
       * Normalize a whole tree -- one can assume that this is the
       * root.  This implementation deletes empty elements (ones
       * with nonterminal tag label '-NONE-') from the tree.  It
       * does work for a null tree.
       */
      @Override
      public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
        Tree t = basicBobChris(tree, tf);
        t = t.transform(new SubordConjTreeTransformer(), tf);
        t = t.transform(new PRPNPTreeTransformer(), tf);
        t = t.transform(new VerbAuxTreeTransformer(), tf);
        return t.transform(new SctrlTreeTransformer(), tf);
      }
    };
  }


  /**
   * This class interns category and terminal labels, and strips
   * functional tags, but does nothing else
   */
  private static class InternOnlyTreeNormalizer extends TreeNormalizer {

    /**
     * 
     */
    private static final long serialVersionUID = -3998671138888101282L;


    /**
     * Normalizes a leaf contents.
     * This implementation interns the leaf.
     */
    @Override
    public String normalizeTerminal(String leaf) {
      // We could unquote * and / with backslash \ in front of
      // them
      return leaf.intern();
    }

    /**
     * Normalizes a nonterminal contents.
     * This implementation strips functional tags, etc. and
     * interns the
     * nonterminal.
     */
    @Override
    public String normalizeNonterminal(String category) {
      return cleanUpLabel(category).intern();
    }


    /**
     * Normalize a whole tree -- a no-op
     */
    @Override
    public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
      return tree;
    }
  }


  private static class SubordConjTreeTransformer implements TreeTransformer {

    public Tree transformTree(Tree t) {
      if (t.isLeaf()) {
        return t;
      }
      Tree[] kids = t.children();
      if (t.label().value().equals("IN") && kids.length == 1) {
        String lab = kids[0].label().value().toLowerCase();
        if (lab.equals("of")) {
          t.label().setValue("OF");
        } else if (lab.equals("as")) {
          t.label().setValue("AS");
        } else if (lab.equals("that") || lab.equals("if") || lab.equals("because") || lab.equals("while") || lab.equals("although") || lab.equals("whether") || lab.equals("though") || lab.equals("unless") || lab.equals("even")) {
          t.label().setValue("INCS");
        }
      }
      return t;
    }
  }


  private static class VerbAuxTreeTransformer implements TreeTransformer {

    private static HeadFinder hf = new CollinsHeadFinder();

    public Tree transformTree(Tree t) {
      if (t.isLeaf()) {
        return t;
      }
      String lab = t.label().value();
      Tree head = hf.determineHead(t);
      if (head == null) {
        return t;
      }
      String dlab = head.label().value().toLowerCase();

      if (lab.equals("VP")) {
        if (dlab.equals("to")) {  // head dtr has been lowercased!!
          t.label().setValue("VP-INF");
        }
      } else if (lab.equals("VBZ")) {
        if (dlab.equals("is") || dlab.equals("'s")) {
          t.label().setValue("VBZ-BE");
        }
      } else if (lab.equals("VBD")) {
        if (dlab.equals("was") || dlab.equals("were")) {
          t.label().setValue("VBD-BE");
        }
      } else if (lab.equals("VBP")) {
        if (dlab.equals("am") || dlab.equals("'m") || dlab.equals("are") || dlab.equals("'re")) {
          t.label().setValue("VBP-BE");
        }
      } else if (lab.equals("VB")) {
        if (dlab.equals("be") || dlab.equals("were")) {
          t.label().setValue("VBD-BE");
        }
      } else if (lab.equals("VBN")) {
        if (dlab.equals("been")) {
          t.label().setValue("VBN-BE");
        }
      } else if (lab.equals("VBG")) {
        if (dlab.equals("being")) {
          t.label().setValue("VBG-BE");
        }
      }
      return t;
    }
  }


  private static class PRPNPTreeTransformer implements TreeTransformer {

    public Tree transformTree(Tree t) {
      if (t.isLeaf()) {
        return t;
      }
      Tree[] kids = t.children();
      if (t.label().value().equals("NP") && kids.length == 1) {
        String lab = kids[0].label().value();
        if (lab.equals("PRP")) {
          t.label().setValue("NPprp");
        }
      }
      return t;
    }
  }

  private static class SctrlTreeTransformer implements TreeTransformer {

    public Tree transformTree(Tree t) {
      if (t.isLeaf()) {
        return t;
      }
      Tree[] kids = t.children();
      if (t.label().value().equals("S") && kids.length == 1) {
        String lab = kids[0].label().value();
        if (lab.equals("VP")) {
          t.label().setValue("Sctrl");
        }
      }
      return t;
    }
  }

}
