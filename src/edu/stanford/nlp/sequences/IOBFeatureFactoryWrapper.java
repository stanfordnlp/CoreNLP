package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.stats.ClassicCounter;

import java.util.*;


/**
 * This is the abstract class that all feature factories must
 * subclass.  It also defines most of the basic {@link Clique}s
 * that you would want to make features over.  It contains a
 * convenient method, getCliques(maxLeft, maxRight) which will give
 * you all the cliques within the specified limits.
 *
 * @author Jenny Finkel
 */
public class IOBFeatureFactoryWrapper extends Type2FeatureFactory<CoreLabel> {

  private static final long serialVersionUID = -893284903284932084L;

  public IOBFeatureFactoryWrapper (FeatureFactory<CoreLabel> featureFactory) {
    wrapped = featureFactory;
  }

  @Override
  public void init(SeqClassifierFlags flags) {
    super.init(flags);
    if (!(wrapped instanceof RVFFeatureFactory)) {
      flags.booleanFeatures = true;
    }
  }

  private FeatureFactory<CoreLabel> wrapped = null;

  private Map<Clique, ClassicCounter> cache = new HashMap<Clique, ClassicCounter>();
  private Map<LabeledClique, Pair<String,String>> labelToString = new HashMap<LabeledClique, Pair<String,String>>();
  private int position = -1;

  @Override
  public ClassicCounter getFeatures(PaddedList<CoreLabel> info, int position, LabeledClique lc) {

    if (position != this.position) {
      cache = new HashMap<Clique, ClassicCounter>();
      this.position = position;
      //System.err.println("hello ... ");
    }
    //System.err.println("world");

    ClassicCounter newFeatures = new ClassicCounter();
    for (Clique c : wrapped.getCliques()) {
      ClassicCounter cliqueFeatures = cache.get(c);
      LabeledClique cliqueLabel = LabeledClique.valueOf(c, lc, 0);
      Pair<String,String> stringLabels = labelToString.get(cliqueLabel);
      if (stringLabels == null) {
        String label = "";
        String boundary = "";
        for (int i = 0; i < cliqueLabel.clique.size(); i++) {
          CoreLabel f = info.get(position+cliqueLabel.clique.relativeIndex(i));
          String ans = f.get(AnswerAnnotation.class);
          String[] bits = ans.split("-", 2);
          if (bits.length == 1 && bits.equals("O")) {
            throw new RuntimeException("  You are using IOBFeatureFactoryWrapper, but your labels aren't IOB: "+ans);
          }
          if (bits.length == 0 || bits.length == 1) {
            label += "|"+ans;
            boundary += "|"+ans;
          } else {
            label += "|"+bits[1];
            boundary += "|"+bits[0];
          }
        }
        stringLabels = new Pair<String,String>(label, boundary);
        labelToString.put(cliqueLabel, stringLabels);
      }
      if (cliqueFeatures == null) {
        cliqueFeatures = new ClassicCounter();
        if (wrapped instanceof RVFFeatureFactory) {
          ClassicCounter tmp = ((RVFFeatureFactory)wrapped).getCliqueFeaturesRVF(info, position, c);
          for (Object f : tmp.keySet()) {
            cliqueFeatures.setCount(Interner.globalIntern(f), tmp.getCount(f));
            //cliqueFeatures.setCount(f, tmp.getCount(f));
          }
        } else {
          Collection tmp = wrapped.getCliqueFeatures(info, position, c);
          for (Object f : tmp) {
            cliqueFeatures.setCount(Interner.globalIntern(f), 1.0);
            //cliqueFeatures.setCount(f, 1.0);
          }
        }
        cache.put(c, cliqueFeatures);
      }
      String l1 = stringLabels.first();
      String l2 = stringLabels.second();
      for (Object f : cliqueFeatures.keySet()) {
        Object feature = new ImmutablePairOfImmutables(f, l1);
        newFeatures.incrementCount(feature, cliqueFeatures.getCount(f));
      }
      if (!l1.equals(l2)) { // i.e. its not all background symbols
        for (Object f : cliqueFeatures.keySet()) {
          Object feature = new ImmutablePairOfImmutables(f, l2);
        newFeatures.incrementCount(feature, cliqueFeatures.getCount(f));
        }
      }
    }
    return newFeatures;
  }

  protected static class ImmutablePairOfImmutables<F,S> extends Pair<F,S> {

    /**
     *
     */
    private static final long serialVersionUID = -9137857904295077788L;

    @Override
    public String toString() {
      return "<"+first+","+second+">::"+hashCode;
    }

    public ImmutablePairOfImmutables(F f, S s) {
      super(f,s);
      hashCode = super.hashCode();
    }

    int hashCode;

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public void setFirst(F f) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setSecond(S s) {
      throw new UnsupportedOperationException();
    }
  }

}
