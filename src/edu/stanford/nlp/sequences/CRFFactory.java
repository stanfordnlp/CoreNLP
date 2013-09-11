package edu.stanford.nlp.sequences;

import java.io.Serializable;

/**
 * A Factory which vends CRFs which can be passed
 * to any of the {@link edu.stanford.nlp.sequences.BestSequenceFinder}s
 * so as to find the best assignment of labels.
 *
 * @author Jenny Finkel
 */

public class CRFFactory
    implements QueriableSequenceModelFactory.QueriableSequenceModelFactoryDataProvider,
    Serializable {

  protected static final long serialVersionUID = -7596221713774004331l;
  
  public ObjectiveFunctionInterface getObjectiveFunction(MultiDocumentCliqueDataset dataset) {
      CRFObjectiveFunction func = new CRFObjectiveFunction();
      func.init(dataset);
      return func;
  }

  public CRF getModel(CliqueDataset dataset) {
    return new CRF(dataset);
  }

}
