package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.util.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 *  A class intended to be attached to a lexical item, determining what mutations are valid on it while
 *  maintaining valid Natural Logic inference.
 * </p>
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")
public class Polarity {

  /**
   * The default (very permissive) polarity.
   */
  public static final Polarity DEFAULT = new Polarity(Collections.singletonList(Pair.makePair(Monotonicity.MONOTONE, MonotonicityType.BOTH)));

  /** The projection function, as a table from a relations fixed index to the projected fixed index */
  private final byte[] projectionFunction = new byte[7];

  /** Create a polarity from a list of operators in scope */
  protected Polarity(List<Pair<Monotonicity, MonotonicityType>> operatorsInNarrowingScopeOrder) {
    if (operatorsInNarrowingScopeOrder.isEmpty()) {
      for (byte i = 0; i < projectionFunction.length; ++i) {
        projectionFunction[i] = i;
      }
    } else {
      for (int rel = 0; rel < 7; ++rel) {
        NaturalLogicRelation relation = NaturalLogicRelation.byFixedIndex(rel);
        for (int op = operatorsInNarrowingScopeOrder.size() - 1; op >= 0; --op) {
          relation = project(relation, operatorsInNarrowingScopeOrder.get(op).first, operatorsInNarrowingScopeOrder.get(op).second);
        }
        projectionFunction[rel] = (byte) relation.fixedIndex;
      }
    }
  }

  /**
   * Create a polarity item by directly copying the projection function from {@link edu.stanford.nlp.naturalli.NaturalLogicRelation}s to
   * their projected relation.
   */
  public Polarity(byte[] projectionFunction) {
    if (projectionFunction.length != 7) {
      throw new IllegalArgumentException("Invalid projection function: " + Arrays.toString(projectionFunction));
    }
    for (int i = 0; i < 7; ++i) {
      if (projectionFunction[i] < 0 || projectionFunction[i] > 6) {
        throw new IllegalArgumentException("Invalid projection function: " + Arrays.toString(projectionFunction));
      }
    }
    System.arraycopy(projectionFunction, 0, this.projectionFunction, 0, 7);
  }

  /**
   * Encode the projection table in painful detail.
   *
   * @param input The input natural logic relation to project up through the operator.
   * @param mono The monotonicity of the operator we are projecting through.
   * @param type The monotonicity type of the operator we are projecting through.
   *
   * @return The projected relation, once passed through an operator with the given specifications.
   */
  private NaturalLogicRelation project(NaturalLogicRelation input, Monotonicity mono, MonotonicityType type) {
    switch (input) {
      case EQUIVALENT:
        return NaturalLogicRelation.EQUIVALENT;
      case FORWARD_ENTAILMENT:
        switch (mono) {
          case MONOTONE:
            return NaturalLogicRelation.FORWARD_ENTAILMENT;
          case ANTITONE:
            return NaturalLogicRelation.REVERSE_ENTAILMENT;
          case NONMONOTONE:
          case INVALID:
            return NaturalLogicRelation.INDEPENDENCE;
        }
      case REVERSE_ENTAILMENT:
        switch (mono) {
          case MONOTONE:
            return NaturalLogicRelation.REVERSE_ENTAILMENT;
          case ANTITONE:
            return NaturalLogicRelation.FORWARD_ENTAILMENT;
          case NONMONOTONE:
          case INVALID:
            return NaturalLogicRelation.INDEPENDENCE;
        }
      case NEGATION:
        switch (type) {
          case NONE:
            return NaturalLogicRelation.INDEPENDENCE;
          case ADDITIVE:
            switch (mono) {
              case MONOTONE:
                return NaturalLogicRelation.COVER;
              case ANTITONE:
                return NaturalLogicRelation.ALTERNATION;
              case NONMONOTONE:
              case INVALID:
                return NaturalLogicRelation.INDEPENDENCE;
            }
          case MULTIPLICATIVE:
            switch (mono) {
              case MONOTONE:
                return NaturalLogicRelation.ALTERNATION;
              case ANTITONE:
                return NaturalLogicRelation.COVER;
              case NONMONOTONE:
              case INVALID:
                return NaturalLogicRelation.INDEPENDENCE;
            }
            break;
          case BOTH:
            return NaturalLogicRelation.NEGATION;
        }
        break;
      case ALTERNATION:
        switch (mono) {
          case MONOTONE:
            switch (type) {
              case NONE:
              case ADDITIVE:
                return NaturalLogicRelation.INDEPENDENCE;
              case MULTIPLICATIVE:
              case BOTH:
                return NaturalLogicRelation.ALTERNATION;
            }
          case ANTITONE:
            switch (type) {
              case NONE:
              case ADDITIVE:
                return NaturalLogicRelation.INDEPENDENCE;
              case MULTIPLICATIVE:
              case BOTH:
                return NaturalLogicRelation.COVER;
            }
          case NONMONOTONE:
          case INVALID:
            return NaturalLogicRelation.INDEPENDENCE;
        }
      case COVER:
        switch (mono) {
          case MONOTONE:
            switch (type) {
              case NONE:
              case MULTIPLICATIVE:
                return NaturalLogicRelation.INDEPENDENCE;
              case ADDITIVE:
              case BOTH:
                return NaturalLogicRelation.COVER;
            }
          case ANTITONE:
            switch (type) {
              case NONE:
              case MULTIPLICATIVE:
                return NaturalLogicRelation.INDEPENDENCE;
              case ADDITIVE:
              case BOTH:
                return NaturalLogicRelation.ALTERNATION;
            }
          case NONMONOTONE:
          case INVALID:
            return NaturalLogicRelation.INDEPENDENCE;
        }
      case INDEPENDENCE:
        return NaturalLogicRelation.INDEPENDENCE;
    }
    throw new IllegalStateException("[should not happen!] Projection table is incomplete for " + mono + " : " + type + " on relation " + input);
  }

