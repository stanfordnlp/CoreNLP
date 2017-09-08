package old.edu.stanford.nlp.trees;

/**
 * Implements a variant on the HeadFinder found in Michael Collins' 1999
 * thesis. This starts with
 * Collins' head finder. As in <code>CollinsHeadFinder.java</code>, we've
 * added a head rule for NX.
 *
 * Changes:
 * <ol>
 * <li>The PRN rule used to just take the leftmost thing, we now have it
 * choose the leftmost lexical category (not the common punctuation etc.)
 * <li>Delete IN as a possible head of S, and add FRAG (low priority)
 * <li>Place NN before QP in ADJP head rules (more to do for ADJP!)
 * <li>Place PDT before RB and after CD in QP rules.  Also prefer CD to
 * DT or RB.  And DT to RB.
 * <li>Add DT, WDT as low priority choice for head of NP. Add PRP before PRN
 * Add RBR as low priority choice of head for NP.
 * <li>Prefer NP or NX as head of NX, and otherwise default to rightmost not
 * leftmost (NP-like headedness)
 * <li>VP: add JJ and NNP as low priority heads (many tagging errors)
 *   Place JJ above NP in priority, as it is to be preferred to NP object.
 * <li>PP: add PP as a possible head (rare conjunctions)
 * <li>Added rule for POSSP (can be introduced by parser)
 * <li>Added a sensible-ish rule for X.
 * <li>Added NML head rules, which are the same as for NP.
 * <li>NP head rule: NP and NML are treated almost identically (NP has precedence)
 * <li>NAC head rule: NML comes after NN/NNS but after NNP/NNPS
 * <li>PP head rule: JJ added
 * <li>Added JJP (appearing in David Vadas's annotation), which seems to play
 * the same role as ADJP.
 * </ol>
 * These rules are suitable for the Penn Treebank.
 * <p/>
 * A case that you apparently just can't handle well in this framework is
 * (NP (NP ... NP)).  If this is a conjunction, apposition or similar, then
 * the leftmost NP is the head, but if the first is a measure phrase like
 * (NP $ 38) (NP a share) then the second should probably be the head.
 *
 * @author Christopher Manning
 * @author Michel Galley
 */
public class ModCollinsHeadFinder extends CollinsHeadFinder {

  public ModCollinsHeadFinder() {
    this(new PennTreebankLanguagePack());
  }

