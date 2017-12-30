package edu.stanford.nlp.pipeline;

/**
 * Wrapper around a CoreMap representing a entity mention.  Adds some helpful methods.
 *
 */

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

public class CoreEntityMention {

  private CoreMap entityMention;

  public CoreEntityMention(CoreMap coreMapEntityMention) {
    this.entityMention = coreMapEntityMention;
  }

  public String text() {
    return this.entityMention.get(CoreAnnotations.TextAnnotation.class);
  }

}
