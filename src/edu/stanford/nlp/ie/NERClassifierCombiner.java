package edu.stanford.nlp.ie;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.StringUtils;

/**
 * Subclass of ClassifierCombiner that behaves like a NER, by copying
 * the AnswerAnnotation labels to NERAnnotation. Also, it can run additional
 * classifiers (NumberSequenceClassifier, QuantifiableEntityNormalizer, SUTime)
 * to recognize numeric and date/time entities, depending on flag settings.
 *
 * @author Mihai Surdeanu
 */
public class NERClassifierCombiner extends ClassifierCombiner<CoreLabel> {

  private final boolean applyNumericClassifiers;
  public static final boolean APPLY_NUMERIC_CLASSIFIERS_DEFAULT = true;
  public static final String APPLY_NUMERIC_CLASSIFIERS_PROPERTY = "ner.applyNumericClassifiers";
  public static final String APPLY_NUMERIC_CLASSIFIERS_PROPERTY_BASE = "applyNumericClassifiers";

  private final boolean useSUTime;

  // todo [cdm 2015]: Could avoid constructing this if applyNumericClassifiers is false
  private final AbstractSequenceClassifier<CoreLabel> nsc;

  public NERClassifierCombiner(Properties props)
    throws IOException
  {
    super(props);
    applyNumericClassifiers = PropertiesUtils.getBool(props, APPLY_NUMERIC_CLASSIFIERS_PROPERTY, APPLY_NUMERIC_CLASSIFIERS_DEFAULT);
    useSUTime = PropertiesUtils.getBool(props, NumberSequenceClassifier.USE_SUTIME_PROPERTY, NumberSequenceClassifier.USE_SUTIME_DEFAULT);
    nsc = new NumberSequenceClassifier(new Properties(), useSUTime, props);
  }

  public NERClassifierCombiner(String... loadPaths)
    throws IOException
  {
    this(APPLY_NUMERIC_CLASSIFIERS_DEFAULT, NumberSequenceClassifier.USE_SUTIME_DEFAULT, loadPaths);
  }

  public NERClassifierCombiner(boolean applyNumericClassifiers,
                               boolean useSUTime,
                               String... loadPaths)
    throws IOException
  {
    super(loadPaths);
    this.applyNumericClassifiers = applyNumericClassifiers;
    this.useSUTime = useSUTime;
    this.nsc = new NumberSequenceClassifier(useSUTime);
  }

  public NERClassifierCombiner(boolean applyNumericClassifiers,
                               boolean useSUTime,
                               Properties nscProps,
                               String... loadPaths)
    throws IOException
  {
    super(nscProps, ClassifierCombiner.extractCombinationModeSafe(nscProps), loadPaths);
    this.applyNumericClassifiers = applyNumericClassifiers;
    this.useSUTime = useSUTime;
    this.nsc = new NumberSequenceClassifier(new Properties(), useSUTime, nscProps);
  }

  @SafeVarargs
  public NERClassifierCombiner(AbstractSequenceClassifier<CoreLabel>... classifiers)
    throws IOException
  {
    this(APPLY_NUMERIC_CLASSIFIERS_DEFAULT, NumberSequenceClassifier.USE_SUTIME_DEFAULT, classifiers);
  }

  @SafeVarargs
  public NERClassifierCombiner(boolean applyNumericClassifiers,
                               boolean useSUTime,
                               AbstractSequenceClassifier<CoreLabel>... classifiers)
    throws IOException
  {
    super(classifiers);
    this.applyNumericClassifiers = applyNumericClassifiers;
    this.useSUTime = useSUTime;
    this.nsc = new NumberSequenceClassifier(useSUTime);
  }

  public static final Set<String> DEFAULT_PASS_DOWN_PROPERTIES =
          CollectionUtils.asSet("encoding", "inputEncoding", "outputEncoding", "maxAdditionalKnownLCWords");

