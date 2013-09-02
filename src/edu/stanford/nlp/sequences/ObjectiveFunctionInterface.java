package edu.stanford.nlp.sequences;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.HasInitial;

public interface ObjectiveFunctionInterface extends DiffFunction, HasInitial {

    public void init(MultiDocumentCliqueDataset dataset);
  
    public void init(MultiDocumentCliqueDataset dataset,
                     LogPrior prior);

    public void setPrior(LogPrior prior);

    public int domainDimension();

    public double[] initial();

    public double valueAt(double[] x);

    public double[] derivativeAt(double[] x);

}
