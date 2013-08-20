package edu.stanford.nlp.trees.international.icegb;

import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

import java.util.HashMap;


/**
 * HeadFinder for the ICE-GB corpus.
 *
 * @author Jeanette Pettibone
 */
public class OldICEGBHeadFinder implements HeadFinder {

  /**
   * 
   */
  private static final long serialVersionUID = 7149535860928448370L;

  private static final boolean DEBUG = false;

  private final TreebankLanguagePack tlp;

  public OldICEGBHeadFinder() {
    this(new ICEGBLanguagePack());
  }

  public OldICEGBHeadFinder(TreebankLanguagePack tlp) {
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

    //System.out.println("Time to determine the head ...");
    Tree theHead;

    if (t.isLeaf()) {
      return null;
    }
    Tree[] kids = t.children();

    //this looks to see if the head is explicitly marked - this
    //is definitely something I want to use in ICE-GB where the
    //head is mostly marked
    // Roger used the idea of having a Category instead of Label for
    // Negra ... because of the functional labeling, etc.  The same
    // could be said for the ICE-GB corpus

    if ((theHead = findMarkedHead(kids)) != null) {
      //System.out.println("Found explicitly marked head");
      theHead.pennPrint();
      //System.out.println("the explicitly marked head is " + theHead);
      return theHead;
    }

    String motherCat = tlp.basicCategory(t.label().value());
    //System.out.println("motherCat is " + motherCat);

    // if the node is a unary, then that kid must be the head
    // it used to special case preterminal and ROOT/TOP case
    // but that seemed bad (especially hardcoding string "ROOT")
    if (kids.length == 1) {
      //System.out.println("the head is: " + kids[0]);
      //System.out.println("and the head of that is: " + determineHead(kids[0]));
      return kids[0];
    } else {
      if (DEBUG) {
        System.err.println("looking for head of " + t.label() + " [" + motherCat + "]");
      }
      // We know we have nonterminals underneath
      // (a bit of a Penn Treebank assumption, but)
      // look at label. We assume it was interned

      theHead = traverseLocate(kids, motherCat, true);
      //System.out.println("the traversed head is " + theHead);
      return theHead;

    }
  }

  public Tree determineHead(Tree t, Tree parent) {
    return determineHead(t);
  }


  // I can try this instead of doing what Roger did - FOR NOW
  // I still think it might be better to get all the
  // parts in order ...


  private Tree findMarkedHead(Tree[] kids) {
    for (int i = 0, n = kids.length; i < n; i++) {
      if (kids[i].label() instanceof ICEGBLabel && ((ICEGBLabel) kids[i].label()).function() != null && ((ICEGBLabel) kids[i].label()).function().matches(".*HD.*")) {
        return kids[i];
      }
    }
    return null;
  }


  private Tree traverseLocate(Tree[] daughterTrees, String motherKey, boolean deflt) {

    //the input is (kids, motherCat, true)

    //System.out.println("I'm traversing the tree ...");

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

    if (direction.equals("left")) {

      //System.out.println("Traversing to the left ...");

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
    }

    /* For now I'm not using rightdis ... I don't think that I'll need it,
       but I'm not ready to erase it yet just in case
    else if (direction.equals("rightdis")) {
        // from right, but search for any, not in turn
        twoloop:
        for (headIdx = daughterTrees.length - 1; headIdx >= 0; headIdx--) {
      childCat = tlp.basicCategory(daughterTrees[headIdx].label().value());
      for (int i = 1; i < how.length; i++) {
          if (DEBUG) {
        System.err.println("Testing for whether " + how[i] + " == " +
            childCat + ": " + ((how[i].equals(childCat)) ?
            "true": "false"));
          }
          if (how[i].equals(childCat)) {
        theHead = daughterTrees[headIdx];
        found = true;
        break twoloop;
          }
      }
        }
        if ( ! found) {
      // none found by tag, so return last or null
      if (deflt) {
          headIdx = daughterTrees.length - 1;
          theHead = daughterTrees[headIdx];
      } else {
          return null;
      }
        }
    }

    */
    else {
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
      System.err.println("Head for " + motherKey + " chose index " + headIdx + " " + theHead.label());
      ///* + "[" + hptr.headWord + "]" */ );
    }
    //System.out.println("the head is: " + theHead);
    return theHead;
  }


  //some notes for me: w -> h ... "left" means that w precedes h, "right"
  //means that w follows h in the sentence


  private HashMap<String, String[]> nonTerminalInfo;

  // Initialization block.
  // "left" means search left-to-right by category and then by position
  // "right" means search right-to-left by category and then by position
  // "rightdis" means search right-to-left by position and then by category
  // In all cases the first thing found is returned.
  {
    nonTerminalInfo = new HashMap<String, String[]>();

    // much of the time we won't need to do this much
    // this is my own doing ... I can't tgrep yet so I'm just going to
    // put in the bare minimum and see what happens
    // fill this in more when the tgrepable is ready

    //AJP comes with head info
    nonTerminalInfo.put("AJP", new String[]{"left", "ADJ", "AVP", "CL", "PP"});

    //AVP comes with head info
    nonTerminalInfo.put("AVP", new String[]{"right", "ADV", "AVP"});

    nonTerminalInfo.put("CL", new String[]{"left", "VP", "CL", "PP", "NP"});

    /*
    nonTerminalInfo.put("CL
    ",
                        new String[] {"left", "VP", "CL", "PP", "NP"});
    */

    nonTerminalInfo.put("NONCL", new String[]{"left", "NP"});

    nonTerminalInfo.put("DTP", new String[]{"right", "ART", "PRON", "NUM", "N"});

    nonTerminalInfo.put("NONCL", new String[]{"left"});

    nonTerminalInfo.put("DISP", new String[]{"left", "NP", "AJP"});

    // the Collins stuff has many different NP's and
    // each has a different direction associated with it -
    // but it should matter because NP comes with head info
    nonTerminalInfo.put("NP", new String[]{"right"});

    nonTerminalInfo.put("PP", new String[]{"right", "PREP"});

    nonTerminalInfo.put("PREP", new String[]{"left", "PREP"});

    nonTerminalInfo.put("PREDEL", new String[]{"left", "PREDEL", "VP"});

    nonTerminalInfo.put("SUBP", new String[]{"left", "CONJUNC"});

    nonTerminalInfo.put("VP", new String[]{"left", "V"});

    nonTerminalInfo.put("ROOT", new String[]{"left", "CL", "NONCL"});
  }

}
