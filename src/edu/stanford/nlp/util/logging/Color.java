
package edu.stanford.nlp.util.logging;

/**
 * ANSI supported colors.
 * These values are mirrored in Redwood.Util.
 *
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public enum Color {

  //note: NONE, BLACK and WHITE must be first three (for random colors in OutputHandler to work)
  /** No color. */ NONE(""), /** Black. */ BLACK("\033[30m"), /** White. */ WHITE("\033[37m"), /** Red. */ RED("\033[31m"), /** Green. */ GREEN("\033[32m"),
  /** Yellow. */ YELLOW("\033[33m"), /** Blue. */ BLUE("\033[34m"), /** Magenta. */ MAGENTA("\033[35m"), /** Cyan. */ CYAN("\033[36m");

  /** The ANSI escape sequence which starts this color (empty for {@link #NONE}). */
  public final String ansiCode;

  Color(String ansiCode){
    this.ansiCode = ansiCode;
  }

  /**
   * Wraps the string in this color's ANSI code and a reset code, if
   * {@link Redwood#supportsAnsi} is set.
   *
   * @param toColor The string to color
   * @return The colored string, or {@code toColor} unchanged if ANSI codes are not supported
   */
  public String apply(String toColor) {
    if (Redwood.supportsAnsi) {
      return ansiCode + toColor + "\033[0m";
    } else {
      return toColor;
    }
  }

}
