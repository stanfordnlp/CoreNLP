package edu.stanford.nlp.international.french.process; 

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.CoreAnnotations.ParentAnnotation;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.LexedTokenFactory;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.WordTokenFactory;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Tokenizer for raw French text. This tokenization scheme is a derivative
 * of PTB tokenization, but with extra rules for French elision and compounding.
 *
 * <p>
 * The tokenizer implicitly inserts segmentation markers by not normalizing
 * the apostrophe and hyphen. Detokenization can thus be performed by right-concatenating
 * apostrophes and left-concatenating hyphens.
 *
 * <p>
 * A single instance of an French Tokenizer is not thread safe, as it
 * uses a non-threadsafe JFlex object to do the processing.  Multiple
 * instances can be created safely, though.  A single instance of a
 * FrenchTokenizerFactory is also not thread safe, as it keeps its
 * options in a local variable.
 *
 * @author Spence Green
 */
public class FrenchTokenizer<T extends HasWord> extends AbstractTokenizer<T>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(FrenchTokenizer.class);

  // The underlying JFlex lexer
  private final FrenchLexer lexer;

  // Internal fields compound and contraction splitting
  private static final String SPLIT_COMPOUNDS_OPTION = "splitCompounds";
  private static final String SPLIT_CONTRACTIONS_OPTION = "splitContractions";

  private final boolean splitCompounds;
  private final boolean splitContractions;
  private List<CoreLabel> compoundBuffer;

  // Produces the tokenization for parsing used by Green, de Marneffe, and Manning (2011)
  public static final String FTB_OPTIONS = "ellipses=ascii,normalizeParentheses=true,dashes=not_cp1252," +
    SPLIT_CONTRACTIONS_OPTION + "=true," + SPLIT_COMPOUNDS_OPTION + "=true";

  // Official pipeline default settings for French
  public static final String DEFAULT_OPTIONS =
      "invertible,splitCompounds=false,splitContractions=false,quotes=ORIGINAL";

  /**
   * Constructor.
   *
   * @param r
   * @param tf
   * @param lexerProperties
   * @param splitCompounds
   * @param splitContractions turn auxquelles into à lesquelles for example
   */
  public FrenchTokenizer(Reader r, LexedTokenFactory<T> tf, Properties lexerProperties,
                         boolean splitCompounds, boolean splitContractions) {
    lexer = new FrenchLexer(r, tf, lexerProperties);
    this.splitCompounds = splitCompounds;
    this.splitContractions = splitContractions;
    if (splitCompounds || splitContractions) compoundBuffer = Generics.newLinkedList();
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
        nextToken = ((splitContractions || splitCompounds) && compoundBuffer.size() > 0) ?
            (T) compoundBuffer.remove(0) :
              (T) lexer.next();
      } while (nextToken != null && nextToken.word().length() == 0);

      // Check for compounds to split
      if (splitCompounds && nextToken instanceof CoreLabel) {
        CoreLabel cl = (CoreLabel) nextToken;
        if (cl.containsKey(ParentAnnotation.class) && cl.get(ParentAnnotation.class).equals(FrenchLexer.COMPOUND_ANNOTATION)) {
          nextToken = (T) processCompound(cl);
        }
      }

      // Check for contractions to split
      if (splitContractions && nextToken instanceof CoreLabel) {
        CoreLabel cl = (CoreLabel) nextToken;
        if (cl.containsKey(ParentAnnotation.class) && cl.get(ParentAnnotation.class).equals(FrenchLexer.CONTR_ANNOTATION)) {
          nextToken = (T) processContraction(cl);
        }
      }

      return nextToken;

    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /** Copies the CoreLabel cl with the new word part */
  private static CoreLabel copyCoreLabel(CoreLabel cl, String part, int beginPosition, int endPosition) {
    CoreLabel newLabel = new CoreLabel(cl);
    newLabel.setWord(part);
    newLabel.setValue(part);
    newLabel.setBeginPosition(beginPosition);
    newLabel.setEndPosition(endPosition);
    newLabel.set(OriginalTextAnnotation.class, part);
    return newLabel;
  }

  private static CoreLabel copyCoreLabel(CoreLabel cl, String part, int beginPosition) {
    return copyCoreLabel(cl, part, beginPosition, beginPosition + part.length());
  }

  /**
   * Splits a compound marked by the lexer.
   */
  private CoreLabel processCompound(CoreLabel cl) {
    cl.remove(ParentAnnotation.class);
    String[] parts = cl.word().replaceAll("-", " - ").split("\\s+");
    for (String part : parts) {
      CoreLabel newLabel = new CoreLabel(cl);
      newLabel.setWord(part);
      newLabel.setValue(part);
      newLabel.set(OriginalTextAnnotation.class, part);
      compoundBuffer.add(newLabel);
    }
    return compoundBuffer.remove(0);
  }

  /**
   * Splits a contraction marked by the lexer.
   * <br>
   * au =&gt; a + u =&gt; à + le
   * aux =&gt; a + ux =&gt; à + les
   * du =&gt; d + u =&gt; de + le
   * <br>
   * des is handled by a special classifier in corenlp.  TODO: handle it here as well
   */
  private CoreLabel processContraction(CoreLabel cl) {
    cl.remove(ParentAnnotation.class);
    String word = cl.word();
    String first;
    String second;
    int secondOffset = 0, secondLength = 0;

    String lowered = word.toLowerCase();
    switch (lowered) {
      case "au":
        first = "à";
        second = "le";
        secondOffset = 1;
        secondLength = 1;
        break;
      case "aux":
        first = "à";
        second = "les";
        secondOffset = 1;
        secondLength = 2;
        break;
      case "du":
        first = "de";
        second = "le";
        secondOffset = 1;
        secondLength = 1;
        break;
      default:
        throw new IllegalArgumentException("Invalid contraction provided to processContraction");
    }

    int secondStart = cl.beginPosition() + secondOffset;
    int secondEnd = secondStart + secondLength;
    compoundBuffer.add(copyCoreLabel(cl, second, secondStart, secondEnd));
    return copyCoreLabel(cl, first, cl.beginPosition(), secondStart);
  }


  /**
   * A factory for French tokenizer instances.
   *
   * @author Spence Green
   */
  public static class FrenchTokenizerFactory<T extends HasWord> implements TokenizerFactory<T>, Serializable  {

    private static final long serialVersionUID = 946818805507187330L;

    protected final LexedTokenFactory<T> factory;
    protected Properties lexerProperties = new Properties();
    protected boolean splitCompoundOption = false;
    protected boolean splitContractionOption = true;

    public static TokenizerFactory<CoreLabel> newTokenizerFactory() {
      return new FrenchTokenizerFactory<>(new CoreLabelTokenFactory(), DEFAULT_OPTIONS);
    }

    /**
     * todo [cdm 2013]: But we should change it to a method that can return any kind of Label and return CoreLabel here
     *
     * @param options A String of options
     * @return A TokenizerFactory that returns Word objects
     */
    public static TokenizerFactory<Word> newWordTokenizerFactory(String options) {
      return new FrenchTokenizerFactory<>(new WordTokenFactory(), options);
    }


    private FrenchTokenizerFactory(LexedTokenFactory<T> factory) {
      this.factory = factory;
    }

    private FrenchTokenizerFactory(LexedTokenFactory<T> factory, String options) {
      this(factory);
      setOptions(options);
    }

    @Override
    public Iterator<T> getIterator(Reader r) {
      return getTokenizer(r);
    }

    @Override
    public Tokenizer<T> getTokenizer(Reader r) {
      return new FrenchTokenizer<>(r, factory, lexerProperties,
          splitCompoundOption, splitContractionOption);
    }

    /**
     * Set underlying tokenizer options.
     *
     * @param options A comma-separated list of options
     */
    @Override
    public void setOptions(String options) {
      String[] optionList = options.split(",");
      for (String option : optionList) {
        String[] fields = option.split("=");
        if (fields.length == 1) {
          if (fields[0].equals(SPLIT_COMPOUNDS_OPTION)) {
            splitCompoundOption = true;
          } else if (fields[0].equals(SPLIT_CONTRACTIONS_OPTION)) {
            splitContractionOption = true;
          } else {
            lexerProperties.setProperty(option, "true");
          }

        } else if (fields.length == 2) {
          if (fields[0].equals(SPLIT_COMPOUNDS_OPTION)) {
            splitCompoundOption = Boolean.valueOf(fields[1]);
          } else if (fields[0].equals(SPLIT_CONTRACTIONS_OPTION)) {
            splitContractionOption = Boolean.valueOf(fields[1]);
          } else {
            lexerProperties.setProperty(fields[0], fields[1]);
          }

        } else {
          System.err.printf("%s: Bad option %s%n", this.getClass().getName(), option);
        }
      }
    }

    @Override
    public Tokenizer<T> getTokenizer(Reader r, String extraOptions) {
      setOptions(extraOptions);
      return getTokenizer(r);
    }

  } // end static class FrenchTokenizerFactory


  /**
   * Returns a factory for FrenchTokenizer. THIS IS NEEDED FOR CREATION BY REFLECTION.
   */
  public static TokenizerFactory<CoreLabel> factory() {
    return FrenchTokenizerFactory.newTokenizerFactory();
  }

  public static <T extends HasWord> TokenizerFactory<T> factory(LexedTokenFactory<T> factory,
                                                                String options) {
    return new FrenchTokenizerFactory<>(factory, options);
  }

  /**
   * Returns a factory for FrenchTokenizer that replicates the tokenization of
   * Green, de Marneffe, and Manning (2011).
   */
  public static TokenizerFactory<CoreLabel> ftbFactory() {
    TokenizerFactory<CoreLabel> tf = FrenchTokenizerFactory.newTokenizerFactory();
    tf.setOptions(FTB_OPTIONS);
    return tf;
  }

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.getProperty("line.separator");
    sb.append(String.format("Usage: java %s [OPTIONS] < file%n%n", FrenchTokenizer.class.getName()));
    sb.append("Options:").append(nl);
    sb.append("   -help          : Print this message.").append(nl);
    sb.append("   -ftb           : Tokenization for experiments in Green et al. (2011).").append(nl);
    sb.append("   -lowerCase     : Apply lowercasing.").append(nl);
    sb.append("   -encoding type : Encoding format.").append(nl);
    sb.append("   -options str   : Orthographic options (see FrenchLexer.java)").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argOptionDefs() {
    Map<String,Integer> argOptionDefs = Generics.newHashMap();
    argOptionDefs.put("help", 0);
    argOptionDefs.put("ftb", 0);
    argOptionDefs.put("lowerCase", 0);
    argOptionDefs.put("encoding", 1);
    argOptionDefs.put("options", 1);
    return argOptionDefs;
  }

  /**
   * A fast, rule-based tokenizer for Modern Standard French.
   * Performs punctuation splitting and light tokenization by default.
   *
   * <p>
   * Currently, this tokenizer does not do line splitting. It assumes that the input
   * file is delimited by the system line separator. The output will be equivalently
   * delimited.

   *
   * @param args
   */
  public static void main(String[] args) {
    final Properties options = StringUtils.argsToProperties(args, argOptionDefs());
    if (options.containsKey("help")) {
      log.info(usage());
      return;
    }

    // Lexer options
    final TokenizerFactory<CoreLabel> tf = options.containsKey("ftb") ?
        FrenchTokenizer.ftbFactory() : FrenchTokenizer.factory();
    String orthoOptions = options.getProperty("options", "");
    // When called from this main method, split on newline. No options for
    // more granular sentence splitting.
    orthoOptions = orthoOptions.isEmpty() ? "tokenizeNLs" : orthoOptions + ",tokenizeNLs";
    tf.setOptions(orthoOptions);

    // Other options
    final String encoding = options.getProperty("encoding", "UTF-8");
    final boolean toLower = PropertiesUtils.getBool(options, "lowerCase", false);

    // Read the file from stdin
    int nLines = 0;
    int nTokens = 0;
    final long startTime = System.nanoTime();
    try {
      Tokenizer<CoreLabel> tokenizer = tf.getTokenizer(new InputStreamReader(System.in, encoding));
      boolean printSpace = false;
      while (tokenizer.hasNext()) {
        ++nTokens;
        String word = tokenizer.next().word();
        if (word.equals(AbstractTokenizer.NEWLINE_TOKEN)) {
          ++nLines;
          printSpace = false;
          System.out.println();
        } else {
          if (printSpace) System.out.print(" ");
          String outputToken = toLower ? word.toLowerCase(Locale.FRENCH) : word;
          System.out.print(outputToken);
          printSpace = true;
        }
      }
    } catch (UnsupportedEncodingException e) {
      log.error(e);
    }
    long elapsedTime = System.nanoTime() - startTime;
    double linesPerSec = (double) nLines / (elapsedTime / 1e9);
    System.err.printf("Done! Tokenized %d lines (%d tokens) at %.2f lines/sec%n", nLines, nTokens, linesPerSec);
  } // end main()

}
