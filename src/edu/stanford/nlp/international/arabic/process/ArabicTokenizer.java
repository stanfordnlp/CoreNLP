package edu.stanford.nlp.international.arabic.process;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Properties;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.*;
import edu.stanford.nlp.util.StringUtils;

/**
 * Tokenizer for UTF-8 Arabic. Buckwalter encoding is *not* supported.
 *
 * <p>
 * A single instance of an Arabic Tokenizer is not thread safe, as it
 * uses a non-threadsafe jflex object to do the processing.  Multiple
 * instances can be created safely, though.  A single instance of a
 * ArabicTokenizerFactory is also not thread safe, as it keeps its
 * options in a local variable.
 * </p>
 *
 * <p>
 * TODO(spenceg): Merge in rules from ibm tokenizer (v5).
 * TODO(spenceg): Add XML escaping
 * TODO(spenceg): When running from the command line, the tokenizer does not
 *   produce the correct number of newline-delimited lines for the ATB data
 *   sets.
 * </p>
 * @author Spence Green
 */
public class ArabicTokenizer<T extends HasWord> extends AbstractTokenizer<T> {

  // The underlying JFlex lexer
  private final ArabicLexer lexer;

  // Produces the normalization for parsing used in Green and Manning (2010)
  private static final Properties atbOptions = new Properties();
  static {
    String optionsStr = "normArDigits,normArPunc,normAlif,removeDiacritics,removeTatweel,removeQuranChars";
    String[] optionToks = optionsStr.split(",");
    for (String option : optionToks) {
      atbOptions.put(option, "true");
    }
  }

  public static ArabicTokenizer<CoreLabel> newArabicTokenizer(Reader r, Properties lexerProperties) {
    return new ArabicTokenizer<CoreLabel>(r, new CoreLabelTokenFactory(), lexerProperties);
  }

  public ArabicTokenizer(Reader r, LexedTokenFactory<T> tf, Properties lexerProperties) {
    lexer = new ArabicLexer(r, tf, lexerProperties);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T getNext() {
    try {
      T nextToken = null;
      // Depending on the orthographic normalization options,
      // some tokens can be obliterated. In this case, keep iterating
      // until we see a non-zero length token.
      do {
        nextToken = (T) lexer.next();
      } while (nextToken != null && nextToken.word().length() == 0);

      return nextToken;

    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static class ArabicTokenizerFactory<T extends HasWord> implements TokenizerFactory<T>, Serializable  {

    private static final long serialVersionUID = 946818805507187330L;

    protected final LexedTokenFactory<T> factory;

    protected Properties lexerProperties = new Properties();

    public static TokenizerFactory<CoreLabel> newTokenizerFactory() {
      return new ArabicTokenizerFactory<CoreLabel>(new CoreLabelTokenFactory());
    }

    private ArabicTokenizerFactory(LexedTokenFactory<T> factory) {
      this.factory = factory;
    }

    @Override
    public Iterator<T> getIterator(Reader r) {
      return getTokenizer(r);
    }

    @Override
    public Tokenizer<T> getTokenizer(Reader r) {
      return new ArabicTokenizer<T>(r, factory, lexerProperties);
    }

    /**
     * options: A comma-separated list of options
     */
    @Override
    public void setOptions(String options) {
      String[] optionList = options.split(",");
      for (String option : optionList) {
        lexerProperties.put(option, "true");
      }
    }

    @Override
    public Tokenizer<T> getTokenizer(Reader r, String extraOptions) {
      setOptions(extraOptions);
      return getTokenizer(r);
    }

  } // end static class ArabicTokenizerFactory

  public static TokenizerFactory<CoreLabel> factory() {
    return ArabicTokenizerFactory.newTokenizerFactory();
  }

  public static TokenizerFactory<CoreLabel> atbFactory() {
    TokenizerFactory<CoreLabel> tf = ArabicTokenizerFactory.newTokenizerFactory();
    for (String option : atbOptions.stringPropertyNames()) {
      tf.setOptions(option);
    }
    return tf;
  }

  /**
   * A fast, rule-based tokenizer for Modern Standard Arabic (UTF-8 encoding).
   * Performs punctuation splitting and light tokenization by default.
   * Orthographic normalization options are available, and can be enabled with
   * command line options.
   * <p>
   * Currently, this tokenizer does not do line splitting. It normalizes non-printing
   * line separators across platforms and prints the system default line splitter
   * to the output.
   * </p>
   * <p>
   * The following normalization options are provided:
   * <ul>
   * <li><code>useUTF8Ellipsis</code> : Replaces sequences of three or more full stops with \u2026</li>
   * <li><code>normArDigits</code> : Convert Arabic digits to ASCII equivalents</li>
   * <li><code>normArPunc</code> : Convert Arabic punctuation to ASCII equivalents</li>
   * <li><code>normAlif</code> : Change all alif forms to bare alif</li>
   * <li><code>normYa</code> : Map ya to alif maqsura</li>
   * <li><code>removeDiacritics</code> : Strip all diacritics</li>
   * <li><code>removeTatweel</code> : Strip tatweel elongation character</li>
   * <li><code>removeQuranChars</code> : Remove diacritics that appear in the Quran</li>
   * <li><code>removeProMarker</code> : Remove the ATB null pronoun marker</li>
   * <li><code>removeSegMarker</code> : Remove the ATB clitic segmentation marker</li>
   * <li><code>removeMorphMarker</code> : Remove the ATB morpheme boundary markers</li>
   * <li><code>removeLengthening</code> : Replace all sequences of three or more identical (non-period) characters with one copy</li>
   * <li><code>atbEscaping</code> : Replace left/right parentheses with ATB escape characters</li>
   * </ul>
   * </p>
   *
   * @param args
   */
  public static void main(String[] args) {
    if (args.length > 0 && args[0].contains("help")) {
      System.err.printf("Usage: java %s [OPTIONS] < file%n", ArabicTokenizer.class.getName());
      System.err.printf("%nOptions:%n");
      System.err.println("   -help : Print this message. See javadocs for all normalization options.");
      System.err.println("   -atb  : Tokenization for the parsing experiments in Green and Manning (2010)");
      System.exit(-1);
    }

    // Process normalization options
    final Properties tokenizerOptions = StringUtils.argsToProperties(args);
    final TokenizerFactory<CoreLabel> tf = tokenizerOptions.containsKey("atb") ?
        ArabicTokenizer.atbFactory() : ArabicTokenizer.factory();
    for (String option : tokenizerOptions.stringPropertyNames()) {
      tf.setOptions(option);
    }

    // Replace line separators with a token so that we can
    // count lines
    tf.setOptions("tokenizeNLs");

    // Read the file
    int nLines = 0;
    int nTokens = 0;
    try {
      final String encoding = "UTF-8";
      Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new InputStreamReader(System.in, encoding));
      boolean printSpace = false;
      while (tokenizer.hasNext()) {
        ++nTokens;
        String word = tokenizer.next().word();
        if (word.equals(ArabicLexer.NEWLINE_TOKEN)) {
          ++nLines;
          printSpace = false;
          System.out.println();
        } else {
          if (printSpace) System.out.print(" ");
          System.out.print(word);
          printSpace = true;
        }
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    System.err.printf("Done! Tokenized %d lines (%d tokens)%n", nLines, nTokens);
  }

}
