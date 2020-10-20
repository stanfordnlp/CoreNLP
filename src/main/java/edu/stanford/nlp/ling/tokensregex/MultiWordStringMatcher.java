package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.CacheMap;
import edu.stanford.nlp.util.IntPair;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds multi word strings in a piece of text
 *
 * @author Angel Chang
 */
public class MultiWordStringMatcher {

  /**
   * if {@code matchType} is {@code EXCT}: match exact string
   * <br>if {@code matchType} is {@code EXCTWS}: match exact string, except whitespace can match multiple whitespaces
   * <br>if {@code matchType} is {@code LWS}: match case insensitive string, except whitespace can match multiple whitespaces
   * <br>if {@code matchType} is {@code LNRM}: disregards punctuation, does case insensitive match
   * <br>if {@code matchType} is {@code REGEX}: interprets string as regex already
   */
  public enum MatchType { EXCT, EXCTWS, LWS, LNRM, REGEX };

  private boolean caseInsensitiveMatch = false;
  private MatchType matchType = MatchType.EXCTWS;

  public MultiWordStringMatcher(MatchType matchType)
  {
    setMatchType(matchType);
  }

  public MultiWordStringMatcher(String matchTypeStr) {
    setMatchType(MultiWordStringMatcher.MatchType.valueOf(matchTypeStr));
  }

  public MatchType getMatchType() {
    return matchType;
  }

  public void setMatchType(MatchType matchType) {
    this.matchType = matchType;
    caseInsensitiveMatch = (matchType != MatchType.EXCT && matchType != MatchType.EXCTWS);
    targetStringPatternCache.clear();
  }

  /**
   * Finds target string in text and put spaces around it so it will be matched with we match against tokens.
   * @param text - String in which to look for the target string
   * @param targetString - Target string to look for
   * @return Updated text with spaces around target string
   */
  public static String putSpacesAroundTargetString(String text, String targetString) {
    return markTargetString(text, targetString, " ", " ", true);
  }

  protected static String markTargetString(String text, String targetString, String beginMark, String endMark, boolean markOnlyIfSpace) {
    StringBuilder sb = new StringBuilder(text);
    int i = sb.indexOf(targetString);
    while (i >= 0) {
      boolean matched = true;
      boolean markBefore = !markOnlyIfSpace;
      boolean markAfter = !markOnlyIfSpace;
      if (i > 0) {
        char charBefore = sb.charAt(i-1);
        if (Character.isLetterOrDigit(charBefore)) {
          matched = false;
        } else if (!Character.isWhitespace(charBefore)) {
          markBefore = true;
        }
      }
      if (i + targetString.length() < sb.length()) {
        char charAfter = sb.charAt(i+targetString.length());
        if (Character.isLetterOrDigit(charAfter)) {
          matched = false;
        } else if (!Character.isWhitespace(charAfter)) {
          markAfter = true;
        }
      }
      if (matched) {
        if (markBefore) {
          sb.insert(i, beginMark);
          i += beginMark.length();
        }
        i = i + targetString.length();
        if (markAfter) {
          sb.insert(i, endMark);
          i += endMark.length();
        }
      } else {
        i++;
      }
      i = sb.indexOf(targetString, i);
    }
    return sb.toString();
  }

