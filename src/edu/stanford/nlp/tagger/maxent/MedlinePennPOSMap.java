package edu.stanford.nlp.tagger.maxent;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;

import edu.stanford.nlp.tagger.common.TaggerConstants;

/**
 * Class to convert Medline tags to Penn tags and vice versa.  Main method will process a file and convert all tags;
 * static methods do conversion within a program. medlineToPenn is based off of the script tag2penn.perl, included with
 * the medpost distribution.  When converting to Medline, we add three tags to the traditional medline tagset since there
 *  are no equivalent Medline tags: FW (foreigh word), UH (interjection), and LS (list item marker).
 * @author Anna Rafferty
 *
 */
public class MedlinePennPOSMap {

  /*
  private static final String[] medlineTags = {"CC","CS","CSN","CST","DB","DD","EX","GE","II","JJ","JJR","JJT","MC","NN","NNP","NNS","PN","PND",
    "PNG","PNR","RR","RRR","RRT","SYM","TO","VM","VBB","VBD","VBG","VBI","VBN","VBZ","VDB","VDD","VDG",
    "VDI","VDN","VDZ","VHB","VHD","VHG","VHI","VHN","VHZ","VVB","VVD","VVG","VVI","VVZ","VVNJ","VVGJ",
    "VVGN","(",")",",",".",":","``","''"};

  private static final String[] pennTags = {"CC","CD","DT","EX","FW","IN","JJ","JJR","JJS","LS","MD","NN","NNS","NNP","NNPS","PDT","POS","PRP",
    "PRP$","RB","RBR","RBS","RP","SYM","TO","UH","VB","VBD","VBG","VBN","VBP","VBZ","WDT","WP","WP$","WRB",
    "#","$","''","(",")",",",".",":","``"};
  */

  /** Tags shared by Penn and Medline where no conversion is necessary */
  private static final String[] sharedTags = {"CC","EX","JJ","JJR","NN","NNS","NNP","TO",".",",",":","(",")","''","``",TaggerConstants.EOS_TAG };
  private static final List<String> sharedTagsList = Arrays.asList(sharedTags);

  private static final String[] medlineSubs = {"DD","MC","VM","DB","GE","PNG","RR","RRR","RRT", "JJT"};
  private static final List<String> medlineSubsList = Arrays.asList(medlineSubs);
  private static final String[] pennSubs = {"DT","CD","MD","PDT","POS","PRP$","RB","RBR","RBS","JJS"};
  private static final List<String> pennSubsList = Arrays.asList(pennSubs);

