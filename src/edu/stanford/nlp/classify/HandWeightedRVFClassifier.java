package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;


/**
 * A simple two-class linear classifier.  Every feature has a
 * manually-specified weight, and there is also a threshold.  The score of
 * a datum is just the sum, over all features, of the feature weight times
 * the feature value.  A datum is classified as a positive example if its
 * score exceeds the threshold.
 * 
 * If there is a special feature called SCALING_FACTOR, it is used to 
 * scale the score in getScore(). It makes sense to give this feature
 * a weight of 0 so that it does not figure in the sum.
 * 
 *
 * @author Bill MacCartney
 * @author Sebastian Pado
 */
public class HandWeightedRVFClassifier<F> implements RVFClassifier<Boolean, F> {

  /**
   * 
   */
  private static final long serialVersionUID = -8634473005977866255L;

  private static final boolean VERBOSE = true;

  private Counter<F> weights;                      // maps feature strings to weights
  private double threshold = 0.0;

  public HandWeightedRVFClassifier() {
    this(null, 0.0);
  }

  public HandWeightedRVFClassifier(Counter<F> weights) {
    this(weights, 0.0);
  }

  public HandWeightedRVFClassifier(Counter<F> weights, double threshold) {
    if (weights == null) {
      this.weights = new ClassicCounter<F>();
    } else {
      this.weights = new ClassicCounter<F>(weights); // defensive copy?
    }
    setThreshold(threshold);
  }


  // -----------------------------------------------------------------------

  public double getWeight(F feature) {
    return weights.getCount(feature);
  }

  public void setWeight(F feature, double weight) {
    weights.setCount(feature, weight);
  }

  public double getThreshold() {
    return threshold;
  }

  public void setThreshold(double threshold) {
    this.threshold = threshold;
  }

  /**
   * Computes a score for the specified datum by summing, over all
   * features, the feature weight (as specified manually in this
   * classifier) times the feature value (from the datum).
   */
  
    public double getScore(RVFDatum<Boolean, F> datum) {
      return getScore(datum,VERBOSE,System.err);
    }
  
    public double getScore(RVFDatum<Boolean, F> datum, boolean print, PrintStream ps) {
    double score = 0.0;
    double scalingFactor = 1;
    Counter<F> features = datum.asFeaturesCounter();

    if (print) {
      ps.printf("%-40s %8s %8s %8s%n",
                        "Feature",
                        "Count",
                        "Weight",
                        "Contrib");
      ps.printf("%-40s %8s %8s %8s%n",
                        "----------------------------------------",
                        "--------",
                        "--------",
                        "--------");
    }
    for (F feature : datum.asFeatures()) {
      if (print && (weights.getCount(feature) != 0)) {
        ps.printf("%-40s %8.4f %8.4f %8.4f%n",
                          feature.toString(),
                          features.getCount(feature),
                          weights.getCount(feature),
                          features.getCount(feature) * weights.getCount(feature));
      }
      
      if (feature.toString().equals("SCALING_FACTOR")) {
        scalingFactor = features.getCount(feature);
      }      
      score += features.getCount(feature) * weights.getCount(feature);
    }
    
    score /= scalingFactor; // scale score!
    
    if (print) {
      ps.printf("%-40s %8s %8s %8s%n",
                        "----------------------------------------",
                        "--------",
                        "--------",
                        "--------");
      if (scalingFactor != 1) {
        ps.printf("%-40s %8s %8s %8.4f%n",
            "score (unscaled)",
            "",
            "",
            score*scalingFactor);
        ps.printf("%-40s %8s %8s %8.4f%n",
            "scaling factor",
            "",
            "",
            scalingFactor);
        ps.printf("%-40s %8s %8s %8.4f%n",
            "score (scaled)",
            "",
            "",
            score);
        
      } else {      
      ps.printf("%-40s %8s %8s %8.4f%n",
          "score",
          "",
          "",
          score);
      }
      ps.printf("%-40s %8s %8s %8.4f%n",
                        "threshold",
                        "",
                        "",
                        threshold);
      ps.printf("%-40s %8s %8s %8s%n",
                        "positive?",
                        "",
                        "",
                        Boolean.valueOf(score >= threshold));
    }
    return score;
  }

  /**
   * Similar to getScore, but returns the nice verbose output
   * as a string (suitable for storage).
   */
  public String printScore(RVFDatum<Boolean, F> datum) {
    PrintStream buf = new PrintStream(new ByteArrayOutputStream());
    getScore(datum,true,buf);
    buf.close();
    return buf.toString();
  }
    

  /**
   * Returns true iff the datum is predicted to belong to the positive
   * class, i.e. if the datum's score exceeds the threshold specified for
   * this classifier.
   */
  public boolean isPositiveClass(RVFDatum<Boolean, F> datum) {
    return (getScore(datum) >= threshold);
  }

  /**
   * Returns a Counter containing a single score: the score we've computed
   * for the positive label, Boolean.TRUE.
   */
  public ClassicCounter<Boolean> scoresOf(RVFDatum<Boolean, F> datum) {
    ClassicCounter<Boolean> c = new ClassicCounter<Boolean>();
    c.setCount(Boolean.TRUE, getScore(datum));
    return c;
  }


  public Boolean classOf(RVFDatum<Boolean, F> datum) {
    if (isPositiveClass(datum)) return Boolean.TRUE;
    else return Boolean.FALSE;
  }

  public void print(PrintStream pw) {
    pw.println("HandWeightedRVFClassifier has threshold " + threshold + " and weights:");
    pw.println(weights.toString());
  }

  public void print() {
    print(System.out);
  }


  // -----------------------------------------------------------------------

  public static void main(String[] args) {
    ClassicCounter<String> weights = new ClassicCounter<String>();
    weights.setCount("isCap", 1.5);
    weights.setCount("hasNums", 2.5);
    weights.setCount("numWords", 3.5);
    weights.setCount("numChars", 4.5);

    HandWeightedRVFClassifier<String> model = new HandWeightedRVFClassifier<String>(weights, 16.0);
    model.print();

    ClassicCounter<String> features = new ClassicCounter<String>();
    features.setCount("isCap", 1.0);
    features.setCount("hasNums", 0.0);
    features.setCount("numWords", 4.0);
    features.setCount("funniness", 19.0);
    RVFDatum<Boolean,String> datum = new RVFDatum<Boolean,String>(features);

    System.out.println("Datum:  " + datum.asFeaturesCounter());
    System.out.println("Score:  " + model.getScore(datum));
    System.out.println("Posi?:  " + model.isPositiveClass(datum));

    System.out.println("Scores: " + model.scoresOf(datum));
    System.out.println("Class:  " + model.classOf(datum));
  }

}
