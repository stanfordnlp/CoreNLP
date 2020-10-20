package edu.stanford.nlp.trees.international.french;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Generics;

/**
 * Head finding rules from Arun Abishek's master's thesis.
 *
 * @author mcdm
 */
public class AbishekFrenchHeadFinder extends AbstractCollinsHeadFinder {

  private static final long serialVersionUID = -7195627297254128427L;

  public AbishekFrenchHeadFinder() {
    this(new FrenchTreebankLanguagePack());
  }


  public AbishekFrenchHeadFinder(FrenchTreebankLanguagePack tlp) {
    super(tlp);


    //French POS:
    // A (adjective), ADV (adverb), C (conjunction and subordinating conjunction), CL (clitics),
    // CS (subordinating conjunction) but occurs only once!,
    // D (determiner), ET (foreign word), I (interjection), N (noun),
    // P (preposition), PREF (prefix), PRO (strong pronoun -- very confusing), V (verb), PUNC (punctuation)

    nonTerminalInfo = Generics.newHashMap();

    // "sentence"
    nonTerminalInfo.put(tlp.startSymbol(), new String[][]{{"left", "VN", "V", "NP", "Srel", "Ssub", "Sint"}});
    nonTerminalInfo.put("SENT", new String[][]{{"left", "VN", "V", "NP", "Srel", "Ssub", "Sint"}});

    // adjectival phrases
    nonTerminalInfo.put("AP", new String[][]{{"right", "A", "N", "V"}});

    // adverbial phrases
    nonTerminalInfo.put("AdP", new String[][]{{"right", "ADV"}, {"left", "P", "D", "C"}});

    // coordinated phrases
    nonTerminalInfo.put("COORD", new String[][]{{"left", "C"}, {"right"}});

    // noun phrases
    nonTerminalInfo.put("NP", new String[][]{{"right", "N", "PRO", "A", "ADV"}, {"left", "NP"}, {"right"}});

    // prepositional phrases
    nonTerminalInfo.put("PP", new String[][]{{"right", "P", "CL", "A", "ADV", "V", "N"}});

    // verbal nucleus
    nonTerminalInfo.put("VN", new String[][]{{"right", "V"}});

    // infinitive clauses
    nonTerminalInfo.put("VPinf", new String[][]{{"left", "VN", "V"}, {"right"}});

    // nonfinite clauses
    nonTerminalInfo.put("VPpart", new String[][]{{"left", "VN", "V"}, {"right"}});

    // relative clauses
    nonTerminalInfo.put("Srel", new String[][]{{"left", "VN", "V"}});

    // subordinate clauses
    nonTerminalInfo.put("Ssub", new String[][]{{"left", "VN", "V"}, {"right"}});

    // parenthetical clauses
    nonTerminalInfo.put("Sint", new String[][]{{"left", "VN", "V"}, {"right"}});

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
    nonTerminalInfo.put(FrenchXMLTreeReader.MISSING_PHRASAL, new String[][]{{"left"}});

  }


  /**
   * Go through trees and determine their heads and print them.
   * Just for debugging. <br>
   * Usage: <code>
   * java edu.stanford.nlp.trees.FrenchHeadFinder treebankFilePath
   * </code>
   *
   * @param args The treebankFilePath
   */
  public static void main(String[] args) {
    Treebank treebank = new DiskTreebank();
    CategoryWordTag.suppressTerminalDetails = true;
    treebank.loadPath(args[0]);
    final HeadFinder chf = new AbishekFrenchHeadFinder();
    treebank.apply(pt -> {
      pt.percolateHeads(chf);
      pt.pennPrint();
      System.out.println();
    });
  }


}

