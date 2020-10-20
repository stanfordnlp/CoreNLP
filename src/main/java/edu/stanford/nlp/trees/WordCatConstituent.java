package edu.stanford.nlp.trees;


/**
 * A class storing information about a constituent in a character-based tree.
 * This is used for evaluation of character-based Chinese parsing.
 * The constituent can be of type "word" (for words), "cat" (for phrases) or "tag" (for POS).
 * @author Galen Andrew
 */
public class WordCatConstituent extends LabeledConstituent {
  public String type;
  public static final String wordType = "word";
  public static final String tagType = "tag";
  public static final String catType = "cat";
  // this one is for POS tag of correctly segmented words only
  public static final String goodWordTagType = "goodWordTag";

  public WordCatConstituent(Tree subTree, Tree root, String type) {
    setStart(Trees.leftEdge(subTree, root));
    setEnd(Trees.rightEdge(subTree, root));
    setFromString(subTree.value());
    this.type = type;
  }


}
