
package edu.stanford.nlp.util.logging;

/**
 * ANSI supported styles (rather, a subset of).
 * These values are mirrored in Redwood.Util.
 *
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public enum Style {

  /** No style. */ NONE(""), /** Bold. */ BOLD("\033[1m"), /** Dim. */ DIM("\033[2m"), /** Italic. */ ITALIC("\033[3m"), /** Underline. */ UNDERLINE("\033[4m"), /** Blinking. */ BLINK("\033[5m"), /** Crossed out. */ CROSS_OUT("\033[9m");

  /** The ANSI escape sequence which starts this style (empty for {@link #NONE}). */
  public final String ansiCode;

  Style(String ansiCode){
    this.ansiCode = ansiCode;
  }


  /**
   * Wraps the string in this style's ANSI code and a reset code, if
   * {@link Redwood#supportsAnsi} is set.
   *
   * @param toColor The string to style
   * @return The styled string, or {@code toColor} unchanged if ANSI codes are not supported
   */
  public String apply(String toColor) {
    StringBuilder b = new StringBuilder();
    if (Redwood.supportsAnsi) { b.append(ansiCode); }
    b.append(toColor);
    if (Redwood.supportsAnsi) { b.append("\033[0m"); }
    return b.toString();
  }
}
