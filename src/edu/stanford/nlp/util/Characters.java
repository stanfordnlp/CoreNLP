package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.List;

/** Character utilities. So far mainly performs a mapping of char to a
 *  Singleton (interned) Character for each character.
 *
 *
 *  @author Dan Klein
 */
public class Characters {

  /** Only static methods */
  private Characters() {}


  private static class CharacterHolder {

    private static final Character[] canonicalCharacters = new Character[65536];

    private CharacterHolder() { }
    
  }


  @SuppressWarnings({"UnnecessaryBoxing"})
  public static Character getCharacter(char c) {
    Character cC = CharacterHolder.canonicalCharacters[c];
    if (cC == null) {
      cC = Character.valueOf(c);
      CharacterHolder.canonicalCharacters[c] = cC;
    }
    return cC;
  }

  /** Map a String to an array of type Character.
   *  <i>Note: Current implementation is correct/useful only for BMP
   *  characters.</i>
   *
   *  @param s The String to map
   *  @return An array of Character
   */
  public static Character[] asCharacterArray(String s) {
    Character[] split = new Character[s.length()];
    for (int i = 0; i < split.length; i++) {
      split[i] = getCharacter(s.charAt(i));
    }
    return split;
  }

  public static List<Character> asCharacterList(String s) {
    return Arrays.asList(asCharacterArray(s));
  }

  public static String unicodeBlockStringOf(int codePoint) {
    Character.Subset block = Character.UnicodeBlock.of(codePoint);
    if (block == null) {
      return "Undefined";
    }
    return block.toString();
  }

}
