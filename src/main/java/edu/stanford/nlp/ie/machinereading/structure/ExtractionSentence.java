package edu.stanford.nlp.ie.machinereading.structure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.Word;

/**
 * A RelationsSentence contains all the relations for a given sentence
 * @author Mihai
 */
public class ExtractionSentence implements Serializable {

  private static final long serialVersionUID = 87958315651919036L;

  /**
   * Id of the textual document containing this sentence
   */
  private final String documentId;

  /** Text of this sentence */
  private String textContent;

  /**
   * List of relation mentions in this sentence
   * There are no ordering guarantees
   */
  private final List<RelationMention> relationMentions;

  /**
   * List of entity mentions in this sentence
   * There are no ordering guarantees
   */
  private final List<EntityMention> entityMentions;

  /**
   * List of event mentions in this sentence
   * There are no ordering guarantees
   */
  private final List<EventMention> eventMentions;

  public ExtractionSentence (String docid, String textContent){
    this.documentId = docid;
    this.textContent = textContent;
    this.entityMentions = new ArrayList<>();
    this.relationMentions = new ArrayList<>();
    this.eventMentions = new ArrayList<>();
  }

  public ExtractionSentence(ExtractionSentence original) {
    this.documentId = original.documentId;
    this.relationMentions = new ArrayList<>(original.relationMentions);
    this.entityMentions = new ArrayList<>(original.entityMentions);
    this.eventMentions = new ArrayList<>(original.eventMentions);
    this.textContent = original.textContent;
  }

  public void addEntityMention(EntityMention arg) {
    this.entityMentions.add(arg);
  }

  public void addEntityMentions(Collection<EntityMention> args) {
    this.entityMentions.addAll(args);
  }

  public void addRelationMention(RelationMention rel) {
    relationMentions.add(rel);
  }

  public List<RelationMention> getRelationMentions() {
    return Collections.unmodifiableList(relationMentions);
  }

  public void setRelationMentions(List<RelationMention> rels) {
    relationMentions.clear();
    relationMentions.addAll(rels);
  }

  /**
   * Return the relation that holds between the given entities.
   * Return a relation of type UNRELATED if this sentence contains no relation between the entities.
   */
  public RelationMention getRelation(RelationMentionFactory factory, ExtractionObject ... args) {
    for (RelationMention rel : relationMentions) {
      if (rel.argsMatch(args)){
        return rel;
      }
    }
    return RelationMention.createUnrelatedRelation(factory, args);
  }

  /**
   * Get list of all relations and non-relations between ArgForRelations in this sentence
   * Use with care. This is an expensive call due to getAllUnrelatedRelations, which creates all non-existing relations between all entity mentions
   */
  public List<RelationMention> getAllRelations(RelationMentionFactory factory) {
    List<RelationMention> allRelations = new ArrayList<>(relationMentions);
    allRelations.addAll(getAllUnrelatedRelations(factory));
    return allRelations;
  }

  public List<RelationMention> getAllUnrelatedRelations(RelationMentionFactory factory) {

    List<RelationMention> nonRelations = new ArrayList<>();
    List<RelationMention> allRelations = new ArrayList<>(relationMentions);

    //
    // scan all possible arguments
    //
    for(int i = 0; i < getEntityMentions().size(); i ++){
      for(int j = 0; j < getEntityMentions().size(); j ++){
        if(i == j) continue;
        EntityMention arg1 = getEntityMentions().get(i);
        EntityMention arg2 = getEntityMentions().get(j);
        boolean match = false;
        for (RelationMention rel : allRelations) {
          if (rel.argsMatch(arg1, arg2)) {
            match = true;
            break;
          }
        }
        if ( ! match) {
          RelationMention nonrel = RelationMention.createUnrelatedRelation(factory, arg1, arg2);
          nonRelations.add(nonrel);
          allRelations.add(nonrel);
        }
      }
    }

    return nonRelations;
  }

  public void addEventMention(EventMention event) {
    eventMentions.add(event);
  }

  public List<EventMention> getEventMentions() {
    return Collections.unmodifiableList(eventMentions);
  }

