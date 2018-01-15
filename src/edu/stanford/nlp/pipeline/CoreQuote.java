package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.*;

/**
 * Wrapper around a CoreMap representing a quote.  Adds some helpful methods.
 *
 */

public class CoreQuote {

  private CoreMap quoteCoreMap;
  private CoreDocument document;
  private List<CoreSentence> sentences;
  // optional speaker info...note there may not be an entity mention corresponding to the speaker
  public boolean hasSpeaker;
  private Optional<String> speaker;
  private Optional<List<CoreLabel>> speakerTokens;
  private Optional<Pair<Integer,Integer>> speakerCharOffsets;
  private Optional<CoreEntityMention> speakerEntityMention;

  public CoreQuote(CoreDocument myDocument, CoreMap coreMapQuote) {
    this.document = myDocument;
    this.quoteCoreMap = coreMapQuote;
    // attach sentences to the quote
    this.sentences = new ArrayList<CoreSentence>();
    int firstSentenceIndex = this.quoteCoreMap.get(CoreAnnotations.SentenceBeginAnnotation.class);
    int lastSentenceIndex = this.quoteCoreMap.get(CoreAnnotations.SentenceEndAnnotation.class);
    for (int currSentIndex = firstSentenceIndex ; currSentIndex <= lastSentenceIndex ; currSentIndex++) {
      this.sentences.add(this.document.sentences().get(currSentIndex));
    }
    // set up the speaker info
    this.speaker = this.quoteCoreMap.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) != null ?
        Optional.of(this.quoteCoreMap.get(QuoteAttributionAnnotator.SpeakerAnnotation.class)) : Optional.empty() ;
    Integer firstSpeakerTokenIndex = quoteCoreMap.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class);
    Integer lastSpeakerTokenIndex = quoteCoreMap.get(QuoteAttributionAnnotator.MentionEndAnnotation.class);
    this.speakerTokens = Optional.empty();
    this.speakerCharOffsets = Optional.empty();
    this.speakerEntityMention = Optional.empty();
    if (firstSpeakerTokenIndex != null && lastSpeakerTokenIndex != null) {
      this.speakerTokens = Optional.of(new ArrayList<CoreLabel>());
      for (int speakerTokenIndex = firstSpeakerTokenIndex ;
           speakerTokenIndex <= lastSpeakerTokenIndex ; speakerTokenIndex++) {
        this.speakerTokens.get().add(this.document.tokens().get(speakerTokenIndex));
      }
      int speakerCharOffsetBegin =
          this.speakerTokens.get().get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int speakerCharOffsetEnd =
          this.speakerTokens.get().get(
              speakerTokens.get().size() - 1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      this.speakerCharOffsets = Optional.of(new Pair<>(speakerCharOffsetBegin, speakerCharOffsetEnd));
      for (CoreEntityMention candidateEntityMention : this.document.entityMentions()) {
        Pair<Integer,Integer> entityMentionOffsets = candidateEntityMention.charOffsets();
        if (entityMentionOffsets.equals(this.speakerCharOffsets.get())) {
          this.speakerEntityMention = Optional.of(candidateEntityMention);
          break;
        }
      }
    }
    // record if there is speaker info
    this.hasSpeaker = this.speaker.isPresent();
  }

  /** get the underlying CoreMap if need be **/
  public CoreMap coreMap() {
    return quoteCoreMap;
  }

  /** get this quote's document **/
  public CoreDocument document() {
    return document;
  }

  /** full text of the mention **/
  public String text() {
    return this.quoteCoreMap.get(CoreAnnotations.TextAnnotation.class);
  }

  /** retrieve the CoreSentence's attached to this quote **/
  public List<CoreSentence> sentences() {
    return this.sentences;
  }

  /** retrieve the text of the speaker **/
  public Optional<String> speaker() {
    return this.speaker;
  }

  /** retrieve the tokens of the speaker **/
  public Optional<List<CoreLabel>> speakerTokens() {
    return this.speakerTokens;
  }

  /** retrieve the character offsets of the speaker **/
  public Optional<Pair<Integer,Integer>> speakerCharOffsets() {
    return this.speakerCharOffsets;
  }

  /** retrieve the entity mention corresponding to the speaker if there is one **/
  public Optional<CoreEntityMention> speakerEntityMention() {
    return this.speakerEntityMention;
  }

  /** char offsets of quote **/
  public Pair<Integer,Integer> charOffsets() {
    int beginCharOffset = this.quoteCoreMap.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int endCharOffset = this.quoteCoreMap.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    return new Pair<>(beginCharOffset,endCharOffset);
  }

}
