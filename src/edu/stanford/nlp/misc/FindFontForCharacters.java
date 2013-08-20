package edu.stanford.nlp.misc;

import java.util.*;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/** This little class will see if there are fonts on a system that can
 *  display certain characters.  It requires the AWT environment.
 *  Based on code by Erik Peterson.
 *
 *  @author Christopher Manning
 */
public class FindFontForCharacters {

  private FindFontForCharacters() {}

  public static Set<String> findFontForCharacters(String str) {
    // Determine which fonts support Chinese here ...
    Set<String> okayFonts = new TreeSet<String>();
    Font[] allfonts =
      GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    for (Font font : allfonts) {
      if (font.canDisplayUpTo(str) < 0) {
        okayFonts.add(font.getFontName());
      }
    }
    return okayFonts;
  }

  public static Set<String> findFontForChinese() {
    return findFontForCharacters("\u4e00\u513f\u9fa5");
  }

  public static void main(String[] args) {
    if (args.length > 0) {
      for (String arg : args) {
        System.out.println("Fonts that can display " + arg + " are " +
                findFontForCharacters(arg));
      }
    } else {
      System.out.println("Fonts that can display Chinese are " +
                         findFontForChinese());
    }
  }

}


