package edu.stanford.nlp.process;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Timing;

// TODO: put in a regexp for ordinals, fraction num/num and perhaps even 30-5/8


/**
 * Provides static methods which
 * map any String to another String indicative of its "word shape" -- e.g.,
 * whether capitalized, numeric, etc.  Different implementations may
 * implement quite different, normally language specific ideas of what
 * word shapes are useful.
 *
 * @author Christopher Manning
 * @author Dan Klein
 */
public class WordShapeClassifier {

  public static final int NOWORDSHAPE = -1;
  public static final int WORDSHAPEDAN1 = 0;
  public static final int WORDSHAPECHRIS1 = 1;
  public static final int WORDSHAPEDAN2 = 2;
  public static final int WORDSHAPEDAN2USELC = 3;
  public static final int WORDSHAPEDAN2BIO = 4;
  public static final int WORDSHAPEDAN2BIOUSELC = 5;
  public static final int WORDSHAPEJENNY1 = 6;
  public static final int WORDSHAPEJENNY1USELC = 7;
  public static final int WORDSHAPECHRIS2 = 8;
  public static final int WORDSHAPECHRIS2USELC = 9;
  public static final int WORDSHAPECHRIS3 = 10;
  public static final int WORDSHAPECHRIS3USELC = 11;
  public static final int WORDSHAPECHRIS4 = 12;
  public static final int WORDSHAPEDIGITS = 13;
  public static final int WORDSHAPECHINESE = 14;
  public static final int WORDSHAPECLUSTER1 = 15;


  // This class cannot be instantiated
  private WordShapeClassifier() {
  }


  /** Look up a shaper by a short String name.
   *
   * @param name Shaper name.  Known names have patterns along the lines of:
   *             dan[12](bio)?(UseLC)?, jenny1(useLC)?, chris[1234](useLC)?, cluster1.
   * @return An integer constant for the shaper
   */
  public static int lookupShaper(String name) {
    if (name == null) {
      return NOWORDSHAPE;
    } else if (name.equalsIgnoreCase("dan1")) {
      return WORDSHAPEDAN1;
    } else if (name.equalsIgnoreCase("chris1")) {
      return WORDSHAPECHRIS1;
    } else if (name.equalsIgnoreCase("dan2")) {
      return WORDSHAPEDAN2;
    } else if (name.equalsIgnoreCase("dan2useLC")) {
      return WORDSHAPEDAN2USELC;
    } else if (name.equalsIgnoreCase("dan2bio")) {
      return WORDSHAPEDAN2BIO;
    } else if (name.equalsIgnoreCase("dan2bioUseLC")) {
      return WORDSHAPEDAN2BIOUSELC;
    } else if (name.equalsIgnoreCase("jenny1")) {
      return WORDSHAPEJENNY1;
    } else if (name.equalsIgnoreCase("jenny1useLC")) {
      return WORDSHAPEJENNY1USELC;
    } else if (name.equalsIgnoreCase("chris2")) {
      return WORDSHAPECHRIS2;
    } else if (name.equalsIgnoreCase("chris2useLC")) {
      return WORDSHAPECHRIS2USELC;
    } else if (name.equalsIgnoreCase("chris3")) {
      return WORDSHAPECHRIS3;
    } else if (name.equalsIgnoreCase("chris3useLC")) {
      return WORDSHAPECHRIS3USELC;
    } else if (name.equalsIgnoreCase("chris4")) {
      return WORDSHAPECHRIS4;
    } else if (name.equalsIgnoreCase("digits")) {
      return WORDSHAPEDIGITS;
    } else if (name.equalsIgnoreCase("chinese")) {
      return WORDSHAPECHINESE;
    } else if (name.equalsIgnoreCase("cluster1")) {
      return WORDSHAPECLUSTER1;
    } else {
      return NOWORDSHAPE;
    }
  }

