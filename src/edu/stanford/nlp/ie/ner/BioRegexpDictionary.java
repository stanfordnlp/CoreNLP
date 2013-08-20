package edu.stanford.nlp.ie.ner;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dictionary which allows a "fuzzy" lookup of biological terms.  Maps
 * a sequence to a Set of associated objects, which can be retrieved by
 * a fuzzy matching against a regular expression based on the sequence.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class BioRegexpDictionary {
  // length of the key used to subdivide the dictionary in order to make it faster
  private static final int KEY_LENGTH = 2;

  private boolean caseSensitive;
  private HashMap<String, Map<String, Entry>> dictionary;
  private int fromIndex;
  private Matcher bestMatcher;
  private Set<String> bestData;

  /**
   * Creates a <b>case sensitive</b> BioRegexpDictionary.
   */
  public BioRegexpDictionary() {
    this(true);
  }

  /**
   * Creates a BioRegexpDictionary
   *
   * @param caseSensitive Whether or not the matching is case sensitive or not.
   */
  public BioRegexpDictionary(boolean caseSensitive) {
    this.caseSensitive = caseSensitive;
    dictionary = new HashMap<String, Map<String, Entry>>();
  }

  /**
   * Adds the given sequence to set of sequences to match against.  If matched,
   * a Set of data associated with the sequence is returned (since there
   * could be more than one instance of the given sequence).
   *
   * @param sequence A sequence to match against
   * @param data     The data to associate with the sequence
   */
  public void add(String sequence, String data) {
    if (!caseSensitive) {
      sequence = sequence.toLowerCase();
    }
    String key = sequence.substring(0, Math.min(KEY_LENGTH, sequence.length()));
    Map<String, Entry> patterns = dictionary.get(key);
    if (patterns == null) {
      patterns = new HashMap<String, Entry>();
      dictionary.put(key, patterns);
    }
    //System.err.println("==> "+sequence);
    String patternString = makePattern(sequence);
    //System.err.println("==> "+patternString);
    Entry pattern = patterns.get(patternString);
    if (pattern == null) {
      pattern = new Entry(patternString);
      patterns.put(patternString, pattern);
    }
    pattern.addData(data);
  }

  /**
   * Returns whether or not the start of String s, matches any known patterns.
   */
  public boolean lookingAt(String s) {
    return lookingAt(s, 0);
  }

  /**
   * Returns whether or not the String s, starting at fromIndex, matches
   * any known patterns.  If a match is found, subsequent calls to {@link #end}
   * will return the index of the end of the match.  Attempts to match the longest
   * string possible.
   */
  public boolean lookingAt(String s, int fromIndex) {
    bestMatcher = null;
    this.fromIndex = fromIndex;
    if (!caseSensitive) {
      s = s.toLowerCase();
    }
    s = s.substring(fromIndex);
    String key = s.substring(0, Math.min(KEY_LENGTH, s.length()));
    //System.err.println(">> "+key);
    Map<String, Entry> patterns = dictionary.get(key);
    if (patterns == null) {
      return false;
    }
    //System.err.println("true");
    int maxLength = 0;
    bestData = new HashSet<String>();

    for (Iterator<Entry> iter = patterns.values().iterator(); iter.hasNext();) {
      Entry pattern = iter.next();
      Matcher m = pattern.pattern().matcher(s);
      if (m.lookingAt()) {
        int length = m.end() - m.start();
        if (length > maxLength) {
          bestMatcher = m;
          bestData = new HashSet<String>();
          bestData.addAll(pattern.data());
          maxLength = length;
        } else if (length > 0 && length == maxLength) {
          bestData.addAll(pattern.data());
        }
      }
    }
    return (bestMatcher != null);
  }

  /**
   * Returns the end index of the match.
   */
  public int end() {
    if (bestMatcher == null) {
      return -1;
    }
    return bestMatcher.end() + fromIndex;
  }

  /**
   * Returns the data associated with the best match.
   */
  public Set<String> data() {
    return bestData;
  }

  /**
   * Returns the data associated with the best match.
   */
  public String firstData() {
    return bestData.toArray(new String[0])[0];
  }

  /**
   * Converts a gene String into a Pattern string that can match several variations of that String.
   */
  public static String makePattern(String synonym) {
    //System.err.println("synonym: " + synonym);
    String pattern = synonym;

    // replace question marks with escaped question marks
    if (pattern.indexOf('?') != -1) {
      pattern = pattern.replaceAll("\\?", "\\\\?");
    }

    // make slashes optional - this should be first
    if (pattern.indexOf('\\') != -1) {
      pattern = pattern.replaceAll("\\\\(?=\\b|[^\\?\\[\\]])", "[\\\\ ]?");
    }

    // make all spaces optional - this should be second
    pattern = pattern.replaceAll(" ", " ?");

    // replace brackets with optional escaped brackets - this should be third
    if (pattern.indexOf('[') != -1) {
      pattern = pattern.replaceAll("\\[", "[\\\\[ ]?");
    }
    if (pattern.indexOf(']') != -1) {
      pattern = pattern.replaceAll("\\](?=\\b|[^?])", "[\\\\] ]?");
    }

    // replace curly brackets with optional escaped curly brackets
    if (pattern.indexOf('{') != -1) {
      pattern = pattern.replaceAll("\\{", "[\\\\{ ]?");
    }
    if (pattern.indexOf('}') != -1) {
      pattern = pattern.replaceAll("\\}", "[\\\\} ]?");
    }

    // replace + with escaped +
    if (pattern.indexOf('+') != -1) {
      pattern = pattern.replaceAll("\\+", "\\\\+");
    }
    // replace * with escaped *
    if (pattern.indexOf('*') != -1) {
      pattern = pattern.replaceAll("\\*", "\\\\*");
    }
    if (pattern.indexOf('/') != -1) {
      pattern = pattern.replaceAll("/", "[/ ]?");
    }
    // make colons optional
    if (pattern.indexOf(':') != -1) {
      pattern = pattern.replaceAll(":", "[: ]?");
    }
    // replace periods with optional escaped periods
    if (pattern.indexOf('.') != -1) {
      pattern = pattern.replaceAll("\\.", "\\\\.?");
    }
    // make parentheses optional
    if (pattern.indexOf('(') != -1) {
      pattern = pattern.replaceAll("\\(", "[( ]?");
    }
    if (pattern.indexOf(')') != -1) {
      pattern = pattern.replaceAll("\\)", "[) ]?");
    }
    // make -'s optional
    if (pattern.indexOf('-') != -1) {
      pattern = pattern.replaceAll("-", "[- ]?");
    }
    // allow British spellings
    if (pattern.indexOf("aemia") != -1) {
      pattern = pattern.replaceAll("aemia", "a?emia");
    }
    if (pattern.indexOf("our") != -1) {
      pattern = pattern.replaceAll("our", "ou?r");
    }
    // wrap pattern in boundary tags
    pattern = "\\b" + pattern + "\\b";
    //System.err.println("pattern: " + pattern);
    // allow case insensitive matching
    return pattern;
  }

  /**
   * An entry in the dictionary consists of a Pattern, and it's associated objects.
   */
  private class Entry {
    public Pattern pattern;
    public Set<String> data;

    Entry(String patternString) {
      pattern = Pattern.compile(patternString);
      data = new HashSet<String>();
    }

    public void addData(String data) {
      this.data.add(data);
    }

    public Set<String> data() {
      return data;
    }

    public Pattern pattern() {
      return pattern;
    }
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    BioRegexpDictionary matcher = new BioRegexpDictionary(false);
    //matcher.add("gene-", "abc");
    matcher.add("gene match", "def");
    if (matcher.lookingAt("gene match blah")) {
      System.err.println(matcher.data().toString());
    }
    if (!matcher.lookingAt("gen")) {
      System.err.println("Negative test worked");
    }
  }
}
