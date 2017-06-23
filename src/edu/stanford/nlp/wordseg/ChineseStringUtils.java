package edu.stanford.nlp.wordseg;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.stanford.nlp.trees.international.pennchinese.ChineseUtils.WHITE;
import static edu.stanford.nlp.trees.international.pennchinese.ChineseUtils.WHITEPLUS;

// TODO: ChineseStringUtils and ChineseUtils should be put somewhere common

/**
 * @author Pichuan Chang
 * @author Michel Galley
 * @author John Bauer
 * @author KellenSunderland (public domain contribution)
 */
public class ChineseStringUtils {

  private static final boolean DEBUG = false;
  private static final Pattern percentsPat = Pattern.compile(WHITE + "([\uff05%])" + WHITE);
  private static final String percentStr = WHITEPLUS + "([\uff05%])";
  private static final HKPostProcessor hkPostProcessor = new HKPostProcessor();
  private static final ASPostProcessor asPostProcessor = new ASPostProcessor();
  private static final BaseChinesePostProcessor basicPostsProcessor = new BaseChinesePostProcessor();
  private static final CTPPostProcessor ctpPostProcessor = new CTPPostProcessor();
  private static final PKPostProcessor pkPostProcessor = new PKPostProcessor();

  private ChineseStringUtils() {} // static methods

  public static boolean isLetterASCII(char c) {
    return c <= 127 && Character.isLetter(c);
  }

