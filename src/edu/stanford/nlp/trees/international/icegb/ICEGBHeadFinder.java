package edu.stanford.nlp.trees.international.icegb;

import edu.stanford.nlp.trees.AbstractCollinsHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;


/**
 * HeadFinder for the ICE-GB corpus.
 *
 * @author Jeanette Pettibone
 */
public class ICEGBHeadFinder extends AbstractCollinsHeadFinder {

  /**
   * 
   */
  private static final long serialVersionUID = 6799948181974741670L;

  public ICEGBHeadFinder() {
    this(new ICEGBLanguagePack());
  }

  protected Tree findMarkedHead(Tree[] kids) {
    for (int i = 0, n = kids.length; i < n; i++) {
      if (kids[i].label() instanceof ICEGBLabel && ((ICEGBLabel) kids[i].label()).function() != null && ((ICEGBLabel) kids[i].label()).function().matches(".*HD.*")) {
        return kids[i];
      }
    }
    return null;
  }

  public ICEGBHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);
    nonTerminalInfo.clear();

    // much of the time we won't need to do this much
    // this is my own doing ... I can't tgrep yet so I'm just going to
    // put in the bare minimum and see what happens
    // fill this in more when the tgrepable is ready

    //AJP comes with head info
    nonTerminalInfo.put("AJP", new String[][]{{"left", "ADJ", "AVP", "CL", "PP"}});

    //AVP comes with head info
    nonTerminalInfo.put("AVP", new String[][]{{"right", "ADV", "AVP"}});

    nonTerminalInfo.put("CL", new String[][]{{"left", "VP", "CL", "PP", "NP"}});

    /*
    nonTerminalInfo.put("CL
",
new String[][] {{"left", "VP", "CL", "PP", "NP"}});
*/

    nonTerminalInfo.put("NONCL", new String[][]{{"left", "NP"}});

    nonTerminalInfo.put("DTP", new String[][]{{"right", "ART", "PRON", "NUM", "N"}});

    nonTerminalInfo.put("NONCL", new String[][]{{"left"}});

    nonTerminalInfo.put("DISP", new String[][]{{"left", "NP", "AJP"}});

    // the Collins stuff has many different NP's and
    // each has a different direction associated with it -
    // but it should matter because NP comes with head info
    nonTerminalInfo.put("NP", new String[][]{{"right"}});

    nonTerminalInfo.put("PP", new String[][]{{"right", "PREP"}});

    nonTerminalInfo.put("PREP", new String[][]{{"left", "PREP"}});

    nonTerminalInfo.put("PREDEL", new String[][]{{"left", "PREDEL", "VP"}});

    nonTerminalInfo.put("SUBP", new String[][]{{"left", "CONJUNC"}});

    nonTerminalInfo.put("VP", new String[][]{{"left", "V"}});

    nonTerminalInfo.put("ROOT", new String[][]{{"left", "CL", "NONCL"}});
  }

}
