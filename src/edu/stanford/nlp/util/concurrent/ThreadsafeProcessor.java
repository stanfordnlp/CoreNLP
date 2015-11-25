package edu.stanford.nlp.util.concurrent;

/**
 * Objects that wish to use MulticoreWrapper for multicore support must implement
 * this interface. Objects that implement this interface should, of course, be threadsafe.
 * 
 * @author Spence Green
 *
 * @param <I> input type
 * @param <O> output type
 */
public interface ThreadsafeProcessor<I,O> {

  /**
   * Set the input item that will be processed when a thread is allocated to
   * this processor.
   * 
   * @param input the object to be processed
   * @return the result of the processing
   */
  public O process(I input);
  
  /**
   * Return a new threadsafe instance.
   */
  public ThreadsafeProcessor<I,O> newInstance();
}