  /**
   * Returns true if the specified word shaper doesn't use
   * known lower case words, even if a list of them is present.
   * This is used for backwards compatibility. It is suggested that
   * new word shape functions are either passed a non-null list of
   * lowercase words or not, depending on whether you want knownLC marking
   * (if it is available in a shaper).  This is how chris4 works.
   *
   * @param shape One of the defined shape constants
   * @return true if the specified word shaper uses
   *     known lower case words.
   */
  private static boolean dontUseLC(int shape) {
    return shape == WORDSHAPEDAN2 ||
            shape == WORDSHAPEDAN2BIO ||
            shape == WORDSHAPEJENNY1 ||
            shape == WORDSHAPECHRIS2 ||
            shape == WORDSHAPECHRIS3;
  }


  /**
   * Specify the String and the int identifying which word shaper to
   * use and this returns the result of using that wordshaper on the String.
   *
   * @param inStr String to calculate word shape of
   * @param wordShaper Constant for which shaping formula to use
   * @return The wordshape String
   */
  public static String wordShape(String inStr, int wordShaper) {
    return wordShape(inStr, wordShaper, null);
  }


  /**
   * Specify the string and the int identifying which word shaper to
   * use and this returns the result of using that wordshaper on the String.
   *
   * @param inStr String to calculate word shape of
   * @param wordShaper Constant for which shaping formula to use
   * @param knownLCWords A Collection of known lowercase words, which some shapers use
   *           to decide the class of capitalized words.
   *           <i>Note: while this code works with any Collection, you should
   *           provide a Set for decent performance.</i>  If this parameter is
   *           null or empty, then this option is not used (capitalized words
   *           are treated the same, regardless of whether the lowercased
   *           version of the String has been seen).
   * @return The wordshape String
   */
  public static String wordShape(String inStr, int wordShaper, Collection<String> knownLCWords) {
    // this first bit is for backwards compatibility with how things were first
    // implemented, where the word shaper name encodes whether to useLC.
    // If the shaper is in the old compatibility list, then a specified
    // list of knownLCwords is ignored
    if (knownLCWords != null && dontUseLC(wordShaper)) {
      knownLCWords = null;
    }
    switch (wordShaper) {
      case NOWORDSHAPE:
        return inStr;
      case WORDSHAPEDAN1:
        return wordShapeDan1(inStr);
      case WORDSHAPECHRIS1:
        return wordShapeChris1(inStr);
      case WORDSHAPEDAN2:
        return wordShapeDan2(inStr, knownLCWords);
      case WORDSHAPEDAN2USELC:
        return wordShapeDan2(inStr, knownLCWords);
      case WORDSHAPEDAN2BIO:
        return wordShapeDan2Bio(inStr, knownLCWords);
      case WORDSHAPEDAN2BIOUSELC:
        return wordShapeDan2Bio(inStr, knownLCWords);
      case WORDSHAPEJENNY1:
        return wordShapeJenny1(inStr, knownLCWords);
      case WORDSHAPEJENNY1USELC:
        return wordShapeJenny1(inStr, knownLCWords);
      case WORDSHAPECHRIS2:
        return wordShapeChris2(inStr, false, knownLCWords);
      case WORDSHAPECHRIS2USELC:
        return wordShapeChris2(inStr, false, knownLCWords);
      case WORDSHAPECHRIS3:
        return wordShapeChris2(inStr, true, knownLCWords);
      case WORDSHAPECHRIS3USELC:
        return wordShapeChris2(inStr, true, knownLCWords);
      case WORDSHAPECHRIS4:
        return wordShapeChris4(inStr, false, knownLCWords);
      case WORDSHAPEDIGITS:
        return wordShapeDigits(inStr);
      case WORDSHAPECHINESE:
        return wordShapeChinese(inStr);
      case WORDSHAPECLUSTER1:
        return wordShapeCluster1(inStr);
      default:
        throw new IllegalStateException("Bad WordShapeClassifier");
    }
  }

