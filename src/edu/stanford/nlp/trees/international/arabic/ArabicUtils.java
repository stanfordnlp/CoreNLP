package edu.stanford.nlp.trees.international.arabic; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.util.*;
import java.util.Map.Entry;


/**
 * This class contains tools for dealing with arabic text, in particular conversion to IBM normalized Arabic.
 *
 * The code was adapted to java from the perl script ar_normalize_v5.pl
 *
 * @author Alex Kleeman
 */
public class ArabicUtils  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ArabicUtils.class);


  public static Map<String,String> presToLogicalMap(){
    Map<String,String> rules = Generics.newHashMap();

        // PRESENTATION FORM TO LOGICAL FORM NORMALIZATION (presentation form is rarely used - but some UN documents have it).
    rules.put("\\ufc5e","\u0020\u064c\u0651"); // ligature shadda with dammatan isloated
    rules.put("\\ufc5f","\u0020\u064d\u0651"); // ligature shadda with kasratan isloated
    rules.put("\\ufc60","\u0020\u064e\u0651"); // ligature shadda with fatha isloated
    rules.put("\\ufc61","\u0020\u064f\u0651"); // ligature shadda with damma isloated
    rules.put("\\ufc62","\u0020\u0650\u0651"); // ligature shadda with kasra isloated
    // Arabic Presentation Form-B to Logical Form
    rules.put("\\ufe80","\u0621"); // isolated hamza
    rules.put("[\\ufe81\\ufe82]","\u0622"); // alef with madda
    rules.put("[\\ufe83\\ufe84]","\u0623"); // alef with hamza above
    rules.put("[\\ufe85\\ufe86]","\u0624"); // waw with hamza above
    rules.put("[\\ufe87\\ufe88]","\u0625"); // alef with hamza below
    rules.put("[\\ufe89\\ufe8a\\ufe8b\\ufe8c]","\u0626"); // yeh with hamza above
    rules.put("[\\ufe8d\\ufe8e]","\u0627"); // alef
    rules.put("[\\ufe8f\\ufe90\\ufe91\\ufe92]","\u0628"); // beh
    rules.put("[\\ufe93\\ufe94]","\u0629"); // teh marbuta
    rules.put("[\\ufe95\\ufe96\\ufe97\\ufe98]","\u062a"); // teh
    rules.put("[\\ufe99\\ufe9a\\ufe9b\\ufe9c]","\u062b"); // theh
    rules.put("[\\ufe9d\\ufe9e\\ufe9f\\ufea0]","\u062c"); // jeem
    rules.put("[\\ufea1\\ufea2\\ufea3\\ufea4]","\u062d"); // haa
    rules.put("[\\ufea5\\ufea6\\ufea7\\ufea8]","\u062e"); // khaa
    rules.put("[\\ufea9\\ufeaa]","\u062f"); // dal
    rules.put("[\\ufeab\\ufeac]","\u0630"); // dhal
    rules.put("[\\ufead\\ufeae]","\u0631"); // reh
    rules.put("[\\ufeaf\\ufeb0]","\u0632"); // zain
    rules.put("[\\ufeb1\\ufeb2\\ufeb3\\ufeb4]","\u0633"); // seen
    rules.put("[\\ufeb5\\ufeb6\\ufeb7\\ufeb8]","\u0634"); // sheen
    rules.put("[\\ufeb9\\ufeba\\ufebb\\ufebc]","\u0635"); // sad
    rules.put("[\\ufebd\\ufebe\\ufebf\\ufec0]","\u0636"); // dad
    rules.put("[\\ufec1\\ufec2\\ufec3\\ufec4]","\u0637"); // tah
    rules.put("[\\ufec5\\ufec6\\ufec7\\ufec8]","\u0638"); // zah
    rules.put("[\\ufec9\\ufeca\\ufecb\\ufecc]","\u0639"); // ain
    rules.put("[\\ufecd\\ufece\\ufecf\\ufed0]","\u063a"); // ghain
    rules.put("[\\ufed1\\ufed2\\ufed3\\ufed4]","\u0641"); // feh
    rules.put("[\\ufed5\\ufed6\\ufed7\\ufed8]","\u0642"); // qaf
    rules.put("[\\ufed9\\ufeda\\ufedb\\ufedc]","\u0643"); // kaf
    rules.put("[\\ufedd\\ufede\\ufedf\\ufee0]","\u0644"); // ghain
    rules.put("[\\ufee1\\ufee2\\ufee3\\ufee4]","\u0645"); // meem
    rules.put("[\\ufee5\\ufee6\\ufee7\\ufee8]","\u0646"); // noon
    rules.put("[\\ufee9\\ufeea\\ufeeb\\ufeec]","\u0647"); // heh
    rules.put("[\\ufeed\\ufeee]","\u0648"); // waw
    rules.put("[\\ufeef\\ufef0]","\u0649"); // alef maksura
    rules.put("[\\ufef1\\ufef2\\ufef3\\ufef4]","\u064a"); // yeh
    rules.put("[\\ufef5\\ufef6]","\u0644\u0622");  // ligature: lam and alef with madda above
    rules.put("[\\ufef7\\ufef8]","\u0644\u0623");  // ligature: lam and alef with hamza above
    rules.put("[\\ufef9\\ufefa]","\u0644\u0625"); // ligature: lam and alef with hamza below
    rules.put("[\\ufefb\\ufefc]","\u0644\u0627"); // ligature: lam and alef

    return rules;

  }


  public static Map<String,String> getArabicIBMNormalizerMap(){

    Map<String,String> rules = Generics.newHashMap();

    try{
      rules.put("[\\u0622\\u0623\\u0625]","\u0627"); // hamza normalization: maddah-n-alef, hamza-on-alef, hamza-under-alef mapped to bare alef

      rules.put("[\\u0649]","\u064A");  // 'alif maqSuura mapped to yaa

      rules.put("[\\u064B\\u064C\\u064D\\u064E\\u064F\\u0650\\u0651\\u0652\\u0653\\u0670]","");  //  fatHatayn, Dammatayn, kasratayn, fatHa, Damma, kasra, shaddah, sukuun, and dagger alef (delete)

      rules.put("\\u0640(?=\\s*\\S)",""); // tatweel, delete except when trailing
      rules.put("(\\S)\\u0640","$1"); // tatweel, delete if preceeded by non-white-space


      rules.put("[\\ufeff\\u00a0]"," "); // white space normalization

      // punctuation normalization

      rules.put("\\u060c",","); // Arabic comma
      rules.put("\\u061b",";"); // Arabic semicolon
      rules.put("\\u061f","?"); // Arabic question mark
      rules.put("\\u066a","%"); // Arabic percent sign
      rules.put("\\u066b","."); // Arabic decimal separator
      rules.put("\\u066c",","); // Arabic thousand separator (comma)
      rules.put("\\u066d","*"); // Arabic asterisk
      rules.put("\\u06d4","."); // Arabic full stop

      // Arabic/Arabic indic/eastern Arabic/ digits normalization

      rules.put("[\\u0660\\u06f0\\u0966]","0");
      rules.put("[\\u0661\\u06f1\\u0967]","1");
      rules.put("[\\u0662\\u06f2\\u0968]","2");
      rules.put("[\\u0663\\u06f3\\u0969]","3");
      rules.put("[\\u0664\\u06f4\\u096a]","4");
      rules.put("[\\u0665\\u06f5\\u096b]","5");
      rules.put("[\\u0666\\u06f6\\u096c]","6");
      rules.put("[\\u0667\\u06f7\\u096d]","7");
      rules.put("[\\u0668\\u06f8\\u096e]","8");
      rules.put("[\\u0669\\u06f9\\u096f]","9");

      // Arabic combining hamza above/below and dagger(superscript)  alef
      rules.put("[\\u0654\\u0655\\u0670]","");

      // replace yaa followed by hamza with hamza on kursi (yaa)
      rules.put("\\u064A\\u0621","\u0626");

      // Normalization Rules Suggested by Ralf Brown (CMU):


      rules.put("\\u2013","-"); // EN-dash to ASCII hyphen
      rules.put("\\u2014","--"); // EM-dash to double ASII hyphen

      // code point 0x91 - latin-1 left single quote
      // code point 0x92 - latin-1 right single quote
      // code point 0x2018 = left single quote; convert to ASCII single quote
      // code point 0x2019 = right single quote; convert to ASCII single quote

      rules.put("[\\u0091\\u0092\\u2018\\u2019]","\'");

      // code point 0x93 - latin-1 left double quote
      // code point 0x94 - latin-1 right double quote
      // code points 0x201C/201D = left/right double quote -> ASCII double quote

      rules.put("[\\u0093\\u0094\\u201C\\u201D]","\"");

    }catch(Exception e){
      log.info("Caught exception creating Arabic normalizer map: " + e.toString() );
    }

    return rules;
  }


  /** This will normalize a Unicode String by applying all the normalization rules from the IBM normalization and
   *    conversion from Presentation to Logical from.
   *
   *
   *  @param in The String to be normalized
   */
  public static String normalize(String in) {

    Map<String,String> ruleMap = getArabicIBMNormalizerMap();   //Get the IBM Normalization rules

    ruleMap.putAll(presToLogicalMap());   //  Get the presentation to logical form rules

    Set<Map.Entry<String, String>> rules = ruleMap.entrySet();

    Iterator<Entry<String, String>> ruleIter = rules.iterator();

    String out = in;

    //Iteratively apply each rule to the string.
    while(ruleIter.hasNext()){
      Map.Entry<String,String> thisRule = ruleIter.next();
      out = out.replaceAll(thisRule.getKey(),thisRule.getValue());
    }

    return out;
  }


  public static void main(String[] args) throws IOException {

    Properties p = StringUtils.argsToProperties(args);

    if (p.containsKey("input")){
      FileInputStream fis = new FileInputStream(p.getProperty("input"));
      InputStreamReader isr = new InputStreamReader(fis,"UTF-8");

      BufferedReader reader = new BufferedReader(isr);
      String thisLine;
      while( (thisLine = reader.readLine()) != null){
        EncodingPrintWriter.out.println(normalize(thisLine),"UTF-8");
      }

    }

  }



}
