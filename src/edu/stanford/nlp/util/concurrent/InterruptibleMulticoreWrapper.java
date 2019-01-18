package edu.stanford.nlp.util.concurrent;

import edu.stanford.nlp.util.RuntimeInterruptedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class InterruptibleMulticoreWrapper<I,O> extends MulticoreWrapper<I,O> {

  private final long timeout;

  /** Create an interruptible thread pool for multithreaded processing.
   *
   * @param numThreads If less than or equal to 0, then automatically determine the number
   *                    of threads. Otherwise, the size of the underlying threadpool.
   * @param processor The processor (factory) for what will be run on data.
   * @param orderResults If true, return results in the order submitted. Otherwise, return results
   *                        as they become available.
   *  @param timeout If {@code timeout} is a positive number, the threadpool attempts to allow jobs to run
   *                 for only this many milliseconds, but it depends on threads being Interruptible to succeed.
   *                 If zero or negative, thread runtime is unlimited.
   */
  public InterruptibleMulticoreWrapper(int numThreads, ThreadsafeProcessor<I,O> processor, boolean orderResults, long timeout) {
    super(numThreads, processor, orderResults);

    this.timeout = timeout;
  }

  @Override
  protected ThreadPoolExecutor buildThreadPool(int nThreads) {
    return new FixedNamedThreadPoolExecutor(nThreads);
  }

  @Override
  protected Integer getProcessor() {
    try {
      return (timeout <= 0) ? idleProcessors.take() : idleProcessors.poll(timeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
  }

  /**
   * Shuts down the thread pool, returns when finished.
   * <p>
   * If {@code timeout} is positive, then {@code join} waits at
   * most {@code timeout} milliseconds for threads to finish.  If
   * any fail to finish in that time, the threadpool is shutdownNow.
   * After that, {@code join} continues to wait for the
   * interrupted threads to finish, so if job do not obey
   * interruptions, they can continue indefinitely regardless of the
   * timeout.
   *
   * @return a list of jobs which had never been started if
   * {@code timeout} was reached, or an empty list if that did not
   * happen.
   */
  public List<I> joinWithTimeout() {
    if (timeout <= 0) {
      join();
      return new ArrayList<>();
    }
    // Make blocking calls to the last processes that are running
    if ( ! threadPool.isShutdown()) {
      try {
        List<I> leftover = null;
        int i;
        for (i = nThreads; i > 0; --i) {
          if (idleProcessors.poll(timeout, TimeUnit.MILLISECONDS) == null) {
            leftover = shutdownNow();
            break;
          }
        }
        // if the poll hit a timeout, retake the remaining processors
        // so join() can guarantee the threads are finished
        if (i > 0) {
          for ( ; i > leftover.size(); --i) {
            idleProcessors.take();
          }
          return leftover;
        } else {
          threadPool.shutdown();
          // Sanity check. The threadpool should be done after iterating over
          // the processors.
          threadPool.awaitTermination(10, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }
    }
    return new ArrayList<>();
  }

  /**
   * Calls shutdownNow on the underlying ThreadPool.  In order for
   * this to be useful, the jobs need to look for their thread being
   * interrupted.  The job the thread is running needs to occasionally
   * check Thread.interrupted() and throw an exception or otherwise
   * clean up.
   */
  private List<I> shutdownNow() {
    List<I> orphans = new ArrayList<>();
    List<Runnable> runnables = threadPool.shutdownNow();
    for (Runnable runnable : runnables) {
      if (!(runnable instanceof NamedTask)) {
        throw new AssertionError("Should have gotten NamedTask");
      }
      @SuppressWarnings("unchecked")
      NamedTask<I, O, ?> task = (NamedTask<I, O, ?>) runnable;
      orphans.add(task.item);
    }
    return orphans;
  }

  /**
   * After a shutdown request, await for the final termination of all
   * threads.  Note that if the threads don't actually obey the
   * interruption, this may take some time.
   */
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return threadPool.awaitTermination(timeout, unit);
  }


  /**
   * Internal class for a FutureTask which happens to know the input
   * it represents.  Useful for if the queue is interrupted with
   * future jobs unallocated.  Since it is always created with
   * CallableJob, we assume that is what it has been created with and
   * extract the input.
   */
  private static class NamedTask<I, O, V> extends FutureTask<V> {
    final I item;

    NamedTask(Callable<V> c) {
      super(c);
      if (!(c instanceof CallableJob)) {
        throw new AssertionError("Should have gotten a CallableJob");
      }
      @SuppressWarnings("unchecked")
      CallableJob<I, O> myCallable = (CallableJob<I, O>) c;
      item = myCallable.item;
    }
  }

  /**
   * Internal class which represents a ThreadPoolExecutor whose future
   * jobs know what their input was.  That way, if the ThreadPool is
   * interrupted, it can return the jobs that were killed.
   * <br>
   * We know this will never be asked to provide new tasks for
   * Runnable, just for Callable, so we throw an exception if asked to
   * provide a new task for a Runnable.
   */
  private static class FixedNamedThreadPoolExecutor<I, O> extends ThreadPoolExecutor {

    FixedNamedThreadPoolExecutor(int nThreads) {
      super(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    @Override
    protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
      return new NamedTask<I, O, V>(c);
    }
    
    @Override
    protected <V> RunnableFuture<V> newTaskFor(Runnable r, V v) {
      throw new UnsupportedOperationException();
    }

  }

}
