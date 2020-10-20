package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.ling.CoreLabel;
import java.util.*;


/**
 * Feature factory for SimpleTagger of Mallet.
 *
 * @author Michel Galley
 */
public class MalletFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> {

  private static final long serialVersionUID = -5586998916869425417L;

  @Override
  public Collection<String> getCliqueFeatures (PaddedList<IN> info, int position, Clique clique) {
    List<String> features = new ArrayList<>(Arrays.asList(info.get(position).word().split(" ")));
    return features;
  }

}
