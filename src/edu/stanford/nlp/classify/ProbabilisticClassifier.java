package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.Counter;

public interface ProbabilisticClassifier<L, F> extends Classifier<L, F>
{
  public Counter<L> probabilityOf(Datum<L, F> example);
  public Counter<L> logProbabilityOf(Datum<L, F> example);
}
