
package edu.stanford.nlp.util.logging;

/**
 * ANSI supported colors.
 * These values are mirrored in Redwood.Util.
 *
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public enum Color {

  //note: NONE, BLACK and WHITE must be first three (for random colors in OutputHandler to work)
  NONE(""), BLACK("\033[30m"), WHITE("\033[37m"), RED("\033[31m"), GREEN("\033[32m"),
  YELLOW("\033[33m"), BLUE("\033[34m"), MAGENTA("\033[35m"), CYAN("\033[36m");

  public final String ansiCode;

  Color(String ansiCode){
    this.ansiCode = ansiCode;
  }

  public String apply(String toColor) {
    if (Redwood.supportsAnsi) {
      return ansiCode + toColor + "\033[0m";
    } else {
      return toColor;
    }
  }

}
