package edu.stanford.nlp.process;


import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.Function;


/**
 * Processor that takes a Function and applies it to every element in
 * the input Document. This is useful when you want to transform
 * each element in an isolated way.
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 * @author Christopher Manning
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels 
 * @param <F> The type of the features
 */
public class FunctionProcessor<IN,OUT, L, F> extends AbstractListProcessor<IN,OUT, L, F> {

  /**
   * Function used to transform each document element during processing.
   */
  protected Function<IN,OUT> func;

  /**
   * Does nothing but allows subclasses with empty constructor.
   */
  protected FunctionProcessor() {
  }

  /**
   * Instantiates a new FunctionProcessor for a given Function
   */
  public FunctionProcessor(Function<IN,OUT> a) {
    func = a;
  }

  /**
   * Converts a Document to a different Document, by transforming
   * or filtering the elements in <tt>in</tt> using the <tt>Function</tt>
   * given in the constructor.
   */
  public List<OUT> process(List<? extends IN> in) {
    List<OUT> out = new ArrayList<OUT>(in.size()); // copies the meta data from in
    for (IN obj : in) {
      out.add(func.apply(obj));
    }
    return (out);
  }

}
