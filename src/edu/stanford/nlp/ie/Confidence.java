package edu.stanford.nlp.ie;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Meant to serve as a parallel structure to the KAON <code>Instance</code>.
 * Confidence ratings are specifically used when two potentially compatible
 * Instances are merged together but contain conflicting information --
 * if the confidence rating for field A is high in Instance X but low
 * in Instance Y, then one might choose to use X's version of A over Y's.
 * <p/>
 * To use a Confidence object, initialize it when the Instance is created.
 * Confidence ratings for individual fields can be set by refering to the
 * relation or the relation name.  In addition, two global metrics per
 * Confidence are supported -- a penalty and a global ranking -- neither
 * of which are field-specific.  The penalty can be used to keep track of
 * things like field conflicts, while the overall ranking can keep track
 * of search rank, for example.  The paradigm is set up such that the
 * overall "score" of a Confidence object (which is the overall score
 * of the corresponding Instance) is the sum of the confidence rankings
 * for each field, plus the global ranking, minus the penalty.
 *
 * @author Miler Lee miler@cs.stanford.edu
 * @author Joseph Smarr (jsmarr@stanford.edu) - new extractor interface 2/12/02
 * @version 25 July 2002
 */


public class Confidence {


  private HashMap<String, Double> rankings;
  private double overallRanking = 0;
  private double penalty = 0;

  /**
   * Empty constructor.
   */
  public Confidence() {
  }

  /**
   * Set the confidence ranking for a field, specified by slot.  Should
   * probably be (0.0, 1.0], but can conceivably be some other value. Any
   * previous ranking is overwritten.
   *
   * @param slot    the slot corresponding to the field that
   *                was filled in the parallel Instance
   * @param ranking the confidence ranking
   */
  public void setConfidence(String slot, double ranking) {
    rankings.put(slot, new Double(ranking));
  }


  /**
   * Returns the confidence ranking for the particular field.
   *
   * @param slot the slot to get the confidence value for
   * @return the ranking or 0 if the relation doesn't exist or
   *         has not been ranked
   */

  public double getConfidence(String slot) {
    Double d = rankings.get(slot);
    if (d != null) {
      return d.doubleValue();
    }
    return 0;
  }


  /**
   * Returns the sum of getConfidence called on all relations.
   */
  public double getConfidenceSum() {
    double sum = 0;
    double temp;
    Iterator<Double> iter = rankings.values().iterator();
    while (iter.hasNext()) {
      if ((temp = (iter.next()).doubleValue()) != -1) {
        sum += temp;
      }
    }
    return sum;
  }


  /**
   * The Confidence object can store an additional global confidence
   * ranking, as decided by the merging functionality.
   * This does not
   * necessarily have to depend on the individual field confidences, but
   * rather can be the result of some meta-level criterion; for instance, if
   * information is extracted from a web page, then the global ranking
   * could be the page relevance according to a search service.
   * Thus, the Confidence
   * object does not calculate this itself, so the only way for the
   * global ranking to be different from the default 0 is to explicitly
   * set it using <code>setGlobalRanking</code>.
   *
   * @return the overall confidence ranking
   */

  public double getGlobalRanking() {
    return overallRanking;
  }


  /**
   * Sets the global confidence ranking field.
   *
   * @param ranking the value to set the overall confidence ranking to
   */

  public void setGlobalRanking(double ranking) {
    overallRanking = ranking;
  }


  /**
   * Similar to the global ranking, the penalty can be another
   * piece of information relevant to the entire Instance rather
   * than a particular field.  The penalty was specifically designed
   * to work with Instance merging as a way to store the scoring
   * penalty associated with merging two instances with conflicting
   * fields.  The default penalty is 0.
   *
   * @return the overall penalty
   */

  public double getPenalty() {
    return penalty;
  }


  /**
   * Sets the penalty field.
   *
   * @param p the value to set the penalty to
   */

  public void setPenalty(double p) {
    penalty = p;
  }


}


