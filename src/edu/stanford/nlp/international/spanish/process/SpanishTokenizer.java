package edu.stanford.nlp.international.spanish.process;
import edu.stanford.nlp.util.logging.Redwood;

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
import java.util.regex.Pattern;


import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalTextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.CoreAnnotations.ParentAnnotation;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.LexedTokenFactory;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.international.spanish.SpanishVerbStripper;

/**
 * Tokenizer for raw Spanish text. This tokenization scheme is a derivative
 * of PTB tokenization, but with extra rules for Spanish contractions and
 * assimilations. It is based heavily on the FrenchTokenizer.
 * <p>
 * The tokenizer tokenizes according to the modified AnCora corpus tokenization
 * standards, so the rules are a little different from PTB.
 * </p>
 * <p>
 * A single instance of a Spanish Tokenizer is not thread safe, as it
 * uses a non-threadsafe JFlex object to do the processing.  Multiple
 * instances can be created safely, though.  A single instance of a
 * SpanishTokenizerFactory is also not thread safe, as it keeps its
 * options in a local variable.
 * </p>
 *
 * @author Ishita Prasad
 */
public class SpanishTokenizer<T extends HasWord> extends AbstractTokenizer<T>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SpanishTokenizer.class);

  // The underlying JFlex lexer
  private final SpanishLexer lexer;

  // Internal fields compound splitting
  private final boolean splitCompounds;
  private final boolean splitVerbs;
  private final boolean splitContractions;
  private final boolean splitAny;
  private List<CoreLabel> compoundBuffer;
  private SpanishVerbStripper verbStripper;

  // Produces the tokenization for parsing used by AnCora (fixed) */
  public static final String ANCORA_OPTIONS = "ptb3Ellipsis=true,normalizeParentheses=true,ptb3Dashes=false,splitAll=true";

  /**
   * Constructor.
   *
   * @param r
   * @param tf
   * @param lexerProperties
   * @param splitCompounds
   */
  public SpanishTokenizer(Reader r, LexedTokenFactory<T> tf, Properties lexerProperties, boolean splitCompounds, boolean splitVerbs, boolean splitContractions) {
    lexer = new SpanishLexer(r, tf, lexerProperties);
    this.splitCompounds = splitCompounds;
    this.splitVerbs = splitVerbs;
    this.splitContractions = splitContractions;
    this.splitAny = (splitCompounds || splitVerbs || splitContractions);

    if (splitAny) compoundBuffer = Generics.newArrayList(4);
    if (splitVerbs) verbStripper = SpanishVerbStripper.getInstance();
  }

  @Override
  @SuppressWarnings("unchecked")
  protected T getNext() {
    try {
      T nextToken; // initialized in do-while
      // Depending on the orthographic normalization options,
      // some tokens can be obliterated. In this case, keep iterating
      // until we see a non-zero length token.
      do {
        nextToken = (splitAny && ! compoundBuffer.isEmpty()) ?
                (T) compoundBuffer.remove(0) :
                (T) lexer.next();
      } while (nextToken != null && nextToken.word().isEmpty());

      // Check for compounds to split
      if (splitAny && nextToken instanceof CoreLabel) {
        CoreLabel cl = (CoreLabel) nextToken;
        if (cl.containsKey(ParentAnnotation.class)) {
          if(splitCompounds && cl.get(ParentAnnotation.class).equals(SpanishLexer.COMPOUND_ANNOTATION))
            nextToken = (T) processCompound(cl);
          else if (splitVerbs && cl.get(ParentAnnotation.class).equals(SpanishLexer.VB_PRON_ANNOTATION))
            nextToken = (T) processVerb(cl);
          else if (splitContractions && cl.get(ParentAnnotation.class).equals(SpanishLexer.CONTR_ANNOTATION))
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
   * Handles contractions like del and al, marked by the lexer
   *
   * del =&gt; de + l =&gt; de + el
   * al =&gt; a + l =&gt; a + el
   * con[mts]igo =&gt; con + [mts]i
   *
   */
  private CoreLabel processContraction(CoreLabel cl) {
    cl.remove(ParentAnnotation.class);
    String word = cl.word();
    String first;
    String second;
    int secondOffset = 0, secondLength = 0;

    String lowered = word.toLowerCase();
    switch (lowered) {
      case "del":
      case "al":
        first = word.substring(0, lowered.length() - 1);
        char lastChar = word.charAt(lowered.length() - 1);
        if (Character.isLowerCase(lastChar))
          second = "el";
        else second = "EL";
        secondOffset = 1;
        secondLength = lowered.length() - 1;
        break;
      case "conmigo":
      case "consigo":
        first = word.substring(0, 3);
        second = word.charAt(3) + "í";
        secondOffset = 3;
        secondLength = 4;
        break;
      case "contigo":
        first = word.substring(0, 3);
        second = word.substring(3, 5);
        secondOffset = 3;
        secondLength = 4;
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
   * Handles verbs with attached suffixes, marked by the lexer:
   *
   * Escribamosela =&gt; Escribamo + se + la =&gt; escribamos + se + la
   * Sentaos =&gt; senta + os =&gt; sentad + os
   * Damelo =&gt; da + me + lo
   *
   */
  private CoreLabel processVerb(CoreLabel cl) {
    cl.remove(ParentAnnotation.class);
    SpanishVerbStripper.StrippedVerb stripped = verbStripper.separatePronouns(cl.word());
    if (stripped == null) {
      return cl;
    }

    // Split the CoreLabel into separate labels, tracking changing begin + end
    // positions.
    int stemEnd = cl.beginPosition() + stripped.getOriginalStem().length();
    int lengthRemoved = 0;
    for (String pronoun : stripped.getPronouns()) {
      int beginOffset = stemEnd + lengthRemoved;
      compoundBuffer.add(copyCoreLabel(cl, pronoun, beginOffset));
      lengthRemoved += pronoun.length();
    }

    CoreLabel stem = copyCoreLabel(cl, stripped.getStem(), cl.beginPosition(), stemEnd);
    stem.setOriginalText(stripped.getOriginalStem());
    return stem;
  }

  private static final Pattern pDash = Pattern.compile("\\-");
  private static final Pattern pSpace = Pattern.compile("\\s+");

  /**
   * Splits a compound marked by the lexer.
   */
  private CoreLabel processCompound(CoreLabel cl) {
    cl.remove(ParentAnnotation.class);

    String[] parts = pSpace.split(pDash.matcher(cl.word()).replaceAll(" - "));
    int lengthAccum = 0;
    for (String part : parts) {
      CoreLabel newLabel = new CoreLabel(cl);
      newLabel.setWord(part);
      newLabel.setValue(part);
      newLabel.setBeginPosition(cl.beginPosition() + lengthAccum);
      newLabel.setEndPosition(cl.beginPosition() + lengthAccum + part.length());
      newLabel.set(OriginalTextAnnotation.class, part);
      compoundBuffer.add(newLabel);

      lengthAccum += part.length();
    }
    return compoundBuffer.remove(0);
  }

  /**
   * recommended factory method
   */
  public static <T extends HasWord> TokenizerFactory<T> factory(LexedTokenFactory<T> factory, String options) {
    return new SpanishTokenizerFactory<>(factory, options);
  }

  public static <T extends HasWord> TokenizerFactory<T> factory(LexedTokenFactory<T> factory) {
    return new SpanishTokenizerFactory<>(factory, ANCORA_OPTIONS);
  }

  /**
   * A factory for Spanish tokenizer instances.
   *
   * @author Spence Green
   *
   * @param <T>
   */
  public static class SpanishTokenizerFactory<T extends HasWord> implements TokenizerFactory<T>, Serializable  {

    private static final long serialVersionUID = 946818805507187330L;

    protected final LexedTokenFactory<T> factory;
    protected Properties lexerProperties = new Properties();

    protected boolean splitCompoundOption = false;
    protected boolean splitVerbOption = false;
    protected boolean splitContractionOption = false;

    public static TokenizerFactory<CoreLabel> newCoreLabelTokenizerFactory() {
      return new SpanishTokenizerFactory<>(new CoreLabelTokenFactory());
    }


    /**
     * Constructs a new SpanishTokenizer that returns T objects and uses the options passed in.
     *
     * @param options a String of options, separated by commas
     * @return A TokenizerFactory that returns the right token types
     * @param factory a factory for the token type that the tokenizer will return
     */
    public static <T extends HasWord> SpanishTokenizerFactory<T> newSpanishTokenizerFactory(
            LexedTokenFactory<T> factory, String options) {
      return new SpanishTokenizerFactory<>(factory, options);
    }


    // Constructors

    /** Make a factory for SpanishTokenizers, default options */
    private SpanishTokenizerFactory(LexedTokenFactory<T> factory) {
      this.factory = factory;
    }

    /** Make a factory for SpanishTokenizers, options passed in */
    private SpanishTokenizerFactory(LexedTokenFactory<T> factory, String options) {
      this.factory = factory;
      setOptions(options);
    }


    @Override
    public Iterator<T> getIterator(Reader r) {
      return getTokenizer(r);
    }

    @Override
    public Tokenizer<T> getTokenizer(Reader r) {
      return new SpanishTokenizer<>(r, factory, lexerProperties, splitCompoundOption, splitVerbOption, splitContractionOption);
    }

    /**
     * Set underlying tokenizer options.
     *
     * @param options A comma-separated list of options
     */
    @Override
    public void setOptions(String options) {
      if (options == null) return;

      String[] optionList = options.split(",");
      for (String option : optionList) {
        String[] fields = option.split("=");
        if (fields.length == 1) {
          switch (fields[0]) {
            case "splitAll":
              splitCompoundOption = true;
              splitVerbOption = true;
              splitContractionOption = true;
              break;
            case "splitCompounds":
              splitCompoundOption = true;
              break;
            case "splitVerbs":
              splitVerbOption = true;
              break;
            case "splitContractions":
              splitContractionOption = true;
              break;
            default:
              lexerProperties.setProperty(option, "true");
              break;
          }

        } else if (fields.length == 2) {
          switch (fields[0]) {
            case "splitAll":
              splitCompoundOption = Boolean.valueOf(fields[1]);
              splitVerbOption = Boolean.valueOf(fields[1]);
              splitContractionOption = Boolean.valueOf(fields[1]);
              break;
            case "splitCompounds":
              splitCompoundOption = Boolean.valueOf(fields[1]);
              break;
            case "splitVerbs":
              splitVerbOption = Boolean.valueOf(fields[1]);
              break;
            case "splitContractions":
              splitContractionOption = Boolean.valueOf(fields[1]);
              break;
            default:
              lexerProperties.setProperty(fields[0], fields[1]);
              break;
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

  } // end static class SpanishTokenizerFactory

  /**
   * Returns a tokenizer with Ancora tokenization.
   */
  public static TokenizerFactory<CoreLabel> ancoraFactory() {
    TokenizerFactory<CoreLabel> tf = SpanishTokenizerFactory.newCoreLabelTokenizerFactory();
    tf.setOptions(ANCORA_OPTIONS);
    return tf;
  }

  /**
   * a factory that vends CoreLabel tokens with default tokenization.
   */
  public static TokenizerFactory<CoreLabel> coreLabelFactory() {
    return SpanishTokenizerFactory.newCoreLabelTokenizerFactory();
  }

  public static TokenizerFactory<CoreLabel> factory() {
    return coreLabelFactory();
  }

  private static String usage() {
    StringBuilder sb = new StringBuilder();
    String nl = System.lineSeparator();
    sb.append(String.format("Usage: java %s [OPTIONS] < file%n%n", SpanishTokenizer.class.getName()));
    sb.append("Options:").append(nl);
    sb.append("   -help          : Print this message.").append(nl);
    sb.append("   -ancora        : Tokenization style of AnCora (fixed).").append(nl);
    sb.append("   -lowerCase     : Apply lowercasing.").append(nl);
    sb.append("   -encoding type : Encoding format.").append(nl);
    sb.append("   -options str   : Orthographic options (see SpanishLexer.java)").append(nl);
    sb.append("   -tokens        : Output tokens as line-separated instead of space-separated.").append(nl);
    sb.append("   -onePerLine    : Output tokens one per line.").append(nl);
    return sb.toString();
  }

  private static Map<String,Integer> argOptionDefs() {
    Map<String,Integer> argOptionDefs = Generics.newHashMap();
    argOptionDefs.put("help", 0);
    argOptionDefs.put("ftb", 0);
    argOptionDefs.put("ancora", 0);
    argOptionDefs.put("lowerCase", 0);
    argOptionDefs.put("encoding", 1);
    argOptionDefs.put("options", 1);
    argOptionDefs.put("tokens", 0);
    return argOptionDefs;
  }

  /**
   * A fast, rule-based tokenizer for Spanish based on AnCora.
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
      log.info(usage());
      return;
    }

    // Lexer options
    final TokenizerFactory<CoreLabel> tf = SpanishTokenizer.coreLabelFactory();
    String orthoOptions = options.containsKey("ancora") ? ANCORA_OPTIONS : "";
    if (options.containsKey("options")) {
      orthoOptions = orthoOptions.isEmpty() ? options.getProperty("options") : orthoOptions + ',' + options;
    }
    final boolean tokens = PropertiesUtils.getBool(options, "tokens", false);
    if ( ! tokens) {
      orthoOptions = orthoOptions.isEmpty() ? "tokenizeNLs" : orthoOptions + ",tokenizeNLs";
    }
    tf.setOptions(orthoOptions);

    // Other options
    final String encoding = options.getProperty("encoding", "UTF-8");
    final boolean toLower = PropertiesUtils.getBool(options, "lowerCase", false);
    final Locale es = new Locale("es");
    boolean onePerLine = PropertiesUtils.getBool(options, "onePerLine", false);

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
        if (word.equals(SpanishLexer.NEWLINE_TOKEN)) {
          ++nLines;
          System.out.println();
          if ( ! onePerLine) {
            printSpace = false;
          }
        } else {
          String outputToken = toLower ? word.toLowerCase(es) : word;
          if (onePerLine) {
            System.out.println(outputToken);
          } else {
            if (printSpace) {
              System.out.print(" ");
            }
            System.out.print(outputToken);
            printSpace = true;
          }
        }
      }
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeIOException("Bad character encoding", e);
    }
    long elapsedTime = System.nanoTime() - startTime;
    double linesPerSec = (double) nLines / (elapsedTime / 1e9);
    System.err.printf("Done! Tokenized %d lines (%d tokens) at %.2f lines/sec%n", nLines, nTokens, linesPerSec);
  } // end main()

}
