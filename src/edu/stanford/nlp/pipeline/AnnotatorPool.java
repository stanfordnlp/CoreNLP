package edu.stanford.nlp.pipeline;
import java.lang.ref.SoftReference;
import java.util.*;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * An object for keeping track of Annotators. Typical use is to allow multiple
 * pipelines to share any Annotators in common.
 *
 * For example, if multiple pipelines exist, and they both need a
 * ParserAnnotator, it would be bad to load two such Annotators into memory.
 * Instead, an AnnotatorPool will only create one Annotator and allow both
 * pipelines to share it.
 *
 * @author bethard
 */
public class AnnotatorPool  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AnnotatorPool.class);

  /**
   * The cache of Annotators already created at some point.
   * This is keyed on a pair: (annotator_name, signature), and returns a soft reference to an annotator.
   */
  private static final Map<Pair<String, String>, SoftReference<Annotator>> cache = Generics.newHashMap();

  /**
   * A set of annotators that we want to keep hard references to.
   * These are cleaned up in {@link AnnotatorPool#gc}'s timer task.
   */
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")  // Updated with Iterator#remove()
  private static final IdentityHashMap<Annotator, Long> annotatorsForcedAlive = new IdentityHashMap<>();


  /** A timer for cleaning up old annotators */
  @SuppressWarnings("unused")  // Unused, but still runs
  private static final Timer gc = new Timer() {{
    scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        int actionsTaken = 0;
        long initialMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        // 1. Allow cleaning up any annotator that hasn't been called in the last 10 minutes
        synchronized (annotatorsForcedAlive) {
          Iterator<Map.Entry<Annotator, Long>> iter = annotatorsForcedAlive.entrySet().iterator();
          while (iter.hasNext()) {
            if (iter.next().getValue() < (System.currentTimeMillis() - 1000 * 60 * 10)) {  // older than 10 minutes old
              actionsTaken += 1;
              iter.remove();
            }
          }
        }
        // 2. Clean up stale keys in the cache
        synchronized (cache) {
          Iterator<Map.Entry<Pair<String, String>, SoftReference<Annotator>>> iter = cache.entrySet().iterator();
          while (iter.hasNext()) {
            Map.Entry<Pair<String, String>, SoftReference<Annotator>> entry = iter.next();
            if (entry.getValue().get() == null) {  // this reference has been garbage collected
              actionsTaken += 1;
              iter.remove();
            }
          }
        }
        // 3. Print stats
        long finalMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        if (actionsTaken > 0) {
          Runtime.getRuntime().gc();
          log.info("Annotator GC run. References cleaned=" + actionsTaken + "; Memory: " + initialMemory + " MB  ->  " + finalMemory + " MB");
          synchronized (cache) {
            log.info("Annotators in cache: " + cache.size());
          }
          synchronized (annotatorsForcedAlive) {
            log.info("Annotators forced alive: " + annotatorsForcedAlive.size());
          }
        }
      }
    }, 0, 30000);
  }};


  /** The set of factories we know about defining how we should create new annotators of each name */
  private final Map<String, AnnotatorFactory> factories;


  /**
   * Create an empty AnnotatorPool.
   */
  public AnnotatorPool() {
    this.factories = Generics.newHashMap();
  }

  /**
   * Register an Annotator that can be created by the pool.
   *
   * Note that factories are used here so that many possible annotators can
   * be defined within the AnnotatorPool, but an Annotator is only created
   * when one is actually needed.
   *
   * @param name    The name to be associated with the Annotator.
   * @param factory A factory that creates an instance of the desired Annotator.
   * @return true if a new annotator was created; false if we reuse an existing one
   */
  public boolean register(String name, AnnotatorFactory factory) {
    boolean newAnnotator = false;
    synchronized (this.factories) {
      if (this.factories.containsKey(name)) {
        AnnotatorFactory oldFactory = this.factories.get(name);
        String oldSig = oldFactory.signature();
        String newSig = factory.signature();
        if (!oldSig.equals(newSig)) {
          // the new annotator uses different properties so we need to update!
          log.info("Replacing old annotator \"" + name + "\" with signature ["
              + oldSig + "] with new annotator with signature [" + newSig + "]");
          this.factories.put(name, factory);
          newAnnotator = true;
        }
        // nothing to do if an annotator with same name and signature already exists
      } else {
        this.factories.put(name, factory);
      }
    }
    return newAnnotator;
  }

  /**
   * Retrieve an Annotator from the pool. If the named Annotator has not yet
   * been requested, it will be created. Otherwise, the existing instance of
   * the Annotator will be returned.
   *
   * @param name The annotator to retrieve from the pool
   * @return The annotator
   * @throws IllegalArgumentException If the annotator cannot be created
   */
  public synchronized Annotator get(String name) {
    AnnotatorFactory factory;
    synchronized (factories) {
      factory = this.factories.get(name);
    }
    if (factory == null) {
      throw new IllegalArgumentException("No annotator named " + name);
    }
    Pair<String, String> key = Pair.makePair(name, factory.signature());
    Optional<Annotator> annotator;
    synchronized (cache) {
      annotator = Optional.ofNullable(cache.get(key)).flatMap(x -> Optional.ofNullable(x.get()));
    }
    if (!annotator.isPresent()) {
      annotator = Optional.of(factory.create());
    }
    synchronized (cache) {
      cache.put(key, new SoftReference<>(annotator.orElse(null)));  // will never be null though
    }

    // Return
    if (annotator.isPresent()) {
      return annotator.get();
    } else {
      throw new IllegalStateException("Logic error in AnnotatorPool#get()");  // should be impossible
    }
  }

}
