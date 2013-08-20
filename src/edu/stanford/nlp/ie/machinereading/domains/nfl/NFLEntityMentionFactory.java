package edu.stanford.nlp.ie.machinereading.domains.nfl;

import edu.stanford.nlp.ie.machinereading.structure.EntityMentionFactory;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.NFLEntityMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.util.CoreMap;

public class NFLEntityMentionFactory extends EntityMentionFactory {
  private static final long serialVersionUID = 568373449595903829L;

  public EntityMention constructEntityMention(
      String objectId,
        CoreMap sentence,
        Span extentSpan,
        Span headSpan,
        String type,
        String subtype,
        String mentionType) {
    return new NFLEntityMention(objectId, sentence, extentSpan, headSpan, type, subtype, mentionType);
  }
}
