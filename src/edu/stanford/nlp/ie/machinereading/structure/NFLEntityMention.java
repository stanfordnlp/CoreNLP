package edu.stanford.nlp.ie.machinereading.structure;

import edu.stanford.nlp.util.CoreMap;

public class NFLEntityMention extends EntityMention {
  private static final long serialVersionUID = -1059604470220048803L;

  public NFLEntityMention(String objectId,
      CoreMap sentence,
      Span extentSpan,
      Span headSpan,
      String type,
      String subtype,
      String mentionType) {
    super(objectId, sentence, extentSpan, headSpan, type, subtype, mentionType);
  }
  
  /**
   * We need proper hash codes for NFL entities. TODO: Ask David why
   */
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((extentTokenSpan == null) ? 0 : extentTokenSpan.hashCode());
    // note that we don't call the real CoreMap.hashCode since that has issues and was causing infinite loops
    result = prime * result + ((sentence == null) ? 0 : System.identityHashCode(sentence));
    result = prime * result + ((subType == null) ? 0 : subType.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }
}
