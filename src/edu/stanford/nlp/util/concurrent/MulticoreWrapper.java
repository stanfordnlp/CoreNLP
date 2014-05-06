package edu.stanford.nlp.util.concurrent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
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

  private long maxSubmitBlockTime = 30000;

  private final int nThreads;
  private int lastSubmittedItemId = 0;
  // Which id was the last id returned.  Only meaningful in the case
  // of a queue where output order matters.
  private int lastReturnedId = -1;
  private final boolean orderResults;

  private final PriorityQueue<QueueItem<O>> outputQueue;
  private final ThreadPoolExecutor threadPool;
//  private final ExecutorCompletionService<Integer> queue;
  private final LinkedBlockingQueue<Integer> idleProcessors;
  private final List<ThreadsafeProcessor<I,O>> processorList;
  private final JobCallback<O> callback;
  // keep track of which jobs are running so we can cancel them if
  // something goes wrong
  private final Map<Integer, Future<Integer>> runningJobs;

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
    outputQueue = new PriorityQueue<QueueItem<O>>(10*nThreads);
    threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(nThreads);
//    queue = new ExecutorCompletionService<Integer>(threadPool);
    processorList = new ArrayList<ThreadsafeProcessor<I,O>>(nThreads);
    idleProcessors = new LinkedBlockingQueue<Integer>();
    runningJobs = new HashMap<Integer, Future<Integer>>();
    callback = new JobCallback<O>() {
      @Override
      public void call(QueueItem<O> result, int processorId) {
        outputQueue.add(result);
        idleProcessors.add(processorId);
      }
    };

    // Sanity check: Fixed thread pool so prevent timeouts.
    // Default should be false
    threadPool.allowCoreThreadTimeOut(false);
    threadPool.prestartAllCoreThreads();

    // Setup the processors, one per thread
    processorList.add(processor);
    idleProcessors.add(0);
    for (int i = 1; i < nThreads; ++i) {
      processorList.add(processor.newInstance());
      idleProcessors.add(i);
    }
  }

  /**
   * Maximum amount of time to block on a call to put() in milliseconds.
   * Default 0, which indicates to never time out.
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
//    if (idleProcessors.peek() == null) blockingGetResult();
    int procId;
    try {
      // TODO: null value if the query fails, so handle that.
      procId = idleProcessors.poll(maxSubmitBlockTime, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return;
    }
    int itemId = lastSubmittedItemId++;
    CallableJob<I,O> job = new CallableJob<I,O>(item, itemId, processorList.get(procId), procId,
        callback);
    Future<Integer> future = threadPool.submit(job);
//    runningJobs.put(itemId, future);
  }

  /**
   * Block until process specified by processorId returns a result.
   */
//  private void blockingGetResult() {
//    try {
//      // Blocking call
//      Future<JobResult<O>> resultFuture;
//      if (maxSubmitBlockTime > 0) {
//        resultFuture = queue.poll(maxSubmitBlockTime, TimeUnit.MILLISECONDS);
//      } else {
//        resultFuture = queue.take();
//      }
//      if (resultFuture != null) {
//        JobResult<O> result = resultFuture.get();
//        QueueItem<O> output = new QueueItem<O>(result.output, result.inputItemId);
//        outputQueue.add(output);
//        idleProcessors.add(result.processorId);
//        runningJobs.remove(result.inputItemId);
//        return;
//      }
//    } catch (InterruptedException e) {
//      threadPool.shutdownNow();
//      throw new RuntimeException(e);
//    } catch (ExecutionException e) {
//      threadPool.shutdownNow();
//      throw new RuntimeException(e);
//    }
//
//    // oops, timed out or hit other error
//    // first, remove everything from the queue
//    // then put null entries into the output queue and hope the
//    // consumer knows how to handle that
//    for (Map.Entry<Integer, Future<JobResult<O>>> entry : runningJobs.entrySet()) {
//      entry.getValue().cancel(true);
//      QueueItem<O> output = new QueueItem<O>(null, entry.getKey());
//      outputQueue.add(output);
//    }
//    runningJobs.clear();
//    for (int i = 0; i < nThreads; ++i) {
//      try {
//        queue.take();
//        idleProcessors.add(i);
//      } catch (InterruptedException e) {
//        throw new RuntimeException(e);
//      }
//    }
//  }

  /**
   * Blocks until all active processes finish.
   */
  public void join() {
    // Make blocking calls to the last processes that are running
    if ( ! threadPool.isShutdown()) {
      int numActiveThreads = nThreads - idleProcessors.size();
      while(numActiveThreads > 0) {
        try {
          idleProcessors.take();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        --numActiveThreads;
      }
      threadPool.shutdown();
    }
  }

  /**
   * Indicates whether a not a new result is available.
   *
   * @return true if a new result is available, false otherwise.
   */
  public boolean peek() {
    if (outputQueue.isEmpty()) {
      return false;
    } else {
      final int nextId = outputQueue.peek().id;
      return orderResults ? nextId == lastReturnedId + 1 : true;
    }
  }

  /**
   * Returns the next available result.
   *
   * @return the next completed result, or null if no result is available
   */
  public O poll() {
    if (!peek()) return null;
    lastReturnedId++;
    QueueItem<O> result = outputQueue.poll();
    return result.item;
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
    public Integer call() throws Exception {
      O result = processor.process(item);
      QueueItem<O> output = new QueueItem<O>(result, itemId);
      callback.call(output, processorId);
      return itemId;
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
