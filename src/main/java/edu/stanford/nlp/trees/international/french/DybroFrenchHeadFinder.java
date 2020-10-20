package edu.stanford.nlp.trees.international.french;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Generics;

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

    // There is also the expanded French CC tagset.
    // V, A, ADV, PRO, C, CL, N, D are all split into multiple tags.
    // http://www.linguist.univ-paris-diderot.fr/~mcandito/Publications/crabbecandi-taln2008-final.pdf
    // (perhaps you can find an English translation somewhere)
    
    nonTerminalInfo = Generics.newHashMap();

    // "sentence"
    nonTerminalInfo.put(tlp.startSymbol(), new String[][]{{"right", "VN", "AP", "NP", "Srel", "VPpart", "AdP", "I", "Ssub", "VPinf", "PP"}, {"rightdis", "ADV", "ADVWH"}, {"right"}});
    nonTerminalInfo.put("SENT", new String[][]{{"right", "VN", "AP", "NP", "Srel", "VPpart", "AdP", "I", "Ssub", "VPinf", "PP"}, {"rightdis", "ADV", "ADVWH"}, {"right"}});

    // adjectival phrases
    nonTerminalInfo.put("AP", new String[][]{{"rightdis", "A", "ADJ", "ADJWH"}, {"right", "ET"}, {"rightdis", "V", "VIMP", "VINF", "VS", "VPP", "VPR"}, {"rightdis", "ADV", "ADVWH"}});

    // adverbial phrases
    nonTerminalInfo.put("AdP", new String[][]{{"rightdis", "ADV", "ADVWH"}, {"right"}});

    // coordinated phrases
    nonTerminalInfo.put("COORD", new String[][]{{"leftdis", "C", "CC", "CS"}, {"left"}});

    // noun phrases
    nonTerminalInfo.put("NP", new String[][]{{"leftdis", "N", "NPP", "NC", "PRO", "PROWH", "PROREL"}, {"left", "NP"}, {"leftdis", "A", "ADJ", "ADJWH"}, {"left", "AP", "I", "VPpart"}, {"leftdis", "ADV", "ADVWH"}, {"left", "AdP", "ET"}, {"leftdis", "D", "DET", "DETWH"}});

    // prepositional phrases
    nonTerminalInfo.put("PP", new String[][]{{"left", "P"}, {"left"}});

    // verbal nucleus
    nonTerminalInfo.put("VN", new String[][]{{"right", "V", "VPinf"}, {"right"}});

    // infinitive clauses
    nonTerminalInfo.put("VPinf", new String[][]{{"left", "VN"}, {"leftdis", "V", "VIMP", "VINF", "VS", "VPP", "VPR"}, {"left"}});

    // nonfinite clauses
    nonTerminalInfo.put("VPpart", new String[][]{{"leftdis", "V", "VIMP", "VINF", "VS", "VPP", "VPR"}, {"left", "VN"}, {"left"}});

    // relative clauses
    nonTerminalInfo.put("Srel", new String[][]{{"right", "VN", "AP", "NP"}, {"right"}});

    // subordinate clauses
    nonTerminalInfo.put("Ssub", new String[][]{{"right", "VN", "AP", "NP", "PP", "VPinf", "Ssub", "VPpart"}, {"rightdis", "A", "ADJ", "ADJWH"}, {"rightdis", "ADV", "ADVWH"}, {"right"}});

    // parenthetical clauses
    nonTerminalInfo.put("Sint", new String[][]{{"right", "VN", "AP", "NP", "PP", "VPinf", "Ssub", "VPpart"}, {"rightdis", "A", "ADJ", "ADJWH"}, {"rightdis", "ADV", "ADVWH"}, {"right"}});

    // adverbes
    //nonTerminalInfo.put("ADV", new String[][] {{"left", "ADV", "PP", "P"}});

    // compound categories: start with MW: D, A, C, N, ADV, V, P, PRO, CL
    nonTerminalInfo.put("MWD", new String[][] {{"leftdis", "D", "DET", "DETWH"}, {"left"}});
    nonTerminalInfo.put("MWA", new String[][] {{"left", "P"}, {"leftdis", "N", "NPP", "NC"}, {"rightdis", "A", "ADJ", "ADJWH"}, {"right"}});
    nonTerminalInfo.put("MWC", new String[][] {{"leftdis", "C", "CC", "CS"}, {"left"}});
    nonTerminalInfo.put("MWN", new String[][] {{"rightdis", "N", "NPP", "NC"}, {"rightdis", "ET"}, {"right"}});
    nonTerminalInfo.put("MWV", new String[][] {{"leftdis", "V", "VIMP", "VINF", "VS", "VPP", "VPR"}, {"left"}});
    nonTerminalInfo.put("MWP", new String[][] {{"left", "P"}, {"leftdis", "ADV", "ADVWH"}, {"leftdis", "PRO", "PROWH", "PROREL"}, {"left"}});
    nonTerminalInfo.put("MWPRO", new String[][] {{"leftdis", "PRO", "PROWH", "PROREL"}, {"leftdis", "CL", "CLS", "CLR", "CLO"}, {"leftdis", "N", "NPP", "NC"}, {"leftdis", "A", "ADJ", "ADJWH"}, {"left"}});
    nonTerminalInfo.put("MWCL", new String[][] {{"leftdis", "CL", "CLS", "CLR", "CLO"}, {"right"}});
    nonTerminalInfo.put("MWADV", new String[][] {{"left", "P"}, {"leftdis", "ADV", "ADVWH"}, {"left"}});

    nonTerminalInfo.put("MWI", new String[][] {{"leftdis", "N", "NPP", "NC"}, {"leftdis", "ADV", "ADVWH"}, {"left", "P"}, {"left"}});
    nonTerminalInfo.put("MWET", new String[][] {{"left", "ET"}, {"leftdis", "N", "NPP", "NC"}, {"left"}});

    //TODO: wsg2011: For phrasal nodes that lacked a label.
    nonTerminalInfo.put(FrenchXMLTreeReader.MISSING_PHRASAL, new String[][]{{"left"}});

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
    treebank.apply(pt -> {
      pt.percolateHeads(chf);
      pt.pennPrint();
      System.out.println();
    });
  }


}


