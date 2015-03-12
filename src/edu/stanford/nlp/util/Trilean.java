package edu.stanford.nlp.util;

/**
 * A boolean, but for three-valued logics (true / false / unknown).
 * For most use cases, you can probably use the static values for TRUE, FALSE, and UNKNOWN.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")
public class Trilean {

  /**
   * 0 = false
   * 1 = true
   * 2 = unknown
   */
  private final byte value;

  /**
   * Construct a new Trilean value.
   * @param isTrue Set to true if the value is true. Set to false if the value is false or unknown.
   * @param isFalse Set to true if the value is false. Set to false if the value is true or unknown.
   */
  public Trilean(boolean isTrue, boolean isFalse) {
    if (isTrue && isFalse) {
      throw new IllegalArgumentException("Value cannot be both true and false.");
    }
    if (isTrue) {
      value = 1;
    } else if (isFalse) {
      value = 0;
    } else {
      value = 2;
    }
  }

  /**
   * The copy constructor.
   * @param other The value to copy from.
   */
  public Trilean(Trilean other) {
    this.value = other.value;
  }

  /**
   * Returns true if this Trilean is true, and false if it is false or unknown.
   */
  public boolean isTrue() {
    return value == 1;
  }

  /**
   * Returns true if this Trilean is false, and false if it is true or unknown.
   */
  public boolean isFalse() {
    return value == 0;
  }

  /**
   * Returns true if this Trilean is either true or false, and false if it is unknown.
   */
  public boolean isKnown() {
    return value != 2;
  }

  /**
   * Returns true if this Trilean is neither true or false, and false if it is either true or false.
   */
  public boolean isUnknown() {
    return value == 2;
  }

  /**
   * Convert this Trilean to a boolean, with a specified default value if the truth value is unknown.
   * @param valueForUnknown The default value to use if the value of this Trilean is unknown.
   * @return The boolean value of this Trilean.
   */
  public boolean toBoolean(boolean valueForUnknown) {
    switch (value) {
      case 1:
        return true;
      case 0:
        return false;
      case 2:
        return valueForUnknown;
      default:
        throw new IllegalStateException("Something went very very wrong.");
    }
  }

  /**
   * Convert this Trilean to a Boolean, or null if the value is not known.
   * @return Either True, False, or null.
   */
  public Boolean toBooleanOrNull() {
    switch (value) {
      case 1:
        return true;
      case 0:
        return false;
      case 2:
        return null;
      default:
        throw new IllegalStateException("Something went very very wrong.");
    }
  }

  /**
   * Returns the logical and of this and the other value.
   * @param other The value to and this value with.
   */
  public Trilean and(Trilean other) {
    if (this.value == 0 || other.value == 0) {
      return FALSE;
    } else if (this.value == 2 || other.value == 2) {
      return UNKNOWN;
    } else {
      return TRUE;
    }
  }

  /**
   * Returns the logical or of this and the other value.
   * @param other The value to or this value with.
   */
  public Trilean or(Trilean other) {
    if (this.value == 1 || other.value == 1) {
      return TRUE;
    } else if (this.value == 2 || other.value == 2) {
      return UNKNOWN;
    } else {
      return FALSE;
    }
  }

  /**
   * Returns the logical not of this value.
   */
  public Trilean not() {
    switch (value) {
      case 0:
        return TRUE;
      case 1:
        return FALSE;
      case 2:
        return UNKNOWN;
      default:
        throw new IllegalStateException("Something went very very wrong.");
    }
  }

  /** The static value for True */
  public static Trilean TRUE = new Trilean(true, false);
  /** The static value for False */
  public static Trilean FALSE = new Trilean(false, true);
  /** The static value for Unknown (neither true or false) */
  public static Trilean UNKNOWN = new Trilean(false, false);

}
