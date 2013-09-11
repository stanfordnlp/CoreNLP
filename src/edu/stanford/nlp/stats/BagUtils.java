package edu.stanford.nlp.stats;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.Collection;
import java.util.Map;

import edu.stanford.nlp.math.CollectionsMath;
import edu.stanford.nlp.stats.counters.objects.Object2DoubleCounter;
import edu.stanford.nlp.stats.counters.objects.Object2DoubleOpenHashCounter;
import edu.stanford.nlp.stats.counters.objects.Object2IntCounter;
import edu.stanford.nlp.stats.counters.objects.Object2IntOpenHashCounter;

/**
 * Utilities for manipulating bags of words.
 *
 * @author dramage
 */
public class BagUtils {

  private BagUtils() {
  }


  /**
   * Returns the count of how many documents each term takes part in.
   */
  public static <E, N extends Number, M extends Map<E,N>>
  Object2IntCounter<E> getDocumentFrequencies(Collection<M> docs) {

    Object2IntCounter<E> df = new Object2IntOpenHashCounter<E>();
    for (Map<E,N> doc : docs) {
      for (Map.Entry<E,N> e : doc.entrySet()) {
        if (e.getValue().doubleValue() > 0) {
          df.increment(e.getKey());
        }
      }
    }

    return df;
  }

  /**
   * Gets the tf-idf weight for each term in the given document frequencies
   * as Math.log(numDocs / df).
   */
  public static <E> Object2DoubleCounter<E> getIDFWeights(int numDocs, Object2IntCounter<E> df) {
    Object2DoubleCounter<E> weights = new Object2DoubleOpenHashCounter<E>();

    for (Object2IntMap.Entry<E> entry : df.object2IntEntrySet()) {
      weights.put(entry.getKey(), Math.log((double)numDocs / (double)entry.getIntValue()));
    }

    return weights;
  }

  /**
   * Gets the log-scale gaussian non-monotonic document frequency weight
   * for all terms.  This is a weighting function based on document
   * frequency analogous to the "idf" in "tf-idf" except that it is more
   * appropriate for similarity comparisons.  It penalizes very frequent
   * (usually including stop words such as "a" and "of") and also penalizes
   * very infrequent terms (that tend to add only add noise).  The penalty
   * is a gaussian over the log of the document frequencies; terms at the
   * mean get the highest score.
   *
   * See "Evaluating Strategies for Similarity Search on the Web" by
   * Haveliwala, Gionis, Klein, and Indyk.  WWW 2002.
   */
  public static <E> Object2DoubleCounter<E> getNMDFWeights(Object2IntCounter<E> df) {
    // compute mean and variance of the UNIQUE document frequency values
    final IntSet uniqueDf = new IntOpenHashSet(df.values());
    final DoubleList logUniqueDf = new DoubleArrayList(uniqueDf.size());
    for (int e : uniqueDf) {
      logUniqueDf.add(Math.log(e));
    }
    final double logmean = CollectionsMath.mean(logUniqueDf);
    final double logvar  = CollectionsMath.variance(logUniqueDf);

    // weight each term by its df's distance from logmean and logvar
    final Object2DoubleCounter<E> weights = new Object2DoubleOpenHashCounter<E>();
    for (Object2IntMap.Entry<E> entry : df.object2IntEntrySet()) {
      final double dist = Math.log(entry.getIntValue()) - logmean;
      weights.put(entry.getKey(), Math.exp(-.5 * (dist * dist / logvar)));
    }

    return weights;

//    // start by computing log document frequency
//    final Object2DoubleCounter<E> weights = new Object2DoubleOpenHashCounter<E>();
//    for (Object2IntMap.Entry<E> entry : df.object2IntEntrySet()) {
//      weights.put(entry.getKey(), Math.log(entry.getIntValue()));
//    }
//
//    double logmean = CollectionsMath.mean(weights.values());
//    double logvar  = CollectionsMath.variance(weights.values());
//
//    // re-weight logdf
//    for (Object2DoubleMap.Entry<E> entry : weights.object2DoubleEntrySet()) {
//      final double exp = (entry.getDoubleValue() - logmean);
//      entry.setValue(Math.exp(- .5 * (exp * exp / logvar)));
//    }
//
//    return weights;
  }

  /**
   * Re-weights and normalizes the given bags according to their log-tf log-idf
   * weights as per, e.g. Manning, et al Introduction to Information Retrieval
   * chapter 6.
   */
  public static <E,M extends Map<E,Double>> void weightByTFIDF(Collection<M> docs) {
    // how many documents each term appears in
    Object2IntCounter<E> df = getDocumentFrequencies(docs);

    final double numDocs = docs.size();

    // scale each by log idf
    for (Map<E,Double> doc : docs) {
      double doctotal = 0;

      // scale by idf_i=log(N / df_i)
      for (Map.Entry<E,Double> e : doc.entrySet()) {
        final double weight = Math.log(e.getValue()) * Math.log(numDocs / df.getInt(e.getKey()));
        e.setValue(weight);
        doctotal += weight;
      }

      // normalize
      for (Map.Entry<E,Double> e : doc.entrySet()) {
        e.setValue(e.getValue() / doctotal);
      }
    }
  }

  /**
   * Computes the log-scale gaussian non-monotonic document frequency for
   * a particular term.  This is a weighting function based on document
   * frequency analogous to the "idf" in "tf-idf" except that it is more
   * appropriate for similarity comparisons.  It penalizes very frequent
   * (usually including stop words such as "a" and "of") and also penalizes
   * very infrequent terms (that tend to add only add noise).  The penalty
   * is a gaussian over the log of the document frequencies; terms at the
   * mean get the highest score.
   *
   * See "Evaluating Strategies for Similarity Search on the Web" by
   * Haveliwala, Gionis, Klein, and Indyk.  WWW 2002.
   */
  public static <E,M extends Map<E,Double>> void weightByNMDF(Collection<M> docs) {
    Object2DoubleCounter<E> weights = getNMDFWeights(getDocumentFrequencies(docs));

    for (Map<E,Double> doc : docs) {
      double doctotal = 0;

      // scale each by weight
      for (Map.Entry<E,Double> e : doc.entrySet()) {
        final double weight = e.getValue() * weights.getDouble(e.getKey());
        e.setValue(weight);
        doctotal += weight;
      }

      // normalize
      for (Map.Entry<E,Double> e : doc.entrySet()) {
        e.setValue(e.getValue() / doctotal);
      }
    }
  }

  /**
   * Normalize the values in each map so that each sums to one.
   */
  public static<E,M extends Map<E,Double>> void normalize(Collection<M> docs) {
    for (M doc : docs) {
      normalize(doc);
    }
  }

  /**
   * Normalize the values in the map so that it sums to one.
   */
  public static<E,M extends Map<E,Double>> void normalize(M doc) {
    double total = 0;
    for (Map.Entry<E,Double> entry : doc.entrySet()) {
      total += entry.getValue();
    }
    for (Map.Entry<E,Double> entry : doc.entrySet()) {
      entry.setValue(entry.getValue() / total);
    }
  }
}
