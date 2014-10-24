package edu.stanford.nlp.pipeline;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.WhitespaceTokenizer;
import edu.stanford.nlp.international.spanish.process.SpanishTokenizer;
import edu.stanford.nlp.international.french.process.FrenchTokenizer;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

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
public class TokenizerAnnotator implements Annotator {

  /**
   * Enum to identify the different TokenizerTypes. To add a new
   * TokenizerType, add it to the list with a default options string
   * and add a clause in getTokenizerType to identify it.
   */
  public enum TokenizerType {
    Unspecified(null, null, "invertible,ptb3Escaping=true"),
    Spanish    ("es", "SpanishTokenizer", "invertible,ptb3Escaping=true,splitAll=true"),
    English    ("en", "PTBTokenizer", "invertible,ptb3Escaping=true"),
    German     ("de", null, "invertible,ptb3Escaping=true"),
    French     ("fr", "FrenchTokenizer", ""),
    Whitespace (null, "WhitespaceTokenizer", "");
		
    private final String abbreviation;
    private final String className;
    private final String defaultOptions;

    private TokenizerType(String abbreviation, String className, String defaultOptions) {
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

    /***
     * Get TokenizerType based on what's in the properties
     */
    public static TokenizerType getTokenizerType(Properties props) {
      String tokClass = props.getProperty("tokenize.class", null);
      boolean whitespace = Boolean.valueOf(props.getProperty("tokenize.whitespace", "false"));
      String language = props.getProperty("tokenize.language", null);
      
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
  
  public static final String EOL_PROPERTY = "tokenize.keepeol";
  
  private final boolean VERBOSE;
  private final TokenizerFactory<CoreLabel> factory;
  
  // CONSTRUCTORS
  
  public TokenizerAnnotator() {
    this(true);
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
    VERBOSE = verbose;
    Properties props = new Properties();
    if (lang != null) {
      props.setProperty("tokenize.language", lang);
    }
    
    TokenizerType type = TokenizerType.getTokenizerType(props);
    factory = initFactory(type, props, options);
  }
  
  public TokenizerAnnotator(boolean verbose, Properties props) {
    this(verbose, props, null);
  }
  
  public TokenizerAnnotator(boolean verbose, Properties props, String options) {
    VERBOSE = verbose;
    if (props == null) {
      props = new Properties();
    }
    
    TokenizerType type = TokenizerType.getTokenizerType(props);
    factory = initFactory(type, props, options);
  }
  
  /** 
   * initFactory returns the right type of TokenizerFactory based on the options in the properties file
   * and the type. When adding a new Tokenizer, modify TokenizerType.getTokenizerType() to retrieve
   * your tokenizer from the properties file, and then add a class is the switch structure here to 
   * instanstiate the new Tokenizer type.
   *
   * @param type the TokenizerType
   * @param type the properties file
   * @param extraOptions extra things that should be passed into the tokenizer constructor
   */
  private TokenizerFactory<CoreLabel> initFactory(TokenizerType type, Properties props, String extraOptions) throws IllegalArgumentException{
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
      options = extraOptions + options;
    }
    
    switch(type) {
    case Spanish:
      factory = SpanishTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;
      
    case French:
      factory = FrenchTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;
      
    case Whitespace:
      boolean eolIsSignificant = Boolean.valueOf(props.getProperty(EOL_PROPERTY, "false"));
      eolIsSignificant = eolIsSignificant || Boolean.valueOf(props.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
      factory = new WhitespaceTokenizer.WhitespaceTokenizerFactory<CoreLabel> (new CoreLabelTokenFactory(), eolIsSignificant);
      break;
      
    case English: 
    case German:
      factory = PTBTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;
      
    case Unspecified:
      System.err.println("TokenizerAnnotator: No tokenizer type provided. Defaulting to PTBTokenizer.");
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
   * Does the actual work of splitting TextAnnotation into CoreLabels,
   * which are then attached to the TokensAnnotation.
   */
  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      System.err.print("Tokenizing ... ");
    }
    
    if (annotation.has(CoreAnnotations.TextAnnotation.class)) {
      String text = annotation.get(CoreAnnotations.TextAnnotation.class);
      Reader r = new StringReader(text);  
      // don't wrap in BufferedReader.  It gives you nothing for in memory String unless you need the readLine() method	!
      
      List<CoreLabel> tokens = getTokenizer(r).tokenize();
      // cdm 2010-05-15: This is now unnecessary, as it is done in CoreLabelTokenFactory
      // for (CoreLabel token: tokens) {
      // token.set(CoreAnnotations.TextAnnotation.class, token.get(CoreAnnotations.TextAnnotation.class));
      // }
      
      annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);
      if (VERBOSE) {
        System.err.println("done.");
        System.err.println("Tokens: " + annotation.get(CoreAnnotations.TokensAnnotation.class));
      }
    } else {
      throw new RuntimeException("Tokenizer unable to find text in annotation: " + annotation);
    }
  }
  
  @Override
  public Set<Requirement> requires() {
    return Collections.emptySet();
  }
  
  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(TOKENIZE_REQUIREMENT);
  }  
}
