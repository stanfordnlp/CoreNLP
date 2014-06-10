package edu.stanford.nlp.trees.international.french;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.trees.*;

import java.util.HashMap;

/**
 * Implements the head finding rules from Dybro-Johansen master's thesis.
 *
 * @author mcdm
 */
public class DybroFrenchHeadFinder extends AbstractCollinsHeadFinder {

  private static final long serialVersionUID = 8798606577201646967L;


  public DybroFrenchHeadFinder() {
    this(new FrenchTreebankLanguagePack());
  }


  public DybroFrenchHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);


    //French POS:
    // A (adjective), ADV (adverb), C (conjunction and subordinating conjunction), CL (clitics),
    // CS (subordinating conjunction) but occurs only once!,
    // D (determiner), ET (foreign word), I (interjection), N (noun),
    // P (preposition), PREF (prefix), PRO (strong pronoun -- very confusing), V (verb), PUNC (punctuation)

    nonTerminalInfo = new HashMap<String, String[][]>();

    // "sentence"
    nonTerminalInfo.put(tlp.startSymbol(), new String[][]{{"right", "VN", "AP", "NP", "Srel", "VPpart", "AdP", "I", "Ssub", "VPinf", "PP", "ADV"}, {"right"}});
    nonTerminalInfo.put("SENT", new String[][]{{"right", "VN", "AP", "NP", "Srel", "VPpart", "AdP", "I", "Ssub", "VPinf", "PP", "ADV"}, {"right"}});

    // adjectival phrases
    nonTerminalInfo.put("AP", new String[][]{{"right", "A", "ET", "V", "ADV"}});

    // adverbial phrases
    nonTerminalInfo.put("AdP", new String[][]{{"right", "ADV"}, {"right"}});

    // coordinated phrases
    nonTerminalInfo.put("COORD", new String[][]{{"leftdis", "C", "CC", "CS"}, {"left"}});

    // noun phrases
    nonTerminalInfo.put("NP", new String[][]{{"leftdis", "N", "PRO"}, {"left", "NP", "A", "AP", "I", "VPpart", "ADV", "AdP", "ET", "D"}});

    // prepositional phrases
    nonTerminalInfo.put("PP", new String[][]{{"left", "P"}, {"left"}});

    // verbal nucleus
    nonTerminalInfo.put("VN", new String[][]{{"right", "V", "VPinf"}, {"right"}});

    // infinitive clauses
    nonTerminalInfo.put("VPinf", new String[][]{{"left", "VN", "V"}, {"left"}});

    // nonfinite clauses
    nonTerminalInfo.put("VPpart", new String[][]{{"left", "V", "VN"}, {"left"}});

    // relative clauses
    nonTerminalInfo.put("Srel", new String[][]{{"right", "VN", "AP", "NP"}, {"right"}});

    // subordinate clauses
    nonTerminalInfo.put("Ssub", new String[][]{{"right", "VN", "AP", "NP", "PP", "VPinf", "Ssub", "VPpart", "A", "ADV"}, {"right"}});

    // parenthetical clauses
    nonTerminalInfo.put("Sint", new String[][]{{"right", "VN", "AP", "NP", "PP", "VPinf", "Ssub", "VPpart", "A", "ADV"}, {"right"}});

    // adverbes
    //nonTerminalInfo.put("ADV", new String[][] {{"left", "ADV", "PP", "P"}});

    // compound categories: start with MW: D, A, C, N, ADV, V, P, PRO, CL
    nonTerminalInfo.put("MWD", new String[][] {{"left", "D"}, {"left"}});
    nonTerminalInfo.put("MWA", new String[][] {{"left", "P"}, {"left", "N"}, {"right", "A"}, {"right"}});
    nonTerminalInfo.put("MWC", new String[][] {{"left", "C", "CS"}, {"left"}});
    nonTerminalInfo.put("MWN", new String[][] {{"right", "N", "ET"}, {"right"}});
    nonTerminalInfo.put("MWV", new String[][] {{"left", "V"}, {"left"}});
    nonTerminalInfo.put("MWP", new String[][] {{"left", "P", "ADV", "PRO"}, {"left"}});
    nonTerminalInfo.put("MWPRO", new String[][] {{"left", "PRO", "CL", "N", "A"}, {"left"}});
    nonTerminalInfo.put("MWCL", new String[][] {{"left", "CL"}, {"right"}});
    nonTerminalInfo.put("MWADV", new String[][] {{"left", "P", "ADV"}, {"left"}});

    nonTerminalInfo.put("MWI", new String[][] {{"left", "N", "ADV", "P"}, {"left"}});
    nonTerminalInfo.put("MWET", new String[][] {{"left", "ET", "N"}, {"left"}});

    //TODO: wsg2011: For phrasal nodes that lacked a label.
    nonTerminalInfo.put(FrenchTreeReader.MISSING_PHRASAL, new String[][]{{"left"}});

  }


  /**
   * Go through trees and determine their heads and print them.
   * Just for debugging. <br>
   * Usage: <code>
   * java edu.stanford.nlp.trees.DybroFrenchHeadFinder treebankFilePath
   * </code>
   *
   * @param args The treebankFilePath
   */
  public static void main(String[] args) {
    Treebank treebank = new DiskTreebank();
    CategoryWordTag.suppressTerminalDetails = true;
    treebank.loadPath(args[0]);
    final HeadFinder chf = new DybroFrenchHeadFinder();
    treebank.apply(new TreeVisitor() {
      public void visitTree(Tree pt) {
        pt.percolateHeads(chf);
        pt.pennPrint();
        System.out.println();
      }
    });
  }


}


