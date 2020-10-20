package edu.stanford.nlp.trees.international.tuebadz; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.regex.Pattern;

import edu.stanford.nlp.trees.AbstractCollinsHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

/** A HeadFinder for TueBa-D/Z.  First version.
 *  <i>Notes:</i> EN_ADD seems to be replaced by ENADD in 2008 ACL German.
 *  Added as alternant by CDM.
 *
 *  @author Roger Levy (rog@csli.stanford.edu)
 */
public class TueBaDZHeadFinder extends AbstractCollinsHeadFinder  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TueBaDZHeadFinder.class);

  private static final long serialVersionUID = 1L;

  private static final boolean DEBUG = false;

  private final String left;
  private String right;

  private boolean coordSwitch = false;


  public TueBaDZHeadFinder() {
    super(new TueBaDZLanguagePack());
    String excluded = String.valueOf(tlp.labelAnnotationIntroducingCharacters());
//    if(excluded.indexOf("-") >= 0) {
     excluded = "-" + excluded.replaceAll("-", ""); // - can only appear at the beginning of a regex character class
//    }
    headMarkedPattern = Pattern.compile("^[^" + excluded + "]*:HD");
    headMarkedPattern2 = Pattern.compile("^[^" + excluded + "]*-HD");

    nonTerminalInfo = Generics.newHashMap();

    left = (coordSwitch ? "right" : "left");
    right = (coordSwitch ? "left" : "right");
    nonTerminalInfo.put("VROOT", new String[][]{{left, "SIMPX"},{left,"NX"},{left,"P"},{left,"PX","ADVX"},{left,"EN","EN_ADD","ENADD"},{left}}); // we'll arbitrarily choose the leftmost.

    nonTerminalInfo.put("ROOT", new String[][]{{left, "SIMPX"},{left,"NX"},{left,"P"},{left,"PX","ADVX"},{left,"EN","EN_ADD","ENADD"},{left}}); // we'll arbitrarily choose the leftmost.
    nonTerminalInfo.put("TOP", new String[][]{{left, "SIMPX"},{left,"NX"},{left,"P"},{left,"PX","ADVX"},{left,"EN","EN_ADD","ENADD"},{left}}); // we'll arbitrarily choose the leftmost.  Using TOP now for ROOT

    nonTerminalInfo.put("PX", new String[][]{{left, "APPR", "APPRART","PX"}});
    nonTerminalInfo.put("NX", new String[][]{{right, "NX"},{right,"NE","NN"},{right,"EN","EN_ADD","ENADD","FX"},{right,"ADJX","PIS","ADVX"},{right,"CARD","TRUNC"},{right}});
    nonTerminalInfo.put("FX", new String[][]{{right, "FM","FX"}}); // junk rule for junk category :)
    nonTerminalInfo.put("ADJX", new String[][]{{right, "ADJX","ADJA","ADJD"},{right}});
    nonTerminalInfo.put("ADVX", new String[][]{{right, "ADVX", "ADV"}}); // what a nice category!
    nonTerminalInfo.put("DP", new String[][]{{left}}); // no need for this really
    nonTerminalInfo.put("VXFIN", new String[][]{{left,"VXFIN"},{right,"VVFIN"}}); // not sure about left vs. right
    nonTerminalInfo.put("VXINF", new String[][]{{right,"VXINF"},{right,"VVPP","VVINF"}}); // not sure about lef vs. right for this one either
    nonTerminalInfo.put("LV", new String[][]{{right}}); // no need
    nonTerminalInfo.put("C", new String[][]{{right,"KOUS"},{right,"NX"}}); // I *think* right makes more sense for this.
    nonTerminalInfo.put("FKOORD", new String[][]{{left,"LK","C"},{right,"FKONJ","MF","VC",}}); // This one is very tough right/left because it conjoins all sorts of fields together.  Not sure about the right solution
    nonTerminalInfo.put("KOORD", new String[][]{{left}}); // no need.
    nonTerminalInfo.put("LK", new String[][]{{left}}); // no need.

    // the one for MF is super-bad. MF does not designate a category
    // corresponding to headship. Really, something totally different
    // ought to be done for dependency.
    nonTerminalInfo.put("MF", new String[][]{{left}});

    nonTerminalInfo.put("MFE", new String[][]{{left}}); // no need.

    // NF is pretty bad too, like MF. But it's not nearly so horrible.
    nonTerminalInfo.put("NF", new String[][]{{left}});

    nonTerminalInfo.put("PARORD", new String[][]{{left}}); // no need.

    // not sure what's right here, but it's rare not to have a head marked.
    nonTerminalInfo.put("VC", new String[][]{{left,"VXINF"}});

    nonTerminalInfo.put("VF", new String[][]{{left,"NX","ADJX","PX","ADVX","EN","SIMPX"}}); // second dtrs are always punctuation.

    nonTerminalInfo.put("FKONJ", new String[][]{{left,"LK"},{right,"VC"},{left,"MF","NF","VF"}}); // these are basically like clauses themselves...the problem is when there's no LK or VC :(

    nonTerminalInfo.put("DM", new String[][]{{left,"PTKANT"},{left,"ITJ"},{left,"KON","FM"},{left}});

    nonTerminalInfo.put("P", new String[][]{{left,"SIMPX"},{left}}); // ***NOTE*** that this is really the P-SIMPX category, but the - will make it stripped to P.
    nonTerminalInfo.put("PSIMPX", new String[][]{{left,"SIMPX"},{left}}); // ***NOTE*** that this is really the P-SIMPX category, but the - will make it stripped to P.

    nonTerminalInfo.put("R", new String[][]{{left,"C"},{left,"R"},{right,"VC"}}); // ***NOTE*** this is really R-SIMPX.  Also: syntactic head here.  Except for the rare ones that have neither C nor R-SIMPX dtrs.
    nonTerminalInfo.put("RSIMPX", new String[][]{{left,"C"},{left,"RSIMPX"},{right,"VC"}}); // ***NOTE*** this is really R-SIMPX.  Also: syntactic head here.  Except for the rare ones that have neither C nor R-SIMPX dtrs.

    nonTerminalInfo.put("SIMPX", new String[][]{{left,"LK"},{right,"VC"},{left,"SIMPX"},{left,"C"},{right,"FKOORD"},{right,"MF"},{right}}); //  syntactic (finite verb) head here.  Note that when there's no LK or VC,the interesting predication tends to be annotated as inside the MF
    nonTerminalInfo.put("EN", new String[][]{{left, "NX"}}); // note that this node label starts as EN-ADD but the -ADD will get stripped off.
    nonTerminalInfo.put("EN_ADD", new String[][]{{left, "NX"},{left, "VXINF"}}); // just in case EN-ADD has been changed to EN_ADD
    nonTerminalInfo.put("ENADD", new String[][]{{left, "NX"},{left, "VXINF"}}); // just in case EN-ADD has been changed to EN_ADD
  }


  private final Pattern headMarkedPattern;
  private final Pattern headMarkedPattern2;

  /* Many TueBaDZ local trees have an explicitly marked head, as :HD or -HD.  (Almost!) all the time, there is only one :HD per local tree.  Use it if possible. */
   @Override
   protected Tree findMarkedHead(Tree t) {
     Tree[] kids = t.children();
     for (Tree kid : kids) {
       if (headMarkedPattern.matcher(kid.label().value()).find() || headMarkedPattern2.matcher(kid.label().value()).find()) {
         //log.info("found manually-labeled head " + kids[i] + " for tree " + t);
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
     if (tlp.isLabelAnnotationIntroducingCharacter(ch)) {
       return true;
     }
     //for heads, there's one more char we want to check because we don't care about grammatical fns
     if (ch == '-') {
       return true;
     }
     return false;
   }


   /** Called by determineHead and may be overridden in subclasses
    *  if special treatment is necessary for particular categories.
    */
   @Override
   protected Tree determineNonTrivialHead(Tree t, Tree parent) {
     Tree theHead = null;
     String motherCat = basicCategory(t.label().value());
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
