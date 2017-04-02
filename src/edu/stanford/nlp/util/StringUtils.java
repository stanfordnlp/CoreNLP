package edu.stanford.nlp.util;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasOffset;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * StringUtils is a class for random String things, including output formatting and command line argument parsing.
 * <p>
 * Many of these methods will be familiar to perl users: {@link #join(Iterable)}, {@link #split(String, String)}, {@link
 * #trim(String, int)}, {@link #find(String, String)}, {@link #lookingAt(String, String)}, and {@link #matches(String,
 * String)}.
 * <p>
 * There are also useful methods for padding Strings/Objects with spaces on the right or left for printing even-width
 * table columns: {@link #padLeft(int, int)}, {@link #pad(String, int)}.
 *
 * <p>Example: print a comma-separated list of numbers:</p>
 * <p>{@code System.out.println(StringUtils.pad(nums, &quot;, &quot;));}</p>
 * <p>Example: print a 2D array of numbers with 8-char cells:</p>
 * <p><code>for(int i = 0; i &lt; nums.length; i++) {<br>
 * &nbsp;&nbsp;&nbsp; for(int j = 0; j &lt; nums[i].length; j++) {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
 * System.out.print(StringUtils.leftPad(nums[i][j], 8));<br>
 * &nbsp;&nbsp;&nbsp; <br>
 * &nbsp;&nbsp;&nbsp; System.out.println();<br>
 * </code></p>
 *
 * @author Dan Klein
 * @author Christopher Manning
 * @author Tim Grow (grow@stanford.edu)
 * @author Chris Cox
 * @version 2006/02/03
 */
public class StringUtils  {

  // todo [cdm 2016]: Remove CoreMap/CoreLabel methods from this class
  // todo [cdm 2016]: Write a really good join method for this class, like William's Ruby one

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(StringUtils.class);

  /**
   * Don't let anyone instantiate this class.
   */
  private StringUtils() {}

  public static final String[] EMPTY_STRING_ARRAY = new String[0];
  private static final String PROP = "prop";
  private static final String PROPS = "props";
  private static final String PROPERTIES = "properties";
  private static final String ARGS = "args";
  private static final String ARGUMENTS = "arguments";

  /**
   * Say whether this regular expression can be found inside
   * this String.  This method provides one of the two "missing"
   * convenience methods for regular expressions in the String class
   * in JDK1.4.  This is the one you'll want to use all the time if
   * you're used to Perl.  What were they smoking?
   *
   * @param str   String to search for match in
   * @param regex String to compile as the regular expression
   * @return Whether the regex can be found in str
   */
  public static boolean find(String str, String regex) {
    return Pattern.compile(regex).matcher(str).find();
  }

  /**
   * Convenience method: a case-insensitive variant of Collection.contains.
   *
   * @param c Collection&lt;String&gt;
   * @param s String
   * @return true if s case-insensitively matches a string in c
   */
  public static boolean containsIgnoreCase(Collection<String> c, String s) {
    for (String sPrime: c) {
      if (sPrime.equalsIgnoreCase(s))
        return true;
    }
    return false;
  }

  /**
   * Say whether this regular expression can be found at the beginning of
   * this String.  This method provides one of the two "missing"
   * convenience methods for regular expressions in the String class
   * in JDK1.4.
   *
   * @param str   String to search for match at start of
   * @param regex String to compile as the regular expression
   * @return Whether the regex can be found at the start of str
   */
  public static boolean lookingAt(String str, String regex) {
    return Pattern.compile(regex).matcher(str).lookingAt();
  }

  /**
   * Takes a string of the form "x1=y1,x2=y2,..." such
   * that each y is an integer and each x is a key.  A
   * String[] s is returned such that s[yn]=xn.
   *
   * @param map A string of the form "x1=y1,x2=y2,..." such
   *     that each y is an integer and each x is a key.
   * @return  A String[] s is returned such that s[yn]=xn
   */
  public static String[] mapStringToArray(String map) {
    String[] m = map.split("[,;]");
    int maxIndex = 0;
    String[] keys = new String[m.length];
    int[] indices = new int[m.length];
    for (int i = 0; i < m.length; i++) {
      int index = m[i].lastIndexOf('=');
      keys[i] = m[i].substring(0, index);
      indices[i] = Integer.parseInt(m[i].substring(index + 1));
      if (indices[i] > maxIndex) {
        maxIndex = indices[i];
      }
    }
    String[] mapArr = new String[maxIndex + 1];
    // Arrays.fill(mapArr, null); // not needed; Java arrays zero initialized
    for (int i = 0; i < m.length; i++) {
      mapArr[indices[i]] = keys[i];
    }
    return mapArr;
  }


  /**
   * Takes a string of the form "x1=y1,x2=y2,..." and returns Map.
   *
   * @param map A string of the form "x1=y1,x2=y2,..."
   * @return  A Map m is returned such that m.get(xn) = yn
   */
  public static Map<String, String> mapStringToMap(String map) {
    String[] m = map.split("[,;]");
    Map<String, String> res = Generics.newHashMap();
    for (String str : m) {
      int index = str.lastIndexOf('=');
      String key = str.substring(0, index);
      String val = str.substring(index + 1);
      res.put(key.trim(), val.trim());
    }
    return res;
  }

  public static List<Pattern> regexesToPatterns(Iterable<String> regexes) {
    List<Pattern> patterns = new ArrayList<>();
    for (String regex:regexes) {
      patterns.add(Pattern.compile(regex));
    }
    return patterns;
  }

  /**
   * Given a pattern, which contains one or more capturing groups, and a String,
   * returns a list with the values of the
   * captured groups in the pattern. If the pattern does not match, returns
   * null. Note that this uses Matcher.find() rather than Matcher.matches().
   * If str is null, returns null.
   */
  public static List<String> regexGroups(Pattern regex, String str) {
    if (str == null) {
      return null;
    }

    Matcher matcher = regex.matcher(str);
    if ( ! matcher.find()) {
      return null;
    }

    List<String> groups = new ArrayList<>(matcher.groupCount());
    for (int index = 1; index <= matcher.groupCount(); index++) {
      groups.add(matcher.group(index));
    }

    return groups;
  }

  /**
   * Say whether this regular expression matches
   * this String.  This method is the same as the String.matches() method,
   * and is included just to give a call that is parallel to the other
   * static regex methods in this class.
   *
   * @param str   String to search for match at start of
   * @param regex String to compile as the regular expression
   * @return Whether the regex matches the whole of this str
   */
  public static boolean matches(String str, String regex) {
    return Pattern.compile(regex).matcher(str).matches();
  }


  public static Set<String> stringToSet(String str, String delimiter)
  {
    Set<String> ret = null;
    if (str != null) {
      String[] fields = str.split(delimiter);
      ret = Generics.newHashSet(fields.length);
      for (String field:fields) {
        field = field.trim();
        ret.add(field);
      }
    }
    return ret;
  }


  public static String joinWords(Iterable<? extends HasWord> l, String glue) {
    StringBuilder sb = new StringBuilder(l instanceof Collection ? ((Collection) l).size() : 64);
    boolean first = true;
    for (HasWord o : l) {
      if ( ! first) {
        sb.append(glue);
      } else {
        first = false;
      }
      sb.append(o.word());
    }
    return sb.toString();
  }


  public static <E> String join(List<? extends E> l, String glue, Function<E,String> toStringFunc, int start, int end) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    start = Math.max(start, 0);
    end = Math.min(end, l.size());
    for (int i = start; i < end; i++) {
      if ( ! first) {
        sb.append(glue);
      } else {
        first = false;
      }
      sb.append(toStringFunc.apply(l.get(i)));
    }
    return sb.toString();
  }

  public static String joinWords(List<? extends HasWord> l, String glue, int start, int end) {
    return join(l, glue, HasWord::word, start, end);
  }

  private static final Function<Object,String> DEFAULT_TOSTRING = Object::toString;

  public static String joinFields(List<? extends CoreMap> l, final Class field, final String defaultFieldValue,
                                  String glue, int start, int end, final Function<Object,String> toStringFunc) {
    return join(l, glue, new Function<CoreMap, String>() {
      public String apply(CoreMap in) {
        Object val = in.get(field);
        return (val != null)? toStringFunc.apply(val):defaultFieldValue;
      }
    }, start, end);
  }

  public static String joinFields(List<? extends CoreMap> l, final Class field, final String defaultFieldValue,
                                  String glue, int start, int end) {
    return joinFields(l, field, defaultFieldValue, glue, start, end, DEFAULT_TOSTRING);
  }

  public static String joinFields(List<? extends CoreMap> l, final Class field, final Function<Object,String> toStringFunc) {
    return joinFields(l, field, "-", " ", 0, l.size(), toStringFunc);
  }

  public static String joinFields(List<? extends CoreMap> l, final Class field) {
    return joinFields(l, field, "-", " ", 0, l.size());
  }

  public static String joinMultipleFields(List<? extends CoreMap> l, final Class[] fields, final String defaultFieldValue,
                                          final String fieldGlue, String glue, int start, int end, final Function<Object,String> toStringFunc) {
    return join(l, glue, (Function<CoreMap, String>) in -> {
      StringBuilder sb = new StringBuilder();
      for (Class field: fields) {
        if (sb.length() > 0) {
          sb.append(fieldGlue);
        }
        Object val = in.get(field);
        String str = (val != null)? toStringFunc.apply(val):defaultFieldValue;
        sb.append(str);
      }
      return sb.toString();
    }, start, end);
  }

  public static String joinMultipleFields(List<? extends CoreMap> l, final Class[] fields, final Function<Object,String> toStringFunc) {
    return joinMultipleFields(l, fields, "-", "/", " ", 0, l.size(), toStringFunc);
  }

  public static String joinMultipleFields(List<? extends CoreMap> l, final Class[] fields, final String defaultFieldValue,
                                          final String fieldGlue, String glue, int start, int end) {
    return joinMultipleFields(l, fields, defaultFieldValue, fieldGlue, glue, start, end, DEFAULT_TOSTRING);
  }

  public static String joinMultipleFields(List<? extends CoreMap> l, final Class[] fields) {
    return joinMultipleFields(l, fields, "-", "/", " ", 0, l.size());
  }

  /**
   * Joins all the tokens together (more or less) according to their original whitespace.
   * It assumes all whitespace was " ".
   *
   * @param tokens list of tokens which implement {@link HasOffset} and {@link HasWord}
   * @return a string of the tokens with the appropriate amount of spacing
   */
  public static String joinWithOriginalWhiteSpace(List<CoreLabel> tokens) {
    if (tokens.isEmpty()) {
      return "";
    }

    CoreLabel lastToken = tokens.get(0);
    StringBuilder buffer = new StringBuilder(lastToken.word());

    for (int i = 1; i < tokens.size(); i++) {
      CoreLabel currentToken = tokens.get(i);
      int numSpaces = currentToken.beginPosition() - lastToken.endPosition();
      if (numSpaces < 0) {
        numSpaces = 0;
      }

      buffer.append(repeat(' ', numSpaces)).append(currentToken.word());
      lastToken = currentToken;
    }

    return buffer.toString();
  }

  /**
   * Joins each elem in the {@link Iterable} with the given glue.
   * For example, given a list of {@code Integers}, you can create
   * a comma-separated list by calling {@code join(numbers, ", ")}.
   *
   * @see StringUtils#join(Stream, String)
   */
  public static <X> String join(Iterable<X> l, String glue) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (X o : l) {
      if ( ! first) {
        sb.append(glue);
      } else {
        first = false;
      }
      sb.append(o);
    }
    return sb.toString();
  }

  /**
   * Joins each elem in the {@link Stream} with the given glue.
   * For example, given a list of {@code Integers}, you can create
   * a comma-separated list by calling {@code join(numbers, ", ")}.
   *
   * @see StringUtils#join(Iterable, String)
   */
  public static <X> String join(Stream<X> l, String glue) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    Iterator<X> iter = l.iterator();
    while (iter.hasNext()) {
      if ( ! first) {
        sb.append(glue);
      } else {
        first = false;
      }
      sb.append(iter.next());
    }
    return sb.toString();
  }

  /**
   * Joins each elem in the array with the given glue. For example, given a
   * list of ints, you can create a comma-separated list by calling
   * {@code join(numbers, ", ")}.
   */
  public static String join(Object[] elements, String glue) {
    return (join(Arrays.asList(elements), glue));
  }

  /**
   * Joins an array of elements in a given span.
   * @param elements The elements to join.
   * @param start The start index to join from.
   * @param end The end (non-inclusive) to join until.
   * @param glue The glue to hold together the elements.
   * @return The string form of the sub-array, joined on the given glue.
   */
  public static String join(Object[] elements, int start, int end, String glue) {
    StringBuilder b = new StringBuilder(127);
    boolean isFirst = true;
    for (int i = start; i < end; ++i) {
      if (isFirst) {
        b.append(elements[i].toString());
        isFirst = false;
      } else {
        b.append(glue).append(elements[i].toString());
      }
    }
    return b.toString();
  }

  /**
   * Joins each element in the given array with the given glue. For example,
   * given an array of Integers, you can create a comma-separated list by calling
   * {@code join(numbers, ", ")}.
   */
  public static String join(String[] items, String glue) {
    return join(Arrays.asList(items), glue);
  }


  /**
   * Joins elems with a space.
   */
  public static String join(Iterable<?> l) {
    return join(l, " ");
  }

  /**
   * Joins elements with a space.
   */
  public static String join(Object[] elements) {
    return (join(elements, " "));
  }


  /**
   * Splits on whitespace (\\s+).
   * @param s String to split
   * @return List<String> of split strings
   */
  public static List<String> split(String s) {
    return split(s, "\\s+");
  }

  /**
   * Splits the given string using the given regex as delimiters.
   * This method is the same as the String.split() method (except it throws
   * the results in a List),
   * and is included just to give a call that is parallel to the other
   * static regex methods in this class.
   *
   * @param str   String to split up
   * @param regex String to compile as the regular expression
   * @return List of Strings resulting from splitting on the regex
   */
  public static List<String> split(String str, String regex) {
    return (Arrays.asList(str.split(regex)));
  }

  /**
   * Split a string on a given single character.
   * This method is often faster than the regular split() method.
   * @param input The input to split.
   * @param delimiter The character to split on.
   * @return An array of Strings corresponding to the original input split on the delimiter character.
   */
  public static String[] splitOnChar(String input, char delimiter) {
    // State
    String[] out = new String[input.length() + 1];
    int nextIndex = 0;
    int lastDelimiterIndex = -1;
    char[] chars = input.toCharArray();
    // Split
    for ( int i = 0; i <= chars.length; ++i ) {
      if (i >= chars.length || chars[i] == delimiter) {
        char[] tokenChars = new char[i - (lastDelimiterIndex + 1)];
        System.arraycopy(chars, lastDelimiterIndex + 1, tokenChars, 0, tokenChars.length);
        out[nextIndex] = new String(tokenChars);
        nextIndex += 1;
        lastDelimiterIndex = i;
      }
    }
    // Clean Result
    String[] trimmedOut = new String[nextIndex];
    System.arraycopy(out, 0, trimmedOut, 0, trimmedOut.length);
    return trimmedOut;
  }

  /**
   * Splits a string into whitespace tokenized fields based on a delimiter and then whitespace.
   * For example, "aa bb | bb cc | ccc ddd" would be split into "[aa,bb],[bb,cc],[ccc,ddd]" based on
   * the delimiter "|". This method uses the old StringTokenizer class, which is up to
   * 3x faster than the regex-based "split()" methods.
   *
   * @param delimiter String to split on
   * @return List of lists of strings.
   */
  public static List<List<String>> splitFieldsFast(String str, String delimiter) {
    List<List<String>> fields = Generics.newArrayList();
    StringTokenizer tokenizer = new StringTokenizer(str.trim());
    List<String> currentField = Generics.newArrayList();
    while(tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (token.equals(delimiter)) {
        fields.add(currentField);
        currentField = Generics.newArrayList();
      } else {
        currentField.add(token.trim());
      }
    }
    if (currentField.size() > 0) {
      fields.add(currentField);
    }
    return fields;
  }


  /**
   * Split on a given character, filling out the fields in the output array.
   * This is suitable for, e.g., splitting a TSV file of known column count.
   * @param out The output array to fill
   * @param input The input to split
   * @param delimiter The delimiter to split on.
   */
  public static void splitOnChar(String[] out, String input, char delimiter) {
    int lastSplit = 0;
    int outI = 0;
    char[] chars = input.toCharArray();
    for (int i = 0; i < chars.length; ++i) {
      if (chars[i] == delimiter) {
        out[outI] = new String(chars, lastSplit, i - lastSplit);
        outI += 1;
        lastSplit = i + 1;
      }
    }
    if (outI < out.length) {
      out[outI] = input.substring(lastSplit);
    }
  }

  /** Split a string into tokens.  Because there is a tokenRegex as well as a
   *  separatorRegex (unlike for the conventional split), you can do things
   *  like correctly split quoted strings or parenthesized arguments.
   *  However, it doesn't do the unquoting of quoted Strings for you.
   *  An empty String argument is returned at the beginning, if valueRegex
   *  accepts the empty String and str begins with separatorRegex.
   *  But str can end with either valueRegex or separatorRegex and this does
   *  not generate an empty String at the end (indeed, valueRegex need not
   *  even accept the empty String in this case.  However, if it does accept
   *  the empty String and there are multiple trailing separators, then
   *  empty values will be returned.
   *
   *  @param str The String to split
   *  @param valueRegex Must match a token. You may wish to let it match the empty String
   *  @param separatorRegex Must match a separator
   *  @return The List of tokens
   *  @throws IllegalArgumentException if str cannot be tokenized by the two regex
   */
  public static List<String> valueSplit(String str, String valueRegex, String separatorRegex) {
    Pattern vPat = Pattern.compile(valueRegex);
    Pattern sPat = Pattern.compile(separatorRegex);
    List<String> ret = new ArrayList<>();
    while (str.length() > 0) {
      Matcher vm = vPat.matcher(str);
      if (vm.lookingAt()) {
        ret.add(vm.group());
        str = str.substring(vm.end());
        // String got = vm.group();
        // log.info("vmatched " + got + "; now str is " + str);
      } else {
        throw new IllegalArgumentException("valueSplit: " + valueRegex + " doesn't match " + str);
      }
      if (str.length() > 0) {
        Matcher sm = sPat.matcher(str);
        if (sm.lookingAt()) {
          str = str.substring(sm.end());
          // String got = sm.group();
          // log.info("smatched " + got + "; now str is " + str);
        } else {
          throw new IllegalArgumentException("valueSplit: " + separatorRegex + " doesn't match " + str);
        }
      }
    } // end while
    return ret;
  }


  /**
   * Return a String of length a minimum of totalChars characters by
   * padding the input String str at the right end with spaces.
   * If str is already longer
   * than totalChars, it is returned unchanged.
   */
  public static String pad(String str, int totalChars) {
    return pad(str, totalChars, ' ');
  }

  /**
   * Return a String of length a minimum of totalChars characters by
   * padding the input String str at the right end with spaces.
   * If str is already longer
   * than totalChars, it is returned unchanged.
   */
  public static String pad(String str, int totalChars, char pad) {
    if (str == null) {
      str = "null";
    }
    int slen = str.length();
    StringBuilder sb = new StringBuilder(str);
    for (int i = 0; i < totalChars - slen; i++) {
      sb.append(pad);
    }
    return sb.toString();
  }

  /**
   * Pads the toString value of the given Object.
   */
  public static String pad(Object obj, int totalChars) {
    return pad(obj.toString(), totalChars);
  }


  /**
   * Pad or trim so as to produce a string of exactly a certain length.
   *
   * @param str The String to be padded or truncated
   * @param num The desired length
   */
  public static String padOrTrim(String str, int num) {
    if (str == null) {
      str = "null";
    }
    int leng = str.length();
    if (leng < num) {
      StringBuilder sb = new StringBuilder(str);
      for (int i = 0; i < num - leng; i++) {
        sb.append(' ');
      }
      return sb.toString();
    } else if (leng > num) {
      return str.substring(0, num);
    } else {
      return str;
    }
  }

  /**
   * Pad or trim so as to produce a string of exactly a certain length.
   *
   * @param str The String to be padded or truncated
   * @param num The desired length
   */
  public static String padLeftOrTrim(String str, int num) {
    if (str == null) {
      str = "null";
    }
    int leng = str.length();
    if (leng < num) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < num - leng; i++) {
        sb.append(' ');
      }
      sb.append(str);
      return sb.toString();
    } else if (leng > num) {
      return str.substring(str.length() - num);
    } else {
      return str;
    }
  }

  /**
   * Pad or trim the toString value of the given Object.
   */
  public static String padOrTrim(Object obj, int totalChars) {
    return padOrTrim(obj.toString(), totalChars);
  }


  /**
   * Pads the given String to the left with the given character ch to ensure that
   * it's at least totalChars long.
   */
  public static String padLeft(String str, int totalChars, char ch) {
    if (str == null) {
      str = "null";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0, num = totalChars - str.length(); i < num; i++) {
      sb.append(ch);
    }
    sb.append(str);
    return sb.toString();
  }


  /**
   * Pads the given String to the left with spaces to ensure that it's
   * at least totalChars long.
   */
  public static String padLeft(String str, int totalChars) {
    return padLeft(str, totalChars, ' ');
  }


  public static String padLeft(Object obj, int totalChars) {
    return padLeft(obj.toString(), totalChars);
  }

  public static String padLeft(int i, int totalChars) {
    return padLeft(Integer.valueOf(i), totalChars);
  }

  public static String padLeft(double d, int totalChars) {
    return padLeft(new Double(d), totalChars);
  }

  /**
   * Returns s if it's at most maxWidth chars, otherwise chops right side to fit.
   */
  public static String trim(String s, int maxWidth) {
    if (s.length() <= maxWidth) {
      return (s);
    }
    return s.substring(0, maxWidth);
  }

  public static String trim(Object obj, int maxWidth) {
    return trim(obj.toString(), maxWidth);
  }

  public static String trimWithEllipsis(String s, int width) {
    if (s.length() > width) s = s.substring(0, width - 3) + "...";
    return s;
  }

  public static String trimWithEllipsis(Object o, int width) {
    return trimWithEllipsis(o.toString(), width);
  }


  public static String repeat(String s, int times) {
    if (times == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(times * s.length());
    for (int i = 0; i < times; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  public static String repeat(char ch, int times) {
    if (times == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(times);
    for (int i = 0; i < times; i++) {
      sb.append(ch);
    }
    return sb.toString();
  }

  /**
   * Returns a "clean" version of the given filename in which spaces have
   * been converted to dashes and all non-alphanumeric chars are underscores.
   */
  public static String fileNameClean(String s) {
    char[] chars = s.toCharArray();
    StringBuilder sb = new StringBuilder();
    for (char c : chars) {
      if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (c == '_')) {
        sb.append(c);
      } else {
        if (c == ' ' || c == '-') {
          sb.append('_');
        } else {
          sb.append('x').append((int) c).append('x');
        }
      }
    }
    return sb.toString();
  }

  /**
   * Returns the index of the <i>n</i>th occurrence of ch in s, or -1
   * if there are less than n occurrences of ch.
   */
  public static int nthIndex(String s, char ch, int n) {
    int index = 0;
    for (int i = 0; i < n; i++) {
      // if we're already at the end of the string,
      // and we need to find another ch, return -1
      if (index == s.length() - 1) {
        return -1;
      }
      index = s.indexOf(ch, index + 1);
      if (index == -1) {
        return (-1);
      }
    }
    return index;
  }


  /**
   * This returns a string from decimal digit smallestDigit to decimal digit
   * biggest digit. Smallest digit is labeled 1, and the limits are
   * inclusive.
   */
  public static String truncate(int n, int smallestDigit, int biggestDigit) {
    int numDigits = biggestDigit - smallestDigit + 1;
    char[] result = new char[numDigits];
    for (int j = 1; j < smallestDigit; j++) {
      n = n / 10;
    }
    for (int j = numDigits - 1; j >= 0; j--) {
      result[j] = Character.forDigit(n % 10, 10);
      n = n / 10;
    }
    return new String(result);
  }

  /**
   * Parses command line arguments into a Map. Arguments of the form
   * <p/>
   * {@code -flag1 arg1a arg1b ... arg1m -flag2 -flag3 arg3a ... arg3n}
   * <p/>
   * will be parsed so that the flag is a key in the Map (including
   * the hyphen) and its value will be a {@link String}[] containing
   * the optional arguments (if present).  The non-flag values not
   * captured as flag arguments are collected into a String[] array
   * and returned as the value of {@code null} in the Map.  In
   * this invocation, flags cannot take arguments, so all the {@link
   * String} array values other than the value for {@code null}
   * will be zero-length.
   *
   * @param args A command-line arguments array
   * @return a {@link Map} of flag names to flag argument {@link
   *         String} arrays.
   */
  public static Map<String, String[]> argsToMap(String[] args) {
    return argsToMap(args, Collections.emptyMap());
  }

  /**
   * Parses command line arguments into a Map. Arguments of the form
   * <p/>
   * {@code -flag1 arg1a arg1b ... arg1m -flag2 -flag3 arg3a ... arg3n}
   * <p/>
   * will be parsed so that the flag is a key in the Map (including
   * the hyphen) and its value will be a {@link String}[] containing
   * the optional arguments (if present).  The non-flag values not
   * captured as flag arguments are collected into a String[] array
   * and returned as the value of {@code null} in the Map.  In
   * this invocation, the maximum number of arguments for each flag
   * can be specified as an {@link Integer} value of the appropriate
   * flag key in the {@code flagsToNumArgs} {@link Map}
   * argument. (By default, flags cannot take arguments.)
   * <p/>
   * Example of usage:
   * <p/>
   * <code>
   * Map flagsToNumArgs = new HashMap();
   * flagsToNumArgs.put("-x",new Integer(2));
   * flagsToNumArgs.put("-d",new Integer(1));
   * Map result = argsToMap(args,flagsToNumArgs);
   * </code>
   * <p/>
   * If a given flag appears more than once, the extra args are appended to
   * the String[] value for that flag.
   *
   * @param args           the argument array to be parsed
   * @param flagsToNumArgs a {@link Map} of flag names to {@link Integer}
   *                       values specifying the number of arguments
   *                       for that flag (default min 0, max 1).
   * @return a {@link Map} of flag names to flag argument {@link String}
   */
  public static Map<String, String[]> argsToMap(String[] args, Map<String, Integer> flagsToNumArgs) {
    Map<String, String[]> result = Generics.newHashMap();
    List<String> remainingArgs = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      String key = args[i];
      if (key.charAt(0) == '-') { // found a flag
        Integer numFlagArgs = flagsToNumArgs.get(key);
        int max = numFlagArgs == null ? 1 : numFlagArgs.intValue();
        int min = numFlagArgs == null ? 0 : numFlagArgs.intValue();
        List<String> flagArgs = new ArrayList<>();
        for (int j = 0; j < max && i + 1 < args.length && (j < min || args[i + 1].length() == 0 || args[i + 1].charAt(0) != '-'); i++, j++) {
          flagArgs.add(args[i + 1]);
        }
        if (result.containsKey(key)) { // append the second specification into the args.
          String[] newFlagArg = new String[result.get(key).length + flagsToNumArgs.get(key)];
          int oldNumArgs = result.get(key).length;
          System.arraycopy(result.get(key), 0, newFlagArg, 0, oldNumArgs);
          for (int j = 0; j < flagArgs.size(); j++) {
            newFlagArg[j + oldNumArgs] = flagArgs.get(j);
          }
          result.put(key, newFlagArg);
        } else {
          result.put(key, flagArgs.toArray(new String[flagArgs.size()]));
        }
      } else {
        remainingArgs.add(args[i]);
      }
    }
    result.put(null, remainingArgs.toArray(new String[remainingArgs.size()]));
    return result;
  }

  /**
   * In this version each flag has zero or one argument. It has one argument
   * if there is a thing following a flag that does not begin with '-'.  See
   * {@link #argsToProperties(String[], Map)} for full documentation.
   *
   * @param args Command line arguments
   * @return A Properties object representing the arguments.
   */
  public static Properties argsToProperties(String... args) {
    return argsToProperties(args, Collections.emptyMap());
  }

  /**
   * Analogous to {@link #argsToMap}.  However, there are several key differences between this method and {@link #argsToMap}:
   * <ul>
   * <li> Hyphens are stripped from flag names </li>
   * <li> Since Properties objects are String to String mappings, the default number of arguments to a flag is
   * assumed to be 1 and not 0. </li>
   * <li> Furthermore, the list of arguments not bound to a flag is mapped to the "" property, not null </li>
   * <li> The special flags "-prop", "-props", "-properties", "-args", or "-arguments" will load the property file
   *      specified by its argument. </li>
   * <li> The value for flags without arguments is set to "true" </li>
   * <li> If a flag has multiple arguments, the value of the property is all
   * of the arguments joined together with a space (" ") character between them.</li>
   * <li> The value strings are trimmed so trailing spaces do not stop you from loading a file.</li>
   * </ul>
   * Properties are read from left to right, and later properties will override earlier ones with the same name.
   * Properties loaded from a Properties file with the special args are defaults that can be overriden by command line
   * flags (or earlier Properties files if there is nested usage of the special args.
   *
   * @param args Command line arguments
   * @param flagsToNumArgs Map of how many arguments flags should have. The keys are without the minus signs.
   * @return A Properties object representing the arguments.
   */
  public static Properties argsToProperties(String[] args, Map<String,Integer> flagsToNumArgs) {
    Properties result = new Properties();
    List<String> remainingArgs = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      String key = args[i];
      if ( ! key.isEmpty() && key.charAt(0) == '-') { // found a flag
        if (key.length() > 1 && key.charAt(1) == '-') {
          key = key.substring(2); // strip off 2 hyphens
        } else {
          key = key.substring(1); // strip off the hyphen
        }

        Integer maxFlagArgs = flagsToNumArgs.get(key);
        int max = maxFlagArgs == null ? 1 : maxFlagArgs;
        int min = maxFlagArgs == null ? 0 : maxFlagArgs;
        if (maxFlagArgs != null && maxFlagArgs == 0 && i < args.length - 1 &&
            ("true".equalsIgnoreCase(args[i + 1]) || "false".equalsIgnoreCase(args[i + 1]))) {
          max = 1;  // case: we're reading a boolean flag. TODO(gabor) there's gotta be a better way...
        }
        List<String> flagArgs = new ArrayList<>();
        // cdm oct 2007: add length check to allow for empty string argument!
        for (int j = 0; j < max && i + 1 < args.length && (j < min || args[i + 1].isEmpty() || args[i + 1].charAt(0) != '-'); i++, j++) {
          flagArgs.add(args[i + 1]);
        }
        String value;
        if (flagArgs.isEmpty()) {
          value = "true";
        } else {
          value = join(flagArgs, " ");
        }
        if (key.equalsIgnoreCase(PROP) || key.equalsIgnoreCase(PROPS) || key.equalsIgnoreCase(PROPERTIES) || key.equalsIgnoreCase(ARGUMENTS) || key.equalsIgnoreCase(ARGS)) {
          result.setProperty(PROPERTIES, value);
        } else {
          result.setProperty(key, value);
        }
      } else {
        remainingArgs.add(args[i]);
      }
    }
    if ( ! remainingArgs.isEmpty()) {
      result.setProperty("", join(remainingArgs, " "));
    }

    /* Processing in reverse order, add properties that aren't present only. Thus, later ones override earlier ones. */
    while (result.containsKey(PROPERTIES)) {
      String file = result.getProperty(PROPERTIES);
      result.remove(PROPERTIES);
      Properties toAdd = new Properties();
      BufferedReader reader = null;
      try {
        reader = IOUtils.readerFromString(file);
        toAdd.load(reader);
        // trim all values
        for (String propKey : toAdd.stringPropertyNames()) {
          String newVal = toAdd.getProperty(propKey);
          toAdd.setProperty(propKey, newVal.trim());
        }
      } catch (IOException e) {
        String msg = "argsToProperties could not read properties file: " + file;
        throw new RuntimeIOException(msg, e);
      } finally {
        IOUtils.closeIgnoringExceptions(reader);
      }

      for (String key : toAdd.stringPropertyNames()) {
        String val = toAdd.getProperty(key);
        if ( ! result.containsKey(key)) {
          result.setProperty(key, val);
        }
      }
    }

    return result;
  }


  /**
   * This method reads in properties listed in a file in the format prop=value, one property per line.
   * Although {@code Properties.load(InputStream)} exists, I implemented this method to trim the lines,
   * something not implemented in the {@code load()} method.
   *
   * @param filename A properties file to read
   * @return The corresponding Properties object
   */
  public static Properties propFileToProperties(String filename) {
    Properties result = new Properties();
    try {
      InputStream is = new BufferedInputStream(new FileInputStream(filename));
      result.load(is);
      // trim all values
      for (String propKey : result.stringPropertyNames()){
        String newVal = result.getProperty(propKey);
        result.setProperty(propKey,newVal.trim());
      }
      is.close();
      return result;
    } catch (IOException e) {
      throw new RuntimeIOException("propFileToProperties could not read properties file: " + filename, e);
    }
  }

  /**
   * This method converts a comma-separated String (with whitespace
   * optionally allowed after the comma) representing properties
   * to a Properties object.  Each property is "property=value".  The value
   * for properties without an explicitly given value is set to "true". This can be used for a 2nd level
   * of properties, for example, when you have a commandline argument like "-outputOptions style=xml,tags".
   */
  public static Properties stringToProperties(String str) {
    Properties result = new Properties();
    return stringToProperties(str, result);
  }

  /**
   * This method updates a Properties object based on
   * a comma-separated String (with whitespace
   * optionally allowed after the comma) representing properties
   * to a Properties object.  Each property is "property=value".  The value
   * for properties without an explicitly given value is set to "true".
   */
  public static Properties stringToProperties(String str, Properties props) {
    String[] propsStr = str.trim().split(",\\s*");
    for (String term : propsStr) {
      int divLoc = term.indexOf('=');
      String key;
      String value;
      if (divLoc >= 0) {
        key = term.substring(0, divLoc).trim();
        value = term.substring(divLoc + 1).trim();
      } else {
        key = term.trim();
        value = "true";
      }
      props.setProperty(key, value);
    }
    return props;
  }

  /**
   * If any of the given list of properties are not found, returns the
   * name of that property.  Otherwise, returns null.
   */
  public static String checkRequiredProperties(Properties props,
                                               String ... requiredProps) {
    for (String required : requiredProps) {
      if (props.getProperty(required) == null) {
        return required;
      }
    }
    return null;
  }


  /**
   * Prints to a file.  If the file already exists, appends if
   * {@code append=true}, and overwrites if {@code append=false}.
   */
  public static void printToFile(File file, String message, boolean append,
                                 boolean printLn, String encoding) {
    PrintWriter pw = null;
    try {
      Writer fw;
      if (encoding != null) {
        fw = new OutputStreamWriter(new FileOutputStream(file, append),
                                         encoding);
      } else {
        fw = new FileWriter(file, append);
      }
      pw = new PrintWriter(fw);
      if (printLn) {
        pw.println(message);
      } else {
        pw.print(message);
      }
    } catch (Exception e) {
      log.info("Exception: in printToFile " + file.getAbsolutePath());
      e.printStackTrace();
    } finally {
      if (pw != null) {
        pw.flush();
        pw.close();
      }
    }
  }


  /**
   * Prints to a file.  If the file already exists, appends if
   * {@code append=true}, and overwrites if {@code append=false}.
   */
  public static void printToFileLn(File file, String message, boolean append) {
    PrintWriter pw = null;
    try {
      Writer fw = new FileWriter(file, append);
      pw = new PrintWriter(fw);
      pw.println(message);
    } catch (Exception e) {
      log.info("Exception: in printToFileLn " + file.getAbsolutePath() + ' ' + message);
      e.printStackTrace();
    } finally {
      if (pw != null) {
        pw.flush();
        pw.close();
      }
    }
  }

  /**
   * Prints to a file.  If the file already exists, appends if
   * {@code append=true}, and overwrites if {@code append=false}.
   */
  public static void printToFile(File file, String message, boolean append) {
    PrintWriter pw = null;
    try {
      Writer fw = new FileWriter(file, append);
      pw = new PrintWriter(fw);
      pw.print(message);
    } catch (Exception e) {
      throw new RuntimeIOException("Exception in printToFile " + file.getAbsolutePath(), e);
    } finally {
      IOUtils.closeIgnoringExceptions(pw);
    }
  }


  /**
   * Prints to a file.  If the file does not exist, rewrites the file;
   * does not append.
   */
  public static void printToFile(File file, String message) {
    printToFile(file, message, false);
  }

  /**
   * Prints to a file.  If the file already exists, appends if
   * {@code append=true}, and overwrites if {@code append=false}.
   */
  public static void printToFile(String filename, String message, boolean append) {
    printToFile(new File(filename), message, append);
  }

  /**
   * Prints to a file.  If the file already exists, appends if
   * {@code append=true}, and overwrites if {@code append=false}.
   */
  public static void printToFileLn(String filename, String message, boolean append) {
    printToFileLn(new File(filename), message, append);
  }


  /**
   * Prints to a file.  If the file does not exist, rewrites the file;
   * does not append.
   */
  public static void printToFile(String filename, String message) {
    printToFile(new File(filename), message, false);
  }

  /**
   * A simpler form of command line argument parsing.
   * Dan thinks this is highly superior to the overly complexified code that
   * comes before it.
   * Parses command line arguments into a Map. Arguments of the form
   * -flag1 arg1 -flag2 -flag3 arg3
   * will be parsed so that the flag is a key in the Map (including the hyphen)
   * and the
   * optional argument will be its value (if present).
   *
   * @return A Map from keys to possible values (String or null)
   */
  @SuppressWarnings("unchecked")
  public static Map<String, String> parseCommandLineArguments(String[] args) {
    return (Map)parseCommandLineArguments(args, false);
  }

  /**
   * A simpler form of command line argument parsing.
   * Dan thinks this is highly superior to the overly complexified code that
   * comes before it.
   * Parses command line arguments into a Map. Arguments of the form
   * -flag1 arg1 -flag2 -flag3 arg3
   * will be parsed so that the flag is a key in the Map (including the hyphen)
   * and the
   * optional argument will be its value (if present).
   * In this version, if the argument is numeric, it will be a Double value
   * in the map, not a String.
   *
   * @return A Map from keys to possible values (String or null)
   */
  public static Map<String, Object> parseCommandLineArguments(String[] args, boolean parseNumbers) {
    Map<String, Object> result = Generics.newHashMap();
    for (int i = 0; i < args.length; i++) {
      String key = args[i];
      if (key.charAt(0) == '-') {
        if (i + 1 < args.length) {
          String value = args[i + 1];
          if (value.charAt(0) != '-') {
            if (parseNumbers) {
              Object numericValue = value;
              try {
                numericValue = Double.parseDouble(value);
              } catch (NumberFormatException e2) {
                // ignore
              }
              result.put(key, numericValue);
            } else {
              result.put(key, value);
            }
            i++;
          } else {
            result.put(key, null);
          }
        } else {
          result.put(key, null);
        }
      }
    }
    return result;
  }

  public static String stripNonAlphaNumerics(String orig) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < orig.length(); i++) {
      char c = orig.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  public static String stripSGML(String orig) {
      Pattern sgmlPattern = Pattern.compile("<.*?>", Pattern.DOTALL);
      Matcher sgmlMatcher = sgmlPattern.matcher(orig);
      return sgmlMatcher.replaceAll("");
  }

  public static void printStringOneCharPerLine(String s) {
    for (int i = 0; i < s.length(); i++) {
      int c = s.charAt(i);
      System.out.println(c + " \'" + (char) c + "\' ");
    }
  }

  public static String escapeString(String s, char[] charsToEscape, char escapeChar) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == escapeChar) {
        result.append(escapeChar);
      } else {
        for (char charToEscape : charsToEscape) {
          if (c == charToEscape) {
            result.append(escapeChar);
            break;
          }
        }
      }
      result.append(c);
    }
    return result.toString();
  }

  /**
   * This function splits the String s into multiple Strings using the
   * splitChar.  However, it provides a quoting facility: it is possible to
   * quote strings with the quoteChar.
   * If the quoteChar occurs within the quotedExpression, it must be prefaced
   * by the escapeChar.
   * This routine can be useful for processing a line of a CSV file.
   *
   * @param s         The String to split into fields. Cannot be null.
   * @param splitChar The character to split on
   * @param quoteChar The character to quote items with
   * @param escapeChar The character to escape the quoteChar with
   * @return An array of Strings that s is split into
   */
  public static String[] splitOnCharWithQuoting(String s, char splitChar, char quoteChar, char escapeChar) {
    List<String> result = new ArrayList<>();
    int i = 0;
    int length = s.length();
    StringBuilder b = new StringBuilder();
    while (i < length) {
      char curr = s.charAt(i);
      if (curr == splitChar) {
        // add last buffer
        // cdm 2014: Do this even if the field is empty!
        // if (b.length() > 0) {
        result.add(b.toString());
        b = new StringBuilder();
        // }
        i++;
      } else if (curr == quoteChar) {
        // find next instance of quoteChar
        i++;
        while (i < length) {
          curr = s.charAt(i);
          // mrsmith: changed this condition from
          // if (curr == escapeChar) {
          if ((curr == escapeChar) && (i+1 < length) && (s.charAt(i+1) == quoteChar)) {
            b.append(s.charAt(i + 1));
            i += 2;
          } else if (curr == quoteChar) {
            i++;
            break; // break this loop
          } else {
            b.append(s.charAt(i));
            i++;
          }
        }
      } else {
        b.append(curr);
        i++;
      }
    }
    // RFC 4180 disallows final comma. At any rate, don't produce a field after it unless non-empty
    if (b.length() > 0) {
      result.add(b.toString());
    }
    return result.toArray(new String[result.size()]);
  }

  /**
   * Computes the longest common substring of s and t.
   * The longest common substring of a and b is the longest run of
   * characters that appear in order inside both a and b. Both a and b
   * may have other extraneous characters along the way. This is like
   * edit distance but with no substitution and a higher number means
   * more similar. For example, the LCS of "abcD" and "aXbc" is 3 (abc).
   */
  public static int longestCommonSubstring(String s, String t) {
    int[][] d; // matrix
    int n; // length of s
    int m; // length of t
    int i; // iterates through s
    int j; // iterates through t
    // int cost; // cost
    // Step 1
    n = s.length();
    m = t.length();
    if (n == 0) {
      return 0;
    }
    if (m == 0) {
      return 0;
    }
    d = new int[n + 1][m + 1];
    // Step 2
    for (i = 0; i <= n; i++) {
      d[i][0] = 0;
    }
    for (j = 0; j <= m; j++) {
      d[0][j] = 0;
    }
    // Step 3
    for (i = 1; i <= n; i++) {
      char s_i = s.charAt(i - 1); // ith character of s
// Step 4
      for (j = 1; j <= m; j++) {
        char t_j = t.charAt(j - 1); // jth character of t
// Step 5
        // js: if the chars match, you can get an extra point
        // otherwise you have to skip an insertion or deletion (no subs)
        if (s_i == t_j) {
          d[i][j] = SloppyMath.max(d[i - 1][j], d[i][j - 1], d[i - 1][j - 1] + 1);
        } else {
          d[i][j] = Math.max(d[i - 1][j], d[i][j - 1]);
        }
      }
    }
    /* ----
      // num chars needed to display longest num
      int numChars = (int) Math.ceil(Math.log(d[n][m]) / Math.log(10));
      for (i = 0; i < numChars + 3; i++) {
        log.info(' ');
      }
      for (j = 0; j < m; j++) {
        log.info(t.charAt(j) + " ");
      }
      log.info();
      for (i = 0; i <= n; i++) {
        log.info((i == 0 ? ' ' : s.charAt(i - 1)) + " ");
        for (j = 0; j <= m; j++) {
          log.info(d[i][j] + " ");
        }
        log.info();
      }
    ---- */
    // Step 7
    return d[n][m];
  }

  /**
   * Computes the longest common contiguous substring of s and t.
   * The LCCS is the longest run of characters that appear consecutively in
   * both s and t. For instance, the LCCS of "color" and "colour" is 4, because
   * of "colo".
   */
  public static int longestCommonContiguousSubstring(String s, String t) {
    if (s.isEmpty() || t.isEmpty()) {
      return 0;
    }
    int M = s.length();
    int N = t.length();
    int[][] d = new int[M + 1][N + 1];
    for (int j = 0; j <= N; j++) {
      d[0][j] = 0;
    }
    for (int i = 0; i <= M; i++) {
      d[i][0] = 0;
    }

    int max = 0;
    for (int i = 1; i <= M; i++) {
      for (int j = 1; j <= N; j++) {
        if (s.charAt(i - 1) == t.charAt(j - 1)) {
          d[i][j] = d[i - 1][j - 1] + 1;
        } else {
          d[i][j] = 0;
        }

        if (d[i][j] > max) {
          max = d[i][j];
        }
      }
    }
    // log.info("LCCS(" + s + "," + t + ") = " + max);
    return max;
  }

  /**
   * Computes the Levenshtein (edit) distance of the two given Strings.
   * This method doesn't allow transposition, so one character transposed between two strings has a cost of 2 (one insertion, one deletion).
   * The EditDistance class also implements the Levenshtein distance, but does allow transposition.
   */
  public static int editDistance(String s, String t) {
    // Step 1
    int n = s.length(); // length of s
    int m = t.length(); // length of t
    if (n == 0) {
      return m;
    }
    if (m == 0) {
      return n;
    }
    int[][] d = new int[n + 1][m + 1]; // matrix
    // Step 2
    for (int i = 0; i <= n; i++) {
      d[i][0] = i;
    }
    for (int j = 0; j <= m; j++) {
      d[0][j] = j;
    }
    // Step 3
    for (int i = 1; i <= n; i++) {
      char s_i = s.charAt(i - 1); // ith character of s
      // Step 4
      for (int j = 1; j <= m; j++) {
        char t_j = t.charAt(j - 1); // jth character of t
        // Step 5
        int cost; // cost
        if (s_i == t_j) {
          cost = 0;
        } else {
          cost = 1;
        }
        // Step 6
        d[i][j] = SloppyMath.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
      }
    }

    // Step 7
    return d[n][m];
  }


  /**
   * Computes the WordNet 2.0 POS tag corresponding to the PTB POS tag s.
   *
   * @param s a Penn TreeBank POS tag.
   */
  public static String pennPOSToWordnetPOS(String s) {
    if (s.matches("NN|NNP|NNS|NNPS")) {
      return "noun";
    }
    if (s.matches("VB|VBD|VBG|VBN|VBZ|VBP|MD")) {
      return "verb";
    }
    if (s.matches("JJ|JJR|JJS|CD")) {
      return "adjective";
    }
    if (s.matches("RB|RBR|RBS|RP|WRB")) {
      return "adverb";
    }
    return null;
  }

  /**
   * Returns a short class name for an object.
   * This is the class name stripped of any package name.
   *
   * @return The name of the class minus a package name, for example
   *         <code>ArrayList</code>
   */
  public static String getShortClassName(Object o) {
    if (o == null) {
      return "null";
    }
    String name = o.getClass().getName();
    int index = name.lastIndexOf('.');
    if (index >= 0) {
      name = name.substring(index + 1);
    }
    return name;
  }


  /**
   * Converts a tab delimited string into an object with given fields
   * Requires the object has setXxx functions for the specified fields
   *
   * @param objClass Class of object to be created
   * @param str string to convert
   * @param delimiterRegex delimiter regular expression
   * @param fieldNames fieldnames
   * @param <T> type to return
   * @return Object created from string
   */
  public static <T> T columnStringToObject(Class objClass, String str, String delimiterRegex, String[] fieldNames)
          throws InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException {
    Pattern delimiterPattern = Pattern.compile(delimiterRegex);
    return StringUtils.columnStringToObject(objClass, str, delimiterPattern, fieldNames);
  }

  /**
   * Converts a tab delimited string into an object with given fields
   * Requires the object has public access for the specified fields
   *
   * @param objClass Class of object to be created
   * @param str string to convert
   * @param delimiterPattern delimiter
   * @param fieldNames fieldnames
   * @param <T> type to return
   * @return Object created from string
   */
  public static <T> T columnStringToObject(Class<?> objClass, String str, Pattern delimiterPattern, String[] fieldNames)
          throws InstantiationException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException, InvocationTargetException {
    String[] fields = delimiterPattern.split(str);
    T item = ErasureUtils.uncheckedCast(objClass.newInstance());
    for (int i = 0; i < fields.length; i++) {
      try {
        Field field = objClass.getDeclaredField(fieldNames[i]);
        field.set(item, fields[i]);
      } catch (IllegalAccessException ex) {
        Method method = objClass.getDeclaredMethod("set" + StringUtils.capitalize(fieldNames[i]), String.class);
        method.invoke(item, fields[i]);
      }
    }
    return item;
  }

  /**
   * Converts an object into a tab delimited string with given fields
   * Requires the object has public access for the specified fields
   *
   * @param object Object to convert
   * @param delimiter delimiter
   * @param fieldNames fieldnames
   * @return String representing object
   */
  public static String objectToColumnString(Object object, String delimiter, String[] fieldNames)
          throws IllegalAccessException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException
  {
    StringBuilder sb = new StringBuilder();
    for (String fieldName : fieldNames) {
      if (sb.length() > 0) {
        sb.append(delimiter);
      }
      try {
        Field field = object.getClass().getDeclaredField(fieldName);
        sb.append(field.get(object));
      } catch (IllegalAccessException ex) {
        Method method = object.getClass().getDeclaredMethod("get" + StringUtils.capitalize(fieldName));
        sb.append(method.invoke(object));
      }
    }
    return sb.toString();
  }

  /**
   * Uppercases the first character of a string.
   *
   * @param s a string to capitalize
   * @return a capitalized version of the string
   */
  public static String capitalize(String s) {
    if (Character.isLowerCase(s.charAt(0))) {
      return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    } else {
      return s;
    }
  }

  /**
   * Check if a string begins with an uppercase.
   *
   * @param s a string
   * @return true if the string is capitalized
   *         false otherwise
   */
  public static boolean isCapitalized(String s) {
    return (Character.isUpperCase(s.charAt(0)));
  }

  public static String searchAndReplace(String text, String from, String to) {
    from = escapeString(from, new char[]{'.', '[', ']', '\\'}, '\\'); // special chars in regex
    Pattern p = Pattern.compile(from);
    Matcher m = p.matcher(text);
    return m.replaceAll(to);
  }

  /**
   * Returns an HTML table containing the matrix of Strings passed in.
   * The first dimension of the matrix should represent the rows, and the
   * second dimension the columns.
   */
  public static String makeHTMLTable(String[][] table, String[] rowLabels, String[] colLabels) {
    StringBuilder buff = new StringBuilder();
    buff.append("<table class=\"auto\" border=\"1\" cellspacing=\"0\">\n");
    // top row
    buff.append("<tr>\n");
    buff.append("<td></td>\n"); // the top left cell
    for (int j = 0; j < table[0].length; j++) { // assume table is a rectangular matrix
      buff.append("<td class=\"label\">").append(colLabels[j]).append("</td>\n");
    }
    buff.append("</tr>\n");
    // all other rows
    for (int i = 0; i < table.length; i++) {
      // one row
      buff.append("<tr>\n");
      buff.append("<td class=\"label\">").append(rowLabels[i]).append("</td>\n");
      for (int j = 0; j < table[i].length; j++) {
        buff.append("<td class=\"data\">");
        buff.append(((table[i][j] != null) ? table[i][j] : ""));
        buff.append("</td>\n");
      }
      buff.append("</tr>\n");
    }
    buff.append("</table>");
    return buff.toString();
  }

  /**
   * Returns a text table containing the matrix of objects passed in.
   * The first dimension of the matrix should represent the rows, and the
   * second dimension the columns. Each object is printed in a cell with toString().
   * The printing may be padded with spaces on the left and then on the right to
   * ensure that the String form is of length at least padLeft or padRight.
   * If tsv is true, a tab is put between columns.
   *
   * @return A String form of the table
   */
  public static String makeTextTable(Object[][] table, Object[] rowLabels, Object[] colLabels, int padLeft, int padRight, boolean tsv) {
    StringBuilder buff = new StringBuilder();
    if (colLabels != null) {
      // top row
      buff.append(makeAsciiTableCell("", padLeft, padRight, tsv)); // the top left cell
      for (int j = 0; j < table[0].length; j++) { // assume table is a rectangular matrix
        buff.append(makeAsciiTableCell(colLabels[j], padLeft, padRight, (j != table[0].length - 1) && tsv));
      }
      buff.append('\n');
    }
    // all other rows
    for (int i = 0; i < table.length; i++) {
      // one row
      if (rowLabels != null) {
        buff.append(makeAsciiTableCell(rowLabels[i], padLeft, padRight, tsv));
      }
      for (int j = 0; j < table[i].length; j++) {
        buff.append(makeAsciiTableCell(table[i][j], padLeft, padRight, (j != table[0].length - 1) && tsv));
      }
      buff.append('\n');
    }
    return buff.toString();
  }


  /** The cell String is the string representation of the object.
   *  If padLeft is greater than 0, it is padded. Ditto right
   *
   */
  private static String makeAsciiTableCell(Object obj, int padLeft, int padRight, boolean tsv) {
    String result = obj.toString();
    if (padLeft > 0) {
      result = padLeft(result, padLeft);
    }
    if (padRight > 0) {
      result = pad(result, padRight);
    }
    if (tsv) {
      result = result + '\t';
    }
    return result;
  }

  /**
   * Tests the string edit distance function.
   */
  public static void main(String[] args) {

    String[] s = {"there once was a man", "this one is a manic", "hey there", "there once was a mane", "once in a manger.", "where is one match?", "Jo3seph Smarr!", "Joseph R Smarr"};
    for (int i = 0; i < 8; i++) {
      for (int j = 0; j < 8; j++) {
        System.out.println("s1: " + s[i]);
        System.out.println("s2: " + s[j]);
        System.out.println("edit distance: " + editDistance(s[i], s[j]));
        System.out.println("LCS:           " + longestCommonSubstring(s[i], s[j]));
        System.out.println("LCCS:          " + longestCommonContiguousSubstring(s[i], s[j]));
        System.out.println();
      }
    }
  }

  public static String toAscii(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c > 127) {
        String result = "?";
        if (c >= 0x00c0 && c <= 0x00c5) {
          result = "A";
        } else if (c == 0x00c6) {
          result = "AE";
        } else if (c == 0x00c7) {
          result = "C";
        } else if (c >= 0x00c8 && c <= 0x00cb) {
          result = "E";
        } else if (c >= 0x00cc && c <= 0x00cf) {
          result = "F";
        } else if (c == 0x00d0) {
          result = "D";
        } else if (c == 0x00d1) {
          result = "N";
        } else if (c >= 0x00d2 && c <= 0x00d6) {
          result = "O";
        } else if (c == 0x00d7) {
          result = "x";
        } else if (c == 0x00d8) {
          result = "O";
        } else if (c >= 0x00d9 && c <= 0x00dc) {
          result = "U";
        } else if (c == 0x00dd) {
          result = "Y";
        } else if (c >= 0x00e0 && c <= 0x00e5) {
          result = "a";
        } else if (c == 0x00e6) {
          result = "ae";
        } else if (c == 0x00e7) {
          result = "c";
        } else if (c >= 0x00e8 && c <= 0x00eb) {
          result = "e";
        } else if (c >= 0x00ec && c <= 0x00ef) {
          result = "i";
        } else if (c == 0x00f1) {
          result = "n";
        } else if (c >= 0x00f2 && c <= 0x00f8) {
          result = "o";
        } else if (c >= 0x00f9 && c <= 0x00fc) {
          result = "u";
        } else if (c >= 0x00fd && c <= 0x00ff) {
          result = "y";
        } else if (c >= 0x2018 && c <= 0x2019) {
          result = "\'";
        } else if (c >= 0x201c && c <= 0x201e) {
          result = "\"";
        } else if (c >= 0x0213 && c <= 0x2014) {
          result = "-";
        } else if (c >= 0x00A2 && c <= 0x00A5) {
          result = "$";
        } else if (c == 0x2026) {
          result = ".";
        }
        b.append(result);
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }


  public static String toCSVString(String[] fields) {
    StringBuilder b = new StringBuilder();
    for (String fld : fields) {
      if (b.length() > 0) {
        b.append(',');
      }
      String field = escapeString(fld, new char[]{'\"'}, '\"'); // escape quotes with double quotes
      b.append('\"').append(field).append('\"');
    }
    return b.toString();
  }

  /**
   * Swap any occurrences of any characters in the from String in the input String with
   * the corresponding character from the to String.  As Perl tr, for example,
   * tr("chris", "irs", "mop").equals("chomp"), except it does not
   * support regular expression character ranges.
   * <p>
   * <i>Note:</i> This is now optimized to not allocate any objects if the
   * input is returned unchanged.
   */
  public static String tr(String input, String from, String to) {
    assert from.length() == to.length();
    StringBuilder sb = null;
    int len = input.length();
    for (int i = 0; i < len; i++) {
      int ind = from.indexOf(input.charAt(i));
      if (ind >= 0) {
        if (sb == null) {
          sb = new StringBuilder(input);
        }
        sb.setCharAt(i, to.charAt(ind));
      }
    }
    if (sb == null) {
      return input;
    } else {
      return sb.toString();
    }
  }

  /**
   * Returns the supplied string with any trailing '\n' or '\r\n' removed.
   */
  public static String chomp(String s) {
    if (s == null) {
      return null;
    }
    int l_1 = s.length() - 1;
    if (l_1 >= 0 && s.charAt(l_1) == '\n') {
      int l_2 = l_1 - 1;
      if (l_2 >= 0 && s.charAt(l_2) == '\r') {
        return s.substring(0, l_2);
      } else {
        return s.substring(0, l_1);
      }
    } else {
      return s;
    }
  }

  /**
   * Returns the result of calling toString() on the supplied Object, but with
   * any trailing '\n' or '\r\n' removed.
   */
  public static String chomp(Object o) {
    return chomp(o.toString());
  }


  /**
   * Strip directory from filename.  Like Unix 'basename'. <p/>
   *
   * Example: {@code getBaseName("/u/wcmac/foo.txt") ==> "foo.txt"}
   */
  public static String getBaseName(String fileName) {
    return getBaseName(fileName, "");
  }

  /**
   * Strip directory and suffix from filename.  Like Unix 'basename'.
   *
   * Example: {@code getBaseName("/u/wcmac/foo.txt", "") ==> "foo.txt"}<br/>
   * Example: {@code getBaseName("/u/wcmac/foo.txt", ".txt") ==> "foo"}<br/>
   * Example: {@code getBaseName("/u/wcmac/foo.txt", ".pdf") ==> "foo.txt"}<br/>
   */
  public static String getBaseName(String fileName, String suffix) {
    return getBaseName(fileName, suffix, "/");
  }

  /**
   * Strip directory and suffix from the given name.  Like Unix 'basename'.
   *
   * Example: {@code getBaseName("/tmp/foo/bar/foo", "", "/") ==> "foo"}<br/>
   * Example: {@code getBaseName("edu.stanford.nlp", "", "\\.") ==> "nlp"}<br/>
   */
  public static String getBaseName(String fileName, String suffix, String sep) {
    String[] elts = fileName.split(sep);
    if (elts.length == 0) return "";
    String lastElt = elts[elts.length - 1];
    if (lastElt.endsWith(suffix)) {
      lastElt = lastElt.substring(0, lastElt.length() - suffix.length());
    }
    return lastElt;
  }

  /**
   * Given a String the method uses Regex to check if the String only contains alphabet characters
   *
   * @param s a String to check using regex
   * @return true if the String is valid
   */
  public static boolean isAlpha(String s){
    Pattern p = Pattern.compile("^[\\p{Alpha}\\s]+$");
    Matcher m = p.matcher(s);
    return m.matches();
  }

  /**
   * Given a String the method uses Regex to check if the String only contains numeric characters
   *
   * @param s a String to check using regex
   * @return true if the String is valid
   */
  public static boolean isNumeric(String s){
    Pattern p = Pattern.compile("^[\\p{Digit}\\s\\.]+$");
    Matcher m = p.matcher(s);
    return m.matches();
  }

  /**
   * Given a String the method uses Regex to check if the String only contains alphanumeric characters
   *
   * @param s a String to check using regex
   * @return true if the String is valid
   */
  public static boolean isAlphanumeric(String s){
    Pattern p = Pattern.compile("^[\\p{Alnum}\\s\\.]+$");
    Matcher m = p.matcher(s);
    return m.matches();
  }

  /**
   * Given a String the method uses Regex to check if the String only contains punctuation characters
   *
   * @param s a String to check using regex
   * @return true if the String is valid
   */
  public static boolean isPunct(String s){
    Pattern p = Pattern.compile("^[\\p{Punct}]+$");
    Matcher m = p.matcher(s);
    return m.matches();
  }

  /**
   * Given a String the method uses Regex to check if the String looks like an acronym
   *
   * @param s a String to check using regex
   * @return true if the String is valid
   */
  public static boolean isAcronym(String s){
    Pattern p = Pattern.compile("^[\\p{Upper}]+$");
    Matcher m = p.matcher(s);
    return m.matches();
  }

  public static String getNotNullString(String s) {
    if (s == null)
      return "";
    else
      return s;
  }

  /** Returns whether a String is either null or empty.
   *  (Copies the Guava method for this.)
   *
   *  @param str The String to test
   *  @return Whether the String is either null or empty
   */
  public static boolean isNullOrEmpty(String str) {
    return str == null || str.equals("");
  }


  /**
   * Resolve variable. If it is the props file, then substitute that variable with
   * the value mentioned in the props file, otherwise look for the variable in the environment variables.
   * If the variable is not found then substitute it for empty string.
   */
  public static String resolveVars(String str, Map props) {
    if (str == null)
      return null;
    // ${VAR_NAME} or $VAR_NAME
    Pattern p = Pattern.compile("\\$\\{(\\w+)\\}");
    Matcher m = p.matcher(str);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String varName = null == m.group(1) ? m.group(2) : m.group(1);
      String vrValue;
      //either in the props file
      if (props.containsKey(varName)) {
        vrValue = ((String) props.get(varName));
      } else {
        //or as the environment variable
        vrValue = System.getenv(varName);
      }
      m.appendReplacement(sb, null == vrValue ? "" : vrValue);
    }
    m.appendTail(sb);
    return sb.toString();
  }


  /**
   * convert args to properties with variable names resolved. for each value
   * having a ${VAR} or $VAR, its value is first resolved using the variables
   * listed in the props file, and if not found then using the environment
   * variables. if the variable is not found then substitute it for empty string
   */
  public static Properties argsToPropertiesWithResolve(String[] args) {
    LinkedHashMap<String, String> result = new LinkedHashMap<>();
    Map<String, String> existingArgs = new LinkedHashMap<>();

    for (int i = 0; i < args.length; i++) {
      String key = args[i];
      if (key.length() > 0 && key.charAt(0) == '-') { // found a flag
        if (key.length() > 1 && key.charAt(1) == '-')
          key = key.substring(2); // strip off 2 hyphens
        else
          key = key.substring(1); // strip off the hyphen

        int max = 1;
        int min = 0;
        List<String> flagArgs = new ArrayList<>();
        // cdm oct 2007: add length check to allow for empty string argument!
        for (int j = 0; j < max && i + 1 < args.length && (j < min || args[i + 1].length() == 0 || args[i + 1].charAt(0) != '-'); i++, j++) {
          flagArgs.add(args[i + 1]);
        }

        if (flagArgs.isEmpty()) {
          existingArgs.put(key, "true");
        } else {

          if (key.equalsIgnoreCase(PROP) || key.equalsIgnoreCase(PROPS) || key.equalsIgnoreCase(PROPERTIES) || key.equalsIgnoreCase(ARGUMENTS) || key.equalsIgnoreCase(ARGS)) {
            for(String flagArg: flagArgs)
              result.putAll(propFileToLinkedHashMap(flagArg, existingArgs));

            existingArgs.clear();
          } else
            existingArgs.put(key, join(flagArgs, " "));
        }
      }
    }
    result.putAll(existingArgs);

    for (Entry<String, String> o : result.entrySet()) {
      String val = resolveVars(o.getValue(), result);
      result.put(o.getKey(), val);
    }
    Properties props = new Properties();
    props.putAll(result);
    return props;
  }

  /**
   * This method reads in properties listed in a file in the format prop=value,
   * one property per line. and reads them into a LinkedHashMap (insertion order preserving)
   * Flags not having any arguments is set to "true".
   *
   * @param filename A properties file to read
   * @return The corresponding LinkedHashMap where the ordering is the same as in the
   *         props file
   */
  public static LinkedHashMap<String, String> propFileToLinkedHashMap(String filename, Map<String, String> existingArgs) {

    LinkedHashMap<String, String> result = new LinkedHashMap<>(existingArgs);
    for (String l : IOUtils.readLines(filename)) {
      l = l.trim();
      if (l.isEmpty() || l.startsWith("#"))
        continue;
      int index = l.indexOf('=');

      if (index == -1)
        result.put(l, "true");
      else
        result.put(l.substring(0, index).trim(), l.substring(index + 1).trim());
    }
    return result;
  }

  /**
   * n grams for already splitted string. the ngrams are joined with a single space
   */
  public static Collection<String> getNgrams(List<String> words, int minSize, int maxSize){
    List<List<String>> ng = CollectionUtils.getNGrams(words, minSize, maxSize);
    Collection<String> ngrams = new ArrayList<>();
    for(List<String> n: ng)
      ngrams.add(StringUtils.join(n," "));

    return ngrams;
  }

  /**
   * n grams for already splitted string. the ngrams are joined with a single space
   */
  public static Collection<String> getNgramsFromTokens(List<CoreLabel> words, int minSize, int maxSize){
    List<String> wordsStr = new ArrayList<>();
    for(CoreLabel l : words)
      wordsStr.add(l.word());
    List<List<String>> ng = CollectionUtils.getNGrams(wordsStr, minSize, maxSize);
    Collection<String> ngrams = new ArrayList<>();
    for(List<String> n: ng)
      ngrams.add(StringUtils.join(n," "));

    return ngrams;
  }

  /**
   * The string is split on whitespace and the ngrams are joined with a single space
   */
  public static Collection<String> getNgramsString(String s, int minSize, int maxSize){
    return getNgrams(Arrays.asList(s.split("\\s+")), minSize, maxSize);
  }

  /**
   * Build a list of character-based ngrams from the given string.
   */
  public static Collection<String> getCharacterNgrams(String s, int minSize, int maxSize) {
    Collection<String> ngrams = new ArrayList<>();
    int len = s.length();

    for (int i = 0; i < len; i++) {
      for (int ngramSize = minSize;
           ngramSize > 0 && ngramSize <= maxSize && i + ngramSize <= len;
           ngramSize++) {
        ngrams.add(s.substring(i, i + ngramSize));
      }
    }

    return ngrams;
  }

  private static Pattern diacriticalMarksPattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}");
  public static String normalize(String s) {
    // Normalizes string and strips diacritics (map to ascii) by
    // 1. taking the NFKD (compatibility decomposition -
    //   in compatibility equivalence, formatting such as subscripting is lost -
    //   see http://unicode.org/reports/tr15/)
    // 2. Removing diacriticals
    // 3. Recombining into NFKC form (compatibility composition)
    // This process may be slow.
    //
    // The main purpose of the function is to remove diacritics for asciis,
    //  but it may normalize other stuff as well.
    // A more conservative approach is to do explicit folding just for ascii character
    //   (see RuleBasedNameMatcher.normalize)
    String d = Normalizer.normalize(s, Normalizer.Form.NFKD);
    d = diacriticalMarksPattern.matcher(d).replaceAll("");
    return Normalizer.normalize(d, Normalizer.Form.NFKC);
  }

  /**
   * Convert a list of labels into a string, by simply joining them with spaces.
   * @param words The words to join.
   * @return A string representation of the sentence, tokenized by a single space.
   */
  public static String toString(List<CoreLabel> words) {
    return join(words.stream().map(CoreLabel::word), " ");
  }

  /**
   * Convert a CoreMap representing a sentence into a string, by simply joining them with spaces.
   * @param sentence The sentence to stringify.
   * @return A string representation of the sentence, tokenized by a single space.
   */
  public static String toString(CoreMap sentence) {
    return toString(sentence.get(CoreAnnotations.TokensAnnotation.class));
  }

  /** I shamefully stole this from: http://rosettacode.org/wiki/Levenshtein_distance#Java --Gabor */
  public static int levenshteinDistance(String s1, String s2) {
    s1 = s1.toLowerCase();
    s2 = s2.toLowerCase();

    int[] costs = new int[s2.length() + 1];
    for (int i = 0; i <= s1.length(); i++) {
      int lastValue = i;
      for (int j = 0; j <= s2.length(); j++) {
        if (i == 0)
          costs[j] = j;
        else {
          if (j > 0) {
            int newValue = costs[j - 1];
            if (s1.charAt(i - 1) != s2.charAt(j - 1))
              newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
            costs[j - 1] = lastValue;
            lastValue = newValue;
          }
        }
      }
      if (i > 0)
        costs[s2.length()] = lastValue;
    }
    return costs[s2.length()];
  }

  /** I shamefully stole this from: http://rosettacode.org/wiki/Levenshtein_distance#Java --Gabor */
  public static <E> int levenshteinDistance(E[] s1, E[] s2) {

    int[] costs = new int[s2.length + 1];
    for (int i = 0; i <= s1.length; i++) {
      int lastValue = i;
      for (int j = 0; j <= s2.length; j++) {
        if (i == 0)
          costs[j] = j;
        else {
          if (j > 0) {
            int newValue = costs[j - 1];
            if (!s1[i - 1].equals(s2[j - 1]))
              newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
            costs[j - 1] = lastValue;
            lastValue = newValue;
          }
        }
      }
      if (i > 0)
        costs[s2.length] = lastValue;
    }
    return costs[s2.length];
  }

  /**
   * Unescape an HTML string.
   * Taken from: http://stackoverflow.com/questions/994331/java-how-to-decode-html-character-entities-in-java-like-httputility-htmldecode
   * @param input The string to unescape
   * @return The unescaped String
   */
  public static String unescapeHtml3(final String input) {
        StringWriter writer = null;
        int len = input.length();
        int i = 1;
        int st = 0;
        while (true) {
            // look for '&'
            while (i < len && input.charAt(i-1) != '&')
                i++;
            if (i >= len)
                break;

            // found '&', look for ';'
            int j = i;
            while (j < len && j < i + 6 + 1 && input.charAt(j) != ';')
                j++;
            if (j == len || j < i + 2 || j == i + 6 + 1) {
                i++;
                continue;
            }

            // found escape
            if (input.charAt(i) == '#') {
                // numeric escape
                int k = i + 1;
                int radix = 10;

                final char firstChar = input.charAt(k);
                if (firstChar == 'x' || firstChar == 'X') {
                    k++;
                    radix = 16;
                }

                try {
                    int entityValue = Integer.parseInt(input.substring(k, j), radix);

                    if (writer == null)
                        writer = new StringWriter(input.length());
                    writer.append(input.substring(st, i - 1));

                    if (entityValue > 0xFFFF) {
                        final char[] chrs = Character.toChars(entityValue);
                        writer.write(chrs[0]);
                        writer.write(chrs[1]);
                    } else {
                        writer.write(entityValue);
                    }

                } catch (NumberFormatException ex) {
                    i++;
                    continue;
                }
            }
            else {
                // named escape
                CharSequence value = htmlUnescapeLookupMap.get(input.substring(i, j));
                if (value == null) {
                    i++;
                    continue;
                }

                if (writer == null)
                    writer = new StringWriter(input.length());
                writer.append(input.substring(st, i - 1));

                writer.append(value);
            }

            // skip escape
            st = j + 1;
            i = st;
        }

        if (writer != null) {
            writer.append(input.substring(st, len));
            return writer.toString();
        }
        return input;
    }

    private static final String[][] HTML_ESCAPES = {
        {"\"",     "quot"}, // " - double-quote
        {"&",      "amp"}, // & - ampersand
        {"<",      "lt"}, // < - less-than
        {">",      "gt"}, // > - greater-than
        {"-",      "ndash"}, // - - dash

        // Mapping to escape ISO-8859-1 characters to their named HTML 3.x equivalents.
        {"\u00A0", "nbsp"}, // non-breaking space
        {"\u00A1", "iexcl"}, // inverted exclamation mark
        {"\u00A2", "cent"}, // cent sign
        {"\u00A3", "pound"}, // pound sign
        {"\u00A4", "curren"}, // currency sign
        {"\u00A5", "yen"}, // yen sign = yuan sign
        {"\u00A6", "brvbar"}, // broken bar = broken vertical bar
        {"\u00A7", "sect"}, // section sign
        {"\u00A8", "uml"}, // diaeresis = spacing diaeresis
        {"\u00A9", "copy"}, // © - copyright sign
        {"\u00AA", "ordf"}, // feminine ordinal indicator
        {"\u00AB", "laquo"}, // left-pointing double angle quotation mark = left pointing guillemet
        {"\u00AC", "not"}, // not sign
        {"\u00AD", "shy"}, // soft hyphen = discretionary hyphen
        {"\u00AE", "reg"}, // ® - registered trademark sign
        {"\u00AF", "macr"}, // macron = spacing macron = overline = APL overbar
        {"\u00B0", "deg"}, // degree sign
        {"\u00B1", "plusmn"}, // plus-minus sign = plus-or-minus sign
        {"\u00B2", "sup2"}, // superscript two = superscript digit two = squared
        {"\u00B3", "sup3"}, // superscript three = superscript digit three = cubed
        {"\u00B4", "acute"}, // acute accent = spacing acute
        {"\u00B5", "micro"}, // micro sign
        {"\u00B6", "para"}, // pilcrow sign = paragraph sign
        {"\u00B7", "middot"}, // middle dot = Georgian comma = Greek middle dot
        {"\u00B8", "cedil"}, // cedilla = spacing cedilla
        {"\u00B9", "sup1"}, // superscript one = superscript digit one
        {"\u00BA", "ordm"}, // masculine ordinal indicator
        {"\u00BB", "raquo"}, // right-pointing double angle quotation mark = right pointing guillemet
        {"\u00BC", "frac14"}, // vulgar fraction one quarter = fraction one quarter
        {"\u00BD", "frac12"}, // vulgar fraction one half = fraction one half
        {"\u00BE", "frac34"}, // vulgar fraction three quarters = fraction three quarters
        {"\u00BF", "iquest"}, // inverted question mark = turned question mark
        {"\u00C0", "Agrave"}, // А - uppercase A, grave accent
        {"\u00C1", "Aacute"}, // Б - uppercase A, acute accent
        {"\u00C2", "Acirc"}, // В - uppercase A, circumflex accent
        {"\u00C3", "Atilde"}, // Г - uppercase A, tilde
        {"\u00C4", "Auml"}, // Д - uppercase A, umlaut
        {"\u00C5", "Aring"}, // Е - uppercase A, ring
        {"\u00C6", "AElig"}, // Ж - uppercase AE
        {"\u00C7", "Ccedil"}, // З - uppercase C, cedilla
        {"\u00C8", "Egrave"}, // И - uppercase E, grave accent
        {"\u00C9", "Eacute"}, // Й - uppercase E, acute accent
        {"\u00CA", "Ecirc"}, // К - uppercase E, circumflex accent
        {"\u00CB", "Euml"}, // Л - uppercase E, umlaut
        {"\u00CC", "Igrave"}, // М - uppercase I, grave accent
        {"\u00CD", "Iacute"}, // Н - uppercase I, acute accent
        {"\u00CE", "Icirc"}, // О - uppercase I, circumflex accent
        {"\u00CF", "Iuml"}, // П - uppercase I, umlaut
        {"\u00D0", "ETH"}, // Р - uppercase Eth, Icelandic
        {"\u00D1", "Ntilde"}, // С - uppercase N, tilde
        {"\u00D2", "Ograve"}, // Т - uppercase O, grave accent
        {"\u00D3", "Oacute"}, // У - uppercase O, acute accent
        {"\u00D4", "Ocirc"}, // Ф - uppercase O, circumflex accent
        {"\u00D5", "Otilde"}, // Х - uppercase O, tilde
        {"\u00D6", "Ouml"}, // Ц - uppercase O, umlaut
        {"\u00D7", "times"}, // multiplication sign
        {"\u00D8", "Oslash"}, // Ш - uppercase O, slash
        {"\u00D9", "Ugrave"}, // Щ - uppercase U, grave accent
        {"\u00DA", "Uacute"}, // Ъ - uppercase U, acute accent
        {"\u00DB", "Ucirc"}, // Ы - uppercase U, circumflex accent
        {"\u00DC", "Uuml"}, // Ь - uppercase U, umlaut
        {"\u00DD", "Yacute"}, // Э - uppercase Y, acute accent
        {"\u00DE", "THORN"}, // Ю - uppercase THORN, Icelandic
        {"\u00DF", "szlig"}, // Я - lowercase sharps, German
        {"\u00E0", "agrave"}, // а - lowercase a, grave accent
        {"\u00E1", "aacute"}, // б - lowercase a, acute accent
        {"\u00E2", "acirc"}, // в - lowercase a, circumflex accent
        {"\u00E3", "atilde"}, // г - lowercase a, tilde
        {"\u00E4", "auml"}, // д - lowercase a, umlaut
        {"\u00E5", "aring"}, // е - lowercase a, ring
        {"\u00E6", "aelig"}, // ж - lowercase ae
        {"\u00E7", "ccedil"}, // з - lowercase c, cedilla
        {"\u00E8", "egrave"}, // и - lowercase e, grave accent
        {"\u00E9", "eacute"}, // й - lowercase e, acute accent
        {"\u00EA", "ecirc"}, // к - lowercase e, circumflex accent
        {"\u00EB", "euml"}, // л - lowercase e, umlaut
        {"\u00EC", "igrave"}, // м - lowercase i, grave accent
        {"\u00ED", "iacute"}, // н - lowercase i, acute accent
        {"\u00EE", "icirc"}, // о - lowercase i, circumflex accent
        {"\u00EF", "iuml"}, // п - lowercase i, umlaut
        {"\u00F0", "eth"}, // р - lowercase eth, Icelandic
        {"\u00F1", "ntilde"}, // с - lowercase n, tilde
        {"\u00F2", "ograve"}, // т - lowercase o, grave accent
        {"\u00F3", "oacute"}, // у - lowercase o, acute accent
        {"\u00F4", "ocirc"}, // ф - lowercase o, circumflex accent
        {"\u00F5", "otilde"}, // х - lowercase o, tilde
        {"\u00F6", "ouml"}, // ц - lowercase o, umlaut
        {"\u00F7", "divide"}, // division sign
        {"\u00F8", "oslash"}, // ш - lowercase o, slash
        {"\u00F9", "ugrave"}, // щ - lowercase u, grave accent
        {"\u00FA", "uacute"}, // ъ - lowercase u, acute accent
        {"\u00FB", "ucirc"}, // ы - lowercase u, circumflex accent
        {"\u00FC", "uuml"}, // ь - lowercase u, umlaut
        {"\u00FD", "yacute"}, // э - lowercase y, acute accent
        {"\u00FE", "thorn"}, // ю - lowercase thorn, Icelandic
        {"\u00FF", "yuml"}, // я - lowercase y, umlaut
    };

  private static final HashMap<String, CharSequence> htmlUnescapeLookupMap;
    static {
        htmlUnescapeLookupMap = new HashMap<>();
        for (final CharSequence[] seq : HTML_ESCAPES)
            htmlUnescapeLookupMap.put(seq[1].toString(), seq[0]);
    }

  /**
   * Decode an array encoded as a String. This entails a comma separated value enclosed in brackets
   * or parentheses.
   *
   * @param encoded The String encoding an array
   * @return A String array corresponding to the encoded array
   */
  public static String[] decodeArray(String encoded) {
    if (encoded.isEmpty()) return EMPTY_STRING_ARRAY;
    char[] chars = encoded.trim().toCharArray();

    //--Parse the String
    // (state)
    char quoteCloseChar = (char) 0;
    List<String> terms = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    //(start/stop overhead)
    int start = 0; int end = chars.length;
    if(chars[0] == '('){ start += 1; end -= 1; if(chars[end] != ')') throw new IllegalArgumentException("Unclosed paren in encoded array: " + encoded); }
    if(chars[0] == '['){ start += 1; end -= 1; if(chars[end] != ']') throw new IllegalArgumentException("Unclosed bracket in encoded array: " + encoded); }
    if(chars[0] == '{'){ start += 1; end -= 1; if(chars[end] != '}') throw new IllegalArgumentException("Unclosed bracket in encoded array: " + encoded); }
    // (finite state automaton)
    for (int i=start; i<end; i++) {
      if (chars[i] == '\r') {
        // Ignore funny windows carriage return
        continue;
      } else if (quoteCloseChar != 0) {
        //(case: in quotes)
        if(chars[i] == quoteCloseChar){
          quoteCloseChar = (char) 0;
        }else{
          current.append(chars[i]);
        }
      } else if(chars[i] == '\\'){
        //(case: escaped character)
        if(i == chars.length - 1) throw new IllegalArgumentException("Last character of encoded array is escape character: " + encoded);
        current.append(chars[i+1]);
        i += 1;
      } else {
        //(case: normal)
        if (chars[i] == '"') {
          quoteCloseChar = '"';
        } else if(chars[i] == '\'') {
          quoteCloseChar = '\'';
        } else if(chars[i] == ',' || chars[i] == ';' || chars[i] == ' ' || chars[i] == '\t' || chars[i] == '\n') {
          //break
          if (current.length() > 0) {
            terms.add(current.toString().trim());
          }
          current = new StringBuilder();
        } else {
          current.append(chars[i]);
        }
      }
    }

    //--Return
    if (current.length() > 0) {
      terms.add(current.toString().trim());
    }
    return terms.toArray(EMPTY_STRING_ARRAY);
  }

  /**
   * Decode a map encoded as a string.
   *
   * @param encoded The String encoded map
   * @return A String map corresponding to the encoded map
   */
  public static Map<String, String> decodeMap(String encoded){
    if (encoded.isEmpty()) return new HashMap<>();
    char[] chars = encoded.trim().toCharArray();

    //--Parse the String
    //(state)
    char quoteCloseChar = (char) 0;
    Map<String, String> map = new HashMap<>();
    String key = "";
    String value = "";
    boolean onKey = true;
    StringBuilder current = new StringBuilder();
    //(start/stop overhead)
    int start = 0; int end = chars.length;
    if(chars[0] == '('){ start += 1; end -= 1; if(chars[end] != ')') throw new IllegalArgumentException("Unclosed paren in encoded map: " + encoded); }
    if(chars[0] == '['){ start += 1; end -= 1; if(chars[end] != ']') throw new IllegalArgumentException("Unclosed bracket in encoded map: " + encoded); }
    if(chars[0] == '{'){ start += 1; end -= 1; if(chars[end] != '}') throw new IllegalArgumentException("Unclosed bracket in encoded map: " + encoded); }
    //(finite state automata)
    for(int i=start; i<end; i++){
      if (chars[i] == '\r') {
        // Ignore funny windows carriage return
        continue;
      } else if(quoteCloseChar != 0){
        //(case: in quotes)
        if(chars[i] == quoteCloseChar){
          quoteCloseChar = (char) 0;
        }else{
          current.append(chars[i]);
        }
      } else if(chars[i] == '\\'){
        //(case: escaped character)
        if(i == chars.length - 1) {
          throw new IllegalArgumentException("Last character of encoded pair is escape character: " + encoded);
        }
        current.append(chars[i+1]);
        i += 1;
      }else{
        //(case: normal)
        if(chars[i] == '"'){
          quoteCloseChar = '"';
        } else if(chars[i] == '\''){
          quoteCloseChar = '\'';
        } else if (chars[i] == '\n' && current.length() == 0) {
          current.append("");  // do nothing
        } else if(chars[i] == ',' || chars[i] == ';' || chars[i] == '\t' || chars[i] == '\n'){
          // case: end a value
          if (onKey) {
            throw new IllegalArgumentException("Encountered key without value");
          }
          if (current.length() > 0) {
            value = current.toString().trim();
          }
          current = new StringBuilder();
          onKey = true;
          map.put(key, value);  // <- add value
        } else if((chars[i] == '-' || chars[i] == '=') && (i < chars.length - 1 && chars[i + 1] == '>')) {
          // case: end a key
          if (!onKey) {
            throw new IllegalArgumentException("Encountered a value without a key");
          }
          if (current.length() > 0) {
            key = current.toString().trim();
          }
          current = new StringBuilder();
          onKey = false;
          i += 1; // skip '>' character
        } else if (chars[i] == ':') {
          // case: end a key
          if (!onKey) {
            throw new IllegalArgumentException("Encountered a value without a key");
          }
          if (current.length() > 0) {
            key = current.toString().trim();
          }
          current = new StringBuilder();
          onKey = false;
        } else {
          current.append(chars[i]);
        }
      }
    }

    //--Return
    if (current.toString().trim().length() > 0 && !onKey) {
      map.put(key.trim(), current.toString().trim());
    }
    return map;
  }


  /**
   * Takes an input String, and replaces any bash-style variables (e.g., $VAR_NAME)
   * with its actual environment variable from the passed environment specification.
   *
   * @param raw The raw String to replace variables in.
   * @param env The environment specification; e.g., {@link System#getenv()}.
   * @return The input String, but with all variables replaced.
   */
  public static String expandEnvironmentVariables(String raw, Map<String, String> env) {
    String pattern = "\\$\\{?([a-zA-Z_]+[a-zA-Z0-9_]*)\\}?";
    Pattern expr = Pattern.compile(pattern);
    String text = raw;
    Matcher matcher = expr.matcher(text);
    while (matcher.find()) {
      String envValue = env.get(matcher.group(1));
      if (envValue == null) {
        envValue = "";
      } else {
        envValue = envValue.replace("\\", "\\\\");
      }
      Pattern subexpr = Pattern.compile(Pattern.quote(matcher.group(0)));
      text = subexpr.matcher(text).replaceAll(envValue);
    }
    return text;
  }

  /**
   * Takes an input String, and replaces any bash-style variables (e.g., $VAR_NAME)
   * with its actual environment variable from {@link System#getenv()}.
   *
   * @param raw The raw String to replace variables in.
   * @return The input String, but with all variables replaced.
   */
  public static String expandEnvironmentVariables(String raw) {
    return expandEnvironmentVariables(raw, System.getenv());
  }


  /**
   * Logs the command line arguments to Redwood on the given channels.
   * The logger should be a RedwoodChannels of a single channel: the main class.
   *
   * @param logger The redwood logger to log to.
   * @param args The command-line arguments to log.
   */
  public static void logInvocationString(Redwood.RedwoodChannels logger, String[] args) {
    StringBuilder sb = new StringBuilder("Invoked on ");
    sb.append(new Date());
    sb.append(" with arguments:");
    for (String arg : args) {
      sb.append(' ').append(arg);
    }
    logger.info(sb.toString());
  }

}