  /**
   * Finds target string in text span from character start to end (exclusive) and returns offsets
   *   (does EXCT string matching).
   *
   * @param text - String in which to look for the target string
   * @param targetString - Target string to look for
   * @param start - position to start search
   * @param end - position to end search
   * @return list of integer pairs indicating the character offsets (begin, end - exclusive)
   *         at which the targetString can be find
   */
  protected static List<IntPair> findTargetStringOffsetsExct(String text, String targetString, int start, int end) {
    if (start > text.length()) return null;
    if (end > text.length()) return null;
    List<IntPair> offsets = null;
    int i = text.indexOf(targetString, start);
    if (i >= 0 && i < end) { offsets = new ArrayList<>(); }
    while (i >= 0 && i < end) {
      boolean matched = true;
      if (i > 0) {
        char charBefore = text.charAt(i-1);
        if (Character.isLetterOrDigit(charBefore)) {
          matched = false;
        }
      }
      if (i + targetString.length() < text.length()) {
        char charAfter = text.charAt(i+targetString.length());
        if (Character.isLetterOrDigit(charAfter)) {
          matched = false;
        }
      }
      if (matched) {
        offsets.add(new IntPair(i, i+targetString.length()));
        i += targetString.length();
      } else {
        i++;
      }
      i = text.indexOf(targetString, i);
    }
    return offsets;
  }

  private CacheMap<String, Pattern> targetStringPatternCache = new CacheMap<>(5000);

  public final static Comparator<String> LONGEST_STRING_COMPARATOR = new LongestStringComparator();
  public static class LongestStringComparator implements Comparator<String> {
    public int compare(String o1, String o2) {
      int l1 = o1.length();
      int l2 = o2.length();
      if (l1 == l2) {
        return o1.compareTo(o2);
      } else {
        return (l1 > l2)? -1:1;
      }
    }
  }

  public Pattern getPattern(String[] targetStrings) {
    String regex = getRegex(targetStrings);
    return Pattern.compile(regex);
  }

  public String getRegex(String[] targetStrings) {
    List<String> strings = Arrays.asList(targetStrings);
    // Sort by longest string first
    strings.sort(LONGEST_STRING_COMPARATOR);
    StringBuilder sb = new StringBuilder();
    for (String s:strings) {
      if (sb.length() > 0) {
        sb.append("|");
      }
      sb.append(getRegex(s));
    }
    String regex = sb.toString();
    return regex;
  }

  public Pattern getPattern(String targetString)
  {
    Pattern pattern = targetStringPatternCache.get(targetString);
    if (pattern == null) {
      pattern = createPattern(targetString);
      targetStringPatternCache.put(targetString, pattern);
    }
    return pattern;
  }

  public Pattern createPattern(String targetString) {
    String wordRegex = getRegex(targetString);
    return Pattern.compile(wordRegex);
  }

  public String getRegex(String targetString) {
    String wordRegex;
    switch (matchType) {
      case EXCT: wordRegex = Pattern.quote(targetString); break;
      case EXCTWS: wordRegex = getExctWsRegex(targetString); break;
      case LWS: wordRegex = getLWsRegex(targetString); break;
      case LNRM: wordRegex = getLnrmRegex(targetString); break;
      case REGEX: wordRegex = targetString; break;
      default:
        throw new UnsupportedOperationException();
    }
    return wordRegex;
  }

  private static Pattern whitespacePattern = Pattern.compile("\\s+");
  private static final Pattern punctWhitespacePattern = Pattern.compile("\\s*(\\p{Punct})\\s*");

  public static String getExctWsRegex(String targetString) {
    StringBuilder sb = new StringBuilder();
    String[] fields = whitespacePattern.split(targetString);
    for (String field:fields) {
      // require at least one whitespace if there is whitespace in target string
      if (sb.length() > 0) {
        sb.append("\\s+");
      }
      // Allow any number of spaces between punctuation and text
      String tmp = punctWhitespacePattern.matcher(field).replaceAll(" $1 ");
      tmp = tmp.trim();
      String[] punctFields = whitespacePattern.split(tmp);
      for (String f:punctFields) {
        if (sb.length() > 0) {
          sb.append("\\s*");
        }
        sb.append(Pattern.quote(f));
      }
    }
    return sb.toString();
  }

  public static String getLWsRegex(String targetString) {
    return "(?iu)" + getExctWsRegex(targetString);
  }

  private static final Pattern lnrmDelimPatternAny = Pattern.compile("(?:\\p{Punct}|\\s)*");
  private static final Pattern lnrmDelimPattern = Pattern.compile("(?:\\p{Punct}|\\s)+");

