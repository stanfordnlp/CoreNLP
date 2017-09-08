package old.edu.stanford.nlp.util;

import old.edu.stanford.nlp.math.SloppyMath;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StringUtils is a class for random String things, including output
 * formatting and command line argument parsing.
 *
 * @author Dan Klein
 * @author Christopher Manning
 * @author Tim Grow (grow@stanford.edu)
 * @author Chris Cox
 * @version 2006/02/03
 */
public class StringUtils {

  /**
   * Don't let anyone instantiate this class.
   */
  private StringUtils() {
  }

  public static final String[] EMPTY_STRING_ARRAY = new String[0];

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
   * Convenience method: a case-insensitive variant of Collection.contains
   * @param c Collection<String>
   * @param s String
   * @return true if s case-insensitively matches a string in c
   */
  public static boolean containsIgnoreCase(Collection<String> c, String s) {
    for (String squote: c) {
      if (squote.equalsIgnoreCase(s))
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
   * String[] s is returned such that s[yn]=xn
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
    Arrays.fill(mapArr, null);
    for (int i = 0; i < m.length; i++) {
      mapArr[indices[i]] = keys[i];
    }
    return mapArr;
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


  /**
   * Joins each elem in the {@code Collection} with the given glue.
   * For example, given a list of {@code Integers}, you can create
   * a comma-separated list by calling {@code join(numbers, ", ")}.
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


// Omitted; I'm pretty sure this are redundant with the above
//  /**
//   * Joins each elem in the List with the given glue. For example, given a
//   * list
//   * of Integers, you can create a comma-separated list by calling
//   * <tt>join(numbers, ", ")</tt>.
//   */
//  public static String join(List l, String glue) {
//    StringBuilder sb = new StringBuilder();
//    for (int i = 0, sz = l.size(); i < sz; i++) {
//      if (i > 0) {
//        sb.append(glue);
//      }
//      sb.append(l.get(i).toString());
//    }
//    return sb.toString();
//  }

  /**
   * Joins each elem in the array with the given glue. For example, given a
   * list of ints, you can create a comma-separated list by calling
   * <tt>join(numbers, ", ")</tt>.
   */
  public static String join(Object[] elements, String glue) {
    return (join(Arrays.asList(elements), glue));
  }

  /**
   * Joins elems with a space.
   */
  public static String join(Iterable<?> l) {
    return join(l, " ");
  }

  /**
   * Joins elems with a space.
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
    List<String> ret = new ArrayList<String>();
    while (str.length() > 0) {
      Matcher vm = vPat.matcher(str);
      if (vm.lookingAt()) {
        ret.add(vm.group());
        str = str.substring(vm.end());
        // String got = vm.group();
        // System.err.println("vmatched " + got + "; now str is " + str);
      } else {
        throw new IllegalArgumentException("valueSplit: " + valueRegex + " doesn't match " + str);
      }
      if (str.length() > 0) {
        Matcher sm = sPat.matcher(str);
        if (sm.lookingAt()) {
          str = str.substring(sm.end());
          // String got = sm.group();
          // System.err.println("smatched " + got + "; now str is " + str);
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
    if (str == null) {
      str = "null";
    }
    int slen = str.length();
    StringBuilder sb = new StringBuilder(str);
    for (int i = 0; i < totalChars - slen; i++) {
      sb.append(' ');
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
   * Pads the given String to the left with the given character to ensure that
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
    return (s.substring(0, maxWidth));
  }

  public static String trim(Object obj, int maxWidth) {
    return trim(obj.toString(), maxWidth);
  }

  public static String repeat(String s, int times) {
    if (times == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < times; i++) {
      sb.append(s);
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
   * -flag1 arg1a arg1b ... arg1m -flag2 -flag3 arg3a ... arg3n
   * <p/>
   * will be parsed so that the flag is a key in the Map (including
   * the hyphen) and its value will be a {@link String}[] containing
   * the optional arguments (if present).  The non-flag values not
   * captured as flag arguments are collected into a String[] array
   * and returned as the value of <code>null</code> in the Map.  In
   * this invocation, flags cannot take arguments, so all the {@link
   * String} array values other than the value for <code>null</code>
   * will be zero-length.
   *
   * @param args A command-line arguments array
   * @return a {@link Map} of flag names to flag argument {@link
   *         String} arrays.
   */
  public static Map<String, String[]> argsToMap(String[] args) {
    return argsToMap(args, new HashMap<String, Integer>());
  }

  /**
   * Parses command line arguments into a Map. Arguments of the form
   * <p/>
   * -flag1 arg1a arg1b ... arg1m -flag2 -flag3 arg3a ... arg3n
   * <p/>
   * will be parsed so that the flag is a key in the Map (including
   * the hyphen) and its value will be a {@link String}[] containing
   * the optional arguments (if present).  The non-flag values not
   * captured as flag arguments are collected into a String[] array
   * and returned as the value of <code>null</code> in the Map.  In
   * this invocation, the maximum number of arguments for each flag
   * can be specified as an {@link Integer} value of the appropriate
   * flag key in the <code>flagsToNumArgs</code> {@link Map}
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
   * @param flagsToNumArgs a {@link Map} of flag names to {@link
   *                       Integer} values specifying the maximum number of
   *                       allowed arguments for that flag (default 0).
   * @return a {@link Map} of flag names to flag argument {@link
   *         String} arrays.
   */
  public static Map<String, String[]> argsToMap(String[] args, Map<String, Integer> flagsToNumArgs) {
    Map<String, String[]> result = new HashMap<String, String[]>();
    List<String> remainingArgs = new ArrayList<String>();
    for (int i = 0; i < args.length; i++) {
      String key = args[i];
      if (key.charAt(0) == '-') { // found a flag
        Integer maxFlagArgs = flagsToNumArgs.get(key);
        int max = maxFlagArgs == null ? 0 : maxFlagArgs.intValue();
        List<String> flagArgs = new ArrayList<String>();
        for (int j = 0; j < max && i + 1 < args.length && args[i + 1].charAt(0) != '-'; i++, j++) {
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

  private static final String PROP = "prop";
  private static final String PROPS = "props";
  private static final String PROPERTIES = "properties";

  /**
   * In this version each flag has zero or one argument. It has one argument
   * if there is a thing following a flag that does not begin with '-'.  See
   * {@link #argsToProperties(String[], Map)} for full documentation.
   *
   * @param args Command line arguments
   * @return A Properties object representing the arguments.
   */
  public static Properties argsToProperties(String[] args) {
    return argsToProperties(args, Collections.<String,Integer>emptyMap());
  }

  /**
   * Analagous to {@link #argsToMap}.  However, there are several key differences between this method and {@link #argsToMap}:
   * <ul>
   * <li> Hyphens are stripped from flag names </li>
   * <li> Since Properties objects are String to String mappings, the default number of arguments to a flag is
   * assumed to be 1 and not 0. </li>
   * <li> Furthermore, the list of arguments not bound to a flag is mapped to the "" property, not null </li>
   * <li> The special flags "-prop", "-props", or "-properties" will load the property file specified by its argument. </li>
   * <li> The value for flags without arguments is set to "true" </li>
   * <li> If a flag has multiple arguments, the value of the property is all
   * of the arguments joined together with a space (" ") character between
   * them.</li>
   * <li> The value strings are trimmed so trailing spaces do not stop you from loading a file</li>
   * </ul>
   *
   * @param args Command line arguments
   * @param flagsToNumArgs Map of how many arguments flags should have. The keys are without the minus signs.
   * @return A Properties object representing the arguments.
   */
  public static Properties argsToProperties(String[] args, Map<String,Integer> flagsToNumArgs) {
    Properties result = new Properties();
    List<String> remainingArgs = new ArrayList<String>();
    for (int i = 0; i < args.length; i++) {
      String key = args[i];
      if (key.length() > 0 && key.charAt(0) == '-') { // found a flag
        key = key.substring(1); // strip off the hyphen

        Integer maxFlagArgs = flagsToNumArgs.get(key);
        int max = maxFlagArgs == null ? 1 : maxFlagArgs;
        int min = maxFlagArgs == null ? 0 : maxFlagArgs;
        List<String> flagArgs = new ArrayList<String>();
        // cdm oct 2007: add length check to allow for empty string argument!
        for (int j = 0; j < max && i + 1 < args.length && (j < min || args[i + 1].length() == 0 || args[i + 1].length() > 0 && args[i + 1].charAt(0) != '-'); i++, j++) {
          flagArgs.add(args[i + 1]);
        }
        if (flagArgs.isEmpty()) {
          result.setProperty(key, "true");
        } else {
          result.setProperty(key, join(flagArgs, " "));
          if (key.equalsIgnoreCase(PROP) || key.equalsIgnoreCase(PROPS) || key.equalsIgnoreCase(PROPERTIES))
          {
            try {
              InputStream is = new BufferedInputStream(new FileInputStream(result.getProperty(key)));
              result.remove(key); // location of this line is critical
              result.load(is);
              // trim all values
              for(Object propKey : result.keySet()){
                String newVal = result.getProperty((String)propKey);
                result.setProperty((String)propKey,newVal.trim());
              }
              is.close();
            } catch (IOException e) {
              result.remove(key);
              System.err.println("argsToProperties could not read properties file: " + result.getProperty(key));
              throw new RuntimeException(e);
            }
          }
        }
      } else {
        remainingArgs.add(args[i]);
      }
    }
    if (!remainingArgs.isEmpty()) {
      result.setProperty("", join(remainingArgs, " "));
    }

    if (result.containsKey(PROP)) {
      String file = result.getProperty(PROP);
      result.remove(PROP);
      Properties toAdd = argsToProperties(new String[]{"-prop", file});
      for (Enumeration<?> e = toAdd.propertyNames(); e.hasMoreElements(); ) {
        String key = (String) e.nextElement();
        String val = toAdd.getProperty(key);
        if (!result.containsKey(key)) {
          result.setProperty(key, val);
        }
      }
    }

    return result;
  }


  /**
   * This method converts a comma-separated String (with whitespace
   * optionally allowed after the comma) representing properties
   * to a Properties object.  Each property is "property=value".  The value
   * for properties without an explicitly given value is set to "true". This can be used for a 2nd level
   * of properties, for example, when you have a comamndline argument like "-outputOptions style=xml,tags".
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
   * Prints to a file.  If the file already exists, appends if
   * <code>append=true</code>, and overwrites if <code>append=false</code>.
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
      System.err.println("Exception: in printToFile " + file.getAbsolutePath());
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
   * <code>append=true</code>, and overwrites if <code>append=false</code>.
   */
  public static void printToFileLn(File file, String message, boolean append) {
    PrintWriter pw = null;
    try {
      Writer fw = new FileWriter(file, append);
      pw = new PrintWriter(fw);
      pw.println(message);
    } catch (Exception e) {
      System.err.println("Exception: in printToFileLn " + file.getAbsolutePath() + ' ' + message);
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
   * <code>append=true</code>, and overwrites if <code>append=false</code>.
   */
  public static void printToFile(File file, String message, boolean append) {
    PrintWriter pw = null;
    try {
      Writer fw = new FileWriter(file, append);
      pw = new PrintWriter(fw);
      pw.print(message);
    } catch (Exception e) {
      System.err.println("Exception: in printToFile " + file.getAbsolutePath());
      e.printStackTrace();
    } finally {
      if (pw != null) {
        pw.flush();
        pw.close();
      }
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
   * <code>append=true</code>, and overwrites if <code>append=false</code>
   */
  public static void printToFile(String filename, String message, boolean append) {
    printToFile(new File(filename), message, append);
  }

  /**
   * Prints to a file.  If the file already exists, appends if
   * <code>append=true</code>, and overwrites if <code>append=false</code>
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
    Map<String, Object> result = new HashMap<String, Object>();
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
   * by the escapeChar
   *
   * @param s         The String to split
   * @param splitChar The character to split on
   * @param quoteChar The character to quote items with
   * @param escapeChar The character to escape the quoteChar with
   * @return An array of Strings that s is split into
   */
  public static String[] splitOnCharWithQuoting(String s, char splitChar, char quoteChar, char escapeChar) {
    List<String> result = new ArrayList<String>();
    int i = 0;
    int length = s.length();
    StringBuilder b = new StringBuilder();
    while (i < length) {
      char curr = s.charAt(i);
      if (curr == splitChar) {
        // add last buffer
        if (b.length() > 0) {
          result.add(b.toString());
          b = new StringBuilder();
        }
        i++;
      } else if (curr == quoteChar) {
        // find next instance of quoteChar
        i++;
        while (i < length) {
          curr = s.charAt(i);
          if (curr == escapeChar) {
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
        System.err.print(' ');
      }
      for (j = 0; j < m; j++) {
        System.err.print(t.charAt(j) + " ");
      }
      System.err.println();
      for (i = 0; i <= n; i++) {
        System.err.print((i == 0 ? ' ' : s.charAt(i - 1)) + " ");
        for (j = 0; j <= m; j++) {
          System.err.print(d[i][j] + " ");
        }
        System.err.println();
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
    if (s.length() == 0 || t.length() == 0) {
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
    // System.err.println("LCCS(" + s + "," + t + ") = " + max);
    return max;
  }

  /**
   * Computes the Levenshtein (edit) distance of the two given Strings.
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
    String name = o.getClass().getName();
    int index = name.lastIndexOf('.');
    if (index >= 0) {
      name = name.substring(index + 1);
    }
    return name;
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
   * Returns an text table containing the matrix of Strings passed in.
   * The first dimension of the matrix should represent the rows, and the
   * second dimension the columns.
   */
  public static String makeAsciiTable(Object[][] table, Object[] rowLabels, Object[] colLabels, int padLeft, int padRight, boolean tsv) {
    StringBuilder buff = new StringBuilder();
    // top row
    buff.append(makeAsciiTableCell("", padLeft, padRight, tsv)); // the top left cell
    for (int j = 0; j < table[0].length; j++) { // assume table is a rectangular matrix
      buff.append(makeAsciiTableCell(colLabels[j], padLeft, padRight, (j != table[0].length - 1) && tsv));
    }
    buff.append('\n');
    // all other rows
    for (int i = 0; i < table.length; i++) {
      // one row
      buff.append(makeAsciiTableCell(rowLabels[i], padLeft, padRight, tsv));
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

    String[] s = {"there once was a man", "this one is a manic", "hey there", "there once was a mane", "once in a manger.", "where is one match?"};
    for (int i = 0; i < 6; i++) {
      for (int j = 0; j < 6; j++) {
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
   * Swap any occurances of any characters in the from String in the input String with
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
   * Returns the supplied string with any trailing '\n' removed.
   */
  public static String chomp(String s) {
    if(s.length() == 0)
      return s;
    int l_1 = s.length() - 1;
    if (s.charAt(l_1) == '\n') {
      return s.substring(0, l_1);
    }
    return s;
  }

  /**
   * Returns the result of calling toString() on the supplied Object, but with
   * any trailing '\n' removed.
   */
  public static String chomp(Object o) {
    return chomp(o.toString());
  }


  public static void printErrInvocationString(String cls, String[] args) {
    System.err.println(toInvocationString(cls, args));
  }


  public static String toInvocationString(String cls, String[] args) {
    StringBuilder sb = new StringBuilder();
    sb.append(cls).append(" invoked on ").append(new Date());
    sb.append(" with arguments:\n  ");
    for (String arg : args) {
      sb.append(' ').append(arg);
    }
    return sb.toString();
  }

  /**
   * Strip directory from filename.  Like Unix 'basename'. <p/>
   *
   * Example: <code>getBaseName("/u/wcmac/foo.txt") ==> "foo.txt"</code>
   */
  public static String getBaseName(String fileName) {
    return getBaseName(fileName, "");
  }

  /**
   * Strip directory and suffix from filename.  Like Unix 'basename'. <p/>
   *
   * Example: <code>getBaseName("/u/wcmac/foo.txt", "") ==> "foo.txt"</code><br/>
   * Example: <code>getBaseName("/u/wcmac/foo.txt", ".txt") ==> "foo"</code><br/>
   * Example: <code>getBaseName("/u/wcmac/foo.txt", ".pdf") ==> "foo.txt"</code><br/>
   */
  public static String getBaseName(String fileName, String suffix) {
    String[] elts = fileName.split("/");
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
}
