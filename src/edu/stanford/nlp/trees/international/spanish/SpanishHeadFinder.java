package edu.stanford.nlp.trees.international.spanish;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Generics;

/**
 * Based on {@link DybroFrenchHeadFinder}.
 *
 * @author Jon Gauthier
 */
public class SpanishHeadFinder extends AbstractCollinsHeadFinder {

  private static final long serialVersionUID = 7710457835992590620L;

  public SpanishHeadFinder() {
    this(new SpanishTreebankLanguagePack());
  }


  public SpanishHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);

    nonTerminalInfo = Generics.newHashMap();

    // "sentence"
    String[][] rootRules = new String[][] {
      {"right", "grup.verb", "s.a", "sn"},
      {"left", "S"},
      {"right", "grup.adv", "i", "grup.prep"},
      {"rightdis", "vm", "n", "r"},
      {"right"}};
    nonTerminalInfo.put(tlp.startSymbol(), rootRules);
    nonTerminalInfo.put("S", rootRules);
    nonTerminalInfo.put("sentence", rootRules);
    nonTerminalInfo.put("inc", rootRules);

    // adjectival phrases
    String[][] adjectivePhraseRules = new String[][] {
      {"leftdis", "grup.a", "s.a", "spec"},
      {"right"}};
    nonTerminalInfo.put("s.a", adjectivePhraseRules);
    nonTerminalInfo.put("sa", adjectivePhraseRules);
    nonTerminalInfo.put("grup.a", new String[][] {
        {"right", "a"},
        {"right", "vm"},
        {"right", "r"}});

    // adverbial phrases
    nonTerminalInfo.put("sadv", new String[][] {{"left", "grup.adv"}});
    nonTerminalInfo.put("grup.adv", new String[][] {{"right", "r"}});
    nonTerminalInfo.put("neg", new String[][] {{"left", "r"}});

    // // coordinated phrases
    // nonTerminalInfo.put("COORD", new String[][]{{"leftdis", "C", "CC", "CS"}, {"left"}});

    // noun phrases
    nonTerminalInfo.put("sn", new String[][] {{"left", "grup.nom", "grup.w", "grup.z", "sn"}});
    nonTerminalInfo.put("grup.nom", new String[][] {
        {"left", "n", "p", "grup.nom"},
        {"leftdis", "a"},
        {"left", "grup.a", "i", "grup.verb"},
        {"leftdis", "grup.adv", "d"}});

    // verb phrases
    String[][] verbRules = new String[][] {{"right", "vm", "va", "vs", "vg"}};
    nonTerminalInfo.put("grup.verb", verbRules);
    nonTerminalInfo.put("infinitiu", new String[][] {{"left", "vm", "va", "vs", "infinitiu"}});
    nonTerminalInfo.put("gerundi", new String[][] {{"left", "vg", "gerundi"}});
    nonTerminalInfo.put("participi", new String[][] {{"left", "aq", "vm", "grup.a"}});

    // specifiers
    nonTerminalInfo.put("spec", new String[][] {
        {"left", "d", "p", "z", "grup.z", "w", "grup.w", "r", "grup.adv", "grup.prep", "spec", "conj", "n"}});

    // etc.
    nonTerminalInfo.put("conj", new String[][] {{"leftdis", "c", "s", "grup.cc"}});
    nonTerminalInfo.put("interjeccio", new String[][] {{"left", "i", "grup.i"}});
    nonTerminalInfo.put("relatiu", new String[][] {{"left", "p"}});

    // prepositional phrases
    nonTerminalInfo.put("sp", new String[][] {{"left", "prep"}});
    nonTerminalInfo.put("prep", new String[][] {{"leftdis", "s", "prep", "grup.prep"}});

    // custom categories
    nonTerminalInfo.put("grup.cc", new String[][] {{"left", "c", "s"}});
    nonTerminalInfo.put("grup.cs", new String[][] {{"left", "cs"}}); // TODO ?? "de forma que," "ya que"
    nonTerminalInfo.put("grup.i", new String[][] {{"left", "i", "n", "a", "p", "d", "v"}});
    nonTerminalInfo.put("grup.prep", new String[][] {{"left", "prep", "grup.prep", "s"}});
    nonTerminalInfo.put("grup.pron", new String[][] {{"right", "d", "p"}});
    nonTerminalInfo.put("grup.w", new String[][] {{"right", "w"}, {"left", "z"}, {"left"}});
    nonTerminalInfo.put("grup.z", new String[][] {{"left", "z"}, {"right", "n", "s"}, {"left"}});
  }


  /**
   * Go through trees and determine their heads and print them.
   * Just for debugging. <br>
   * Usage: <code>
   * java edu.stanford.nlp.trees.international.spanish.SpanishHeadFinder treebankFilePath
   * </code>
   *
   * @param args The treebankFilePath
   */
  public static void main(String[] args) {
    Treebank treebank = new DiskTreebank();
    CategoryWordTag.suppressTerminalDetails = true;
    treebank.loadPath(args[0]);
    final HeadFinder chf = new SpanishHeadFinder();
    treebank.apply(new TreeVisitor() {
      public void visitTree(Tree pt) {
        // pt.percolateHeads(chf);

        pt.pennPrint();
        Tree head = pt.headTerminal(chf);
        System.out.println("======== " + head.label());
      }
    });
  }


}
