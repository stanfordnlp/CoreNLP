package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;

import java.util.*;

/**
 * Wrapper around a CoreMap representing a quote.  Adds some helpful methods.
 *
 * @author Jason Bolton
 */

public class CoreQuote {

  private final CoreMap quoteCoreMap;
  private final CoreDocument document;
  private final List<CoreSentence> sentences;
  // optional speaker info...note there may not be an entity mention corresponding to the speaker
  public boolean hasSpeaker;
  public boolean hasCanonicalSpeaker;
  private Optional<String> speaker;
  private Optional<String> canonicalSpeaker;
  private Optional<List<CoreLabel>> speakerTokens;
  private Optional<List<CoreLabel>> canonicalSpeakerTokens;
  private Optional<Pair<Integer,Integer>> speakerCharOffsets;
  private Optional<Pair<Integer,Integer>> canonicalSpeakerCharOffsets;
  private Optional<CoreEntityMention> speakerEntityMention;
  private Optional<CoreEntityMention> canonicalSpeakerEntityMention;

  public CoreQuote(CoreDocument myDocument, CoreMap coreMapQuote) {
    this.document = myDocument;
    this.quoteCoreMap = coreMapQuote;
    // attach sentences to the quote
    this.sentences = new ArrayList<>();
    int firstSentenceIndex = this.quoteCoreMap.get(CoreAnnotations.SentenceBeginAnnotation.class);
    int lastSentenceIndex = this.quoteCoreMap.get(CoreAnnotations.SentenceEndAnnotation.class);
    for (int currSentIndex = firstSentenceIndex ; currSentIndex <= lastSentenceIndex ; currSentIndex++) {
      this.sentences.add(this.document.sentences().get(currSentIndex));
    }
    // set up the speaker info
    this.speaker = Optional.ofNullable(this.quoteCoreMap.get(QuoteAttributionAnnotator.SpeakerAnnotation.class));
    this.canonicalSpeaker = Optional.ofNullable(this.quoteCoreMap.get(QuoteAttributionAnnotator.CanonicalMentionAnnotation.class));
    // set up info for direct speaker mention (example: "He")
    Integer firstSpeakerTokenIndex = quoteCoreMap.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class);
    Integer lastSpeakerTokenIndex = quoteCoreMap.get(QuoteAttributionAnnotator.MentionEndAnnotation.class);
    this.speakerTokens = Optional.empty();
    this.speakerCharOffsets = Optional.empty();
    this.speakerEntityMention = Optional.empty();
    if (firstSpeakerTokenIndex != null && lastSpeakerTokenIndex != null) {
      this.speakerTokens = Optional.of(new ArrayList<>());
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
    // set up info for canonical speaker mention (example: "Joe Smith")
    Integer firstCanonicalSpeakerTokenIndex = quoteCoreMap.get(QuoteAttributionAnnotator.CanonicalMentionBeginAnnotation.class);
    Integer lastCanonicalSpeakerTokenIndex = quoteCoreMap.get(QuoteAttributionAnnotator.CanonicalMentionEndAnnotation.class);
    this.canonicalSpeakerTokens = Optional.empty();
    this.canonicalSpeakerCharOffsets = Optional.empty();
    this.canonicalSpeakerEntityMention = Optional.empty();
    if (firstCanonicalSpeakerTokenIndex != null && lastCanonicalSpeakerTokenIndex != null) {
      this.canonicalSpeakerTokens = Optional.of(new ArrayList<>());
      for (int canonicalSpeakerTokenIndex = firstCanonicalSpeakerTokenIndex ;
           canonicalSpeakerTokenIndex <= lastCanonicalSpeakerTokenIndex ; canonicalSpeakerTokenIndex++) {
        this.canonicalSpeakerTokens.get().add(this.document.tokens().get(canonicalSpeakerTokenIndex));
      }
      int canonicalSpeakerCharOffsetBegin =
          this.canonicalSpeakerTokens.get().get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int canonicalSpeakerCharOffsetEnd =
          this.canonicalSpeakerTokens.get().get(
              canonicalSpeakerTokens.get().size() - 1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      this.canonicalSpeakerCharOffsets = Optional.of(new Pair<>(canonicalSpeakerCharOffsetBegin, canonicalSpeakerCharOffsetEnd));
      for (CoreEntityMention candidateEntityMention : this.document.entityMentions()) {
        Pair<Integer,Integer> entityMentionOffsets = candidateEntityMention.charOffsets();
        if (entityMentionOffsets.equals(this.canonicalSpeakerCharOffsets.get())) {
          this.canonicalSpeakerEntityMention = Optional.of(candidateEntityMention);
          break;
        }
      }
    }
    // record if there is speaker info
    this.hasSpeaker = this.speaker.isPresent();
    this.hasCanonicalSpeaker = this.canonicalSpeaker.isPresent();
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

  /** retrieve the text of the canonical speaker **/
  public Optional<String> canonicalSpeaker() { return this.canonicalSpeaker; }

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

  /** retrieve the tokens of the canonical speaker **/
  public Optional<List<CoreLabel>> canonicalSpeakerTokens() {
    return this.canonicalSpeakerTokens;
  }

  /** retrieve the character offsets of the canonical speaker **/
  public Optional<Pair<Integer,Integer>> canonicalSpeakerCharOffsets() {
    return this.canonicalSpeakerCharOffsets;
  }

  /** retrieve the entity mention corresponding to the canonical speaker if there is one **/
  public Optional<CoreEntityMention> canonicalSpeakerEntityMention() {
    return this.canonicalSpeakerEntityMention;
  }

  /** char offsets of quote **/
  public Pair<Integer,Integer> quoteCharOffsets() {
    int beginCharOffset = this.quoteCoreMap.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int endCharOffset = this.quoteCoreMap.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    return new Pair<>(beginCharOffset,endCharOffset);
  }

  @Override
  public String toString() {
    return coreMap().toString();
  }
}
