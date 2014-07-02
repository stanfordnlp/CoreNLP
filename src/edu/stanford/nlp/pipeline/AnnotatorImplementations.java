package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.util.ReflectionLoading;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.Set;

/**
 * A class abstracting the implementation of various annotators.
 * Importantly, subclasses of this class can overwrite the implementation
 * of these annotators by returning a different annotator, and
 * {@link edu.stanford.nlp.pipeline.StanfordCoreNLP} will automatically load
 * the new annotator instead.
 *
 * @author Gabor Angeli
 */
public class AnnotatorImplementations {

  /**
   * Tokenize, according to whitespace only
   */
  public Annotator whitespaceTokenizer(Properties properties) {
    return new WhitespaceTokenizerAnnotator(properties);
  }

  /**
   * Tokenize, emulating the Penn Treebank
   */
  public Annotator ptbTokenizer(Properties properties, boolean verbose, String options) {
    return new PTBTokenizerAnnotator(verbose, options);
  }

  /**
   * Clean XML input
   */
  public CleanXmlAnnotator cleanXML(Properties properties,
                               String xmlTagsToRemove,
                               String sentenceEndingTags,
                               String dateTags,
                               boolean allowFlawedXml) {
    return new CleanXmlAnnotator(xmlTagsToRemove, sentenceEndingTags, dateTags, allowFlawedXml);
  }

  /**
   * Sentence split, in addition to a bunch of other things in this annotator (be careful to check the implementation!)
   */
  public Annotator wordToSentences(Properties properties,
                                      boolean verbose, String boundaryTokenRegex,
                                      Set<String> boundaryToDiscard, Set<String> htmlElementsToDiscard,
                                      String newlineIsSentenceBreak, String boundaryMultiTokenRegex,
                                      Set<String> tokenRegexesToDiscard) {
    return new WordsToSentencesAnnotator(verbose, boundaryTokenRegex, boundaryToDiscard, htmlElementsToDiscard,
        newlineIsSentenceBreak, boundaryMultiTokenRegex, tokenRegexesToDiscard);
  }

  /**
   * Part of speech tag
   */
  public Annotator posTagger(Properties properties, String annotatorName) {
    return new POSTaggerAnnotator(annotatorName, properties);
  }

  /**
   * Annotate lemmas
   */
  public Annotator morpha(Properties properties, boolean verbose) {
    return new MorphaAnnotator(verbose);
  }

  /**
   * Annotate for named entities -- note that this combines multiple NER tag sets, and some auxilliary things (like temporal tagging)
   */
  public Annotator ner(Properties properties,
                          boolean applyNumericClassifiers,
                          boolean useSUTime,
                          boolean verbose,
                          String... loadPaths) throws FileNotFoundException {
    NERClassifierCombiner nerCombiner = new NERClassifierCombiner(applyNumericClassifiers,
        useSUTime, properties,
        loadPaths);
    return new NERCombinerAnnotator(nerCombiner, verbose);
  }

  /**
   * Run RegexNER -- rule-based NER based on a deterministic mapping file
   */
  public Annotator tokensRegexNER(Properties properties, String name) {
    return new TokensRegexNERAnnotator("regexner", properties);
  }

  /**
   * Annotate for gender of tokens
   */
  public Annotator gender(Properties properties, boolean verbose) {
    return new GenderAnnotator(false, properties.getProperty("gender.firstnames", DefaultPaths.DEFAULT_GENDER_FIRST_NAMES));
  }

  /**
   * Annotate parse trees
   *
   * @param properties
   * @return
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
    String customName = property.substring(StanfordCoreNLP
            .CUSTOM_ANNOTATOR_PREFIX.length());
    String customClassName = properties.getProperty(property);

    return ReflectionLoading.loadByReflection(customClassName, customName,
            properties);
  }

  /**
   * Infer the original casing of tokens
   */
  public Annotator trueCase(Properties properties, String modelLoc,
                               String classBias,
                               String mixedCaseFileName,
                               boolean verbose) {
    return new TrueCaseAnnotator(modelLoc, classBias, mixedCaseFileName, verbose);
  }

  /**
   * Annotate for coreference
   */
  public Annotator coref(Properties properties) {
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

}
