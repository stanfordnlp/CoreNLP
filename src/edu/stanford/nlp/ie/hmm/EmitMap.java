package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.stats.ClassicCounter;
import java.io.PrintWriter;
import java.text.NumberFormat;

/**
 * Interface to model a states emission distribution. Maps strings to the
 * probability that they are emitted.
 *
 * @author Jim McFadden
 */
public interface EmitMap {

  public double get(String s);

  public void set(String s, double d);

  public ClassicCounter getCounter();

  /**
   * Tune any relevant internal parameters to better represent the given
   * expected emissions (word -> expected # times emitted). The parent HMM
   * is provided in case tuning parameters requires access to additional
   * information such as the vocabulary or other states.
   *
   * @return max change of any internal parameter as a result of tuning. This is
   *         used to decide when to stop re-estimating.
   */
  public double tuneParameters(ClassicCounter<String> expectedEmissions, HMM hmm);


  /**
   * Print all or some emissions of emitMap.  Note that this
   * prints P(emission|seen,state), and so, for example,  you'd need
   * to multiply it by P(seen) to get real emission probability for
   * an UnseenEmitMap.
   */
  public void printEmissions(PrintWriter pw, boolean justCommon);

  public void printUnseenEmissions(PrintWriter pw, NumberFormat nf);

}
