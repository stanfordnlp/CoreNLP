package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.ie.NERFeatureFactory;

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
public class FeatureFactoryWrapper<IN extends CoreLabel> extends Type2FeatureFactory<IN> {

  private static final long serialVersionUID = -2370420062000889441L;

  public FeatureFactoryWrapper (FeatureFactory<IN> featureFactory) {
    wrapped = featureFactory;
  }

  @Override
  public void init(SeqClassifierFlags flags) {
    super.init(flags);
    if (!(wrapped instanceof RVFFeatureFactory)) {
      flags.booleanFeatures = true;
    }
  }

  private final FeatureFactory<IN> wrapped; // initialized in constructor

  private Map<Clique, ClassicCounter<String>> cache = new HashMap<Clique, ClassicCounter<String>>();
  private int position = -1;

  public void clearSubstrings() {
    ((NERFeatureFactory)wrapped).clearMemory();
  }

  @Override
  public ClassicCounter getFeatures(PaddedList<IN> info, int position, LabeledClique lc) {

    if (position != this.position) {
      cache = new HashMap<Clique, ClassicCounter<String>>();
      this.position = position;
      //System.err.println("hello ... ");
    }
    //System.err.println("world");

    ClassicCounter<Pair<String,LabeledClique>> newFeatures = new ClassicCounter<Pair<String,LabeledClique>>();
    for (Clique c : wrapped.getCliques()) {
      ClassicCounter<String> cliqueFeatures = cache.get(c); // RS: the keys are the names of the features?
      LabeledClique cliqueLabel = LabeledClique.valueOf(c, lc, 0);
      if (cliqueFeatures == null) {
        cliqueFeatures = new ClassicCounter<String>();
        if (wrapped instanceof RVFFeatureFactory) {
          ClassicCounter<String> tmp = ((RVFFeatureFactory<IN>)wrapped).getCliqueFeaturesRVF(info, position, c);
          for (String f : tmp.keySet()) {
            cliqueFeatures.setCount(Interner.globalIntern(f), tmp.getCount(f));
            //cliqueFeatures.setCount(f, tmp.getCount(f));
          }
        } else {
          Collection<String> tmp = wrapped.getCliqueFeatures(info, position, c);
          for (String f : tmp) {
            cliqueFeatures.setCount(Interner.globalIntern(f), 1.0);
            //cliqueFeatures.setCount(f, 1.0);
          }
        }
        cache.put(c, cliqueFeatures);
      }
      for (String f : cliqueFeatures.keySet()) {
        Pair<String,LabeledClique> feature = new ImmutablePairOfImmutables<String,LabeledClique>(f, cliqueLabel);
        newFeatures.incrementCount(feature, cliqueFeatures.getCount(f));
      }
    }
    return newFeatures;
  }

  protected static class ImmutablePairOfImmutables<F,S> extends Pair<F,S> {

    private static final long serialVersionUID = 3145321753043003608L;

    protected Object readResolve() {
      this.first = Interner.globalIntern(this.first);
      return this;
    }

    @Override
    public String toString() {
      return "<"+first+","+second+">";
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

    // TODO: This needs to be rewritten using generic types!!!
    @Override
    public int compareTo(Pair<F, S> other) {
      ImmutablePairOfImmutables<F,S> o = (ImmutablePairOfImmutables<F,S>)other;
      String s1 = (String)o.first();
      String s2 = (String)first();

      int c = s2.compareTo(s1);
      if (c == 0) {
        s1 = ((LabeledClique)o.second()).clique.toString();
        s2 = ((LabeledClique)second()).clique.toString();
        c = s2.compareTo(s1);
      }
      return c;
    }
  }

}
