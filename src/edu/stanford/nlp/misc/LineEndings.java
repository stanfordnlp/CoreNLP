package edu.stanford.nlp.misc;

import edu.stanford.nlp.io.IOUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;


/**
 * Report line endings of a file and optionally fix them.
 *
 * @author Christopher Manning
 */
public class LineEndings {

  private static boolean nonUnix;
  private static boolean fix;
  private static char fixChar = '\n';
  private static boolean undoubleCR;


  private LineEndings() {
  }


  public static void processFile(String name) {
    int numCR = 0;
    int numLF = 0;
    int numCRLF = 0;
    boolean lastWasCR = false;
    String content = null;

    // we can't handle directories.
    File fl = new File(name);
    if (fl.isDirectory()) {
      System.err.println(name + ": a directory (NOT PROCESSED)");
      return;
    }

    try {
      content = IOUtils.slurpFile(name);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (content == null) {
      // File was not read successfully
      System.err.println(name + ": could not be read (no such file?)");
      return;
    }
    int cleng = content.length();
    for (int i = 0; i < cleng; i++) {
      char ch = content.charAt(i);
      if (ch == '\n') {
        numLF++;
        if (lastWasCR) {
          numCRLF++;
        }
        lastWasCR = false;
      } else if (ch == '\r') {
        numCR++;
        lastWasCR = true;
      } else {
        lastWasCR = false;
      }
    }
    if (numCR == 0 && numLF > 0) {
      if (!nonUnix) {
        System.err.print(name);
        System.err.print(": ");
        System.err.println("Unix text file (LF line endings)");
      }
    } else {
      System.err.print(name);
      System.err.print(": ");
      if (numLF == 0 && numCR > 0) {
        System.err.println("Mac text file (CR line endings)");
      } else if (numCRLF == numCR && numCRLF == numLF && numCRLF > 0) {
        System.err.println("Windows text file (CR/LF line endings)");
      } else if (numLF == 0 && numCR == 0) {
        System.err.println("One line text file (no line endings!)");
      } else {
        System.err.println("Mixed up text file (varied line endings)");
      }
    }
    if (fix && ((fixChar == '\n' && numCR > 0) ||
                (fixChar == '\r' && numLF > 0))) {
      boolean justWroteCRasLF = false;
      char[] out = new char[cleng + 1];  // + 1 in case add eol at eof
      int numWrote = 0;
      for (int i = 0; i < cleng; i++) {
        char ch = content.charAt(i);
        if (ch == '\n') {
          if (justWroteCRasLF) {
            justWroteCRasLF = false;
          } else {
            out[numWrote++] = fixChar;
          }
        } else if (ch == '\r') {
          if (justWroteCRasLF && undoubleCR) {
            justWroteCRasLF = false;
          } else {
            out[numWrote++] = fixChar;
            justWroteCRasLF = true;
          }
        } else {
          out[numWrote++] = ch;
          justWroteCRasLF = false;
        }
      }
      // finish with nl if not there
      if (out[numWrote - 1] != fixChar) {
        System.err.println("  Adding eoln at eof");
        out[numWrote++] = fixChar;
      }
      try {
        Writer w = new FileWriter(name);
        w.write(out, 0, numWrote);
        w.close();
        System.err.println("  Fixed!");
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }

  /** Runs the program on some files. <br>
   *  Usage: LineEndings [-nonUnix] [-fix] [-undoubleCR] filename* <br>
   *  -nonUnix means to only report files that do not have Unix line endings.
   *  -fix means to change line endings to Unix (LF) line endings. Note that
   *  this is done in situ, and so is a little dangerous if your computer
   *  crashes at just the wrong moment.
   *  -undoubleCR says to remove duplicated CR characters at end of line while
   *  fixing files.
   *  -mac says to fix with a Mac CR line ending used instead of LF.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("usage: java LineEndings [-fix|-mac|-nonUnix|-undoubleCR] filename*");
    } else {
      int i = 0;
      while (i < args.length && args[i].charAt(0) == '-') {
        if (args[i].equals("-fix")) {
          fix = true;
        } else if (args[i].equals("-mac")) {
          fix = true;
          fixChar = '\r';
        } else if (args[i].equals("-nonUnix")) {
          nonUnix = true;
        } else if (args[i].equals("-undoubleCR")) {
          undoubleCR = true;
        } else {
          System.err.println("Unknown flag: " + args[i]);
        }
        i++;
      }
      for (; i < args.length; i++) {
        processFile(args[i]);
      }
    }
  }

}
