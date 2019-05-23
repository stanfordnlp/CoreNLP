package edu.stanford.nlp.trees.international.negra; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.trees.AbstractCollinsHeadFinder;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.Generics;


/**
 * HeadFinder for the Negra Treebank.  Adapted from
 * CollinsHeadFinder.
 *
 * @author Roger Levy
 */
public class NegraHeadFinder extends AbstractCollinsHeadFinder  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(NegraHeadFinder.class);
  /**
   * 
   */
  private static final long serialVersionUID = -7253035927065152766L;
  private static final boolean DEBUG = false;

  /** Vends a "semantic" NegraHeadFinder---one that disprefers modal/auxiliary verbs as the heads of S or VP.
   * 
   * @return a NegraHeadFinder that uses a "semantic" head-finding rule for the S category. 
   */
  public static HeadFinder negraSemanticHeadFinder() {
    NegraHeadFinder result = new NegraHeadFinder();
    result.nonTerminalInfo.put("S", new String[][]{{result.right,  "VVFIN",  "VVIMP"}, {"right", "VP","CVP"}, { "right", "VMFIN", "VAFIN", "VAIMP"}, {"right", "S","CS"}}); 
    result.nonTerminalInfo.put("VP", new String[][]{{"right","VVINF","VVIZU","VVPP"}, {result.right, "VZ", "VAINF", "VMINF", "VMPP", "VAPP", "PP"}}); 
    result.nonTerminalInfo.put("VZ", new String[][]{{result.right,"VVINF","VAINF","VMINF","VVFIN","VVIZU"}}); // note that VZ < VVIZU is very rare, maybe shouldn't even exist.
    return result;
  }
  
  private boolean coordSwitch = false;

  public NegraHeadFinder() {
    this(new NegraPennLanguagePack());
  }

  String left;
  String right;
  
  public NegraHeadFinder(TreebankLanguagePack tlp) {
    super(tlp);

    nonTerminalInfo = Generics.newHashMap();

    left = (coordSwitch ? "right" : "left");
    right = (coordSwitch ? "left" : "right");

    /* BEGIN ROGER TODO */
    //
    //    // some special rule for S
    //    if(motherCat.equals("S") && kids[0].label().value().equals("PRELS"))
    //return kids[0];
    //
    nonTerminalInfo.put("S", new String[][]{{left, "PRELS"}});
    /* END ROGER TODO */

    // these are first-cut rules

    // there are non-unary nodes I put in
    nonTerminalInfo.put("NUR", new String[][]{{left, "S"}});

    // root -- yuk
    nonTerminalInfo.put("ROOT", new String[][]{{left, "S", "CS", "VP", "CVP", "NP", "XY", "CNP", "DL", "AVP", "CAVP", "PN", "AP", "PP", "CO", "NN", "NE", "CPP", "CARD", "CH"}});
    // in case a user's treebank has TOP instead of ROOT or unlabeled
    nonTerminalInfo.put("TOP", new String[][]{{left, "S", "CS", "VP", "CVP", "NP", "XY", "CNP", "DL", "AVP", "CAVP", "PN", "AP", "PP", "CO", "NN", "NE", "CPP", "CARD", "CH"}});

    // Major syntactic categories -- in order appearing in negra.export
    nonTerminalInfo.put("NP", new String[][]{{right, "NN", "NE", "MPN", "NP", "CNP", "PN", "CAR"}}); // Basic heads are NN/NE/NP; CNP is coordination; CAR is cardinal
    nonTerminalInfo.put("AP", new String[][]{{right, "ADJD", "ADJA", "CAP", "AA", "ADV"}}); // there is one ADJP unary rewrite to AD but otherwise all have JJ or ADJP
    nonTerminalInfo.put("PP", new String[][]{{left, "KOKOM", "APPR", "PROAV"}});
    //nonTerminalInfo.put("S", new String[][] {{right, "S","CS","NP"}}); //Most of the time, S has its head explicitly marked.  CS is coordinated sentence.  I don't fully understand the rest of "non-headed" german sentences to say much.
    nonTerminalInfo.put("S", new String[][]{{right, "VMFIN", "VVFIN", "VAFIN", "VVIMP", "VAIMP" }, {"right", "VP","CVP"}, {"right", "S","CS"}}); // let finite verbs (including imperatives) be head always.
    nonTerminalInfo.put("VP", new String[][]{{right, "VZ", "VAINF", "VMINF", "VVINF", "VVIZU", "VVPP", "VMPP", "VAPP", "PP"}}); // VP usually has explicit head marking; there's lots of garbage here to sort out, though.
    nonTerminalInfo.put("VZ", new String[][]{{left, "PRTZU", "APPR","PTKZU"}}); // we could also try using the verb (on the right) instead of ZU as the head, maybe this would make more sense...
    nonTerminalInfo.put("CO", new String[][]{{left}}); // this is an unlike coordination
    nonTerminalInfo.put("AVP", new String[][]{{right, "ADV", "AVP", "ADJD", "PROAV", "PP"}});
    nonTerminalInfo.put("AA", new String[][]{{right, "ADJD", "ADJA"}}); // superlative adjective phrase with "am"; I'm using the adjective not the "am" marker
    nonTerminalInfo.put("CNP", new String[][]{{right, "NN", "NE", "MPN", "NP", "CNP", "PN", "CAR"}});
    nonTerminalInfo.put("CAP", new String[][]{{right, "ADJD", "ADJA", "CAP", "AA", "ADV"}});
    nonTerminalInfo.put("CPP", new String[][]{{right, "APPR", "PROAV", "PP", "CPP"}});
    nonTerminalInfo.put("CS", new String[][]{{right, "S", "CS"}});
    nonTerminalInfo.put("CVP", new String[][]{{right, "VP", "CVP"}}); // covers all examples
    nonTerminalInfo.put("CVZ", new String[][]{{right, "VZ"}}); // covers all examples
    nonTerminalInfo.put("CAVP", new String[][]{{right, "ADV", "AVP", "ADJD", "PWAV", "APPR", "PTKVZ"}});
    nonTerminalInfo.put("MPN", new String[][]{{right, "NE", "FM", "CARD"}}); //presumably left/right doesn't matter
    nonTerminalInfo.put("NM", new String[][]{{right, "CARD", "NN"}}); // covers all examples
    nonTerminalInfo.put("CAC", new String[][]{{right, "APPR", "AVP"}}); //covers all examples
    nonTerminalInfo.put("CH", new String[][]{{right}});
    nonTerminalInfo.put("MTA", new String[][]{{right, "ADJA", "ADJD", "NN"}});
    nonTerminalInfo.put("CCP", new String[][]{{right, "AVP"}});
    nonTerminalInfo.put("DL", new String[][]{{left}}); // don't understand this one yet
    nonTerminalInfo.put("ISU", new String[][]{{right}}); // idioms, I think
    nonTerminalInfo.put("QL", new String[][]{{right}}); // these are all complicated numerical expressions I think

    nonTerminalInfo.put("--", new String[][]{{right, "PP"}}); // a garbage conjoined phrase appearing once

    // some POS tags apparently sit where phrases are supposed to be
    nonTerminalInfo.put("CD", new String[][]{{right, "CD"}});
    nonTerminalInfo.put("NN", new String[][]{{right, "NN"}});
    nonTerminalInfo.put("NR", new String[][]{{right, "NR"}});
  }

  /* Some Negra local trees have an explicitly marked head.  Use it if
  * possible. */
  protected Tree findMarkedHead(Tree[] kids) {
    for (Tree kid : kids) {
      if (kid.label() instanceof NegraLabel && ((NegraLabel) kid.label()).getEdge() != null && ((NegraLabel) kid.label()).getEdge().equals("HD")) {
        //log.info("found manually-labeled head");
        return kid;
      }
    }
    return null;
  }
  
  //Taken from AbstractTreebankLanguage pack b/c we have a slightly different definition of 
  //basic category for head finding - we strip grammatical function tags.
  public String basicCategory(String category) {
    if (category == null) {
      return null;
    }
    return category.substring(0, postBasicCategoryIndex(category));
  }
  
  private int postBasicCategoryIndex(String category) {
    boolean sawAtZero = false;
    char seenAtZero = '\u0000';
    int i = 0;
    for (int leng = category.length(); i < leng; i++) {
      char ch = category.charAt(i);
      if (isLabelAnnotationIntroducingCharacter(ch)) {
        if (i == 0) {
          sawAtZero = true;
          seenAtZero = ch;
        } else if (sawAtZero && ch == seenAtZero) {
          sawAtZero = false;
        } else {
          break;
        }
      }
    }
    return i;
  }
  
  /**
   * Say whether this character is an annotation introducing
   * character.
   *
   * @param ch The character to check
   * @return Whether it is an annotation introducing character
   */
  public boolean isLabelAnnotationIntroducingCharacter(char ch) {
    char[] cutChars = tlp.labelAnnotationIntroducingCharacters();
    for (char cutChar : cutChars) {
      if (ch == cutChar) {
        return true;
      }
    }
    //for heads, there's one more char we want to check because we don't care about grammatical fns
    if(ch == '-')
      return true;
    return false;
  }

  
  /** Called by determineHead and may be overridden in subclasses
   *  if special treatment is necessary for particular categories.
   */
  @Override
  protected Tree determineNonTrivialHead(Tree t, Tree parent) {
    Tree theHead = null;
    String motherCat = basicCategory(t.label().value());
    if (motherCat.startsWith("@")) {
      motherCat = motherCat.substring(1);
    }
    if (DEBUG) {
      log.info("Looking for head of " + t.label() +
                         "; value is |" + t.label().value() + "|, " +
                         " baseCat is |" + motherCat + "|");
    }
    // We know we have nonterminals underneath
    // (a bit of a Penn Treebank assumption, but).

    //   Look at label.
    String[][] how = nonTerminalInfo.get(motherCat);
    if (how == null) {
      if (DEBUG) {
        log.info("Warning: No rule found for " + motherCat +
                           " (first char: " + motherCat.charAt(0) + ")");
        log.info("Known nonterms are: " + nonTerminalInfo.keySet());
      }
      if (defaultRule != null) {
        if (DEBUG) {
          log.info("  Using defaultRule");
        }
        return traverseLocate(t.children(), defaultRule, true);
      } else {
        return null;
      }
    }
    for (int i = 0; i < how.length; i++) {
      boolean deflt = (i == how.length - 1);
      theHead = traverseLocate(t.children(), how[i], deflt);
      if (theHead != null) {
        break;
      }
    }
    if (DEBUG) {
      log.info("  Chose " + theHead.label());
    }
    return theHead;
  }
}
