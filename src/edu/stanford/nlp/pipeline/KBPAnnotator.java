package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.KBPRelationExtractor;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.*;

/**
 * An annotator which takes as input sentences, and produces KBP relation annotations.
 *
 * TODO(gabor) finish writing me!
 *
 * @author Gabor Angeli
 */
public class KBPAnnotator extends SentenceAnnotator {
  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(KBPAnnotator.class);

  /**
   * The number of threads to run on.
   */
  public final int threads;

  /**
   * The extractor implementation.
   */
  public final KBPRelationExtractor extractor;


  /**
   * A TokensRegexNER annotator for the special KBP NER types (case-sensitive).
   */
  private final TokensRegexNERAnnotator casedNER;
  /**
   * A TokensRegexNER annotator for the special KBP NER types (case insensitive).
   */
  private final TokensRegexNERAnnotator caselessNER;


  /**
   * Create a new KBP annotator from the given properties.
   *
   * @param props The properties to use when creating this extractor.
   */
  public KBPAnnotator(Properties props) {
    // Parse standard properties
    this.threads = Integer.parseInt(props.getProperty("threads", "1"));

    try {
      // Load the extractor
      this.extractor = IOUtils.readObjectFromURLOrClasspathOrFileSystem(
          props.getProperty("kbp.model", DefaultPaths.DEFAULT_KBP_CLASSIFIER));
      // Load TokensRegexNER
      this.casedNER = new TokensRegexNERAnnotator(
          props.getProperty("kbp.regexner.cased", DefaultPaths.DEFAULT_KBP_REGEXNER_CASED),
          true);
      this.caselessNER = new TokensRegexNERAnnotator(
          props.getProperty("kbp.regexner.caseless", DefaultPaths.DEFAULT_KBP_REGEXNER_CASELESS),
          false,
          "^(NN|JJ).*");
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
  }

  /** {@inheritDoc} */
  @Override
  protected int nThreads() {
    return threads;
  }

  /** {@inheritDoc} */
  @Override
  protected long maxTime() {
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    // TODO(gabor) write me!
  }

  /** {@inheritDoc} */
  @Override
  protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    sentence.set(CoreAnnotations.KBPRelationAnnotation.class, Collections.emptyList());
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.KBPRelationAnnotation.class);
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    Set<Class<? extends CoreAnnotation>> requirements = new HashSet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.SentenceIndexAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class,
        CoreAnnotations.LemmaAnnotation.class,
        SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class
    ));
    return Collections.unmodifiableSet(requirements);
  }
}
