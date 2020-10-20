package edu.stanford.nlp.ie; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.sequences.ListeningSequenceModel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import java.util.List;

/**
 * @author Christopher Manning
 */
public class UniformPriorFactory<IN extends CoreMap> implements PriorModelFactory<IN>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(UniformPriorFactory.class);

  @Override
  public ListeningSequenceModel getInstance(String backgroundSymbol,
                                            Index<String> classIndex,
                                            Index<String> tagIndex,
                                            List<IN> document,
                                            Pair<double[][], double[][]> entityMatrices,
                                            SeqClassifierFlags flags) {
    // log.info("Using uniform prior!");
    UniformPrior<IN> uniPrior = new UniformPrior<>(flags.backgroundSymbol, classIndex, document);
    return uniPrior;
  }

}