  public ModCollinsHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);

    // just throw away the nonTerminalInfo created by the superclass
    // ModCollins extends Collins just so it can use the same postOperationFix.
    nonTerminalInfo.clear();

    // This version from Collins' diss (1999: 236-238)
    // NNS, NN is actually sensible (money, etc.)!
    // QP early isn't; should prefer JJR NN RB
    // remove ADVP; it just shouldn't be there.
    // if two JJ, should take right one (e.g. South Korean)

    nonTerminalInfo.put("ADJP", new String[][]{{"left", "NNS", "NN", "$", "QP"}, {"right", "JJ"}, {"left", "VBN", "VBG", "ADJP", "JJP", "JJR", "NP", "JJS", "DT", "FW", "RBR", "RBS", "SBAR", "RB"}});
    nonTerminalInfo.put("JJP", new String[][]{{"left", "NNS", "NN", "$", "QP", "JJ", "VBN", "VBG", "ADJP", "JJP", "JJR", "NP", "JJS", "DT", "FW", "RBR", "RBS", "SBAR", "RB"}});
    nonTerminalInfo.put("ADVP", new String[][]{{"right", "RB", "RBR", "RBS", "FW", "ADVP", "TO", "CD", "JJR", "JJ", "IN", "NP", "NML", "JJS", "NN"}});
    nonTerminalInfo.put("CONJP", new String[][]{{"right", "CC", "RB", "IN"}});
    nonTerminalInfo.put("FRAG", new String[][]{{"right"}}); // crap
    nonTerminalInfo.put("INTJ", new String[][]{{"left"}});
    nonTerminalInfo.put("LST", new String[][]{{"right", "LS", ":"}});

    // NML is head in: (NAC-LOC (NML San Antonio) (, ,) (NNP Texas))
    // TODO: NNP should be head (rare cases, could be ignored):
    //   (NAC (NML New York) (NNP Court) (PP of Appeals))
    //   (NAC (NML Prudential Insurance) (NNP Co.) (PP Of America))
    // Chris: This could maybe still do with more thought, but NAC is rare.
    nonTerminalInfo.put("NAC", new String[][]{{"left", "NN", "NNS", "NML", "NNP", "NNPS", "NP", "NAC", "EX", "$", "CD", "QP", "PRP", "VBG", "JJ", "JJS", "JJR", "ADJP", "JJP", "FW"}});
    nonTerminalInfo.put("NX", new String[][]{{"right", "NP", "NX"}});

    // Added JJ to PP head table, since it is a head in several cases, e.g.:
    // (PP (JJ next) (PP to them))
    // When you have both JJ and IN daughters, it is invariably "such as" -- not so clear which should be head, but leave as IN
    // should prefer JJ? (PP (JJ such) (IN as) (NP (NN crocidolite)))  Michel thinks we should make JJ a head of PP
    nonTerminalInfo.put("PP", new String[][]{{"right", "IN", "TO", "VBG", "VBN", "RP", "FW", "JJ"}, {"right", "PP"}});

    nonTerminalInfo.put("PRN", new String[][]{{"left", "VP", "NP", "PP", "S", "SINV", "SBAR", "ADJP", "JJP", "ADVP", "INTJ", "WHNP", "NAC", "VBP", "JJ", "NN", "NNP"}});
    nonTerminalInfo.put("PRT", new String[][]{{"right", "RP"}});
    // add '#' for pounds!!
    nonTerminalInfo.put("QP", new String[][]{{"left", "$", "IN", "NNS", "NN", "JJ", "CD", "PDT", "DT", "RB", "NCD", "QP", "JJR", "JJS"}});
    nonTerminalInfo.put("RRC", new String[][]{{"right", "VP", "NP", "ADVP", "ADJP", "JJP", "PP"}});

    // delete IN -- go for main part of sentence; add FRAG

    nonTerminalInfo.put("S", new String[][]{{"left", "TO", "VP", "S", "FRAG", "SBAR", "ADJP", "JJP", "UCP", "NP"}});
    nonTerminalInfo.put("SBAR", new String[][]{{"left", "WHNP", "WHPP", "WHADVP", "WHADJP", "IN", "DT", "S", "SQ", "SINV", "SBAR", "FRAG"}});
    nonTerminalInfo.put("SBARQ", new String[][]{{"left", "SQ", "S", "SINV", "SBARQ", "FRAG"}});
    nonTerminalInfo.put("SINV", new String[][]{{"left", "VBZ", "VBD", "VBP", "VB", "MD", "VP", "S", "SINV", "ADJP", "JJP", "NP"}});    
    nonTerminalInfo.put("SQ", new String[][]{{"left", "VBZ", "VBD", "VBP", "VB", "MD", "AUX", "AUXG", "VP", "SQ"}});
    nonTerminalInfo.put("UCP", new String[][]{{"right"}});
    // below is weird!! Make 2 lists, one for good and one for bad heads??
    // VP: add AUX and AUXG to work with Charniak tags
    nonTerminalInfo.put("VP", new String[][]{{"left", "TO", "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "AUX", "AUXG", "ADJP", "JJP", "NN", "NNS", "JJ", "NP", "NNP"}});
    nonTerminalInfo.put("WHADJP", new String[][]{{"left", "CC", "WRB", "JJ", "ADJP", "JJP"}});
    nonTerminalInfo.put("WHADVP", new String[][]{{"right", "CC", "WRB"}});
    nonTerminalInfo.put("WHNP", new String[][]{{"left", "WDT", "WP", "WP$", "WHADJP", "WHPP", "WHNP"}});
    nonTerminalInfo.put("WHPP", new String[][]{{"right", "IN", "TO", "FW"}});
    nonTerminalInfo.put("X", new String[][]{{"right", "S", "VP", "ADJP", "JJP", "NP", "SBAR", "PP", "X"}});
    nonTerminalInfo.put("NP", new String[][]{{"rightdis", "NN", "NNP", "NNPS", "NNS", "NX", "POS", "JJR"}, {"left", "NP", "NML", "PRP"}, {"rightdis", "$", "ADJP", "JJP", "PRN"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "RB", "QP", "DT", "WDT", "RBR", "ADVP"}});
    // TODO: seems JJ should be head of NML in this case:
    // (NP (NML (JJ former) (NML Red Sox) (JJ great)) (NNP Luis) (NNP Tiant)),
    // TODO: seems JJ should be head of NML in this case:
    // (NP (NML (JJ former) (NML Red Sox) (JJ great)) (NNP Luis) (NNP Tiant)),
    nonTerminalInfo.put("NML", new String[][]{{"rightdis", "NN", "NNP", "NNPS", "NNS", "NX", "POS", "JJR"}, {"left", "NP", "NML", "PRP"}, {"rightdis", "$", "ADJP", "JJP", "PRN"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "RB", "QP", "DT", "WDT", "RBR", "ADVP"}});

    nonTerminalInfo.put("POSSP", new String[][]{{"right", "POS"}});

	/* HJT: Adding the following to deal with oddly formed data in
	 (for example) the Brown corpus */
    nonTerminalInfo.put("ROOT", new String[][]{{"left", "S", "SQ", "SINV", "SBAR", "FRAG"}});
    nonTerminalInfo.put("TYPO", new String[][]{{"left", "NN", "NP", "NML", "NNP", "NNPS", "TO",
      "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "ADJP", "JJP", "FRAG"}}); // for Brown (Roger)
    nonTerminalInfo.put("ADV", new String[][]{{"right", "RB", "RBR", "RBS", "FW",
      "ADVP", "TO", "CD", "JJR", "JJ", "IN", "NP", "NML", "JJS", "NN"}});
    nonTerminalInfo.put("EDITED", new String[][] {{"left"}});  // crap rule for Switchboard (if don't delete EDITED nodes)
    nonTerminalInfo.put("META", new String[][] {{"left"}});  // rule for OntoNotes, but maybe should just be deleted to TreeReader??
    nonTerminalInfo.put("XS", new String[][] {{"right", "IN"}}); // rule for new structure in QP

    // nonTerminalInfo.put(null, new String[][] {{"left"}});  // rule for OntoNotes from Michel, but it would be better to fix this in TreeReader or to use a default rule?
  }

  private static final long serialVersionUID = -5870387458902637256L;

}
