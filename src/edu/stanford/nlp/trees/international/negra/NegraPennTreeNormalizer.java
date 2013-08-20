package edu.stanford.nlp.trees.international.negra;

import java.util.*;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Filter;

/**
 * Tree normalizer for Negra Penn Treebank format.
 *
 * @author Roger Levy
 */
public class NegraPennTreeNormalizer extends TreeNormalizer {
  /** How to clean up node labels: 0 = do nothing, 1 = keep category and
   *  function, 2 = just category
   */
  private final int nodeCleanup;
  private static final String nonUnaryRoot = "NUR"; // non-unary root
  protected final TreebankLanguagePack tlp;
  private boolean insertNPinPP = false;

  private final Filter<Tree> emptyFilter;
  private final Filter<Tree> aOverAFilter;

  public NegraPennTreeNormalizer() {
    this(new NegraPennLanguagePack());
  }

  public NegraPennTreeNormalizer(TreebankLanguagePack tlp) {
    this(tlp, 0);
  }

  public NegraPennTreeNormalizer(TreebankLanguagePack tlp, int nodeCleanup) {
    this.tlp = tlp;
    this.nodeCleanup = nodeCleanup;

    emptyFilter = new Filter<Tree>() {
      private static final long serialVersionUID = -606371737889816130L;
      public boolean accept(Tree t) {
        Tree[] kids = t.children();
        Label l = t.label();
        if ((l != null) && l.value() != null && (l.value().matches("^\\*T.*$")) && !t.isLeaf() && kids.length == 1 && kids[0].isLeaf())
          return false;
        return true;
      }
    };
    aOverAFilter = new Filter<Tree>() {
      private static final long serialVersionUID = -606371737889816130L;
      public boolean accept(Tree t) {
        if (t.isLeaf() || t.isPreTerminal() || t.children().length != 1)
          return true;
        if (t.label() != null && t.label().equals(t.children()[0].label()))
          return false;
        return true;
      }
    };
  }


  public String rootSymbol() {
    return tlp.startSymbol();
  }

  public String nonUnaryRootSymbol() {
    return nonUnaryRoot;
  }

  public void setInsertNPinPP(boolean b) {
    insertNPinPP = b;
  }

  public boolean getInsertNPinPP() {
    return insertNPinPP;
  }

  /**
   * Normalizes a leaf contents.
   * This implementation interns the leaf.
   */
  @Override
  public String normalizeTerminal(String leaf) {
    return leaf.intern();
  }

  private static final String junkCPP = "---CJ";
  private static final String cpp = "CPP";

  /**
   * Normalizes a nonterminal contents.
   * This implementation strips functional tags, etc. and interns the
   * nonterminal.
   */
  @Override
  public String normalizeNonterminal(String category) {
    if (junkCPP.equals(category)) // one garbage category cleanup here.
      category = cpp;

    //Accommodate the null root nodes in Negra/Tiger trees
    category = cleanUpLabel(category);

    return (category == null) ? null : category.intern();
  }

  private Tree fixNonUnaryRoot(Tree t, TreeFactory tf) {
    List<Tree> kids = t.getChildrenAsList();
    if(kids.size() == 2 && t.firstChild().isPhrasal() && tlp.isSentenceFinalPunctuationTag(t.lastChild().value())) {
      List<Tree> grandKids = t.firstChild().getChildrenAsList();
      grandKids.add(t.lastChild());
      t.firstChild().setChildren(grandKids);
      kids.remove(kids.size() - 1);
      t.setChildren(kids);
      t.setValue(tlp.startSymbol());
      
    } else {
      t.setValue(nonUnaryRoot);
      t = tf.newTreeNode(tlp.startSymbol(), Collections.singletonList(t));
    }
    return t;
  }


  /**
   * Normalize a whole tree -- one can assume that this is the
   * root.  This implementation deletes empty elements (ones with
   * nonterminal tag label starting with '*T') from the tree.  It
   * does work for a null tree.
   */
  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf) {
    // add an extra root to non-unary roots
    if(tree.value() == null)
      tree = fixNonUnaryRoot(tree, tf);
    else if(!tree.value().equals(tlp.startSymbol()))
      tree = tf.newTreeNode(tlp.startSymbol(), Collections.singletonList(tree));

    tree = tree.prune(emptyFilter, tf).spliceOut(aOverAFilter, tf);

    // insert NPs in PPs if you're supposed to do that
    if (insertNPinPP) {
      insertNPinPPall(tree);
    }

    for(Tree t : tree) {
      if(t.isLeaf() || t.isPreTerminal()) continue;
      if(t.value() == null || t.value().equals("")) t.setValue("DUMMY");

      // there's also a '--' category
      if(t.value().matches("--.*")) continue;

      // fix a bug in the ACL08 German tiger treebank
      String cat = t.value();
      if(cat == null || cat.equals("")) {
        if (t.numChildren() == 3 && t.firstChild().label().value().equals("NN") && t.getChild(1).label().value().equals("$.")) {
          System.err.println("Correcting treebank error: giving phrase label DL to " + t);
          t.label().setValue("DL");
        }
      }
    }

    return tree;
  }


  private Set<String> prepositionTags = new HashSet<String>(Arrays.asList(new String[]{"APPR", "APPRART"}));
  private Set<String> postpositionTags = new HashSet<String>(Arrays.asList(new String[]{"APPO", "APZR"}));


  private void insertNPinPPall(Tree t) {
    Tree[] kids = t.children();
    for (int i = 0, n = kids.length; i < n; i++) {
      insertNPinPPall(kids[i]);
    }
    insertNPinPP(t);
  }


  private void insertNPinPP(Tree t) {
    if (tlp.basicCategory(t.label().value()).equals("PP")) {
      Tree[] kids = t.children();
      int i = 0;
      int j = kids.length - 1;
      while (i < j && prepositionTags.contains(tlp.basicCategory(kids[i].label().value()))) {
        i++;
      } // i now indexes first dtr of new NP
      while (i < j && postpositionTags.contains(tlp.basicCategory(kids[j].label().value()))) {
        j--;
      } // j now indexes last dtr of new NP

      if (i > j) {
        System.err.println("##### Warning -- no NP material here!");
        return; // there is no NP material!
      }

      int npKidsLength = j - i + 1;
      Tree[] npKids = new Tree[npKidsLength];
      System.arraycopy(kids, i, npKids, 0, npKidsLength);
      Tree np = t.treeFactory().newTreeNode(t.label().labelFactory().newLabel("NP"), Arrays.asList(npKids));
      Tree[] newPPkids = new Tree[kids.length - npKidsLength + 1];
      System.arraycopy(kids, 0, newPPkids, 0, i + 1);
      newPPkids[i] = np;
      System.arraycopy(kids, j + 1, newPPkids, i + 1, kids.length - j - 1);
      t.setChildren(newPPkids);
      System.out.println("#### inserted NP in PP");
      t.pennPrint();
    }
  }


  /**
   * Remove things like hyphened functional tags and equals from the
   * end of a node label.
   */
  protected String cleanUpLabel(String label) {
    if (nodeCleanup == 1) {
      return tlp.categoryAndFunction(label);
    } else if (nodeCleanup == 2) {
      return tlp.basicCategory(label);
    } 
    return label;
  }

  private static final long serialVersionUID = 8529514903815041064L;
}
