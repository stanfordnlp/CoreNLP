package edu.stanford.nlp.stats;

import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.ProbabilisticClassifier;

/**
 * @author Jenny Finkel
 */

public interface Scorer<L> {

  public <F> double score(ProbabilisticClassifier<L,F> classifier, GeneralDataset<L,F> data) ;

  public String getDescription(int numDigits);

} 
