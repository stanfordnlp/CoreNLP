package edu.stanford.nlp.pipeline;

import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.WhitespaceTokenizer;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.international.spanish.process.SpanishTokenizer;
import edu.stanford.nlp.international.french.process.FrenchTokenizer;

/**
 * This class will PTB tokenize the input.  It assumes that the original
 * String is under the CoreAnnotations.TextAnnotation field
 * and it will add the output from the
 * InvertiblePTBTokenizer ({@code List<CoreLabel>}) under
 * CoreAnnotation.TokensAnnotation.
 *
 * @author Jenny Finkel
 * @author Christopher Manning
 */
public class PTBTokenizerAnnotator extends TokenizerAnnotator {

  private final TokenizerFactory<CoreLabel> factory;

  public static enum Language {
    Spanish    ("es"), 
    French     ("fr"), 
    English    ("en"), 
    Whitespace ("wh");

    private final String abbreviation;
    public String abbreviation() { return abbreviation; }

    private static final Map<String, Language> nameToLanguageMap = initializeFromMap();

    private Language(String abbreviation) {
      this.abbreviation = abbreviation;
    }

    private static Map<String, Language> initializeFromMap() {
      Map<String, Language> map = Generics.newHashMap();
      for (Language language : Language.values()) {
        map.put(language.abbreviation.toUpperCase(), language);
        map.put(language.toString().toUpperCase(), language);
      }
      return Collections.unmodifiableMap(map);
    }

    /** Returns null for an unknown language */
    public static Language fromName(String value) {
      return nameToLanguageMap.get(value.toUpperCase());
    }
  }

  public static final String DEFAULT_OPTIONS_EN = "invertible,ptb3Escaping=true";
  public static final String DEFAULT_OPTIONS_ES = "invertible,ptb3Escaping=true,splitAll=true" ;
  public static final String DEFAULT_OPTIONS_FR = "";

  public static final String EOL_PROPERTY = "tokenize.keepeol";
  
  public PTBTokenizerAnnotator() {
    this(true);
  }
    
  public PTBTokenizerAnnotator(boolean verbose) {
    this(verbose, new Properties());
  }

  public PTBTokenizerAnnotator(boolean verbose, String options) {
    super(verbose);
    
    Properties props = new Properties();
    if (options != null)
      props.setProperty("tokenize.options", options);

    factory = initFactory(Language.English, props);
  }

  public PTBTokenizerAnnotator(boolean verbose, Properties props) {
    super(verbose);

    if (props == null)
      props = new Properties();

    Language type = getLangType(props);
    factory = initFactory(type, props);
  }

  private TokenizerFactory<CoreLabel> initFactory(Language type, Properties props) {
    String options = props.getProperty("tokenize.options", null);
    TokenizerFactory<CoreLabel> factory;
    
    switch(type) {
    case Spanish:
      options = (options == null) ? DEFAULT_OPTIONS_ES : options;
      factory = SpanishTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;

    case French:
      options = (options == null) ? DEFAULT_OPTIONS_FR : options;
      factory = FrenchTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;

    case Whitespace:
      boolean eolIsSignificant = Boolean.valueOf(props.getProperty(EOL_PROPERTY, "false"));
      eolIsSignificant = eolIsSignificant || Boolean.valueOf(props.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
      factory = new WhitespaceTokenizer.WhitespaceTokenizerFactory<CoreLabel> (new CoreLabelTokenFactory(), eolIsSignificant);
      break;

    case English:
      options = (options == null) ? DEFAULT_OPTIONS_EN : options;
      factory = PTBTokenizer.factory(new CoreLabelTokenFactory(), options);
      break;

    default:
      throw new IllegalArgumentException("Unknown language " + type);
    }
    return factory;
  }

  /**
   * Returns the language or tokenizer type that the annotator should
   * use. Language/Tokenizer type is specified through tokenize.class
   * or tokenize.language.
   */
  private Language getLangType(Properties props) {
    String tokClass = props.getProperty("tokenize.class", null);
    if (tokClass != null) {
      if (tokClass.equals("SpanishTokenizer")) 
        return Language.Spanish;
      else if (tokClass.equals("FrenchTokenizer"))
        return Language.French;
      else
        return Language.English;
    }

    if (Boolean.valueOf(props.getProperty("tokenize.whitespace", "false")))
      return Language.Whitespace;

    String languageName = props.getProperty("tokenize.language", "english");
    Language language = Language.fromName(languageName);
    if (language == null) {
      throw new IllegalArgumentException("Tokenizer does not support language " + languageName);
    }
    return language;
  }
	
  @Override
  public Tokenizer<CoreLabel> getTokenizer(Reader r) {
    return factory.getTokenizer(r);
  }

}
