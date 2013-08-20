package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.CategoryWordTagFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * Implements the HeadFinder found in Ciprian Chelba's 2000 thesis.
 * These rules are suitable for the Penn Treebank.
 * This code assumes that tree category labels are interned and
 * does comparisons with equality (==).  It won't work if the category labels
 * are not interned.  In particular, this also means that functional tags
 * should have been stripped.
 * <p>
 * <i>Note:</i> [CDM 2008] There's really weird stuff in this class with the
 * regular expressions.  All I've done is convert them from Oro to Java 1.4.
 * Really this should all be redone if it is to be used.
 *
 * @author John Rappaport
 */
public class ChelbaHeadFinder implements HeadFinder {

  /**
   *
   */
  private static final long serialVersionUID = 714845907365065609L;

  private static final boolean DEBUG = false;

  /**
   * Determine which daughter of the current parse tree is the head.
   * It assumes that the daughters already have had their heads
   * determined.  It assumes that category Strings were interned.
   * It assumes that labels are a <code>CategoryWordTag</code>
   *
   * @param t The parse tree to examine the daughters of.
   *          This is assumed to never be a leaf
   * @return The parse tree that is the head
   */
  public Tree determineHead(Tree t) {
    if (t.isLeaf()) {
      return null;
    }
    Tree[] kids = t.children();
    String motherCat = ((CategoryWordTag) (t.label())).category();
    // do terminal case and ROOT/TOP case
    if (kids.length == 1 && (kids[0].isLeaf() || motherCat.equals("ROOT"))) {
      return kids[0];
    } else {
      if (DEBUG) {
        System.err.println("looking for head of " + t.label());
      }
      // We know we have nonterminals underneath
      // (a bit of a Penn Treebank assumption, but)
      // look at label. We assume it was interned
      Tree theHead;
      theHead = traverseLocate(kids, motherCat, true);

      if (DEBUG) {
        System.err.println("  head was " + motherCat + "[" + /* theHead.headWord + */ "]");
      }
      return theHead;
    }
  }

  public Tree determineHead(Tree t, Tree parent) {
    return determineHead(t);
  }


