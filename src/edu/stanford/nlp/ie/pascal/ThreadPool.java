package edu.stanford.nlp.ie.pascal;

import java.util.LinkedList;

/**
 * Holds threads for phase 2 system.
 *
 * @author Jamie Nicolson
 */
public class ThreadPool {

  private LinkedList jobQueue = new LinkedList();
  private WorkerThread[] threads;

  private static class WorkerThread extends Thread {
    private LinkedList jobQueue;
    private boolean keepGoing = true;

    public WorkerThread(LinkedList jobQueue) {
      this.jobQueue = jobQueue;
    }

    public synchronized void safeStop() {
      keepGoing = false;
      this.interrupt();
    }

    private synchronized boolean keepGoing() {
      return keepGoing;
    }

    @Override
    public void run() {
      while (keepGoing()) {
        Job j;
        synchronized (jobQueue) {
          while (jobQueue.isEmpty() && keepGoing()) {
            try {
              jobQueue.wait();
            } catch (InterruptedException e) {
            }
          }
          if (!keepGoing()) {
            break;
          }
          j = (Job) jobQueue.removeFirst();
          jobQueue.notifyAll(); // so the parent can see when it's empty
        }
        j.run();
        j = null;
      }
    }
  }


  public ThreadPool(int numThreads) {
    threads = new WorkerThread[numThreads];
    for (int i = 0; i < numThreads; ++i) {
      threads[i] = new WorkerThread(jobQueue);
      threads[i].start();
    }
  }

  /**
   * Inserts a Job into the work queue.
   */
  public void insertJob(Job j) {
    synchronized (jobQueue) {
      jobQueue.addLast(j);
      jobQueue.notifyAll();
    }
  }

  /**
   * Returns when there are no more jobs in the queue.
   */
  public void waitForEmpty() {
    synchronized (jobQueue) {
      while (!jobQueue.isEmpty()) {
        try {
          jobQueue.wait();
        } catch (InterruptedException e) {
        }
      }
    }
  }

  /**
   * Waits for all threads to stop.
   */
  public void stop() {
    for (int i = 0; i < threads.length; ++i) {
      threads[i].safeStop();
    }
    for (int i = 0; i < threads.length; ++i) {
      boolean joined = false;
      while (!joined) {
        try {
          threads[i].join();
          joined = true;
        } catch (InterruptedException e) {
        }
      }
    }
  }

  public void stopWhenEmpty() {
    waitForEmpty();
    stop();
  }
}