  public static String combineSegmentedSentence(List<CoreLabel> doc,
                                                SeqClassifierFlags flags) {
    // Hey all: Some of the code that was previously here for
    // whitespace normalization was a bit hackish as well as
    // obviously broken for some test cases. So...I went ahead and
    // re-wrote it.
    //
    // Also, putting everything into 'testContent', is a bit wasteful
    // memory wise. But, it's on my near-term todo list to
    // code something that's a bit more memory efficient.
    //
    // Finally, if these changes ended up breaking anything
    // just e-mail me (cerd@colorado.edu), and I'll try to fix it
    // asap  -cer (6/14/2006)

      /* Sun Oct  7 19:55:09 2007
         I'm actually not using "testContent" anymore.
         I think it's broken because the whole test file has been read over and over again,
         tand the testContentIdx has been set to 0 every time, while "doc" is moving
         line by line!!!!
         -pichuan
      */

    int testContentIdx = 0;
    StringBuilder ans = new StringBuilder(); // the actual output we will return
    StringBuilder unmod_ans = new StringBuilder();  // this is the original output from the CoreLabel
    StringBuilder unmod_normed_ans = new StringBuilder();  // this is the original output from the CoreLabel
    CoreLabel wi = null;
    for (Iterator<CoreLabel> wordIter = doc.iterator(); wordIter.hasNext();
         testContentIdx++) {
      CoreLabel pwi = wi;
      wi = wordIter.next();
      boolean originalWhiteSpace = "1".equals(wi.get(CoreAnnotations.SpaceBeforeAnnotation.class));

      //  if the CRF says "START" (segmented), and it's not the first word..
      if (wi.get(CoreAnnotations.AnswerAnnotation.class).equals("1") && !("0".equals(String.valueOf(wi.get(CoreAnnotations.PositionAnnotation.class))))) {
        // check if we need to preserve the "no space" between English
        // characters
        boolean seg = true; // since it's in the "1" condition.. default is to seg
        if (flags.keepEnglishWhitespaces) {
          if (testContentIdx > 0) {
            char prevChar = pwi.get(CoreAnnotations.OriginalCharAnnotation.class).charAt(0);
            char currChar = wi.get(CoreAnnotations.OriginalCharAnnotation.class).charAt(0);
            if (isLetterASCII(prevChar) && isLetterASCII(currChar)) {
              // keep the "non space" before wi
              if (!originalWhiteSpace) {
                seg = false;
              }
            }
          }
        }

        // if there was space and keepAllWhitespaces is true, restore it no matter what
        if (flags.keepAllWhitespaces && originalWhiteSpace) {
          seg = true;
        }
        if (seg) {
          if (originalWhiteSpace) {
            ans.append('\u1924'); // a pretty Limbu character which is later changed to a space
          } else {
            ans.append(' ');
          }
        }
        unmod_ans.append(' ');
        unmod_normed_ans.append(' ');
      } else {
        boolean seg = false; // since it's in the "0" condition.. default
        // Changed after conversation with Huihsin.
        //
        // Decided that all words consisting of English/ASCII characters
        // should be separated from the surrounding Chinese characters. -cer
          /* Sun Oct  7 22:14:46 2007 (pichuan)
             the comment above was from DanC.
             I changed the code but I think I'm doing the same thing here.
          */
        if (testContentIdx > 0) {
          char prevChar = pwi.get(CoreAnnotations.OriginalCharAnnotation.class).charAt(0);
          char currChar = wi.get(CoreAnnotations.OriginalCharAnnotation.class).charAt(0);
          if ((prevChar < (char) 128) != (currChar < (char) 128)) {
            if (ChineseUtils.isNumber(prevChar) && ChineseUtils.isNumber(currChar)) {
              // cdm: you would get here if you had an ASCII number next to a
              // Unihan range number.  Does that happen?  It presumably
              // shouldn't do any harm.... [cdm, oct 2007]
            } else if (flags.separateASCIIandRange) {
              seg = true;
            }
          }
        }

        if (flags.keepEnglishWhitespaces) {
          if (testContentIdx > 0) {
            char prevChar = pwi.get(CoreAnnotations.OriginalCharAnnotation.class).charAt(0);
            char currChar = wi.get(CoreAnnotations.OriginalCharAnnotation.class).charAt(0);
            if (isLetterASCII(prevChar) && isLetterASCII(currChar) ||
                    isLetterASCII(prevChar) && ChineseUtils.isNumber(currChar) ||
                    ChineseUtils.isNumber(prevChar) && isLetterASCII(currChar)) {
              // keep the "space" before wi
              if ("1".equals(wi.get(CoreAnnotations.SpaceBeforeAnnotation.class))) {
                seg = true;
              }
            }
          }
        }

        // if there was space and keepAllWhitespaces is true, restore it no matter what
        if (flags.keepAllWhitespaces) {
          if (!("0".equals(String.valueOf(wi.get(CoreAnnotations.PositionAnnotation.class))))
                  && "1".equals(wi.get(CoreAnnotations.SpaceBeforeAnnotation.class))) {
            seg = true;
          }
        }
        if (seg) {
          if (originalWhiteSpace) {
            ans.append('\u1924'); // a pretty Limbu character which is later changed to a space
          } else {
            ans.append(' ');
          }
        }
      }
      ans.append(wi.get(CoreAnnotations.OriginalCharAnnotation.class));
      unmod_ans.append(wi.get(CoreAnnotations.OriginalCharAnnotation.class));
      unmod_normed_ans.append(wi.get(CoreAnnotations.CharAnnotation.class));
    }
    String ansStr = ans.toString();
    if (flags.sighanPostProcessing) {
      if (!flags.keepAllWhitespaces) {
        // remove the Limbu char now, so it can be deleted in postprocessing
        ansStr = ansStr.replaceAll("\u1924", " ");
      }
      ansStr = postProcessingAnswer(ansStr, flags);
    }
    // definitely remove the Limbu char if it survived till now
    ansStr = ansStr.replaceAll("\u1924", " ");
    if (DEBUG) {
      EncodingPrintWriter.err.println("CLASSIFIER(normed): " + unmod_normed_ans, "UTF-8");
      EncodingPrintWriter.err.println("CLASSIFIER: " + unmod_ans, "UTF-8");
      EncodingPrintWriter.err.println("POSTPROCESSED: " + ans, "UTF-8");
    }
    return ansStr;
  }

  /**
   * post process the answer to be output
   * these post processing are not dependent on original input
   */
  private static String postProcessingAnswer(String ans, SeqClassifierFlags flags) {
    if (flags.useHk) {
      //logger.info("Using HK post processing.");
      return hkPostProcessor.postProcessingAnswer(ans);
    } else if (flags.useAs) {
      //logger.info("Using AS post processing.");
      return asPostProcessor.postProcessingAnswer(ans);
    } else if (flags.usePk) {
      //logger.info("Using PK post processing.");
      return pkPostProcessor.postProcessingAnswer(ans, flags.keepAllWhitespaces);
    } else if (flags.useMsr) {
      //logger.info("Using MSR post processing.");
      return basicPostsProcessor.postProcessingAnswer(ans);
    } else {
      //logger.info("Using CTB post processing.");
      return ctpPostProcessor.postProcessingAnswer(ans, flags.suppressMidDotPostprocessing);
    }
  }

