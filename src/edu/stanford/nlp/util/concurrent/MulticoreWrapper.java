package edu.stanford.nlp.util.concurrent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
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

  private long maxSubmitBlockTime = 0;

  private final int nThreads;
  private int lastSubmittedItemId = 0;
  // Which id was the last id returned.  Only meaningful in the case
  // of a queue where output order matters.
  private int lastReturnedId = -1;
  private final boolean orderResults;

  private final PriorityBlockingQueue<QueueItem<O>> outputQueue;
  private final ThreadPoolExecutor threadPool;
  private final ExecutorCompletionService<JobResult<O>> queue;
  private final Queue<Integer> idleProcessors;
  private final List<ThreadsafeProcessor<I,O>> processorList;
  // keep track of which jobs are running so we can cancel them if
  // something goes wrong
  private final Map<Integer, Future<JobResult<O>>> runningJobs;

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
    outputQueue = new PriorityBlockingQueue<QueueItem<O>>(10*nThreads);
    threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(nThreads);
    queue = new ExecutorCompletionService<JobResult<O>>(threadPool);
    processorList = new ArrayList<ThreadsafeProcessor<I,O>>(nThreads);
    idleProcessors = new ConcurrentLinkedQueue<Integer>();
    runningJobs = new HashMap<Integer, Future<JobResult<O>>>();

    // Sanity check: Fixed thread pool so prevent timeouts.
    // Default should be false
    threadPool.allowCoreThreadTimeOut(false);

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
   */
  public synchronized void put(I item) {
    if (idleProcessors.peek() == null) blockingGetResult();
    int procId = idleProcessors.poll();
    int itemId = lastSubmittedItemId++;
    CallableJob<I,O> job = new CallableJob<I,O>(item, itemId, processorList.get(procId), procId);
    Future<JobResult<O>> future = queue.submit(job);
    runningJobs.put(itemId, future);
  }

  /**
   * Block until process specified by processorId returns a result.
   */
  private void blockingGetResult() {
    try {
      // Blocking call
      Future<JobResult<O>> resultFuture;
      if (maxSubmitBlockTime > 0) {
        resultFuture = queue.poll(maxSubmitBlockTime, TimeUnit.MILLISECONDS);
      } else {
        resultFuture = queue.take();
      }
      if (resultFuture != null) {
        JobResult<O> result = resultFuture.get();
        QueueItem<O> output = new QueueItem<O>(result.output, result.inputItemId);
        outputQueue.add(output);
        idleProcessors.add(result.processorId);
        runningJobs.remove(result.inputItemId);
        return;
      }
    } catch (InterruptedException e) {
      threadPool.shutdownNow();
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      threadPool.shutdownNow();
      throw new RuntimeException(e);
    }

    // oops, timed out or hit other error
    // first, remove everything from the queue
    // then put null entries into the output queue and hope the
    // consumer knows how to handle that
    for (Map.Entry<Integer, Future<JobResult<O>>> entry : runningJobs.entrySet()) {
      entry.getValue().cancel(true);
      QueueItem<O> output = new QueueItem<O>(null, entry.getKey());
      outputQueue.add(output);
    }
    runningJobs.clear();
    for (int i = 0; i < nThreads; ++i) {
      try {
        Future<JobResult<O>> result = queue.take();
        idleProcessors.add(i);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Blocks until all active processes finish.
   */
  public void join() {
    // Make blocking calls to the last processes that are running
    if ( ! threadPool.isShutdown()) {
      while(idleProcessors.size() != nThreads) {
        blockingGetResult();
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
   * Internal class for the result of a CallableJob.
   *
   * @author Spence Green
   *
   * @param <O>
   */
  private static class JobResult<O> {
    public final O output;
    public final int inputItemId;
    public final int processorId;
    public JobResult(O result, int inputItemId, int processorId) {
      this.output = result;
      this.inputItemId = inputItemId;
      this.processorId = processorId;
    }
  }

  /**
   * Internal class for adding a job to the thread pool.
   *
   * @author Spence Green
   *
   * @param <I>
   * @param <O>
   */
  private static class CallableJob<I,O> implements Callable<JobResult<O>> {
    private final I item;
    private final int itemId;
    private final ThreadsafeProcessor<I,O> processor;
    private final int processorId;

    public CallableJob(I item, int itemId, ThreadsafeProcessor<I,O> processor, int processorId) {
      this.item = item;
      this.itemId = itemId;
      this.processor = processor;
      this.processorId = processorId;
    }

    @Override
    public JobResult<O> call() throws Exception {
      return new JobResult<O>(processor.process(item), itemId, processorId);
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