  public void setEventMentions(List<EventMention> events) {
    eventMentions.clear();
    eventMentions.addAll(events);
  }

  public String getTextContent() {
    return textContent;
  }

  /*
  public String getTextContent(Span span) {
    StringBuilder buf = new StringBuilder();
    assert(span != null);
    for(int i = span.start(); i < span.end(); i ++){
      if(i > span.start()) buf.append(" ");
      buf.append(tokens[i].word());
    }
    return buf.toString();
  }
  */

  public void setTextContent(String textContent) {
    this.textContent = textContent;
  }

  // /**
  //  * Returns true if the character offset span is contained within this
  //  * sentence.
  //  * 
  //  * @param span a Span of character offsets
  //  * @return true if the span starts and ends within the sentence
  //  */
  // public boolean containsSpan(Span span) {
  //   int sentenceStart = tokens[0].beginPosition();
  //   int sentenceEnd = tokens[tokens.length - 1].endPosition();
  //   return sentenceStart <= span.start() && sentenceEnd >= span.end();
  // }

  public List<EntityMention> getEntityMentions() {
    return Collections.unmodifiableList(entityMentions);
  }

  public void setEntityMentions(List<EntityMention> newArgs) {
    entityMentions.clear();
    entityMentions.addAll(newArgs);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder(512);
    sb.append("\"" + textContent + "\"");
    sb.append("\n");

    for (RelationMention rel : this.relationMentions) {
      sb.append("\n");
      sb.append(rel);
    }

    // TODO: add event mentions

    return sb.toString();
  }

  public static String tokensToString(Word [] tokens) {
    StringBuilder  sb = new StringBuilder(512);
    for(int i = 0; i < tokens.length; i ++){
      if(i > 0) sb.append(" ");
      Word l = tokens[i];
      sb.append(l.word() + "{" + l.beginPosition() + ", " + l.endPosition() + "}");
    }
    return sb.toString();
  }

  // /**
  //  * Converts an ExtractionSentence to the equivalent List of CoreLabels.
  //  *
  //  * @param addAnswerAnnotation
  //  *          whether to annotate with gold NER tags
  //  * @return the sentence as a List<CoreLabel>
  //  */
  // public List<CoreLabel> toCoreLabels(
  //     boolean addAnswerAnnotation,
  //     Set<String> annotationsToSkip,
  //     boolean useSubTypes) {
  //   Tree completeTree = getTree();
  //   List<CoreLabel> labels = new ArrayList<CoreLabel>();
  //   List<Tree> tokenList = getTree().getLeaves();
  //   for (Tree tree : tokenList) {
  //     Word word = new Word(tree.label());
  //     CoreLabel label = new CoreLabel();
  //     label.set(TextAnnotation.class, word.value());
  //     if (addAnswerAnnotation) {
  //       label.set(AnswerAnnotation.class,
  //           SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
  //     }
  //     label.set(PartOfSpeechAnnotation.class, tree.parent(completeTree).label().value());
  //     labels.add(label);
  //   }

  //   if (addAnswerAnnotation) {
  //     // reset some annotation with answer types
  //     for (EntityMention entity : getEntityMentions()) {
  //       if (annotationsToSkip == null || ! annotationsToSkip.contains(entity.getType())) {
  //         // ignore entities without indices
  //         //if (entity.getSyntacticHeadTokenPosition() >= 0) {
  //         //  labels.get(entity.getSyntacticHeadTokenPosition()).set(
  //         //      AnswerAnnotation.class, entity.getType());
  //         //}
  //         if(entity.getHead() != null){
  //           for(int i = entity.getHeadTokenStart(); i < entity.getHeadTokenEnd(); i ++){
  //             String tag = entity.getType();
  //             if(useSubTypes && entity.getSubType() != null) tag += "-" + entity.getSubType();
  //             labels.get(i).set(AnswerAnnotation.class, tag);
  //           }
  //         }
  //       }
  //     }
  //   }

  //   return labels;
  // }

  public String getDocumentId() { return documentId; }

}