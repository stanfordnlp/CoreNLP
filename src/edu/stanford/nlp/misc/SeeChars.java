package edu.stanford.nlp.misc;

import java.io.*;
import java.util.*;


/** This class tells you in detail about what characters are contained in
 *  a certain file when it is interpreted as being in a certain encoding.
 *  This is useful when you want to be absolutely sure what characters are
 *  in a file and your computer may not be displaying them correctly.
 *  <p>
 *  There is a command-line version, and a version that you can call to do
 *  the same thing on a String.
 *  <p>
 *  This class can handle Unicode characters that are outside the
 *  Base Multilingual Plane.
 *
 *  @author Christopher Manning
 */
public class SeeChars {

  private SeeChars() {}

  public static void seeChars(String str, String outputEncoding) {
    PrintWriter pw;
    try {
      pw = new PrintWriter(new OutputStreamWriter(System.out, outputEncoding), true);
    } catch (UnsupportedEncodingException uee) {
      System.err.println("Unsupported encoding: " + outputEncoding);
      pw = new PrintWriter(System.out, true);
    }
    seeChars(str, pw);
  }

  public static void seeChars(String str, PrintWriter pw) {
    int numCodePoints = str.codePointCount(0, str.length());
    for (int i = 0; i < numCodePoints; i++) {
      int index = str.offsetByCodePoints(0, i);
      int ch = str.codePointAt(index);
      seeCodePoint(ch, pw);
    }
  }

  public static void seeList(List<?> sentence, String outputEncoding) {
    for (int ii = 0, len = sentence.size(); ii < len; ii++) {
      System.out.println("Word " + ii + " in " + outputEncoding);
      seeChars(sentence.get(ii).toString(), outputEncoding);
    }
  }

  public static void seeCodePoint(int ch, PrintWriter pw) {
    String chstr;
    if (ch == 10) {
      chstr = "nl";
    } else if (ch == 13) {
      chstr = "cr";
    } else {
      char[] chArr = Character.toChars(ch);
      chstr = new String(chArr);
    }
    int ty = Character.getType(ch);
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
    Character.UnicodeBlock cub = Character.UnicodeBlock.of(ch);
    pw.println("Character " + ch + " [" + chstr +
               ", U+" + Integer.toHexString(ch).toUpperCase() +
               ", valid=" + Character.isValidCodePoint(ch) +
               ", suppl=" + Character.isSupplementaryCodePoint(ch) +
               ", mirror=" + Character.isMirrored(ch) +
               ", type=" + Character.getType(ch) + tyStr +
               ", uBlock=" + cub + "]");
  }

  public static void main(String[] args) {
    try {
      if (args.length < 1 || args.length > 4) {
        System.err.println("usage: java SeeChars file [inCharEncoding [outCharEncoding [nonAscii]]]");
      } else {
        String inEncoding = System.getProperty("file.encoding");
        String outEncoding;
        if (args.length < 3) {
          System.out.println("Using system default character encoding \"" + inEncoding +
                  "\" for not explicitly specified encodings");
          if (args.length > 1) {
            inEncoding = args[1];
          }
          outEncoding = inEncoding;
        } else {
          inEncoding = args[1];
          outEncoding = args[2];
          System.out.println("Input encoding is " + inEncoding + " and output encoding is " + outEncoding);
        }
        boolean onlyNonAscii = args.length > 3;
        Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), inEncoding));
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out, outEncoding), true);
        int ch;
        int pos = 0;
        while ((ch = r.read()) >= 0) {
          if (ch >= 0 && Character.isHighSurrogate((char) ch)) {
            int ch2 = r.read();
            if (ch2 >= 0) {
              ch = Character.toCodePoint((char) ch, (char) ch2);
            }
          }
          if ( ! onlyNonAscii) {
            seeCodePoint(ch, pw);
          } else if (ch > 127) {
            pw.print("Offset " + pos + ": ");
            seeCodePoint(ch, pw);
          }
          pos++;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
