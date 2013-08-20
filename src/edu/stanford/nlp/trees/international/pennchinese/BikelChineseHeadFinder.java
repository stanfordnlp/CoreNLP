package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.trees.AbstractCollinsHeadFinder;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;

/**
 * A headfinder implementing Dan Bikel's head rules.
 * March 2005: Updated to match the head-finding rules found in
 * Bikel's thesis (2004).
 *
 * @author Galen Andrew
 * @author Christopher Manning.
 */
public class BikelChineseHeadFinder extends AbstractCollinsHeadFinder {

  /**
   * 
   */
  private static final long serialVersionUID = -5445795668059315082L;

  public BikelChineseHeadFinder() {
    this(new ChineseTreebankLanguagePack());
  }

  public BikelChineseHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);

    nonTerminalInfo = Generics.newHashMap();
    // these are first-cut rules

    defaultRule = new String[]{"right"};

    // ROOT is not always unary for chinese -- PAIR is a special notation
    // that the Irish people use for non-unary ones....
    nonTerminalInfo.put("ROOT", new String[][]{{"left", "IP"}});
    nonTerminalInfo.put("PAIR", new String[][]{{"left", "IP"}});

    // Major syntactic categories
    nonTerminalInfo.put("ADJP", new String[][]{{"right", "ADJP", "JJ"}, {"right", "AD", "NN", "CS"}});
    nonTerminalInfo.put("ADVP", new String[][]{{"right", "ADVP", "AD"}});
    nonTerminalInfo.put("CLP", new String[][]{{"right", "CLP", "M"}});
    nonTerminalInfo.put("CP", new String[][]{{"right", "DEC", "SP"}, {"left", "ADVP", "CS"}, {"right", "CP", "IP"}});
    nonTerminalInfo.put("DNP", new String[][]{{"right", "DNP", "DEG"}, {"right", "DEC"}});
    nonTerminalInfo.put("DP", new String[][]{{"left", "DP", "DT"}});
    nonTerminalInfo.put("DVP", new String[][]{{"right", "DVP", "DEV"}});
    nonTerminalInfo.put("FRAG", new String[][]{{"right", "VV", "NR", "NN"}});
    nonTerminalInfo.put("INTJ", new String[][]{{"right", "INTJ", "IJ"}});
    nonTerminalInfo.put("IP", new String[][]{{"right", "IP", "VP"}, {"right", "VV"}});
    nonTerminalInfo.put("LCP", new String[][]{{"right", "LCP", "LC"}});
    nonTerminalInfo.put("LST", new String[][]{{"left", "LST", "CD", "OD"}});
    nonTerminalInfo.put("NP", new String[][]{{"right", "NP", "NN", "NT", "NR", "QP"}});
    nonTerminalInfo.put("PP", new String[][]{{"left", "PP", "P"}});
    nonTerminalInfo.put("PRN", new String[][]{{"right", "NP", "IP", "VP", "NT", "NR", "NN"}});
    nonTerminalInfo.put("QP", new String[][]{{"right", "QP", "CLP", "CD", "OD"}});
    nonTerminalInfo.put("UCP", new String[][]{{"right"}});
    nonTerminalInfo.put("VP", new String[][]{{"left", "VP", "VA", "VC", "VE", "VV", "BA", "LB", "VCD", "VSB", "VRD", "VNV", "VCP"}});
    nonTerminalInfo.put("VCD", new String[][]{{"right", "VCD", "VV", "VA", "VC", "VE"}});
    nonTerminalInfo.put("VCP", new String[][]{{"right", "VCP", "VV", "VA", "VC", "VE"}});
    nonTerminalInfo.put("VRD", new String[][]{{"right", "VRD", "VV", "VA", "VC", "VE"}});
    nonTerminalInfo.put("VSB", new String[][]{{"right", "VSB", "VV", "VA", "VC", "VE"}});
    nonTerminalInfo.put("VNV", new String[][]{{"right", "VNV", "VV", "VA", "VC", "VE"}});
    nonTerminalInfo.put("VPT", new String[][]{{"right", "VNV", "VV", "VA", "VC", "VE"}}); // VNV typo for VPT? None of either in ctb4.
    nonTerminalInfo.put("WHNP", new String[][]{{"right", "WHNP", "NP", "NN", "NT", "NR", "QP"}});
    nonTerminalInfo.put("WHPP", new String[][]{{"left", "WHPP", "PP", "P"}});

    // some POS tags apparently sit where phrases are supposed to be
    nonTerminalInfo.put("CD", new String[][]{{"right", "CD"}});
    nonTerminalInfo.put("NN", new String[][]{{"right", "NN"}});
    nonTerminalInfo.put("NR", new String[][]{{"right", "NR"}});
    // parsing.  It shouldn't affect anything else because heads of preterminals are not
    // generally queried - GMA
    nonTerminalInfo.put("VV", new String[][]{{"left"}});
    nonTerminalInfo.put("VA", new String[][]{{"left"}});
    nonTerminalInfo.put("VC", new String[][]{{"left"}});
    nonTerminalInfo.put("VE", new String[][]{{"left"}});
  }

}
