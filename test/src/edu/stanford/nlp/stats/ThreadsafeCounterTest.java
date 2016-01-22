package edu.stanford.nlp.stats;

/**
 * 
 * @author Spence Green
 *
 */
public class ThreadsafeCounterTest extends CounterTestBase {
  public ThreadsafeCounterTest() {
    super(new ThreadsafeCounter<String>());
  }
}
