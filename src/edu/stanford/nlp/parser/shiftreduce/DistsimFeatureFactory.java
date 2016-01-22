package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.tagger.maxent.Distsim;

/**
 * Featurizes words based only on their distributional similarity classes.
 * Borrows the Distsim class from the tagger.
 *
 * @author John Bauer
 */
public class DistsimFeatureFactory extends FeatureFactory {
  private final Distsim distsim;

  DistsimFeatureFactory() {
    throw new UnsupportedOperationException("Illegal construction of DistsimFeatureFactory.  It must be created with a path to a cluster file");
  }

  DistsimFeatureFactory(String path) {
    distsim = Distsim.initLexicon(path);
  }

  public void addDistsimFeatures(List<String> features, CoreLabel label, String featureName) {
    if (label == null) {
      return;
    }

    String word = getFeatureFromCoreLabel(label, FeatureComponent.HEADWORD);
    String tag = getFeatureFromCoreLabel(label, FeatureComponent.HEADTAG);

    String cluster = distsim.getMapping(word);

    features.add(featureName + "dis-" + cluster);
    features.add(featureName + "disT-" + cluster + "-" + tag);
  }

  @Override
  public List<String> featurize(State state, List<String> features) {
    CoreLabel s0Label = getStackLabel(state.stack, 0); // current top of stack
    CoreLabel s1Label = getStackLabel(state.stack, 1); // one previous
    CoreLabel q0Label = getQueueLabel(state.sentence, state.tokenPosition, 0); // current location in queue

    addDistsimFeatures(features, s0Label, "S0");
    addDistsimFeatures(features, s1Label, "S1");
    addDistsimFeatures(features, q0Label, "Q0");

    return features;
  }

  private static final long serialVersionUID = -396152777907151063L;
}
