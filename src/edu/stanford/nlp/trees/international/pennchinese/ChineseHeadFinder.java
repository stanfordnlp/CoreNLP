package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.trees.AbstractCollinsHeadFinder;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;


/**
 * HeadFinder for the Penn Chinese Treebank.  Adapted from
 * CollinsHeadFinder. This is the version used in Levy and Manning (2003).
 *
 * @author Roger Levy
 */
public class ChineseHeadFinder extends AbstractCollinsHeadFinder {

  /**
   * If true, reverses the direction of search in VP and IP coordinations.
   * Works terribly .
   */
  private static final boolean coordSwitch = false;

  static final String[] leftExceptPunct = {"leftexcept", "PU"};
  static final String[] rightExceptPunct = {"rightexcept", "PU"};

  public ChineseHeadFinder() {
    this(new ChineseTreebankLanguagePack());
  }

  public ChineseHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);

    nonTerminalInfo = Generics.newHashMap();
    // these are first-cut rules

    String left = (coordSwitch ? "right" : "left");
    String right = (coordSwitch ? "left" : "right");
    String rightdis = "rightdis";

    defaultRule = new String[]{right};

    // ROOT is not always unary for chinese -- PAIR is a special notation
    // that the Irish people use for non-unary ones....
    nonTerminalInfo.put("ROOT", new String[][]{{left, "IP"}});
    nonTerminalInfo.put("PAIR", new String[][]{{left, "IP"}});

    // Major syntactic categories
    nonTerminalInfo.put("ADJP", new String[][]{{left, "JJ", "ADJP"}}); // there is one ADJP unary rewrite to AD but otherwise all have JJ or ADJP
    nonTerminalInfo.put("ADVP", new String[][]{{left, "AD", "CS", "ADVP", "JJ"}}); // CS is a subordinating conjunctor, and there are a couple of ADVP->JJ unary rewrites
    nonTerminalInfo.put("CLP", new String[][]{{right, "M", "CLP"}});
    //nonTerminalInfo.put("CP", new String[][] {{left, "WHNP","IP","CP","VP"}}); // this is complicated; see bracketing guide p. 34.  Actually, all WHNP are empty.  IP/CP seems to be the best semantic head; syntax would dictate DEC/ADVP. Using IP/CP/VP/M is INCREDIBLY bad for Dep parser - lose 3% absolute.
    nonTerminalInfo.put("CP", new String[][]{{right, "DEC", "WHNP", "WHPP"}, rightExceptPunct}); // the (syntax-oriented) right-first head rule
    // nonTerminalInfo.put("CP", new String[][]{{right, "DEC", "ADVP", "CP", "IP", "VP", "M"}}); // the (syntax-oriented) right-first head rule
    nonTerminalInfo.put("DNP", new String[][]{{right, "DEG", "DEC"}, rightExceptPunct}); // according to tgrep2, first preparation, all DNPs have a DEG daughter
    nonTerminalInfo.put("DP", new String[][]{{left, "DT", "DP"}}); // there's one instance of DP adjunction
    nonTerminalInfo.put("DVP", new String[][]{{right, "DEV", "DEC"}}); // DVP always has DEV under it
    nonTerminalInfo.put("FRAG", new String[][]{{right, "VV", "NN"}, rightExceptPunct}); //FRAG seems only to be used for bits at the beginnings of articles: "Xinwenshe<DATE>" and "(wan)"
    nonTerminalInfo.put("IP", new String[][]{{left, "VP", "IP"}, rightExceptPunct});  // CDM July 2010 following email from Pi-Chuan changed preference to VP over IP: IP can be -SBJ, -OBJ, or -ADV, and shouldn't be head
    nonTerminalInfo.put("LCP", new String[][]{{right, "LC", "LCP"}}); // there's a bit of LCP adjunction
    nonTerminalInfo.put("LST", new String[][]{{right, "CD", "PU"}}); // covers all examples
    nonTerminalInfo.put("NP", new String[][]{{right, "NN", "NR", "NT", "NP", "PN", "CP"}}); // Basic heads are NN/NR/NT/NP; PN is pronoun.  Some NPs are nominalized relative clauses without overt nominal material; these are NP->CP unary rewrites.  Finally, note that this doesn't give any special treatment of coordination.
    nonTerminalInfo.put("PP", new String[][]{{left, "P", "PP"}}); // in the manual there's an example of VV heading PP but I couldn't find such an example with tgrep2
    // cdm 2006: PRN changed to not choose punctuation.  Helped parsing (if not significantly)
    // nonTerminalInfo.put("PRN", new String[][]{{left, "PU"}}); //presumably left/right doesn't matter
    nonTerminalInfo.put("PRN", new String[][]{{left, "NP", "VP", "IP", "QP", "PP", "ADJP", "CLP", "LCP"}, {rightdis, "NN", "NR", "NT", "FW"}});
    // cdm 2006: QP: add OD -- occurs some; occasionally NP, NT, M; parsing performance no-op
    nonTerminalInfo.put("QP", new String[][]{{right, "QP", "CLP", "CD", "OD", "NP", "NT", "M"}}); // there's some QP adjunction
    // add OD?
    nonTerminalInfo.put("UCP", new String[][]{{left, }}); //an alternative would be "PU","CC"
    nonTerminalInfo.put("VP", new String[][]{{left, "VP", "VCD", "VPT", "VV", "VCP", "VA", "VC", "VE", "IP", "VSB", "VCP", "VRD", "VNV"}, leftExceptPunct}); //note that ba and long bei introduce IP-OBJ small clauses; short bei introduces VP
    // add BA, LB, as needed

    // verb compounds
    nonTerminalInfo.put("VCD", new String[][]{{left, "VCD", "VV", "VA", "VC", "VE"}}); // could easily be right instead
    nonTerminalInfo.put("VCP", new String[][]{{left, "VCD", "VV", "VA", "VC", "VE"}}); // not much info from documentation
    nonTerminalInfo.put("VRD", new String[][]{{left, "VCD", "VRD", "VV", "VA", "VC", "VE"}}); // definitely left
    nonTerminalInfo.put("VSB", new String[][]{{right, "VCD", "VSB", "VV", "VA", "VC", "VE"}}); // definitely right, though some examples look questionably classified (na2lai2 zhi1fu4)
    nonTerminalInfo.put("VNV", new String[][]{{left, "VV", "VA", "VC", "VE"}}); // left/right doesn't matter
    nonTerminalInfo.put("VPT", new String[][]{{left, "VV", "VA", "VC", "VE"}}); // activity verb is to the left

    // some POS tags apparently sit where phrases are supposed to be
    nonTerminalInfo.put("CD", new String[][]{{right, "CD"}});
    nonTerminalInfo.put("NN", new String[][]{{right, "NN"}});
    nonTerminalInfo.put("NR", new String[][]{{right, "NR"}});

    // I'm adding these POS tags to do primitive morphology for character-level
    // parsing.  It shouldn't affect anything else because heads of preterminals are not
    // generally queried - GMA
    nonTerminalInfo.put("VV", new String[][]{{left}});
    nonTerminalInfo.put("VA", new String[][]{{left}});
    nonTerminalInfo.put("VC", new String[][]{{left}});
    nonTerminalInfo.put("VE", new String[][]{{left}});

    // new for ctb6.
    nonTerminalInfo.put("FLR", new String[][]{rightExceptPunct});

    // new for CTB9
    nonTerminalInfo.put("DFL", new String[][]{rightExceptPunct});
    nonTerminalInfo.put("EMO", new String[][]{leftExceptPunct}); // left/right doesn't matter
    nonTerminalInfo.put("INC", new String[][]{leftExceptPunct});
    // old version suitable for v5.1 ... does not cover "我的天 哪" for example
    // nonTerminalInfo.put("INTJ", new String[][]{{right, "INTJ", "IJ", "SP"}});
    nonTerminalInfo.put("INTJ", new String[][]{leftExceptPunct}); 
    nonTerminalInfo.put("OTH", new String[][]{leftExceptPunct}); 
    nonTerminalInfo.put("SKIP", new String[][]{leftExceptPunct}); 

  }

  private static final long serialVersionUID = 6143632784691159283L;

}
