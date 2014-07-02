package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;

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
  protected Annotator whitespaceTokenizer(Properties properties) {
    return new WhitespaceTokenizerAnnotator(properties);
  }

  /**
   * Tokenize, emulating the Penn Treebank
   */
  protected Annotator ptbTokenizer(Properties properties, boolean verbose, String options) {
    return new PTBTokenizerAnnotator(verbose, options);
  }

  /**
   * Clean XML input
   */
  protected CleanXmlAnnotator cleanXML(Properties properties,
                               String xmlTagsToRemove,
                               String sentenceEndingTags,
                               String dateTags,
                               boolean allowFlawedXml) {
    return new CleanXmlAnnotator(xmlTagsToRemove, sentenceEndingTags, dateTags, allowFlawedXml);
  }

  /**
   * Sentence split, in addition to a bunch of other things in this annotator (be careful to check the implementation!)
   */
  protected Annotator wordToSentences(Properties properties,
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
  protected Annotator posTagger(Properties properties, String annotatorName) {
    return new POSTaggerAnnotator(annotatorName, properties);
  }

  /**
   * Annotate lemmas
   */
  protected Annotator morpha(Properties properties, boolean verbose) {
    return new MorphaAnnotator(verbose);
  }

  /**
   * Annotate for named entities -- note that this combines multiple NER tag sets, and some auxilliary things (like temporal tagging)
   */
  protected Annotator ner(Properties properties,
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
  protected Annotator tokensRegexNER(Properties properties, String name) {
    return new TokensRegexNERAnnotator("regexner", properties);
  }

  /**
   * Annotate for gender of tokens
   */
  protected Annotator gender(Properties properties, boolean verbose) {
    return new GenderAnnotator(false, properties.getProperty("gender.firstnames", DefaultPaths.DEFAULT_GENDER_FIRST_NAMES));
  }

  /**
   * Infer the original casing of tokens
   */
  protected Annotator trueCase(Properties properties, String modelLoc,
                               String classBias,
                               String mixedCaseFileName,
                               boolean verbose) {
    return new TrueCaseAnnotator(modelLoc, classBias, mixedCaseFileName, verbose);
  }

  /**
   * Annotate for coreference
   */
  protected Annotator coref(Properties properties) {
    return new DeterministicCorefAnnotator(properties);
  }

  /**
   * Annotate for relations expressed in sentences
   */
  protected Annotator relations(Properties properties) {
    return new RelationExtractorAnnotator(properties);
  }

  /**
   * Annotate for sentiment in sentences
   */
  public Annotator sentiment(Properties properties, String name) {
    return new SentimentAnnotator(name, properties);
  }

}
