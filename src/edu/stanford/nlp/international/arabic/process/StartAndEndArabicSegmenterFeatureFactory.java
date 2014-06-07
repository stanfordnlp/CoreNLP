package edu.stanford.nlp.international.arabic.process;

import java.util.Collection;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.PaddedList;

/**
 * Feature factory for the IOB clitic segmentation model described by
 * Green and DeNero (2012).
 * 
 * @author Spence Green
 *
 * @param <IN>
 */
public class StartAndEndArabicSegmenterFeatureFactory<IN extends CoreLabel> extends ArabicSegmenterFeatureFactory<IN> {
  
  private static final long serialVersionUID = 6864940988019110930L;

  public void init(SeqClassifierFlags flags) {
    super.init(flags);
  }

  @Override
  protected Collection<String> featuresCpC(PaddedList<IN> cInfo, int loc) {
    Collection<String> features = super.featuresCpC(cInfo, loc);

    CoreLabel c = cInfo.get(loc);

    // "Wrapper" feature: identity of first and last two chars of the current word.
    // This helps detect ma+_+sh in dialect, as well as avoiding segmenting possessive
    // pronouns if the word starts with al-.
    if (c.word().length() > 3) {
      String start = c.word().substring(0, 2);
      String end = c.word().substring(c.word().length() - 2);
      if (c.index() == 2) {
        features.add(start + "_" + end + "-begin-wrap");
      }
      if (c.index() == c.word().length() - 1) {
        features.add(start + "_" + end + "-end-wrap");
      }
    }
    
    return features;
  }
}
