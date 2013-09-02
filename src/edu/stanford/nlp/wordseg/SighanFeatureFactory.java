package edu.stanford.nlp.wordseg;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.Serializable;

import edu.stanford.nlp.trees.international.pennchinese.RadicalMap;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.CharAnnotation;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.util.PaddedList;

/**
 * A Feature Factory for Chinese word segmentation (from Sighan Bakeoff 2005).
 *
 * @author Huihsin Tseng
 * @author Pichuan Chang
 */
public class SighanFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> implements Serializable {
  /**
   *
   */
  private static final long serialVersionUID = 9030619717868566938L;
  private static CtbDetector ctbDetector = null;
  private static AsbcDetector asbcDetector = null;
  private static HkDetector  hkDetector = null;
  private static PkDetector pkDetector = null;
  private static MsrDetector msrDetector = null;

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
    Collection<String> features = new HashSet<String>();

    if (clique == cliqueC) {
      addAllInterningAndSuffixing(features, featuresC(cInfo, loc), "C");
    } else if (clique == cliqueCpC) {
      addAllInterningAndSuffixing(features, featuresCpC(cInfo, loc), "CpC");
      addAllInterningAndSuffixing(features, featuresCnC(cInfo, loc-1), "CnC");
    }

    return features;
  }


  private static final Pattern patE = Pattern.compile("[a-z]");
  private static final Pattern patEC = Pattern.compile("[A-Z]");

  private static String isEnglish(String Ep, String Ec) {
    String chp = Ep;
    String chc = Ec;
    Matcher mp = patE.matcher(chp);
    Matcher mc = patE.matcher(chc);
    Matcher mpC = patEC.matcher(chp);
    Matcher mcC = patEC.matcher(chc);
    String ANS;
    if (mp.matches() && mcC.matches()){
      ANS="BND";
    }else if (mp.matches() && mc.matches()){
      ANS="ENG";
    }else if (mpC.matches() && mcC.matches()){
      ANS="BCC";
    }else if (mp.matches()){
      ANS="e1";
    }else if (mc.matches()){
      ANS="e2";
    }else if (mpC.matches()){
      ANS="e3";
    }else if (mcC.matches()){
      ANS="e4";
    }else{
      ANS="";
    }
    return ANS;
  } // end isEnglish

  // TODO: Chris: this pattern looks wrong to me! Just: [-\u00b7.] ?  Also why doesn't it match other punctuation?
  private static final Pattern patP = Pattern.compile("[\u00b7\\-\\.]");

  private static String isEngPU(String Ep) {
    String chp = Ep;
    String ANS;
    Matcher mp = patP.matcher(chp);
    if (mp.matches()){
      ANS="1:EngPU";
    }else{
      ANS="";
    }
    return ANS;
  }//is EnglishPU



  protected Collection<String> featuresC(PaddedList<? extends CoreLabel> cInfo, int loc) {
    Collection<String> features = new ArrayList<String>();
    CoreLabel c = cInfo.get(loc);
    CoreLabel c1 = cInfo.get(loc + 1);
    CoreLabel c2 = cInfo.get(loc + 2);
    CoreLabel c3 = cInfo.get(loc + 3);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);
    String charc = c.get(CharAnnotation.class);
    String charc1 = c1.get(CharAnnotation.class);
    String charc2 =  c2.get(CharAnnotation.class);
    String charc3 = c3.get(CharAnnotation.class);
    String charp = p.get(CharAnnotation.class);
    String charp2 = p2.get(CharAnnotation.class);
    String charp3 = p3.get(CharAnnotation.class);

    /*
     * N-gram features. N is upto 2.
     */
    if (flags.useWord1) {
      features.add(charc +"c");
      features.add(charc1+"c1");
      features.add(charp +"p");
      features.add(charp +charc  +"pc");
      if(flags.useAs || flags.useMsr||flags.usePk||flags.useHk){//msr, as
        features.add(charc +charc1 +"cc1");
        features.add(charp + charc1 +"pc1");
      }
      features.add("|word1");

    }

    return features;
  }

  protected Collection<String> featuresCpC(PaddedList<? extends CoreLabel> cInfo, int loc) {
    Collection<String> features = new ArrayList<String>();
    CoreLabel c = cInfo.get(loc);
    CoreLabel c1 = cInfo.get(loc + 1);
    CoreLabel c2 = cInfo.get(loc + 2);
    CoreLabel c3 = cInfo.get(loc + 3);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    CoreLabel p3 = cInfo.get(loc - 3);
    String charc = c.get(CharAnnotation.class);
    String charc1 = c1.get(CharAnnotation.class);
    String charc2 = c2.get(CharAnnotation.class);
    String charc3 = c3.get(CharAnnotation.class);
    String charp = p.get(CharAnnotation.class);
    String charp2 = p2.get(CharAnnotation.class);
    String charp3 = p3.get(CharAnnotation.class);


    /*
     * N-gram features. N is upto 2.
     */
    if (flags.useWord2) {
      features.add(charc +"c");
      features.add(charc1+"c1");
      features.add(charp +"p");
      features.add(charp +charc  +"pc");
      if( flags.useMsr){
        features.add(charc +charc1 +"cc1");
        features.add(charp + charc1 +"pc1");
      }

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
    if (charp.length()==0)  { rcharp='n';  } else { rcharp=RadicalMap.getRadical(charp.charAt(0));  }
    if (charp2.length()==0) { rcharp2='n'; } else { rcharp2=RadicalMap.getRadical(charp2.charAt(0));}
    if (charp3.length()==0) { rcharp3='n'; } else { rcharp3=RadicalMap.getRadical(charp3.charAt(0));}

    if(flags.useRad2){
      features.add(rcharc+"rc");
      features.add(rcharc1+"rc1");
      features.add(rcharp+"rp");
      features.add(rcharp + rcharc + "rpc");
      features.add(rcharc + rcharc1 + "rcc1");
      features.add(rcharp + rcharc + rcharc1 + "rpcc1");
      features.add("|rad2");
    }

/*non-word dictionary:SEEM bi-gram marked as non-word*/
    if (flags.useDict2){
      NonDict nd = new NonDict(flags.sighanCorporaDict);
      features.add(nd.checkDic(charp+charc, flags)+"nondict");
      features.add("|useDict2");
    }

/*
  CTB POS information of each characters.
  If a character falls into some function categories, it is very likely there is a boundary. A lot of Chinese function words belong to single characters.
  This feature is also good for numbers and punctuations.
  DE* are grouped into DE.
*/
    if (flags.useCTBChar2) {
      //String[] tagsets = new String[]{"AD", "CC","CD", "DT",  "JJ", "LC", "M",  "NN",  "NR", "NT", "OD", "P", "PN",  "VA", "VV" };//open categories
      String[] tagsets = new String[]{"2","3","4" };
      if (ctbDetector == null) {
        ctbDetector = new CtbDetector(flags.sighanCorporaDict);
      }
      for(int k=0;k<tagsets.length;k++){
	features.add(ctbDetector.checkDic(tagsets[k]+"p", charp) + ctbDetector.checkDic(tagsets[k]+"i", charp) + ctbDetector.checkDic(tagsets[k]+"s", charc)+ ctbDetector.checkInDic(charp)+ctbDetector.checkInDic(charc)+ tagsets[k]+ "prep-sufc" );
      }
      features.add("|ctbchar2");
    }

/*
  ASBC POS information of each characters.
*/

    if (flags.useASBCChar2) {
      //String[] tagsets = new String[]{"Na", "FW", "Nb", "Nc", "VC", "Neu", "VH", "VA", "Nd", "A", "VJ", "VG", "D", "VE", "VHC", "VB", "VCL", "Neqa", "VD", "Nf", "Ncd", "VAC", "VF"};
      String[] tagsets = new String[]{"2","3","4" };
      if (asbcDetector == null) {
        asbcDetector = new AsbcDetector(flags.sighanCorporaDict);
      }
      //AsbcInDetector aid = new AsbcInDetector();

      for(int k=0;k<tagsets.length;k++){
	features.add(asbcDetector.checkDic(tagsets[k]+"p", charp) +  asbcDetector.checkDic(tagsets[k]+"i", charp) + asbcDetector.checkDic(tagsets[k]+"s", charc)+ asbcDetector.checkInDic(charp)+asbcDetector.checkInDic(charc)+ tagsets[k]+ "prep-sufc" );
      }
      features.add("|asbcchar2");
    }
/*
  HK POS information of each characters.
*/

    if (flags.useHKChar2) {
      //String[] tagsets = new String[]{"Na", "FW", "Nb", "Nc", "VC", "Neu", "VH", "VA", "Nd", "A", "VJ", "VG", "D", "VE", "VHC", "VB", "VCL", "Neqa", "VD", "Nf", "Ncd", "VAC", "VF"};
      String[] tagsets = new String[]{"2","3","4" };
      if (hkDetector == null) {
        hkDetector = new HkDetector(flags.sighanCorporaDict);
      }

      for(int k=0;k<tagsets.length;k++){
	features.add(hkDetector.checkDic(tagsets[k]+"p", charp) +  hkDetector.checkDic(tagsets[k]+"i", charp) + hkDetector.checkDic(tagsets[k]+"s", charc)+ hkDetector.checkInDic(charp)+hkDetector.checkInDic(charc)+ tagsets[k]+ "prep-sufc" );
      }
      features.add("|hkchar2");
    }

/*
  PK POS information of each characters.
*/

    if (flags.usePKChar2) {
      //String[] tagsets = new String[]{"r", "j", "t", "a", "nz", "l", "vn", "i", "m", "ns", "nr", "v", "n", "q", "Ng", "b", "d", "nt"};
      String[] tagsets = new String[]{"2","3","4" };
      if (pkDetector == null) {
        pkDetector = new PkDetector(flags.sighanCorporaDict);
      }
      for(int k=0;k<tagsets.length;k++){
	features.add(pkDetector.checkDic(tagsets[k]+"p", charp) +  pkDetector.checkDic(tagsets[k]+"i", charp) + pkDetector.checkDic(tagsets[k]+"s", charc)+ pkDetector.checkInDic(charp)+pkDetector.checkInDic(charc)+ tagsets[k]+ "prep-sufc" );
      }
      features.add("|pkchar2");
    }

/*
  MSR POS information of each characters.
*/

    if (flags.useMSRChar2) {
      // String[] tagsets = new String[]{ "CC",  "OD",  "NT", "NV" };
      String[] tagsets = new String[]{"2","3","4" };
      if (msrDetector == null) {
        msrDetector = new MsrDetector(flags.sighanCorporaDict);
      }

      for(int k=0;k<tagsets.length;k++){
	features.add(msrDetector.checkDic(tagsets[k]+"p", charp) +  msrDetector.checkDic(tagsets[k]+"i", charp) + msrDetector.checkDic(tagsets[k]+"s", charc)+ msrDetector.checkInDic(charp)+msrDetector.checkInDic(charc)+ tagsets[k]+ "prep-sufc" );
      }
      features.add("|msrchar2");
    }

    /*
      In error analysis, we found English words and numbers are often separated.
      Rule 1: isNumber feature: check if the current and previous char is a number.
      Rule 2: Disambiguation of time point and time duration.
      Rule 3: isEnglish feature: check if the current and previous character is an english letter.
      Rule 4: English name feature: check if the current char is a conjunct pu for English first and last name, since there is no space between two names.
      Most of PUs are a good indicator for word boundary, but - and .  is a strong indicator that there is no boundary within a previous , a follow char and it.
    */

    if (flags.useRule2) {
      if(charp.equals(charc)){ features.add("11");}
      if(charp.equals(charc1)){ features.add("22");}
      if(flags.usePk||flags.useHk){}else{
        if(charc.equals(charc2)){features.add("33");}
      }

      if (charc.length() == 1 && charp.length() == 1 && charc1.length() == 1 && charc2.length() == 1) {
        char cur1 = charc1.charAt(0);
        char cur2 = charc2.charAt(0);
        char cur = charc.charAt(0);
        char pre = charp.charAt(0);
        String prer=""+rcharp;

 	Pattern E = Pattern.compile("[a-zA-Z]");
 	Pattern N = Pattern.compile("[0-9]");
	Matcher m = E.matcher(charp);
	Matcher ce = E.matcher(charc);
        Matcher pe = E.matcher(charp2);
    	Matcher cn = N.matcher(charc);
        Matcher pn = N.matcher(charp2);


	if (cur >= '0' && cur <= '9'&& pre >= '0' && pre <= '9'){
          // TODO: Chris: This seems a bit too specific to the Sighan data: at least match from 1980-2040!
          if (cur == '9' && pre == '1' && cur1 == '9'&& cur2 >= '0' && cur2 <= '9'){ //199x
            features.add("YR");
          }else{
            features.add("2N");
	  }
        }else if (pre >= '0' && pre <= '9'){
	  features.add("1N");
        }else if(m.matches()){
          features.add("E");
        }else if(prer.equals(".")){
          if(flags.useHk || flags.usePk ){//17
          }else{//20
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

        if (isEnglish(charp, charc).equals("")){
        }else{
          features.add(isEnglish(charp, charc));
        }
        if (isEngPU(charp).equals("") || isEnglish(charp, charc).equals("")){
        }else{
          features.add(isEngPU(charp) + isEnglish(charp, charc));
        }

      }//end of check char
    }//end of use rule

    if (flags.useOccurrencePatterns) {//msr
	Pattern NUM = Pattern.compile("[\ufeff0-9\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\uff2f\u96f6\u5341\u767e\u5343\u842c\u5104\uff11\uff12\uff13\uff14\uff15\uff16\uff17\uff18\uff19\uff10\u4e07\u4ebf]");
	Matcher P = NUM.matcher(charp);
	Matcher P2 = NUM.matcher(charp2);
	Matcher P3 = NUM.matcher(charp3);

        // TODO: Chris: is it not a good idea to include % in the last default case?  And should these patterns also include an ASCII % -- the number pattern has ASCII numbers
        Pattern CL;
    	if(flags.useAs){
	  CL = Pattern.compile("[\u5e74\uff05\uff09\u591a\u865f\u9918\u5206\u65e5\u73ed\u4e16\u9ede\u6708\u5df7\u8def\u6642\u5146\u5e7e\u6210\u89d2\u5b57\u91cd\u6a13]");
	}else if(flags.useHk){
	  CL = Pattern.compile("[\u25cb\u5e74\u6708\u9023\u5f37\u89d2\u5206\u5927\u5b57\u9f8d\u9ede\u4eba\u661f\u4e16\u624b\u5146\u58d8\u6642\u88e1]");
	}else if (flags.useMsr){
	  CL = Pattern.compile("[\u5143\u65e5\u591a\uff05\u5e74\u7f8e\u4e2a\u6708\u516c\u540d\u5206\u4f59\u5428\u7c73\u4ea9\u5bb6\u5c81\u6b21\u70b9\u79cd\u6761\u4ef6\u4f4d\u6237\u53f0\u9879\u53f7\u65f6\u5468\u5929]");
	}else if (flags.usePk){
	  CL = Pattern.compile("[\uff05\u5e74\u65e5\uff0e\u6708\u65f6\u5206\u70b9\u578b\u5927\u53f7\u91cc\u7b49\u661f]");
	}else{
	  CL = Pattern.compile("[\u5e74\u591a\u65e5\u6708\u4f59\u65f6\u6210\u5206]");
	}

	Matcher C = CL.matcher(charc);
	if(P.matches() && P2.matches()&& P3.matches()&& C.matches()){
	  features.add("3P");
	}
	if(P.matches() && P2. matches()&& C.matches()){
	  features.add("2P");
	}
	if(P.matches() && C.matches()){
	  features.add("1P");
	}

     }//end of use pattern good for ctb and msr

    return features;
  }


  protected Collection<String> featuresCnC(PaddedList<? extends CoreLabel> cInfo, int loc) {
    Collection<String> features = new ArrayList<String>();
    CoreLabel c = cInfo.get(loc);
    CoreLabel c1 = cInfo.get(loc + 1);
    CoreLabel c2 = cInfo.get(loc + 2);
    CoreLabel p = cInfo.get(loc - 1);
    CoreLabel p2 = cInfo.get(loc - 2);
    String charc = c.get(CharAnnotation.class);
    String charc1 = c1.get(CharAnnotation.class);
    String charc2 = c2.get(CharAnnotation.class);
    String charp = p.get(CharAnnotation.class);
    String charp2 = p2.get(CharAnnotation.class);


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


} //end of Class SighanFeatureFactory





class NonDict  {
  public String sighanCorporaDict = "/u/nlp/data/chinese-segmenter/";
  private static CorpusDictionary as = null;
  private static CorpusDictionary hk = null;
  private static CorpusDictionary msr = null;
  private static CorpusDictionary pk = null;
  private static CorpusDictionary ctb = null;

  public NonDict() {
    if (as == null) {
      as   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/as.non");
      hk   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/city.non");
      msr   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/msr.non");
      pk   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/pku.non");
      ctb   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/ctb.non");
    }
  }

  public NonDict(String sighanCorporaDict) {
    this.sighanCorporaDict = sighanCorporaDict;
    if (as == null) {
      as   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/as.non");
      hk   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/city.non");
      msr   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/msr.non");
      pk   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/pku.non");
      ctb   = new CorpusDictionary(sighanCorporaDict+"/Sighan2005/dict/pos_close/ctb.non");
    }
  }

  String checkDic(String c2,SeqClassifierFlags flags){
    /*
    Pattern p = Pattern.compile("\uFFFF");
    Matcher m = p.matcher(c2);
    c2 = m.replaceAll("");
    */

    CorpusDictionary nd;

    if(flags.useAs){
      nd=as;
    }else if(flags.useHk){
      nd=hk;
    }else if (flags.useMsr){
      nd=msr;
    }else if (flags.usePk){
      nd=pk;
    }else{
      nd=ctb;
    }
    if(nd.getW(c2).equals("1"))
      return "1";
    return "0";
  }

}


///////////////////////
/*
class msrInDetector  {
  static String checkInDic(String c2 ){
    if(affDict.getmsrIn(c2).equals("1"))
      return "1";
    return "0";
  }
}

class CtbInDetector  {
  String checkInDic(String c2 ){
    if(affDict.getctbIn(c2).equals("1"))
      return "1";
    return "0";
  }
}

class asbcInDetector  {
  static String checkInDic(String c2 ){
    if(affDict.getasbcIn(c2).equals("1"))
      return "1";
    return "0";
  }
}

class hkInDetector  {
  static String checkInDic(String c2 ){
    if(affDict.gethkIn(c2).equals("1"))
      return "1";
    return "0";
  }
}

class pkInDetector  {
  static String checkInDic(String c2 ){
    if(affDict.getpkIn(c2).equals("1"))
      return "1";
    return "0";
  }
  }*/

/////////////////////////////////////////////////////////////////////////////////////////////////////

class MsrDetector{
  CorpusChar msr;
  affDict mAD;

  public MsrDetector(String sighanCorporaDict) {
    msr = new CorpusChar(sighanCorporaDict+"/Sighan2005/dict/pos_close/char.msr.list");
    mAD = new affDict(sighanCorporaDict+"/Sighan2005/dict/affix/in.msr");
  }

  String checkDic(String t2, String c2 ){
    /*
    Pattern p = Pattern.compile("\uFFFF");
    Matcher m = p.matcher(t2);
    t2 = m.replaceAll("");
    m = p.matcher(c2);
    c2 = m.replaceAll("");
    */

    if(msr.getTag(t2, c2).equals("1"))
      return "1";
    return "0";
  }
  String checkInDic(String c2 ){
    if(mAD.getInDict(c2).equals("1"))
      return "1";
    return "0";
  }
}


class CtbDetector{
  CorpusChar ctb;
  affDict cAD;

  public CtbDetector(String sighanCorporaDict) {
    ctb = new CorpusChar(sighanCorporaDict+"/Sighan2005/dict/pos_close/char.ctb.list");
    cAD = new affDict(sighanCorporaDict+"/Sighan2005/dict/affix/in.ctb");

  }


  String checkDic(String t2, String c2){
    /*
    Pattern p = Pattern.compile("\uFFFF");
    Matcher m = p.matcher(t2);
    t2 = m.replaceAll("");
    m = p.matcher(c2);
    c2 = m.replaceAll("");
    */

    if(ctb.getTag(t2, c2).equals("1"))
      return "1";
    return "0";
  }

  String checkInDic(String c2 ){
    if(cAD.getInDict(c2).equals("1"))
      return "1";
    return "0";
  }

}

/* This is a class containing POS infromation for ASBC Corpus*/
class AsbcDetector{
  CorpusChar asbc;
  affDict aAD;

  public AsbcDetector(String sighanCorporaDict) {
    asbc = new CorpusChar(sighanCorporaDict+"/Sighan2005/dict/pos_close/char.as.list");
    aAD = new affDict(sighanCorporaDict+"/Sighan2005/dict/affix/in.as");
  }


  String checkDic(String t2, String c2){
    /*
    Pattern p = Pattern.compile("\uFFFF");
    Matcher m = p.matcher(t2);
    t2 = m.replaceAll("");
    m = p.matcher(c2);
    c2 = m.replaceAll("");
    */

    if(asbc.getTag(t2, c2).equals("1"))
      return "1";
    return "0";
  }

  String checkInDic(String c2 ){
    if(aAD.getInDict(c2).equals("1"))
      return "1";
    return "0";
  }
}

/* This is a class containing POS infromation for PKU Corpus*/
class PkDetector{
  CorpusChar pk;
  affDict pAD;

  public PkDetector(String sighanCorporaDict) {
    pk = new CorpusChar(sighanCorporaDict+"/Sighan2005/dict/pos_close/char.pk.list");
    pAD = new affDict(sighanCorporaDict+"/Sighan2005/dict/affix/in.pk");
  }


  String checkDic(String t2, String c2){
    /*
    Pattern p = Pattern.compile("\uFFFF");
    Matcher m = p.matcher(t2);
    t2 = m.replaceAll("");
    m = p.matcher(c2);
    c2 = m.replaceAll("");
    */

    if(pk.getTag(t2, c2).equals("1"))
      return "1";
    return "0";
  }

  String checkInDic(String c2 ){
    if(pAD.getInDict(c2).equals("1"))
      return "1";
    return "0";
  }
}

/*HKSAR .*/

class HkDetector{
  CorpusChar hk;
  affDict hAD;

  public HkDetector(String sighanCorporaDict) {
    hk = new CorpusChar(sighanCorporaDict+"/Sighan2005/dict/pos_close/char.city.list");
    hAD = new affDict(sighanCorporaDict+"/Sighan2005/dict/affix/in.city");
  }

  String checkDic(String t2, String c2){
    /*
    Pattern p = Pattern.compile("\uFFFF");
    Matcher m = p.matcher(t2);
    t2 = m.replaceAll("");
    m = p.matcher(c2);
    c2 = m.replaceAll("");
    */

    if(hk.getTag(t2, c2).equals("1"))
      return "1";
    return "0";
  }

  String checkInDic(String c2 ){
    if(hAD.getInDict(c2).equals("1"))
      return "1";
    return "0";
  }
} // end class HkDetector

