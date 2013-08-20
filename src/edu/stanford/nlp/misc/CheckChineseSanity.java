package edu.stanford.nlp.misc;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;


import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.StringUtils;


/** This class is designed for checking Chinese source files to see if they
 *  have anything "wonky" in them.
 *  <p>
 *  This class can handle Unicode characters that are outside the
 *  Base Multilingual Plane.
 *
 *  @author Christopher Manning
 */
public class CheckChineseSanity {

  private CheckChineseSanity() {}


  private static void checkChinese(String filename, PrintWriter pw) throws IOException {
    pw.printf("Examining file %s%n", filename);
    Counter<String> weirdCounts = new ClassicCounter<String>();
    Counter<String> nonBmpCounts = new ClassicCounter<String>();
    int lineNo = 0;
    for (String line : ObjectBank.getLineIterator(filename)) {
      lineNo++;
      int numCodePoints = line.codePointCount(0, line.length());
      int numLatin = 0;
      int numChinese = 0;
      int numOkay = 0;
      int numFunny = 0;
      int numNonBmp = 0;
      for (int i = 0; i < numCodePoints; i++) {
        int index = line.offsetByCodePoints(0, i);
        int ch = line.codePointAt(index);
        int ty = Character.getType(ch);
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        if ( ! Character.isValidCodePoint(ch)) {
          pw.printf("Invalid codepoint %d %s in line: %s%n", ch, Integer.toString(ch, 16), line);
          numFunny++;
        } else if (Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block) || 
                   Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION.equals(block) ||
                   Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A.equals(block)) {
          numChinese++;
        } else if (Character.UnicodeBlock.BASIC_LATIN.equals(block) || Character.UnicodeBlock.LATIN_1_SUPPLEMENT.equals(block)) {
          numLatin++;
        } else if (Character.UnicodeBlock.NUMBER_FORMS.equals(block) || // Roman numerals, etc.
                   Character.UnicodeBlock.GENERAL_PUNCTUATION.equals(block) || // ldots, per mille, quotes
                   Character.UnicodeBlock.ENCLOSED_ALPHANUMERICS.equals(block) || // circled numerals
                   Character.UnicodeBlock.GEOMETRIC_SHAPES.equals(block) || // solid circle, triangle, etc.
                   Character.UnicodeBlock.BOX_DRAWING.equals(block) || // box drawing characters 'dash', vertical rule, etc.
                   Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS.equals(block) || // only in raw; normalized out for processed.
                   ch == '\u30FB' || // Katakana middot
                   ch == '\u2103' || // Celsius sign
                   ch == '\uFE6A' || ch == '\uFE63' || ch == '\uFE52' || // small form variants: percent and dash
                   ch == '\u2236') { // Math colon
          numOkay++;
        } else {
          String nonBmpStr = "";
          if (ch >= (1 << 16)) {
            numNonBmp++;
            nonBmpStr = ", non-BMP!";
            char[] chArrBmp = Character.toChars(ch);
            nonBmpCounts.incrementCount(new String(chArrBmp) + "[U+" + Integer.toHexString(ch).toUpperCase() + "]");
          }
          String tyStr = "";
          switch (ty) {
            case 1:
              tyStr = " uppercase";
              break;
            case 2:
              tyStr = " lowercase";
              break;
            case 5:
              tyStr = " otherLetter";
              break;
            case 12:
              tyStr = " spaceSeparator";
              break;
            case 20:
              tyStr = " dashPunct";
              break;
            case 21:
              tyStr = " startPunct";
              break;
            case 22:
              tyStr = " endPunct";
              break;
            case 24:
              tyStr = " otherPunct";
              break;
            default:
          }
          char[] chArr = Character.toChars(ch);
          String chstr = new String(chArr);
          pw.println(filename + ", line: " + lineNo + ": Character " + ch + " [" + chstr +
                  ", U+" + Integer.toHexString(ch).toUpperCase() +
                  ", valid=" + Character.isValidCodePoint(ch) +
                  ", suppl=" + Character.isSupplementaryCodePoint(ch) +
                  ", mirror=" + Character.isMirrored(ch) +
                  ", type=" + Character.getType(ch) + tyStr +
                  ", uBlock=" + block + nonBmpStr + "]");
        }
      }
      int sane = numLatin + numChinese + numOkay;
      if (sane  < (numCodePoints - 5)) {
        pw.println(filename + ", line: " + lineNo + ": Content is funny: " + line);
      }
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.err.println("usage: java CheckChineseSanity file+");
      return;
    }
    String inEncoding = System.getProperty("file.encoding");
    System.err.println("Using system default character encoding \"" + inEncoding +
            "\" (hopefully UTF-8).");
    // StringUtils.printErrInvocationString(CheckChineseSanity.class.toString(), args);

    PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out), true);
    for (String arg : args) {
      checkChinese(arg, pw);
    }
    pw.flush();
    pw.close();
  }

}
