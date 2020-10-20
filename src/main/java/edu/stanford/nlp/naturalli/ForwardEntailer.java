package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A class to find the forward entailments warranted by a particular sentence or clause.
 * Note that this will _only_ do deletions -- it will neither consider insertions, nor mutations of
 * the original sentence.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")
public class ForwardEntailer implements BiFunction<SemanticGraph, Boolean, ForwardEntailerSearchProblem> {

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
    this(maxResults, maxResults * 25, weights);
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
   * @param parseTree The original tree of the sentence we are beginning with
   * @param truthOfPremise The truth of the premise. In most applications, this will just be true.
   *
   * @return A new search problem instance.
   */
  @Override
  public ForwardEntailerSearchProblem apply(SemanticGraph parseTree, Boolean truthOfPremise) {
    for (IndexedWord vertex : parseTree.vertexSet()) {
      CoreLabel token = vertex.backingLabel();
      if (token != null && !token.containsKey(NaturalLogicAnnotations.PolarityAnnotation.class)) {
        throw new IllegalArgumentException("Cannot run Natural Logic forward entailment without polarity annotations set. See " + NaturalLogicAnnotator.class.getSimpleName());
      }
    }
    return new ForwardEntailerSearchProblem(parseTree, truthOfPremise, maxResults, maxTicks, weights);
  }
}