  /**
   * A fairly basic 5-way classifier, that notes digits, and upper
   * and lower case, mixed, and non-alphanumeric.
   *
   * @param s String to find word shape of
   * @return Its word shape: a 5 way classification
   */
  private static String wordShapeDan1(String s) {
    boolean digit = true;
    boolean upper = true;
    boolean lower = true;
    boolean mixed = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isDigit(c)) {
        digit = false;
      }
      if (!Character.isLowerCase(c)) {
        lower = false;
      }
      if (!Character.isUpperCase(c)) {
        upper = false;
      }
      if ((i == 0 && !Character.isUpperCase(c)) || (i >= 1 && !Character.isLowerCase(c))) {
        mixed = false;
      }
    }
    if (digit) {
      return "ALL-DIGITS";
    }
    if (upper) {
      return "ALL-UPPER";
    }
    if (lower) {
      return "ALL-LOWER";
    }
    if (mixed) {
      return "MIXED-CASE";
    }
    return "OTHER";
  }


  /**
   * A fine-grained word shape classifier, that equivalence classes
   * lower and upper case and digits, and collapses sequences of the
   * same type, but keeps all punctuation, etc. <p>
   * <i>Note:</i> We treat '_' as a lowercase letter, sort of like many
   * programming languages.  We do this because we use '_' joining of
   * tokens in some applications like RTE.
   *
   * @param s           The String whose shape is to be returned
   * @param knownLCWords If this is non-null and non-empty, mark words whose
   *                    lower case form is found in the
   *                    Collection of known lower case words
   * @return The word shape
   */
  private static String wordShapeDan2(String s, Collection<String> knownLCWords) {
    StringBuilder sb = new StringBuilder("WT-");
    char lastM = '~';
    boolean nonLetters = false;
    int len = s.length();
    for (int i = 0; i < len; i++) {
      char c = s.charAt(i);
      char m = c;
      if (Character.isDigit(c)) {
        m = 'd';
      } else if (Character.isLowerCase(c) || c == '_') {
        m = 'x';
      } else if (Character.isUpperCase(c)) {
        m = 'X';
      }
      if (m != 'x' && m != 'X') {
        nonLetters = true;
      }
      if (m != lastM) {
        sb.append(m);
      }
      lastM = m;
    }
    if (len <= 3) {
      sb.append(':').append(len);
    }
    if (knownLCWords != null) {
      if (!nonLetters && knownLCWords.contains(s.toLowerCase())) {
        sb.append('k');
      }
    }
    // System.err.println("wordShapeDan2: " + s + " became " + sb);
    return sb.toString();
  }

  private static String wordShapeJenny1(String s, Collection<String> knownLCWords) {
    StringBuilder sb = new StringBuilder("WT-");
    char lastM = '~';
    boolean nonLetters = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      char m = c;

      if (Character.isDigit(c)) {
        m = 'd';
      } else if (Character.isLowerCase(c)) {
        m = 'x';
      } else if (Character.isUpperCase(c)) {
        m = 'X';
      }

      for (String gr : greek) {
        if (s.startsWith(gr, i)) {
          m = 'g';
          i = i + gr.length() - 1;
          //System.out.println(s + "  ::  " + s.substring(i+1));
          break;
        }
      }

      if (m != 'x' && m != 'X') {
        nonLetters = true;
      }
      if (m != lastM) {
        sb.append(m);
      }
      lastM = m;


    }
    if (s.length() <= 3) {
      sb.append(':').append(s.length());
    }
    if (knownLCWords != null) {
      if ( ! nonLetters && knownLCWords.contains(s.toLowerCase())) {
        sb.append('k');
      }
    }
    //System.out.println(s+" became "+sb);
    return sb.toString();
  }


  /** Note: the optimizations in wordShapeChris2 would break if BOUNDARY_SIZE
   * was greater than the shortest greek word, so valid values are: 0, 1, 2, 3.
   */
  private static final int BOUNDARY_SIZE = 2;

  /**
   * This one picks up on Dan2 ideas, but seeks to make less distinctions
   * mid sequence by sorting for long words, but to maintain extra
   * distinctions for short words. It exactly preserves the character shape
   * of the first and last 2 (i.e., BOUNDARY_SIZE) characters and then
   * will record shapes that occur between them (perhaps only if they are
   * different)
   *
   * @param s The String to find the word shape of
   * @param omitIfInBoundary If true, character classes present in the
   *                         first or last two (i.e., BOUNDARY_SIZE) letters
   *                         of the word are not also registered
   *                         as classes that appear in the middle of the word.
   * @param knownLCWords If non-null and non-empty, tag with a "k" suffix words
   *                    that are in this list when lowercased (representing
   *                    that the word is "known" as a lowercase word).
   * @return A word shape for the word.
   */
  private static String wordShapeChris2(String s, boolean omitIfInBoundary, Collection<String> knownLCWords) {
    int len = s.length();
    if (len <= BOUNDARY_SIZE * 2) {
      return wordShapeChris2Short(s, len, knownLCWords);
    } else {
      return wordShapeChris2Long(s, omitIfInBoundary, len, knownLCWords);
    }
  }

  // Do the simple case of words <= BOUNDARY_SIZE * 2 (i.e., 4) with only 1 object allocation!
  private static String wordShapeChris2Short(String s, int len, Collection<String> knownLCWords) {
    int sbLen = (knownLCWords != null) ? len + 1: len;  // markKnownLC makes String 1 longer
    final StringBuilder sb = new StringBuilder(sbLen);
    boolean nonLetters = false;

    for (int i = 0; i < len; i++) {
      char c = s.charAt(i);
      char m = c;
      if (Character.isDigit(c)) {
        m = 'd';
      } else if (Character.isLowerCase(c)) {
        m = 'x';
      } else if (Character.isUpperCase(c) || Character.isTitleCase(c)) {
        m = 'X';
      }
      for (String gr : greek) {
        if (s.startsWith(gr, i)) {
          m = 'g';
          //System.out.println(s + "  ::  " + s.substring(i+1));
          i += gr.length() - 1;
          // System.out.println("Position skips to " + i);
          break;
        }
      }
      if (m != 'x' && m != 'X') {
        nonLetters = true;
      }

      sb.append(m);
    }

    if (knownLCWords != null) {
      if ( ! nonLetters && knownLCWords.contains(s.toLowerCase())) {
        sb.append('k');
      }
    }
    // System.out.println(s + " became " + sb);
    return sb.toString();
  }

  // introduce sizes and optional allocation to reduce memory churn demands;
  // this class could blow a lot of memory if used in a tight loop,
  // as the naive version allocates lots of kind of heavyweight objects
  // endSB should be of length BOUNDARY_SIZE
  // sb is maximally of size s.length() + 1, but is usually (much) shorter. The +1 might happen if markKnownLC is true and it applies
  // boundSet is maximally of size BOUNDARY_SIZE * 2 (and is often smaller)
  // seenSet is maximally of size s.length() - BOUNDARY_SIZE * 2, but might often be of size <= 4. But it has no initial size allocation
  // But we want the initial size to be greater than BOUNDARY_SIZE * 2 * (4/3) since the default loadfactor is 3/4.
  // That is, of size 6, which become 8, since HashMaps are powers of 2.  Still, it's half the size
  private static String wordShapeChris2Long(String s, boolean omitIfInBoundary, int len, Collection<String> knownLCWords) {
    final char[] beginChars = new char[BOUNDARY_SIZE];
    final char[] endChars = new char[BOUNDARY_SIZE];
    int beginUpto = 0;
    int endUpto = 0;
    final Set<Character> seenSet = new TreeSet<Character>();  // TreeSet guarantees stable ordering; has no size parameter

    boolean nonLetters = false;

    for (int i = 0; i < len; i++) {
      int iIncr = 0;
      char c = s.charAt(i);
      char m = c;
      if (Character.isDigit(c)) {
        m = 'd';
      } else if (Character.isLowerCase(c)) {
        m = 'x';
      } else if (Character.isUpperCase(c) || Character.isTitleCase(c)) {
        m = 'X';
      }
      for (String gr : greek) {
        if (s.startsWith(gr, i)) {
          m = 'g';
          //System.out.println(s + "  ::  " + s.substring(i+1));
          iIncr = gr.length() - 1;
          break;
        }
      }
      if (m != 'x' && m != 'X') {
        nonLetters = true;
      }

      if (i < BOUNDARY_SIZE) {
        beginChars[beginUpto++] = m;
      } else if (i < len - BOUNDARY_SIZE) {
        seenSet.add(Character.valueOf(m));
      } else {
        endChars[endUpto++] = m;
      }
      i += iIncr;
      // System.out.println("Position skips to " + i);
    }

    // Calculate size. This may be an upperbound, but is often correct
    int sbSize = beginUpto + endUpto + seenSet.size();
    if (knownLCWords != null) { sbSize++; }
    final StringBuilder sb = new StringBuilder(sbSize);
    // put in the beginning chars
    sb.append(beginChars, 0, beginUpto);
    // put in the stored ones sorted
    if (omitIfInBoundary) {
      for (Character chr : seenSet) {
        char ch = chr.charValue();
        boolean insert = true;
        for (int i = 0; i < beginUpto; i++) {
          if (beginChars[i] == ch) {
            insert = false;
            break;
          }
        }
        for (int i = 0; i < endUpto; i++) {
          if (endChars[i] == ch) {
            insert = false;
            break;
          }
        }
        if (insert) {
          sb.append(ch);
        }
      }
    } else {
      for (Character chr : seenSet) {
        sb.append(chr.charValue());
      }
    }
    // and add end ones
    sb.append(endChars, 0, endUpto);

    if (knownLCWords != null) {
      if (!nonLetters && knownLCWords.contains(s.toLowerCase())) {
        sb.append('k');
      }
    }
    // System.out.println(s + " became " + sb);
    return sb.toString();
  }


  private static char chris4equivalenceClass(final char c) {
    int type = Character.getType(c);
    if (Character.isDigit(c) || type == Character.LETTER_NUMBER
            || type == Character.OTHER_NUMBER
            || "一二三四五六七八九十零〇百千万亿兩○◯".indexOf(c) > 0) {
      // include Chinese numbers that are just of unicode type OTHER_LETTER (and a couple of round symbols often used (by mistake?) for zeroes)
      return 'd';
    } else if (c == '第') {
      return 'o'; // detect those Chinese ordinals!
    } else if (c == '年' || c == '月' || c == '日') { // || c == '号') {
      return 'D'; // Chinese date characters.
    } else if (Character.isLowerCase(c)) {
      return 'x';
    } else if (Character.isUpperCase(c) || Character.isTitleCase(c)) {
      return 'X';
    } else if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
      return 's';
    } else if (type == Character.OTHER_LETTER) {
      return 'c'; // Chinese characters, etc. without case
    } else if (type == Character.CURRENCY_SYMBOL) {
      return '$';
    } else if (type == Character.MATH_SYMBOL) {
      return '+';
    } else if (type == Character.OTHER_SYMBOL || c == '|') {
      return '|';
    } else if (type == Character.START_PUNCTUATION) {
      return '(';
    } else if (type == Character.END_PUNCTUATION) {
      return ')';
    } else if (type == Character.INITIAL_QUOTE_PUNCTUATION) {
      return '`';
    } else if (type == Character.FINAL_QUOTE_PUNCTUATION || c == '\'') {
      return '\'';
    } else if (c == '%') {
      return '%';
    } else if (type == Character.OTHER_PUNCTUATION) {
      return '.';
    } else if (type == Character.CONNECTOR_PUNCTUATION) {
      return '_';
    } else if (type == Character.DASH_PUNCTUATION) {
      return '-';
    } else {
      return 'q';
    }
  }

  public static String wordShapeChris4(String s) {
    return wordShapeChris4(s, false, null);
  }

  /**
   * This one picks up on Dan2 ideas, but seeks to make less distinctions
   * mid sequence by sorting for long words, but to maintain extra
   * distinctions for short words, by always recording the class of the
   * first and last two characters of the word.
   * Compared to chris2 on which it is based,
   * it uses more Unicode classes, and so collapses things like
   * punctuation more, and might work better with real unicode.
   *
   * @param s The String to find the word shape of
   * @param omitIfInBoundary If true, character classes present in the
   *                         first or last two (i.e., BOUNDARY_SIZE) letters
   *                         of the word are not also registered
   *                         as classes that appear in the middle of the word.
   * @param knownLCWords If non-null and non-empty, tag with a "k" suffix words
   *                    that are in this list when lowercased (representing
   *                    that the word is "known" as a lowercase word).
   * @return A word shape for the word.
   */
  private static String wordShapeChris4(String s, boolean omitIfInBoundary, Collection<String> knownLCWords) {
    int len = s.length();
    if (len <= BOUNDARY_SIZE * 2) {
      return wordShapeChris4Short(s, len, knownLCWords);
    } else {
      return wordShapeChris4Long(s, omitIfInBoundary, len, knownLCWords);
    }
  }

  // Do the simple case of words <= BOUNDARY_SIZE * 2 (i.e., 4) with only 1 object allocation!
  private static String wordShapeChris4Short(String s, int len, Collection<String> knownLCWords) {
    int sbLen = (knownLCWords != null) ? len + 1: len;  // markKnownLC makes String 1 longer
    final StringBuilder sb = new StringBuilder(sbLen);
    boolean nonLetters = false;

    for (int i = 0; i < len; i++) {
      char c = s.charAt(i);
      char m = chris4equivalenceClass(c);
      for (String gr : greek) {
        if (s.startsWith(gr, i)) {
          m = 'g';
          //System.out.println(s + "  ::  " + s.substring(i+1));
          i += gr.length() - 1;
          // System.out.println("Position skips to " + i);
          break;
        }
      }
      if (m != 'x' && m != 'X') {
        nonLetters = true;
      }

      sb.append(m);
    }

    if (knownLCWords != null) {
      if ( ! nonLetters && knownLCWords.contains(s.toLowerCase())) {
        sb.append('k');
      }
    }
    // System.out.println(s + " became " + sb);
    return sb.toString();
  }


  private static String wordShapeChris4Long(String s, boolean omitIfInBoundary, int len, Collection<String> knownLCWords) {
    StringBuilder sb = new StringBuilder(s.length() + 1);
    StringBuilder endSB = new StringBuilder(BOUNDARY_SIZE);
    Set<Character> boundSet = Generics.newHashSet(BOUNDARY_SIZE * 2);
    Set<Character> seenSet = new TreeSet<Character>();  // TreeSet guarantees stable ordering
    boolean nonLetters = false;
    for (int i = 0; i < len; i++) {
      char c = s.charAt(i);
      char m = chris4equivalenceClass(c);
      int iIncr = 0;
      for (String gr : greek) {
        if (s.startsWith(gr, i)) {
          m = 'g';
          iIncr = gr.length() - 1;
          //System.out.println(s + "  ::  " + s.substring(i+1));
          break;
        }
      }
      if (m != 'x' && m != 'X') {
        nonLetters = true;
      }

      if (i < BOUNDARY_SIZE) {
        sb.append(m);
        boundSet.add(Character.valueOf(m));
      } else if (i < len - BOUNDARY_SIZE) {
        seenSet.add(Character.valueOf(m));
      } else {
        boundSet.add(Character.valueOf(m));
        endSB.append(m);
      }
      // System.out.println("Position " + i + " --> " + m);
      i += iIncr;
    }
    // put in the stored ones sorted and add end ones
    for (Character chr : seenSet) {
      if (!omitIfInBoundary || !boundSet.contains(chr)) {
        char ch = chr.charValue();
        sb.append(ch);
      }
    }
    sb.append(endSB);

    if (knownLCWords != null) {
      if (!nonLetters && knownLCWords.contains(s.toLowerCase())) {
        sb.append('k');
      }
    }
    // System.out.println(s + " became " + sb);
    return sb.toString();
  }


  /**
   * Returns a fine-grained word shape classifier, that equivalence classes
   * lower and upper case and digits, and collapses sequences of the
   * same type, but keeps all punctuation.  This adds an extra recognizer
   * for a greek letter embedded in the String, which is useful for bio.
   */
  private static String wordShapeDan2Bio(String s, Collection<String> knownLCWords) {
    if (containsGreekLetter(s)) {
      return wordShapeDan2(s, knownLCWords) + "-GREEK";
    } else {
      return wordShapeDan2(s, knownLCWords);
    }
  }


  /** List of greek letters for bio.  We omit eta, mu, nu, xi, phi, chi, psi.
   *  Maybe should omit rho too, but it is used in bio "Rho kinase inhibitor".
   */
  private static final String[] greek = {"alpha", "beta", "gamma", "delta", "epsilon", "zeta", "theta", "iota", "kappa", "lambda", "omicron", "rho", "sigma", "tau", "upsilon", "omega"};
  private static final Pattern biogreek = Pattern.compile("alpha|beta|gamma|delta|epsilon|zeta|theta|iota|kappa|lambda|omicron|rho|sigma|tau|upsilon|omega", Pattern.CASE_INSENSITIVE);


  /**
   * Somewhat ad-hoc list of only greek letters that bio people use, partly
   * to avoid false positives on short ones.
   * @param s String to check for Greek
   * @return true iff there is a greek lette embedded somewhere in the String
   */
  private static boolean containsGreekLetter(String s) {
    Matcher m = biogreek.matcher(s);
    return m.find();
  }


  /** This one equivalence classes all strings into one of 24 semantically
   *  informed classes, somewhat similarly to the function specified in the
   *  BBN Nymble NER paper (Bikel et al. 1997).
   *  <p>
   *  Note that it regards caseless non-Latin letters as lowercase.
   *
   *  @param s String to word class
   *  @return The string's class
   */
  private static String wordShapeChris1(String s) {
    int length = s.length();
    if (length == 0) {
      return "SYMBOL"; // unclear if this is sensible, but it's what a length 0 String becomes....
    }

    boolean cardinal = false;
    boolean number = true;
    boolean seenDigit = false;
    boolean seenNonDigit = false;

    for (int i = 0; i < length; i++) {
      char ch = s.charAt(i);
      boolean digit = Character.isDigit(ch);
      if (digit) {
        seenDigit = true;
      } else {
        seenNonDigit = true;
      }
      // allow commas, decimals, and negative numbers
      digit = digit || ch == '.' || ch == ',' || (i == 0 && (ch == '-' || ch == '+'));
      if (!digit) {
        number = false;
      }
    }

    if ( ! seenDigit) {
      number = false;
    } else if ( ! seenNonDigit) {
      cardinal = true;
    }

    if (cardinal) {
      if (length < 4) {
        return "CARDINAL13";
      } else if (length == 4) {
        return "CARDINAL4";
      } else {
        return "CARDINAL5PLUS";
      }
    } else if (number) {
      return "NUMBER";
    }

    boolean seenLower = false;
    boolean seenUpper = false;
    boolean allCaps = true;
    boolean allLower = true;
    boolean initCap = false;
    boolean dash = false;
    boolean period = false;

    for (int i = 0; i < length; i++) {
      char ch = s.charAt(i);
      boolean up = Character.isUpperCase(ch);
      boolean let = Character.isLetter(ch);
      boolean tit = Character.isTitleCase(ch);
      if (ch == '-') {
        dash = true;
      } else if (ch == '.') {
        period = true;
      }

      if (tit) {
        seenUpper = true;
        allLower = false;
        seenLower = true;
        allCaps = false;
      } else if (up) {
        seenUpper = true;
        allLower = false;
      } else if (let) {
        seenLower = true;
        allCaps = false;
      }
      if (i == 0 && (up || tit)) {
        initCap = true;
      }
    }

    if (length == 2 && initCap && period) {
      return "ACRONYM1";
    } else if (seenUpper && allCaps && !seenDigit && period) {
      return "ACRONYM";
    } else if (seenDigit && dash && !seenUpper && !seenLower) {
      return "DIGIT-DASH";
    } else if (initCap && seenLower && seenDigit && dash) {
      return "CAPITALIZED-DIGIT-DASH";
    } else if (initCap && seenLower && seenDigit) {
      return "CAPITALIZED-DIGIT";
    } else if (initCap && seenLower && dash) {
      return "CAPITALIZED-DASH";
    } else if (initCap && seenLower) {
      return "CAPITALIZED";
    } else if (seenUpper && allCaps && seenDigit && dash) {
      return "ALLCAPS-DIGIT-DASH";
    } else if (seenUpper && allCaps && seenDigit) {
      return "ALLCAPS-DIGIT";
    } else if (seenUpper && allCaps && dash) {
      return "ALLCAPS";
    } else if (seenUpper && allCaps) {
      return "ALLCAPS";
    } else if (seenLower && allLower && seenDigit && dash) {
      return "LOWERCASE-DIGIT-DASH";
    } else if (seenLower && allLower && seenDigit) {
      return "LOWERCASE-DIGIT";
    } else if (seenLower && allLower && dash) {
      return "LOWERCASE-DASH";
    } else if (seenLower && allLower) {
      return "LOWERCASE";
    } else if (seenLower && seenDigit) {
      return "MIXEDCASE-DIGIT";
    } else if (seenLower) {
      return "MIXEDCASE";
    } else if (seenDigit) {
      return "SYMBOL-DIGIT";
    } else {
      return "SYMBOL";
    }
  }


  /**
   * Just collapses digits to 9 characters.
   * Does lazy copying of String.
   *
   * @param s String to find word shape of
   * @return The same string except digits are equivalence classed to 9.
   */
  private static String wordShapeDigits(final String s) {
    char[] outChars = null;

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isDigit(c)) {
        if (outChars == null) {
          outChars = s.toCharArray();
        }
        outChars[i] = '9';
      }
    }
    if (outChars == null) {
      // no digit found
      return s;
    } else {
      return new String(outChars);
    }
  }


  /**
   * Uses distributional similarity clusters for unknown words.  Except that
   * numbers are just turned into NUMBER.
   * This one uses ones from a fixed file that we've used for NER.
   *
   * @param s String to find word shape of
   * @return Its word shape
   */
  private static String wordShapeCluster1(String s) {
    boolean digit = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if ( ! (Character.isDigit(c) || c == '.' || c == ',' || (i == 0 && (c == '-' || c == '+')))) {
        digit = false;
      }
    }
    if (digit) {
      return "NUMBER";
    } else {
      String cluster = DistributionalClusters.cluster1.get(s);
      if (cluster == null) {
        cluster = "NULL";
      }
      return cluster;
    }
  }

  private static String wordShapeChinese(final String s) {
    return ChineseUtils.shapeOf(s, true, true);
  }


  private static class DistributionalClusters {

    private DistributionalClusters() {}

    public static Map<String,String> cluster1  = loadWordClusters("/u/nlp/data/pos_tags_are_useless/egw.bnc.200",
                                                           "alexClark");

    private static class LcMap<K,V> extends HashMap<K,V> {

      private static final long serialVersionUID = -457913281600751901L;

      @Override
      public V get(Object key) {
        return super.get(key.toString().toLowerCase());
      }
    }

    public static Map<String,String> loadWordClusters(String file, String format) {
      Timing.startDoing("Loading distsim lexicon from " + file);
      Map<String,String> lexicon = new LcMap<String, String>();
      if ("terryKoo".equals(format)) {
        for (String line : ObjectBank.getLineIterator(file)) {
          String[] bits = line.split("\\t");
          String word = bits[1];
          // for now, always lowercase, but should revisit this
          word = word.toLowerCase();
          String wordClass = bits[0];
          lexicon.put(word, wordClass);
        }
      } else {
        // "alexClark"
        for (String line : ObjectBank.getLineIterator(file)) {
          String[] bits = line.split("\\s+");
          String word = bits[0];
          // for now, always lowercase, but should revisit this
          word = word.toLowerCase();
          lexicon.put(word, bits[1]);
        }
      }
      Timing.endDoing();
      return lexicon;
    }

  }


  /**
   * Usage: <code>java edu.stanford.nlp.process.WordShapeClassifier
   * [-wordShape name] string+ </code><br>
   * where <code>name</code> is an argument to <code>lookupShaper</code>.
   * Known names have patterns along the lines of: dan[12](bio)?(UseLC)?,
   * jenny1(useLC)?, chris[1234](useLC)?, cluster1.
   * If you don't specify a word shape function, you get chris1.
   *
   * @param args Command-line arguments, as above.
   */
  public static void main(String[] args) {
    int i = 0;
    int classifierToUse = WORDSHAPECHRIS1;
    if (args.length == 0) {
      System.out.println("edu.stanford.nlp.process.WordShapeClassifier [-wordShape name] string+");
    } else if (args[0].charAt(0) == '-') {
      if (args[0].equals("-wordShape") && args.length >= 2) {
        classifierToUse = lookupShaper(args[1]);
        i += 2;
      } else {
        System.err.println("Unknown flag: " + args[0]);
        i++;
      }
    }

    for (; i < args.length; i++) {
      System.out.print(args[i] + ": ");
      System.out.println(wordShape(args[i], classifierToUse));
    }
  }

}
