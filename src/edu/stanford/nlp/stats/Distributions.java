package edu.stanford.nlp.stats;

import java.util.Set;

import edu.stanford.nlp.util.Generics;

/**
 * Static methods for operating on {@link Distributions}s.
 *
 * In general, if a method is operating on a pair of Distribution objects, we imagine that the
 * set of possible keys for each Distribution is the same.
 * Therefore we require that d1.numberOFKeys = d2.numberOfKeys and that the number of keys in the union
 * of the two key sets &lt;= numKeys
 *
 *
 * @author Jeff Michels (jmichels@stanford.edu)
 */
public class Distributions {

  private Distributions() {
  }


  protected static <K> Set<K> getSetOfAllKeys(Distribution<K> d1, Distribution<K> d2) {
    if (d1.getNumberOfKeys() != d2.getNumberOfKeys()){
      throw new RuntimeException("Tried to compare two Distribution<K> objects but d1.numberOfKeys != d2.numberOfKeys");
    }

    Set<K> allKeys = Generics.newHashSet(d1.getCounter().keySet());
    allKeys.addAll(d2.getCounter().keySet());
    if (allKeys.size() > d1.getNumberOfKeys()){
      throw new RuntimeException("Tried to compare two Distribution<K> objects but d1.counter intersect d2.counter > numberOfKeys");
    }
    return allKeys;
  }

  /**
   * Returns a double between 0 and 1 representing the overlap of d1 and d2.
   * Equals 0 if there is no overlap, equals 1 iff d1==d2
   */
  public static <K> double overlap(Distribution<K> d1, Distribution<K> d2) {
    Set<K> allKeys = getSetOfAllKeys(d1, d2);

    double result = 0.0;
    double remainingMass1 = 1.0;
    double remainingMass2 = 1.0;

    for (K key : allKeys){
      double p1 = d1.probabilityOf(key);
      double p2 = d2.probabilityOf(key);
      remainingMass1 -= p1;
      remainingMass2 -= p2;
      result += Math.min(p1, p2);
    }
    result += Math.min(remainingMass1, remainingMass2);
    return result;
  }

    /**
   * Returns a new {@code Distribution<K>} with counts averaged from the two given Distributions.
   * The average {@code Distribution<K>} will contain the union of keys in both
   * source Distributions, and each count will be the weighted average of the two source
   * counts for that key,  a missing count in one Distribution
   * is treated as if it has probability equal to that returned by the {@code probabilityOf()} function.
   *
   * @return A new distribution with counts that are the mean of the resp. counts
   *         in the given distributions with the remaining probability mass adjusted accordingly.
   */
  public static <K> Distribution<K> weightedAverage(Distribution<K> d1, double w1, Distribution<K> d2) {
    double w2 = 1.0 - w1;
    Set<K> allKeys = getSetOfAllKeys(d1, d2);
    int numKeys = d1.getNumberOfKeys();
    Counter<K> c = new ClassicCounter<>();

      for (K key : allKeys){
        double newProbability = d1.probabilityOf(key) * w1 + d2.probabilityOf(key) * w2;
        c.setCount(key, newProbability);
    }
    return (Distribution.getDistributionFromPartiallySpecifiedCounter(c, numKeys));
  }

  public static <K> Distribution<K> average(Distribution<K> d1, Distribution<K> d2) {
    return weightedAverage(d1, 0.5, d2);
  }

  /** we will multiply by this constant instead of divide by log(2) */
  private static final double LN_TO_LOG2 = 1. / Math.log(2);

  /**
   * Calculates the KL divergence between the two distributions.
   * That is, it calculates KL(from || to).
   * In other words, how well can d1 be represented by d2.
   * if there is some value in d1 that gets zero prob in d2, then return positive infinity.
   *
   * @return The KL divergence between the distributions
   */
  public static <K> double klDivergence(Distribution<K> from, Distribution<K> to) {
    Set<K> allKeys = getSetOfAllKeys(from, to);
    int numKeysRemaining = from.getNumberOfKeys();
    double result = 0.0;
    double assignedMass1 = 0.0;
    double assignedMass2 = 0.0;
    double p1, p2;
    double epsilon = 1e-10;

    for (K key : allKeys){
      p1 = from.probabilityOf(key);
      p2 = to.probabilityOf(key);
      numKeysRemaining--;
      assignedMass1 += p1;
      assignedMass2 += p2;
      if (p1 < epsilon) {
        continue;
      }
      double logFract = Math.log(p1 / p2);
      if (logFract == Double.POSITIVE_INFINITY) {
        System.out.println("Didtributions.kldivergence returning +inf: p1=" + p1 + ", p2=" +p2);
        System.out.flush();
        return Double.POSITIVE_INFINITY; // can't recover
      }
      result += p1 * logFract * LN_TO_LOG2; // express it in log base 2
    }

    if (numKeysRemaining != 0){
      p1 = (1.0 - assignedMass1) / numKeysRemaining;
      if (p1 > epsilon){
        p2 = (1.0 - assignedMass2) / numKeysRemaining;
        double logFract = Math.log(p1 / p2);
        if (logFract == Double.POSITIVE_INFINITY) {
          System.out.println("Distributions.klDivergence (remaining mass) returning +inf: p1=" + p1 + ", p2=" +p2);
          System.out.flush();
          return Double.POSITIVE_INFINITY; // can't recover
        }
        result += numKeysRemaining * p1 * logFract * LN_TO_LOG2; // express it in log base 2
      }
    }
    return result;
  }

  /**
   * Calculates the Jensen-Shannon divergence between the two distributions.
   * That is, it calculates 1/2 [KL(d1 || avg(d1,d2)) + KL(d2 || avg(d1,d2))] .
   *
   * @return The KL divergence between the distributions
   */
  public static <K> double jensenShannonDivergence(Distribution<K> d1, Distribution<K> d2) {
    Distribution<K> average = average(d1, d2);
    double kl1 = klDivergence(d1, average);
    double kl2 = klDivergence(d2, average);
    double js = (kl1 + kl2) / 2.0;
    return js;
  }

  /**
   * Calculates the skew divergence between the two distributions.
   * That is, it calculates KL(d1 || (d2*skew + d1*(1-skew))) .
   * In other words, how well can d1 be represented by a "smoothed" d2.
   *
   * @return The skew divergence between the distributions
   */
  public static <K> double skewDivergence(Distribution<K> d1, Distribution<K> d2, double skew) {
    Distribution<K> average = weightedAverage(d2, skew, d1);
    return klDivergence(d1, average);
  }

  /**
   * Calculates the information radius (aka the Jensen-Shannon divergence)
   * between the two Distributions.  This measure is defined as:
   * <blockquote> iRad(p,q) = D(p||(p+q)/2)+D(q,(p+q)/2) </blockquote>
   * where p is one Distribution, q is the other distribution, and D(p||q) is the
   * KL divergence bewteen p and q.  Note that iRad(p,q) = iRad(q,p).
   *
   * @return The information radius between the distributions
   */
  public static <K> double informationRadius(Distribution<K> d1, Distribution<K> d2) {
    Distribution<K> avg = average(d1, d2); // (p+q)/2
    return (klDivergence(d1, avg) + klDivergence(d2, avg));
  }

}
