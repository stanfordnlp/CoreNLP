package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotator;
import edu.stanford.nlp.naturalli.OpenIE;
import edu.stanford.nlp.util.MetaClass;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.*;

/**
 * A class abstracting the implementation of various annotators.
 * Importantly, subclasses of this class can overwrite the implementation
 * of these annotators by returning a different annotator, and
 * {@link edu.stanford.nlp.pipeline.StanfordCoreNLP} will automatically load
 * the new annotator instead.
 *
 * @author Gabor Angeli
 */
public class AnnotatorImplementations  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(AnnotatorImplementations.class);

  /**
   * Tokenize, emulating the Penn Treebank
   */
  public Annotator tokenizer(Properties properties) {
    return new TokenizerAnnotator(properties);
  }

  /**
   * Tokenize, ssplit, and mwt all at once, using ColumnDataClassifier
   */
  public Annotator cdcTokenizer(Properties properties) {
    return new StatTokSentAnnotator(properties);
  }

  /**
   * Clean XML input
   */
  public CleanXmlAnnotator cleanXML(Properties properties) {
    return new CleanXmlAnnotator(properties);
  }

  /**
   * Sentence split, in addition to a bunch of other things in this annotator (be careful to check the implementation!)
   */
  public Annotator wordToSentences(Properties properties) {
    return new WordsToSentencesAnnotator(properties);
  }

  /**
   * Multi-word-token, split tokens into words (e.g. "des" in French into "de" and "les")
   */
  public Annotator multiWordToken(Properties props) {
    // MWTAnnotator defaults to using "mwt." as prefix
    return new MWTAnnotator("", props);
  }

  /**
   * Set document date
   */
  public Annotator docDate(Properties properties) {
    return new DocDateAnnotator("docdate", properties);
  }

  /**
   * Part of speech tag
   */
  public Annotator posTagger(Properties properties) {
    String annotatorName = "pos";
    return new POSTaggerAnnotator(annotatorName, properties);
  }

  /**
   * Annotate lemmas
   */
  public Annotator morpha(Properties properties, boolean verbose) {
    return new MorphaAnnotator(verbose);
  }

  /**
   * Annotate for named entities -- note that this combines multiple NER tag sets, and some auxiliary things (like temporal tagging)
   */
  public Annotator ner(Properties properties) {
    try {
      return new NERCombinerAnnotator(properties);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /**
   * Run TokensRegex -- annotate patterns found in tokens
   */
  public Annotator tokensregex(Properties properties, String name) {
    return new TokensRegexAnnotator(name, properties);
  }

  /**
   * Run RegexNER -- rule-based NER based on a deterministic mapping file
   */
  public Annotator tokensRegexNER(Properties properties, String name) {
    return new TokensRegexNERAnnotator(name, properties);
  }

  /**
   * Annotate mentions
   */
  public Annotator entityMentions(Properties properties, String name) {
    return new EntityMentionsAnnotator(name, properties);
  }

  /**
   * Annotate for gender of tokens
   */
  public Annotator gender(Properties properties, String name) {
    return new GenderAnnotator(name, properties);
  }

  /**
   * Annotate parse trees
   *
   * @param properties Properties that control the behavior of the parser. It use "parse.x" properties.
   * @return A ParserAnnotator
   */
  public Annotator parse(Properties properties) {
    String parserType = properties.getProperty("parse.type", "stanford");
    String maxLenStr = properties.getProperty("parse.maxlen");

    if (parserType.equalsIgnoreCase("stanford")) {
      return new ParserAnnotator("parse", properties);
    } else if (parserType.equalsIgnoreCase("charniak")) {
      String model = properties.getProperty("parse.model");
      String parserExecutable = properties.getProperty("parse.executable");
      if (model == null || parserExecutable == null) {
        throw new RuntimeException("Both parse.model and parse.executable properties must be specified if parse.type=charniak");
      }
      int maxLen = 399;
      if (maxLenStr != null) {
        maxLen = Integer.parseInt(maxLenStr);
      }

      return new CharniakParserAnnotator(model, parserExecutable, false, maxLen);
    } else {
      throw new RuntimeException("Unknown parser type: " + parserType + " (currently supported: stanford and charniak)");
    }
  }

  public Annotator custom(Properties properties, String property) {
    String customName = property;
    String customClassName = properties.getProperty(StanfordCoreNLP.CUSTOM_ANNOTATOR_PREFIX + property);
    if (property.startsWith(StanfordCoreNLP.CUSTOM_ANNOTATOR_PREFIX)) {
      customName = property.substring(StanfordCoreNLP.CUSTOM_ANNOTATOR_PREFIX.length());
      customClassName = properties.getProperty(property);
    }

    try {
      // name + properties
      return new MetaClass(customClassName).createInstance(customName, properties);
    } catch (MetaClass.ConstructorNotFoundException e) {
      try {
        // name
        return new MetaClass(customClassName).createInstance(customName);
      } catch (MetaClass.ConstructorNotFoundException e2) {
        // properties
        try {
          return new MetaClass(customClassName).createInstance(properties);
        } catch (MetaClass.ConstructorNotFoundException e3) {
          // empty arguments
          return new MetaClass(customClassName).createInstance();
        }
      }
    }
  }

  /**
   * Infer the original casing of tokens
   */
  public Annotator trueCase(Properties properties) {
    return new TrueCaseAnnotator(properties);
  }

  /**
   * Annotate for mention (statistical or hybrid)
   */
  public Annotator corefMention(Properties properties) {
    // TO DO: split up coref and mention properties
    Properties corefProperties = PropertiesUtils.extractPrefixedProperties(properties,
            Annotator.STANFORD_COREF + ".",
            true);
    Properties mentionProperties = PropertiesUtils.extractPrefixedProperties(properties,
            Annotator.STANFORD_COREF_MENTION + ".",
            true);

    Properties allPropsForCoref = new Properties();
    allPropsForCoref.putAll(corefProperties);
    allPropsForCoref.putAll(mentionProperties);
    return new CorefMentionAnnotator(allPropsForCoref);
  }

  /**
   * Annotate for coreference (statistical or hybrid)
   */
  public Annotator coref(Properties properties) {
    Properties corefProperties = PropertiesUtils.extractPrefixedProperties(properties,
            Annotator.STANFORD_COREF + ".",
            true);
    Properties mentionProperties = PropertiesUtils.extractPrefixedProperties(properties,
            Annotator.STANFORD_COREF_MENTION + ".",
            true);
    Properties allPropsForCoref = new Properties();
    allPropsForCoref.putAll(corefProperties);
    allPropsForCoref.putAll(mentionProperties);
    return new CorefAnnotator(allPropsForCoref);
  }

  /**
   * Annotate for coreference (deterministic)
   */
  public Annotator dcoref(Properties properties) {
    return new DeterministicCorefAnnotator(properties);
  }

  /**
   * Annotate for relations expressed in sentences
   */
  public Annotator relations(Properties properties) {
    return new RelationExtractorAnnotator(properties);
  }

  /**
   * Annotate for sentiment in sentences
   */
  public Annotator sentiment(Properties properties, String name) {
    return new SentimentAnnotator(name, properties);
  }


  /**
   * Annotate with the column data classifier.
   */
  public Annotator columnData(Properties properties) {
    if (properties.containsKey("classify.loadClassifier")) {
      properties.setProperty("loadClassifier", properties.getProperty("classify.loadClassifier"));
    }
    if (!properties.containsKey("loadClassifier")) {
      throw new RuntimeException("Must load a classifier when creating a column data classifier annotator");
    }
    return new ColumnDataClassifierAnnotator(properties);
  }

  /**
   * Annotate dependency relations in sentences
   */
  public Annotator dependencies(Properties properties) {
    Properties relevantProperties = PropertiesUtils.extractPrefixedProperties(properties,
        Annotator.STANFORD_DEPENDENCIES + '.');
    if (!relevantProperties.containsKey("nthreads") &&
        properties.containsKey("nthreads")) {
      relevantProperties.setProperty("nthreads", properties.getProperty("nthreads"));
    }

    return new DependencyParseAnnotator(relevantProperties);
  }

  /**
   * Annotate operators (e.g., quantifiers) and polarity of tokens in a sentence
   */
  public Annotator natlog(Properties properties) {
    Properties relevantProperties = PropertiesUtils.extractPrefixedProperties(properties,
        Annotator.STANFORD_NATLOG + '.');
    return new NaturalLogicAnnotator(relevantProperties);
  }

  /**
   * Annotate {@link edu.stanford.nlp.ie.util.RelationTriple}s from text.
   */
  public Annotator openie(Properties properties) {
    Properties relevantProperties = PropertiesUtils.extractPrefixedProperties(properties,
        Annotator.STANFORD_OPENIE + '.');
    return new OpenIE(relevantProperties);
  }

  /**
   * Annotate quotes and extract them like sentences
   */
  public Annotator quote(Properties properties) {
	Properties relevantProperties = PropertiesUtils.extractPrefixedProperties(properties,
	    Annotator.STANFORD_QUOTE + '.', true);
	Properties depparseProperties = PropertiesUtils.extractPrefixedProperties(properties,
	    Annotator.STANFORD_DEPENDENCIES + '.');
	for (String key: depparseProperties.stringPropertyNames())  {
	    relevantProperties.setProperty("quote.attribution." + Annotator.STANFORD_DEPENDENCIES + '.' + key,
		depparseProperties.getProperty(key));
		}
    return new QuoteAnnotator(relevantProperties);
  }

  /**
   * Attribute quotes to speakers
   */
  public Annotator quoteattribution(Properties properties) {
    Properties relevantProperties = PropertiesUtils.extractPrefixedProperties(properties,
        Annotator.STANFORD_QUOTE_ATTRIBUTION + '.');
    return new QuoteAttributionAnnotator(relevantProperties);
  }

  /**
   * Add universal dependencies features
   */
  public Annotator udfeats(Properties properties) {
    return new UDFeatureAnnotator();
  }

  /**
   * Annotate for KBP relations
   */
  public Annotator kbp(Properties properties) {
    return new KBPAnnotator(Annotator.STANFORD_KBP, properties);
  }

  public Annotator link(Properties properties) {
    return new WikidictAnnotator(Annotator.STANFORD_LINK, properties);
  }
}
