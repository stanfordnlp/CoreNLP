package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.Tree;

/**
 * Process a Tree and return a score.  Typically constructed by the
 * Reranker, possibly given some extra information about the sentence
 * being parsed.
 *
 * @author John Bauer
 */
public interface RerankerQuery {
  double score(Tree tree);
}
