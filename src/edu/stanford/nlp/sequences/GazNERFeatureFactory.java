package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ie.NERFeatureFactory;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.FemaleGazAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LastGazAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MaleGazAnnotation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.PaddedList;


public class GazNERFeatureFactory<IN extends CoreLabel> extends RVFFeatureFactory<IN> {

  /**
   *
   */
  private static final long serialVersionUID = 9051266323822366578L;

  @Override
  public void init(SeqClassifierFlags flags) {
    super.init(flags);
    otherFeatureFactory = new NERFeatureFactory<IN>();
    otherFeatureFactory.init(flags);
  }

  private FeatureFactory<IN> otherFeatureFactory = null;

  @Override
  public ClassicCounter<String> getCliqueFeaturesRVF(PaddedList<IN> info, int position, Clique clique) {

    CoreLabel c = info.get(position);
    CoreLabel n = info.get(position + 1);
    CoreLabel p = info.get(position - 1);

    ClassicCounter<String> features = new ClassicCounter<String>();

    double cF = Double.valueOf(c.get(FemaleGazAnnotation.class));
    double pF = (p.get(FemaleGazAnnotation.class) == null ? 0.0 : Double.valueOf(p.get(FemaleGazAnnotation.class)));
    double nF = (n.get(FemaleGazAnnotation.class) == null ? 0.0 : Double.valueOf(n.get(FemaleGazAnnotation.class)));
    double cM = Double.valueOf(c.get(MaleGazAnnotation.class));
    double pM = (p.get(MaleGazAnnotation.class) == null ? 0.0 : Double.valueOf(p.get(MaleGazAnnotation.class)));
    double nM = (n.get(MaleGazAnnotation.class) == null ? 0.0 : Double.valueOf(n.get(MaleGazAnnotation.class)));
    double cL = Double.valueOf(c.get(LastGazAnnotation.class));
    double pL = (p.get(LastGazAnnotation.class) == null ? 0.0 : Double.valueOf(p.get(LastGazAnnotation.class)));
    double nL = (n.get(LastGazAnnotation.class) == null ? 0.0 : Double.valueOf(n.get(LastGazAnnotation.class)));

    if (clique == cliqueC) {
      features.incrementCount("FEMALE_GAZ",cF);
      features.incrementCount("MALE_GAZ",cM);
      features.incrementCount("LAST_GAZ",cL);
      features.incrementCount("FEMALE_NGAZ",nF);
      features.incrementCount("MALE_NGAZ",nM);
      features.incrementCount("LAST_NGAZ",nL);
      features.incrementCount("FEMALE_PGAZ",pF);
      features.incrementCount("MALE_PGAZ",pM);
      features.incrementCount("LAST_PGAZ",pL);
      features.incrementCount("FEMALE_PC_GAZ",(pF*cL));
      features.incrementCount("MALE_PC_GAZ",(pM*cL));
      features.incrementCount("FEMALE_CN_GAZ",(cF*nL));
      features.incrementCount("MALE_CN_GAZ",(cM*nL));
    }

    if (clique == cliqueCpC) {
      features.incrementCount("FEMALE_PC_GAZ",(pF*cL));
      features.incrementCount("MALE_PC_GAZ",(pM*cL));
      features.incrementCount("FEMALE_CN_GAZ",(cF*nL));
      features.incrementCount("MALE_CN_GAZ",(cM*nL));
    }

    for (String key : otherFeatureFactory.getCliqueFeatures(info, position, clique)) {
      features.incrementCount(key, 1.0);
    }

    return features;
  }

}
