package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.types.Tags;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.TimeExpression;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;


/**
 * This class will add NER information to an Annotation using a combination of NER models.
 * It assumes that the Annotation already contains the tokenized words in sentences
 * under {@code CoreAnnotations.SentencesAnnotation.class} as
 * {@code List<? extends CoreLabel>}} or a
 * {@code List<List<? extends CoreLabel>>} under {@code Annotation.WORDS_KEY}
 * and adds NER information to each CoreLabel,
 * in the {@code CoreLabel.NER_KEY} field.  It uses
 * the NERClassifierCombiner class in the ie package.
 *
 * @author Jenny Finkel
 * @author Mihai Surdeanu (modified it to work with the new NERClassifierCombiner)
 */
public class NERCombinerAnnotator extends SentenceAnnotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(NERCombinerAnnotator.class);

  private final NERClassifierCombiner ner;

  // options for specifying only using rules or only using the statistical model
  // default is to use the full pipeline
  private boolean rulesOnly = false;
  private boolean statisticalOnly = false;

  private final boolean VERBOSE;
  private boolean setDocDate = false;

  private final long maxTime;
  private final int nThreads;
  private final int maxSentenceLength;
  private final boolean applyNumericClassifiers;
  private LanguageInfo.HumanLanguage language = LanguageInfo.HumanLanguage.ENGLISH;

  /**
   * Apply NER-specific tokenization before running NER modules (e.g. merge together tokens split by hyphen)
   */
  private boolean useNERSpecificTokenization = true;
  private static final HashSet<String> nerSpecificTokenizationExceptions =
      new HashSet<>(Arrays.asList("based", "area", "registered", "headquartered", "native", "born", "raised", 
                                  "backed", "controlled", "owned", "resident", "trained", "educated"));

  /**
   * Helper class for aligning merged tokens and original tokens, stores number of merged tokens
   * this merged token contains (e.g. All - Star --> All-Star, 2)
   */
  public static class TokenMergeCountAnnotation implements CoreAnnotation<Integer> {
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  private static final String spanishNumberRegexRules =
      "edu/stanford/nlp/models/kbp/spanish/gazetteers/kbp_regexner_number_sp.tag";

  private TokensRegexNERAnnotator spanishNumberAnnotator;

  /** fine grained ner **/
  private boolean applyFineGrained = true;
  private TokensRegexNERAnnotator fineGrainedNERAnnotator;

  /** additional rules ner - add your own additional regexner rules after fine grained phase **/
  private boolean applyAdditionalRules = true;
  private TokensRegexNERAnnotator additionalRulesNERAnnotator;

  /** run tokensregex rules before the entity building phase **/
  private boolean applyTokensRegexRules = false;
  private TokensRegexAnnotator tokensRegexAnnotator;

  /** entity mentions **/
  private boolean buildEntityMentions = true;
  private EntityMentionsAnnotator entityMentionsAnnotator;

  /** doc date finding **/
  private DocDateAnnotator docDateAnnotator;


  public NERCombinerAnnotator(Properties properties) throws IOException {
    // TODO: this is basically the same as the block in ie.NERClassifierCombiner.  Refactor

    // if rulesOnly is set, just run the rules-based NER
    rulesOnly = PropertiesUtils.getBool(properties, "ner.rulesOnly", false);
    // if statisticalOnly is set, just run statistical models
    statisticalOnly = PropertiesUtils.getBool(properties, "ner.statisticalOnly", false);
    // set up models list
    List<String> models = new ArrayList<>();
    // check for rulesOnly
    if (!rulesOnly) {
      String modelNames = properties.getProperty("ner.model");
      if (modelNames == null) {
        modelNames = DefaultPaths.DEFAULT_NER_THREECLASS_MODEL + ',' + DefaultPaths.DEFAULT_NER_MUC_MODEL + ',' + DefaultPaths.DEFAULT_NER_CONLL_MODEL;
      }
      modelNames = modelNames.trim();
      if (!modelNames.isEmpty()) {
        models.addAll(Arrays.asList(modelNames.split(",")));
      }
      if (models.isEmpty()) {
        // Allow for no real NER model - can just use numeric classifiers or SUTime.
        // Have to unset ner.model, so unlikely that people got here by accident.
        log.info("WARNING: no NER models specified");
      }
    }

    this.applyNumericClassifiers = PropertiesUtils.getBool(properties,
        NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_PROPERTY,
        NERClassifierCombiner.APPLY_NUMERIC_CLASSIFIERS_DEFAULT) && !statisticalOnly;

    boolean useSUTime =
        PropertiesUtils.getBool(properties,
            NumberSequenceClassifier.USE_SUTIME_PROPERTY,
            NumberSequenceClassifier.USE_SUTIME_DEFAULT) && !statisticalOnly;

    NERClassifierCombiner.Language nerLanguage = NERClassifierCombiner.Language.fromString(PropertiesUtils.getString(properties,
        NERClassifierCombiner.NER_LANGUAGE_PROPERTY, null), NERClassifierCombiner.NER_LANGUAGE_DEFAULT);

    boolean verbose = PropertiesUtils.getBool(properties, "ner." + "verbose", false);

    String[] loadPaths = models.toArray(new String[models.size()]);

    Properties combinerProperties = PropertiesUtils.extractSelectedProperties(properties,
        NERClassifierCombiner.DEFAULT_PASS_DOWN_PROPERTIES);
    if (useSUTime) {
      // Make sure SUTime parameters are included
      Properties sutimeProps = PropertiesUtils.extractPrefixedProperties(properties, NumberSequenceClassifier.SUTIME_PROPERTY  + '.', true);
      PropertiesUtils.overWriteProperties(combinerProperties, sutimeProps);
    }
    NERClassifierCombiner nerCombiner = new NERClassifierCombiner(applyNumericClassifiers, nerLanguage,
        useSUTime, combinerProperties, loadPaths);

    this.nThreads = PropertiesUtils.getInt(properties, "ner.nthreads", PropertiesUtils.getInt(properties, "nthreads", 1));
    this.maxTime = PropertiesUtils.getLong(properties, "ner.maxtime", 0);
    this.maxSentenceLength = PropertiesUtils.getInt(properties, "ner.maxlen", Integer.MAX_VALUE);
    this.language =
        LanguageInfo.getLanguageFromString(PropertiesUtils.getString(properties, "ner.language", "en"));

    // set whether or not to apply NER-specific tokenization (e.g. merge tokens separated by hyphens)
    useNERSpecificTokenization = PropertiesUtils.getBool(properties, "ner.useNERSpecificTokenization", true);

    // in case of Spanish, use the Spanish number regexner annotator
    if (language.equals(LanguageInfo.HumanLanguage.SPANISH)) {
      Properties spanishNumberRegexNerProperties = new Properties();
      spanishNumberRegexNerProperties.setProperty("spanish.number.regexner.mapping", spanishNumberRegexRules);
      spanishNumberRegexNerProperties.setProperty("spanish.number.regexner.validpospattern", "NUM.*");
      spanishNumberRegexNerProperties.setProperty("spanish.number.regexner.ignorecase", "true");
      spanishNumberAnnotator = new TokensRegexNERAnnotator("spanish.number.regexner",
          spanishNumberRegexNerProperties);
    }

    // set up fine grained ner
    setUpFineGrainedNER(properties);

    // set up additional rules ner
    setUpAdditionalRulesNER(properties);

    // set up tokens regex rules
    setUpTokensRegexRules(properties);

    // set up entity mentions
    setUpEntityMentionBuilding(properties);

    // set up doc date finding if specified
    setUpDocDateAnnotator(properties);

    log.info("numeric classifiers: " + applyNumericClassifiers +
            "; SUTime: " + useSUTime + (docDateAnnotator != null ? " " + docDateAnnotator: " [no docDate]") +
            "; fine grained: " + this.applyFineGrained);

    VERBOSE = verbose;
    this.ner = nerCombiner;
  }


  // TODO evaluate necessity of these legacy constructors, primarily used in testing,
  // we should probably get rid of them
  public NERCombinerAnnotator() throws IOException, ClassNotFoundException {
    this(true);
  }

  public NERCombinerAnnotator(boolean verbose) throws IOException, ClassNotFoundException {
    this(new NERClassifierCombiner(new Properties()), verbose);
  }

  public NERCombinerAnnotator(boolean verbose, String... classifiers)
    throws IOException, ClassNotFoundException {
    this(new NERClassifierCombiner(classifiers), verbose);
  }

  public NERCombinerAnnotator(NERClassifierCombiner ner, boolean verbose) {
    this(ner, verbose, 1, 0, Integer.MAX_VALUE);
  }

  public NERCombinerAnnotator(NERClassifierCombiner ner, boolean verbose, int nThreads, long maxTime) {
    this(ner, verbose, nThreads, maxTime, Integer.MAX_VALUE);
  }

  public NERCombinerAnnotator(NERClassifierCombiner ner, boolean verbose, int nThreads, long maxTime, int maxSentenceLength) {
    this(ner, verbose, nThreads, maxTime, maxSentenceLength, true, true);
  }

  public NERCombinerAnnotator(NERClassifierCombiner ner, boolean verbose, int nThreads, long maxTime,
                              int maxSentenceLength, boolean fineGrained, boolean entityMentions) {
    VERBOSE = verbose;
    this.ner = ner;
    this.maxTime = maxTime;
    this.nThreads = nThreads;
    this.maxSentenceLength = maxSentenceLength;
    this.applyNumericClassifiers = true;
    this.useNERSpecificTokenization = false;
    Properties nerProperties = new Properties();
    nerProperties.setProperty("ner.applyFineGrained", Boolean.toString(fineGrained));
    nerProperties.setProperty("ner.buildEntityMentions", Boolean.toString(entityMentions));
    setUpAdditionalRulesNER(nerProperties);
    setUpFineGrainedNER(nerProperties);
    setUpEntityMentionBuilding(nerProperties);
  }

  /**
   * Set up the fine-grained TokensRegexNERAnnotator sub-annotator
   *
   * @param properties Properties for the TokensRegexNER sub-annotator
   */
  private void setUpFineGrainedNER(Properties properties) {
    // set up fine grained ner
    this.applyFineGrained =
        PropertiesUtils.getBool(properties, "ner.applyFineGrained", true) && !statisticalOnly;
    if (this.applyFineGrained) {
      String fineGrainedPrefix = "ner.fine.regexner";
      Properties fineGrainedProps =
          PropertiesUtils.extractPrefixedProperties(properties, fineGrainedPrefix+ '.', true);
      // explicity set fine grained ner default here
      if (!fineGrainedProps.containsKey("ner.fine.regexner.mapping"))
        fineGrainedProps.setProperty("ner.fine.regexner.mapping", DefaultPaths.DEFAULT_KBP_TOKENSREGEX_NER_SETTINGS);
      // build the fine grained ner TokensRegexNERAnnotator
      fineGrainedNERAnnotator = new TokensRegexNERAnnotator(fineGrainedPrefix, fineGrainedProps);
    }
  }

  /**
   * Set up the additional TokensRegexNERAnnotator sub-annotator
   *
   * @param properties Properties for the TokensRegexNER sub-annotator
   */
  private void setUpAdditionalRulesNER(Properties properties) {
    this.applyAdditionalRules =
        (!properties.getProperty("ner.additional.regexner.mapping", "").isEmpty()) && !statisticalOnly;
    if (this.applyAdditionalRules) {
      String additionalRulesPrefix = "ner.additional.regexner";
      Properties additionalRulesProps =
          PropertiesUtils.extractPrefixedProperties(properties, additionalRulesPrefix+ '.', true);
      // build the additional rules ner TokensRegexNERAnnotator
      additionalRulesNERAnnotator = new TokensRegexNERAnnotator(additionalRulesPrefix, additionalRulesProps);
    }
  }

  /**
   * Set up the TokensRegexAnnotator sub-annotator
   *
   * @param properties Properties for the TokensRegex sub-annotator
   */
  private void setUpTokensRegexRules(Properties properties) {
    this.applyTokensRegexRules =
        (!properties.getProperty("ner.additional.tokensregex.rules", "").isEmpty())
            && !statisticalOnly;
    if (this.applyTokensRegexRules) {
      String tokensRegexRulesPrefix = "ner.additional.tokensregex";
      Properties tokensRegexRulesProps =
          PropertiesUtils.extractPrefixedProperties(properties, tokensRegexRulesPrefix+ '.', true);
      // build the additional rules ner TokensRegexNERAnnotator
      tokensRegexAnnotator = new TokensRegexAnnotator(tokensRegexRulesPrefix, tokensRegexRulesProps);
    }
  }

  /**
   * Set up the additional EntityMentionsAnnotator sub-annotator
   *
   * @param properties Properties for the EntityMentionsAnnotator sub-annotator
   */
  private void setUpEntityMentionBuilding(Properties properties) {
    this.buildEntityMentions = PropertiesUtils.getBool(properties, "ner.buildEntityMentions", true);
    if (this.buildEntityMentions) {
      String entityMentionsPrefix = "ner.entitymentions";
      Properties entityMentionsProps =
          PropertiesUtils.extractPrefixedProperties(properties, entityMentionsPrefix+ '.', true);
      // pass language info to the entity mention annotator
      entityMentionsProps.setProperty("ner.entitymentions.language", language.name());
      entityMentionsAnnotator = new EntityMentionsAnnotator(entityMentionsPrefix, entityMentionsProps);
    }
  }

  /**
   * Set up the additional DocDateAnnotator sub-annotator
   *
   * @param properties Properties for the DocDateAnnotator sub-annotator
   */
  private void setUpDocDateAnnotator(Properties properties) throws IOException {
    for (String property : properties.stringPropertyNames()) {
      if (property.startsWith("ner.docdate")) {
        setDocDate = true;
        docDateAnnotator = new DocDateAnnotator("ner.docdate", properties);
        break;
      }
    }
  }

  @Override
  protected int nThreads() {
    return nThreads;
  }

  @Override
  protected long maxTime() {
    return maxTime;
  }

  /** Check that after() is not null and the empty string **/
  public static Function<CoreLabel, Boolean> afterIsEmpty = tok ->
      tok.containsKey(CoreAnnotations.AfterAnnotation.class) && tok.after().equals("");


  /**
   * Helper method for creating NER-specific tokenization
   **/
  public static void mergeTokens(CoreLabel token, CoreLabel nextToken) {
    // NOTE: right now the merged tokens get the part-of-speech tag of the first token
    token.setWord(token.word() + nextToken.word());
    token.setAfter(nextToken.after());
    token.setEndPosition(nextToken.endPosition());
    token.setValue(token.word()+"-"+token.sentIndex());
    // update number of merges that went into building this token
    if (token.get(TokenMergeCountAnnotation.class) == null) {
      token.set(TokenMergeCountAnnotation.class, 1);
    } else {
      token.set(TokenMergeCountAnnotation.class, token.get(TokenMergeCountAnnotation.class) + 1);
    }
  }

  /**
   * Create a copy of an Annotation with NER specific tokenization (e.g. merge hyphen split tokens into one token).
   *
   * @param originalAnnotation The original annotation
   * @return Annotation with NER specific tokenization
   */
  private static Annotation annotationWithNERTokenization(Annotation originalAnnotation) {
    // start to make copy with same document text
    Annotation copyAnnotation = new Annotation(originalAnnotation.get(CoreAnnotations.TextAnnotation.class));
    // create new sentences with NER-specific tokenization
    copyAnnotation.set(CoreAnnotations.SentencesAnnotation.class, new ArrayList<>());
    copyAnnotation.set(CoreAnnotations.DocDateAnnotation.class, originalAnnotation.get(CoreAnnotations.DocDateAnnotation.class));
    copyAnnotation.set(CoreAnnotations.DocIDAnnotation.class, originalAnnotation.get(CoreAnnotations.DocIDAnnotation.class));
    for (CoreMap sentence : originalAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreLabel> originalTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      List<CoreLabel> copyTokens = new ArrayList<>();
      int nextTokenIndex = 0;
      for (CoreLabel currToken : originalTokens) {
        nextTokenIndex++;
        CoreLabel processedToken = new CoreLabel(currToken);
        CoreLabel lastProcessedToken =
            copyTokens.size() > 0 ? copyTokens.get(copyTokens.size() - 1) : null;
        if (lastProcessedToken != null && afterIsEmpty.apply(lastProcessedToken) && currToken.word().equals("-")) {
          // only merge if there is another token to the right, and it's not something
          // like "based" or "area"...handle corner case of Chicago-area
          if (nextTokenIndex < originalTokens.size() &&
              !nerSpecificTokenizationExceptions.contains(originalTokens.get(nextTokenIndex).word())) {
            mergeTokens(lastProcessedToken, currToken);
          } else {
            copyTokens.add(processedToken);
          }
        } else if (lastProcessedToken != null && lastProcessedToken.word().endsWith("-") &&
            afterIsEmpty.apply(lastProcessedToken) && !nerSpecificTokenizationExceptions.contains(currToken.word())) {
          mergeTokens(lastProcessedToken, currToken);
        } else {
          copyTokens.add(processedToken);
        }
      }
      Annotation copySentence = new Annotation(sentence.get(CoreAnnotations.TextAnnotation.class));
      copySentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class,
          sentence.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
      copySentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class,
          sentence.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
      copySentence.set(CoreAnnotations.SentenceIndexAnnotation.class,
          sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
      copySentence.set(CoreAnnotations.TokensAnnotation.class, copyTokens);
      copyAnnotation.get(CoreAnnotations.SentencesAnnotation.class).add(copySentence);
    }
    copyAnnotation.set(CoreAnnotations.TokensAnnotation.class, new ArrayList<>());
    int globalTokenIndex = 0;
    for (CoreMap sentence : copyAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        token.set(CoreAnnotations.TokenBeginAnnotation.class, globalTokenIndex);
        token.set(CoreAnnotations.TokenEndAnnotation.class, globalTokenIndex+1);
        copyAnnotation.get(CoreAnnotations.TokensAnnotation.class).add(token);
        globalTokenIndex++;
      }
      sentence.set(CoreAnnotations.TokenBeginAnnotation.class,
          sentence.get(CoreAnnotations.TokensAnnotation.class).get(0).get(CoreAnnotations.TokenBeginAnnotation.class));
      sentence.set(CoreAnnotations.TokenEndAnnotation.class,
          sentence.get(CoreAnnotations.TokensAnnotation.class).get(sentence.get(
              CoreAnnotations.TokensAnnotation.class).size()-1).get(CoreAnnotations.TokenEndAnnotation.class));
    }
    return copyAnnotation;
  }

  /**
   * Copy NER annotations from the NER-tokenized annotation.  nerTokenizedAnnotation should have the same text
   * as originalAnnotation,
   * @param nerTokenizedAnnotation
   * @param originalAnnotation
   */

  public static void transferNERAnnotationsToAnnotation(Annotation nerTokenizedAnnotation, Annotation originalAnnotation) {
    // annotations might have no tokens if empty strings are annotated
    if (nerTokenizedAnnotation.get(CoreAnnotations.TokensAnnotation.class).isEmpty() ||
        originalAnnotation.get(CoreAnnotations.TokensAnnotation.class).isEmpty())
      return;
    // list of all NER related keys
    List<Class> nerKeys = Arrays.asList(CoreAnnotations.NamedEntityTagAnnotation.class,
        CoreAnnotations.NormalizedNamedEntityTagAnnotation.class,
        CoreAnnotations.NamedEntityTagProbsAnnotation.class,
        CoreAnnotations.FineGrainedNamedEntityTagAnnotation.class,
        CoreAnnotations.CoarseNamedEntityTagAnnotation.class, TimeAnnotations.TimexAnnotation.class,
        CoreAnnotations.NumericValueAnnotation.class, CoreAnnotations.NumericTypeAnnotation.class,
        CoreAnnotations.NumericCompositeValueAnnotation.class, CoreAnnotations.NumericCompositeTypeAnnotation.class);
    List<CoreLabel> originalTokens = originalAnnotation.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreLabel> nerTokenizedTokens = nerTokenizedAnnotation.get(CoreAnnotations.TokensAnnotation.class);
    int nerTokenizedIdx = 0;
    int mergeCount = 0;
    int originalIdx = 0;
    for (CoreMap sentence : originalAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (CoreLabel origToken : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        // get current tokens to align
        CoreLabel nerTokenizedToken = nerTokenizedTokens.get(nerTokenizedIdx);
        // copy data
        for (Class c : nerKeys) {
          if (nerTokenizedToken.get(c) != null)
            origToken.set(c, nerTokenizedToken.get(c));
        }
        // increment index into ner tokenization list, if on a merged token, stay on this till exhausted
        // check if first time on merged token
        if (mergeCount == 0 && nerTokenizedToken.get(TokenMergeCountAnnotation.class) != null) {
          mergeCount = nerTokenizedToken.get(TokenMergeCountAnnotation.class);
        } else if (mergeCount > 1) {
          // check if in middle of processing merged token
          // drop the mergeIdx, but dont leave this token in the ner tokenized list
          mergeCount--;
        } else {
          // move on if mergeIdx exhausted or this isn't a merge token
          mergeCount = 0;
          nerTokenizedIdx++;
        }
      }
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      log.info("Adding NER Combiner annotation ... ");
    }

    // potentially make a copy of the annotation with NER-specific tokenization
    Annotation nerAnnotation;
    if (useNERSpecificTokenization)
      nerAnnotation = annotationWithNERTokenization(annotation);
    else
      nerAnnotation = annotation;

    // set the doc date if using a doc date annotator
    if (setDocDate)
      docDateAnnotator.annotate(nerAnnotation);

    super.annotate(nerAnnotation);
    this.ner.finalizeAnnotation(nerAnnotation);

    if (VERBOSE) {
      log.info("done.");
    }
    // if Spanish, run the regexner with Spanish number rules
    if (LanguageInfo.HumanLanguage.SPANISH.equals(language) && this.applyNumericClassifiers)
      spanishNumberAnnotator.annotate(nerAnnotation);

    // perform safety clean up
    // MONEY and NUMBER ner tagged items should not have Timex values
    for (CoreLabel token : nerAnnotation.get(CoreAnnotations.TokensAnnotation.class)) {
      if (token == null || token.ner() == null)
        continue;
      if (token.ner().equals("MONEY") || token.ner().equals("NUMBER"))
        token.remove(TimeAnnotations.TimexAnnotation.class);
    }

    // if fine grained ner is requested, run that
    if (!statisticalOnly && (this.applyFineGrained || this.applyAdditionalRules || this.applyTokensRegexRules)) {
      // run the fine grained NER
      if (this.applyFineGrained)
        fineGrainedNERAnnotator.annotate(nerAnnotation);
      // run the custom rules specified
      if (this.applyAdditionalRules)
        additionalRulesNERAnnotator.annotate(nerAnnotation);
      // run tokens regex
      if (this.applyTokensRegexRules)
        tokensRegexAnnotator.annotate(nerAnnotation);
      // set the FineGrainedNamedEntityTagAnnotation.class
      for (CoreLabel token : nerAnnotation.get(CoreAnnotations.TokensAnnotation.class)) {
        String fineGrainedTag = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        token.set(CoreAnnotations.FineGrainedNamedEntityTagAnnotation.class, fineGrainedTag);
      }
    }

    // set confidence for anything not already set to n.e. tag, -1.0
    for (CoreLabel token : nerAnnotation.get(CoreAnnotations.TokensAnnotation.class)) {
      if (token.get(CoreAnnotations.NamedEntityTagProbsAnnotation.class) == null) {
        Map<String,Double> labelToProb = Collections.singletonMap(token.ner(), -1.0);
        token.set(CoreAnnotations.NamedEntityTagProbsAnnotation.class, labelToProb);
      }
    }

    // transfer annotations from NER-tokenized Annotation to the actual Annotation
    if (useNERSpecificTokenization)
      transferNERAnnotationsToAnnotation(nerAnnotation, annotation);

    // if entity mentions should be built, run that
    if (this.buildEntityMentions) {
      entityMentionsAnnotator.annotate(annotation);
    }

  }

  @Override
  public void doOneSentence(Annotation annotation, CoreMap sentence) {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreLabel> output; // only used if try assignment works.
    if (tokens.size() <= this.maxSentenceLength) {
      try {
        output = this.ner.classifySentenceWithGlobalInformation(tokens, annotation, sentence);
      } catch (RuntimeInterruptedException e) {
        // If we get interrupted, set the NER labels to the background
        // symbol if they are not already set, then exit.
        output = null;
      }
    } else {
      output = null;
    }
    if (output == null) {
      doOneFailedSentence(annotation, sentence);
    } else {
      for (int i = 0, sz = tokens.size(); i < sz; ++i) {
        // add the named entity tag to each token
        String neTag = output.get(i).get(CoreAnnotations.NamedEntityTagAnnotation.class);
        String normNeTag = output.get(i).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
        Map<String,Double> neTagProbMap = output.get(i).get(CoreAnnotations.NamedEntityTagProbsAnnotation.class);
        tokens.get(i).setNER(neTag);
        tokens.get(i).set(CoreAnnotations.NamedEntityTagProbsAnnotation.class, neTagProbMap);
        tokens.get(i).set(CoreAnnotations.CoarseNamedEntityTagAnnotation.class, neTag);
        if (normNeTag != null) tokens.get(i).set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, normNeTag);
        NumberSequenceClassifier.transferAnnotations(output.get(i), tokens.get(i));
      }

      if (VERBOSE) {
        boolean first = true;
        StringBuilder sb = new StringBuilder("NERCombinerAnnotator output: [");
        for (CoreLabel w : tokens) {
          if (first) {
            first = false;
          } else {
            sb.append(", ");
          }
          sb.append(w.toShorterString("Text", "NamedEntityTag", "NormalizedNamedEntityTag"));
        }
        sb.append(']');
        log.info(sb);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    for (CoreLabel token : tokens) {
      // add the background named entity tag to each token if it doesn't have an NER tag.
      if (token.ner() == null) {
        token.setNER(this.ner.backgroundSymbol());
      }
    }
  }


  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    // TODO: we could check the models to see which ones use lemmas
    // and which ones use pos tags
    if (ner.usesSUTime() || ner.appliesNumericClassifiers() || applyFineGrained) {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
          CoreAnnotations.TextAnnotation.class,
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class,
          CoreAnnotations.CharacterOffsetBeginAnnotation.class,
          CoreAnnotations.CharacterOffsetEndAnnotation.class,
          CoreAnnotations.PartOfSpeechAnnotation.class,
          CoreAnnotations.LemmaAnnotation.class,
          CoreAnnotations.BeforeAnnotation.class,
          CoreAnnotations.AfterAnnotation.class,
          CoreAnnotations.TokenBeginAnnotation.class,
          CoreAnnotations.TokenEndAnnotation.class,
          CoreAnnotations.IndexAnnotation.class,
          CoreAnnotations.OriginalTextAnnotation.class,
          CoreAnnotations.SentenceIndexAnnotation.class,
          CoreAnnotations.IsNewlineAnnotation.class
      )));
    } else {
      return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
          CoreAnnotations.TextAnnotation.class,
          CoreAnnotations.TokensAnnotation.class,
          CoreAnnotations.SentencesAnnotation.class,
          CoreAnnotations.CharacterOffsetBeginAnnotation.class,
          CoreAnnotations.CharacterOffsetEndAnnotation.class,
          CoreAnnotations.BeforeAnnotation.class,
          CoreAnnotations.AfterAnnotation.class,
          CoreAnnotations.TokenBeginAnnotation.class,
          CoreAnnotations.TokenEndAnnotation.class,
          CoreAnnotations.IndexAnnotation.class,
          CoreAnnotations.OriginalTextAnnotation.class,
          CoreAnnotations.SentenceIndexAnnotation.class,
          CoreAnnotations.IsNewlineAnnotation.class
      )));
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    HashSet<Class<? extends CoreAnnotation>> nerRequirementsSatisfied =
        new HashSet<>(Arrays.asList(
        CoreAnnotations.NamedEntityTagAnnotation.class,
        CoreAnnotations.NormalizedNamedEntityTagAnnotation.class,
        CoreAnnotations.ValueAnnotation.class,
        TimeExpression.Annotation.class,
        TimeExpression.TimeIndexAnnotation.class,
        CoreAnnotations.DistSimAnnotation.class,
        CoreAnnotations.NumericCompositeTypeAnnotation.class,
        TimeAnnotations.TimexAnnotation.class,
        CoreAnnotations.NumericValueAnnotation.class,
        TimeExpression.ChildrenAnnotation.class,
        CoreAnnotations.NumericTypeAnnotation.class,
        CoreAnnotations.ShapeAnnotation.class,
        Tags.TagsAnnotation.class,
        CoreAnnotations.NumerizedTokensAnnotation.class,
        CoreAnnotations.AnswerAnnotation.class,
        CoreAnnotations.NumericCompositeValueAnnotation.class,
        CoreAnnotations.CoarseNamedEntityTagAnnotation.class,
        CoreAnnotations.FineGrainedNamedEntityTagAnnotation.class
        ));
    if (this.buildEntityMentions) {
      nerRequirementsSatisfied.add(CoreAnnotations.MentionsAnnotation.class);
      nerRequirementsSatisfied.add(CoreAnnotations.EntityTypeAnnotation.class);
      nerRequirementsSatisfied.add(CoreAnnotations.EntityMentionIndexAnnotation.class);
    }
    return nerRequirementsSatisfied;
  }

}
