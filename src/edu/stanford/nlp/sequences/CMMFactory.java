
package edu.stanford.nlp.sequences;

import java.io.Serializable;

/**
 * A Factory which vends CMMs (MEMMs) which can be passed
 * to any of the {@link edu.stanford.nlp.sequences.BestSequenceFinder}s
 * so as to find the best assignment of labels.
 *
 * @author Jenny Finkel
 */

public class CMMFactory
    implements QueriableSequenceModelFactory.QueriableSequenceModelFactoryDataProvider,
    Serializable {
  
  /**
   * 
   */
  private static final long serialVersionUID = 7698629370427133778L;

  public ObjectiveFunctionInterface getObjectiveFunction(MultiDocumentCliqueDataset dataset) {
      CMMObjectiveFunction func = new CMMObjectiveFunction();
      func.init(dataset);
      return func;
  }

  public CMM getModel(CliqueDataset dataset) {
    return new CMM(dataset);
  }

  
}
