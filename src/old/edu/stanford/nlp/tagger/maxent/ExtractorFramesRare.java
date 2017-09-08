// ExtractorFramesRare -- StanfordMaxEnt, A Maximum Entropy Toolkit
// Copyright (c) 2002-2008 The Board of Trustees of
// Leland Stanford Junior University. All rights reserved.

//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml

package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.process.WordShapeClassifier;
import old.edu.stanford.nlp.util.StringUtils;

import java.util.*;


/**
 * Provides arrays of ExtractorFrames for rare words.  This file
 * also defines all the rare word extractors as non-public classes.
 * This file is used simply as a resource to initialize extractors stored
 * in Extractors class instances.
 *
 * @author Kristina Toutanova
 * @author Christopher Manning
 * @author Michel Galley
 * @version 2.0
 */
public class ExtractorFramesRare {

  /**
   * Last 1-4 characters of word
   */
  private static final Extractor cWordSuff1 = new ExtractorCWordSuff(1);
  private static final Extractor cWordSuff2 = new ExtractorCWordSuff(2);
  private static final Extractor cWordSuff3 = new ExtractorCWordSuff(3);
  private static final Extractor cWordSuff4 = new ExtractorCWordSuff(4);

  /**
   * "1" iff word contains 1 or more upper case characters (somewhere)
   */
  private static final Extractor cWordUppCase = new ExtractorUCase();

  /**
   * "1" iff word contains 1 or more digit characters (somewhere)
   */
  private static final Extractor cWordNumber = new ExtractorCNumber();

  /**
   * "1" iff word contains 1 or more dash characters (somewhere)
   */
  private static final Extractor cWordDash = new ExtractorDash();

  /**
   * "0" if not 1st word of sentence or not upper case, or lowercased version
   * not in dictionary.  Else first tag of word lowercased.
   */
  private static final Extractor cWordStartUCase = new ExtractorStartSentenceCap();

  /**
   * "0" if first word of sentence or not first letter uppercase or if
   * lowercase version isn't in dictionary.  Otherwise first tag of lowercase
   * equivalent.
   */
  private static final Extractor cWordMidUCase = new ExtractorMidSentenceCapC();

  /**
   * "1" if not first word of sentence and _some_ letter is uppercase
   */
  private static final Extractor cMidSentence = new ExtractorMidSentenceCap();

  /**
   * "1" if token has no lower case letters
   */
  private static final Extractor cAllCap = new ExtractorAllCap();

  /**
   * "1" if token has only upper case letters
   */
  private static final Extractor cAllCapitalized = new ExtractorAllCapitalized();

  /**
   * "1" if capitalized and one of following 3 words is Inc., Co., or Corp.
   */
  private static final Extractor cCompany = new CompanyNameDetector();

  /**
   * "1" if word contains letter, digit, and dash, in any position and case
   */
  private static final Extractor cLetterDigitDash = new ExtractorLetterDigitDash();

  /**
   * "1" if word contains uppercase letter, digit, and dash
   */
  private static final Extractor cUpperDigitDash = new ExtractorUpperDigitDash();

  /**
   * Distance to lowercase word.  Used by another extractor....
   */
  private static final Extractor cCapDist = new ExtractorCapDistLC();


  private static final Extractor[] eFrames_motley_naacl2003 = { cWordUppCase, cWordNumber, cWordDash, cAllCap, cMidSentence, cWordStartUCase, cWordMidUCase, cLetterDigitDash, cCompany, cAllCapitalized, cUpperDigitDash};
  private static final Extractor[] eFrames_motley_naacl2003_left = { cWordUppCase, cWordNumber, cWordDash, cAllCap, cMidSentence, cWordStartUCase, cWordMidUCase, cLetterDigitDash, cAllCapitalized, cUpperDigitDash};


  private ExtractorFramesRare() {
  }