  static class PKPostProcessor extends BaseChinesePostProcessor {

    @Override
    public String postProcessingAnswer(String ans) {
      return postProcessingAnswer(ans, true);
    }

    public String postProcessingAnswer(String ans, Boolean keepAllWhitespaces) {
      ans = separatePuncs(ans);
      if (!keepAllWhitespaces) {
      /* Note!! All the "digits" are actually extracted/learned from the training data!!!!
         They are not real "digits" knowledge.
         See /u/nlp/data/chinese-segmenter/Sighan2005/dict/wordlist for the list we extracted
      */
        String numPat = "[0-9\uff10-\uff19\uff0e\u00b7\u4e00\u5341\u767e]+";
        ans = processColons(ans, numPat);
        ans = processPercents(ans, numPat);
        ans = processDots(ans, numPat);
        ans = processCommas(ans);

      /* "\u2014\u2014\u2014" and "\u2026\u2026" should be together */

        String[] puncPatterns = {"\u2014" + WHITE + "\u2014" + WHITE + "\u2014", "\u2026" + WHITE + "\u2026"};
        String[] correctPunc = {"\u2014\u2014\u2014", "\u2026\u2026"};

        for (int i = 0; i < puncPatterns.length; i++) {
          Pattern p = patternMap.computeIfAbsent(WHITE + puncPatterns[i] + WHITE, s -> Pattern.compile(s));
          Matcher m = p.matcher(ans);
          ans = m.replaceAll(" " + correctPunc[i] + " ");
        }
      }
      ans = ans.trim();

      return ans;
    }
  }

  static class CTPPostProcessor extends BaseChinesePostProcessor {

    public CTPPostProcessor() {
      puncs = new Character[]{'\u3001', '\u3002', '\u3003', '\u3008', '\u3009', '\u300a', '\u300b',
              '\u300c', '\u300d', '\u300e', '\u300f', '\u3010', '\u3011', '\u3014', '\u3015',
              '\u0028', '\u0029', '\u0022', '\u003c', '\u003e'};
    }

    @Override
    public String postProcessingAnswer(String ans) {
      return postProcessingAnswer(ans, true);
    }

    public String postProcessingAnswer(String ans, Boolean suppressMidDotPostprocessing) {
      String numPat = "[0-9\uff10-\uff19]+";
      ans = separatePuncs(ans);
      if (!suppressMidDotPostprocessing) {
        ans = gluePunc('\u30fb', ans); // this is a 'connector' - the katakana midDot char
      }
      ans = processColons(ans, numPat);
      ans = processPercents(ans, numPat);
      ans = processDots(ans, numPat);
      ans = processCommas(ans);
      return ans.trim();
    }
  }

  static class ASPostProcessor extends BaseChinesePostProcessor {

    @Override
    public String postProcessingAnswer(String ans) {
      ans = separatePuncs(ans);

      /* Note!! All the "digits" are actually extracted/learned from the training data!!!!
       They are not real "digits" knowledge.
       See /u/nlp/data/chinese-segmenter/Sighan2005/dict/wordlist for the list we extracted
      */
      String numPat = "[\uff10-\uff19\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343]+";

      ans = processColons(ans, numPat);
      ans = processPercents(ans, numPat);
      ans = processDots(ans, numPat);
      ans = processCommas(ans);

      return ans;
    }
  }


  static class HKPostProcessor extends BaseChinesePostProcessor {

    public HKPostProcessor() {
      puncs = new Character[]{'\u3001', '\u3002', '\u3003', '\u3008', '\u3009', '\u300a',
              '\u300b', '\u300c', '\u300d', '\u300e', '\u300f', '\u3010', '\u3011', '\u3014',
              '\u3015', '\u2103'};
    }

