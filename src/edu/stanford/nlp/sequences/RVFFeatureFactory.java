package edu.stanford.nlp.sequences;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.*;

/**
 * This is a feature factory for generating features with real values
 * instead of just with boolean values.
 *
 * @author Jenny Finkel
 */
public abstract class RVFFeatureFactory<IN> extends FeatureFactory<IN> {

  private static final long serialVersionUID = -5217071654353485387L;

  public abstract ClassicCounter<String> getCliqueFeaturesRVF(PaddedList<IN> info, int position, Clique clique);

  @Override
  public Collection<String> getCliqueFeatures(PaddedList<IN> info, int position, Clique clique) {
    throw new UnsupportedOperationException();
  }

}
