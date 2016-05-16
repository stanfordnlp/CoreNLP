package edu.stanford.nlp.wordseg;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.Serializable;


import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.trees.international.pennchinese.RadicalMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PaddedList;

/**
 * A Chinese segmenter Feature Factory for GALE project. (modified from Sighan Bakeoff 2005.)
 * This is supposed to have all the good closed-track features from Sighan bakeoff 2005,
 * and some other "open-track" features
 *
 * This will also be used to do a character-based chunking!
 * <p>
 * c is Chinese character ("char").  c means current, n means next and p means previous.
 * </p>
 *
 * <table>
 * <tr>
 * <th>Feature</th><th>Templates</th>
 * </tr>
 * <tr>
 * <tr>
 * <th></th><th>Current position clique</th>
 * </tr>
 * <tr>
 * <td>useWord1</td><td>CONSTANT, cc, nc, pc, pc+cc, if (As|Msr|Pk|Hk) cc+nc, pc,nc </td>
 * </tr>
 * </table>
 *
 * @author Huihsin Tseng
 * @author Pichuan Chang
 */

public class ChineseSegmenterFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> implements Serializable {
  /**
   *
   */

  private static final long serialVersionUID = 3387166382968763350L;
  private static TagAffixDetector taDetector = null;

  private static Redwood.RedwoodChannels logger = Redwood.channels(ChineseSegmenterFeatureFactory.class);

  public void init(SeqClassifierFlags flags) {
    super.init(flags);
  }


  /**
   * Extracts all the features from the input data at a certain index.
   *
   * @param cInfo The complete data set as a List of WordInfo
   * @param loc  The index at which to extract features.
   */
  public Collection<String> getCliqueFeatures(PaddedList<IN> cInfo, int loc, Clique clique) {
    Collection<String> features = Generics.newHashSet();

    if (clique == cliqueC) {
      addAllInterningAndSuffixing(features, featuresC(cInfo, loc), "C");
    } else if (clique == cliqueCpC) {
      addAllInterningAndSuffixing(features, featuresCpC(cInfo, loc), "CpC");
      addAllInterningAndSuffixing(features, featuresCnC(cInfo, loc-1), "CnC");
    } 
    // else if (clique == cliqueCpCp2C) {
    //   addAllInterningAndSuffixing(features, featuresCpCp2C(cInfo, loc), "CpCp2C");
    // } else if (clique == cliqueCpCp2Cp3C) {
    //   addAllInterningAndSuffixing(features, featuresCpCp2Cp3C(cInfo, loc), "CpCp2Cp3C");
    // } else if (clique == cliqueCpCp2Cp3Cp4C) {
    //   addAllInterningAndSuffixing(features, featuresCpCp2Cp3Cp4C(cInfo, loc), "CpCp2Cp3Cp4C");
    // } else if (clique == cliqueCpCp2Cp3Cp4Cp5C) {
    //   addAllInterningAndSuffixing(features, featuresCpCp2Cp3Cp4Cp5C(cInfo, loc), "CpCp2Cp3Cp4Cp5C");
    // }

    return features;
  }



  private static Pattern patE = Pattern.compile("[a-z]");
  private static Pattern patEC = Pattern.compile("[A-Z]");
  private static String isEnglish(String Ep, String Ec) {
    String chp = Ep;
    String chc = Ec;
    Matcher mp = patE.matcher(chp);   // previous char is [a-z]
    Matcher mc = patE.matcher(chc);   //  current char is [a-z]
    Matcher mpC = patEC.matcher(chp); // previous char is [A-Z]
    Matcher mcC = patEC.matcher(chc); //  current char is [A-Z]
    if (mp.matches() && mcC.matches()){
      return "BND"; // [a-z][A-Z]
    } else if (mp.matches() && mc.matches()){
      return "ENG"; // [a-z][a-z]
    } else if (mpC.matches() && mcC.matches()){
      return "BCC"; // [A-Z][A-Z]
    } else if (mp.matches() && !mc.matches() && !mcC.matches()){
      return "e1";  // [a-z][^A-Za-z]
    } else if (mc.matches() && !mp.matches() && !mpC.matches()) {
      return "e2";  // [^A-Za-z][a-z]
    } else if (mpC.matches() && !mc.matches() && !mcC.matches()){
      return "e3";  // [A-Z][^A-Za-z]
    } else if (mcC.matches() && !mp.matches() && !mpC.matches()) {
      return "e4";  // [^A-Za-z][A-Z]
    } else {
      return "";
    }
  }//is English

  private static Pattern patP = Pattern.compile("[\u00b7\\-\\.]");
  private static String isEngPU(String Ep) {
    Matcher mp = patP.matcher(Ep);
    if (mp.matches()){
      return "1:EngPU";
    } else {
      return "";
    }
  }//is EnglishPU



