
package edu.stanford.nlp.util.logging;

/**
 * ANSI supported styles (rather, a subset of).
 * These values are mirrored in Redwood.Util.
 *
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public enum Style {

  NONE(""), BOLD("\033[1m"), DIM("\033[2m"), ITALIC("\033[3m"), UNDERLINE("\033[4m"), BLINK("\033[5m"), CROSS_OUT("\033[9m");

  public final String ansiCode;

  Style(String ansiCode){
    this.ansiCode = ansiCode;
  }


  public String apply(String toColor) {
    StringBuilder b = new StringBuilder();
    if (Redwood.supportsAnsi) { b.append(ansiCode); }
    b.append(toColor);
    if (Redwood.supportsAnsi) { b.append("\033[0m"); }
    return b.toString();
  }
}