  private static final HashMap<String, String> medlineToPennMap = new HashMap<String, String>();
  static {
    medlineToPennMap.put("VVGN", "NN");
    medlineToPennMap.put("CS", "IN");
    medlineToPennMap.put("CSN", "IN");
    medlineToPennMap.put("CST", "IN");
    medlineToPennMap.put("II", "IN");
    medlineToPennMap.put("DD", "DT");
    medlineToPennMap.put("DB", "PDT");
    medlineToPennMap.put("CC", "CC");
    medlineToPennMap.put("MC", "CD");
    medlineToPennMap.put("GE", "POS");
    medlineToPennMap.put("VVNJ", "JJ");
    medlineToPennMap.put("VVGJ", "JJ");
    medlineToPennMap.put("JJ", "JJ");
    medlineToPennMap.put("JJT", "JJS");
    medlineToPennMap.put("RR", "RB");
    medlineToPennMap.put("RRR", "RBR");
    medlineToPennMap.put("RRT", "RBS");
    medlineToPennMap.put("PN", "PRP");
    medlineToPennMap.put("PND", "PRP");
    medlineToPennMap.put("PNG", "PRP$");
    medlineToPennMap.put("PNR", "WDT");
    medlineToPennMap.put("VM", "MD");
    medlineToPennMap.put("VBB", "VBP");
    medlineToPennMap.put("VHB", "VBP");
    medlineToPennMap.put("VDB", "VBP");
    medlineToPennMap.put("VVB", "VBP");
    medlineToPennMap.put("VBI", "VB");
    medlineToPennMap.put("VHI", "VB");
    medlineToPennMap.put("VDI", "VB");
    medlineToPennMap.put("VVI", "VB");
    medlineToPennMap.put("VBD", "VBD");
    medlineToPennMap.put("VHD", "VBD");
    medlineToPennMap.put("VDD", "VBD");
    medlineToPennMap.put("VVD", "VBD");
    medlineToPennMap.put("VBN", "VBN");
    medlineToPennMap.put("VHN", "VBN");
    medlineToPennMap.put("VDN", "VBN");
    medlineToPennMap.put("VVN", "VBN");
    medlineToPennMap.put("VBG", "VBG");
    medlineToPennMap.put("VHG", "VBG");
    medlineToPennMap.put("VDG", "VBG");
    medlineToPennMap.put("VVG", "VBG");
    medlineToPennMap.put("VBZ", "VBZ");
    medlineToPennMap.put("VHZ", "VBZ");
    medlineToPennMap.put("VDZ", "VBZ");
    medlineToPennMap.put("VVZ", "VBZ");
    // this is just something internal to the tagger, not something
    // external, so we can just reuse the normal tagger EOS tag
    medlineToPennMap.put(TaggerConstants.EOS_TAG, TaggerConstants.EOS_TAG);
    medlineToPennMap.put("SYM", "SYM");
    medlineToPennMap.put("VVGN+", "NN");
    medlineToPennMap.put("CS+", "IN");
    medlineToPennMap.put("CSN+", "IN");
    medlineToPennMap.put("CST+", "IN");
    medlineToPennMap.put("II+", "IN");
    medlineToPennMap.put("DD+", "DT");
    medlineToPennMap.put("DB+", "PDT");
    medlineToPennMap.put("CC+", "CC");
    medlineToPennMap.put("MC+", "CD");
    medlineToPennMap.put("GE+", "POS");
    medlineToPennMap.put("VVNJ+", "JJ");
    medlineToPennMap.put("VVGJ+", "JJ");
    medlineToPennMap.put("JJ+", "JJ");
    medlineToPennMap.put("JJT+", "JJS");
    medlineToPennMap.put("RR+", "RB");
    medlineToPennMap.put("RRR+", "RBR");
    medlineToPennMap.put("RRT+", "RBS");
    medlineToPennMap.put("PN+", "PRP");
    medlineToPennMap.put("PND+", "PRP");
    medlineToPennMap.put("PNG+", "PRP$");
    medlineToPennMap.put("PNR+", "WDT");
    medlineToPennMap.put("VM+", "MD");
    medlineToPennMap.put("VBB+", "VBP");
    medlineToPennMap.put("VHB+", "VBP");
    medlineToPennMap.put("VDB+", "VBP");
    medlineToPennMap.put("VVB+", "VBP");
    medlineToPennMap.put("VBI+", "VB");
    medlineToPennMap.put("VHI+", "VB");
    medlineToPennMap.put("VDI+", "VB");
    medlineToPennMap.put("VVI+", "VB");
    medlineToPennMap.put("VBD+", "VBD");
    medlineToPennMap.put("VHD+", "VBD");
    medlineToPennMap.put("VDD+", "VBD");
    medlineToPennMap.put("VVD+", "VBD");
    medlineToPennMap.put("VBN+", "VBN");
    medlineToPennMap.put("VHN+", "VBN");
    medlineToPennMap.put("VDN+", "VBN");
    medlineToPennMap.put("VVN+", "VBN");
    medlineToPennMap.put("VBG+", "VBG");
    medlineToPennMap.put("VHG+", "VBG");
    medlineToPennMap.put("VDG+", "VBG");
    medlineToPennMap.put("VVG+", "VBG");
    medlineToPennMap.put("VBZ+", "VBZ");
    medlineToPennMap.put("VHZ+", "VBZ");
    medlineToPennMap.put("VDZ+", "VBZ");
    medlineToPennMap.put("VVZ+", "VBZ");
  }
  /**
   * Takes a Penn tag and using that and the word it applies to,
   * returns the appropriate medline tag.
   * @param tag Penn tag
   * @return Medline tag
   */
  public static String pennToMedline(String tag, String word, String prevTag) {
    word = word.toLowerCase();//to avoid doing equalsIgnoreCase

    if(sharedTagsList.contains(tag)) {
      return tag;
    } else if(pennSubsList.contains(tag)){
      return medlineSubsList.get(pennSubsList.indexOf(tag));
    } else if(tag.equals("FW")) {//start of non-trivial substitutions
      //there seems to be no equivalent tag in medpost - we augment medpost tagset
      return "FW";
    } else if(tag.equals("IN")) {
      if(word.equals("than"))
        return "CSN";//this is slightly lossy, since "as" occasionally gets "CSN" too - very rare though, maybe mis-tags?
      if(word.equals("that") || word.equals("whether"))
        return "CST";
      //figure something out for CS vs. II -> words appear in both
      return "II";
    } else if(tag.equals("LS")) {
      //there seems to be no equivalent tag in medpost - we augment medpost tagset
      return "LS";
    } else if(tag.equals("NNPS")) {
      //this is a lossy conversion
      return "NNP";
    } else if(tag.equals("PRP")) {
      return "PND";
    } else if(tag.equals("RP")) {
      //this is the + issue - for now, just take the previous tag
      //should really be: add a plus to the previous tag, take the previous tag as our own
      return prevTag;
    } else if(tag.equals("UH")) {
      //there seems to be no equivalent tag in medpost - we augment medpost tagset
      return "UH";
    } else if(tag.equals("VBP")) {
      //subcategorization of have, be, and do
      if(word.equals("be") || word.equals("am") || word.equals("are"))
        return "VBB";
      if(word.equals("do"))
        return "VDB";
      if(word.equals("have"))
        return "VHB";
      return "VVB";
    } else if(tag.equals("VBD")) {
      if(word.equals("was") || word.equals("were"))
        return "VBD";
      if(word.equals("did"))
        return "VDD";
      if(word.equals("had"))
        return "VHD";
      return "VVD";
    } else if(tag.equals("VBG")) {
      if(word.equals("being"))
        return "VBG";
      if(word.equals("doing"))
        return "VDG";
      if(word.equals("having"))
        return "VHG";
      return "VVG";
    } else if(tag.equals("VBN")) {
      if(word.equals("been"))
        return "VBN";
      if(word.equals("done"))
        return "VDN";
      if(word.equals("had"))
        return "VHN";
      return "VVN";
    } else if(tag.equals("VBZ")) {
      if(word.equals("is"))
        return "VBZ";
      if(word.equals("does"))
        return "VDZ";
      if(word.equals("has"))
        return "VHZ";
      return "VVZ";
    } else if(tag.equals("VB")) {//infinitive forms
      if(word.equals("be"))
        return "VBI";
      if(word.equals("do"))
        return "VDI";
      if(word.equals("have"))
        return "VHI";
      return "VVI";
    } else if(tag.equals("WDT")) {
      return "PNR";
    } else if(tag.equals("WP")) {
      if(word.equals("that"))
        return "PND";
      if(word.equals("what"))
        return "PND";
      return "PNR";
    } else if(tag.equals("WP$")) {
      return "PNR";
    } else if(tag.equals("WRB")) {
      if(word.equals("how") || word.equals("why"))
        return "CST";
      if(word.equals("however"))
        return "RR";
      if(word.equals("when"))
        return "CS";
    } else if(tag.equals("#")) {
      return "SYM";
    } else if(tag.equals("$")) {
      return "SYM";
    } else {
      throw new RuntimeException("Couldn't convert from Penn to Medline: " + tag + " with word " + word);
    }
    return "ERROR: " + tag + " with word " + word;
  }

