package edu.stanford.nlp.simple;


import edu.stanford.nlp.ling.*;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 *  A utility class for representing a token in the Simple API.
 *  This nominally tries to conform to a {@link CoreLabel}-like interface,
 *  though many of the methods are not supported (most notably, the setters).
 *
 * @author Gabor Angeli
 */
public class Token implements AbstractToken {

  /** The underlying sentence supplying the fields for this token. */
  public final Sentence sentence;
  /** The index of this token in the underlying sentence. This can be out of bounds; the sentence is assumed to be infinitely padded. */
  public final int index;


  /** Create a wrapper for a token, given a sentence and an index in the sentence */
  public Token(Sentence sentence, int index) {
    this.sentence = sentence;
    this.index = index;
  }


  /**
   * The previous token in the sentence.
   */
  public Token previous() {
    return new Token(sentence, index - 1);
  }


  /**
   * The next token in the sentence.
   */
  public Token next() {
    return new Token(sentence, index + 1);
  }


  /** @see Sentence#word(int) */
  public String word() {
    return sentence.word(index);
  }

  @Override
  public void setWord(String word) {

  }


  /**
   * Return the value at the supplier, but make sure that the index is in bounds first.
   * If the index is out of bounds, return either '^' or '$' depending on whether it's the beginning
   * or end of the sentence.
   */
  private String pad(Supplier<String> value) {
    if (index < 0) {
      return "^";
    } else if (index >= sentence.length()) {
      return "$";
    } else {
      return value.get();
    }
  }


  /**
   * Return the value at the supplier, but make sure that the index is in bounds first.
   * If the index is out of bounds, return {@link Optional#empty()}.
   */
  private <E> Optional<E> padOpt(Supplier<Optional<E>> value) {
    if (index < 0) {
      return Optional.empty();
    } else if (index >= sentence.length()) {
      return Optional.empty();
    } else {
      return value.get();
    }
  }


  /** @see Sentence#originalText(int) */
  public String originalText() {
    return pad(() -> sentence.originalText(index));
  }

  @Override
  public void setOriginalText(String originalText) {
    throw new UnsupportedOperationException();
  }

  /** @see Sentence#lemma(int) */
  public String lemma() {
    return pad(() -> sentence.lemma(index));
  }

  @Override
  public void setLemma(String lemma) {
    throw new UnsupportedOperationException();
  }


  /** @see Sentence#nerTag(int) */
  public String ner() {
    return pad(() -> sentence.nerTag(index));
  }

  @Override
  public void setNER(String ner) {
    throw new UnsupportedOperationException();
  }


  /** @see Sentence#nerTag(int) */
  public String nerTag() {
    return ner();
  }

  /** @see Sentence#posTag(int) */
  public String tag() {
    return pad(() -> sentence.posTag(index));
  }

  @Override
  public void setTag(String tag) {
    throw new UnsupportedOperationException();
  }


  /** @see Sentence#posTag(int) */
  public String posTag() {
    return tag();
  }


  /** @see Sentence#governor(int) */
  public Optional<Integer> governor() {
    return padOpt(() -> sentence.governor(index));
  }


  /** @see Sentence#characterOffsetBegin(int) */
  public int characterOffsetBegin() {
    if (index < 0) {
      return -1;
    } else if (index >= sentence.length()) {
      return -1;
    } else {
      return sentence.characterOffsetBegin(index);
    }
  }


  /** @see Sentence#characterOffsetEnd(int) */
  public int characterOffsetEnd() {
    if (index < 0) {
      return -1;
    } else if (index >= sentence.length()) {
      return -1;
    } else {
      return sentence.characterOffsetEnd(index);
    }
  }


  /** @see Sentence#before(int) */
  public String before() {
    if (index < 0) {
      return "";
    } else if (index >= sentence.length()) {
      return "";
    } else {
      return sentence.before(index);
    }
  }

  @Override
  public void setBefore(String before) {
    throw new UnsupportedOperationException();
  }


  /** @see Sentence#after(int) */
  public String after() {
    if (index < 0) {
      return "";
    } else if (index >= sentence.length()) {
      return "";
    } else {
      return sentence.after(index);
    }
  }

  @Override
  public void setAfter(String after) {
    throw new UnsupportedOperationException();
  }


  /** @see Sentence#incomingDependencyLabel(int) */
  public Optional<String> incomingDependencyLabel() {
    return padOpt(() -> sentence.incomingDependencyLabel(index));
  }

  @Override
  public String docID() {
    return sentence.document.docid().orElse("");
  }

  @Override
  public void setDocID(String docID) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int sentIndex() {
    return sentence.sentenceIndex();
  }

  @Override
  public void setSentIndex(int sentIndex) {
    throw new UnsupportedOperationException();

  }

  @Override
  public int index() {
    return index;
  }

  @Override
  public void setIndex(int index) {
    throw new UnsupportedOperationException();

  }

  @Override
  public int beginPosition() {
    return characterOffsetBegin();
  }

  @Override
  public void setBeginPosition(int beginPos) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int endPosition() {
    return characterOffsetEnd();
  }

  @Override
  public void setEndPosition(int endPos) {
    throw new UnsupportedOperationException();
  }
}
