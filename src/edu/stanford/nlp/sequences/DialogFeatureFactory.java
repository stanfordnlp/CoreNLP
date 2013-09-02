package edu.stanford.nlp.sequences;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.PaddedList;

public class DialogFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> {

  /**
   *
   */
  private static final long serialVersionUID = -4417769187415417226L;

  public DialogFeatureFactory() {}

  private Set<String> wordsToIgnore = new HashSet<String>(Arrays.asList(new String[]{"answer", "goldAnswer", "word", "position"}));

  @Override
  public Collection<String> getCliqueFeatures(PaddedList<IN> info, int position, Clique clique) {
    Collection<String> features = new HashSet<String>();
    if (clique.equals(cliqueC)) {
      CoreLabel c = info.get(position);
      for (Class<?> f : c.keySet()) {
        if (!wordsToIgnore.contains(f)) {
          String feature = f+"="+c.get((Class<? extends CoreAnnotation<String>>) f);
          features.add(feature);
        }
      }

      // class feature
      features.add("##");
    } else {
      features.add("##");
    }
//    System.err.println(features);
    return features;
  }
}
