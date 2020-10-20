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
    // todo [pengqi 2016]: prioritizing VP over VV works in most cases, but this actually interferes
    //   with xcomps(?) like
    // (VP (VV 继续)
    //     (VP (VC 是)
    //         (NP 重要 的 国际 新闻)
    //     )
    // )
    nonTerminalInfo.put("VP", new String[][]{{"left", "VP", "VCD", "VSB", "VPT", "VV", "VCP", "VA", "VE", "IP", "VRD", "VNV", "NP"}, leftExceptPunct});

    //nonTerminalInfo.put("CP", new String[][]{{"right", "CP", "IP", "VP"}, rightExceptPunct});
    nonTerminalInfo.put("CP", new String[][]{{"rightexcept", "DEC", "WHNP", "WHPP", "SP"}, rightExceptPunct});
    nonTerminalInfo.put("DVP", new String[][]{{"leftdis", "VP" ,"ADVP"}});
    nonTerminalInfo.put("LST", new String[][]{{"right", "CD", "NP", "QP", "PU"}});

    nonTerminalInfo.put("QP", new String[][]{{"right", "QP", "CD", "OD", "NP", "NT", "M", "CLP"}}); // there's some QP adjunction

    nonTerminalInfo.put("PP", new String[][]{{"leftexcept", "P"}});  // Preposition
    nonTerminalInfo.put("LCP", new String[][]{{"leftexcept", "LC"}});  // Localizer
    nonTerminalInfo.put("DNP", new String[][]{{"rightexcept", "DEG", "DEC"}});  // Associative
  }

  private static final long serialVersionUID = 2L;

}
