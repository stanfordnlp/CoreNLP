package edu.stanford.nlp.util.concurrent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * Provides convenient multicore processing for threadsafe objects. Objects that can
 * be wrapped by MulticoreWrapper must implement the ThreadsafeProcessor interface.
 *
 * See edu.stanford.nlp.util.concurrent.MulticoreWrapperTest and
 * edu.stanford.nlp.tagger.maxent.documentation.MulticoreWrapperDemo for examples of use.
 *
 * TODO(spenceg): Handle exceptions gracefully in the queue.
 * TODO(spenceg): This code does not support multiple consumers, i.e., multi-threaded calls
 * to peek() and poll().
 *
 * @author Spence Green
 *
 * @param <I> input type
 * @param <O> output type
 */
public class MulticoreWrapper<I,O> {

  // Default: never time out
  private long maxSubmitBlockTime = -1;

  private final int nThreads;
  private int submittedItemCounter = 0;
  // Which id was the last id returned.  Only meaningful in the case
  // of a queue where output order matters.
  private int returnedItemCounter = -1;
  private final boolean orderResults;

  private final Map<Integer,O> outputQueue;
  private final ThreadPoolExecutor threadPool;
//  private final ExecutorCompletionService<Integer> queue;
  private final BlockingQueue<Integer> idleProcessors;
  private final List<ThreadsafeProcessor<I,O>> processorList;
  private final JobCallback<O> callback;

  /**
   * Constructor.
   *
   * @param nThreads If less than or equal to 0, then automatically determine the number
   *                    of threads. Otherwise, the size of the underlying threadpool.
   * @param processor
   */
  public MulticoreWrapper(int nThreads, ThreadsafeProcessor<I,O> processor) {
    this(nThreads, processor, true);
  }

  /**
   * Constructor.
   *
   * @param numThreads -- if less than or equal to 0, then automatically determine the number
   *                    of threads. Otherwise, the size of the underlying threadpool.
   * @param processor
   * @param orderResults -- If true, return results in the order submitted. Otherwise, return results
   *                        as they become available.
   */
  public MulticoreWrapper(int numThreads, ThreadsafeProcessor<I,O> processor, boolean orderResults) {
    nThreads = numThreads <= 0 ? Runtime.getRuntime().availableProcessors() : numThreads;
    this.orderResults = orderResults;
    outputQueue = new ConcurrentHashMap<Integer,O>(2*nThreads);
    threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(nThreads);
//    queue = new ExecutorCompletionService<Integer>(threadPool);
    idleProcessors = new ArrayBlockingQueue<Integer>(nThreads, false);
    callback = new JobCallback<O>() {
      @Override
      public void call(QueueItem<O> result, int processorId) {
        outputQueue.put(result.id, result.item);
        idleProcessors.add(processorId);
      }
    };

    // Sanity check: Fixed thread pool so prevent timeouts.
    // Default should be false
    threadPool.allowCoreThreadTimeOut(false);
    threadPool.prestartAllCoreThreads();

    // Setup the processors, one per thread
    List<ThreadsafeProcessor<I,O>> procList = new ArrayList<ThreadsafeProcessor<I,O>>(nThreads);
    procList.add(processor);
    idleProcessors.add(0);
    for (int i = 1; i < nThreads; ++i) {
      procList.add(processor.newInstance());
      idleProcessors.add(i);
    }
    processorList = Collections.unmodifiableList(procList);
  }

  /**
   * Maximum amount of time to block on a call to put() in milliseconds.
   * Default is to never time out.
   *
   * @param t
   */
  public void setMaxBlockTime(long t) { maxSubmitBlockTime = t; }

  /**
   * Return status information about the underlying threadpool.
   */
  @Override
  public String toString() {
    return String.format("active: %d/%d  submitted: %d  completed: %d  input_q: %d  output_q: %d  idle_q: %d",
        threadPool.getActiveCount(),
        threadPool.getPoolSize(),
        threadPool.getTaskCount(),
        threadPool.getCompletedTaskCount(),
        threadPool.getQueue().size(),
        outputQueue.size(),
        idleProcessors.size());
  }

