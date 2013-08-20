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
public class SimpleComboFeatureFactory extends ComboFeatureFactory {

  private static final long serialVersionUID = 6124131315339969687L;

  private transient Map<SequenceClassifier,QueriableSequenceModel> cache = new HashMap<SequenceClassifier,QueriableSequenceModel>();
  private transient List<CoreLabel> cacheCheck = null;

  @Override
  public ClassicCounter<String> getFeatures(PaddedList<CoreLabel> info, int position, LabeledClique lc) {

    ClassicCounter<String> features = new ClassicCounter<String>();

    if (lc.clique != FeatureFactory.cliqueCpC) { return features; }

    for (int i = 0; i < classifiersToCombine.size(); i++) {
      SequenceClassifier sc = classifiersToCombine.get(i);

      // [cdm 2012: I think it needed this so the hash map lookup would work, and so some rearchitecting would be needed to remove this 
      List<CoreLabel> otherData = getData(info.getWrappedList(), sc);

      LabeledClique otherLC = getLabeledClique(lc, sc);
      if (otherLC == null) { continue; }

      if (info != cacheCheck) {
        cache = new HashMap<SequenceClassifier,QueriableSequenceModel>();
        cacheCheck = info;
      }

      QueriableSequenceModel qsm = cache.get(sc);
      if (info != cacheCheck || qsm == null) {
//        System.err.println(info.size()+" {"+info.hashCode()+"}\t"+otherData.size()+" {"+otherData.hashCode()+"}");
        qsm = sc.getSequenceModel(otherData);
        cache.put(sc, qsm);
      }

      features.incrementCount("model-"+i+"-lc-"+lc, qsm.logProbOf(position, otherLC));
//      features.incrementCount("model-"+i, qsm.logProbOf(position, otherLC));

    }

//    System.err.println(features);
    return features;

  }

}
