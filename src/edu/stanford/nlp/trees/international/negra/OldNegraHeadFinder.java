package edu.stanford.nlp.trees.international.negra;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

import java.util.HashMap;


/**
 * HeadFinder for the Negra Treebank.  Adapted from
 * CollinsHeadFinder.
 *
 * @author Roger Levy
 */
public class OldNegraHeadFinder implements HeadFinder {

  /**
   * 
   */
  private static final long serialVersionUID = -5175303899621438824L;

  private static final boolean DEBUG = false;

  private boolean coordSwitch = false;

  private final TreebankLanguagePack tlp;

  public OldNegraHeadFinder() {
    this(new NegraPennLanguagePack());
  }

  public OldNegraHeadFinder(TreebankLanguagePack tlp) {
    this.tlp = tlp;
  }

  /**
   * Determine which daughter of the current parse tree is the head.
   * It assumes that the daughters already have had their heads
   * determined. Another method has to do the tree walking.
   *
   * @param t The parse tree to examine the da
   *          ughters of
   * @return The parse tree that is the head
   */
  public Tree determineHead(Tree t) {
    Tree theHead;

    if (t.isLeaf()) {
      return null;
    }
    Tree[] kids = t.children();

    if ((theHead = findMarkedHead(kids)) != null) {
      //System.out.println("Found explicitly marked head");
      //theHead.pennPrint();
      return theHead;
    }

    String motherCat = tlp.basicCategory(t.label().value());
    // if the node is a unary, then that kid must be the head
    // it used to special case preterminal and ROOT/TOP case
    // but that seemed bad (especially hardcoding string "ROOT")
    if (kids.length == 1) {
      return kids[0];
    } else {
      if (DEBUG) {
        System.err.println("looking for head of " + t.label() + " [" + motherCat + "]");
      }

      // some special rule for S
      if (motherCat.equals("S") && kids[0].label().value().equals("PRELS")) {
        return kids[0];
      }
	
	

      // We know we have nonterminals underneath
      // (a bit of a Penn Treebank assumption, but)
      // look at label. We assume it was interned
      theHead = traverseLocate(kids, motherCat, true);
      return theHead;
    }

  }

  public Tree determineHead(Tree t, Tree parent) {
    return determineHead(t);
  }

  /* Some Negra local trees have an explicitly marked head.  Use it if
   * possible. */
  private Tree findMarkedHead(Tree[] kids) {
    for (int i = 0, n = kids.length; i < n; i++) {
      if (kids[i].label() instanceof NegraLabel && ((NegraLabel) kids[i].label()).getEdge() != null && ((NegraLabel) kids[i].label()).getEdge().equals("HD")) {
        //System.err.println("found manually-labeled head");
        return kids[i];
      }
    }
    return null;
  }


