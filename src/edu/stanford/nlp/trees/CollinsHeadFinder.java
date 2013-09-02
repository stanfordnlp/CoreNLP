package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.util.Generics;


/**
 * Implements the HeadFinder found in Michael Collins' 1999 thesis.
 * Except: we've added a head rule for NX, which returns the leftmost item.
 * No rule for the head of NX is found in any of the versions of
 * Collins' head table that we have (did he perhaps use the NP rules
 * for NX? -- no Bikel, CL, 2005 says it defaults to leftmost).
 * These rules are suitable for the Penn Treebank.
 * <p>
 * May 2004: Added support for AUX and AUXG to the VP rules; these cause
 * no interference in Penn Treebank parsing, but means that these rules
 * also work for the BLLIP corpus (or Charniak parser output in general).
 * Feb 2005: Fixes to coordination reheading so that punctuation cannot
 * become head.
 *
 * @author Christopher Manning
 */

public class CollinsHeadFinder extends AbstractCollinsHeadFinder {

  private static final String[] EMPTY_STRING_ARRAY = {};

  public CollinsHeadFinder() {
    this(new PennTreebankLanguagePack());
  }

  /** This constructor provides the traditional behavior, where there is
   *  no special avoidance of punctuation categories.
   *
   *  @param tlp TreebankLanguagePack used for basic category function
   */
  public CollinsHeadFinder(TreebankLanguagePack tlp) {
    this(tlp, EMPTY_STRING_ARRAY);
  }

  public CollinsHeadFinder(TreebankLanguagePack tlp, String... categoriesToAvoid) {
    super(tlp, categoriesToAvoid);

    nonTerminalInfo = Generics.newHashMap();
    // This version from Collins' diss (1999: 236-238)
    nonTerminalInfo.put("ADJP", new String[][]{{"left", "NNS", "QP", "NN", "$", "ADVP", "JJ", "VBN", "VBG", "ADJP", "JJR", "NP", "JJS", "DT", "FW", "RBR", "RBS", "SBAR", "RB"}});
    nonTerminalInfo.put("ADVP", new String[][]{{"right", "RB", "RBR", "RBS", "FW", "ADVP", "TO", "CD", "JJR", "JJ", "IN", "NP", "JJS", "NN"}});
    nonTerminalInfo.put("CONJP", new String[][]{{"right", "CC", "RB", "IN"}});
    nonTerminalInfo.put("FRAG", new String[][]{{"right"}}); // crap
    nonTerminalInfo.put("INTJ", new String[][]{{"left"}});
    nonTerminalInfo.put("LST", new String[][]{{"right", "LS", ":"}});
    nonTerminalInfo.put("NAC", new String[][]{{"left", "NN", "NNS", "NNP", "NNPS", "NP", "NAC", "EX", "$", "CD", "QP", "PRP", "VBG", "JJ", "JJS", "JJR", "ADJP", "FW"}});
    nonTerminalInfo.put("NX", new String[][]{{"left"}}); // crap
    nonTerminalInfo.put("PP", new String[][]{{"right", "IN", "TO", "VBG", "VBN", "RP", "FW"}});
    // should prefer JJ? (PP (JJ such) (IN as) (NP (NN crocidolite)))
    nonTerminalInfo.put("PRN", new String[][]{{"left"}});
    nonTerminalInfo.put("PRT", new String[][]{{"right", "RP"}});
    nonTerminalInfo.put("QP", new String[][]{{"left", "$", "IN", "NNS", "NN", "JJ", "RB", "DT", "CD", "NCD", "QP", "JJR", "JJS"}});
    nonTerminalInfo.put("RRC", new String[][]{{"right", "VP", "NP", "ADVP", "ADJP", "PP"}});
    nonTerminalInfo.put("S", new String[][]{{"left", "TO", "IN", "VP", "S", "SBAR", "ADJP", "UCP", "NP"}});
    nonTerminalInfo.put("SBAR", new String[][]{{"left", "WHNP", "WHPP", "WHADVP", "WHADJP", "IN", "DT", "S", "SQ", "SINV", "SBAR", "FRAG"}});
    nonTerminalInfo.put("SBARQ", new String[][]{{"left", "SQ", "S", "SINV", "SBARQ", "FRAG"}});
    nonTerminalInfo.put("SINV", new String[][]{{"left", "VBZ", "VBD", "VBP", "VB", "MD", "VP", "S", "SINV", "ADJP", "NP"}});
    nonTerminalInfo.put("SQ", new String[][]{{"left", "VBZ", "VBD", "VBP", "VB", "MD", "VP", "SQ"}});
    nonTerminalInfo.put("UCP", new String[][]{{"right"}});
    nonTerminalInfo.put("VP", new String[][]{{"left", "TO", "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "AUX", "AUXG", "VP", "ADJP", "NN", "NNS", "NP"}});
    nonTerminalInfo.put("WHADJP", new String[][]{{"left", "CC", "WRB", "JJ", "ADJP"}});
    nonTerminalInfo.put("WHADVP", new String[][]{{"right", "CC", "WRB"}});
    nonTerminalInfo.put("WHNP", new String[][]{{"left", "WDT", "WP", "WP$", "WHADJP", "WHPP", "WHNP"}});
    nonTerminalInfo.put("WHPP", new String[][]{{"right", "IN", "TO", "FW"}});
    nonTerminalInfo.put("X", new String[][]{{"right"}}); // crap rule
    nonTerminalInfo.put("NP", new String[][]{{"rightdis", "NN", "NNP", "NNPS", "NNS", "NX", "POS", "JJR"}, {"left", "NP"}, {"rightdis", "$", "ADJP", "PRN"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "RB", "QP"}});
    nonTerminalInfo.put("TYPO", new String[][] {{"left"}}); // another crap rule, for Brown (Roger)
    nonTerminalInfo.put("EDITED", new String[][] {{"left"}});  // crap rule for Switchboard (if don't delete EDITED nodes)
    nonTerminalInfo.put("XS", new String[][] {{"right", "IN"}}); // rule for new structure in QP
  }

  @Override
  protected int postOperationFix(int headIdx, Tree[] daughterTrees) {
    if (headIdx >= 2) {
      String prevLab = tlp.basicCategory(daughterTrees[headIdx - 1].value());
      if (prevLab.equals("CC") || prevLab.equals("CONJP")) {
        int newHeadIdx = headIdx - 2;
        Tree t = daughterTrees[newHeadIdx];
        while (newHeadIdx >= 0 && t.isPreTerminal() &&
            tlp.isPunctuationTag(t.value())) {
          newHeadIdx--;
        }
        if (newHeadIdx >= 0) {
          headIdx = newHeadIdx;
        }
      }
    }
    return headIdx;
  }


  /**
   * Go through trees and determine their heads and print them.
   * Just for debuggin'. <br>
   * Usage: <code>
   * java edu.stanford.nlp.trees.CollinsHeadFinder treebankFilePath
   * </code>
   *
   * @param args The treebankFilePath
   */
  public static void main(String[] args) {
    Treebank treebank = new DiskTreebank();
    CategoryWordTag.suppressTerminalDetails = true;
    treebank.loadPath(args[0]);
    final HeadFinder chf = new CollinsHeadFinder();
    treebank.apply(new TreeVisitor() {
      @Override
      public void visitTree(Tree pt) {
        pt.percolateHeads(chf);
        pt.pennPrint();
        System.out.println();
      }
    });
  }

  private static final long serialVersionUID = -8747319554557223437L;

}
