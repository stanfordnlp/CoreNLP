package edu.stanford.nlp.sequences;

/**
 * An interface for SequenceModels for which one can make arbitrary queries
 * (as opposed to just the queries necessary to find the best sequence).
 *
 * @author Jenny Finkel
 */

public interface QueriableSequenceModel extends SequenceModel {

  public CliqueDataset dataset() ;
  
  /**
   * set the weights of the model.  optional.
   */
  public void setParameters(double[] weights) ;
  
  /**
   * positions should be sorted
   */
  public double probOf(int[] sequenceLabels, int[] positions) ;

  /**
   * positions should be sorted
   */
  public double logProbOf(int[] sequenceLabels, int[] positions) ;

  /**
   * positions should be sorted
   */
  public double conditionalProbOf(int[] sequenceLabels, int[] positions, int[] conditionOnPositions) ;

  /**
   * positions should be sorted
   */
  public double logConditionalProbOf(int[] sequenceLabels, int[] positions, int[] conditionOnPositions) ;

  /**
   * positions should be sorted
   */
  public double probOf(int position, LabeledClique labeledClique) ;

  /**
   * positions should be sorted
   */
  public double logProbOf(int position, LabeledClique labeledClique) ;

  /**
   * prob of position, conditioned on rest of clique
   */
  public double conditionalProbOf(int position, LabeledClique labeledClique) ;

  /**
   * prob of position, conditioned on rest of clique
   */
  public double logConditionalProbOf(int position, LabeledClique labeledClique) ;

}
