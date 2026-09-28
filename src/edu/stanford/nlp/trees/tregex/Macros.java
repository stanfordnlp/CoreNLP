package edu.stanford.nlp.trees.tregex;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.Pair;

/**
 * This defines how to use macros from a file in Tregex.  Macro files
 * are expected to be lines of macros, one per line, with the original
 * and the replacement separated by tabs.  Blank lines and lines
 * starting with # are ignored.
 *
 * @author John Bauer
 */
public class Macros {
  private Macros() {} // static methods only

  /**
   * Reads macros from a UTF-8 file.
   *
   * @param filename The file to read
   * @return The (original, replacement) pairs, in file order
   * @throws RuntimeIOException if the file cannot be read
   * @throws IllegalArgumentException if a line has no tab
   */
  public static List<Pair<String, String>> readMacros(String filename) {
    return readMacros(filename, "utf-8");
  }

  /**
   * Reads macros from a file.
   *
   * @param filename The file to read
   * @param encoding The encoding of the file
   * @return The (original, replacement) pairs, in file order
   * @throws RuntimeIOException if the file cannot be read
   * @throws IllegalArgumentException if a line has no tab
   */
  public static List<Pair<String, String>> readMacros(String filename, String encoding) {
    try {
      BufferedReader bin = new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding));
      return readMacros(bin);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Reads macros from a reader, which is left open.
   *
   * @param bin The reader to read from
   * @return The (original, replacement) pairs, in the order read
   * @throws RuntimeIOException if the reader throws an IOException
   * @throws IllegalArgumentException if a line has no tab
   */
  public static List<Pair<String, String>> readMacros(BufferedReader bin) {
    try {
      List<Pair<String, String>> macros = new ArrayList<>();
      String line;
      int lineNumber = 0;
      while ((line = bin.readLine()) != null) {
        ++lineNumber;
        String trimmed = line.trim();
        if (trimmed.equals("") || trimmed.charAt(0) == '#') {
          continue;
        }
        String[] pieces = line.split("\t", 2);
        if (pieces.length < 2) {
          throw new IllegalArgumentException("Expected lines of the format " +
                                             "original (tab) replacement.  " +
                                             "Line number " + lineNumber +
                                             " does not match.");
        }
        macros.add(new Pair<>(pieces[0], pieces[1]));
      }
      return macros;
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Reads macros from a file and adds them to a compiler.
   *
   * @param compiler The compiler to add the macros to
   * @param filename The file to read; if null or empty, nothing is added
   * @param encoding The encoding of the file
   * @throws RuntimeIOException if the file cannot be read
   * @throws IllegalArgumentException if a line has no tab
   */
  public static void addAllMacros(TregexPatternCompiler compiler,
                                  String filename, String encoding) {
    if (filename == null || filename.equals("")) {
      return;
    }
    for (Pair<String, String> macro : readMacros(filename, encoding)) {
      compiler.addMacro(macro.first(), macro.second());
    }
  }

  /**
   * Reads macros from a reader, which is left open, and adds them to a compiler.
   *
   * @param compiler The compiler to add the macros to
   * @param br The reader to read from
   * @throws RuntimeIOException if the reader throws an IOException
   * @throws IllegalArgumentException if a line has no tab
   */
  public static void addAllMacros(TregexPatternCompiler compiler,
                                  BufferedReader br) {
    for (Pair<String, String> macro : readMacros(br)) {
      compiler.addMacro(macro.first(), macro.second());
    }
  }

}