  public Collection<String> featuresC(PaddedList<IN> cInfo, int loc) {
    Collection<String> features = new ArrayList<>();
    CoreLabel c = cInfo.get(loc);
    CoreLabel c1 = cInfo.get(loc + 1);
    CoreLabel c2 = cInfo.get(loc + 2);
    CoreLabel c3 = cInfo.get(loc + 3);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);
    String charc = c.get(CoreAnnotations.CharAnnotation.class);
    String charc1 = c1.get(CoreAnnotations.CharAnnotation.class);
    String charc2 = c2.get(CoreAnnotations.CharAnnotation.class);
    String charc3 = c3.get(CoreAnnotations.CharAnnotation.class);
    String charp = p.get(CoreAnnotations.CharAnnotation.class);
    String charp2 = p2.get(CoreAnnotations.CharAnnotation.class);
    String charp3 = p3.get(CoreAnnotations.CharAnnotation.class);

    /**
     * N-gram features. N is upto 2.
     */
    if (flags.useWord1) {
      // features.add(charc +"c");
      // features.add(charc1+"c1");
      // features.add(charp +"p");
      // features.add(charp +charc  +"pc");
      // if(flags.useAs || flags.useMsr || flags.usePk || flags.useHk){ //msr, as
      //   features.add(charc +charc1 +"cc1");
      //   features.add(charp + charc1 +"pc1");
      // }

      features.add(charc +"::c");
      features.add(charc1+"::c1");
      features.add(charp +"::p");
      features.add(charp2 +"::p2");
      // trying to restore the features that Huishin described in SIGHAN 2005 paper
      features.add(charc +charc1  +"::cn");
      features.add(charp +charc  +"::pc");
      features.add(charp +charc1  +"::pn");
      features.add(charp2 +charp  +"::p2p");
      features.add(charp2 +charc  +"::p2c");
      features.add(charc2 +charc  +"::n2c");

      features.add("|word1");
    }

