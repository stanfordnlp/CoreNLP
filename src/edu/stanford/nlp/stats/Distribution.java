package edu.stanford.nlp.stats; 

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Immutable class for representing normalized, smoothed discrete distributions
 * from {@link Counters}. Smoothed counters reserve probability mass for unseen
 * items, so queries for the probability of unseen items will return a small
 * positive amount.  Normalization is L1 normalization:
 * {@link #totalCount} should always return 1.
 * <p>
 * A Counter passed into a constructor is copied. This class is Serializable.
 *
 * @author Galen Andrew (galand@cs.stanford.edu), Sebastian Pado
 */
public class Distribution<E> implements Sampler<E>, ProbabilityDistribution<E>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Distribution.class);

  private static final long serialVersionUID = 6707148234288637809L;

  // todo [cdm Apr 2013]: Make these 3 variables final and put into constructor
  private int numberOfKeys;
  private double reservedMass;
  protected Counter<E> counter;
  private static final int NUM_ENTRIES_IN_STRING = 20;

  private static final boolean verbose = false;

  public Counter<E> getCounter() {
    return counter;
  }


  /**
   * Exactly the same as sampleFrom(), needed for the Sampler interface.
   */
  @Override
  public E drawSample() {
    return sampleFrom();
  }

  /**
   * A method to draw a sample, providing an own random number generator.
   * Needed for the ProbabilityDistribution interface.
   */
  @Override
  public E drawSample(Random random) {
    return sampleFrom(random);
  }

  public String toString(NumberFormat nf) {
    return Counters.toString(counter, nf);
  }

  public double getReservedMass() {
    return reservedMass;
  }

  public int getNumberOfKeys() {
    return numberOfKeys;
  }

  //--- cdm added Jan 2004 to help old code compile

  public Set<E> keySet() {
    return counter.keySet();
  }

  public boolean containsKey(E key) {
    return counter.containsKey(key);
  }

  /**
   * Returns the current count for the given key, which is 0 if it hasn't been
   * seen before. This is a convenient version of {@code get} that casts
   * and extracts the primitive value.
   *
   * @param key The key to look up.
   * @return The current count for the given key, which is 0 if it hasn't
   *     been seen before
   */
  public double getCount(E key) {
    return counter.getCount(key);
  }

  //---- end cdm added

  //--- JM added for Distributions

  /**
   * Assuming that c has a total count &lt; 1, returns a new Distribution using the counts in c as probabilities.
   * If c has a total count &gt; 1, returns a normalized distribution with no remaining mass.
   */
  public static <E> Distribution<E> getDistributionFromPartiallySpecifiedCounter(Counter<E> c, int numKeys){
    Distribution<E> d;
    double total = c.totalCount();
    if (total >= 1.0){
      d = getDistribution(c);
      d.numberOfKeys = numKeys;
    } else {
      d = new Distribution<>();
      d.numberOfKeys = numKeys;
      d.counter = c;
      d.reservedMass = 1.0 - total;
    }
    return d;
  }
  //--- end JM added


  /**
   * @param s a Collection of keys.
   */
  public static <E> Distribution<E> getUniformDistribution(Collection<E> s) {
    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();
    norm.numberOfKeys = s.size();
    norm.reservedMass = 0;
    double total = s.size();
    double count = 1.0 / total;
    for (E key : s) {
      norm.counter.setCount(key, count);
    }
    return norm;
  }

  /**
   * @param s a Collection of keys.
   */
  public static <E> Distribution<E> getPerturbedUniformDistribution(Collection<E> s, Random r) {
    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();
    norm.numberOfKeys = s.size();
    norm.reservedMass = 0;
    double total = s.size();
    double prob = 1.0 / total;
    double stdev = prob / 1000.0;
    for (E key : s) {
      norm.counter.setCount(key, prob + (r.nextGaussian() * stdev));
    }
    return norm;
  }

  public static <E> Distribution<E> getPerturbedDistribution(Counter<E> wordCounter, Random r) {
    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();
    norm.numberOfKeys = wordCounter.size();
    norm.reservedMass = 0;
    double totalCount = wordCounter.totalCount();
    double stdev = 1.0 / norm.numberOfKeys / 1000.0; // tiny relative to average value
    for (E key : wordCounter.keySet()) {
      double prob = wordCounter.getCount(key) / totalCount;
      double perturbedProb = prob + (r.nextGaussian() * stdev);
      if (perturbedProb < 0.0) {
        perturbedProb = 0.0;
      }
      norm.counter.setCount(key, perturbedProb);
    }
    return norm;
  }

  /**
   * Creates a Distribution from the given counter. It makes an internal
   * copy of the counter and divides all counts by the total count.
   *
   * @return a new Distribution
   */
  public static <E> Distribution<E> getDistribution(Counter<E> counter) {
    return getDistributionWithReservedMass(counter, 0.0);
  }

  public static <E> Distribution<E> getDistributionWithReservedMass(Counter<E> counter, double reservedMass) {
    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();
    norm.numberOfKeys = counter.size();
    norm.reservedMass = reservedMass;
    double total = counter.totalCount() * (1 + reservedMass);
    if (total == 0.0) {
      total = 1.0;
    }
    for (E key : counter.keySet()) {
      double count = counter.getCount(key) / total;
      //      if (Double.isNaN(count) || count < 0.0 || count> 1.0 ) throw new RuntimeException("count=" + counter.getCount(key) + " total=" + total);
      norm.counter.setCount(key, count);
    }
    return norm;
  }

  /**
   * Creates a Distribution from the given counter, ie makes an internal
   * copy of the counter and divides all counts by the total count.
   *
   * @return a new Distribution
   */
  public static <E> Distribution<E> getDistributionFromLogValues(Counter<E> counter) {
    Counter<E> c = new ClassicCounter<>();
    // go through once to get the max
    // shift all by max so as to minimize the possibility of underflow
    double max = Counters.max(counter); // Thang 17Feb12: max should operate on counter instead of c, fixed!
    for (E key : counter.keySet()) {
      double count = Math.exp(counter.getCount(key) - max);
      c.setCount(key, count);
    }
    return getDistribution(c);
  }

  public static <E> Distribution<E> absolutelyDiscountedDistribution(Counter<E> counter, int numberOfKeys, double discount) {
    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();
    double total = counter.totalCount();
    double reservedMass = 0.0;
    for (E key : counter.keySet()) {
      double count = counter.getCount(key);
      if (count > discount) {
        double newCount = (count - discount) / total;
        norm.counter.setCount(key, newCount); // a positive count left over
        //        System.out.println("seen: " + newCount);
        reservedMass += discount;
      } else { // count <= discount
        reservedMass += count;
        // if the count <= discount, don't put key in counter, and we treat it as unseen!!
      }
    }
    norm.numberOfKeys = numberOfKeys;
    norm.reservedMass = reservedMass / total;
    if (verbose) {
      log.info("unseenKeys=" + (norm.numberOfKeys - norm.counter.size()) + " seenKeys=" + norm.counter.size() + " reservedMass=" + norm.reservedMass);
      double zeroCountProb = (norm.reservedMass / (numberOfKeys - norm.counter.size()));
      log.info("0 count prob: " + zeroCountProb);
      if (discount >= 1.0) {
        log.info("1 count prob: " + zeroCountProb);
      } else {
        log.info("1 count prob: " + (1.0 - discount) / total);
      }
      if (discount >= 2.0) {
        log.info("2 count prob: " + zeroCountProb);
      } else {
        log.info("2 count prob: " + (2.0 - discount) / total);
      }
      if (discount >= 3.0) {
        log.info("3 count prob: " + zeroCountProb);
      } else {
        log.info("3 count prob: " + (3.0 - discount) / total);
      }
    }
    //    System.out.println("UNSEEN: " + reservedMass / total / (numberOfKeys - counter.size()));
    return norm;
  }

  /**
   * Creates an Laplace smoothed Distribution from the given counter, ie adds one count
   * to every item, including unseen ones, and divides by the total count.
   *
   * @return a new add-1 smoothed Distribution
   */
  public static <E> Distribution<E> laplaceSmoothedDistribution(Counter<E> counter, int numberOfKeys) {
    return laplaceSmoothedDistribution(counter, numberOfKeys, 1.0);
  }

  /**
   * Creates a smoothed Distribution using Lidstone's law, ie adds lambda (typically
   * between 0 and 1) to every item, including unseen ones, and divides by the total count.
   *
   * @return a new Lidstone smoothed Distribution
   */
  public static <E> Distribution<E> laplaceSmoothedDistribution(Counter<E> counter, int numberOfKeys, double lambda) {
    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();
    double total = counter.totalCount();
    double newTotal = total + (lambda * numberOfKeys);
    double reservedMass = ((double) numberOfKeys - counter.size()) * lambda / newTotal;
    if (verbose) {
      log.info(((double) numberOfKeys - counter.size()) + " * " + lambda + " / (" + total + " + ( " + lambda + " * " + (double) numberOfKeys + ") )");
    }
    norm.numberOfKeys = numberOfKeys;
    norm.reservedMass = reservedMass;
    if (verbose) {
      log.info("reserved mass=" + reservedMass);
    }
    for (E key : counter.keySet()) {
      double count = counter.getCount(key);
      norm.counter.setCount(key, (count + lambda) / newTotal);
    }
    if (verbose) {
      log.info("unseenKeys=" + (norm.numberOfKeys - norm.counter.size()) + " seenKeys=" + norm.counter.size() + " reservedMass=" + norm.reservedMass);
      log.info("0 count prob: " + lambda / newTotal);
      log.info("1 count prob: " + (1.0 + lambda) / newTotal);
      log.info("2 count prob: " + (2.0 + lambda) / newTotal);
      log.info("3 count prob: " + (3.0 + lambda) / newTotal);
    }
    return norm;
  }

  /**
   * Creates a smoothed Distribution with Laplace smoothing, but assumes an explicit
   * count of "UNKNOWN" items.  Thus anything not in the original counter will have
   * probability zero.
   *
   * @param counter the counter to normalize
   * @param lambda  the value to add to each count
   * @param UNK     the UNKNOWN symbol
   * @return a new Laplace-smoothed distribution
   */
  public static <E> Distribution<E> laplaceWithExplicitUnknown(Counter<E> counter, double lambda, E UNK) {
    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();
    double total = counter.totalCount() + (lambda * (counter.size() - 1));
    norm.numberOfKeys = counter.size();
    norm.reservedMass = 0.0;
    for (E key : counter.keySet()) {
      if (key.equals(UNK)) {
        norm.counter.setCount(key, counter.getCount(key) / total);
      } else {
        norm.counter.setCount(key, (counter.getCount(key) + lambda) / total);
      }
    }
    return norm;
  }

  /**
   * Creates a Good-Turing smoothed Distribution from the given counter.
   *
   * @return a new Good-Turing smoothed Distribution.
   */
  public static <E> Distribution<E> goodTuringSmoothedCounter(Counter<E> counter, int numberOfKeys) {
    // gather count-counts
    int[] countCounts = getCountCounts(counter);

    // if count-counts are unreliable, we shouldn't be using G-T
    // revert to laplace
    for (int i = 1; i <= 10; i++) {
      if (countCounts[i] < 3) {
        return laplaceSmoothedDistribution(counter, numberOfKeys, 0.5);
      }
    }

    double observedMass = counter.totalCount();
    double reservedMass = countCounts[1] / observedMass;

    // calculate and cache adjusted frequencies
    // also adjusting total mass of observed items
    double[] adjustedFreq = new double[10];
    for (int freq = 1; freq < 10; freq++) {
      adjustedFreq[freq] = (double) (freq + 1) * (double) countCounts[freq + 1] / countCounts[freq];
      observedMass -= (freq - adjustedFreq[freq]) * countCounts[freq];
    }

    double normFactor = (1.0 - reservedMass) / observedMass;

    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();

    // fill in the new Distribution, renormalizing as we go
    for (E key : counter.keySet()) {
      int origFreq = (int) Math.round(counter.getCount(key));
      if (origFreq < 10) {
        norm.counter.setCount(key, adjustedFreq[origFreq] * normFactor);
      } else {
        norm.counter.setCount(key, origFreq * normFactor);
      }
    }

    norm.numberOfKeys = numberOfKeys;
    norm.reservedMass = reservedMass;
    return norm;
  }

  /**
   * Creates a Good-Turing smoothed Distribution from the given counter without
   * creating any reserved mass-- instead, the special object UNK in the counter
   * is assumed to be the count of "UNSEEN" items.  Probability of objects not in
   * original counter will be zero.
   *
   * @param counter the counter
   * @param UNK     the unknown symbol
   * @return a good-turing smoothed distribution
   */
  public static <E> Distribution<E> goodTuringWithExplicitUnknown(Counter<E> counter, E UNK) {
    // gather count-counts
    int[] countCounts = getCountCounts(counter);

    // if count-counts are unreliable, we shouldn't be using G-T
    // revert to laplace
    for (int i = 1; i <= 10; i++) {
      if (countCounts[i] < 3) {
        return laplaceWithExplicitUnknown(counter, 0.5, UNK);
      }
    }

    double observedMass = counter.totalCount();

    // calculate and cache adjusted frequencies
    // also adjusting total mass of observed items
    double[] adjustedFreq = new double[10];
    for (int freq = 1; freq < 10; freq++) {
      adjustedFreq[freq] = (double) (freq + 1) * (double) countCounts[freq + 1] / countCounts[freq];
      observedMass -= (freq - adjustedFreq[freq]) * countCounts[freq];
    }

    Distribution<E> norm = new Distribution<>();
    norm.counter = new ClassicCounter<>();

    // fill in the new Distribution, renormalizing as we go
    for (E key : counter.keySet()) {
      int origFreq = (int) Math.round(counter.getCount(key));
      if (origFreq < 10) {
        norm.counter.setCount(key, adjustedFreq[origFreq] / observedMass);
      } else {
        norm.counter.setCount(key, origFreq / observedMass);
      }
    }

    norm.numberOfKeys = counter.size();
    norm.reservedMass = 0.0;
    return norm;

  }

  private static <E> int[] getCountCounts(Counter<E> counter) {
    int[] countCounts = new int[11];
    for (int i = 0; i <= 10; i++) {
      countCounts[i] = 0;
    }
    for (E key : counter.keySet()) {
      int count = (int) Math.round(counter.getCount(key));
      if (count <= 10) {
        countCounts[count]++;
      }
    }
    return countCounts;
  }


  // ----------------------------------------------------------------------------

  /**
   * Creates a Distribution from the given counter using Gale &amp; Sampsons'
   * "simple Good-Turing" smoothing.
   *
   * @return a new simple Good-Turing smoothed Distribution.
   */
  public static <E> Distribution<E> simpleGoodTuring(Counter<E> counter, int numberOfKeys) {

    // check arguments
    validateCounter(counter);
    int numUnseen = numberOfKeys - counter.size();
    if (numUnseen < 1)
      throw new IllegalArgumentException(String.format("ERROR: numberOfKeys %d must be > size of counter %d!", numberOfKeys, counter.size()));

    // do smoothing
    int[][] cc = countCounts2IntArrays(collectCountCounts(counter));
    int[] r = cc[0];                    // counts
    int[] n = cc[1];                    // counts of counts
    SimpleGoodTuring sgt = new SimpleGoodTuring(r, n);

    // collate results
    Counter<Integer> probsByCount = new ClassicCounter<>();
    double[] probs = sgt.getProbabilities();
    for (int i = 0; i < probs.length; i++) {
      probsByCount.setCount(r[i], probs[i]);
    }

    // make smoothed distribution
    Distribution<E> dist = new Distribution<>();
    dist.counter = new ClassicCounter<>();
    for (Map.Entry<E, Double> entry : counter.entrySet()) {
      E item = entry.getKey();
      Integer count = (int) Math.round(entry.getValue());
      dist.counter.setCount(item, probsByCount.getCount(count));
    }
    dist.numberOfKeys = numberOfKeys;
    dist.reservedMass = sgt.getProbabilityForUnseen();
    return dist;

  }

  /* Helper to simpleGoodTuringSmoothedCounter() */
  private static <E> void validateCounter(Counter<E> counts) {
    for (Map.Entry<E, Double> entry : counts.entrySet()) {
      E item = entry.getKey();
      Double dblCount = entry.getValue();
      if (dblCount == null) {
        throw new IllegalArgumentException("ERROR: null count for item " + item + "!");
      }
      if (dblCount < 0) {
        throw new IllegalArgumentException("ERROR: negative count " + dblCount + " for item " + item + "!");
      }
    }
  }

  /* Helper to simpleGoodTuringSmoothedCounter() */
  private static <E> Counter<Integer> collectCountCounts(Counter<E> counts) {
    Counter<Integer> cc = new ClassicCounter<>(); // counts of counts
    for (Map.Entry<E, Double> entry : counts.entrySet()) {
      //E item = entry.getKey();
      Integer count = (int) Math.round(entry.getValue());
      cc.incrementCount(count);
    }
    return cc;
  }

  /* Helper to simpleGoodTuringSmoothedCounter() */
  private static int[][] countCounts2IntArrays(Counter<Integer> countCounts) {
    int size = countCounts.size();
    int[][] arrays = new int[2][];
    arrays[0] = new int[size]; // counts
    arrays[1] = new int[size]; // count counts
    PriorityQueue<Integer> q = new PriorityQueue<>(countCounts.keySet());
    int i = 0;
    while (!q.isEmpty()) {
      Integer count = q.poll();
      Integer countCount = (int) Math.round(countCounts.getCount(count));
      arrays[0][i] = count;
      arrays[1][i] = countCount;
      i++;
    }
    return arrays;
  }


  // ----------------------------------------------------------------------------

  /**
   * Returns a Distribution that uses prior as a Dirichlet prior
   * weighted by weight.  Essentially adds "pseudo-counts" for each Object
   * in prior equal to that Object's mass in prior times weight,
   * then normalizes.
   * <p>
   * WARNING: If unseen item is encountered in c, total may not be 1.
   * NOTE: This will not work if prior is a DynamicDistribution
   * to fix this, you could add a CounterView to Distribution and use that
   * in the linearCombination call below
   *
   * @param weight multiplier of prior to get "pseudo-count"
   * @return new Distribution
   */
  public static <E> Distribution<E> distributionWithDirichletPrior(Counter<E> c, Distribution<E> prior, double weight) {
    Distribution<E> norm = new Distribution<>();
    double totalWeight = c.totalCount() + weight;
    if (prior instanceof DynamicDistribution) {
      throw new UnsupportedOperationException("Cannot make normalized counter with Dynamic prior.");
    }
    norm.counter = Counters.linearCombination(c, 1 / totalWeight, prior.counter, weight / totalWeight);
    norm.numberOfKeys = prior.numberOfKeys;
    norm.reservedMass = prior.reservedMass * weight / totalWeight;
    //System.out.println("totalCount: " + norm.totalCount());
    return norm;
  }

  /**
   * Like normalizedCounterWithDirichletPrior except probabilities are
   * computed dynamically from the counter and prior instead of all at once up front.
   * The main advantage of this is if you are making many distributions from relatively
   * sparse counters using the same relatively dense prior, the prior is only represented
   * once, for major memory savings.
   *
   * @param weight multiplier of prior to get "pseudo-count"
   * @return new Distribution
   */
  public static <E> Distribution<E> dynamicCounterWithDirichletPrior(Counter<E> c, Distribution<E> prior, double weight) {
    double totalWeight = c.totalCount() + weight;
    Distribution<E> norm = new DynamicDistribution<>(prior, weight / totalWeight);
    norm.counter = new ClassicCounter<>();
    // this might be done more efficiently with entrySet but there isn't a way to get
    // the entrySet from a Counter now.  In most cases c will be small(-ish) anyway
    for (E key : c.keySet()) {
      double count = c.getCount(key) / totalWeight;
      prior.addToKeySet(key);
      norm.counter.setCount(key, count);
    }
    norm.numberOfKeys = prior.numberOfKeys;
    return norm;
  }

  private static class DynamicDistribution<E> extends Distribution<E> {

    private static final long serialVersionUID = -6073849364871185L;
    private final Distribution<E> prior;
    private final double priorMultiplier;

    public DynamicDistribution(Distribution<E> prior, double priorMultiplier) {
      super();
      this.prior = prior;
      this.priorMultiplier = priorMultiplier;
    }

    @Override
    public double probabilityOf(E o) {
      return this.counter.getCount(o) + prior.probabilityOf(o) * priorMultiplier;
    }

    @Override
    public double totalCount() {
      return this.counter.totalCount() + prior.totalCount() * priorMultiplier;
    }

    @Override
    public Set<E> keySet() {
      return prior.keySet();
    }

    @Override
    public void addToKeySet(E o) {
      prior.addToKeySet(o);
    }

    @Override
    public boolean containsKey(E key) {
      return prior.containsKey(key);
    }

    @Override
    public E argmax() {
      return Counters.argmax(Counters.linearCombination(this.counter, 1.0, prior.counter, priorMultiplier));
    }

    @Override
    public E sampleFrom() {
      double d = Math.random();
      Set<E> s = prior.keySet();
      for (E o : s) {
        d -= probabilityOf(o);
        if (d < 0) {
          return o;
        }
      }
      log.error("Distribution sums to less than 1");
      log.info("Sampled " + d + "      sum is " + totalCount());
      throw new RuntimeException("");
    }
  }

  /**
   * Maps a counter representing the linear weights of a multiclass
   * logistic regression model to the probabilities of each class.
   */
  public static <E> Distribution<E> distributionFromLogisticCounter(Counter<E> cntr) {
    double expSum = 0.0;
    int numKeys = 0;
    for (E key : cntr.keySet()) {
      expSum += Math.exp(cntr.getCount(key));
      numKeys++;
    }
    Distribution<E> probs = new Distribution<>();
    probs.counter = new ClassicCounter<>();
    probs.reservedMass = 0.0;
    probs.numberOfKeys = numKeys;
    for (E key : cntr.keySet()) {
      probs.counter.setCount(key, Math.exp(cntr.getCount(key)) / expSum);
    }
    return probs;
  }

  /**
   * Returns an object sampled from the distribution using Math.random().
   * There may be a faster way to do this if you need to...
   *
   * @return a sampled object
   */
  public E sampleFrom() {
    return Counters.sample(counter);
  }
  /**
   * Returns an object sampled from the distribution using a self-provided
   * random number generator.
   *
   * @return a sampled object
   */
  public E sampleFrom(Random random) {
    return Counters.sample(counter, random);
  }

  /**
   * Returns the normalized count of the given object.
   *
   * @return the normalized count of the object
   */
  public double probabilityOf(E key) {
    if (counter.containsKey(key)) {
      return counter.getCount(key);
    } else {
      int remainingKeys = numberOfKeys - counter.size();
      if (remainingKeys <= 0) {
        return 0.0;
      } else {
        return (reservedMass / remainingKeys);
      }
    }
  }

  /**
   * Returns the natural logarithm of the object's probability
   *
   * @return the logarithm of the normalised count (may be NaN if Pr==0.0)
   */
  public double logProbabilityOf(E key) {
    double prob = probabilityOf(key);
    return Math.log(prob);
  }

  public E argmax() {
    return Counters.argmax(counter);
  }

  public double totalCount() {
    return counter.totalCount() + reservedMass;
  }

  /**
   * Insures that object is in keyset (with possibly zero value)
   *
   * @param o object to put in keyset
   */
  public void addToKeySet(E o) {
    if (!counter.containsKey(o)) {
      counter.setCount(o, 0);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return o instanceof Distribution && equals((Distribution) o);
  }

  public boolean equals(Distribution<E> distribution) {
    if (numberOfKeys != distribution.numberOfKeys) {
      return false;
    }
    if (reservedMass != distribution.reservedMass) {
      return false;
    }
    return counter.equals(distribution.counter);
  }

  @Override
  public int hashCode() {
    int result = numberOfKeys;
    long temp = Double.doubleToLongBits(reservedMass);
    result = 29 * result + (int) (temp ^ (temp >>> 32));
    result = 29 * result + counter.hashCode();
    return result;
  }

  // no public constructor; use static methods instead
  private Distribution() {}

  @Override
  public String toString() {
    NumberFormat nf = new DecimalFormat("0.0##E0");
    List<E> keyList = new ArrayList<>(keySet());
    Collections.sort(keyList, (o1, o2) -> {
      if (probabilityOf(o1) < probabilityOf(o2)) {
        return 1;
      } else {
        return -1;
      }
    });
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < NUM_ENTRIES_IN_STRING; i++) {
      if (keyList.size() <= i) {
        break;
      }
      E o = keyList.get(i);
      double prob = probabilityOf(o);
      sb.append(o).append(":").append(nf.format(prob)).append(" ");
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * For internal testing purposes only.
   */
  public static void main(String[] args) {
    Counter<String> c2 = new ClassicCounter<>();
    c2.incrementCount("p", 13);
    c2.setCount("q", 12);
    c2.setCount("w", 5);
    c2.incrementCount("x", 7.5);
    // System.out.println(getDistribution(c2).getCount("w") + " should be 0.13333");

    ClassicCounter<String> c = new ClassicCounter<>();

    final double p = 1000;

    String UNK = "!*UNKNOWN*!";
    Set<String> s = Generics.newHashSet();
    s.add(UNK);

    // fill counter with roughly Zipfian distribution
    //    "1" : 1000
    //    "2" :  500
    //    "3" :  333
    //       ...
    //  "UNK" :   45
    //       ...
    //  "666" :    2
    //  "667" :    1
    //       ...
    // "1000" :    1
    for (int rank = 1; rank < 2000; rank++) {
      String i = String.valueOf(rank);
      c.setCount(i, Math.round(p / rank));
      s.add(i);
    }
    for (int rank = 2000; rank <= 4000; rank++) {
      String i = String.valueOf(rank);
      s.add(i);
    }

    Distribution<String> n = getDistribution(c);
    Distribution<String> prior = getUniformDistribution(s);
    Distribution<String> dir1 = distributionWithDirichletPrior(c, prior, 4000);
    Distribution<String> dir2 = dynamicCounterWithDirichletPrior(c, prior, 4000);
    Distribution<String> add1;
    Distribution<String> gt;
    if (true) {
      add1 = laplaceSmoothedDistribution(c, 4000);
      gt = goodTuringSmoothedCounter(c, 4000);
    } else {
      c.setCount(UNK, 45);
      add1 = laplaceWithExplicitUnknown(c, 0.5, UNK);
      gt = goodTuringWithExplicitUnknown(c, UNK);
    }
    Distribution<String> sgt = simpleGoodTuring(c, 4000);

    System.out.printf("%10s %10s %10s %10s %10s %10s %10s%n",
                      "Freq", "Norm", "Add1", "Dir1", "Dir2", "GT", "SGT");
    System.out.printf("%10s %10s %10s %10s %10s %10s %10s%n",
                      "----------", "----------", "----------", "----------", "----------", "----------", "----------");

    for (int i = 1; i < 5; i++) {
      System.out.printf("%10d ", Math.round(p / i));
      String in = String.valueOf(i);
      System.out.printf("%10.8f ", n.probabilityOf(String.valueOf(in)));
      System.out.printf("%10.8f ", add1.probabilityOf(in));
      System.out.printf("%10.8f ", dir1.probabilityOf(in));
      System.out.printf("%10.8f ", dir2.probabilityOf(in));
      System.out.printf("%10.8f ", gt.probabilityOf(in));
      System.out.printf("%10.8f ", sgt.probabilityOf(in));
      System.out.println();
    }

    System.out.printf("%10s %10s %10s %10s %10s %10s %10s%n",
                      "----------", "----------", "----------", "----------", "----------", "----------", "----------");
    System.out.printf("%10d ", 1);
    String last = String.valueOf(1500);
    System.out.printf("%10.8f ", n.probabilityOf(last));
    System.out.printf("%10.8f ", add1.probabilityOf(last));
    System.out.printf("%10.8f ", dir1.probabilityOf(last));
    System.out.printf("%10.8f ", dir2.probabilityOf(last));
    System.out.printf("%10.8f ", gt.probabilityOf(last));
    System.out.printf("%10.8f ", sgt.probabilityOf(last));
    System.out.println();

    System.out.printf("%10s %10s %10s %10s %10s %10s %10s%n",
                      "----------", "----------", "----------", "----------", "----------", "----------", "----------");
    System.out.printf("%10s ", "UNK");
    System.out.printf("%10.8f ", n.probabilityOf(UNK));
    System.out.printf("%10.8f ", add1.probabilityOf(UNK));
    System.out.printf("%10.8f ", dir1.probabilityOf(UNK));
    System.out.printf("%10.8f ", dir2.probabilityOf(UNK));
    System.out.printf("%10.8f ", gt.probabilityOf(UNK));
    System.out.printf("%10.8f ", sgt.probabilityOf(UNK));
    System.out.println();

    System.out.printf("%10s %10s %10s %10s %10s %10s %10s%n",
                      "----------", "----------", "----------", "----------", "----------", "----------", "----------");
    System.out.printf("%10s ", "RESERVE");
    System.out.printf("%10.8f ", n.getReservedMass());
    System.out.printf("%10.8f ", add1.getReservedMass());
    System.out.printf("%10.8f ", dir1.getReservedMass());
    System.out.printf("%10.8f ", dir2.getReservedMass());
    System.out.printf("%10.8f ", gt.getReservedMass());
    System.out.printf("%10.8f ", sgt.getReservedMass());
    System.out.println();

    System.out.printf("%10s %10s %10s %10s %10s %10s %10s%n",
                      "----------", "----------", "----------", "----------", "----------", "----------", "----------");
    System.out.printf("%10s ", "Total");
    System.out.printf("%10.8f ", n.totalCount());
    System.out.printf("%10.8f ", add1.totalCount());
    System.out.printf("%10.8f ", dir1.totalCount());
    System.out.printf("%10.8f ", dir2.totalCount());
    System.out.printf("%10.8f ", gt.totalCount());
    System.out.printf("%10.8f ", sgt.totalCount());
    System.out.println();

  }

}
