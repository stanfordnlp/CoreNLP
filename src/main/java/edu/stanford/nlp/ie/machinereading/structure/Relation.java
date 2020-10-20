package edu.stanford.nlp.ie.machinereading.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Relation holds a map from relation to relation mentions. Assumes a single
 * dataset.
 * 
 */
public class Relation {

  private Map<String, List<RelationMention>> relationToRelationMentions = new HashMap<>();

  public void addRelation(String relation, RelationMention rm) {
    List<RelationMention> mentions = this.relationToRelationMentions
        .get(relation);
    if (mentions == null) {
      mentions = new ArrayList<>();
      this.relationToRelationMentions.put(relation, mentions);
    }
    mentions.add(rm);
  }

  public List<RelationMention> getRelationMentions(String relation) {
    List<RelationMention> retVal = this.relationToRelationMentions
        .get(relation);
    return retVal != null ? retVal : Collections.<RelationMention> emptyList();
  }

}