  private Tree traverseLocate(Tree[] daughterTrees, String motherKey, boolean deflt) {

    String[] how = nonTerminalInfo.get(motherKey);
    Tree theHead = null;
    int headIdx = 0;
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
      System.err.println("Head for " + motherKey + " chose index " + headIdx + " " + theHead.label()
              /* + "[" + hptr.headWord + "]" */);
    }
    return theHead;
  }


  private HashMap<String, String[]> nonTerminalInfo;

  {
    nonTerminalInfo = new HashMap<String, String[]>();
    // these are first-cut rules 

    // there are non-unary nodes I put in
    nonTerminalInfo.put("NUR", new String[]{"left", "S"});

    // root -- yuk
    nonTerminalInfo.put("ROOT", new String[]{"left", "S", "CS", "VP", "CVP", "NP", "XY", "CNP", "AVP", "CAVP"});

    // Major syntactic categories -- in order appearing in negra.export
    nonTerminalInfo.put("NP", new String[]{"right", "NN", "NE", "MPN", "NP", "CNP", "PN", "CAR"}); // Basic heads are NN/NE/NP; CNP is coordination; CAR is cardinal
    nonTerminalInfo.put("AP", new String[]{"right", "ADJD", "ADJA", "CAP", "AA", "ADV"}); // there is one ADJP unary rewrite to AD but otherwise all have JJ or ADJP
    nonTerminalInfo.put("PP", new String[]{"left", "KOKOM", "APPR", "PROAV"});
    //nonTerminalInfo.put("S", new String[] {"right", "S","CS","NP"}); //Most of the time, S has its head explicitly marked.  CS is coordinated sentence.  I don't fully understand the rest of "non-headed" german sentences to say much.
    nonTerminalInfo.put("S", new String[]{"right", "VMFIN", "VVFIN", "VAFIN", "S", "VP"});
    nonTerminalInfo.put("VP", new String[]{"right", "VZ", "VAINF", "VMINF", "VVINF", "VVIZU", "VVPP", "VMPP", "VAPP", "PP"}); // VP usually has explicit head marking; there's lots of garbage here to sort out, though.
    nonTerminalInfo.put("VZ", new String[]{"left", "PRTZU", "APPR"}); // we could also try using the verb (on the right) instead of ZU as the head, maybe this would make more sense...
    nonTerminalInfo.put("CO", new String[]{"left"}); // this is an unlike coordination
    nonTerminalInfo.put("AVP", new String[]{"right", "ADV", "AVP", "ADJD", "PROAV", "PP"});
    nonTerminalInfo.put("AA", new String[]{"right", "ADJD", "ADJA"}); // superlative adjective phrase with "am"; I'm using the adjective not the "am" marker
    nonTerminalInfo.put("CNP", new String[]{"right", "NN", "NE", "MPN", "NP", "CNP", "PN", "CAR"});
    nonTerminalInfo.put("CAP", new String[]{"right", "ADJD", "ADJA", "CAP", "AA", "ADV"});
    nonTerminalInfo.put("CPP", new String[]{"right", "APPR", "PROAV", "PP", "CPP"});
    nonTerminalInfo.put("CS", new String[]{"right", "S", "CS"});
    nonTerminalInfo.put("CVP", new String[]{"right", "VP", "CVP"}); // covers all examples
    nonTerminalInfo.put("CVZ", new String[]{"right", "VZ"}); // covers all examples
    nonTerminalInfo.put("CAVP", new String[]{"right", "ADV", "AVP", "ADJD", "PWAV", "APPR", "PTKVZ"});
    nonTerminalInfo.put("MPN", new String[]{"right", "NE", "FM", "CARD"}); //presumably left/right doesn't matter
    nonTerminalInfo.put("NM", new String[]{"right", "CARD", "NN"}); // covers all examples
    nonTerminalInfo.put("CAC", new String[]{"right", "APPR", "AVP"}); //covers all examples
    nonTerminalInfo.put("CH", new String[]{"right"});
    nonTerminalInfo.put("MTA", new String[]{"right", "ADJA", "ADJD", "NN"});
    nonTerminalInfo.put("CCP", new String[]{"right", "AVP"});
    nonTerminalInfo.put("DL", new String[]{"left"}); // don't understand this one yet
    nonTerminalInfo.put("ISU", new String[]{"right"}); // idioms, I think
    nonTerminalInfo.put("QL", new String[]{"right"}); // these are all complicated numerical expressions I think

    nonTerminalInfo.put("---CJ", new String[]{"right", "PP"}); // a garbage conjoined phrase appearing once

    //nonTerminalInfo.put("gooble", new String[][] {{"right"},{"PP","ADVJ"}}); // gunk
	



    // some POS tags apparently sit where phrases are supposed to be
    nonTerminalInfo.put("CD", new String[]{"right", "CD"});
    nonTerminalInfo.put("NN", new String[]{"right", "NN"});
    nonTerminalInfo.put("NR", new String[]{"right", "NR"});

  }
}
