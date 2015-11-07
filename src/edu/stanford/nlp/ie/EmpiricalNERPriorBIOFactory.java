package edu.stanford.nlp.ie;

import edu.stanford.nlp.sequences.ListeningSequenceModel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import java.util.List;

/**
 * @author Christopher Manning
 */
public class EmpiricalNERPriorBIOFactory<IN extends CoreMap> implements PriorModelFactory<IN> {

  @Override
  public ListeningSequenceModel getInstance(String backgroundSymbol,
                                            Index<String> classIndex,
                                            Index<String> tagIndex,
                                            List<IN> document,
                                            Pair<double[][], double[][]> entityMatrices,
                                            SeqClassifierFlags flags) {
    EntityCachingAbstractSequencePriorBIO<IN> prior =
            new EmpiricalNERPriorBIO<>(flags.backgroundSymbol, classIndex, tagIndex, document, entityMatrices, flags);
    return prior;
  }

}
