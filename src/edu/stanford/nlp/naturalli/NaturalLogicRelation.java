package edu.stanford.nlp.naturalli;

/**
 * The catalog of the seven Natural Logic relations.
 * Set-theoretically, if we assume A and B are two sets (e.g., denotations),
 * and D is the universe of discourse,
 * then the relations between A and B are defined as follows:
 *
 * <ul>
 *   <li>Equivalence: A = B</li>
 *   <li>Forward entailment: A \\subset B</li>
 *   <li>Reverse entailment: A \\supset B</li>
 *   <li>Negation: A \\intersect B = \\empty \\land A \\union B = D </li>
 *   <li>Alternation: A \\intersect B = \\empty </li>
 *   <li>Cover: A \\union B = D </li>
 * </ul>
 *
 * @author Gabor Angeli
 */
public enum NaturalLogicRelation {
  EQUIVALENCE(0, true, false),
  FORWARD_ENTAILMENT(1, true, false),
  REVERSE_ENTAILMENT(2, false, false),
  NEGATION(3, false, true),
  ALTERNATION(4, false, true),
  COVER(5, false, false),
  INDEPENDENCE(6, false, false);

  public final int fixedIndex;
  public final boolean isEntailed, isNegated;

  NaturalLogicRelation(int fixedIndex, boolean isEntailed, boolean isNegated) {
    this.fixedIndex = fixedIndex;
    this.isEntailed = isEntailed;
    this.isNegated = isNegated;
  }

  protected static NaturalLogicRelation byFixedIndex(int index) {
    switch (index) {
      case 0: return EQUIVALENCE;
      case 1: return FORWARD_ENTAILMENT;
      case 2: return REVERSE_ENTAILMENT;
      case 3: return NEGATION;
      case 4: return ALTERNATION;
      case 5: return COVER;
      case 6: return INDEPENDENCE;
      default: throw new IllegalArgumentException("Unknown index for Natural Logic relation: " + index);
    }
  }

  /**
   * The MacCartney "join table" -- this determines the transitivity of entailment if we chain two relations together.
   * These should already be projected up through the sentence, so that the relations being joined are relations between
   * <i>sentences</i> rather than relations between <i>lexical items</i> (see {@link Polarity#projectLexicalRelation(NaturalLogicRelation)},
   * set by {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator} using the {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.PolarityAnnotation}).
   * @param other The relation to join this relation with.
   * @return The new joined relation.
   */
  public NaturalLogicRelation join(NaturalLogicRelation other) {
    switch (this) {
      case EQUIVALENCE:
        return other;
      case FORWARD_ENTAILMENT:
        switch (other) {
          case EQUIVALENCE:
          case FORWARD_ENTAILMENT:
            return FORWARD_ENTAILMENT;
          case NEGATION:
          case ALTERNATION:
            return COVER;
          case REVERSE_ENTAILMENT:
          case COVER:
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case REVERSE_ENTAILMENT:
        switch (other) {
          case EQUIVALENCE:
          case REVERSE_ENTAILMENT:
            return REVERSE_ENTAILMENT;
          case NEGATION:
          case COVER:
            return COVER;
          case FORWARD_ENTAILMENT:
          case ALTERNATION:
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case NEGATION:
        switch (other) {
          case EQUIVALENCE:
            return NEGATION;
          case FORWARD_ENTAILMENT:
            return COVER;
          case REVERSE_ENTAILMENT:
            return ALTERNATION;
          case NEGATION:
            return EQUIVALENCE;
          case ALTERNATION:
            return REVERSE_ENTAILMENT;
          case COVER:
            return FORWARD_ENTAILMENT;
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case ALTERNATION:
        switch (other) {
          case EQUIVALENCE:
          case REVERSE_ENTAILMENT:
            return ALTERNATION;
          case NEGATION:
          case COVER:
            return FORWARD_ENTAILMENT;
          case FORWARD_ENTAILMENT:
          case ALTERNATION:
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case COVER:
        switch (other) {
          case EQUIVALENCE:
          case FORWARD_ENTAILMENT:
            return COVER;
          case NEGATION:
          case ALTERNATION:
            return REVERSE_ENTAILMENT;
          case REVERSE_ENTAILMENT:
          case COVER:
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case INDEPENDENCE:
        return INDEPENDENCE;
    }
    throw new IllegalStateException("[should be impossible]: Incomplete join table for " + this + " joined with " + other);
  }
}
