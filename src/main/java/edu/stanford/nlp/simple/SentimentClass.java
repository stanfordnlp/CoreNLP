package edu.stanford.nlp.simple;

import java.util.NoSuchElementException;

/**
 * An enum for the Simple CoreNLP API to represent a sentiment value.
 *
 * @author <a href="mailto:angeli@stanford.edu">Gabor Angeli</a>
 */
public enum SentimentClass {
  VERY_POSITIVE,
  POSITIVE,
  NEUTRAL,
  NEGATIVE,
  VERY_NEGATIVE,
  ;

  public boolean isPositive() {
    return this == VERY_POSITIVE || this == POSITIVE;
  }

  public boolean isNegative() {
    return this == VERY_NEGATIVE || this == NEGATIVE;
  }

  public boolean isExtreme() {
    return this == VERY_NEGATIVE || this == VERY_POSITIVE;
  }

  public boolean isMild() {
    return !isExtreme();
  }

  public boolean isNeutral() {
    return this == NEUTRAL;
  }


  /**
   * Get the sentiment class from the Stanford Sentiment Treebank
   * integer encoding. That is, an integer between 0 and 4 (inclusive)
   *
   * @param sentiment The Integer representation of a sentiment.
   *
   * @return The sentiment class associated with that integer.
   */
  public static SentimentClass fromInt(int sentiment) {
    switch (sentiment) {
      case 0:
        return VERY_NEGATIVE;
      case 1:
        return NEGATIVE;
      case 2:
        return NEUTRAL;
      case 3:
        return POSITIVE;
      case 4:
        return VERY_POSITIVE;
      default:
        throw new NoSuchElementException("No sentiment value for integer: " + sentiment);
    }
  }
}
