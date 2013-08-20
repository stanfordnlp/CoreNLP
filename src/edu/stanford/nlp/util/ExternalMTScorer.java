package edu.stanford.nlp.util;

import java.util.List;
import java.util.Set;

import edu.stanford.nlp.stats.Counter;

/**
 * 
 * Interface for obtaining scores for MT output from additional
 * knowledge sources
 * 
 * @author pado, mgalley
 *
 */
public interface ExternalMTScorer {

  /* Each instance of MTOutputScorer is constructed using the default
  parameterless constructor. */

  /* Contract: this function is called immediately after construction. */
  public void init(String configurationFile);

  /**
   * Return a counter with scores for 
   */
  public Counter<String> scoreMTOutput(String reference, String mtoutput);

  /**
   * Contract: call this method before any scoreMTOutput() calls.
   * @param refAndHyp All references and hypotheses for the current dataset
   */
  public void readAllReferencesAndHypotheses(List<Pair<String,String>> refAndHyp);

  /**
   * Returns all score names.
   */
  public Set<String> scoresProvided();
  
}
