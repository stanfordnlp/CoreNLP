package edu.stanford.nlp.classify;

import edu.stanford.nlp.optimization.BadLicenseHildreth;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.Counter;


/**
 * MIRA learning.
 * 
 * This class makes use of code (Hildreth) that was taken directly from 
 * Ryan McDonald's MSTParser.
 * 
 * The MSTParser is licensed under the Common Public License 1.0. 
 * This license is <strong>not</strong> GPL compatible[1]. The code should
 * not be included in any GPL licensed public release of JavaNLP.
 * 
 * TODO:BadLicense
 *
 * [1] http://www.gnu.org/licenses/license-list.html#CommonPublicLicense10
 *
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 * @author daniel cer   (http://dmcer.net)
 */
 
public class BadLicenseMIRAWeightUpdater<T, ID> implements CounterWeightUpdater<T, ID> {

  /**
   * Updates the weights in such a way that they are minimally changed subject to the constraint that
   * they now obey the constraints that the gold score minus the guessed score must be greater than the loss.
   */
  @SuppressWarnings({ "cast", "unchecked" })
  public Counter<T> getUpdate(Counter<T> weights, Counter<T>[] goldVectors, Counter<T>[] guessedVectors, double[] losses,
                               ID[] datumIDs, int iterSinceLastUpdate) {
    int len = losses.length;
    Counter<T>[] featureDifferences = (ClassicCounter<T>[])new ClassicCounter[len];
    double[] lossMinusScoreDiff = new double[len];
    for (int i=0; i<len; i++) {
      featureDifferences[i] = Counters.diff(goldVectors[i], guessedVectors[i]);
      double scoreDiff = Counters.dotProduct(featureDifferences[i], weights);
      lossMinusScoreDiff[i] = losses[i] - scoreDiff;
    }
    double[] alphas = BadLicenseHildreth.runHildreth(featureDifferences, lossMinusScoreDiff);
    ClassicCounter<T> result = new ClassicCounter<T>();
    for (int i=0; i<len; i++) {
      Counters.addInPlace(result, featureDifferences[i], alphas[i]);
    }
    return result;
  }

  @SuppressWarnings("unused")
  private boolean checkConstraints(ClassicCounter<T> weights, ClassicCounter<T>[] goldVectors, ClassicCounter<T>[] guessedVectors, double[] losses) {
    // for debugging:
    double guessScore = Counters.dotProduct(guessedVectors[0], weights);
    double goldScore = Counters.dotProduct(goldVectors[0], weights);
    double scoreDiff = goldScore-guessScore;
    double error = scoreDiff-losses[0];
    if (error<-1e-6) {
      return false;
    } 
    return true;
  }

  public void endEpoch() {
  }
  
}
