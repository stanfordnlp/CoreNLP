package edu.stanford.nlp.pipeline;

/**
 * Wrapper around a CoreMap representing a entity mention.  Adds some helpful methods.
 *
 */

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.*;

public class CoreEntityMention {

  private CoreMap entityMentionCoreMap;
  private CoreSentence sentence;

  public CoreEntityMention(CoreSentence mySentence, CoreMap coreMapEntityMention) {
    this.sentence = mySentence;
    this.entityMentionCoreMap = coreMapEntityMention;
  }

  /** get the underlying CoreMap if need be **/
  public CoreMap coreMap() {
    return entityMentionCoreMap;
  }

  /** get this entity mention's sentence **/
  public CoreSentence sentence() {
    return sentence;
  }

  /** full text of the mention **/
  public String text() {
    return this.entityMentionCoreMap.get(CoreAnnotations.TextAnnotation.class);
  }

  /** the list of tokens for this entity mention **/
  public List<CoreLabel> tokens() { return this.entityMentionCoreMap.get(CoreAnnotations.TokensAnnotation.class); }

  /** char offsets of mention **/
  public Pair<Integer,Integer> charOffsets() {
    int beginCharOffset = this.entityMentionCoreMap.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int endCharOffset = this.entityMentionCoreMap.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    return new Pair<>(beginCharOffset,endCharOffset);
  }

  /** return the type of the entity mention **/
  public String entityType() {
    return this.entityMentionCoreMap.get(CoreAnnotations.EntityTypeAnnotation.class);
  }

  /** return a map of labels to confidences **/
  public Map<String, Double> entityTypeConfidences() {
    return this.entityMentionCoreMap.get(CoreAnnotations.NamedEntityTagProbsAnnotation.class);
  }

  /** return the entity this entity mention is linked to **/
  public String entity() {
    return this.entityMentionCoreMap.get(CoreAnnotations.WikipediaEntityAnnotation.class);
  }

  /** return the canonical entity mention for this entity mention **/
  public Optional<CoreEntityMention> canonicalEntityMention() {
    CoreDocument myDocument = sentence.document();
    Optional<Integer> canonicalEntityMentionIndex =
        Optional.ofNullable(coreMap().get(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class));
    return canonicalEntityMentionIndex.isPresent() ?
        Optional.of(sentence.document().entityMentions().get(canonicalEntityMentionIndex.get())) :
        Optional.empty();
  }

  @Override
  public String toString() {
    return coreMap().toString();
  }
}