  /**
   * Go through daughterTrees looking for things from how, and if
   * you do not find one, take leftmost or rightmost thing iff
   * deflt is true
   */
  private Tree traverseLocate(Tree[] daughterTrees, String motherKey, boolean deflt) {

    Pattern[] how = nonTerminalInfo.get(motherKey);
    Tree theHead = null;
    int headIdx = 0;
    CategoryWordTag childLabel;
    String childCat;
    boolean found = false;

    if (how == null) {
      return null;
    }
    if (how[0] == LEFT) {
      twoloop:
      for (int i = 1; i < how.length; i++) {
        for (headIdx = 0; headIdx < daughterTrees.length; headIdx++) {
          childLabel = (CategoryWordTag) daughterTrees[headIdx].label();
          childCat = childLabel.category();
          if ((how[i].matcher("not").matches() && ! how[i].matcher(childCat).matches()) ||
                  ( ! how[i].matcher("not").matches() && how[i].matcher(childCat).matches())) {
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
    } else {
      // from right
      twoloop:
	    for (int i = 1; i < how.length; i++) {
        for (headIdx = daughterTrees.length - 1; headIdx >= 0; headIdx--) {
          childLabel = (CategoryWordTag) daughterTrees[headIdx].label();
          childCat = childLabel.category();
          if ((how[i].matcher("not").matches() && ! how[i].matcher(childCat).matches()) ||
                  ( ! how[i].matcher("not").matches()) && how[i].matcher(childCat).matches()) {
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
    // fix for coordination so left of pair is alway head (p.237). Messy
    //	if (headIdx >= 2) {
    //  String prevLab = daughterTrees[headIdx - 1].label().toString();
    //  if (prevLab == "CC") {
    //	headIdx -= 2;
    //	theHead = daughterTrees[headIdx];
    //   }
    //}
    if (DEBUG) {
      Tree hptr = theHead;
      System.err.println("Head for " + motherKey + " chose index " + headIdx + " " + hptr.label().toString() + "[" + /* hptr.headWord + */ "]");
    }
    return theHead;
  }


  private HashMap<String, Pattern[]> nonTerminalInfo;

  private Pattern LEFT = null;
  private Pattern RIGHT = null;

  //negated set of punctuation characters--
  //used to check that a word is not punctuation
  private Pattern PUNCT = null;

  //non-negated set of punct. characters--
  //used when combining punctuation set with other
  //labels to check for non-inclusion
  private Pattern POS_PUNCT = null;


  // Initialization block
  {
    nonTerminalInfo = new HashMap<String, Pattern[]>();
    String POS_STR = "[]\"['.,:]";

    try {
      LEFT = Pattern.compile("left");
      RIGHT = Pattern.compile("right");
      PUNCT = Pattern.compile("[^]\"['.,:]");
      POS_PUNCT = Pattern.compile(POS_STR);


      // This version from Chelba's diss. pg 15
      nonTerminalInfo.put("TOP", new Pattern[]{RIGHT, Pattern.compile("SE"), Pattern.compile("SB")});

      nonTerminalInfo.put("ADJP", new Pattern[]{RIGHT, Pattern.compile("QP|JJ|VBN|ADJP|\\$|JJR"), Pattern.compile("not|PP|S|SBAR|" + POS_STR)});

      nonTerminalInfo.put("ADVP", new Pattern[]{RIGHT, Pattern.compile("RBR|RB|TO|ADVP"), Pattern.compile("not|PP|S|SBAR|" + POS_STR)});

      nonTerminalInfo.put("CONJP", new Pattern[]{LEFT, Pattern.compile("RB"), PUNCT});

      nonTerminalInfo.put("FRAG", new Pattern[]{LEFT, PUNCT});

      nonTerminalInfo.put("INTJ", new Pattern[]{LEFT, PUNCT});

      nonTerminalInfo.put("LST", new Pattern[]{LEFT, Pattern.compile("LS"), PUNCT});

      nonTerminalInfo.put("NAC", new Pattern[]{RIGHT, Pattern.compile("NNP|NNPS|NP|NN|NNS|NX|CD|QP|VBG"), PUNCT});

      nonTerminalInfo.put("NP", new Pattern[]{RIGHT, Pattern.compile("NNP|NNPS|NP|NN|NNS|NX|CD|QP|PRP|VBG"), PUNCT});

      nonTerminalInfo.put("NX", new Pattern[]{RIGHT, Pattern.compile("NNP|NNPS|NP|NN|NNS|NX|CD|QP|VBG"), PUNCT});

      nonTerminalInfo.put("PP", new Pattern[]{LEFT, Pattern.compile("IN"), Pattern.compile("TO"), Pattern.compile("VBG"), Pattern.compile("VBN"), Pattern.compile("PP"), PUNCT});

      nonTerminalInfo.put("PRN", new Pattern[]{LEFT, Pattern.compile("NP"), Pattern.compile("PP"), Pattern.compile("SBAR"), Pattern.compile("ADVP"), Pattern.compile("SINV"), Pattern.compile("S"), Pattern.compile("VP"), PUNCT});

      nonTerminalInfo.put("PRT", new Pattern[]{LEFT, Pattern.compile("RP"), PUNCT});

      nonTerminalInfo.put("QP", new Pattern[]{LEFT, Pattern.compile("CD|QP"), Pattern.compile("NNP|NNPS|NP|NN|NNS|NX"), Pattern.compile("DT|PDT"), Pattern.compile("JJR|JJ"), Pattern.compile("not|CC|" + POS_STR)});

      nonTerminalInfo.put("RRC", new Pattern[]{LEFT, Pattern.compile("ADJP"), Pattern.compile("PP"), Pattern.compile("VP"), PUNCT});

      nonTerminalInfo.put("S", new Pattern[]{RIGHT, Pattern.compile("VP"), Pattern.compile("SBAR|SBARQ|S|SQ|SINV"), PUNCT});

      nonTerminalInfo.put("SBAR", new Pattern[]{RIGHT, Pattern.compile("S|SBAR|SBARQ|SQ|SINV"), PUNCT});

      nonTerminalInfo.put("SBARQ", new Pattern[]{RIGHT, Pattern.compile("SQ"), Pattern.compile("S"), Pattern.compile("SINV"), Pattern.compile("SBAR"), PUNCT});

      nonTerminalInfo.put("SINV", new Pattern[]{RIGHT, Pattern.compile("VP|VBD|VBN|MD|VBZ|VB|VBG|VBP"), Pattern.compile("S"), Pattern.compile("SINV"), PUNCT});

      nonTerminalInfo.put("SQ", new Pattern[]{LEFT, Pattern.compile("VBD|VBN|MD|VBZ|VB|VP|VBG|VBP"), PUNCT});

      nonTerminalInfo.put("UCP", new Pattern[]{LEFT, PUNCT});

      nonTerminalInfo.put("VP", new Pattern[]{LEFT, Pattern.compile("VBD|VBN|MD|VBZ|VB|VP|VBG|VBP"), PUNCT});

      nonTerminalInfo.put("WHADJP", new Pattern[]{RIGHT, PUNCT});

      nonTerminalInfo.put("WHADVP", new Pattern[]{RIGHT, Pattern.compile("WRB"), PUNCT});

      nonTerminalInfo.put("WHNP", new Pattern[]{RIGHT, Pattern.compile("WP"), Pattern.compile("WDT"), Pattern.compile("JJ"), Pattern.compile("WP$"), Pattern.compile("WHNP"), PUNCT});

      nonTerminalInfo.put("WHPP", new Pattern[]{LEFT, Pattern.compile("IN"), PUNCT});

      nonTerminalInfo.put("X", new Pattern[]{RIGHT, PUNCT});
    } catch (PatternSyntaxException e) {
      System.out.println("Bad expression");
      System.exit(1);
    }

  }


  /**
   * Go through trees and determine their heads and print them.
   * Just for debuggin'
   * Use: java ChelbaHeadFinder <path>/wsj/07
   */
  public static void main(String[] args) {
    Treebank treebank = new DiskTreebank(new TreeReaderFactory() {
      public TreeReader newTreeReader(Reader in) {
        return new PennTreeReader(in, new LabeledScoredTreeFactory(new CategoryWordTagFactory()), new BobChrisTreeNormalizer());
      }
    });
    treebank.loadPath(args[0]);
    final HeadFinder chf = new ChelbaHeadFinder();
    treebank.apply(new TreeVisitor() {
      public void visitTree(Tree pt) {
        pt.percolateHeads(chf);
        pt.pennPrint();
        System.out.println();
      }
    });
  }

}
