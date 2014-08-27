package edu.stanford.nlp.pipeline;

import java.io.Reader;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;

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

	public static enum Language {Spanish, French, English, Whitespace}

	public static final String SPANISH = "spanish";
	public static final String ES = "es";
	public static final String FRENCH = "french";
	public static final String FR = "fr";
	public static final String ENGLISH = "english";
	public static final String EN = "en";

  public static final String DEFAULT_OPTIONS_EN = "invertible,ptb3Escaping=true";
	public static final String DEFAULT_OPTIONS_ES = "ptb3Ellipsis=true,normalizeParentheses=true,ptb3Dashes=false,splitAll=true";
	public static final String DEFAULT_OPTIONS_FR = "";
    
	public PTBTokenizerAnnotator() {
		this(true);
  }
    
	public PTBTokenizerAnnotator(boolean verbose) {
		this(verbose, new Properties());
  }

	public PTBTokenizerAnnotator(boolean verbose, Properties props) {
		super(verbose);
		Language type = getLangType(props);
		String options = props.getProperty("tokenize.options", null);

		switch(type) {
		case Spanish:			
			options = (options == null) ? DEFAULT_OPTIONS_ES : options;
			factory = SpanishTokenizer.factory(new CoreLabelTokenFactory(), options);
			break;
		case French:
			options = (options == null) ? DEFAULT_OPTIONS_ES : options;
			factory = FrenchTokenizer.factory(new CoreLabelTokenFactory(), options);
			break;
		default:
      options = (options == null) ? DEFAULT_OPTIONS_EN : options;
      factory = PTBTokenizer.factory(new CoreLabelTokenFactory(), options);
    }
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
			else return Language.English;
		}

		if(Boolean.valueOf(props.getProperty("tokenize.whitespace", "false")))
			return Language.Whitespace;

		String language = props.getProperty("tokenize.language", "english");
		if (language.equalsIgnoreCase(SPANISH) || language.equalsIgnoreCase(ES))
			return Language.Spanish;
		if (language.equalsIgnoreCase(FRENCH) || language.equalsIgnoreCase(FR))
			return Language.French;
		else return Language.English;
	}
	
  @Override
  public Tokenizer<CoreLabel> getTokenizer(Reader r) {
    return factory.getTokenizer(r);
  }

}
