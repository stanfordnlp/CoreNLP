package edu.stanford.nlp.util.concurrent;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.CounterTestBase;

/**
 * 
 * @author Spence Green
 *
 */
public class ConcurrentHashCounterTest extends CounterTestBase {
  public ConcurrentHashCounterTest() {
    super(new ClassicCounter<String>());
    // TODO(spenceg): Fix concurrenthashcounter and reactivate
//    super(new ConcurrentHashCounter<String>());
  }
}
