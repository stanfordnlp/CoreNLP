package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.trees.TreebankLanguagePack;


/**
 * Implements a 'semantic head' variant of the the HeadFinder found in Chinese Head Finder
 * 
 *
 * @author Pi-Chuan Chang
 * @author Huihsin Tseng
 */
public class ChineseSemanticHeadFinder extends ChineseHeadFinder {

  public ChineseSemanticHeadFinder() {
    this(new ChineseTreebankLanguagePack());
  }

  public ChineseSemanticHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);
    ruleChanges();
  }

  //makes modifications of head finder rules to better fit with semantic notions of heads
  private void ruleChanges() {
    nonTerminalInfo.remove("VP");
    nonTerminalInfo.put("VP", new String[][]{{"left", "VP", "VCD", "VPT", "VV", "VCP", "VA", "VE", "VC","IP", "VSB", "VCP", "VRD", "VNV"}, leftExceptPunct});

    nonTerminalInfo.remove("CP");
    nonTerminalInfo.put("CP", new String[][]{{"right", "CP", "IP", "VP"}, rightExceptPunct});

    nonTerminalInfo.remove("DNP");
    nonTerminalInfo.put("DNP", new String[][]{{"leftdis", "NP" }});    

    nonTerminalInfo.remove("DVP");
    nonTerminalInfo.put("DVP", new String[][]{{"leftdis", "VP" ,"ADVP"}});

    nonTerminalInfo.remove("LST");
    nonTerminalInfo.put("LST", new String[][]{{"right", "CD", "NP", "QP", "PU"}});
  }

  private static final long serialVersionUID = 2L;

}
