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
public interface PriorModelFactory<IN extends CoreMap> {

  ListeningSequenceModel getInstance(String backgroundSymbol,
                                     Index<String> classIndex,
                                     Index<String> tagIndex,
                                     List<IN> document,
                                     Pair<double[][], double[][]> entityMatrices,
                                     SeqClassifierFlags flags);

}
