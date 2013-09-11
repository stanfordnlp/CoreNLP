package edu.stanford.nlp.trees;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.trees.international.pennchinese.ChineseTreebankLanguagePack;

import java.util.HashMap;
import java.io.File;

/**
 * Head finder whose rules are read from a Malt-TAB file used for CoNLL07.
 * Standard Malt-TAB files can be downloaded from
 * http://w3.msi.vxu.se/~nivre/research/Penn2Malt.html.
 *
 * This class generates dependencies that are the same as the ones of Penn2Malt
 * in about 99.4% of the times on Sec 02-21 of PTB. The source code of Penn2Malt
 * is not available, so it makes it kind of hard to figure out differences.
 * It seems differences moslty involve punctuation.
 * 
 * @author Michel Galley
 */
public class MaltTabHeadFinder extends AbstractCollinsHeadFinder {

  /**
   * 
   */
  private static final long serialVersionUID = 3970059472026730651L;

  public enum Lang { EN_NML, EN, ZH }

  public static final String MALT_TAB_DIR = "/u/nlp/packages/Penn2Malt/";

  public static final String DEFAULT_ZH_MALT_TAB = MALT_TAB_DIR+"zh_headrules.txt";
  public static final String DEFAULT_EN_MALT_TAB = MALT_TAB_DIR+"en_headrules.txt";
  // deals with new treebank tags (NP internal tags: NML + JJP):
  public static final String DEFAULT_EN_NML_MALT_TAB = MALT_TAB_DIR+"en_headrules_nml.txt";

  public MaltTabHeadFinder() {
    this(DEFAULT_EN_NML_MALT_TAB);
  }

  public MaltTabHeadFinder(Lang l) {
    this(defaultMaltTabFile(l), tlp(l));
  }

  public MaltTabHeadFinder(String maltTabFile) {
    this(maltTabFile, new PennTreebankLanguagePack());
  }

  public MaltTabHeadFinder(String maltTabFile, TreebankLanguagePack tlp) {
    super(tlp);
    init(maltTabFile);
  }

  private void init(String maltTabFile) {
    nonTerminalInfo = new HashMap<String, String[][]>();

    for(String line : ObjectBank.getLineIterator(new File(maltTabFile))) {
      String[] cols = line.split("\\t");
      String[] els = cols[1].split(";");
      String[][] nti = new String[els.length][];
      for(int i=0; i<nti.length; ++i) {
        nti[i] = els[i].split(" +");
        nti[i][0] = nti[i][0].replaceAll("^l$","leftdis").replaceAll("^r$","rightdis");
      }
      nonTerminalInfo.put(cols[0], nti);
    }
    nonTerminalInfo.put(null, new String[][] {{"leftdis"}});
  }

  private static TreebankLanguagePack tlp(Lang l) {
    switch(l) {
      case EN: case EN_NML: return new PennTreebankLanguagePack();
      case ZH: return new ChineseTreebankLanguagePack();
      default: throw new RuntimeException("unknown language");
    }
  }

  private static String defaultMaltTabFile(Lang l) {
    switch(l) {
      case EN: return DEFAULT_EN_MALT_TAB;
      case EN_NML: return DEFAULT_EN_NML_MALT_TAB;
      case ZH: return DEFAULT_ZH_MALT_TAB;
      default: throw new RuntimeException("unknown language");
    }
  }

}
