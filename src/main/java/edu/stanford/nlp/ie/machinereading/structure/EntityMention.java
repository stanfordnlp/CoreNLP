package edu.stanford.nlp.ie.machinereading.structure;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

/**
 * Each entity mention is described by a type (possibly subtype) and a span of text
 *
 * @author Andrey Gusev
 * @author Mihai
 */
public class EntityMention extends ExtractionObject {

  private static final long serialVersionUID = -2745903102654191527L;

  /** Mention type, if available, e.g., nominal */
  private final String mentionType;
  private String corefID = "-1";

  /**
   * Offsets the head span, e.g., "George Bush" in the extent "the president George Bush"
   * The offsets are relative to the sentence containing this mention
   */
  private Span headTokenSpan;

  /**
   * Position of the syntactic head word of this mention, e.g., "Bush" for the head span "George Bush"
   * The offset is relative the sentence containing this mention
   * Note: use headTokenSpan when sequence tagging entity mentions not this.
   *       This is meant to be used only for event/relation feature extraction!
   */
  private int syntacticHeadTokenPosition;

  private String normalizedName;

  public EntityMention(String objectId,
      CoreMap sentence,
      Span extentSpan,
      Span headSpan,
      String type,
      String subtype,
      String mentionType) {
    super(objectId, sentence, extentSpan, type, subtype);
    this.mentionType = (mentionType != null ? mentionType.intern() : null);
    this.headTokenSpan = headSpan;
    this.syntacticHeadTokenPosition = -1;
    this.normalizedName = null;
  }

  public String getCorefID(){
    return corefID;
  }

  public void setCorefID(String id) {
    this.corefID = id;
  }
  public String getMentionType() { return mentionType; }

  public Span getHead() { return headTokenSpan; }

  public int getHeadTokenStart() {
    return headTokenSpan.start();
  }

  public int getHeadTokenEnd() {
    return headTokenSpan.end();
  }

  public void setHeadTokenSpan(Span s) {
    headTokenSpan = s;
  }

  public void setHeadTokenPosition(int i) {
    this.syntacticHeadTokenPosition = i;
  }

  public int getSyntacticHeadTokenPosition() {
    return this.syntacticHeadTokenPosition;
  }

  public CoreLabel getSyntacticHeadToken() {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    return tokens.get(syntacticHeadTokenPosition);
  }

  public Tree getSyntacticHeadTree() {
    Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    return tree.getLeaves().get(syntacticHeadTokenPosition);
  }

  public String getNormalizedName() { return normalizedName; }
  public void setNormalizedName(String n) { normalizedName = n; }

  /*
  @Override
  public boolean equals(Object other) {
    if(! (other instanceof EntityMention)) return false;
    ExtractionObject o = (ExtractionObject) other;
    if(o.objectId.equals(objectId) && o.sentence == sentence) return true;
    return false;
  }
   */

  @Override
  public boolean equals(Object other) {
    if(! (other instanceof EntityMention)) return false;
    EntityMention otherEnt = (EntityMention) other;
    return equals(otherEnt, true);
  }

  public boolean headIncludes(EntityMention otherEnt, boolean useSubType) {
    return otherEnt.getSyntacticHeadTokenPosition() >= getHeadTokenStart() &&
            otherEnt.getSyntacticHeadTokenPosition() < getHeadTokenEnd() &&
            ((type != null && otherEnt.type != null && type.equals(otherEnt.type)) || (type == null && otherEnt.type == null)) &&
            ( ! useSubType || ((subType != null && otherEnt.subType != null && subType.equals(otherEnt.subType)) || (subType == null && otherEnt.subType == null)));
  }

  public boolean equals(EntityMention otherEnt, boolean useSubType) {
    //
    // two mentions are equal if they are over the same sentence,
    // have the same head span, the same type/subtype, and the same text.
    // We need this for scoring NER, and in various places in KBP
    //
    if(sentence.get(CoreAnnotations.TextAnnotation.class).equals(otherEnt.sentence.get(CoreAnnotations.TextAnnotation.class)) && textEquals(otherEnt) && labelEquals(otherEnt, useSubType)){
      return true;
    }
    /*
  	if(((headTokenSpan != null && headTokenSpan.equals(otherEnt.headTokenSpan)) ||
        (extentTokenSpan != null && extentTokenSpan.equals(otherEnt.extentTokenSpan))) &&
        ((type != null && otherEnt.type != null && type.equals(otherEnt.type)) || (type == null && otherEnt.type == null)) &&
        (! useSubType || ((subType != null && otherEnt.subType != null && subType.equals(otherEnt.subType)) || (subType == null && otherEnt.subType == null))) &&
        AnnotationUtils.getTextContent(sentence, headTokenSpan).equals(AnnotationUtils.getTextContent(otherEnt.getSentence(), otherEnt.headTokenSpan))){
      return true;
    }
     */
    return false;
  }