  public static String medlineToPenn(String tag, String word) {
    word = word.toLowerCase();//to avoid doing equalsIgnoreCase
    //first we do word checks - these override anything in our map
    if(word.equals("to"))
      return "TO";
    if(word.equals("what") || word.equals("who") || word.equals("whom")) {
      return "WP";
    }
    if(word.equals("how") || word.equals("however") || word.equals("when") || word.equals("whenever") || word.equals("where") || word.equals("whereby") || word.equals("why")) {
      return "WRB";
    }
    if(tag.equals("#"))
      return "SYM";
    if(tag.equals("$"))
      return "SYM";
    if(sharedTagsList.contains(tag))
      return tag;
    String newTag = medlineToPennMap.get(tag);
    if(newTag != null && newTag.equals("IN") && (tag.equals("CS") || tag.equals("II"))) {
      System.out.println("Word: " + word + "; tag: " + tag);

    }
    return newTag;
  }

  public static List<String> medlineToPenn(List<String> sentence, List<String> tags) {
    List<String> newTags = new ArrayList<String>();
    for(int i = 0; i < sentence.size(); i++) {
      String word = sentence.get(i);
      newTags.add(medlineToPenn(tags.get(i), word));
    }
    return newTags;
  }

  public static List<String> pennToMedline(List<String> sentence, List<String> tags) {
    List<String> newTags = new ArrayList<String>();
    String prevTag = "";
    for(int i = 0; i < sentence.size(); i++) {
      String word = sentence.get(i);
      String newTag = pennToMedline(tags.get(i), word, prevTag);
      newTags.add(newTag);
      prevTag = newTag;
    }
    return newTags;
  }

  public static void main(String[] args) {
    String direction = args[0];
    String filename = args[1];
    String delimiter = "_";
    List<String> sentence = new ArrayList<String>();
    List<String> tagsArr = new ArrayList<String>();
    String w1;
    String t1;
    String eosTag = TaggerConstants.EOS_TAG;
    String eosWord = TaggerConstants.EOS_WORD;
    String s;
    int index;
    try {
      BufferedReader rf = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));

      while ((s = rf.readLine()) != null) {
        StringTokenizer st = new StringTokenizer(s);
        while (st.hasMoreTokens()) {// find the sentence there

          String token = st.nextToken();
          index = token.indexOf(delimiter);

          if(index == -1)
            continue;
          w1 = token.substring(0, index);
          sentence.add(w1);
          t1 = token.substring(index + 1);
          tagsArr.add(t1);
        }

        //the sentence is read already, add eos
        sentence.add(eosWord);
        tagsArr.add(eosTag);
        if(direction.equals("m2p"))
          medlineToPenn(sentence, tagsArr);
        else
          pennToMedline(sentence, tagsArr);
        System.out.println();
      }
    } catch(Exception e) {
      e.printStackTrace();
    }
  }



}
