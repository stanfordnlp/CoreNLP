package edu.stanford.nlp.trees;

/**
 * like ModCollinsHeadFinder but with NER tags acting like NNP tags.
 * This was written for use in Coref.
 *
 * @author Jenny Finkel
 */
public class NERModCollinsHeadFinder extends ModCollinsHeadFinder {

  public NERModCollinsHeadFinder() {
    this(new PennTreebankLanguagePack());
  }

  public NERModCollinsHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);

    // This version from Collins' diss (1999: 236-238)
    // NNS, NN is actually sensible (money, etc.)!
    // QP early isn't; should prefer JJR NN RB
    // remove ADVP; it just shouldn't be there.
    // NOT DONE: if two JJ, should take right one (e.g. South Korean)

    nonTerminalInfo.put("NAC", new String[][]{{"left", "NN", "NNS", "PERSON", "ORGANIZATION", "LOCATION", "NNP", "NNPS", "NP", "NAC", "EX", "$", "CD", "QP", "PRP", "VBG", "JJ", "JJS", "JJR", "ADJP", "FW"}});
    nonTerminalInfo.put("PRN", new String[][]{{"left", "VP", "NP", "PP", "S", "SINV", "SBAR", "ADJP", "ADVP", "INTJ", "WHNP", "NAC", "VBP", "JJ", "NN", "PERSON", "ORGANIZATION", "LOCATION", "NNP"}});

    nonTerminalInfo.put("VP", new String[][]{{"left", "TO", "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "ADJP", "NN", "NNS", "JJ", "NP", "PERSON", "ORGANIZATION", "LOCATION", "NNP"}});
    nonTerminalInfo.put("NP", new String[][]{{"rightdis", "NN", "PERSON", "ORGANIZATION", "LOCATION", "NNP", "NNPS", "NNS", "NX", "POS", "JJR"}, {"left", "NP", "PRP"}, {"rightdis", "$", "ADJP", "PRN"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "RB", "QP", "DT", "WDT", "RBR", "ADVP"}});
  }

  private static final long serialVersionUID = -5870387458902637256L;

}
