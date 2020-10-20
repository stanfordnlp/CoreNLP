package edu.stanford.nlp.ie.machinereading.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity holds a map from entity to entity mentions. Assumes a single dataset.
 * 
 */
public class Entity {

  private Map<String, List<EntityMention>> entityToEntityMentions = new HashMap<>();

  /**
   * 
   * @param entity
   *          - identifier for entity, could be entity id or common string that
   *          all entity mentions of this entity share
   * @param em - entity mention
   */
  public void addEntity(String entity, EntityMention em) {
    List<EntityMention> mentions = this.entityToEntityMentions.get(entity);
    if (mentions == null) {
      mentions = new ArrayList<>();
      this.entityToEntityMentions.put(entity, mentions);
    }
    mentions.add(em);
  }

  public List<EntityMention> getEntityMentions(String entity) {
    List<EntityMention> retVal = this.entityToEntityMentions.get(entity);
    return retVal != null ? retVal : Collections.<EntityMention> emptyList();
  }
}
