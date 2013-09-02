package edu.stanford.nlp.sequences;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.PriorAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PositionAnnotation;
import edu.stanford.nlp.util.PaddedList;

import java.util.Collection;
import java.util.Map;
import java.util.TreeSet;

public class TrueCaserFeatureFactory<IN extends CoreLabel> extends FeatureFactory<IN> {

  private static final boolean DEBUG = true;

  private static final long serialVersionUID = -8983718938165092704L;

  @Override
  public Collection<String> getCliqueFeatures(PaddedList<IN> info, int position, Clique clique) {

    TreeSet<String> featuresC = new TreeSet<String>(), featuresC2 = new TreeSet<String>();

    CoreLabel c = info.get(position);
    //String cShape = c.getString(ShapeAnnotation.class);
    Map<String,Double> f = c.get(PriorAnnotation.class);

    int pos = Integer.parseInt(c.get(PositionAnnotation.class));
    if(pos > 5) pos = 5;
    featuresC.add(pos + "-POSITION");
    if (pos == 0) {
      featuresC.add("BEGIN-SENT");
    } else {
      featuresC.add("IN-SENT");
    }

    if(f != null)
      for(Map.Entry<String,Double> el : f.entrySet())
        featuresC.add(el.getKey()+"@"+binLogProb(el.getValue()));

    String[] ff = featuresC.toArray(new String[featuresC.size()]);
    for(int i=0; i<ff.length; ++i)
      for(int j=i+1; j<ff.length; ++j)
        featuresC2.add(ff[i]+"#"+ff[j]);
    if(DEBUG) {
      String w = c.get(CoreAnnotations.TextAnnotation.class);
      System.err.println("Features for: "+w);
      for(String feat : featuresC) {
        System.err.println("  "+feat);
      }
    }
    featuresC2.add("##");
    return featuresC2;
  }

  private static String binLogProb(double logp) {
    assert(logp <= 0);
    if(logp < -20) logp = 20;
    return Integer.toString((int)Math.floor(logp));
  }

}
