package edu.stanford.nlp.trees.international.spanish;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.Generics;

/**
 * @author Jon Gauthier
 */
public class SpanishHeadFinder extends AbstractCollinsHeadFinder {

  private static final long serialVersionUID = -841219428125220698L;

  private static final String[] allVerbs = new String[] {
    "vmip000", "vmii000", "vmif000", "vmis000", "vmic000",
    "vmsp000", "vmsi000",
    "vmm0000", "vmn0000", "vmg0000", "vmp0000",

    "vaip000", "vaii000", "vaif000", "vais000", "vaic000",
    "vasp000", "vasi000",
    "vam0000", "van0000", "vag0000", "vap0000",

    "vsip000", "vsii000", "vsis000", "vsif000", "vsic000",
    "vssp000", "vssi000",
    "vsm0000", "vsn0000", "vsg0000", "vsp0000"
  };

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
      {"right", "sadv", "grup.adv", "neg", "interjeccio", "i", "sp", "grup.prep"},
      insertVerbs(new String[] {"rightdis"},
        new String[] {"nc0s000", "nc0p000", "nc00000", "np00000", "rg", "rn"})};

    nonTerminalInfo.put(tlp.startSymbol(), rootRules);
    nonTerminalInfo.put("S", rootRules);
    nonTerminalInfo.put("sentence", rootRules);
    nonTerminalInfo.put("inc", rootRules);

    // adjectival phrases
    String[][] adjectivePhraseRules = new String[][] {
      {"leftdis", "grup.a", "s.a", "spec"}};
    nonTerminalInfo.put("s.a", adjectivePhraseRules);
    nonTerminalInfo.put("sa", adjectivePhraseRules);
    nonTerminalInfo.put("grup.a", new String[][] {
      {"rightdis", "aq0000", "ao0000"},
      insertVerbs(new String[] {"right"}, new String[] {}),
      {"right", "rg", "rn"}});

    // adverbial phrases
    nonTerminalInfo.put("sadv", new String[][] {{"left", "grup.adv", "sadv"}});
    nonTerminalInfo.put("grup.adv", new String[][] {
      {"left", "conj"},
      {"rightdis", "rg", "rn", "neg", "grup.adv"},
      {"rightdis", "pr000000", "pi000000", "nc0s000", "nc0p000", "nc00000", "np00000"}});
    nonTerminalInfo.put("neg", new String[][] {{"leftdis", "rg", "rn"}});

    // noun phrases
    nonTerminalInfo.put("sn", new String[][] {
      {"leftdis", "nc0s000", "nc0p000", "nc00000"},
      {"left", "grup.nom", "grup.w", "grup.z", "sn"},
      {"leftdis", "spec"}});
    nonTerminalInfo.put("grup.nom", new String[][] {
      {"leftdis", "nc0s000", "nc0p000", "nc00000", "np00000", "w", "grup.w"},
      {"leftdis", "pi000000", "pd000000"},
      {"left", "grup.nom", "sp"},
      {"leftdis", "pn000000", "aq0000", "ao0000"},
      {"left", "grup.a", "i", "grup.verb"},
      {"leftdis", "grup.adv"}});

    // verb phrases
    nonTerminalInfo.put("grup.verb", new String[][] {insertVerbs(new String[] {"left"}, new String[] {})});
    nonTerminalInfo.put("infinitiu", new String[][] {insertVerbs(new String[] {"left"}, new String[] {"infinitiu"})});
    nonTerminalInfo.put("gerundi", new String[][] {{"left", "vmg0000", "vag0000", "vsg0000", "gerundi"}});
    nonTerminalInfo.put("participi", new String[][] {{"left", "aq", "vmp0000", "vap0000", "vsp0000", "grup.a"}});

    // specifiers
    nonTerminalInfo.put("spec", new String[][] {
      {"left", "conj", "spec"}, // entre A y B
      {"leftdis", "da0000", "de0000", "di0000", "dd0000", "dp0000", "dn0000", "dt0000"},
      {"leftdis", "z0", "grup.z"},
      {"left", "rg", "rn"},
      {"leftdis", "pt000000", "pe000000", "pd000000", "pp000000", "pi000000", "pn000000", "pr000000"},
      {"left", "grup.adv", "w"}});

    // etc.
    nonTerminalInfo.put("conj", new String[][] {
      {"leftdis", "cs", "cc"},
      {"leftdis", "grup.cc", "grup.cs"},
      {"left", "sp"}});
    nonTerminalInfo.put("interjeccio", new String[][] {
      {"leftdis", "i", "nc0s000", "nc0p000", "nc00000", "np00000", "pi000000"},
      {"left", "interjeccio"}});
    nonTerminalInfo.put("relatiu", new String[][] {{"left", "pr000000"}});

    // prepositional phrases
    nonTerminalInfo.put("sp", new String[][] {{"left", "prep", "sp"}});
    nonTerminalInfo.put("prep", new String[][] {{"leftdis", "sp000", "prep", "grup.prep"}});

    // custom categories
    nonTerminalInfo.put("grup.cc", new String[][] {{"left", "cs"}});
    nonTerminalInfo.put("grup.cs", new String[][] {{"left", "cs"}});
    nonTerminalInfo.put("grup.prep", new String[][] {{"left", "prep", "grup.prep", "s"}});
    nonTerminalInfo.put("grup.pron", new String[][] {{"rightdis", "px000000"}});
    nonTerminalInfo.put("grup.w", new String[][] {{"right", "w"}, {"leftdis", "z0"}, {"left"}});
    nonTerminalInfo.put("grup.z", new String[][] {
      {"leftdis", "z0", "zu", "zp", "zd", "zm"},
      {"right", "nc0s000", "nc0p000", "nc00000", "np00000"}});
  }

  /**
   * Build a list of head rules containing all of the possible verb
   * tags. The verbs are inserted in between <tt>toLeft</tt> and
   * <tt>toRight</tt>.
   */
  private String[] insertVerbs(String[] toLeft, String[] toRight) {
    return ArrayUtils.concatenate(toLeft, ArrayUtils.concatenate(allVerbs, toRight));
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

        //pt.pennPrint();
        Tree head = pt.headTerminal(chf);
        //System.out.println("======== " + head.label());
      }
    });
  }


}
