package edu.stanford.nlp.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Provides convenient multicore processing for threadsafe objects. Objects that can
 * be wrapped by MulticoreWrapper must implement the ThreadsafeProcessor interface.
 *
 * @author Spence Green
 *
 * @param <I> input type
 * @param <O> output type
 */
public class MulticoreWrapper<I,O> {

  private final int nThreads;
  private int lastSubmittedItemId = 0;
  private int lastReturnedId = -1;
  private final boolean orderResults;
  
  private final PriorityBlockingQueue<QueueItem<O>> outputQueue;
  private final ExecutorService threadPool;
  private final ExecutorCompletionService<JobResult<O>> queue;
  private final Queue<Integer> idleProcessors;
  private final List<ThreadsafeProcessor<I,O>> processorList;

  /**
   * Constructor.
   * 
   * @param nThreads -- if less than or equal to 0, then automatically determine the number
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
    threadPool = Executors.newFixedThreadPool(nThreads);
    queue = new ExecutorCompletionService<JobResult<O>>(threadPool);
    processorList = new ArrayList<ThreadsafeProcessor<I,O>>(nThreads);
    idleProcessors = new ConcurrentLinkedQueue<Integer>();

    // Setup the processors, one per thread
    processorList.add(processor);
    idleProcessors.add(0);
    for (int i = 1; i < nThreads; ++i) {
      processorList.add(processor.newInstance());
      idleProcessors.add(i);
    }
  }

  /**
   * Allocate instance to a process and return. This call blocks until I
   * can be assigned to a thread.
   *
   * @param item Input to a Processor
   */
  public void put(I item) {
    while(true) {
      if (idleProcessors.peek() == null) blockingGetResult();
      Integer id = idleProcessors.poll();
      if (id != null) {
        int procId = id;
        CallableJob<I,O> job = new CallableJob<I,O>(item, lastSubmittedItemId++, processorList.get(procId), procId);
        queue.submit(job);
        return;
      }
    }
  }

  /**
   * Block until process specified by processorId returns a result.
   */
  private void blockingGetResult() {
    try {
      // Blocking call
      JobResult<O> result = queue.take().get();
      QueueItem<O> output = new QueueItem<O>(result.output, result.inputItemId);
      outputQueue.add(output);
      idleProcessors.add(result.processorId);
      return;

    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }

    // Error case
    threadPool.shutdown();
    throw new RuntimeException("Unable to submit to job to thread pool");
  }

  /**
   * Waits for all active processes to finish, then returns true only if all
   * results have been returned via calls to next().
   *
   * @return True on successful shutdown, false otherwise.
   */
  public boolean join() {
    // Make blocking calls to the last processes that are running
    while(idleProcessors.size() != nThreads) {
      blockingGetResult();
    }
    threadPool.shutdown();
    return lastSubmittedItemId-1 == lastReturnedId;
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
   * @return
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
