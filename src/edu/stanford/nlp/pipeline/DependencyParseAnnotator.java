package edu.stanford.nlp.pipeline;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.SemanticGraphFactory.Mode;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.util.ArraySet;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.MetaClass;
import edu.stanford.nlp.util.PropertiesUtils;

import java.util.*;

/**
 * This class adds dependency parse information to an Annotation.
 *
 * Dependency parses are added to each sentence under the annotation
 * {@link edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation}.
 *
 * @author Jon Gauthier
 */
public class DependencyParseAnnotator extends SentenceAnnotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DependencyParseAnnotator.class);

  private final DependencyParser parser;

  private final int nThreads;
  private static final int DEFAULT_NTHREADS = 1;

  /**
   * Maximum parse time (in milliseconds) for a sentence
   */
  private final long maxTime;
  /**
   * The default maximum parse time.
   */
  private static final long DEFAULT_MAXTIME = -1;

  /**
   * If true, include the extra arcs in the dependency representation.
   */
  private final GrammaticalStructure.Extras extraDependencies;

  public DependencyParseAnnotator() {
    this(new Properties());
  }

  public DependencyParseAnnotator(Properties properties) {
    String modelPath = PropertiesUtils.getString(properties, "model", DependencyParser.DEFAULT_MODEL);
    parser = DependencyParser.loadFromModelFile(modelPath, properties);

    nThreads = PropertiesUtils.getInt(properties, "testThreads", DEFAULT_NTHREADS);
    maxTime = PropertiesUtils.getLong(properties, "sentenceTimeout", DEFAULT_MAXTIME);
    extraDependencies = MetaClass.cast(properties.getProperty("extradependencies", "NONE"), GrammaticalStructure.Extras.class);
  }

  @Override
  protected int nThreads() {
    return nThreads;
  }

  @Override
  protected long maxTime() {
    return maxTime;
  }

  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    GrammaticalStructure gs = parser.predict(sentence);

    SemanticGraph deps = SemanticGraphFactory.makeFromTree(gs, Mode.COLLAPSED, extraDependencies, null),
                  uncollapsedDeps = SemanticGraphFactory.makeFromTree(gs, Mode.BASIC, extraDependencies, null),
                  ccDeps = SemanticGraphFactory.makeFromTree(gs, Mode.CCPROCESSED, extraDependencies, null),
                  enhancedDeps = SemanticGraphFactory.makeFromTree(gs, Mode.ENHANCED, extraDependencies, null),
                  enhancedPlusPlusDeps = SemanticGraphFactory.makeFromTree(gs, Mode.ENHANCED_PLUS_PLUS, extraDependencies, null);


    sentence.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, deps);
    sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, uncollapsedDeps);
    sentence.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, ccDeps);
    sentence.set(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class, enhancedDeps);
    sentence.set(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class, enhancedPlusPlusDeps);
  }

  @Override
  protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    // TODO
    log.info("fail");
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.ValueAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.SentenceIndexAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class,
        SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class
    )));
  }

}