  /** Get an array of rare word feature Extractor identified by a name.
   *  Note: Names used here must also be known in getExtractorFrames, so we
   *  can appropriately add error messages.  So if you add a keyword here,
   *  add it there as one to be ignored, too. (In the next iteration, this
   *  class and ExtractorFrames should probably just be combined).
   *
   *  @param identifier Describes a set of extractors for rare word features
   *  @return A set of extractors for rare word features
   */
  protected static Extractor[] getExtractorFramesRare(String identifier) {
    ArrayList<Extractor> extrs = new ArrayList<Extractor>();
    List<String> args = StringUtils.valueSplit(identifier, "[a-z0-9]*(?:\\([^)]*\\))?", "\\s*,\\s*");

    for (String arg : args) {
      if ("naacl2003unknowns".equalsIgnoreCase(arg)) {
        extrs.addAll(Arrays.asList(eFrames_motley_naacl2003));
        for (int i = 1; i <= 10; i++) {
          extrs.add(new ExtractorCWordSuff(i));
        }
        for (int i = 1; i <= 10; i++) {
          extrs.add(new ExtractorCWordPref(i));
        }

      } else if (("lnaacl2003unknowns").equalsIgnoreCase(arg)) {
        extrs.addAll(Arrays.asList(eFrames_motley_naacl2003_left));
        for (int i = 1; i <= 10; i++) {
          extrs.add(new ExtractorCWordSuff(i));
        }
        for (int i = 1; i <= 10; i++) {
          extrs.add(new ExtractorCWordPref(i));
        }
      } else if ("naacl2003conjunctions".equalsIgnoreCase(arg)) {
        extrs.addAll(Arrays.asList(naacl2003Conjunctions()));
      } else if (arg.startsWith("wordshapes(")) {
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new ExtractorWordShapeClassifier(i, "chris2"));
        }
      } else if (arg.startsWith("unicodeshapes(")) {
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new ExtractorWordShapeClassifier(i, "chris4"));
        }
      } else if (arg.startsWith("unicodeshapeconjunction(")) {
        int lWindow = Extractor.getParenthesizedNum(arg, 1);
        int rWindow = Extractor.getParenthesizedNum(arg, 2);
        extrs.add(new ExtractorWordShapeConjunction(lWindow, rWindow, "chris4"));
      } else if ("sighan2005".equalsIgnoreCase(arg)) {
        extrs.add(cWordNumber);
        for (int i = 1; i <= 4; i++) {
          extrs.add(new ExtractorCWordSuff(i));
        }
        for (int i = 1; i <= 4; i++) {
          extrs.add(new ExtractorCWordPref(i));
        }
      } else if (arg.startsWith("chinesedictionaryfeatures(")) {
        String path = Extractor.getParenthesizedArg(arg, 1);
        // Default nlp location for these features is: /u/nlp/data/pos-tagger/dictionary
        int lWindow = Extractor.getParenthesizedNum(arg, 2);
        int rWindow = Extractor.getParenthesizedNum(arg, 3);
        // First set up the dictionary prefix for the Chinese dictionaries
        ASBCDict.setPathPrefix(path);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.addAll(Arrays.asList(ctbPreFeatures(i)));
          extrs.addAll(Arrays.asList(ctbSufFeatures(i)));
          extrs.addAll(Arrays.asList(ctbUnkDictFeatures(i)));
          extrs.addAll(Arrays.asList(asbcUnkFeatures(i)));
        }
      // No longer add prefix suffix features, now that you can more flexibly add them separately.
      // } else if ("generic".equalsIgnoreCase(arg)) {
      //   // does prefix and suffix up to 6 grams
      //   for (int i = 1; i <= 6; i++) {
      //     extrs.add(new ExtractorCWordSuff(i));
      //     extrs.add(new ExtractorCWordPref(i));
      //   }
      } else if (arg.equalsIgnoreCase("motleyUnknown")) {  // This is naacl2003unknown minus prefix and suffix features.
        extrs.addAll(Arrays.asList(eFrames_motley_naacl2003));
      } else if (arg.startsWith("suffix(")) {
        int max = Extractor.getParenthesizedNum(arg, 1);
        for (int i = 1; i <= max; i++) {
          extrs.add(new ExtractorCWordSuff(i));
        }
      } else if (arg.startsWith("prefix(")) {
        int max = Extractor.getParenthesizedNum(arg, 1);
        for (int i = 1; i <= max; i++) {
          extrs.add(new ExtractorCWordPref(i));
        }
      } else if (arg.startsWith("prefixsuffix(")) {
        int max = Extractor.getParenthesizedNum(arg, 1);
        for (int i = 1; i <= max; i++) {
          extrs.add(new ExtractorsConjunction(new ExtractorCWordPref(i), new ExtractorCWordSuff(i)));
        }
      } else if (arg.startsWith("capitalizationsuffix(")) {
        int max = Extractor.getParenthesizedNum(arg, 1);
        for (int i = 1; i <= max; i++) {
          extrs.add(new ExtractorsConjunction(cWordUppCase, new ExtractorCWordSuff(i)));
        }
      } else if (arg.startsWith("distsim(")) {
        String path = Extractor.getParenthesizedArg(arg, 1);
        // traditional nlp filesystem location is: /u/nlp/data/pos_tags_are_useless/egw.bnc.200.pruned
        int lWindow = Extractor.getParenthesizedNum(arg, 2);
        int rWindow = Extractor.getParenthesizedNum(arg, 3);
        for (int i = lWindow; i <= rWindow; i++) {
          extrs.add(new ExtractorDistsim(path, i));
        }
      } else if (arg.startsWith("distsimconjunction(")) {
        String path = Extractor.getParenthesizedArg(arg, 1);
        int lWindow = Extractor.getParenthesizedNum(arg, 2);
        int rWindow = Extractor.getParenthesizedNum(arg, 3);
        extrs.add(new ExtractorDistsim.ExtractorDistsimConjunction(path, lWindow, rWindow));
      } else if (arg.equalsIgnoreCase("lctagfeatures")) {
        extrs.addAll(Arrays.asList(lcTagFeatures()));
      }
    }

    return extrs.toArray(new Extractor[extrs.size()]);
  }


  /**
   * This provides the conjunction of various features as rare words features.
   *
   * @return An array of feature conjunctions
   */
  private static Extractor[] naacl2003Conjunctions() {
    Extractor[] newW = new Extractor[24];
    //add them manually ....
    newW[0] = new ExtractorsConjunction(cWordUppCase, cWordSuff1);
    newW[1] = new ExtractorsConjunction(cWordUppCase, cWordSuff2);
    newW[2] = new ExtractorsConjunction(cWordUppCase, cWordSuff3);
    newW[3] = new ExtractorsConjunction(cWordUppCase, cWordSuff4);

    newW[4] = new ExtractorsConjunction(cAllCap, cWordSuff1);
    newW[5] = new ExtractorsConjunction(cAllCap, cWordSuff2);
    newW[6] = new ExtractorsConjunction(cAllCap, cWordSuff3);
    newW[7] = new ExtractorsConjunction(cAllCap, cWordSuff4);

    newW[8] = new ExtractorsConjunction(cMidSentence, cWordSuff1);
    newW[9] = new ExtractorsConjunction(cMidSentence, cWordSuff2);
    newW[10] = new ExtractorsConjunction(cMidSentence, cWordSuff3);
    newW[11] = new ExtractorsConjunction(cMidSentence, cWordSuff4);

    newW[12] = new ExtractorsConjunction(cWordStartUCase, cWordSuff1);
    newW[13] = new ExtractorsConjunction(cWordStartUCase, cWordSuff2);
    newW[14] = new ExtractorsConjunction(cWordStartUCase, cWordSuff3);
    newW[15] = new ExtractorsConjunction(cWordStartUCase, cWordSuff4);

    newW[16] = new ExtractorsConjunction(cWordMidUCase, cWordSuff1);
    newW[17] = new ExtractorsConjunction(cWordMidUCase, cWordSuff2);
    newW[18] = new ExtractorsConjunction(cWordMidUCase, cWordSuff3);
    newW[19] = new ExtractorsConjunction(cWordMidUCase, cWordSuff4);

    newW[20] = new ExtractorsConjunction(cCapDist, cWordSuff1);
    newW[21] = new ExtractorsConjunction(cCapDist, cWordSuff2);
    newW[22] = new ExtractorsConjunction(cCapDist, cWordSuff3);
    newW[23] = new ExtractorsConjunction(cCapDist, cWordSuff4);

    return newW;
  }


  private static Extractor[] lcTagFeatures() {
    Extractor[] newE = new Extractor[GlobalHolder.tags.getSize()];
    for (int i = 0; i < GlobalHolder.tags.getSize(); i++) {
      String tag = GlobalHolder.tags.getTag(i);
      newE[i] = new ExtractorCapLCSeen(tag);
    }
    return newE;
  }


  /* private ExtractorFramesRare() {
    // this is now a statics only class!
  } */

  /*
    ArrayList<Extractor> v = new ArrayList<Extractor>();
    GlobalHolder.ySize = GlobalHolder.tags.getSize();
    for (int i = 1; i < 5; i++) {
      for (int y = 0; y < GlobalHolder.tags.getSize(); y++) {
        if (!GlobalHolder.tags.isClosed(GlobalHolder.tags.getTag(y))) {
          ExtractorMorpho extr = new ExtractorMorpho(i, y);
          v.add(extr);
        }// if open
      }
    }// for i

    for (int y = 0; y < GlobalHolder.ySize; y++) {
      for (int y1 = 0; y1 < GlobalHolder.ySize; y1++) {
        if (!GlobalHolder.tags.isClosed(GlobalHolder.tags.getTag(y)) && (!GlobalHolder.tags.isClosed(GlobalHolder.tags.getTag(y)))) {
          ExtractorMorpho extr = new ExtractorMorpho(5, y, y1);
          v.add(extr);
        }// if open
      }
    }
    int vSize = v.size();
    Extractor[] eFramestemp = new Extractor[eFrames.length + vSize];
    System.arraycopy(eFrames, 0, eFramestemp, 0, eFrames.length);
    for (int i = 0; i < vSize; i++) {
      eFramestemp[i + eFrames.length] = v.get(i);
    }
    eFrames = eFramestemp;
     */

  private static Extractor[] ctbPreFeatures(int n) {
    String[] tagsets = new String[]{"AD", "AS", "BA", "CC", "CD", "CS", "DEC", "DEG", "DER", "DEV", "DT", "ETC", "FW", "IJ", "JJ", "LB", "LC", "M", "MSP", "NN", "NP", "NR", "NT", "OD", "P", "PN", "PU", "SB", "SP", "VA", "VC", "VE", "VV" };
    Extractor[] newW = new Extractor[tagsets.length];
    for (int k = 0; k < tagsets.length; k++) {
      newW[k] = new CtbPreDetector(tagsets[k], n);
    }
    return newW;
  } // end ctbPreFeatures


  private static Extractor[] ctbSufFeatures(int n) {
    String[] tagsets = new String[]{"AD", "AS", "BA", "CC", "CD", "CS", "DEC", "DEG", "DER", "DEV", "DT", "ETC", "FW", "IJ", "JJ", "LB", "LC", "M", "MSP", "NN", "NP", "NR", "NT", "OD", "P", "PN", "PU", "SB", "SP", "VA", "VC", "VE", "VV" };
    Extractor[] newW=new Extractor[tagsets.length];
    for(int k=0;k<tagsets.length;k++){
      newW[k] = new CtbSufDetector(tagsets[k], n);
    }
    return newW;
  } // end ctbSuffFeatures

  /*
  public static Extractor[] asbcPreFeatures(int n) {
    String[] tagsets = new String[]{"A", "Caa", "Cab", "Cba", "Cbb", "D", "DE", "DK", "Da", "Dd", "De", "Des", "Dfa", "Dfb", "Di", "Dk", "FW", "I", " Na", "Nb", " Nc", "Ncb", "Ncd", " Nd", "Neaq", "Nep", "Neqa", "Neqb", "Nes", "Neu", "Nf", "Ng", "Nh", "P", "PU", "SHI", "T", "VA", "VAC", "VB", "VC", "VCL", "VD", "VE", "VF", "VG", "VH", "VHC", "VI", "VJ", "VK", "VL", "V_2" };
    Extractor[] newW=new Extractor[tagsets.length];
    for(int k=0;k<tagsets.length;k++){
      newW[k] = new ASBCPreDetector(tagsets[k], n);
    }
    return newW;
  }

  public static Extractor[] asbcSufFeatures(int n) {
    String[] tagsets = new String[]{"A", "Caa", "Cab", "Cba", "Cbb", "D", "DE", "DK", "Da", "Dd", "De", "Des", "Dfa", "Dfb", "Di", "Dk", "FW", "I", " Na", "Nb", " Nc", "Ncb", "Ncd", " Nd", "Neaq", "Nep", "Neqa", "Neqb", "Nes", "Neu", "Nf", "Ng", "Nh", "P", "PU", "SHI", "T", "VA", "VAC", "VB", "VC", "VCL", "VD", "VE", "VF", "VG", "VH", "VHC", "VI", "VJ", "VK", "VL", "V_2"  };
    Extractor[] newW=new Extractor[tagsets.length];
    for(int k=0;k<tagsets.length;k++){
      newW[k] = new ASBCSufDetector(tagsets[k], n);
    }
    return newW;
  }
  */

  private static Extractor[] asbcUnkFeatures(int n) {
    String[] tagsets = new String[]{"A", "Caa", "Cab", "Cba", "Cbb", "D", "DE", "DK", "Da", "Dd", "De", "Des", "Dfa", "Dfb", "Di", "Dk", "FW", "I", " Na", "Nb", " Nc", "Ncb", "Ncd", " Nd", "Neaq", "Nep", "Neqa", "Neqb", "Nes", "Neu", "Nf", "Ng", "Nh", "P", "PU", "SHI", "T", "VA", "VAC", "VB", "VC", "VCL", "VD", "VE", "VF", "VG", "VH", "VHC", "VI", "VJ", "VK", "VL", "V_2"  };

    Extractor[] newW=new Extractor[tagsets.length];
    for(int k=0;k<tagsets.length;k++){
      newW[k] = new ASBCunkDetector(tagsets[k], n);
    }
    return newW;
  }


  private static Extractor[] ctbUnkDictFeatures(int n) {
    String[] tagsets = new String[]{"A", "Caa", "Cab", "Cba", "Cbb", "D", "DE", "DK", "Da", "Dd", "De", "Des", "Dfa", "Dfb", "Di", "Dk", "FW", "I", " Na", "Nb", " Nc", "Ncb", "Ncd", " Nd", "Neaq", "Nep", "Neqa", "Neqb", "Nes", "Neu", "Nf", "Ng", "Nh", "P", "PU", "SHI", "T", "VA", "VAC", "VB", "VC", "VCL", "VD", "VE", "VF", "VG", "VH", "VHC", "VI", "VJ", "VK", "VL", "V_2"  };

    Extractor[] newW=new Extractor[tagsets.length];
    for(int k=0;k<tagsets.length;k++){
      newW[k] = new CTBunkDictDetector(tagsets[k], n);
    }
    return newW;
  }

} // end class ExtractorFramesRare


