package edu.stanford.nlp.trees.international.pennchinese;

import java.util.HashMap;

import edu.stanford.nlp.trees.*;

/**
 * HeadFinder for the Penn Chinese Treebank.  Adapted from
 * CollinsHeadFinder.
 *
 * @author Roger Levy
 */
public class OldChineseHeadFinder implements HeadFinder {

  /**
   *
   */
  private static final long serialVersionUID = 6397738771545467067L;

  private static final boolean DEBUG = false;

  /**
   * If true, reverses the direction of search in VP and IP coordinations.
   * Works terribly .
   */
  private static final boolean coordSwitch = false;

  private final TreebankLanguagePack tlp;

  public OldChineseHeadFinder() {
    this(new ChineseTreebankLanguagePack());
  }

  public OldChineseHeadFinder(TreebankLanguagePack tlp) {
    this.tlp = tlp;
  }


  /**
   * Determine which daughter of the current parse tree is the head.
   * It assumes that the daughters already have had their heads
   * determined. Another method has to do the tree walking.
   *
   * @param t The parse tree to examine the daughters of
   * @return The parse tree that is the head
   */
  public Tree determineHead(Tree t) {
    if (t.isLeaf()) {
      return null;
    }
    Tree[] kids = t.children();
    String motherCat = tlp.basicCategory(t.label().value());
    // if the node is a unary, then that kid must be the head
    // it used to special case preterminal and ROOT/TOP case
    // but that seemed bad (especially hardcoding string "ROOT")
    if (kids.length == 1) {
      return kids[0];
    } else {
      if (DEBUG) {
        System.err.println("looking for head of " + t.label() + " [" + motherCat + ']');
      }
      // We know we have nonterminals underneath
      // (a bit of a Penn Treebank assumption, but)
      // look at label. We assume it was interned
      Tree theHead = traverseLocate(kids, motherCat, true);
      return theHead;
    }
  }

  public Tree determineHead(Tree t, Tree parent) {
    return determineHead(t);
  }

  private Tree traverseLocate(Tree[] daughterTrees, String motherKey, boolean deflt) {

    String[] how = nonTerminalInfo.get(motherKey);
    Tree theHead = null;
    int headIdx; // = 0;
    String childCat;
    boolean found = false;

    if (how == null) {
      if (DEBUG) {
        System.err.println("Warning: No rule found for " + motherKey);
      }
      return null;
    }

    String direction = how[0];

    if (coordSwitch) {
      if (how[0].equals("left")) {
        direction = "right";
      }
      if (how[0].equals("right")) {
        direction = "left";
      }
    }

    if (direction.equals("left")) {
      twoloop:
      for (int i = 1; i < how.length; i++) {
        for (headIdx = 0; headIdx < daughterTrees.length; headIdx++) {
          childCat = tlp.basicCategory(daughterTrees[headIdx].label().value());
          if (how[i].equals(childCat)) {
            theHead = daughterTrees[headIdx];
            found = true;
            break twoloop;
          }
        }
      }
      if (!found) {
        // none found by tag, so return first or null
        if (deflt) {
          headIdx = 0;
          theHead = daughterTrees[headIdx];
        } else {
          return null;
        }
      }
    } else if (direction.equals("rightdis")) {
      // from right, but search for any, not in turn
      twoloop:
      for (headIdx = daughterTrees.length - 1; headIdx >= 0; headIdx--) {
        childCat = tlp.basicCategory(daughterTrees[headIdx].label().value());
        for (int i = 1; i < how.length; i++) {
          if (DEBUG) {
            System.err.println("Testing for whether " + how[i] + " == " + childCat + ": " + ((how[i].equals(childCat)) ? "true" : "false"));
          }
          if (how[i].equals(childCat)) {
            theHead = daughterTrees[headIdx];
            found = true;
            break twoloop;
          }
        }
      }
      if (!found) {
        // none found by tag, so return last or null
        if (deflt) {
          headIdx = daughterTrees.length - 1;
          theHead = daughterTrees[headIdx];
        } else {
          return null;
        }
      }
    } else {
      // from right
      twoloop:
	    for (int i = 1; i < how.length; i++) {
        for (headIdx = daughterTrees.length - 1; headIdx >= 0; headIdx--) {
          childCat = tlp.basicCategory(daughterTrees[headIdx].label().value());
          if (how[i].equals(childCat)) {
            theHead = daughterTrees[headIdx];
            found = true;
            break twoloop;
          }
        }
      }
      if (!found) {
        // none found by tag, so return last or null
        if (deflt) {
          headIdx = daughterTrees.length - 1;
          theHead = daughterTrees[headIdx];
        } else {
          return null;
        }
      }
    }
    if (DEBUG) {
      System.err.println("Head for " + motherKey + " chose index " + headIdx + ' ' + theHead.label()
              /* + "[" + hptr.headWord + "]" */);
    }
    return theHead;
  }


