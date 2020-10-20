package edu.stanford.nlp.international.arabic;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

import edu.stanford.nlp.international.arabic.pipeline.DefaultLexicalMapper;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.international.arabic.ATBTreeUtils;
import java.util.function.Function;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * This escaper is intended for use on flat input to be parsed by {@code LexicalizedParser}.
 * It performs these functions functions:
 * <ul>
 *  <li>Deletes the clitic markers inserted by the IBM segmenter ('#' and '+')
 *  <li>Deletes IBM classing for numbers
 *  <li>Replaces tokens that must be escaped with the appropriate LDC escape sequences
 *  <li>Applies the same orthographic normalization performed by {@link edu.stanford.nlp.trees.international.arabic.ArabicTreeNormalizer}
 *  <li>intern()'s strings
 * </ul>
 *
 * This class supports both Buckwalter and UTF-8 encoding.
 *
 * IMPORTANT: This class must implement {@code Function<List<HasWord>, List<HasWord>>}
 * in order to run with the parser.
 *
 * @author Christopher Manning
 * @author Spence Green
 */
public class IBMArabicEscaper implements Function<List<HasWord>, List<HasWord>>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(IBMArabicEscaper.class);

  private static final Pattern pEnt = Pattern.compile("\\$[a-z]+_\\((.*?)\\)");
  private boolean warnedEntityEscaping = false;
  private boolean warnedProcliticEnclitic = false;
  private final DefaultLexicalMapper lexMapper;
  private final boolean annotationsAndClassingOnly;

  public IBMArabicEscaper() {
    this(false);
  }

  public IBMArabicEscaper(boolean annoteAndClassOnly) {
    annotationsAndClassingOnly = annoteAndClassOnly;
    lexMapper = new DefaultLexicalMapper();
  }

  /**
   * Disable warnings generated when tokens are escaped.
   */
  public void disableWarnings() {
    warnedEntityEscaping = true;
    warnedProcliticEnclitic = true;
  }


  /**
   * Escapes a word. This method will *not* map a word to the null string.
   *
   * @return The escaped string
   */
  private String escapeString(String word) {

    String firstStage = stripAnnotationsAndClassing(word);

    String secondStage = ATBTreeUtils.escape(firstStage);
    if (secondStage.isEmpty()) {
      return firstStage;
    } else if (!firstStage.equals(secondStage)) {
      return secondStage;
    }

    String thirdStage = lexMapper.map(null, secondStage);
    if (thirdStage.isEmpty()) {
      return secondStage;
    }
    return thirdStage;

    //    Matcher mAM = pAM.matcher(w);
    //    if (mAM.find()) {
    //      if ( ! warnedNormalization) {
    //        log.info("IBMArabicEscaper Note: equivalence classing certain characters, such as Alef with madda/hamza, e.g., in: " + w);
    //        warnedNormalization = true;
    //      }
    //      // 'alif maqSuura mapped to yaa
    //      w = mAM.replaceAll("\u064A");
    //    }
    //    Matcher mYH = pYaaHamza.matcher(w);
    //    if (mYH.find()) {
    //      if ( ! warnedNormalization) {
    //        log.info("IBMArabicEscaper Note: equivalence classing certain characters, such as Alef with madda/hamza, e.g., in: " + w);
    //        warnedNormalization = true;
    //      }
    //      // replace yaa followed by hamza with hamza on kursi (yaa)
    //      w = mYH.replaceAll("\u0626");
    //    }
    //    w = StringUtils.tr(w, "\u060C\u061B\u061F\u066A\u066B\u066C\u066D\u06D4\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669\u0966\u0967\u0968\u0969\u096A\u096B\u096C\u096D\u096E\u096F\u2013\u2014\u0091\u0092\u2018\u2019\u0093\u0094\u201C\u201D",
    //    ",;%.,*.01234567890123456789--''''\"\"\"\"");
  }

  /**
   * Removes IBM clitic annotations and classing from a word.
   *
   * Note: We do not want to nullify a word, so we only perform these operations
   * on words of length 1 or more.
   *
   * @param word The unescaped word
   * @return The escaped word
   */
  private String stripAnnotationsAndClassing(String word) {
    String w = word;
    final int wLen = w.length();

    if (wLen > 1) {  // only for two or more letter words
      Matcher m2 = pEnt.matcher(w);
      if (m2.matches()) {
        if ( ! warnedEntityEscaping) {
          System.err.printf("%s: Removing IBM MT-style classing: %s --> %s\n", this.getClass().getName(), m2.group(0), m2.group(1));
          warnedEntityEscaping = true;
        }
        w = m2.replaceAll("$1");

      } else if (w.charAt(0) == '+') {
        if ( ! warnedProcliticEnclitic) {
          warnedProcliticEnclitic = true;
          System.err.printf("%s: Removing IBM MT-style proclitic/enclitic indicators\n",this.getClass().getName());
        }
        w = w.substring(1);

      } else if (w.charAt(wLen - 1) == '#') {
        if ( ! warnedProcliticEnclitic) {
          warnedProcliticEnclitic = true;
          System.err.printf("%s: Removing IBM MT-style proclitic/enclitic indicators\n",this.getClass().getName());
        }
        w = w.substring(0, wLen - 1);
      }
    }

    // Don't map a word to null
    if (w.isEmpty()) {
      return word;
    }
    return w;
  }


  /** Converts an input list of {@link HasWord} in IBM Arabic to
   *  LDC ATBv3 representation. The method safely copies the input object
   *  prior to escaping.
   *
   *  @param sentence A collection of type {@link edu.stanford.nlp.ling.Word}
   *  @return A copy of the input with each word escaped.
   *  @throws RuntimeException If a word is mapped to null
   */
  @Override
  public List<HasWord> apply(List<HasWord> sentence) {
    List<HasWord> newSentence = new ArrayList<>(sentence);

    for (HasWord wd : newSentence)
      wd.setWord(apply(wd.word()));

    return newSentence;
  }

  /**
   * Applies escaping to a single word. Interns the escaped string.
   *
   * @param w The word
   * @return The escaped word
   * @throws RuntimeException If a word is nullified (which is really bad for the parser and
   * for MT)
   */
  public String apply(String w) {

    String escapedWord = (annotationsAndClassingOnly) ?
        stripAnnotationsAndClassing(w) : escapeString(w);

    if (escapedWord.isEmpty()) {
      throw new RuntimeException(String.format("Word (%s) mapped to null", w));
    }

    return escapedWord.intern();
  }

  /** This main method preprocesses one-sentence-per-line input, making the
   *  same changes as the Function.  By default it writes the output to files
   *  with the same name as the files passed in on the command line but with
   *  {@code .sent} appended to their names.  If you give the flag
   *  {@code -f} then output is instead sent to stdout.  Input and output
   *  is always in UTF-8.
   *
   *  @param args A list of filenames.  The files must be UTF-8 encoded.
   *  @throws IOException If there are any issues
   */
  public static void main(String[] args) throws IOException {
    IBMArabicEscaper escaper = new IBMArabicEscaper();
    boolean printToStdout = false;
    for (String arg : args) {
      if ("-f".equals(arg)) {
        printToStdout = true;
        continue;
      }
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(arg), "UTF-8"));
      PrintWriter pw;
      if (printToStdout) {
        pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8")));
      } else {
        String outFile = arg + ".sent";
        pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8")));
      }
      for (String line ; (line = br.readLine()) != null; ) {
        String[] words = line.split("\\s+");
        for (int i = 0; i < words.length; i++) {
          String w = escaper.escapeString(words[i]);
          pw.print(w);
          if (i != words.length - 1) {
            pw.print(" ");
          }
        }
        pw.println();
      }
      br.close();
      pw.close();
    }
  }

}
