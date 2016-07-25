package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.trees.TreebankLanguagePack;


/**
 * Implements a 'semantic head' variant of the the HeadFinder found in Chinese Head Finder.
 *
 * @author Pi-Chuan Chang
 * @author Huihsin Tseng
 * @author Percy Liang
 */
public class UniversalChineseSemanticHeadFinder extends ChineseHeadFinder {

  public UniversalChineseSemanticHeadFinder() {
    this(new ChineseTreebankLanguagePack());
  }

  public UniversalChineseSemanticHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);
    ruleChanges();
  }

  /** Makes modifications of head finder rules to better fit with semantic notions of heads. */
  private void ruleChanges() {
    // Note: removed VC and added NP; copula should not be the head.
    nonTerminalInfo.put("VP", new String[][]{{"left", "VP", "VCD", "VSB", "VPT", "VV", "VCP", "VA", "VE", "IP", "VRD", "VNV", "NP"}, leftExceptPunct});

    //nonTerminalInfo.put("CP", new String[][]{{"right", "CP", "IP", "VP"}, rightExceptPunct});
    nonTerminalInfo.put("CP", new String[][]{{"rightexcept", "DEC", "WHNP", "WHPP"}, rightExceptPunct});
    nonTerminalInfo.put("DVP", new String[][]{{"leftdis", "VP" ,"ADVP"}});
    nonTerminalInfo.put("LST", new String[][]{{"right", "CD", "NP", "QP", "PU"}});

    nonTerminalInfo.put("PP", new String[][]{{"leftexcept", "P"}});  // Preposition
    nonTerminalInfo.put("LCP", new String[][]{{"leftexcept", "LC"}});  // Localizer
    nonTerminalInfo.put("DNP", new String[][]{{"leftexcept", "DEG"}});  // Associative
  }

  private static final long serialVersionUID = 2L;

}
