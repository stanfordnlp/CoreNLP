package edu.stanford.nlp.simple;

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
}