  /**
   * Compares the labels of the two mentions
   * @param otherEnt
   * @param useSubType
   */
  public boolean labelEquals(EntityMention otherEnt, boolean useSubType) {
    if(((type != null && otherEnt.type != null && type.equals(otherEnt.type)) || (type == null && otherEnt.type == null)) &&
        (! useSubType || ((subType != null && otherEnt.subType != null && subType.equals(otherEnt.subType)) || (subType == null && otherEnt.subType == null)))){
      return true;
    }
    return false;
  }

  /**
   * Compares the text spans of the two entity mentions.
   *
   * @param otherEnt
   */
  public boolean textEquals(EntityMention otherEnt) {
    //
    // we attempt three comparisons:
    // a) if syntactic heads are defined we consider two texts similar if they have the same syntactic head
    //    (this is necessary because in NFL we compare entities with different spans but same heads, e.g. "49ers" vs "San Francisco 49ers"
    // b) if head spans are defined we consider two texts similar if they have the same head span
    // c) if extent spans are defined we consider two texts similar if they have the same extent span
    //
    if(syntacticHeadTokenPosition != -1 && otherEnt.syntacticHeadTokenPosition != -1){
      if(syntacticHeadTokenPosition == otherEnt.syntacticHeadTokenPosition) return true;
      return false;
    }

    if(headTokenSpan != null && otherEnt.headTokenSpan != null){
      if(headTokenSpan.equals(otherEnt.headTokenSpan)) return true;
      return false;
    }

    if(extentTokenSpan != null && otherEnt.extentTokenSpan != null){
      if(extentTokenSpan.equals(otherEnt.extentTokenSpan)) return true;
      return false;
    }

    if (!this.getExtentString().equals(otherEnt.getExtentString())) {
      return false;
    }

    return false;
  }

  /**
   * Get the text value of this entity.
   * The headTokenSpan MUST be set before calling this method!
   */
  public String getValue() {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    // int lastEnd = -1;
    StringBuilder sb = new StringBuilder();
    for (int i = headTokenSpan.start(); i < headTokenSpan.end(); i ++){
      CoreLabel token = tokens.get(i);

      // we are not guaranteed to have CharacterOffsets so we can't use them...
      /*
    	Integer start = token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    	Integer end = token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);

    	if (start != null && end != null) {
    	  if (lastEnd != -1 && !start.equals(lastEnd)) {
    	    sb.append(StringUtils.repeat(" ", start - lastEnd));
    	    lastEnd = end;
    	  }
    	} else {
    	  if (lastEnd != -1) sb.append(" ");
    	  lastEnd = 0;
    	}
       */
      if(i > headTokenSpan.start()) sb.append(" ");

      sb.append(token.word());

    }

    return sb.toString();
  }

  
  @Override
  public String toString() {
    return "EntityMention [type=" + type 
    + (subType != null ? ", subType=" + subType : "")
    + (mentionType != null ? ", mentionType=" + mentionType : "")
    + (objectId != null ? ", objectId=" + objectId : "") 
    + (headTokenSpan != null ? ", hstart=" + headTokenSpan.start() + ", hend=" + headTokenSpan.end() : "")
    + (extentTokenSpan != null ? ", estart=" + extentTokenSpan.start() + ", eend=" + extentTokenSpan.end() : "")
    + (syntacticHeadTokenPosition >= 0 ? ", headPosition=" + syntacticHeadTokenPosition : "")
    + (headTokenSpan != null ? ", value=\"" + getValue() + "\"" : "") 
    + (normalizedName != null ? ", normalizedName=\"" + normalizedName + "\"" : "")
    + ", corefID=" + corefID
    + (typeProbabilities != null ? ", probs=" + probsToString() : "")
    + "]";
  }

  @Override
  public int hashCode() {
    int result = mentionType != null ? mentionType.hashCode() : 0;
    result = 31 * result + (headTokenSpan != null ? headTokenSpan.hashCode() : 0);
    result = 31 * result + (normalizedName != null ? normalizedName.hashCode() : 0);
    result = 31 * result + (extentTokenSpan != null ? extentTokenSpan.hashCode() : 0);
    return result;
  }

  static class CompByHead implements Comparator<EntityMention> {
    public int compare(EntityMention o1, EntityMention o2) {
      if(o1.getHeadTokenStart() < o2.getHeadTokenStart()){
        return -1;
      } else if(o1.getHeadTokenStart() > o2.getHeadTokenStart()){
        return 1;
      } else if(o1.getHeadTokenEnd() < o2.getHeadTokenEnd()) {
        return -1;
      } else if(o1.getHeadTokenEnd() > o2.getHeadTokenEnd()) {
        return 1;
      } else {
        return 0;
      }
    }
  }

  public static void sortByHeadSpan(List<EntityMention> mentions) {
    Collections.sort(mentions, new CompByHead());
  }

  private static int MENTION_COUNTER = 0;

  /**
   * Creates a new unique id for an entity mention
   * @return the new id
   */
  public static synchronized String makeUniqueId() {
    MENTION_COUNTER ++;
    return "EntityMention-" + MENTION_COUNTER;
  }
}
