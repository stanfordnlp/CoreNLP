package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.ShapeAnnotation;

import java.util.*;


/**
 * This is basically a tester feature factory.
 *
 * @author Jenny Finkel
 */
public class SuperSimpleFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> {

  /**
   *
   */
  private static final long serialVersionUID = 8482882637052066375L;

  @Override
  public Collection<String> getCliqueFeatures (PaddedList<IN> info, int position, Clique clique) {
    List<String> features = new ArrayList<String>();
    features.add(info.get(position).word());
    features.add(info.get(position).get(ShapeAnnotation.class)+"||SHAPE");
    features.add("###");
    //features.add(info.get(position-1).word()+" - "+info.get(position).word());
    return features;
  }

}
