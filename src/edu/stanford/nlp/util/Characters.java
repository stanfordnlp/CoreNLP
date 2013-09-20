package edu.stanford.nlp.util;

/** 
 * Character-level utilities.
 *
 *
 * @author Dan Klein
 * @author Spence Green
 */
public final class Characters {

  /** Only static methods */
  private Characters() {}
  
  // TODO(spenceg) This method used to cache the lookup, in this package,
  // but actually the valueOf method performs internal caching. This method
  // should be removed.
  public static Character getCharacter(char c) {
    return Character.valueOf(c);
  }

  /** 
   * Map a String to an array of type Character.
   *
   * @param s The String to map
   * @return An array of Character
   */
  public static Character[] asCharacterArray(String s) {
    Character[] split = new Character[s.length()];
    for (int i = 0; i < split.length; i++) {
      split[i] = getCharacter(s.charAt(i));
    }
    return split;
  }

  /**
   * Returns a string representation of a character's unicode
   * block.
   * 
   * @param c
   * @return
   */
  public static String unicodeBlockStringOf(char c) {
    Character.Subset block = Character.UnicodeBlock.of(c);
    return block == null ? "Undefined" : block.toString();
  }
  
  /**
   * Returns true if a character is punctuation, and false
   * otherwise.
   * 
   * @param c
   * @return
   */
  public static boolean isPunctuation(char c) {
    int cType = Character.getType(c);
    return cType == Character.START_PUNCTUATION ||
        cType == Character.END_PUNCTUATION ||
        cType == Character.OTHER_PUNCTUATION ||
        cType == Character.CONNECTOR_PUNCTUATION ||
        cType == Character.DASH_PUNCTUATION ||
        cType == Character.INITIAL_QUOTE_PUNCTUATION ||
        cType == Character.FINAL_QUOTE_PUNCTUATION;
  }
  
  /**
   * Returns true if a character is a symbol, and false
   * otherwise.
   * 
   * @param c
   * @return
   */
  public static boolean isSymbol(char c) {
    int cType = Character.getType(c);
    return cType == Character.MATH_SYMBOL || 
        cType == Character.CURRENCY_SYMBOL ||
        cType == Character.MODIFIER_SYMBOL ||
        cType == Character.OTHER_SYMBOL;
  }

  /**
   * Returns true if a character is a control character, and
   * false otherwise.
   * 
   * @param c
   * @return
   */
  public static boolean isControl(char c) {
    return Character.getType(c) == Character.CONTROL;
  }
}
