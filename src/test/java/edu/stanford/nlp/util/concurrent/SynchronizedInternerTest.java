package edu.stanford.nlp.util.concurrent;

import junit.framework.TestCase;

public class SynchronizedInternerTest extends TestCase {

  private final Object[] objects = {
      "salamander",
      "kitten",
      new String[]{"fred", "george", "sam"},
      Integer.valueOf(5),
      Float.valueOf(5f),
  };
  private final Thread[] threads = new Thread[100];

  public void testGlobal() {
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(new Runnable(){
        @Override
        public void run() {
          for (Object object : objects) {
            Object interned = SynchronizedInterner.globalIntern(object);
            Thread.yield();
            if (interned != object)
              throw new AssertionError("Interning failed for " + object);
          }
        }
      });
    }
    for (Thread thread : threads) {
      thread.start();
    }
    for (Thread thread : threads) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

}
