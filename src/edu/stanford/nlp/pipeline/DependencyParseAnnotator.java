package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.parser.nndep.NNParser;
import edu.stanford.nlp.parser.nndep.util.DependencyTree;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.EnglishGrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFromDependenciesFactory;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class adds dependency parse information to an Annotation.
 *
 * Parse trees are added to each sentence under the annotation
 * {@link edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.BasicDependenciesAnnotation}.
 *
 * @author Jon Gauthier
 */
public class DependencyParseAnnotator extends SentenceAnnotator {

  private final NNParser parser;

  private final int nThreads;

  /**
   * Maximum parse time (in milliseconds) for a sentence
   */
  private final long maxTime;

  public DependencyParseAnnotator() {
    this(NNParser.DEFAULT_MODEL);
  }

  public DependencyParseAnnotator(String modelPath) {
    this(modelPath, 1, 0);
  }

  public DependencyParseAnnotator(String modelPath, int nThreads, long maxTime) {
    parser = new NNParser();
    parser.loadModelFile(modelPath);
    parser.initialize();

    this.nThreads = nThreads;
    this.maxTime = maxTime;
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

    SemanticGraph deps = SemanticGraphFactory.generateCollapsedDependencies(gs),
        uncollapsedDeps = SemanticGraphFactory.generateUncollapsedDependencies(gs),
        ccDeps = SemanticGraphFactory.generateCCProcessedDependencies(gs);

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
    return new HashSet<>();
  }

}