    return features;
  }

  private static CorpusDictionary outDict = null;

  public Collection<String> featuresCpC(PaddedList<IN> cInfo, int loc) {
    Collection<String> features = new ArrayList<>();
    CoreLabel c = cInfo.get(loc);
    CoreLabel c1 = cInfo.get(loc + 1);
    CoreLabel c2 = cInfo.get(loc + 2);
    CoreLabel c3 = cInfo.get(loc + 3);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);
    String charc = c.get(CoreAnnotations.CharAnnotation.class);
    if (charc == null) charc = "";
    String charc1 = c1.get(CoreAnnotations.CharAnnotation.class);
    if (charc1 == null) charc1 = "";
    String charc2 = c2.get(CoreAnnotations.CharAnnotation.class);
    if (charc2 == null) charc2 = "";
    String charc3 = c3.get(CoreAnnotations.CharAnnotation.class);
    if (charc3 == null) charc3 = "";
    String charp = p.get(CoreAnnotations.CharAnnotation.class);
    if (charp == null) charp = "";
    String charp2 = p2.get(CoreAnnotations.CharAnnotation.class);
    if (charp2 == null) charp2 = "";
    String charp3 = p3.get(CoreAnnotations.CharAnnotation.class);
    if (charp3 == null) charp3 = "";


    /*
     * N-gram features. N is upto 2.
     */

    if (flags.useWord2) {
      // features.add(charc +"c");
      // features.add(charc1+"c1");
      // features.add(charp +"p");
      // features.add(charp +charc  +"pc");
      // if( flags.useMsr ){
      //   features.add(charc +charc1 +"cc1");
      //   features.add(charp + charc1 +"pc1");
      // }

      features.add(charc +"::c");
      features.add(charc1+"::c1");
      features.add(charp +"::p");
      features.add(charp2 +"::p2");
      // trying to restore the features that Huishin described in SIGHAN 2005 paper
      features.add(charc +charc1  +"::cn");
      features.add(charp +charc  +"::pc");
      features.add(charp +charc1  +"::pn");
      features.add(charp2 +charp  +"::p2p");
      features.add(charp2 +charc  +"::p2c");
      features.add(charc2 +charc  +"::n2c");

      features.add("|word2");
    }

    /*
      Radical N-gram features. N is upto 4.
      Smoothing method of N-gram, because there are too many characters in Chinese.
      (It works better than N-gram when they are used individually. less sparse)
    */

    char rcharc, rcharc1,rcharc2, rcharc3, rcharp, rcharp1,rcharp2,rcharp3;
    if (charc.length()==0) { rcharc='n'; } else { rcharc=RadicalMap.getRadical(charc.charAt(0));}
    if (charc1.length()==0) { rcharc1='n'; } else { rcharc1=RadicalMap.getRadical(charc1.charAt(0));}
    if (charc2.length()==0) { rcharc2='n'; } else { rcharc2=RadicalMap.getRadical(charc2.charAt(0));}
    if (charc3.length()==0) { rcharc3='n'; } else { rcharc3=RadicalMap.getRadical(charc3.charAt(0));}
    if (charp.length()==0) { rcharp='n'; } else { rcharp=RadicalMap.getRadical(charp.charAt(0));}
    if (charp2.length()==0) { rcharp2='n'; } else { rcharp2=RadicalMap.getRadical(charp2.charAt(0));}
    if (charp3.length()==0) { rcharp3='n'; } else { rcharp3=RadicalMap.getRadical(charp3.charAt(0));}

    if(flags.useRad2){
      features.add(rcharc+"rc");
      features.add(rcharc1+"rc1");
      features.add(rcharp+"rp");
      features.add(rcharp  +  rcharc+"rpc");
      features.add(rcharc +rcharc1 +"rcc1");
      features.add(rcharp +  rcharc  +rcharc1 +"rpcc1");
      features.add("|rad2");
    }

    /* non-word dictionary:SEEM bi-gram marked as non-word */
    if (flags.useDict2) {
      NonDict2 nd = new NonDict2(flags);
      features.add(nd.checkDic(charp+charc, flags)+"nondict");
      features.add("|useDict2");
    }

    if (flags.useOutDict2){
      if (outDict == null) {
        logger.info("reading "+flags.outDict2+" as a seen lexicon");
        outDict = new CorpusDictionary(flags.outDict2, true);
      }
      features.add(outDict.getW(charp+charc)+"outdict");       // -1 0
      features.add(outDict.getW(charc+charc1)+"outdict");      // 0 1
      features.add(outDict.getW(charp2+charp)+"outdict");      // -2 -1
      features.add(outDict.getW(charp2+charp+charc)+"outdict");      // -2 -1 0
      features.add(outDict.getW(charp3+charp2+charp)+"outdict");      // -3 -2 -1
      features.add(outDict.getW(charp+charc+charc1)+"outdict");      // -1 0 1
      features.add(outDict.getW(charc+charc1+charc2)+"outdict");      // 0 1 2
      features.add(outDict.getW(charp+charc+charc1+charc2)+"outdict");      // -1 0 1 2
    }

    /*
      (CTB/ASBC/HK/PK/MSR) POS information of each characters.
      If a character falls into some function categories,
      it is very likely there is a boundary.
      A lot of Chinese function words belong to single characters.
      This feature is also good for numbers and punctuations.
      DE* are grouped into DE.
    */
    if (flags.useCTBChar2 || flags.useASBCChar2 || flags.useHKChar2
        || flags.usePKChar2 || flags.useMSRChar2) {
      String[] tagsets;
      // the "useChPos" now only works for CTB and PK
      if (flags.useChPos) {
        if(flags.useCTBChar2) {
          tagsets = new String[]{"AD", "AS", "BA", "CC", "CD", "CS", "DE", "DT", "ETC", "IJ", "JJ", "LB", "LC", "M",  "NN",  "NR", "NT", "OD", "P", "PN", "PU", "SB", "SP", "VA", "VC", "VE", "VV" };
        } else if (flags.usePKChar2) {
          //tagsets = new String[]{"r", "j", "t", "a", "nz", "l", "vn", "i", "m", "ns", "nr", "v", "n", "q", "Ng", "b", "d", "nt"};
          tagsets = new String[]{"2","3","4"};
        } else {
          throw new RuntimeException("only support settings for CTB and PK now.");
        }
      } else {
        //logger.info("Using Derived features");
        tagsets = new String[]{"2","3","4"};
      }

      if (taDetector == null) {
        taDetector = new TagAffixDetector(flags);
      }
      for (String tagset : tagsets) {
        features.add(taDetector.checkDic(tagset + "p", charp) + taDetector.checkDic(tagset + "i", charp) + taDetector.checkDic(tagset + "s", charc) + taDetector.checkInDic(charp) + taDetector.checkInDic(charc) + tagset + "prep-sufc");
        // features.add("|ctbchar2");  // Added a constant feature several times!!
      }
    }

    /*
      In error analysis, we found English words and numbers are often separated.
      Rule 1: isNumber feature: check if the current and previous char is a number.
      Rule 2: Disambiguation of time point and time duration.
      Rule 3: isEnglish feature: check if the current and previous character is an english letter.
      Rule 4: English name feature: check if the current char is a conjunct pu for English first and last name, since there is no space between two names.
      Most of PUs are a good indicator for word boundary, but - and .  is a strong indicator that there is no boundry within a previous , a follow char and it.
    */

    if (flags.useRule2) {
      /* Reduplication features */
      // previous character == current character
      if(charp.equals(charc)){ features.add("11");}
      // previous character == next character
      if(charp.equals(charc1)){ features.add("22");}

      // current character == next next character
      // fire only when usePk and useHk are both false.
      // Notice: this should be (almost) the same as the "22" feature, but we keep it for now.
      if( !flags.usePk && !flags.useHk) {
        if(charc.equals(charc2)){features.add("33");}
      }

      char cur1 = ' ';
      char cur2 = ' ';
      char cur =  ' ';
      char pre =  ' ';
      // actually their length must be either 0 or 1
      if (charc1.length() > 0) { cur1 = charc1.charAt(0); }
      if (charc2.length() > 0) { cur2 = charc2.charAt(0); }
      if (charc.length() > 0) { cur = charc.charAt(0); }
      if (charp.length() > 0) { pre = charp.charAt(0); }

      String prer= String.valueOf(rcharp); // the radical of previous character

      Pattern E = Pattern.compile("[a-zA-Z]");
      Pattern N = Pattern.compile("[0-9]");
      Matcher m = E.matcher(charp);
      Matcher ce = E.matcher(charc);
      Matcher pe = E.matcher(charp2);
      Matcher cn = N.matcher(charc);
      Matcher pn = N.matcher(charp2);


      // if current and previous characters are numbers...
      if (cur >= '0' && cur <= '9'&& pre >= '0' && pre <= '9'){
        if (cur == '9' && pre == '1' && cur1 == '9'&& cur2 >= '0' && cur2 <= '9'){ //199x
          features.add("YR");
        }else{
          features.add("2N");
        }

      // if current and previous characters are not both numbers
      // but previous char is a number
      // i.e. patterns like "1N" , "2A", etc
      } else if (pre >= '0' && pre <= '9'){
        features.add("1N");

      // if previous character is an English character
      } else if(m.matches()){
        features.add("E");

      // if the previous character contains no radical (and it exist)
      } else if(prer.equals(".") && charp.length() == 1){
        // fire only when usePk and useHk are both false. Not sure why. -pichuan
        if(!flags.useHk && !flags.usePk ){
          if(ce.matches()){
            features.add("PU+E");
          }
          if(pe.matches()){
            features.add("E+PU");
          }
          if(cn.matches()){
            features.add("PU+N");
          }
          if(pn.matches()){
            features.add("N+PU");
          }
        }
        features.add("PU");
      }

      String engType = isEnglish(charp, charc);
      String engPU = isEngPU(charp);
      if ( ! engType.equals(""))
        features.add(engType);
      if ( ! engPU.equals("") && ! engType.equals(""))
        features.add(engPU + engType);
    }//end of use rule


    // features using "Character.getType" information!
    String origS = c.get(CoreAnnotations.OriginalCharAnnotation.class);
    char origC = ' ';
    if (origS.length() > 0) { origC = origS.charAt(0); }
    int type = Character.getType(origC);
    switch (type) {
    case Character.UPPERCASE_LETTER: // A-Z and full-width A-Z
    case Character.LOWERCASE_LETTER: // a-z and full-width a-z
      features.add("CHARTYPE-LETTER");
      break;
    case Character.DECIMAL_DIGIT_NUMBER:
      features.add("CHARTYPE-DECIMAL_DIGIT_NUMBER");
      break;
    case Character.OTHER_LETTER: // mostly chinese chars
      features.add("CHARTYPE-OTHER_LETTER");
      break;
    default: // other types
      features.add("CHARTYPE-MISC");
    }

    return features;
  }


  public Collection<String> featuresCnC(PaddedList<IN> cInfo, int loc) {
    Collection<String> features = new ArrayList<>();
    CoreLabel c = cInfo.get(loc);
    CoreLabel c1 = cInfo.get(loc + 1);
    CoreLabel p = cInfo.get(loc - 1);
    String charc = c.get(CoreAnnotations.CharAnnotation.class);
    String charc1 = c1.get(CoreAnnotations.CharAnnotation.class);
    String charp = p.get(CoreAnnotations.CharAnnotation.class);


    if (flags.useWordn) {
      features.add(charc +"c");
      features.add(charc1+"c1");
      features.add(charp +"p");
      features.add(charp +charc  +"pc");

      if(flags.useAs || flags.useMsr||flags.usePk||flags.useHk){
        features.add(charc +charc1 +"cc1");
        features.add(charp + charc1 +"pc1");
      }
      features.add("|wordn");
    }
    return features;
  }//end of CnC


}//end of Class