  public static String getLnrmRegex(String targetString) {
    StringBuilder sb = new StringBuilder("(?iu)");
    String[] fields = lnrmDelimPattern.split(targetString);
    boolean first = true;
    for (String field:fields) {
      if (!first) {
        sb.append(lnrmDelimPatternAny);
      } else {
        first = false;
      }
      sb.append(Pattern.quote(field));
    }
    return sb.toString();
  }

  /**
   * Finds target string in text and returns offsets using regular expressions
   *   (matches based on set matchType).
   *
   * @param text - String in which to find target string
   * @param targetString - Target string to look for
   * @param start - position to start search
   * @param end - position to end search
   * @return list of integer pairs indicating the character offsets (begin, end - exclusive)
   *         at which the target string can be find
   */
  protected List<IntPair> findTargetStringOffsetsRegex(String text, String targetString, int start, int end) {
    if (start > text.length()) return null;
    if (end > text.length()) return null;
    Pattern targetPattern = getPattern(targetString);
    return findOffsets(targetPattern, text, start, end);
  }

  /**
   * Finds pattern in text and returns offsets.
   *
   * @param pattern - pattern to look for
   * @param text - String in which to look for the pattern
   * @return list of integer pairs indicating the character offsets (begin, end - exclusive)
   *         at which the pattern can be find
   */
  public static List<IntPair> findOffsets(Pattern pattern, String text) {
    return findOffsets(pattern, text, 0, text.length());
  }

  /**
   * Finds pattern in text span from character start to end (exclusive) and returns offsets.
   *
   * @param pattern - pattern to look for
   * @param text - String in which to look for the pattern
   * @param start - position to start search
   * @param end - position to end search
   * @return list of integer pairs indicating the character offsets (begin, end - exclusive)
   *         at which the pattern can be find
   */
  public static List<IntPair> findOffsets(Pattern pattern, String text, int start, int end) {
    Matcher matcher = pattern.matcher(text);
    List<IntPair> offsets = null;
    matcher.region(start,end);
    int i = (matcher.find())? matcher.start():-1;
    if (i >= 0 && i < end) { offsets = new ArrayList<>(); }
    while (i >= 0 && i < end) {
      boolean matched = true;
      int matchEnd = matcher.end();
      if (i > 0) {
        char charBefore = text.charAt(i-1);
        if (Character.isLetterOrDigit(charBefore)) {
          matched = false;
        }
      }
      if (matchEnd < text.length()) {
        char charAfter = text.charAt(matchEnd);
        if (Character.isLetterOrDigit(charAfter)) {
          matched = false;
        }
      }
      if (matched) {
        offsets.add(new IntPair(i, matchEnd));
      }
      i = (matcher.find())? matcher.start():-1;
    }
    return offsets;
  }

  /**
   * Finds target string in text and returns offsets
   *   (matches based on set matchType).
   *
   * @param text - String in which to look for the target string
   * @param targetString - Target string to look for
   * @return list of integer pairs indicating the character offsets (begin, end - exclusive)
   *         at which the target string can be find
   */
  public List<IntPair> findTargetStringOffsets(String text, String targetString) {
    return findTargetStringOffsets(text, targetString, 0, text.length());
  }

  /**
   * Finds target string in text span from character start to end (exclusive) and returns offsets
   *   (matches based on set matchType).
   *
   * @param text - String in which to look for the target string
   * @param targetString - Target string to look for
   * @param start - position to start search
   * @param end - position to end search
   * @return list of integer pairs indicating the character offsets (begin, end - exclusive)
   *         at which the target string can be find
   */
  public List<IntPair> findTargetStringOffsets(String text, String targetString, int start, int end) {
    switch (matchType) {
      case EXCT: return findTargetStringOffsetsExct(text, targetString, start, end);
      default: return findTargetStringOffsetsRegex(text, targetString, start, end);
    }
  }

}
