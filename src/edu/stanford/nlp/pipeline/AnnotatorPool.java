package edu.stanford.nlp.pipeline;
import java.lang.ref.SoftReference;
import java.util.*;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Lazy;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
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
   * A cached annotator, including the signature it should cache on.
   */
  private static class CachedAnnotator {
    /** The signature of the annotator. */
    public final String signature;
    /** The cached annotator. */
    public final Lazy<Annotator> annotator;

    /**
     * The straightforward constructor.
     */
    private CachedAnnotator(String signature, Lazy<Annotator> annotator) {
      this.signature = signature;
      this.annotator = annotator;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CachedAnnotator that = (CachedAnnotator) o;
      return signature != null ? signature.equals(that.signature) : that.signature == null && (annotator != null ? annotator.equals(that.annotator) : that.annotator == null);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
      int result = signature != null ? signature.hashCode() : 0;
      result = 31 * result + (annotator != null ? annotator.hashCode() : 0);
      return result;
    }
  }


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
  private static final Timer gc = new Timer(true) {{
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
  private final Map<String, CachedAnnotator> factories;


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
   * @param name       The name to be associated with the Annotator.
   * @param props The properties we are using to create the annotator
   * @param annotator    A factory that creates an instance of the desired Annotator.
   * @return true if a new annotator was created; false if we reuse an existing one
   */
  public boolean register(String name, Properties props, Lazy<Annotator> annotator) {
    boolean newAnnotator = false;
    synchronized (this.factories) {
      CachedAnnotator oldAnnotator = this.factories.get(name);
      String newSig = PropertiesUtils.getSignature(name, props);
      if (oldAnnotator == null || !Objects.equals(oldAnnotator.signature, newSig)) {
        // the new annotator uses different properties so we need to update!
        if (oldAnnotator != null) {
          log.info("Replacing old annotator \"" + name + "\" with signature ["
              + oldAnnotator.signature + "] with new annotator with signature [" + newSig + "]");
        }
        // Add the new annotator
        this.factories.put(name, new CachedAnnotator(newSig, annotator));
        // Unmount the old annotator
        Optional.ofNullable(oldAnnotator).flatMap(ann -> Optional.ofNullable(ann.annotator.getIfDefined())).ifPresent(Annotator::unmount);
        // Register that we added an annotator
        newAnnotator = true;
      }
      // nothing to do if an annotator with same name and signature already exists
    }
    return newAnnotator;
  }


  /**
   * Clear this pool, and unmount all the annotators mounted on it.
   */
  public synchronized void clear() {
    synchronized (this.factories) {
      for (Map.Entry<String, CachedAnnotator> entry : new HashSet<>(this.factories.entrySet())) {
        // Unmount the annotator
        Optional.ofNullable(entry.getValue()).flatMap(ann -> Optional.ofNullable(ann.annotator.getIfDefined())).ifPresent(Annotator::unmount);
        // Remove the annotator
        this.factories.remove(entry.getKey());
      }
    }
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
    CachedAnnotator factory;
    synchronized (factories) {
      factory = this.factories.get(name);
    }
    if (factory == null) {
      throw new IllegalArgumentException("No annotator named " + name);
    }
    Pair<String, String> key = Pair.makePair(name, factory.signature);
    Optional<Annotator> annotator;
    synchronized (cache) {
      annotator = Optional.ofNullable(cache.get(key)).flatMap(x -> Optional.ofNullable(x.get()));
    }
    if (!annotator.isPresent()) {
      annotator = Optional.of(factory.annotator.get());
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
