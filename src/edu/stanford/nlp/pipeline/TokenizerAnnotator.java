package edu.stanford.nlp.pipeline;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
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
		Unknown ("invertible,ptb3Escaping=true"),
		Spanish ("invertible,ptb3Escaping=true,splitAll=true"),
		English ("invertible,ptb3Escaping=true"),
		German ("invertible,ptb3Escaping=true"),
		French (""),
		Whitespace ("");
		
		private String defaultOptions;

		// constructors
		private TokenizerType() {
			defaultOptions = null;
		}

		private TokenizerType(String defaultOptions) {
			this.defaultOptions = defaultOptions;
		}

		public String getDefaultOptions() {
			return defaultOptions;
		}

		/**
		 * Get TokenizerType based on what's in the properties
		 */
		public static TokenizerType getTokenizerType(Properties props) {
			String tokClass = props.getProperty("tokenize.class", null);
			if (tokClass != null) {
				if (tokClass.equals("SpanishTokenizer"))
					return Spanish;
				else if (tokClass.equals("FrenchTokenizer"))
					return French;
				else if (tokClass.equals("PTBTokenizer"))
					return English;
			}
			if(Boolean.valueOf(props.getProperty("tokenize.whitespace", "false")))
				return Whitespace;

			String language = props.getProperty("tokenize.language", "").toLowerCase();

			if (language.equals(SPANISH) || language.equals(ES)) {
				return Spanish;

			} else if (language.equals(FRENCH) || language.equals(FR)) {
				return French;

			} else if (language.equals(ENGLISH) || language.equals(EN) ||
								 language.equals(GERMAN) || language.equals(DE)) {
				return English;

			}
			return Unknown;
		}
	} // end enum TokenizerType

	public static final String SPANISH = "spanish";
	public static final String ES = "es";
	public static final String FRENCH = "french";
	public static final String FR = "fr";
	public static final String ENGLISH = "english";
	public static final String EN = "en";
	public static final String GERMAN = "german";
	public static final String DE = "de";
	public static final String EOL_PROPERTY = "tokenize.keepeol";

	private final boolean VERBOSE;
  private final TokenizerFactory<CoreLabel> factory;

	// CONSTRUCTORS
    
	public TokenizerAnnotator() {
		this(true);
  }
    
	public TokenizerAnnotator(boolean verbose) {
		this(verbose, EN);
	}

	public TokenizerAnnotator(String lang) {
		this(true, lang, null);
	}

	public TokenizerAnnotator(boolean verbose, String lang) {
		this(verbose, lang, null);
	}

	public TokenizerAnnotator(boolean verbose, String lang, String options) {
    VERBOSE = verbose;
    Properties props = new Properties();
    props.setProperty("tokenize.language", lang);
		System.out.println(props.getProperty("tokenize.language", "banana"));

    TokenizerType type = TokenizerType.getTokenizerType(props);
    factory = initFactory(type, props, options);
	}

  public TokenizerAnnotator(boolean verbose, Properties props) {
    this(verbose, props, null);
  }

	public TokenizerAnnotator(boolean verbose, Properties props, String extraOptions) {
		VERBOSE = verbose;
		if (props == null) {
			props = new Properties();
		}

		TokenizerType type = TokenizerType.getTokenizerType(props);
		System.err.println(type.name());
		factory = initFactory(type, props, extraOptions);
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

		// set it to the equivalent of both extraOptions and options, unless both are nulls
		if (options == null) {
			options = extraOptions;
		} else if (extraOptions != null) {
			options = extraOptions + options;
		}

		// if options is STILL null set it to default options
		if (options == null) {
			options = type.getDefaultOptions();
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
			default:
				throw new IllegalArgumentException("No valid tokenizer type provided.\n" +
																					 "Use -tokenize.language, -tokenize.class, or -tokenize.whitespace \n" +
																					 "to specify a tokenizer.");
		 
		}
		/*		catch (IllegalArgumentException e) {
			System.err.println("Illegal Argument Exception: " + e.getMessage());
			System.err.println("Use -tokenize.language, -tokenize.class, or -tokenizer.whitespace \n" +
												 "to specify a tokenizer type.");
			System.err.println("Using PTBTokenizer as default tokenizer.");
			}*/

		/*		if (factory == null) {
			factory = PTBTokenizer.factory(new CoreLabelTokenFactory(), options);
			}*/
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
