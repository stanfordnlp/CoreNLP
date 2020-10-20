
package edu.stanford.nlp.ie.machinereading.common; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Simple string tokenization
 */
public class SimpleTokenize  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SimpleTokenize.class);
  /** Basic string tokenization, skipping over white spaces */
  public static ArrayList<String> tokenize(String line) {
    ArrayList<String> tokens = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(line);
    while (tokenizer.hasMoreElements()) {
      tokens.add(tokenizer.nextToken());
    }
    return tokens;
  }

  /** Basic string tokenization, skipping over white spaces */
  public static ArrayList<String> tokenize(String line, String separators) {
    ArrayList<String> tokens = new ArrayList<>();
    StringTokenizer tokenizer = new StringTokenizer(line, separators);
    while (tokenizer.hasMoreElements()) {
      tokens.add(tokenizer.nextToken());
    }
    return tokens;
  }

  /**
   * Finds the first non-whitespace character starting at start
   */
  private static int findNonWhitespace(String s, int start) {
    for (; start < s.length(); start++) {
      if (Character.isWhitespace(s.charAt(start)) == false)
        return start;
    }
    return -1;
  }

  private static int findWhitespace(String s, int start) {
    for (; start < s.length(); start++) {
      if (Character.isWhitespace(s.charAt(start)))
        return start;
    }
    return -1;
  }

  /**
   * Replaces all occurences of \" with "
   */
  private static String normalizeQuotes(String str) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < str.length(); i++) {
      // do not include \ if followed by "
      if (str.charAt(i) == '\\' && i < str.length() - 1 && str.charAt(i + 1) == '\"') {
        continue;
      } else {
        buffer.append(str.charAt(i));
      }
    }
    return buffer.toString();
  }

  /**
   * String tokenization, considering everything within quotes as 1 token
   * Regular quotes inside tokens MUST be preceded by \
   */
  public static ArrayList<String> tokenizeWithQuotes(String line) {
    ArrayList<String> tokens = new ArrayList<>();
    int position = 0;

    while ((position = findNonWhitespace(line, position)) != -1) {
      int end = -1;

      // found quoted token (not preceded by \)
      if (line.charAt(position) == '\"' && (position == 0 || line.charAt(position - 1) != '\\')) {

        // find the first quote not preceded by \
        int current = position;
        for (;;) {
          // found end of string first
          if ((end = line.indexOf('\"', current + 1)) == -1) {
            end = line.length();
            break;
          } else { // found a quote
            if (line.charAt(end - 1) != '\\') { // valid quote
              end++;
              break;
            } else { // quote preceded by \
              current = end;
            }
          }
        }

        // do not include the quotes in the token
        tokens.add(normalizeQuotes(line.substring(position + 1, end - 1)));
      }

      // regular token
      else {
        if ((end = findWhitespace(line, position + 1)) == -1)
          end = line.length();

        tokens.add(new String(line.substring(position, end)));
      }

      position = end;
    }

    return tokens;
  }

  /**
   * Constructs a valid quote-surrounded token All inside quotes are preceded by
   * \
   */
  public static String quotify(String str) {
    StringBuffer buffer = new StringBuffer();
    buffer.append('\"');
    for (int i = 0; i < str.length(); i++) {
      if (str.charAt(i) == '\"')
        buffer.append('\\');
      buffer.append(str.charAt(i));
    }
    buffer.append('\"');
    return buffer.toString();
  }

  /** Implements a simple test */
  public static void main(String[] argv) {
    String in = "T \"Athens \\\"the beautiful\\\"\" \"Athens\" \"\" \"Greece\"";
    log.info("Input: " + in);
    log.info(tokenizeWithQuotes(in));
  }
}
