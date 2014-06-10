package edu.stanford.nlp.ie;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Subclass of ClassifierCombiner that behaves like a NER, by copying the AnswerAnnotation labels to NERAnnotation
 * Also, it runs an additional classifier (QuantifiableEntityNormalizer) to recognize numeric entities
 * @author Mihai Surdeanu
 *
 */
public class NERClassifierCombiner extends ClassifierCombiner<CoreLabel> {

  private final boolean applyNumericClassifiers;
  public static final boolean APPLY_NUMERIC_CLASSIFIERS_DEFAULT = true;
  public static final String APPLY_NUMERIC_CLASSIFIERS_PROPERTY = "ner.applyNumericClassifiers";

  private final boolean useSUTime;

  private final AbstractSequenceClassifier<CoreLabel> nsc;

  public NERClassifierCombiner(Properties props)
    throws FileNotFoundException
  {
    super(props);
    applyNumericClassifiers = PropertiesUtils.getBool(props, APPLY_NUMERIC_CLASSIFIERS_PROPERTY, APPLY_NUMERIC_CLASSIFIERS_DEFAULT);
    useSUTime = PropertiesUtils.getBool(props, NumberSequenceClassifier.USE_SUTIME_PROPERTY, NumberSequenceClassifier.USE_SUTIME_DEFAULT);
    nsc = new NumberSequenceClassifier(new Properties(), useSUTime, props);
  }

  public NERClassifierCombiner(String... loadPaths)
    throws FileNotFoundException
  {
    this(APPLY_NUMERIC_CLASSIFIERS_DEFAULT, NumberSequenceClassifier.USE_SUTIME_DEFAULT, loadPaths);
  }

  public NERClassifierCombiner(boolean applyNumericClassifiers,
                               boolean useSUTime,
                               String... loadPaths)
    throws FileNotFoundException
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
    throws FileNotFoundException
  {
    super(loadPaths);
    this.applyNumericClassifiers = applyNumericClassifiers;
    this.useSUTime = useSUTime;
    this.nsc = new NumberSequenceClassifier(new Properties(), useSUTime, nscProps);
  }

  public NERClassifierCombiner(AbstractSequenceClassifier<CoreLabel>... classifiers)
    throws FileNotFoundException
  {
    this(APPLY_NUMERIC_CLASSIFIERS_DEFAULT, NumberSequenceClassifier.USE_SUTIME_DEFAULT, classifiers);
  }

  public NERClassifierCombiner(boolean applyNumericClassifiers,
                               boolean useSUTime,
                               AbstractSequenceClassifier<CoreLabel>... classifiers)
    throws FileNotFoundException
  {
    super(classifiers);
    this.applyNumericClassifiers = applyNumericClassifiers;
    this.useSUTime = useSUTime;
    this.nsc = new NumberSequenceClassifier(useSUTime);
  }

  public boolean appliesNumericClassifiers() {
    return applyNumericClassifiers;
  }

  public boolean usesSUTime() {
    return useSUTime;
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
        QuantifiableEntityNormalizer.addNormalizedQuantitiesToEntities(output);
      } catch (Exception e) {
        System.err.println("Ignored an exception in QuantifiableEntityNormalizer: (result is that entities were not normalized)");
        System.err.println("Tokens: " + StringUtils.joinWords(tokens, " "));
        e.printStackTrace(System.err);
      } catch(AssertionError e){
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
}