    @Override
    public String postProcessingAnswer(String ans) {
      ans = separatePuncs(ans);

      /* Note!! All the "digits" are actually extracted/learned from the training data!!!!
         They are not real "digits" knowledge.
         See /u/nlp/data/chinese-segmenter/Sighan2005/dict/wordlist for the list we extracted
      */
      String numPat = "[0-9]+";
      ans = processColons(ans, numPat);


      /* "\u2014\u2014\u2014" and "\u2026\u2026" should be together */

      String[] puncPatterns = {"\u2014" + WHITE + "\u2014" + WHITE + "\u2014", "\u2026" + WHITE +
              "\u2026"};
      String[] correctPunc = {"\u2014\u2014\u2014", "\u2026\u2026"};

      for (int i = 0; i < puncPatterns.length; i++) {
        Pattern p = patternMap.computeIfAbsent(WHITE + puncPatterns[i] + WHITE, (s) -> Pattern.compile(s));
        Matcher m = p.matcher(ans);
        ans = m.replaceAll(" " + correctPunc[i] + " ");
      }

      return ans.trim();
    }
  }

  static class BaseChinesePostProcessor {

    protected static final ConcurrentHashMap<String, Pattern> patternMap = new ConcurrentHashMap<>();
    protected Character[] puncs;
    private Pattern[] colonsPat = null;
    private final Character[] colons = {'\ufe55', ':', '\uff1a'};
    private Pattern percentsWhitePat; // = null;
    private Pattern[] colonsWhitePat = null;

    public BaseChinesePostProcessor() {
      puncs = new Character[] {'\u3001', '\u3002', '\u3003', '\u3008', '\u3009', '\u300a', '\u300b',
              '\u300c', '\u300d', '\u300e', '\u300f', '\u3010', '\u3011', '\u3014', '\u3015'};
    }

    public String postProcessingAnswer(String ans) {
      return separatePuncs(ans);
    }

    /* make sure some punctuations will only appeared as one word (segmented from others). */
    /* These punctuations are derived directly from the training set. */
    String separatePuncs(String ans) {
      Pattern[] puncsPat = compilePunctuationPatterns();

      for (int i = 0; i < puncsPat.length; i++) {
        Pattern p = puncsPat[i];
        Character punc = puncs[i];
        Matcher m = p.matcher(ans);
        ans = m.replaceAll(" " + punc + " ");
      }
      return ans.trim();
    }

    private Pattern[] compilePunctuationPatterns() {
      Pattern[] puncsPat = new Pattern[puncs.length];
      for (int i = 0; i < puncs.length; i++) {
        Character punc = puncs[i];
        puncsPat[i] = patternMap.computeIfAbsent(getEscapedPuncPattern(punc), (s) -> Pattern.compile(s));
      }
      return puncsPat;
    }

    private static String getEscapedPuncPattern(Character punc) {
      String pattern;
      if (punc == '(' || punc == ')') { // escape
        pattern = WHITE + "\\" + punc + WHITE;
      } else {
        pattern = WHITE + punc + WHITE;
      }
      return pattern;
    }

    protected String processColons(String ans, String numPat) {
    /*
     ':' 1. if "5:6" then put together
         2. if others, separate ':' and others
         *** Note!! All the "digits" are actually extracted/learned from the training data!!!!
             They are not real "digits" knowledge.
         *** See /u/nlp/data/chinese-segmenter/Sighan2005/dict/wordlist for the list we extracted.
    */

      // first , just separate all ':'
      compileColonPatterns();

      for (int i = 0; i < colons.length; i++) {
        Character colon = colons[i];
        Pattern p = colonsPat[i];
        Matcher m = p.matcher(ans);
        ans = m.replaceAll(" " + colon + " ");
      }

      compileColonsWhitePatterns(numPat);
      // second , combine "5:6" patterns
      for (int i = 0; i < colons.length; i++) {
        Character colon = colons[i];
        Pattern p = colonsWhitePat[i];
        Matcher m = p.matcher(ans);
        while (m.find()) {
          ans = m.replaceAll("$1" + colon + "$2");
          m = p.matcher(ans);
        }
      }
      ans = ans.trim();
      return ans;
    }

    private synchronized void compileColonsWhitePatterns(String numPat) {
      if (colonsWhitePat == null) {
        colonsWhitePat = new Pattern[colons.length];
        for (int i = 0; i < colons.length; i++) {
          Character colon = colons[i];
          String pattern = "(" + numPat + ")" + WHITEPLUS + colon + WHITEPLUS + "(" + numPat + ")";
          colonsWhitePat[i] = patternMap.computeIfAbsent(pattern, (s) -> Pattern.compile(s));
        }
      }
    }

