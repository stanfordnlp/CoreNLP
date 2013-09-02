package edu.stanford.nlp.ling;

/**
 * A class for sentence normalization.  This one knows about the funny
 * things in Penn Treebank pos files -- like lots of equals signs and
 * square brackets.  Additionally, it recodes brackets as strings like
 * -LRB- so as to make them the same as the encoding used in the parsed
 * files.
 * A Singleton.
 *
 * @author Christopher Manning
 */
public class PennSentenceMrgNormalizer<T extends HasWord> extends PennSentenceNormalizer<T> {

  public PennSentenceMrgNormalizer() {
    super();
  }


  /**
   * Normalizes a read string word (and maybe intern it).
   */
  @Override
  public String normalizeString(String word) {
    if (word.equals("(/(")) {
      return "-LRB-/-LRB-";
    } else if (word.equals(")/)")) {
      return "-RRB-/-RRB-";
    } else if (word.equals("{/(")) {
      return "-LCB-/-LRB-";
    } else if (word.equals("}/)")) {
      return "-RCB-/-RRB-";
    }
    return word;
    // no point in doing intern(); as still word/tag
  }

}
