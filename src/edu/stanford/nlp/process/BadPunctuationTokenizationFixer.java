package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BadPunctuationTokenizationFixer
 * fixes bad tokenization of a string, e.g., if " Mr. " was tokenized as " Mr . "
 * then this will restore its original form (note need for preceding and trailing
 * space).  (This is a rather specific and ad hoc class designed for dealing
 * with a particular kind of tokenization mistake of separated out periods and
 * commas.)
 *
 * @author Teg Grenager
 */
public class BadPunctuationTokenizationFixer implements Function<String,String>, Serializable {

  private static final long serialVersionUID = -6771080630746364974L;

  private static final String[] oldRegex = new String[]{
      " [A-Z][a-z]{1,3} \\. ", // matches Mr . and Co .
      " (?:[A-Za-z] \\. )+", // matches U . S . and U . S . A . and P . S .
      " \\d{1,3} (?:, \\d{3} )+", // matches 1 , 300 and 23 , 000 , 000
      " \\d+ \\. \\d* " // matches 0 . 1 and 1 . 0 and 1 .
  };

  private final Pattern[] pattern;

  public BadPunctuationTokenizationFixer() {
    pattern = new Pattern[oldRegex.length];
    for (int i = 0; i < oldRegex.length; i++) {
      pattern[i] = Pattern.compile(oldRegex[i]);
    }
  }

  /**
   * Fixes the bad tokenization of this string. For instance, if "Mr." was tokenized as
   * "Mr . " then this will restore its original form.
   *
   * @param s A String with bad tokenization
   * @return a String with better Tokenization
   * @throws ClassCastException if in is not a String
   */
  public String apply(String s) {
    for (Pattern p : pattern) {
      Matcher m = p.matcher(s);
      StringBuffer sb = new StringBuffer(); // need StringBuffer for Matcher appendReplacement() method!
      while (m.find()) {
        //         System.out.println("group: \"" + m.group() + "\"");
        String newGroup = removeAllInnerSpaces(m.group());
        //         System.out.println("newGroup: \"" + newGroup + "\"");
        m.appendReplacement(sb, newGroup);
      }
      m.appendTail(sb);
      s = sb.toString();
    }
    return s;
  }

  private static String removeAllInnerSpaces(String s) {
    StringBuilder sb = new StringBuilder();
    sb.append(s.charAt(0));
    for (int i = 1, sLenM1 = s.length() - 1; i < sLenM1; i++) {
      char c = s.charAt(i);
      if (c != ' ') {
        sb.append(c);
      }
    }
    sb.append(s.charAt(s.length() - 1));
    return sb.toString();
  }


//public static void main(String[] args) throws Exception {
//BadPunctuationTokenizationFixer fixer = new BadPunctuationTokenizationFixer();
//BufferedReader in = new BufferedReader(new FileReader(args[0]));
//String line = in.readLine();
//while (line != null) {
//System.out.println("old line: " + line);
//String newLine = fixer.apply(line);
//System.out.println("new line: " + newLine);
//line = in.readLine();
//System.out.println();
//}
//}

}