  private HashMap<String, String[]> nonTerminalInfo;

  {
    nonTerminalInfo = new HashMap<String, String[]>();
    // these are first-cut rules

    // ROOT is not always unary for chinese -- PAIR is a special notation
    // that the Irish people use for non-unary ones....
    nonTerminalInfo.put("ROOT", new String[]{"left", "IP"});
    nonTerminalInfo.put("PAIR", new String[]{"left", "IP"});

    // Major syntactic categories
    nonTerminalInfo.put("ADJP", new String[]{"left", "JJ", "ADJP"}); // there is one ADJP unary rewrite to AD but otherwise all have JJ or ADJP
    nonTerminalInfo.put("ADVP", new String[]{"left", "AD", "CS", "ADVP", "JJ"}); // CS is a subordinating conjunctor, and there are a couple of ADVP->JJ unary rewrites
    nonTerminalInfo.put("CLP", new String[]{"right", "M", "CLP"});
    //nonTerminalInfo.put("CP", new String[] {"left", "WHNP","IP","CP","VP"}); // this is complicated; see bracketing guide p. 34.  Actually, all WHNP are empty.  IP/CP seems to be the best semantic head; syntax would dictate DEC/ADVP.
    nonTerminalInfo.put("CP", new String[]{"right", "DEC", "WHNP", "WHPP"}); // the (syntax-oriented) right-first head rule
    nonTerminalInfo.put("DNP", new String[]{"right", "DEG"}); // according to tgrep2, first preparation, all DNPs have a DEG daughter
    nonTerminalInfo.put("DP", new String[]{"left", "DT", "DP"}); // there's one instance of DP adjunction
    nonTerminalInfo.put("DVP", new String[]{"right", "DEV"}); // DVP always has DEV under it
    nonTerminalInfo.put("FRAG", new String[]{"right", "VV", "NN"}); //FRAG seems only to be used for bits at the beginnings of articles: "Xinwenshe<DATE>" and "(wan)"
    nonTerminalInfo.put("IP", new String[]{"left", "IP", "VP"});  // seems to cover everything
    nonTerminalInfo.put("LCP", new String[]{"right", "LC", "LCP"}); // there's a bit of LCP adjunction
    nonTerminalInfo.put("LST", new String[]{"right", "CD", "PU"}); // covers all examples
    nonTerminalInfo.put("NP", new String[]{"right", "NN", "NR", "NT", "NP", "PN", "CP"}); // Basic heads are NN/NR/NT/NP; PN is pronoun.  Some NPs are nominalized relative clauses without overt nominal material; these are NP->CP unary rewrites.  Finally, note that this doesn't give any special treatment of coordination.
    nonTerminalInfo.put("PP", new String[]{"left", "P", "PP"}); // in the manual there's an example of VV heading PP but I couldn't find such an example with tgrep2
    nonTerminalInfo.put("PRN", new String[]{"left", "PU"}); //presumably left/right doesn't matter
    nonTerminalInfo.put("QP", new String[]{"right", "CD", "OD", "QP"}); // there's some QP adjunction
    nonTerminalInfo.put("UCP", new String[]{"left", }); //an alternative would be "PU","CC"
    nonTerminalInfo.put("VP", new String[]{"left", "VP", "VPT", "VV", "VA", "VC", "VE", "IP"});//note that ba and long bei introduce IP-OBJ small clauses; short bei introduces VP

    // verb compounds
    nonTerminalInfo.put("VCD", new String[]{"left", "VV"}); // could easily be right instead
    nonTerminalInfo.put("VCP", new String[]{"left", "VV"}); // not much info from documentation
    nonTerminalInfo.put("VRD", new String[]{"left", "VV"}); // definitely left
    nonTerminalInfo.put("VSB", new String[]{"right", "VV"}); // definitely right, though some examples look questionably classified (na2lai2 zhi1fu4)
    nonTerminalInfo.put("VNV", new String[]{"left", "VV"}); // left/right doesn't matter
    nonTerminalInfo.put("VPT", new String[]{"left", "VV"}); // activity verb is to the left

    // some POS tags apparently sit where phrases are supposed to be
    nonTerminalInfo.put("CD", new String[]{"right", "CD"});
    nonTerminalInfo.put("NN", new String[]{"right", "NN"});
    nonTerminalInfo.put("NR", new String[]{"right", "NR"});

  }
}
