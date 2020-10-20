package edu.stanford.nlp.parser.lexparser;

/**
 * 
 * @author Spence Green
 *
 */
public interface LatticeScorer extends Scorer {

	public Item convertItemSpan(Item item);
}
