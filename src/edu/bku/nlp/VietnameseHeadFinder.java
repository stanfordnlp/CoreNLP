package edu.bku.nlp;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.trees.*;

public class VietnameseHeadFinder extends CollinsHeadFinder {

  public VietnameseHeadFinder() {
    this(new VietnamesePennTreebankLanguagePack());
  }

  public VietnameseHeadFinder(TreebankLanguagePack tlp) {
    super(tlp, tlp.punctuationTags()); // avoid punctuation as head in final default rule

    nonTerminalInfo = Generics.newHashMap();
    /******************************************************************
     ******************* English Rule from ModCollinsHeadFinder *******
     ******************************************************************
     */
    // This version from Collins' diss (1999: 236-238)
    // NNS, NN is actually sensible (money, etc.)!
    // QP early isn't; should prefer JJR NN RB
    // remove ADVP; it just shouldn't be there.
    // if two JJ, should take right one (e.g. South Korean)
    // nonTerminalInfo.put("ADJP", new String[][]{{"left", "NNS", "NN", "$", "QP"}, {"right", "JJ"}, {"left", "VBN", "VBG", "ADJP", "JJP", "JJR", "NP", "JJS", "DT", "FW", "RBR", "RBS", "SBAR", "RB"}});
    nonTerminalInfo.put("ADJP", new String[][]{{"left", "$"}, {"rightdis", "NNS", "NN", "JJ", "QP", "VBN", "VBG"}, {"left", "ADJP"}, {"rightdis", "JJP", "JJR", "JJS", "DT", "RB", "RBR", "CD", "IN", "VBD"}, {"left", "ADVP", "NP"}});
    nonTerminalInfo.put("JJP", new String[][]{{"left", "NNS", "NN", "$", "QP", "JJ", "VBN", "VBG", "ADJP", "JJP", "JJR", "NP", "JJS", "DT", "FW", "RBR", "RBS", "SBAR", "RB"}});  // JJP is introduced for NML-like adjective phrases in Vadas' treebank; Chris wishes he hadn't used JJP which should be a POS-tag.
    // ADVP rule rewritten by Chris in Nov 2010 to be rightdis.  This is right! JJ.* is often head and rightmost.
    nonTerminalInfo.put("ADVP", new String[][]{{"left", "ADVP", "IN"},
                                               {"rightdis", "RB", "RBR", "RBS", "JJ", "JJR", "JJS"},
                                               {"rightdis", "RP", "DT", "NN", "CD", "NP", "VBN", "NNP", "CC", "FW", "NNS", "ADJP", "NML"}});
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

    // Added JJ to PP head table, since it is a head in several cases, e.g.:
    // (PP (JJ next) (PP to them))
    // When you have both JJ and IN daughters, it is invariably "such as" -- not so clear which should be head, but leave as IN
    // should prefer JJ? (PP (JJ such) (IN as) (NP (NN crocidolite)))  Michel thinks we should make JJ a head of PP
    // added SYM as used in new treebanks for symbols filling role of IN
    // Changed PP search to left -- just what you want for conjunction (and consistent with SemanticHeadFinder)
    nonTerminalInfo.put("PP", new String[][]{{"right", "IN", "TO", "VBG", "VBN", "RP", "FW", "JJ", "SYM"}, {"left", "PP"}});

    nonTerminalInfo.put("PRN", new String[][]{{"left", "VP", "NP", "PP", "SQ", "S", "SINV", "SBAR", "ADJP", "JJP", "ADVP", "INTJ", "WHNP", "NAC", "VBP", "JJ", "NN", "NNP"}});
    nonTerminalInfo.put("PRT", new String[][]{{"right", "RP"}});
    // add '#' for pounds!!
    nonTerminalInfo.put("QP", new String[][]{{"left", "$", "IN", "NNS", "NN", "JJ", "CD", "PDT", "DT", "RB", "NCD", "QP", "JJR", "JJS"}});
    // reduced relative clause can be any predicate VP, ADJP, NP, PP.
    // For choosing between NP and PP, really need to know which one is temporal and to choose the other.
    // It's not clear ADVP needs to be in the list at all (delete?).
    nonTerminalInfo.put("RRC", new String[][]{{"left", "RRC"}, {"right", "VP", "ADJP", "JJP", "NP", "PP", "ADVP"}});

    // delete IN -- go for main part of sentence; add FRAG

    nonTerminalInfo.put("S", new String[][]{{"left", "TO", "VP", "S", "FRAG", "SBAR", "ADJP", "JJP", "UCP", "NP"}});
    nonTerminalInfo.put("SBAR", new String[][]{{"left", "WHNP", "WHPP", "WHADVP", "WHADJP", "IN", "DT", "S", "SQ", "SINV", "SBAR", "FRAG"}});
    nonTerminalInfo.put("SBARQ", new String[][]{{"left", "SQ", "S", "SINV", "SBARQ", "FRAG", "SBAR"}});
    // cdm: if you have 2 VP under an SINV, you should really take the 2nd as syntactic head, because the first is a topicalized VP complement of the second, but for now I didn't change this, since it didn't help parsing.  (If it were changed, it'd need to be also changed to the opposite in SemanticHeadFinder.)
    nonTerminalInfo.put("SINV", new String[][]{{"left", "VBZ", "VBD", "VBP", "VB", "MD", "VBN", "VP", "S", "SINV", "ADJP", "JJP", "NP"}});
    nonTerminalInfo.put("SQ", new String[][]{{"left", "VBZ", "VBD", "VBP", "VB", "MD", "AUX", "AUXG", "VP", "SQ"}});  // TODO: Should maybe put S before SQ for tag questions. Check.
    nonTerminalInfo.put("UCP", new String[][]{{"right"}});
    // below is weird!! Make 2 lists, one for good and one for bad heads??
    // VP: added AUX and AUXG to work with Charniak tags
    nonTerminalInfo.put("VP", new String[][]{{"left", "TO", "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "AUX", "AUXG", "ADJP", "JJP", "NN", "NNS", "JJ", "NP", "NNP"}});
    nonTerminalInfo.put("WHADJP", new String[][]{{"left", "WRB", "WHADVP", "RB", "JJ", "ADJP", "JJP", "JJR"}});
    nonTerminalInfo.put("WHADVP", new String[][]{{"right", "WRB", "WHADVP"}});
    nonTerminalInfo.put("WHNP", new String[][]{{"left", "WDT", "WP", "WP$", "WHADJP", "WHPP", "WHNP"}});
    nonTerminalInfo.put("WHPP", new String[][]{{"right", "IN", "TO", "FW"}});
    nonTerminalInfo.put("X", new String[][]{{"right", "S", "VP", "ADJP", "JJP", "NP", "SBAR", "PP", "X"}});
    nonTerminalInfo.put("NP", new String[][]{{"rightdis", "NN", "NNP", "NNPS", "NNS", "NML", "NX", "POS", "JJR"}, {"left", "NP", "PRP"}, {"rightdis", "$", "ADJP", "JJP", "PRN", "FW"}, {"right", "CD"}, {"rightdis", "JJ", "JJS", "RB", "QP", "DT", "WDT", "RBR", "ADVP"}});
    nonTerminalInfo.put("NX", nonTerminalInfo.get("NP"));
    // TODO: seems JJ should be head of NML in this case:
    // (NP (NML (JJ former) (NML Red Sox) (JJ great)) (NNP Luis) (NNP Tiant)),
    // (although JJ great is tagged wrong)
    nonTerminalInfo.put("NML", nonTerminalInfo.get("NP"));


    nonTerminalInfo.put("POSSP", new String[][]{{"right", "POS"}});

    /* HJT: Adding the following to deal with oddly formed data in (for example) the Brown corpus */
    nonTerminalInfo.put("ROOT", new String[][]{{"left", "S", "SQ", "SINV", "SBAR", "FRAG"}});
    // Just to handle trees which have TOP instead of ROOT at the root
    nonTerminalInfo.put("TOP", nonTerminalInfo.get("ROOT"));
    nonTerminalInfo.put("TYPO", new String[][]{{"left", "NN", "NP", "NML", "NNP", "NNPS", "TO",
      "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "ADJP", "JJP", "FRAG"}}); // for Brown (Roger)
    nonTerminalInfo.put("ADV", new String[][]{{"right", "RB", "RBR", "RBS", "FW",
      "ADVP", "TO", "CD", "JJR", "JJ", "IN", "NP", "NML", "JJS", "NN"}});

    // SWBD
    nonTerminalInfo.put("EDITED", new String[][] {{"left"}});  // crap rule for Switchboard (if don't delete EDITED nodes)
    // in sw2756, a "VB". (copy "VP" to handle this problem, though should really fix it on reading)
    nonTerminalInfo.put("VB", new String[][]{{"left", "TO", "VBD", "VBN", "MD", "VBZ", "VB", "VBG", "VBP", "VP", "AUX", "AUXG", "ADJP", "JJP", "NN", "NNS", "JJ", "NP", "NNP"}});

    nonTerminalInfo.put("META", new String[][] {{"left"}});  // rule for OntoNotes, but maybe should just be deleted in TreeReader??
    nonTerminalInfo.put("XS", new String[][] {{"right", "IN"}}); // rule for new structure in QP, introduced by Stanford in QPTreeTransformer
    // nonTerminalInfo.put(null, new String[][] {{"left"}});  // rule for OntoNotes from Michel, but it would be better to fix this in TreeReader or to use a default rule?

    // todo: Uncomment this line if we always want to take the leftmost if no head rule is defined for the mother category.
    // defaultRule = defaultLeftRule; // Don't exception, take leftmost if no rule defined for a certain parent category
    
    /******************************************************************
     ******************* Vietnamese Rule ******************************
     ******************************************************************
     */
    
    nonTerminalInfo.put("AP", new String[][]{{"left", "A"}});
    nonTerminalInfo.put("MDP", new String[][]{{"left", "T"}});
    nonTerminalInfo.put("X", new String[][]{{"left"}});
    nonTerminalInfo.put("XP", new String[][]{{"left", "X"}}); // There is no rule XP, the line keep parser work
    nonTerminalInfo.put("WHRP", new String[][]{{"right", "X"}});
    nonTerminalInfo.put("WHAP", new String[][]{{"right", "NP"}});
    nonTerminalInfo.put("WPNP", new String[][]{{"right", "N"}});
    nonTerminalInfo.put("WHADV", new String[][]{{"left", "N"}});
    nonTerminalInfo.put("WHVP", new String[][]{{"left", "V"}});
    nonTerminalInfo.put("RP", new String[][]{{"left"}});
    nonTerminalInfo.put("MP", new String[][]{{"left", "M"}});
    nonTerminalInfo.put("UCP", new String[][]{{"left"}});
    
  
  }

  private static final long serialVersionUID = -5970387458902637256L;

}
