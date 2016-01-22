package edu.stanford.nlp.parser.shiftreduce;

/**
 * Used internally by the Oracle.  If the next oracle transition is
 * known, that is stored.  Otherwise, general classes of transitions
 * are allowed.
 *
 * @author John Bauer
 */
class OracleTransition {
  final Transition transition;
  final boolean allowsShift;
  final boolean allowsBinary;
  final boolean allowsEitherSide;

  OracleTransition(Transition transition, boolean allowsShift, boolean allowsBinary, boolean allowsEitherSide) {
    this.transition = transition;
    this.allowsShift = allowsShift;
    this.allowsBinary = allowsBinary;
    this.allowsEitherSide = allowsEitherSide;
  }

  boolean isCorrect(Transition other) {
    if (transition != null && transition.equals(other)) {
      return true;
    }

    if (allowsShift && (other instanceof ShiftTransition)) {
      return true;
    }

    if (allowsBinary && (other instanceof BinaryTransition)) {
      return true;
    }

    if (allowsEitherSide && (other instanceof BinaryTransition) && (transition instanceof BinaryTransition)) {
      if (((BinaryTransition) other).label.equals(((BinaryTransition) transition).label)) {
        return true;
      }
    }

    return false;
  }
}