  /**
   * Project the given natural logic lexical relation on this word. So, for example, if we want to go up the
   * Hypernymy hierarchy ({@link edu.stanford.nlp.naturalli.NaturalLogicRelation#FORWARD_ENTAILMENT}) on this word,
   * then this function will tell you what relation holds between the new mutated fact and this fact.
   *
   * @param lexicalRelation The lexical relation we are applying to this word.
   * @return The relation between the mutated sentence and the original sentence.
   */
  public NaturalLogicRelation projectLexicalRelation(NaturalLogicRelation lexicalRelation) {
    return NaturalLogicRelation.byFixedIndex( projectionFunction[lexicalRelation.fixedIndex] );
  }

  /**
   * If true, applying this lexical relation to this word creates a sentence which is entailed by the original sentence,
   * Note that both this, and {@link Polarity#negatesTruth(NaturalLogicRelation)} can be false. If this is the case, then
   * natural logic can neither verify nor disprove this mutation.
   */
  public boolean maintainsTruth(NaturalLogicRelation lexicalRelation) {
    return projectLexicalRelation(lexicalRelation).maintainsTruth;
  }

  /**
   * If true, applying this lexical relation to this word creates a sentence which is negated by the original sentence
   * Note that both this, and {@link Polarity#maintainsTruth(NaturalLogicRelation)}} can be false. If this is the case, then
   * natural logic can neither verify nor disprove this mutation.
   */
   public boolean negatesTruth(NaturalLogicRelation lexicalRelation) {
    return projectLexicalRelation(lexicalRelation).negatesTruth;
  }

  /**
   * @see Polarity#maintainsTruth(NaturalLogicRelation)
   */
  public boolean maintainsFalsehood(NaturalLogicRelation lexicalRelation) {
    return projectLexicalRelation(lexicalRelation).maintainsFalsehood;
  }

  /**
   * @see Polarity#negatesTruth(NaturalLogicRelation)
   */
  public boolean negatesFalsehood(NaturalLogicRelation lexicalRelation) {
    return projectLexicalRelation(lexicalRelation).negatesFalsehood;
  }

  /**
   * Ignoring exclusion, determine if this word has upward polarity.
   */
  public boolean isUpwards() {
    return projectLexicalRelation(NaturalLogicRelation.FORWARD_ENTAILMENT) == NaturalLogicRelation.FORWARD_ENTAILMENT &&
          projectLexicalRelation(NaturalLogicRelation.REVERSE_ENTAILMENT) == NaturalLogicRelation.REVERSE_ENTAILMENT;

  }

  /**
   * Ignoring exclusion, determine if this word has downward polarity.
   */
  public boolean isDownwards() {
    return projectLexicalRelation(NaturalLogicRelation.FORWARD_ENTAILMENT) == NaturalLogicRelation.REVERSE_ENTAILMENT &&
        projectLexicalRelation(NaturalLogicRelation.REVERSE_ENTAILMENT) == NaturalLogicRelation.FORWARD_ENTAILMENT;
  }

  @Override
  public String toString() {
    if (isUpwards()) {
      return "up";
    } else if (isDownwards()) {
      return "down";
    } else {
      return "flat";
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof String) {
      switch (((String) o).toLowerCase()) {
        case "down":
        case "downward":
        case "downwards":
        case "v":
          return this.isDownwards();
        case "up":
        case "upward":
        case "upwards":
        case "^":
          return this.isUpwards();
        case "flat":
        case "none":
        case "-":
          return !this.isDownwards() && !this.isUpwards();
        default:
          return false;
      }
    }
    if (!(o instanceof Polarity)) return false;
    Polarity polarity = (Polarity) o;
    return Arrays.equals(projectionFunction, polarity.projectionFunction);
  }

  @Override
  public int hashCode() {
    return projectionFunction != null ? Arrays.hashCode(projectionFunction) : 0;
  }
}
