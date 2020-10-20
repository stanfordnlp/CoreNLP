package edu.stanford.nlp.international.arabic.process;

import java.util.Collection;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.PaddedList;

/**
 * Feature factory for the IOB clitic segmentation model described by
 * Green and DeNero (2012).
 * 
 * @author Spence Green
 *
 * @param <IN> CoreLabel type to produce
 */
public class BigWindowArabicSegmenterFeatureFactory<IN extends CoreLabel> extends ArabicSegmenterFeatureFactory<IN> {
  
  private static final long serialVersionUID = 6864940988019110930L;

  public void init(SeqClassifierFlags flags) {
    super.init(flags);
  }

  protected Collection<String> featuresC(PaddedList<IN> cInfo, int loc) {
    Collection<String> features = super.featuresC(cInfo, loc);
    CoreLabel n3 = cInfo.get(loc + 3);
    CoreLabel p3 = cInfo.get(loc - 3);

    String charn3 = n3.get(CoreAnnotations.CharAnnotation.class);
    String charp3 = p3.get(CoreAnnotations.CharAnnotation.class);

    // a 7 character window instead of a 5 character window
    features.add(charn3 + "-n3");
    features.add(charp3 + "-p3");
    return features;
  }
}
