package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.util.Function;

import java.util.HashMap;

/**
 * A map from POS tag to POS tag(s) (string to string(s)).  Both 1->many and 1->1 mappings are
 * supported.  Current implementation doesn't allow for sensitivity to
 * the specific word being mapped.  Considerable room exists for
 * refinement.
 *
 * @author Roger Levy
 * @author Sebastian Pado
 */

public class TagMapper extends HashMap implements Function<TaggedWord,TaggedWord> {

  /**
   * 
   */
  private static final long serialVersionUID = 1000854105522440099L;
  /* tagMap should be a <code>HashMap</code> with a key being a
   * <code>String</code> and a value being an array of
   * <code>String</code>s.
   */
  private HashMap<String,String[]> tagMap = new HashMap<String,String[]>();

  /**
   * The one to many map is straightforward, but at time of writing
   * (December 5, 2002) doesn't have any multi-tagged word class to
   * plug into.
   */
  public String[] mapTagOneToMany(String l) {
    return tagMap.get(l);
  }


  /**
   * one to one map is achieved by just taking the first tag from
   * the list of possible tags and making it the new tag.  Lots of
   * refinement could be done.
   */
  public String mapTagOneToOne(String l) {
    if (l == null) {
      return null;
    } else if (containsKey(l)) {
      String[] s = tagMap.get(l);
      return s[0];
    } else {
      throw new RuntimeException("No mapping present for tag "+l); }
    }


  /**
   * apply() is based on the 1->1 mapping.
   */
  public TaggedWord apply(TaggedWord in) {
    String tIn = in.tag();
    String tOut = mapTagOneToOne(tIn);
    return new TaggedWord(in.word(), tOut);
  }


  /**
   * The 1-argument constructor allows you to use your own
   * tagmap
   */
  public TagMapper(HashMap<String,String[]> t) {
    tagMap = t;
  }

  /**
   * The 0-argument constructor is a default map for crude BNC->Penn
   * Treebank tagset mapping.
   */
  public TagMapper() {
    tagMap.put("AJ0", new String[]{"JJ"});
    tagMap.put("AJC", new String[]{"JJR"});
    tagMap.put("AJS", new String[]{"JJS"});
    tagMap.put("AT0", new String[]{"DT"});
    tagMap.put("AV0", new String[]{"RB", "RBR", "RBS"});
    tagMap.put("AVP", new String[]{"RP"});
    tagMap.put("AVQ", new String[]{"WRB"});
    tagMap.put("CJC", new String[]{"CC"});
    tagMap.put("CJS", new String[]{"IN"});
    tagMap.put("CJT", new String[]{"IN"});
    tagMap.put("CRD", new String[]{"CD"});
    tagMap.put("DPS", new String[]{"PRP$"});
    tagMap.put("DT0", new String[]{"DT", "PDT"});
    tagMap.put("DTQ", new String[]{"WDT"});
    tagMap.put("EX0", new String[]{"EX"});
    tagMap.put("ITJ", new String[]{"UH"});
    tagMap.put("NN0", new String[]{"NN"});
    tagMap.put("NN1", new String[]{"NN"});
    tagMap.put("NN2", new String[]{"NNS"});
    tagMap.put("NP0", new String[]{"NNP", "NNPS"});
    tagMap.put("ORD", new String[]{"JJ"});
    tagMap.put("PNI", new String[]{"NN"});
    tagMap.put("PNP", new String[]{"PRP"});
    tagMap.put("PNQ", new String[]{"WP"});
    tagMap.put("PNX", new String[]{"PRP"});
    tagMap.put("POS", new String[]{"POS"});
    tagMap.put("PRF", new String[]{"IN"});
    tagMap.put("PRP", new String[]{"IN", "TO"});
    tagMap.put("PUL", new String[]{"-LRB-"});
    tagMap.put("PUN", new String[]{".", ",", ":"});
    tagMap.put("PUQ", new String[]{"``", "''"});
    tagMap.put("PUR", new String[]{"-RRB-"});
    tagMap.put("TO0", new String[]{"TO"});
    tagMap.put("UNC", new String[]{"FW"});
    tagMap.put("VBB", new String[]{"VBP"});
    tagMap.put("VBD", new String[]{"VBD"});
    tagMap.put("VBG", new String[]{"VBG"});
    tagMap.put("VBI", new String[]{"VB"});
    tagMap.put("VBN", new String[]{"VBN"});
    tagMap.put("VBZ", new String[]{"VBZ"});
    tagMap.put("VDB", new String[]{"VBP"});
    tagMap.put("VDD", new String[]{"VBP"});
    tagMap.put("VDG", new String[]{"VBG"});
    tagMap.put("VDI", new String[]{"VB"});
    tagMap.put("VDN", new String[]{"VBN"});
    tagMap.put("VDZ", new String[]{"VBZ"});
    tagMap.put("VHD", new String[]{"VBP"});
    tagMap.put("VHG", new String[]{"VBG"});
    tagMap.put("VHI", new String[]{"VB"});
    tagMap.put("VHN", new String[]{"VBN"});
    tagMap.put("VHZ", new String[]{"VBZ"});
    tagMap.put("VVD", new String[]{"VBP"});
    tagMap.put("VVG", new String[]{"VBG"});
    tagMap.put("VVI", new String[]{"VB"});
    tagMap.put("VVN", new String[]{"VBN"});
    tagMap.put("VVZ", new String[]{"VBZ"});
    tagMap.put("XX0", new String[]{"RB"});
  }

  public static void main(String[] args) {
    TagMapper B2T = new TagMapper();
    TaggedWord oldTW = new TaggedWord(args[0], args[1]);
    TaggedWord newTW = B2T.apply(oldTW);
    System.out.println("1->1 mapping: " + oldTW.toString() + " -> " + newTW.toString());
    System.out.print("1->many mapping: " + args[1] + " ->");
    String[] newTags = B2T.mapTagOneToMany(args[1]);
    for (String newTag : newTags) {
      System.out.print(" ");
      System.out.print(newTag);
    }
    System.out.println();
  }

}
