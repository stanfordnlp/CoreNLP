package edu.stanford.nlp.tagger.maxent;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.tagger.common.TaggerConstants;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.LabeledScoredTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;

/** Convert POS tagging on a Penn Tree to the Medline tagset defined by
 *
 *
 *  @author Anna Rafferty
 */
public class MedlinePennTreeTransformer implements TreeTransformer {

  /** Tags shared by Penn and Medline where no conversion is necessary */
  private static final String[] sharedTags = {"CC","EX","JJ","JJR","NN","NNS","NNP","TO",".",",",":","(",")","''","``",TaggerConstants.EOS_TAG };
  private static final List<String> sharedTagsList = Arrays.asList(sharedTags);

  private static final String[] medlineSubs = {"DD","MC","VM","DB","GE","PNG","RR","RRR","RRT", "JJT"};
  private static final List<String> medlineSubsList = Arrays.asList(medlineSubs);
  private static final String[] pennSubs = {"DT","CD","MD","PDT","POS","PRP$","RB","RBR","RBS","JJS"};
  private static final List<String> pennSubsList = Arrays.asList(pennSubs);

  /** Assumes we have a penn tree, we want to go to a medline tree.
   *
   *  @param t A tree with normal Penn treebank tags
   *  @return A tree with Medline tags
   */
  public Tree transformTree(Tree t) {
    List<Tree> children = t.getChildrenAsList();
		//String prevTag = ""; // mg: written, but never read
    for (Tree child : children) {
      //TODO: handle RP

      if (child.isPreTerminal()) {
        String word = child.firstChild().value();
        String curTag = child.value();
        String newTag = pennToMedline(curTag, word);
        if (newTag != null) {
          //simple tag transformation is sufficient
        } else {
          if(curTag.startsWith("IN")) {
            String parentTag = t.value();
            if(parentTag.equals("ADJP") || parentTag.equals("WHPP") || parentTag.equals("PP") || parentTag.equals("NP") || parentTag.equals("ADVP"))
              newTag = "II";
            else if(parentTag.equals("S"))
              newTag = "CS";
            else if(parentTag.equals("QP")) {
              if(word.equals("than"))
                newTag = "CSN";
              else
                newTag = "II";
            }
            else if(parentTag.equals("CONJP"))
              newTag = "CS";
            else if(parentTag.equals("SBAR")) {
              if(word.equals("that"))
                newTag = "CST";
              else
                newTag = "CS";
            }
            else if(parentTag.equals("WHNP")) {
              if(word.equalsIgnoreCase("that") || word.equalsIgnoreCase("which"))
                newTag = "CST";
            } else if(parentTag.equals("VP")) {
              newTag = "II";//i think this might be an error in the treebank - things like "complicated" show up marked IN
            } else if(parentTag.equals("WHADVP")) {
              newTag = "PNR";
            } else {//default
              newTag = "II";
            }
          }
          if(curTag.startsWith("WRB")) {
            String parentTag = t.value();
            if(parentTag.equals("WHADVP"))//not sure if this is right //&& (word.equalsIgnoreCase("where") || word.equalsIgnoreCase("whereby") || word.equalsIgnoreCase("whenever")))
              newTag = "PNR";
            else if(parentTag.equals("WHNP")) //not sure if this is right
              newTag = "PNR";
          }
        }
        if(newTag == null) {
          System.err.println("Error converting " + curTag + " in: ");
          t.pennPrint();
        } else {
          child.setValue(newTag);
        }
      }
    }

    return t;
  }

  private static String pennToMedline(String tag, String word) {
    word = word.toLowerCase();//to avoid doing equalsIgnoreCase

    if (sharedTagsList.contains(tag)) {
      return tag;
    } else if(pennSubsList.contains(tag)){
      return medlineSubsList.get(pennSubsList.indexOf(tag));
    } else if(tag.equals("FW")) {//start of non-trivial substitutions
      //there seems to be no equivalent tag in medpost - we augment medpost tagset
      return "FW";
    } else if (tag.equals("LS")) {
      //there seems to be no equivalent tag in medpost - we augment medpost tagset
      return "LS";
    } else if (tag.equals("NNPS")) {
      //this is a lossy conversion
      return "NNP";
    } else if(tag.equals("PRP")) {
      return "PND";
    } else if (tag.equals("UH")) {
      //there seems to be no equivalent tag in medpost - we augment medpost tagset
      return "UH";
    } else if (tag.equals("VBP")) {
      //subcategorization of have, be, and do
      if (word.equals("be") || word.equals("am") || word.equals("are"))
        return "VBB";
      if (word.equals("do"))
        return "VDB";
      if (word.equals("have"))
        return "VHB";
      return "VVB";
    } else if (tag.equals("VBD")) {
      if (word.equals("was") || word.equals("were"))
        return "VBD";
      if (word.equals("did"))
        return "VDD";
      if (word.equals("had"))
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
    } else if(tag.equals("SYM")) {
      return "SYM";
    }else if(tag.equals("-RRB-")) {
      return ")";
    } else if(tag.equals("-LRB-")) {
      return "(";
    } else if(tag.equals("RP")) {
      //augment tag set
      return "RP";
    } else {
      //throw new RuntimeException("Couldn't convert from Penn to Medline: " + tag + " with word " + word);
    }
    return null;//"ERROR: " + tag + " with word " + word;
  }

  public static void main(String[] args) {
    String encoding = "UTF-8";
    String filename = args[0];
    try {
      TreeTransformer mpt = new MedlinePennTreeTransformer();
      TreeReaderFactory trf = new LabeledScoredTreeReaderFactory();
      final DiskTreebank treebank = new DiskTreebank(trf,encoding);
      treebank.loadPath(filename);
      for(Tree t : treebank) {
        t.transform(mpt);
      }
    } catch(Exception e) {
      System.err.println("Error: " + filename);
      e.printStackTrace();
    }

  }

}
