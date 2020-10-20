package edu.stanford.nlp.pipeline;

import java.util.*;
import java.util.function.Supplier;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Lazy;
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
  private static final Redwood.RedwoodChannels log = Redwood.channels(AnnotatorPool.class);

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
      if (!annotator.isCache()) {
        log.warn("Cached annotator will never GC -- this can cause OOM exceptions!");
      }
      this.signature = signature;
      this.annotator = annotator;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CachedAnnotator that = (CachedAnnotator) o;
      return Objects.equals(signature, that.signature) && (Objects.equals(annotator, that.annotator));
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
   * The set of annotators that we have cached, possibly with garbage collected annotator instances.
   * This is a map from annotator name to cached annotator instances.
   */
  private final Map<String, CachedAnnotator> cachedAnnotators;


  /**
   * Create an empty AnnotatorPool.
   */
  public AnnotatorPool() {
    this.cachedAnnotators = Generics.newHashMap();
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
   *                     This should be an instance of {@link Lazy#cache(Supplier)}, if we want
   *                     the annotator pool to behave as a cache (i.e., evict old annotators
   *                     when the GC requires it).
   *
   * @return true if a new annotator was created; false if we reuse an existing one
   */
  public boolean register(String name, Properties props, Lazy<Annotator> annotator) {
    boolean newAnnotator = false;
    String newSig = PropertiesUtils.getSignature(name, props);
    synchronized (this.cachedAnnotators) {
      CachedAnnotator oldAnnotator = this.cachedAnnotators.get(name);
      if (oldAnnotator == null || !Objects.equals(oldAnnotator.signature, newSig)) {
        // the new annotator uses different properties so we need to update!
        if (oldAnnotator != null) {
          // Try to get it from the global cache
          log.debug("Replacing old annotator \"" + name + "\" with signature ["
              + oldAnnotator.signature + "] with new annotator with signature [" + newSig + "]");
        }
        // Add the new annotator
        this.cachedAnnotators.put(name, new CachedAnnotator(newSig, annotator));
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
    synchronized (this.cachedAnnotators) {
      for (Map.Entry<String, CachedAnnotator> entry : new HashSet<>(this.cachedAnnotators.entrySet())) {
        // Unmount the annotator
        Optional.ofNullable(entry.getValue()).flatMap(ann -> Optional.ofNullable(ann.annotator.getIfDefined())).ifPresent(Annotator::unmount);
        // Remove the annotator
        this.cachedAnnotators.remove(entry.getKey());
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
    CachedAnnotator factory =  this.cachedAnnotators.get(name);
    if (factory != null) {
      return factory.annotator.get();
    } else {
      throw new IllegalArgumentException("No annotator named " + name);
    }
  }


  /**
   * A global singleton annotator pool, so that we can cache globally on a JVM instance.
   */
  public static final AnnotatorPool SINGLETON = new AnnotatorPool();

}
