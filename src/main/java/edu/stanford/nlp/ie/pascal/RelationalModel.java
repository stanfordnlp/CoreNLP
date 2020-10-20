package edu.stanford.nlp.ie.pascal;


/**
 *  An interface for the relational models in phase 2 of the pascal system.
 *
 *  @author Jamie Nicolson
 */
public interface RelationalModel {
    /**
     *
     * @param temp template to be scored
     * @return its score
     */
    public double computeProb(PascalTemplate temp);


}
