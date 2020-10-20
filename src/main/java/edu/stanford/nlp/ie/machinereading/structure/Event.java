package edu.stanford.nlp.ie.machinereading.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event holds a map from event to event mentions. Assumes a single
 * dataset.
 * 
 */
public class Event {

  private Map<String, List<EventMention>> eventToEventMentions = new HashMap<>();

  public void addEntity(String event, EventMention em) {
    List<EventMention> mentions = this.eventToEventMentions.get(event);
    if (mentions == null) {
      mentions = new ArrayList<>();
      this.eventToEventMentions.put(event, mentions);
    }
    mentions.add(em);
  }

  public List<EventMention> getEventMentions(String event) {
    List<EventMention> retVal = this.eventToEventMentions.get(event);
    return retVal != null ? retVal : Collections.<EventMention> emptyList();
  }
}
