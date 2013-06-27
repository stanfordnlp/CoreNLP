package edu.stanford.nlp.international.french.process;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.LexedTokenFactory;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Tokenizer for raw French text. This tokenization scheme is a derivative
 * of PTB tokenization, but with extra rules for French elision and compounding.
 *
 * <p>
 * The tokenizer implicitly inserts segmentation markers by not normalizing
 * the apostrophe and hyphen. Detokenization can thus be performed by right-concatenating
 * apostrophes and left-concatenating hyphens.
 * </p>
 * <p>
 * A single instance of an French Tokenizer is not thread safe, as it
 * uses a non-threadsafe jflex object to do the processing.  Multiple
 * instances can be created safely, though.  A single instance of a
 * FrenchTokenizerFactory is also not thread safe, as it keeps its
 * options in a local variable.
 * </p>
 *
 * @author Spence Green
 */
public class FrenchTokenizer<T extends HasWord> extends AbstractTokenizer<T> {

  // The underlying JFlex lexer
  private final FrenchLexer lexer;

  // Produces the normalization for parsing used in Green and Manning (2010)
  private static final Properties ftbOptions = new Properties();
  static {
    // TODO: Add default options
    String optionsStr = "";
    String[] optionToks = optionsStr.split(",");
    for (String option : optionToks) {
      ftbOptions.put(option, "true");
    }
  }

  public static FrenchTokenizer<CoreLabel> newFrenchTokenizer(Reader r, Properties lexerProperties) {
    return new FrenchTokenizer<CoreLabel>(r, new CoreLabelTokenFactory(), lexerProperties);
  }

  public FrenchTokenizer(Reader r, LexedTokenFactory<T> tf, Properties lexerProperties) {
    lexer = new FrenchLexer(r, tf, lexerProperties);
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

  public static class FrenchTokenizerFactory<T extends HasWord> implements TokenizerFactory<T>, Serializable  {

    private static final long serialVersionUID = 946818805507187330L;

    protected final LexedTokenFactory<T> factory;

    protected Properties lexerProperties = new Properties();

    public static TokenizerFactory<CoreLabel> newTokenizerFactory() {
      return new FrenchTokenizerFactory<CoreLabel>(new CoreLabelTokenFactory());
    }

    private FrenchTokenizerFactory(LexedTokenFactory<T> factory) {
      this.factory = factory;
    }

    public Iterator<T> getIterator(Reader r) {
      return getTokenizer(r);
    }

    public Tokenizer<T> getTokenizer(Reader r) {
      return new FrenchTokenizer<T>(r, factory, lexerProperties);
    }

    /**
     * options: A comma-separated list of options
     */
    public void setOptions(String options) {
      String[] optionList = options.split(",");
      for (String option : optionList) {
        lexerProperties.put(option, "true");
      }
    }

    public Tokenizer<T> getTokenizer(Reader r, String extraOptions) {
      setOptions(extraOptions);
      return getTokenizer(r);
    }
  }

  public static TokenizerFactory<CoreLabel> factory() {
    return FrenchTokenizerFactory.newTokenizerFactory();
  }

  public static TokenizerFactory<CoreLabel> ftbFactory() {
    TokenizerFactory<CoreLabel> tf = FrenchTokenizerFactory.newTokenizerFactory();
    for (String option : ftbOptions.stringPropertyNames()) {
      tf.setOptions(option);
    }
    return tf;
  }

  private static String usage() {
    StringBuffer sb = new StringBuffer();
    String nl = System.getProperty("line.separator");
    sb.append(String.format("Usage: java %s [OPTIONS] < file%n%n", FrenchTokenizer.class.getName()));
    sb.append("Options:").append(nl);
    sb.append("   -help          : Print this message.").append(nl);
    sb.append("   -ftb           : Tokenization for experiments in Green et al. (2011).").append(nl);
    sb.append("   -lowerCase     : Apply lowercasing.").append(nl);
    sb.append("   -encoding type : Encoding format.").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argOptionDefs() {
    Map<String,Integer> argOptionDefs = Generics.newHashMap();
    argOptionDefs.put("help", 0);
    argOptionDefs.put("ftb", 0);
    argOptionDefs.put("lowerCase", 0);
    argOptionDefs.put("encoding", 1);
    return argOptionDefs;
  }

  /**
   * A fast, rule-based tokenizer for Modern Standard French.
   * Performs punctuation splitting and light tokenization by default.
   * <p>
   * Currently, this tokenizer does not do line splitting. It assumes that the input
   * file is delimited by the system line separator. The output will be equivalently
   * delimited.
   * </p>
   *
   * @param args
   */
  public static void main(String[] args) {
    final Properties options = StringUtils.argsToProperties(args, argOptionDefs());
    if (options.containsKey("help")) {
      System.err.println(usage());
      System.exit(-1);
    }

    // Process normalization options
    final TokenizerFactory<CoreLabel> tf = options.containsKey("ftb") ?
        FrenchTokenizer.ftbFactory() : FrenchTokenizer.factory();
    for (String option : options.stringPropertyNames()) {
      tf.setOptions(option);
    }

    // Normalize line separators so that we can count lines in the output
    tf.setOptions("tokenizeNLs");

    // Read the file from stdin
    int nLines = 0;
    int nTokens = 0;
    final String encoding = options.getProperty("encoding", "UTF-8");
    final boolean toLower = PropertiesUtils.getBool(options, "lowerCase", false);
    try {
      Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new InputStreamReader(System.in, encoding));
      boolean printSpace = false;
      while (tokenizer.hasNext()) {
        ++nTokens;
        String word = tokenizer.next().word();
        if (word.equals(FrenchLexer.NEWLINE_TOKEN)) {
          ++nLines;
          printSpace = false;
          System.out.println();
        } else {
          if (printSpace) System.out.print(" ");
          System.out.print(toLower ? word.toLowerCase(Locale.FRENCH) : word);
          printSpace = true;
        }
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    System.err.printf("Done! Tokenized %d lines (%d tokens)%n", nLines, nTokens);
  }
}
