package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.util.List;

import edu.stanford.nlp.ie.machinereading.structure.RelationMentionFactory;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.NFLRelationMention;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;

public class NFLRelationMentionFactory extends RelationMentionFactory {
  private static final long serialVersionUID = 7802607087717797129L;

  public RelationMention constructRelationMention(
      String objectId, 
        CoreMap sentence,
        Span span,
        String type,
        String subtype,
        List<ExtractionObject> args,
        Counter<String> probs) {
    NFLRelationMention relation = new NFLRelationMention(
          objectId,
          sentence,
          span,
          type,
          subtype,
          args);
      relation.setTypeProbabilities(probs);
      return relation;
  }
}