  /**
   * Allocate instance to a process and return. This call blocks until item
   * can be assigned to a thread.
   *
   * @param item Input to a Processor
   * @throws RejectedExecutionException -- A RuntimeException when there is an
   * uncaught exception in the queue. Resolution is for the calling class to shutdown
   * the wrapper and create a new threadpool.
   * 
   */
  public synchronized void put(I item) throws RejectedExecutionException {
    Integer procId;
    try {
      procId = maxSubmitBlockTime < 0 ? idleProcessors.take() :
        idleProcessors.poll(maxSubmitBlockTime, TimeUnit.MILLISECONDS);
      if (procId == null) {
        throw new RejectedExecutionException("Couldn't submit item to threadpool: " + item.toString());
      }
    
    } catch (InterruptedException e) {
      throw new RejectedExecutionException("Exception in threadpool: " + e.toString());
    }
    final int itemId = submittedItemCounter++;
    CallableJob<I,O> job = new CallableJob<I,O>(item, itemId, processorList.get(procId), procId,
        callback);
    threadPool.submit(job);
  }
  
  /**
   * Wait for all threads to finish, then destroy the pool of
   * worker threads so that the main thread can shutdown.
   */
  public void join() {
    join(true);
  }

  /**
   * Wait for all threads to finish.
   * 
   * @param destroyThreadpool -- if true, then destroy the worker threads
   * so that the main thread can shutdown.
   */
  public void join(boolean destroyThreadpool) {
    // Make blocking calls to the last processes that are running
    if ( ! threadPool.isShutdown()) {
      try {
        for (int i = nThreads; i > 0; --i) {
          idleProcessors.take();
        }
        if (destroyThreadpool) {
          threadPool.shutdown();
          // Sanity check. The threadpool should be done after iterating over
          // the processors.
          threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } else {
          // Repopulate the list of processors
          for (int i = 0; i < nThreads; ++i) {
            idleProcessors.put(i);
          }
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Indicates whether or not a new result is available.
   *
   * @return true if a new result is available, false otherwise.
   */
  public boolean peek() {
    if (outputQueue.isEmpty()) {
      return false;
    } else {
       return orderResults ? outputQueue.containsKey(returnedItemCounter + 1) : true;
    }
  }

  /**
   * Returns the next available result.
   *
   * @return the next completed result, or null if no result is available
   */
  public O poll() {
    if (!peek()) return null;
    returnedItemCounter++;
    int itemIndex = orderResults ? returnedItemCounter : 
      outputQueue.keySet().iterator().next();
    return outputQueue.remove(itemIndex);
  }
  
  /**
   * Internal class for a result when a CallableJob completes.
   * 
   * @author Spence Green
   *
   * @param <O>
   */
  private static interface JobCallback<O> {
    public void call(QueueItem<O> result, int processorId);
  }
  
  /**
   * Internal class for adding a job to the thread pool.
   *
   * @author Spence Green
   *
   * @param <I>
   * @param <O>
   */
  private static class CallableJob<I,O> implements Callable<Integer> {
    private final I item;
    private final int itemId;
    private final ThreadsafeProcessor<I,O> processor;
    private final int processorId;
    private final JobCallback<O> callback;
    
    public CallableJob(I item, int itemId, ThreadsafeProcessor<I,O> processor, int processorId, 
        JobCallback<O> callback) {
      this.item = item;
      this.itemId = itemId;
      this.processor = processor;
      this.processorId = processorId;
      this.callback = callback;
    }

    @Override
    public Integer call() {
      try {
        O result = processor.process(item);
        QueueItem<O> output = new QueueItem<O>(result, itemId);
        callback.call(output, processorId);
        return itemId;
      
      } catch (Exception e) {
        e.printStackTrace();
        // Hope that the consumer knows how to handle null!
        QueueItem<O> output = new QueueItem<O>(null, itemId);
        callback.call(output, processorId);
        return itemId;
      }
    }
  }

  /**
   * Internal class for storing results of type O in a min queue.
   *
   * @author Spence Green
   *
   * @param <O>
   */
  private static class QueueItem<O> implements Comparable<QueueItem<O>> {
    public final int id;
    public final O item;

    public QueueItem(O item, int id) {
      this.item = item;
      this.id = id;
    }

    @Override
    public int compareTo(QueueItem<O> other) {
      return this.id - other.id;
    }

    @SuppressWarnings("unchecked")
    public boolean equals(Object other) {
      if (other == this) return true;
      if ( ! (other instanceof QueueItem)) return false;
      QueueItem<O> otherQueue = (QueueItem<O>) other;
      return this.id == otherQueue.id;
    }

    public int hashCode() {
      return id;
    }
  }
}
