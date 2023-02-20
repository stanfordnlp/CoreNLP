package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.Tree;

/**
 * Interface for the Collinizers
 *<br>
 * TODO: pass in both the guess and the gold
 *
 * @author John Bauer
 */
public interface AbstractCollinizer  {
  Tree transformTree(Tree guess, Tree gold);
}