  /** This factory method is used to create the NERClassifierCombiner used in NERCombinerAnnotator
   *  (and, thence, in StanfordCoreNLP).
   *
   *  @param name A "x.y" format property name prefix (the "x" part). This is commonly null,
   *              and then "ner" is used.  If it is the empty string, then no property prefix is used.
   *  @param properties Various properties, including a list in "ner.model".
   *                    The used ones start with name + "." or are in passDownProperties
   *  @return An NERClassifierCombiner with the given properties
   */
  public static NERClassifierCombiner createNERClassifierCombiner(String name, Properties properties) {
    return createNERClassifierCombiner(name, DEFAULT_PASS_DOWN_PROPERTIES, properties);
  }

  /** This factory method is used to create the NERClassifierCombiner used in NERCombinerAnnotator
   *  (and, thence, in StanfordCoreNLP).
   *
   *  @param name A "x.y" format property name prefix (the "x" part). This is commonly null,
   *              and then "ner" is used.  If it is the empty string, then no property prefix is used.
   *  @param passDownProperties Property names for which the property should be passed down
   *              to the NERClassifierCombiner. The default is not to pass down, but pass down is
   *              useful for things like charset encoding.
   *  @param properties Various properties, including a list in "ner.model".
   *                    The used ones start with name + "." or are in passDownProperties
   *  @return An NERClassifierCombiner with the given properties
   */
  public static NERClassifierCombiner createNERClassifierCombiner(String name,
                                                                  Set<String> passDownProperties,
                                                                  Properties properties) {
    String prefix = (name == null) ? "ner." : name.isEmpty() ? "" : name + '.';
    String modelNames = properties.getProperty(prefix + "model");
    if (modelNames == null) {
      modelNames = DefaultPaths.DEFAULT_NER_THREECLASS_MODEL + ',' + DefaultPaths.DEFAULT_NER_MUC_MODEL + ',' +
              DefaultPaths.DEFAULT_NER_CONLL_MODEL;
    }
    // but modelNames can still be empty string is set explicitly to be empty!
    String[] models;
    if ( ! modelNames.isEmpty()) {
      models  = modelNames.split(",");
    } else {
      // Allow for no real NER model - can just use numeric classifiers or SUTime
      System.err.println("WARNING: no NER models specified");
      models = StringUtils.EMPTY_STRING_ARRAY;
    }
    NERClassifierCombiner nerCombiner;
    try {
      boolean applyNumericClassifiers =
              PropertiesUtils.getBool(properties,
                      prefix + APPLY_NUMERIC_CLASSIFIERS_PROPERTY_BASE,
                      APPLY_NUMERIC_CLASSIFIERS_DEFAULT);
      boolean useSUTime =
              PropertiesUtils.getBool(properties,
                      prefix + NumberSequenceClassifier.USE_SUTIME_PROPERTY_BASE,
                      NumberSequenceClassifier.USE_SUTIME_DEFAULT);
      Properties combinerProperties = PropertiesUtils.extractSelectedProperties(properties, passDownProperties);
      nerCombiner = new NERClassifierCombiner(applyNumericClassifiers,
              useSUTime, combinerProperties, models);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    return nerCombiner;
  }

  public boolean appliesNumericClassifiers() {
    return applyNumericClassifiers;
  }

  public boolean usesSUTime() {
    // if applyNumericClassifiers is false, SUTime isn't run regardless of setting of useSUTime
    return useSUTime && applyNumericClassifiers;
  }

  private static <INN extends CoreMap> void copyAnswerFieldsToNERField(List<INN> l) {
    for (INN m: l) {
      m.set(CoreAnnotations.NamedEntityTagAnnotation.class, m.get(CoreAnnotations.AnswerAnnotation.class));
    }
  }

  @Override
  public List<CoreLabel> classify(List<CoreLabel> tokens) {
    return classifyWithGlobalInformation(tokens, null, null);
  }

  @Override
  public List<CoreLabel> classifyWithGlobalInformation(List<CoreLabel> tokens, final CoreMap document, final CoreMap sentence) {
    List<CoreLabel> output = super.classify(tokens);
    if (applyNumericClassifiers) {
      try {
        // recognizes additional MONEY, TIME, DATE, and NUMBER using a set of deterministic rules
        // note: some DATE and TIME entities are recognized by our statistical NER based on MUC
        // note: this includes SUTime
        // note: requires TextAnnotation, PartOfSpeechTagAnnotation, and AnswerAnnotation
        // note: this sets AnswerAnnotation!
        recognizeNumberSequences(output, document, sentence);
      } catch (RuntimeInterruptedException e) {
        throw e;
      } catch (Exception e) {
        System.err.println("Ignored an exception in NumberSequenceClassifier: (result is that some numbers were not classified)");
        System.err.println("Tokens: " + StringUtils.joinWords(tokens, " "));
        e.printStackTrace(System.err);
      }

      // AnswerAnnotation -> NERAnnotation
      copyAnswerFieldsToNERField(output);

      try {
        // normalizes numeric entities such as MONEY, TIME, DATE, or PERCENT
        // note: this uses and sets NamedEntityTagAnnotation!
        QuantifiableEntityNormalizer.addNormalizedQuantitiesToEntities(output, false, useSUTime);
      } catch (Exception e) {
        System.err.println("Ignored an exception in QuantifiableEntityNormalizer: (result is that entities were not normalized)");
        System.err.println("Tokens: " + StringUtils.joinWords(tokens, " "));
        e.printStackTrace(System.err);
      } catch(AssertionError e) {
        System.err.println("Ignored an assertion in QuantifiableEntityNormalizer: (result is that entities were not normalized)");
        System.err.println("Tokens: " + StringUtils.joinWords(tokens, " "));
        e.printStackTrace(System.err);
      }
    } else {
      // AnswerAnnotation -> NERAnnotation
      copyAnswerFieldsToNERField(output);
    }
    return output;
  }

  private void recognizeNumberSequences(List<CoreLabel> words, final CoreMap document, final CoreMap sentence) {
    // we need to copy here because NumberSequenceClassifier overwrites the AnswerAnnotation
    List<CoreLabel> newWords = NumberSequenceClassifier.copyTokens(words, sentence);

    nsc.classifyWithGlobalInformation(newWords, document, sentence);

    // copy AnswerAnnotation back. Do not overwrite!
    // also, copy all the additional annotations generated by SUTime and NumberNormalizer
    for (int i = 0, sz = words.size(); i < sz; i++){
      CoreLabel origWord = words.get(i);
      CoreLabel newWord = newWords.get(i);

      // System.err.println(newWord.word() + " => " + newWord.get(CoreAnnotations.AnswerAnnotation.class) + " " + origWord.ner());

      String before = origWord.get(CoreAnnotations.AnswerAnnotation.class);
      String newGuess = newWord.get(CoreAnnotations.AnswerAnnotation.class);
      if ((before == null || before.equals(nsc.flags.backgroundSymbol) || before.equals("MISC")) && !newGuess.equals(nsc.flags.backgroundSymbol)) {
        origWord.set(CoreAnnotations.AnswerAnnotation.class, newGuess);
      }

      // transfer other annotations generated by SUTime or NumberNormalizer
      NumberSequenceClassifier.transferAnnotations(newWord, origWord);
    }
  }

  public void finalizeAnnotation(Annotation annotation) {
    nsc.finalizeClassification(annotation);
  }

  /** The main method. Very basic, could usefully be expanded and common code shared with other methods. */
  public static void main(String[] args) throws Exception {
    StringUtils.printErrInvocationString("NERClassifierCombiner", args);
    Properties props = StringUtils.argsToProperties(args);
    NERClassifierCombiner ncc = createNERClassifierCombiner("", props);

    String textFile = props.getProperty("textFile");
    if (textFile != null) {
      ncc.classifyAndWriteAnswers(textFile);
    }
  }

}

