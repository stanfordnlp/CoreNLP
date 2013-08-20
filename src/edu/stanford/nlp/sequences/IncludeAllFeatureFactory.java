package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.PaddedList;

public class IncludeAllFeatureFactory<IN extends CoreLabel> extends RVFFeatureFactory<IN> {

  /**
   *
   */
  private static final long serialVersionUID = -8983718938165092707L;

  @Override
  public ClassicCounter getCliqueFeaturesRVF(PaddedList<IN> info, int position, Clique clique) {

    CoreLabel c = info.get(position);
    ClassicCounter features = new ClassicCounter();
    for (Class<?> key : c.keySet() ){
      if (!key.equals(AnswerAnnotation.class) && !key.equals(GoldAnswerAnnotation.class)) {
        String value = c.get((Class<? extends CoreAnnotation<String>>) key);
        features.incrementCount(key, Double.valueOf(value));
      }
    }
    return features;
  }

}
