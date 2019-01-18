package edu.stanford.nlp.pipeline;

/**
 * Wrapper around a CoreMap representing a sentence.  Adds some helpful methods.
 *
 */

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class CoreSentence {

  private CoreDocument document;
  private CoreMap sentenceCoreMap;
  private List<CoreEntityMention> entityMentions;

  public CoreSentence(CoreDocument myDocument, CoreMap coreMapSentence) {
    this.document = myDocument;
    this.sentenceCoreMap = coreMapSentence;
  }

  /** create list of CoreEntityMention's based on the CoreMap's entity mentions **/
  public void wrapEntityMentions() {
    if (this.sentenceCoreMap.get(CoreAnnotations.MentionsAnnotation.class) != null) {
      entityMentions = this.sentenceCoreMap.get(CoreAnnotations.MentionsAnnotation.class).
          stream().map(coreMapEntityMention -> new CoreEntityMention(this,coreMapEntityMention)).collect(Collectors.toList());
    }
  }

  /** get the document this sentence is in **/
  public CoreDocument document() {
    return document;
  }

  /** get the underlying CoreMap if need be **/
  public CoreMap coreMap() {
    return sentenceCoreMap;
  }

  /** full text of the sentence **/
  public String text() {
    return sentenceCoreMap.get(CoreAnnotations.TextAnnotation.class);
  }

  /** char offsets of mention **/
  public Pair<Integer,Integer> charOffsets() {
    int beginCharOffset = this.sentenceCoreMap.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int endCharOffset = this.sentenceCoreMap.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    return new Pair<>(beginCharOffset,endCharOffset);
  }

  /** list of tokens **/
  public List<CoreLabel> tokens() {
    return sentenceCoreMap.get(CoreAnnotations.TokensAnnotation.class);
  }

  /** list of pos tags **/
  public List<String> posTags() { return tokens().stream().map(token -> token.tag()).collect(Collectors.toList()); }

  /** list of ner tags **/
  public List<String> nerTags() { return tokens().stream().map(token -> token.ner()).collect(Collectors.toList()); }

  /** constituency parse **/
  public Tree constituencyParse() {
    return sentenceCoreMap.get(TreeCoreAnnotations.TreeAnnotation.class);
  }

  /** dependency parse **/
  public SemanticGraph dependencyParse() {
    return sentenceCoreMap.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
  }

  /** sentiment **/
  public String sentiment() {
    return sentenceCoreMap.get(SentimentCoreAnnotations.SentimentClass.class);
  }

  /** sentiment tree **/
  public Tree sentimentTree() {
    return sentenceCoreMap.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
  }

  /** list of entity mentions **/
  public List<CoreEntityMention> entityMentions() { return this.entityMentions; }

  /** list of KBP relations found **/
  public List<RelationTriple> relations() {
    return sentenceCoreMap.get(CoreAnnotations.KBPTriplesAnnotation.class);
  }

  @Override
  public String toString() {
    return coreMap().toString();
  }
}
