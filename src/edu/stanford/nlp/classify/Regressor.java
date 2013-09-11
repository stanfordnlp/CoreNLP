package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;

import java.io.Serializable;
import java.util.List;

/**
 *
 * An interface for models that Regress into a real value rather than classify.
 *  e.g.: LinearRegression and SVMRegression stc.
 * @param <F> The type of the features in each Datum
 */

public interface Regressor<F> extends Serializable {

  public double valueOf(Datum<Double,F> example);

  public List<Double> valuesOf(GeneralDataset<Double,F> dataset);
}