    private synchronized void compileColonPatterns() {
      if (colonsPat == null) {
        colonsPat = new Pattern[colons.length];
        for (int i = 0; i < colons.length; i++) {
          Character colon = colons[i];
          colonsPat[i] = patternMap.computeIfAbsent(WHITE + colon + WHITE, (s) -> Pattern.compile(s));
        }
      }
    }

    protected String processPercents(String ans, String numPat) {
      //  1. if "6%" then put together
      //  2. if others, separate '%' and others
      // logger.info("Process percents called!");
      // first , just separate all '%'
      Matcher m = percentsPat.matcher(ans);
      ans = m.replaceAll(" $1 ");

      // second , combine "6%" patterns

      percentsWhitePat = patternMap.computeIfAbsent("(" + numPat + ")" + percentStr, (s) -> Pattern.compile(s));
      Matcher m2 = percentsWhitePat.matcher(ans);
      ans = m2.replaceAll("$1$2");
      ans = ans.trim();
      return ans;
    }

    protected static String processDots(String ans, String numPat) {
    /* all "\d\.\d" patterns */
      String dots = "[\ufe52\u2027\uff0e.]";
      Pattern p = patternMap.computeIfAbsent("(" + numPat + ")" + WHITEPLUS + "(" + dots + ")" + WHITEPLUS +
              "(" + numPat + ")", s -> Pattern.compile(s));
      Matcher m = p.matcher(ans);
      while (m.find()) {
        ans = m.replaceAll("$1$2$3");
        m = p.matcher(ans);
      }

      p = patternMap.computeIfAbsent("(" + numPat + ")(" + dots + ")" + WHITEPLUS + "(" + numPat
              + ")", s -> Pattern.compile(s));
      m = p.matcher(ans);
      while (m.find()) {
        ans = m.replaceAll("$1$2$3");
        m = p.matcher(ans);
      }

      p = patternMap.computeIfAbsent("(" + numPat + ")" + WHITEPLUS + "(" + dots + ")(" + numPat
              + ")", s -> Pattern.compile(s));
      m = p.matcher(ans);
      while (m.find()) {
        ans = m.replaceAll("$1$2$3");
        m = p.matcher(ans);
      }

      ans = ans.trim();
      return ans;
    }

    /**
     * The one extant use of this method is to connect a U+30FB (Katakana midDot
     * with preceding and following non-space characters (in CTB
     * postprocessing). I would hypothesize that if mid dot chars were correctly
     * recognized in shape contexts, then this would be unnecessary [cdm 2007].
     * Also, note that IBM GALE normalization seems to produce U+30FB and not
     * U+00B7.
     *
     * @param punc character to be joined to surrounding chars
     * @param ans  Input string which may or may not contain punc
     * @return String with spaces removed between any instance of punc and
     * surrounding chars.
     */
    protected static String gluePunc(Character punc, String ans) {
      Pattern p = patternMap.computeIfAbsent(WHITE + punc, s -> Pattern.compile(s));
      Matcher m = p.matcher(ans);
      ans = m.replaceAll(String.valueOf(punc));
      p = patternMap.computeIfAbsent(punc + WHITE, s -> Pattern.compile(s));
      m = p.matcher(ans);
      ans = m.replaceAll(String.valueOf(punc));
      ans = ans.trim();
      return ans;
    }

    protected static String processCommas(String ans) {
      String numPat = "[0-9\uff10-\uff19]";
      String nonNumPat = "[^0-9\uff10-\uff19]";

      /* all "\d\.\d" patterns */
      String commas = ",";

      ans = ans.replaceAll(",", " , ");
      ans = ans.replaceAll("  ", " ");
      if (DEBUG) EncodingPrintWriter.err.println("ANS (before comma norm): " + ans, "UTF-8");
      Pattern p = patternMap.computeIfAbsent("(" + numPat + ")" + WHITE + "(" + commas + ")" +
              WHITE + "(" + numPat + "{3}" + nonNumPat + ")", s -> Pattern.compile(s));
      Matcher m = p.matcher(ans);
      if (m.find()) {
        ans = m.replaceAll("$1$2$3");
      }

      ans = ans.trim();
      return ans;
    }
  }

}
