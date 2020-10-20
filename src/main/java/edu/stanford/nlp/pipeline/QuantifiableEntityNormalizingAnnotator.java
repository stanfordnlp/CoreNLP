package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ie.QuantifiableEntityNormalizer;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.Timing;

import java.util.*;

/**
 * This class provides a facility for normalizing content of numerical named
 * entities (number, money, date, time) in the pipeline package world. It uses a
 * lot of code with {@link edu.stanford.nlp.ie.QuantifiableEntityNormalizer}.
 * New stuff should generally be added there so as to reduce code duplication.
 * 
 * @author Jenny Finkel
 * @author Christopher Manning (extended for RTE)
 * @author Chris Cox (original version)
 */

public class QuantifiableEntityNormalizingAnnotator implements Annotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(QuantifiableEntityNormalizingAnnotator.class);

  private Timing timer = new Timing();
  private final boolean VERBOSE;
  private static final String DEFAULT_BACKGROUND_SYMBOL = "O";
  private final boolean collapse;  // TODO: collpase = true won't work properly (see annotateTokens)

  public static final String BACKGROUND_SYMBOL_PROPERTY = "background";
  public static final String COLLAPSE_PROPERTY = "collapse";

  public QuantifiableEntityNormalizingAnnotator() {
    this(DEFAULT_BACKGROUND_SYMBOL, true);
  }

  public QuantifiableEntityNormalizingAnnotator(boolean verbose) {
    this(DEFAULT_BACKGROUND_SYMBOL, verbose);
  }

  public QuantifiableEntityNormalizingAnnotator(String name, Properties props) {
    String property = name + "." + BACKGROUND_SYMBOL_PROPERTY;
    String backgroundSymbol = props.getProperty(property, DEFAULT_BACKGROUND_SYMBOL);
    // this next line is yuck as QuantifiableEntityNormalizer is still static
    QuantifiableEntityNormalizer.BACKGROUND_SYMBOL = backgroundSymbol;
    property = name + "." + COLLAPSE_PROPERTY;
    collapse = PropertiesUtils.getBool(props, property, false);
    if (this.collapse) {
      log.info("WARNING: QuantifiableEntityNormalizingAnnotator does not work well with collapse=true");
    }
    VERBOSE = false;
  }

  /**
   * Do quantity entity normalization and collapse together multitoken quantity
   * entities into a single token.
   * 
   * @param backgroundSymbol
   *          NER background symbol
   * @param verbose
   *          Whether to write messages
   */
  public QuantifiableEntityNormalizingAnnotator(String backgroundSymbol, boolean verbose) {
    this(backgroundSymbol, verbose, false);
  }

  /**
   * Do quantity entity normalization and collapse together multitoken quantity
   * entities into a single token.
   * 
   * @param verbose
   *          Whether to write messages
   * @param collapse
   *          Whether to collapse multitoken quantity entities.
   */
  public QuantifiableEntityNormalizingAnnotator(boolean verbose, boolean collapse) {
    this(DEFAULT_BACKGROUND_SYMBOL, verbose, collapse);
  }

  public QuantifiableEntityNormalizingAnnotator(String backgroundSymbol, boolean verbose, boolean collapse) {
    // this next line is yuck as QuantifiableEntityNormalizer is still static
    QuantifiableEntityNormalizer.BACKGROUND_SYMBOL = backgroundSymbol;
    VERBOSE = verbose;
    this.collapse = collapse;
    if (this.collapse) {
      log.info("WARNING: QuantifiableEntityNormalizingAnnotator does not work well with collapse=true");
    }
  }

  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      timer.start();
      log.info("Normalizing quantifiable entities...");
    }
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        annotateTokens(tokens);
      }
      if (VERBOSE) {
        timer.stop("done.");
        log.info("output: " + sentences + '\n');
      }
    } else if (annotation.containsKey(CoreAnnotations.TokensAnnotation.class)) {
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      annotateTokens(tokens);
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }

  private <TOKEN extends CoreLabel> void annotateTokens(List<TOKEN> tokens) {
    // Make a copy of the tokens before annotating because QuantifiableEntityNormalizer may change the POS too
    List<CoreLabel> words = new ArrayList<>();
    for (CoreLabel token : tokens) {
      CoreLabel word = new CoreLabel();
      word.setWord(token.word());
      word.setNER(token.ner());
      word.setTag(token.tag());

      // copy fields potentially set by SUTime
      NumberSequenceClassifier.transferAnnotations(token, word);

      words.add(word);
    }
    doOneSentence(words);
    // TODO: If collapsed is set, tokens for entities are collapsed into one node then
    // (words.size() != tokens.size() and the logic below just don't work!!!
    for (int i = 0; i < words.size(); i++) {
      String ner = words.get(i).ner();
      tokens.get(i).setNER(ner);
      tokens.get(i).set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class,
              words.get(i).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
    }
  }

  private <TOKEN extends CoreLabel> void doOneSentence(List<TOKEN> words) {
    QuantifiableEntityNormalizer.addNormalizedQuantitiesToEntities(words, collapse);
  }


  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    // technically it adds some NER, but someone who wants full NER
    // labels will be very disappointed, so we do not claim to produce NER
    return Collections.singleton(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
  }
}
