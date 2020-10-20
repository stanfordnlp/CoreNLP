package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.trees.AbstractCollinsHeadFinder;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;

/**
 * A HeadFinder for Chinese based on rules described in Sun/Jurafsky NAACL 2004.
 *
 * @author Galen Andrew
 * @version Jul 12, 2004
 */
public class SunJurafskyChineseHeadFinder extends AbstractCollinsHeadFinder {

  private static final long serialVersionUID = -7942375587642755210L;

  public SunJurafskyChineseHeadFinder() {
    this(new ChineseTreebankLanguagePack());
  }

  public SunJurafskyChineseHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);

    defaultRule = new String[]{"right"};

    nonTerminalInfo = Generics.newHashMap();

    nonTerminalInfo.put("ROOT", new String[][]{{"left", "IP"}});
    nonTerminalInfo.put("PAIR", new String[][]{{"left", "IP"}});

    nonTerminalInfo.put("ADJP", new String[][]{{"right", "ADJP", "JJ", "AD"}});
    nonTerminalInfo.put("ADVP", new String[][]{{"right", "ADVP", "AD", "CS", "JJ", "NP", "PP", "P", "VA", "VV"}});
    nonTerminalInfo.put("CLP", new String[][]{{"right", "CLP", "M", "NN", "NP"}});
    nonTerminalInfo.put("CP", new String[][]{{"right", "CP", "IP", "VP"}});
    nonTerminalInfo.put("DNP", new String[][]{{"right", "DEG", "DNP", "DEC", "QP"}});
    nonTerminalInfo.put("DP", new String[][]{{"left", "M", "DP", "DT", "OD"}});
    nonTerminalInfo.put("DVP", new String[][]{{"right", "DEV", "AD", "VP"}});
    nonTerminalInfo.put("IP", new String[][]{{"right", "VP", "IP", "NP"}});
    nonTerminalInfo.put("LCP", new String[][]{{"right", "LCP", "LC"}});
    nonTerminalInfo.put("LST", new String[][]{{"right", "CD", "NP", "QP"}});
    nonTerminalInfo.put("NP", new String[][]{{"right", "NP", "NN", "IP", "NR", "NT"}});
    nonTerminalInfo.put("PP", new String[][]{{"left", "P", "PP"}});
    nonTerminalInfo.put("PRN", new String[][]{{"left", "PU"}});
    nonTerminalInfo.put("QP", new String[][]{{"right", "QP", "CLP", "CD"}});
    nonTerminalInfo.put("UCP", new String[][]{{"left", "IP", "NP", "VP"}});
    nonTerminalInfo.put("VCD", new String[][]{{"left", "VV", "VA", "VE"}});
    nonTerminalInfo.put("VP", new String[][]{{"left", "VE", "VC", "VV", "VNV", "VPT", "VRD", "VSB", "VCD", "VP"}});
    nonTerminalInfo.put("VPT", new String[][]{{"left", "VA", "VV"}});
    nonTerminalInfo.put("VCP", new String[][]{{"left"}});
    nonTerminalInfo.put("VNV", new String[][]{{"left"}});
    nonTerminalInfo.put("VRD", new String[][]{{"left", "VV", "VA"}});
    nonTerminalInfo.put("VSB", new String[][]{{"right", "VV", "VE"}});
    nonTerminalInfo.put("FRAG", new String[][]{{"right", "VV", "NN"}}); //FRAG seems only to be used for bits at the beginnings of articles: "Xinwenshe<DATE>" and "(wan)"

    // some POS tags apparently sit where phrases are supposed to be
    nonTerminalInfo.put("CD", new String[][]{{"right", "CD"}});
    nonTerminalInfo.put("NN", new String[][]{{"right", "NN"}});
    nonTerminalInfo.put("NR", new String[][]{{"right", "NR"}});

    // I'm adding these POS tags to do primitive morphology for character-level
    // parsing.  It shouldn't affect anything else because heads of preterminals are not
    // generally queried - GMA
    nonTerminalInfo.put("VV", new String[][]{{"left"}});
    nonTerminalInfo.put("VA", new String[][]{{"left"}});
    nonTerminalInfo.put("VC", new String[][]{{"left"}});
    nonTerminalInfo.put("VE", new String[][]{{"left"}});
  }

  /* Yue Zhang and Stephen Clark 2008 based their rules on Sun/Jurafsky but changed a few things.
  Constituent Rules
  ADJP r ADJP JJ AD; r
  ADVP r ADVP AD CS JJ NP PP P VA VV; r
  CLP r CLP M NN NP; r
  CP r CP IP VP; r
  DNP r DEG DNP DEC QP; r
  DP r M; l DP DT OD; l
  DVP r DEV AD VP; r
  FRAG r VV NR NN NT; r
  IP r VP IP NP; r
  LCP r LCP LC; r
  LST r CD NP QP; r
  NP r NP NN IP NR NT; r
  NN r NP NN IP NR NT; r
  PP l P PP; l
  PRN l PU; l
  QP r QP CLP CD; r
  UCP l IP NP VP; l
  VCD l VV VA VE; l
  VP l VE VC VV VNV VPT VRD VSB
  VCD VP; l
  VPT l VA VV; l
  VRD l VVI VA; l
  VSB r VV VE; r
  default r
  */

}
