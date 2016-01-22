package edu.stanford.nlp.ie;

import edu.stanford.nlp.sequences.ListeningSequenceModel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import java.util.List;

/** Used for creating an NER prior by reflection.
 *
 *  @author Christopher Manning
 */
public class EmpiricalNERPriorFactory<IN extends CoreMap> implements PriorModelFactory<IN> {

  @Override
  public ListeningSequenceModel getInstance(String backgroundSymbol,
                                            Index<String> classIndex,
                                            Index<String> tagIndex,
                                            List<IN> document,
                                            Pair<double[][], double[][]> entityMatrices,
                                            SeqClassifierFlags flags) {
    EntityCachingAbstractSequencePrior<IN> prior =
            new EmpiricalNERPrior<>(flags.backgroundSymbol, classIndex, document);
    // SamplingNERPrior prior = new SamplingNERPrior(flags.backgroundSymbol, classIndex, newDocument);
    return prior;
  }

}
