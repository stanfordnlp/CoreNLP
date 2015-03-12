package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;

import java.util.*;
import java.util.function.BiFunction;

/**
 * A class to find the forward entailments warranted by a particular sentence or clause.
 * Note that this will _only_ do deletions -- it will neither consider insertions, nor mutations of
 * the original sentence.
 *
 * @author Gabor Angeli
 */
public class ForwardEntailer implements BiFunction<List<CoreLabel>, SemanticGraph, ForwardEntailerSearchProblem> {

  /**
   * The maximum number of ticks top search for. Otherwise, the search will be exhaustive.
   */
  public final int maxTicks;

  /**
   * The maximum number of results to return from a single search.
   */
  public final int maxResults;

  /**
   * The weights to use for entailment.
   */
  public final NaturalLogicWeights weights;

  /**
   * Create a new searcher with the specified parameters.
   *
   * @param maxResults The maximum number of results to return from a single search.
   * @param maxTicks The maximum number of ticks to search for.
   * @param weights The natural logic weights to use for the searches.
   */
  public ForwardEntailer(int maxResults, int maxTicks, NaturalLogicWeights weights) {
    this.maxResults = maxResults;
    this.maxTicks = maxTicks;
    this.weights = weights;
  }

  /**
   * @see ForwardEntailer#ForwardEntailer(int, int, NaturalLogicWeights)
   */
  public ForwardEntailer(int maxResults, NaturalLogicWeights weights) {
    this(maxResults, Integer.MAX_VALUE, weights);
  }

  /**
   * @see ForwardEntailer#ForwardEntailer(int, int, NaturalLogicWeights)
   */
  public ForwardEntailer(NaturalLogicWeights weights) {
    this(Integer.MAX_VALUE, Integer.MAX_VALUE, weights);
  }

  /**
   * Create a new search problem instance, given a sentence (possibly fragment), and the corresponding
   * parse tree.
   *
   * @param tokens The sentence to begin with.
   * @param parseTree The original tree of the sentence we are beginning with
   *
   * @return A new search problem instance.
   */
  @Override
  public ForwardEntailerSearchProblem apply(List<CoreLabel> tokens, SemanticGraph parseTree) {
    for (CoreLabel token : tokens) {
      if (!token.containsKey(NaturalLogicAnnotations.PolarityAnnotation.class)) {
        throw new IllegalArgumentException("Cannot run Natural Logic forward entailment without polarity annotations set. See " + NaturalLogicAnnotator.class.getSimpleName());
      }
    }
    return new ForwardEntailerSearchProblem(tokens, parseTree, maxResults, maxTicks, weights);
  }


}
