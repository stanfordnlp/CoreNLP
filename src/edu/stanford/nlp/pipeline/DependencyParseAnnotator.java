package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
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
public class DependencyParseAnnotator extends SentenceAnnotator {

  private final DependencyParser parser;

  private final int nThreads;
  private static final int DEFAULT_NTHREADS = 1;

  /**
   * Maximum parse time (in milliseconds) for a sentence
   */
  private final long maxTime;
  private static final long DEFAULT_MAXTIME = Long.MAX_VALUE;

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

    SemanticGraph deps = SemanticGraphFactory.makeFromTree(gs, SemanticGraphFactory.Mode.COLLAPSED, extraDependencies, true, null),
                  uncollapsedDeps = SemanticGraphFactory.makeFromTree(gs, SemanticGraphFactory.Mode.BASIC, extraDependencies, true, null),
                  ccDeps = SemanticGraphFactory.makeFromTree(gs, SemanticGraphFactory.Mode.CCPROCESSED, extraDependencies, true, null);

    sentence.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, deps);
    sentence.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, uncollapsedDeps);
    sentence.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, ccDeps);

  }

  @Override
  protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    // TODO
    System.err.println("fail");
  }

  @Override
  public Set<Requirement> requires() {
    return TOKENIZE_SSPLIT_POS;
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(DEPENDENCY_REQUIREMENT);
  }

  public static String signature(String annotatorName, Properties props) {
    return annotatorName +
            ".extradependencies:" + props.getProperty(annotatorName + ".extradependencies", "NONE").toLowerCase();
  }

}