/**
 * Superclass for rare word feature frames.  Provides some common functions.
 * Designed to be extended.
 */
class RareExtractor extends Extractor {

  static final String naTag = "NA";

  RareExtractor() {
    super();
  }

  RareExtractor(int position) {
    super(position, false);
  }

  static boolean startsUpperCase(String s) {
    if (s == null || s.length() == 0) {
      return false;
    }
    char ch = s.charAt(0);
    return Character.isUpperCase(ch);
  }

  /**
   * a string is lowercase if it starts with a lowercase letter
   * such as one from a to z.
   * Should we include numbers?
   * @param s The String to check
   * @return If its first character is lower case
   */
  protected static boolean startsLowerCase(String s) {
    if (s == null) {
      return false;
    }
    char ch = s.charAt(0);
    return Character.isLowerCase(ch);
  }

  protected static boolean containsDash(String s) {
    return s != null && s.indexOf('-') >= 0;
  }

  protected static boolean containsNumber(String s) {
    if (s == null) {
      return false;
    }
    for (int i = 0, len = s.length(); i < len; i++) {
      if (Character.isDigit(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  protected static boolean containsLetter(String s) {
    if (s == null) {
      return false;
    }
    for (int i = 0, len = s.length(); i < len; i++) {
      if (Character.isLetter(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  protected static boolean containsUpperCase(String s) {
    if (s == null) {
      return false;
    }
    for (int i = 0, len = s.length(); i < len; i++) {
      if (Character.isUpperCase(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  protected static boolean allUpperCase(String s) {
    if (s == null) {
      return false;
    }
    for (int i = 0, len = s.length(); i < len; i++) {
      if (!Character.isUpperCase(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  static boolean noneLowerCase(String s) {
    if (s == null) {
      return false;
    }
    for (int i = 0, len = s.length(); i < len; i++) {
      if (Character.isLowerCase(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static final long serialVersionUID = -7682607870855426599L;

} // end class RareExtractor


/** English-specific crude company name NER. */
class CompanyNameDetector extends RareExtractor {

  private final Set<String> companyNameEnds;

  public CompanyNameDetector() {
    companyNameEnds = new HashSet<String>();
    companyNameEnds.add("Company");
    companyNameEnds.add("COMPANY");
    companyNameEnds.add("Co.");
    companyNameEnds.add("Co");  // at end of sentence in PTB
    companyNameEnds.add("Cos.");
    companyNameEnds.add("CO.");
    companyNameEnds.add("COS.");
    companyNameEnds.add("Corporation");
    companyNameEnds.add("CORPORATION");
    companyNameEnds.add("Corp.");
    companyNameEnds.add("Corp"); // at end of sentence in PTB
    companyNameEnds.add("CORP.");
    companyNameEnds.add("Incorporated");
    companyNameEnds.add("INCORPORATED");
    companyNameEnds.add("Inc.");
    companyNameEnds.add("Inc"); // at end of sentence in PTB
    companyNameEnds.add("INC.");
    companyNameEnds.add("Association");
    companyNameEnds.add("ASSOCIATION");
    companyNameEnds.add("Assn");
    companyNameEnds.add("ASSN");
    companyNameEnds.add("Limited");
    companyNameEnds.add("LIMITED");
    companyNameEnds.add("Ltd.");
    companyNameEnds.add("LTD.");
    companyNameEnds.add("L.P.");
    // companyNameEnds.add("PLC"); // Other thing added at same time.
  }

  private boolean companyNameEnd(String s) {
    return companyNameEnds.contains(s);
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if ( ! startsUpperCase(s)) {
      return "0";
    }
    for (int i = 0; i <= 3; i++) {
      String s1 = pH.getWord(h, i);
      if (companyNameEnd(s1)) {
        return "1";
      }
    }
    return "0";
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }

  private static final long serialVersionUID = 21L;

} // end class CompanyNameDetector


class ExtractorUCase extends RareExtractor {

  public ExtractorUCase() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (containsUpperCase(s)) {
      return "1";
    }
    return "0";
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }

  private static final long serialVersionUID = 22L;

}


class ExtractorLetterDigitDash extends RareExtractor {

  public ExtractorLetterDigitDash() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (containsLetter(s) && containsDash(s) && containsNumber(s)) {
      return "1";
    }
    return "0";
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }

  private static final long serialVersionUID = 23;

}


class ExtractorUpperDigitDash extends RareExtractor {

  public ExtractorUpperDigitDash() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (containsUpperCase(s) && containsDash(s) && containsNumber(s)) {
      return "1";
    }
    return "0";
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }

  private static final long serialVersionUID = 33L;

}


/** This requires the 3 character classes in order.  This was worse than ExtractorLetterDigitDash (Oct 2009) */
class ExtractorLetterDashDigit extends RareExtractor {

  public ExtractorLetterDashDigit() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (s == null) return "0";
    boolean seenLetter = false;
    boolean seenDash = false;
    boolean seenNumber = false;
    for (int i = 0, len = s.length(); i < len; i++) {
      char ch = s.charAt(i);
      if (Character.isLetter(ch)) {
        seenLetter = true;
      } else if (seenLetter && ch == '-') {
        seenDash = true;
      } else if (seenDash && Character.isDigit(ch)) {
        seenNumber = true;
        break;
      }
    }
    if (seenNumber) {
      return "1";
    }
    return "0";
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }

  private static final long serialVersionUID = 33L;

}


/**
 * creates features which are true if the current word is all caps
 * and the distance to the first lowercase word to the left is dist
 * the distance is 1 for adjacent, 2 for one across, 3 for ... and so on.
 * inifinity if no capitalized word (we hit the start of sentence or '')
 */
class ExtractorCapDistLC extends RareExtractor {

  boolean verbose = false;

  public ExtractorCapDistLC() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String word = pH.getWord(h, 0);
    String ret;
    if (!startsUpperCase(word)) {
      if (verbose) {
        System.out.println("did not apply because not start with upper case");
      }
      return "0";
    }
    if (allUpperCase(word)) {
      ret = "all:";
    } else {
      ret = "start";
    }

    //now find the distance
    int current = -1;
    int distance = 1;

    while (true) {
      String prevWord = pH.getWord(h, current);
      if (startsLowerCase(prevWord)) {
        if (verbose) {
          System.out.println("returning " + (ret + current) + "for " + word + ' ' + prevWord);
        }
        return ret + distance;
      }
      if (prevWord.equals(naTag) || prevWord.equals("``")) {
        if (verbose) {
          System.out.println("returning " + ret + "infinity for " + word + ' ' + prevWord);
        }
        return ret + "infinity";
      }
      current--;
      distance++;
    }
  }

  @Override public boolean isDynamic() { return false; }
  @Override public boolean isLocal() { return false; }

  private static final long serialVersionUID = 34L;

}


/**
 * This feature applies when the word is capitalized
 * and the previous lower case is infinity
 * and the lower cased version of it has occured 2 or more times with tag t
 * false if the word was not seen.
 * create features only for tags that are the same as the tag t
 */
class ExtractorCapLCSeen extends RareExtractor {

  final String tag;
  int cutoff = 1;
  private final Extractor cCapDist = new ExtractorCapDistLC();

  ExtractorCapLCSeen(String tag) {
    this.tag = tag;
  }

  @Override
  public boolean precondition(String tag1) {
    return tag.equals(tag1);
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String res = cCapDist.extract(h, pH);
    if (res.equals("0")) {
      return res;
    }
    //otherwise it is capitalized
    String word = ExtractorFrames.cWord.extract(h, pH);
    if (GlobalHolder.dict.getCount(word, tag) > cutoff) {
      return res + tag;
    } else {
      return "0";
    }
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }

  private static final long serialVersionUID = 35L;

}


class ExtractorMidSentenceCap extends RareExtractor {

  public ExtractorMidSentenceCap() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String prevTag = pH.getTag(h, -1);
    if(prevTag == null) { return "0"; }
    if (prevTag.equals(naTag)) {
      return "0";
    }
    String s = pH.getWord(h, 0);
    if (containsUpperCase(s)) {
      return "1";
    }
    return "0";
  }

  private static final long serialVersionUID = 24L;

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


class ExtractorStartSentenceCap extends RareExtractor {

  public ExtractorStartSentenceCap() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String prevTag = pH.getTag(h, -1);
    if(prevTag == null) { return zeroSt; }
    if (!prevTag.equals(naTag)) {
      return zeroSt;
    }
    String s = pH.getWord(h, 0);
    if (startsUpperCase(s)) {
      String s1 = s.toLowerCase();
      if (GlobalHolder.dict.isUnknown(s1)) {
        return zeroSt;
      }
      return GlobalHolder.dict.getFirstTag(s1);
    }
    return zeroSt;
  }

  private static final long serialVersionUID = 25L;

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


class ExtractorMidSentenceCapC extends RareExtractor {

  public ExtractorMidSentenceCapC() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String prevTag = pH.getTag(h, -1);
    if (prevTag == null) { return zeroSt; }
    if (prevTag.equals(naTag)) {
      return zeroSt;
    }
    String s = pH.getWord(h, 0);
    if (startsUpperCase(s)) {
      String s1 = s.toLowerCase();
      if (GlobalHolder.dict.isUnknown(s1)) {
        return zeroSt;
      }
      return GlobalHolder.dict.getFirstTag(s1);
    }
    return zeroSt;
  }

  private static final long serialVersionUID = 26L;

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return true; }
}


class ExtractorCapC extends RareExtractor {

  public ExtractorCapC() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (startsUpperCase(s)) {
      String s1 = s.toLowerCase();
      if (GlobalHolder.dict.isUnknown(s1)) {
        return zeroSt;
      }
      return GlobalHolder.dict.getFirstTag(s1);
    }
    return zeroSt;
  }

  private static final long serialVersionUID = 26L;

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }
}


class ExtractorAllCap extends RareExtractor {

  public ExtractorAllCap() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (noneLowerCase(s)) {
      return "1";
    }
    return "0";
  }

  private static final long serialVersionUID = 27L;

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }
}


class ExtractorAllCapitalized extends RareExtractor {

  public ExtractorAllCapitalized() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (allUpperCase(s)) {
      return "1";
    }
    return "0";
  }

  private static final long serialVersionUID = 32L;

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }
}


class ExtractorCNumber extends RareExtractor {

  public ExtractorCNumber() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (containsNumber(s)) {
      return "1";
    }
    return "0";
  }

  private static final long serialVersionUID = 28L;

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }
}


class ExtractorDash extends RareExtractor {

  public ExtractorDash() {
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (containsDash(s)) {
      return "1";
    }
    return "0";
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }

  private static final long serialVersionUID = 29L;
}


class ExtractorCWordSuff extends RareExtractor {

  private final int num;

  ExtractorCWordSuff(int num) {
    this.num = num;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    // String word = TestSentence.toNice(pH.getWord(h, 0));
    String word = pH.getWord(h, 0);
    if (word.length() < num) {
      return "######";
    }
    return word.substring(word.length() - num);
  }

  private static final long serialVersionUID = 30L;

  @Override
  public String toString() {
    return super.toString() + " size " + num;
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }

}


class ExtractorCWordPref extends RareExtractor {

  private final int num;

  ExtractorCWordPref(int num) {
    this.num = num;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    // String word = TestSentence.toNice(pH.getWord(h, 0));
    String word = pH.getWord(h, 0);
    if (word.length() < num) {
      return "######";
    } else {
      return word.substring(0, num);
    }
  }

  private static final long serialVersionUID = 31L;

  @Override
  public String toString() {
    return super.toString() + " size " + num;
  }

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }

} // end class ExtractorCWordPref


class ExtractorsConjunction extends RareExtractor {

  private Extractor extractor1;
  private Extractor extractor2;

  volatile boolean isLocal, isDynamic;

  ExtractorsConjunction(Extractor e1, Extractor e2) {
    extractor1 = e1;
    extractor2 = e2;
    isLocal = e1.isLocal() && e2.isLocal();
    isDynamic = e1.isDynamic() || e2.isDynamic();
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String ex1 = extractor1.extract(h, pH);
    if (ex1.equals(zeroSt)) {
      return zeroSt;
    }
    String ex2 = extractor2.extract(h, pH);
    if (ex2.equals(zeroSt)) {
      return zeroSt;
    }
    return ex1 + ':' + ex2;
  }

  private static final long serialVersionUID = 36L;

  @Override public boolean isLocal() { return isLocal; }
  @Override public boolean isDynamic() { return isDynamic; }
}


class PluralAcronymDetector extends RareExtractor {

  public PluralAcronymDetector() {
  }

  private static boolean pluralAcronym(String s) {
    int len = s.length();
    len--;
    if (s.charAt(len) != 's') {
      return false;
    }
    for (int i = 0; i < len; i++) {
      if (!Character.isUpperCase(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = pH.getWord(h, 0);
    if (pluralAcronym(s)) {
      return "1";
    }
    return "0";
  }

  private static final long serialVersionUID = 33L;

  @Override public boolean isLocal() { return true; }
  @Override public boolean isDynamic() { return false; }

}


class CtbPreDetector extends RareExtractor {

  private String t1;

  CtbPreDetector(String t2, int n2) {
    super(n2);
    t1=t2;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = TestSentence.toNice(pH.getWord(h, position));

    if (!s.equals("") && CtbDict.getTagPre(t1, s.substring(0, 1)).equals("1"))
      return "1:"+t1;
    return "0:"+t1;
  }

  private static final long serialVersionUID = 43L;

  @Override
  public String toString() {
    return super.toString() + " tag=" + t1;
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }

} // end class ctbPreDetector


class CtbSufDetector extends RareExtractor {

  private String t1;

  CtbSufDetector(String t2, int n2) {
    super(n2);
    t1=t2;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s=TestSentence.toNice(pH.getWord(h, position));

    if(!s.equals("") && CtbDict.getTagSuf(t1, s.substring(s.length()-1, s.length())).equals("1"))
      return "1:"+t1;
    return "0:"+t1;
  }

  private static final long serialVersionUID = 44L;

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }

  @Override
  public String toString() {
    return super.toString() + " tag=" + t1;
  }

} // end class ctbPreDetector

/*
class ASBCPreDetector extends RareExtractor {
  private String t1;
  private int n1;
  public ASBCPreDetector(String t2, int n2) {
    t1=t2;
    n1=n2;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s=TestSentence.toNice(pH.get(h,n1,false));

    if(!s.equals("") && ASBCDict.getTagPre(t1, s.substring(0, 1)).equals("1"))
      return "1:"+t1;
    return "0:"+t1;
  }
  private static final long serialVersionUID = 53L;
} // end class ASBCPreDetector

class ASBCSufDetector extends RareExtractor {
  private String t1;
  private int n1;
  public ASBCSufDetector(String t2, int n2) {
    t1=t2;
    n1=n2;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s=TestSentence.toNice(pH.get(h,n1,false));
    if (!s.equals("") && ASBCDict.getTagSuf(t1, s.substring(s.length()-1, s.length())).equals("1"))
      return "1:"+t1;
    return "0:"+t1;
  }
  private static final long serialVersionUID = 54L;
} // end class ASBCPreDetector
*/

class ASBCunkDetector extends RareExtractor {

  private String t1;
  private int n1;

  ASBCunkDetector(String t2, int n2) {
    t1=t2;
    n1=n2;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s=TestSentence.toNice(pH.getWord(h,n1));

    if (ASBCunkDict.getTag(t1, s).equals("1"))
      return "1:"+t1;
    return "0:"+t1;
  }

  private static final long serialVersionUID = 57L;

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }

} // end class ASBCunkDetector

class CTBunkDictDetector extends RareExtractor {

  private String t1;
  private int n1;

  CTBunkDictDetector(String t2, int n2) {
    t1=t2;
    n1=n2;
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s=TestSentence.toNice(pH.getWord(h,n1));

    if (CTBunkDict.getTag(t1, s).equals("1"))
      return "1:"+t1;
    return "0:"+t1;
  }

  private static final long serialVersionUID = 80L;

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }

} // end class CTBunkDictDetector

class ExtractorWordShapeClassifier extends Extractor {

  private final int wordShaper;

  private static final Map<String,String> shapes = new HashMap<String,String>();

  ExtractorWordShapeClassifier(int position, String wsc) {
    super(position, false);
    wordShaper = WordShapeClassifier.lookupShaper(wsc);
  }

  @Override
  String extract(History h, PairsHolder pH) {
    String s = super.extract(h, pH);
    String shape;
    if((shape = shapes.get(s)) == null) {
      shape = WordShapeClassifier.wordShape(s, wordShaper);
      shapes.put(s,shape);
    }
    return shape;
  }

  private static final long serialVersionUID = 101L;

  @Override public boolean isLocal() { return position == 0; }
  @Override public boolean isDynamic() { return false; }
}


/**
 * This extractor extracts a conjunction of word shapes.
 */
class ExtractorWordShapeConjunction extends Extractor {

  private static final long serialVersionUID = -49L;

  private final int wordShaper;
  private final int left;
  private final int right;
  private final String name;

  ExtractorWordShapeConjunction(int left, int right, String wsc) {
    super();
    this.left = left;
    this.right = right;
    wordShaper = WordShapeClassifier.lookupShaper(wsc);
    name = "ExtractorWordShapeConjunction(" + left + ',' + right + ')';
  }

  @Override
  String extract(History h, PairsHolder pH) {
    StringBuilder sb = new StringBuilder();
    for (int j = left; j <= right; j++) {
      String s = pH.getWord(h, j);
      sb.append(WordShapeClassifier.wordShape(s, wordShaper));
      if (j < right) {
        sb.append('|');
      }
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }

}

