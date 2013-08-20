package edu.stanford.nlp.ie.pascal;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates "abstract" workshop/conference names by removing common words/acronyms.
 *
 * @author Jenny Finkel
 */
public class PascalAnswers {

  static String[] stopwordsA = {"conference", "workshop", "annual", "international", "european", "of", "a", "the", "on", "*newline*", "in", "and", ",", ".", ":", ";", "symposium", "*", "'", "\"", "&", "(", ")", "-", "track", "+", "for", "_", "|", "[", "]", "{", "}", "!", "#", "^", "ieee", "acm", "iclp", "cia", "/"};

  static Set stopwords = new HashSet(Arrays.asList(stopwordsA));

  public static List process(String orig) {

    List origAsList = Arrays.asList(orig.split("\\s+"));
    List result = process(origAsList);
    return result;
  }

  public static List process(List orig) {
    List canonical = new ArrayList();
    for (Iterator iter = orig.iterator(); iter.hasNext();) {
      String s = (String) iter.next();
      s = s.toLowerCase();
      if (s.endsWith("-")) {
        s = s.substring(0, s.length() - 1);
        if (s.length() == 0) {
          continue;
        }
      }
      int index = s.indexOf("-");
      if (index >= 0) {
        String s1 = s.substring(0, index);
        String s2 = s.substring(index + 1);
        if (!stopwords.contains(s1)) {
          Matcher m = removePattern.matcher(s1);
          if (!m.matches()) {
            canonical.add(s1);
          }
        }
        if (!stopwords.contains(s2)) {
          Matcher m = removePattern.matcher(s2);
          if (!m.matches()) {
            canonical.add(s2);
          }
        }
      } else {
        if (!stopwords.contains(s)) {
          Matcher m = removePattern.matcher(s);
          if (!m.matches()) {
            canonical.add(s);
          }
        }
      }
    }
    return canonical;
  }


  private static Pattern removePattern = Pattern.compile("(?:(?:first|second|third|fourth|fifth|" + "sixth|seventh|eighth|ninth|tenth|" + "eleventh|twelfth|thirteenth|" + "fourteenth|fifteenth|sixteenth|" + "seventeen|eighteenth|ninteenth|" + "twenty|twentieth|thirty|thirtieth|" + "fourty|fourtieth|fifty|fiftieth|" + "sixty|sixtieth|seventy|seventieth|" + "eighty|eightieth|ninety|ninetieth|" + "one|two|three|four|five|six|seven|" + "eight|nine|hundred|hundreth)-?)+|[0-9]+|st|nd|rd|th|" + "[0-9]+/[0-9]+/[0-9]+|[ivx]+", Pattern.CASE_INSENSITIVE);

}
