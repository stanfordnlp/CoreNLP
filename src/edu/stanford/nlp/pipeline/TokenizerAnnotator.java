package edu.stanford.nlp.pipeline;

import java.io.Reader;
import java.io.StringReader;
import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.*;
import edu.stanford.nlp.international.spanish.process.SpanishTokenizer;
import edu.stanford.nlp.international.french.process.FrenchTokenizer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * This class will PTB tokenize the input.  It assumes that the original
 * String is under the CoreAnnotations.TextAnnotation field
 * and it will add the output from the
 * InvertiblePTBTokenizer ({@code List<CoreLabel>}) under
 * CoreAnnotation.TokensAnnotation.
 *
 * @author Jenny Finkel
 * @author Christopher Manning
 * @author Ishita Prasad
 */
public class TokenizerAnnotator implements Annotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TokenizerAnnotator.class);

  /**
   * Enum to identify the different TokenizerTypes. To add a new
   * TokenizerType, add it to the list with a default options string
   * and add a clause in getTokenizerType to identify it.
   */
  public enum TokenizerType {
    Unspecified(null, null, "invertible,ptb3Escaping=true"),
    Arabic     ("ar", null, ""),
    Chinese    ("zh", null, ""),
    Spanish    ("es", "SpanishTokenizer", SpanishTokenizer.DEFAULT_OPTIONS),
    English    ("en", "PTBTokenizer", "invertible"),
    German     ("de", null, "invertible,ptb3Escaping=false,splitHyphenated=true"),
    French     ("fr", "FrenchTokenizer", FrenchTokenizer.DEFAULT_OPTIONS),
    Whitespace (null, "WhitespaceTokenizer", "");

    private final String abbreviation;
    private final String className;
    private final String defaultOptions;

    TokenizerType(String abbreviation, String className, String defaultOptions) {
      this.abbreviation = abbreviation;
      this.className = className;
      this.defaultOptions = defaultOptions;
    }

    public String getDefaultOptions() {
      return defaultOptions;
    }

    private static final Map<String, TokenizerType> nameToTokenizerMap = initializeNameMap();

    private static Map<String, TokenizerType> initializeNameMap() {
      Map<String, TokenizerType> map = Generics.newHashMap();
      for (TokenizerType type : TokenizerType.values()) {
        if (type.abbreviation != null) {
          map.put(type.abbreviation.toUpperCase(), type);
        }
        map.put(type.toString().toUpperCase(), type);
      }
      return Collections.unmodifiableMap(map);
    }

    private static final Map<String, TokenizerType> classToTokenizerMap = initializeClassMap();

    private static Map<String, TokenizerType> initializeClassMap() {
      Map<String, TokenizerType> map = Generics.newHashMap();
      for (TokenizerType type : TokenizerType.values()) {
        if (type.className != null) {
          map.put(type.className.toUpperCase(), type);
        }
      }
      return Collections.unmodifiableMap(map);
    }

    /**
     * Get TokenizerType based on what's in the properties.
     *
     * @param props Properties to find tokenizer options in
     * @return An element of the TokenizerType enum indicating the tokenizer to use
     */
    public static TokenizerType getTokenizerType(Properties props) {
      String tokClass = props.getProperty("tokenize.class", null);
      boolean whitespace = Boolean.parseBoolean(props.getProperty("tokenize.whitespace", "false"));
      String language = props.getProperty("tokenize.language", "en");

      if(whitespace) {
        return Whitespace;
      }

      if (tokClass != null) {
        TokenizerType type = classToTokenizerMap.get(tokClass.toUpperCase());
        if (type == null) {
          throw new IllegalArgumentException("TokenizerAnnotator: unknown tokenize.class property " + tokClass);
        }
        return type;
      }

      if (language != null) {
        TokenizerType type = nameToTokenizerMap.get(language.toUpperCase());
        if (type == null) {
          throw new IllegalArgumentException("TokenizerAnnotator: unknown tokenize.language property " + language);
        }
        return type;
      }

      return Unspecified;
    }
  } // end enum TokenizerType


  @SuppressWarnings("WeakerAccess")
  public static final String EOL_PROPERTY = "tokenize.keepeol";
  @SuppressWarnings("WeakerAccess")
  public static final String KEEP_NL_OPTION = "tokenizeNLs,";

  private final boolean VERBOSE;
  private final TokenizerFactory<CoreLabel> factory;

  /** new segmenter properties **/
  private final Annotator segmenterAnnotator;
  /** If not null, will use this instead of a lexer or segmenter */
  private final StatTokSentAnnotator cdcAnnotator;
  private final CleanXmlAnnotator cleanxmlAnnotator;
  private final WordsToSentencesAnnotator ssplitAnnotator;

  /** run a custom post processor after the lexer.  DOES NOT apply to segmenters **/
  private final List<CoreLabelProcessor> postProcessors;

  // CONSTRUCTORS

  /** Gives a non-verbose, English tokenizer. */
  public TokenizerAnnotator() {
    this(false);
  }


  private static String computeExtraOptions(Properties properties) {
    String extraOptions = null;
    boolean keepNewline = Boolean.parseBoolean(properties.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false")); // ssplit.eolonly

    // Only possibly put in *NL* if not never (the Boolean method treats null as false)
    // We used to also check for ssplit annotator being present, but
    // that was wrong in the case where a tokenizer model was
    // preloaded (such as in the case of segmenters) and we didn't
    // want to need to reload the model when the ssplit was later added.
    if (!Boolean.parseBoolean(properties.getProperty("ssplit.isOneSentence"))) {
      // Set to { NEVER, ALWAYS, TWO_CONSECUTIVE } based on  ssplit.newlineIsSentenceBreak
      String nlsbString = properties.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY,
                                                 StanfordCoreNLP.DEFAULT_NEWLINE_IS_SENTENCE_BREAK);
      WordToSentenceProcessor.NewlineIsSentenceBreak nlsb = WordToSentenceProcessor.stringToNewlineIsSentenceBreak(nlsbString);
      if (nlsb != WordToSentenceProcessor.NewlineIsSentenceBreak.NEVER) {
        keepNewline = true;
      }
    }
    if (keepNewline) {
      extraOptions = KEEP_NL_OPTION;
    }
    return extraOptions;
  }


  public TokenizerAnnotator(Properties properties) {
    this(false, properties, computeExtraOptions(properties));
  }

  public TokenizerAnnotator(boolean verbose) {
    this(verbose, TokenizerType.English);
  }

  public TokenizerAnnotator(String lang) {
    this(true, lang, null);
  }

  public TokenizerAnnotator(boolean verbose, TokenizerType lang) {
    this(verbose, lang.toString());
  }

  public TokenizerAnnotator(boolean verbose, String lang) {
    this(verbose, lang, null);
  }

  public TokenizerAnnotator(boolean verbose, String lang, String options) {
    this(verbose, lang == null ? null : PropertiesUtils.asProperties("tokenize.language", lang), options);
  }

  public TokenizerAnnotator(boolean verbose, Properties props) {
    this(verbose, props, computeExtraOptions(props));
  }

  public TokenizerAnnotator(boolean verbose, Properties props, String options) {
    if (props == null) {
      props = new Properties();
    }
    // check if segmenting must be done (Chinese or Arabic and not tokenizing on whitespace)
    boolean whitespace = Boolean.parseBoolean(props.getProperty("tokenize.whitespace", "false"));
    if (props.getProperty("tokenize.language") != null &&
        LanguageInfo.isSegmenterLanguage(props.getProperty("tokenize.language")) &&
        !whitespace) {
      cdcAnnotator = null;
      if (LanguageInfo.getLanguageFromString(props.getProperty("tokenize.language")) == LanguageInfo.HumanLanguage.ARABIC) {
        segmenterAnnotator = new ArabicSegmenterAnnotator("segment", props);
      } else if (LanguageInfo.getLanguageFromString(props.getProperty("tokenize.language")) == LanguageInfo.HumanLanguage.CHINESE) {
        segmenterAnnotator = new ChineseSegmenterAnnotator("segment", props);
      } else {
        segmenterAnnotator = null;
        throw new RuntimeException("No segmenter implemented for: "+
                                   LanguageInfo.getLanguageFromString(props.getProperty("tokenize.language")));
      }
    } else if (props.getProperty(STANFORD_CDC_TOKENIZE + ".model", null) != null) {
      cdcAnnotator = new StatTokSentAnnotator(props);
      segmenterAnnotator = null;
    } else {
      segmenterAnnotator = null;
      cdcAnnotator = null;
    }

    // load any custom token post processing
    String postProcessorClass = props.getProperty("tokenize.postProcessor", "");
    List<CoreLabelProcessor> processors = new ArrayList<>();
    try {
      if (!postProcessorClass.equals("")) {
        processors.add(ReflectionLoading.loadByReflection(postProcessorClass));
      }
    } catch (RuntimeException e) {
      throw new RuntimeException("Loading: "+postProcessorClass+" failed with: "+e.getMessage());
    }
    if (PropertiesUtils.getBool(props, "tokenize.codepoint")) {
      processors.add(new CodepointCoreLabelProcessor());
    }
    postProcessors = Collections.unmodifiableList(processors);

    VERBOSE = PropertiesUtils.getBool(props, "tokenize.verbose", verbose);
    TokenizerType type = TokenizerType.getTokenizerType(props);
    factory = initFactory(type, props, options);
    if (VERBOSE) {
      log.info("Initialized tokenizer factory: " + factory);
    }

    if (PropertiesUtils.getBool(props, STANFORD_TOKENIZE + "." + STANFORD_CLEAN_XML)) {
      this.cleanxmlAnnotator = new CleanXmlAnnotator(props);
    } else {
      this.cleanxmlAnnotator = null;
    }

    if (PropertiesUtils.getBool(props, STANFORD_TOKENIZE + "." + STANFORD_SSPLIT, true)) {
      this.ssplitAnnotator = new WordsToSentencesAnnotator(props);
    } else {
      this.ssplitAnnotator = null;
    }
  }

  /**
   * initFactory returns the right type of TokenizerFactory based on the options in the properties file
   * and the type. When adding a new Tokenizer, modify TokenizerType.getTokenizerType() to retrieve
   * your tokenizer from the properties file, and then add a class is the switch structure here to
   * instantiate the new Tokenizer type.
   *
   * @param type the TokenizerType
   * @param props the properties file
   * @param extraOptions extra things that should be passed into the tokenizer constructor
   */
  private static TokenizerFactory<CoreLabel> initFactory(TokenizerType type, Properties props, String extraOptions) throws IllegalArgumentException{
    TokenizerFactory<CoreLabel> factory;
    String options = props.getProperty("tokenize.options", null);

    // set it to the equivalent of both extraOptions and options
    // TODO: maybe we should always have getDefaultOptions() and
    // expect the user to turn off default options.  That would
    // require all options to have negated options, but
    // currently there are some which don't have that
    if (options == null) {
      options = type.getDefaultOptions();
    }
    if (extraOptions != null) {
      if (extraOptions.endsWith(",")) {
        options = extraOptions + options;
      } else {
        options = extraOptions + ',' + options;
      }
    }

    switch(type) {

    case Arabic:
    case Chinese:
      factory = null;
      break;

    case Spanish:
      factory = SpanishTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;

    case French:
      factory = FrenchTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;

    case Whitespace:
      boolean eolIsSignificant = Boolean.parseBoolean(props.getProperty(EOL_PROPERTY, "false"));
      eolIsSignificant = eolIsSignificant || KEEP_NL_OPTION.equals(computeExtraOptions(props));
      factory = new WhitespaceTokenizer.WhitespaceTokenizerFactory<>(new CoreLabelTokenFactory(), eolIsSignificant);
      break;

    case English:
    case German:
      factory = PTBTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;

    case Unspecified:
      log.info("No tokenizer type provided. Defaulting to PTBTokenizer.");
      factory = PTBTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;

    default:
      throw new IllegalArgumentException("No valid tokenizer type provided.\n" +
                                         "Use -tokenize.language, -tokenize.class, or -tokenize.whitespace \n" +
                                         "to specify a tokenizer.");
    }
    return factory;
  }

  /**
   * Returns a thread-safe tokenizer
   */
  public Tokenizer<CoreLabel> getTokenizer(Reader r) {
    return factory.getTokenizer(r);
  }

  /**
   * Helper method to set the TokenBeginAnnotation and TokenEndAnnotation of every token.
   */
  private static void setTokenBeginTokenEnd(List<CoreLabel> tokensList) {
    int tokenIndex = 0;
    for (CoreLabel token : tokensList) {
      token.set(CoreAnnotations.TokenBeginAnnotation.class, tokenIndex);
      token.set(CoreAnnotations.TokenEndAnnotation.class, tokenIndex+1);
      tokenIndex++;
    }
  }

  /**
   * set isNewline()
   */
  private static void setNewlineStatus(List<CoreLabel> tokensList) {
    // label newlines
    // TODO: could look to see if the original text was exactly *NL*,
    // in which case we don't want to do this.  Could even check that
    // length == 4 as an optimization.  This will involve checking
    // the sentence splitter to make sure all comparisons to
    // NEWLINE_TOKEN respect isNewlineAnnotation
    // What didn't work was checking if length was 1, since that
    // runs afoul of two character Windows newlines...
    for (CoreLabel token : tokensList) {
      if (token.word().equals(AbstractTokenizer.NEWLINE_TOKEN))
        token.set(CoreAnnotations.IsNewlineAnnotation.class, true);
      else
        token.set(CoreAnnotations.IsNewlineAnnotation.class, false);
    }
  }

  public static void adjustFinalToken(List<CoreLabel> tokens) {
    if (tokens == null || tokens.size() == 0) {
      return;
    }
    CoreLabel finalToken = tokens.get(tokens.size() - 1);
    String finalTokenAfter = finalToken.get(CoreAnnotations.AfterAnnotation.class);
    if (finalTokenAfter != null && finalTokenAfter.length() > 0) {
      char last = finalTokenAfter.charAt(finalTokenAfter.length() - 1);
      if (last != ' ') {
        throw new IllegalArgumentException("adjustFinalToken: Unexpected final char: |" + last + "| (" + (int) last + ')');
      }
      finalTokenAfter = finalTokenAfter.substring(0, finalTokenAfter.length() - 1);
      finalToken.set(CoreAnnotations.AfterAnnotation.class, finalTokenAfter);
    }
  }

  /**
   * Does the actual work of splitting TextAnnotation into CoreLabels,
   * which are then attached to the TokensAnnotation.
   */
  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      log.info("Beginning tokenization");
    }

    if (cdcAnnotator != null) {
      cdcAnnotator.annotate(annotation);
      // the CDC annotator does tokenize, ssplit, and mwt (if we even
      // integrate that into tokenize), so we just leave once it's
      // done.  the unique internal workings of that tokenizer prevent
      // cleanxml from working, at least for now
      return;
    }

    // for Arabic and Chinese use a segmenter instead
    if (segmenterAnnotator != null) {
      segmenterAnnotator.annotate(annotation);
      // set indexes into document wide tokens list
      setTokenBeginTokenEnd(annotation.get(CoreAnnotations.TokensAnnotation.class));
      setNewlineStatus(annotation.get(CoreAnnotations.TokensAnnotation.class));
    } else if (annotation.containsKey(CoreAnnotations.TextAnnotation.class)) {
      // TODO: This is a huge hack.  jflex does not have a lookahead operation which can match EOF
      // Because of this, the PTBTokenizer has a few productions which can't operate at EOF.
      // For example,
      //   {ASSIMILATIONS2}/[^\p{Alpha}]
      // We compensate by adding a space, then undoing the space later on
      // We can change it back to this if that feature is ever added to jflex:
      //   String text = annotation.get(CoreAnnotations.TextAnnotation.class);
      String text = annotation.get(CoreAnnotations.TextAnnotation.class) + " ";
      Reader r = new StringReader(text);
      // don't wrap in BufferedReader.  It gives you nothing for in-memory String unless you need the readLine() method!

      List<CoreLabel> tokens = getTokenizer(r).tokenize();
      adjustFinalToken(tokens);

      // cdm 2010-05-15: This is now unnecessary, as it is done in CoreLabelTokenFactory
      // for (CoreLabel token: tokens) {
      // token.set(CoreAnnotations.TextAnnotation.class, token.get(CoreAnnotations.TextAnnotation.class));
      // }

      // label newlines
      setNewlineStatus(tokens);

      // set indexes into document wide token list
      setTokenBeginTokenEnd(tokens);

      // run post processing
      for (CoreLabelProcessor postProcessor : postProcessors) {
        tokens = postProcessor.process(tokens);
      }

      // add tokens list to annotation
      annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);

      if (VERBOSE) {
        log.info("Tokenized: " + annotation.get(CoreAnnotations.TokensAnnotation.class));
      }
    } else {
      throw new RuntimeException("Tokenizer unable to find text in annotation: " + annotation);
    }

    // If the annotation was already processed before and already has
    // a SentenceAnnotation.class, recreating the tokenization
    // invalidates any existing sentence annotation
    annotation.remove(CoreAnnotations.SentencesAnnotation.class);
    if (this.cleanxmlAnnotator != null) {
      this.cleanxmlAnnotator.annotate(annotation);
    }
    if (this.ssplitAnnotator != null) {
      this.ssplitAnnotator.annotate(annotation);
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return new HashSet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.BeforeAnnotation.class,
        CoreAnnotations.AfterAnnotation.class,
        CoreAnnotations.TokenBeginAnnotation.class,
        CoreAnnotations.TokenEndAnnotation.class,
        CoreAnnotations.PositionAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class,
        CoreAnnotations.ValueAnnotation.class,
        CoreAnnotations.IsNewlineAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.SentenceIndexAnnotation.class
    ));
  }

}
