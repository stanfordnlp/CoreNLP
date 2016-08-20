package edu.stanford.nlp.pipeline;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Map;

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


  /** The last timestamp when we checked the cache for stale SoftReference objects */
  private static long lastSweepMillis = 0L;
  /**
   * A cache of annotators ever created in this Java runtime.
   * This serves to ensure that different pools don't re-create the exact same annotator,
   * while also being stored as {@link SoftReference}s so that we don't explode memory
   * unnecessarily.
   * The key of this cache is the pair (annotator_name, factory_signature); the value
   * is a softreference to the annotator.
   * {@link AnnotatorPool#lastSweepMillis} keeps track of the last time we cleaned out stale
   * soft references from the cache.
   */
  private static final Map<Pair<String, String>, SoftReference<Annotator>> cache = Generics.newHashMap();

  private final Map<String, Annotator> annotators;
  private final Map<String, AnnotatorFactory> factories;


  /**
   * Create an empty AnnotatorPool.
   */
  public AnnotatorPool() {
    this.annotators = Generics.newHashMap();
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
    if (this.factories.containsKey(name)) {
      AnnotatorFactory oldFactory = this.factories.get(name);
      String oldSig = oldFactory.signature();
      String newSig = factory.signature();
      if(! oldSig.equals(newSig)) {
        // the new annotator uses different properties so we need to update!
        // TODO: this printout should be logged instead of going to stderr. we need to standardize logging
        // log.info("Replacing old annotator \"" + name + "\" with signature ["
        //         + oldSig + "] with new annotator with signature [" + newSig + "]");
        this.factories.put(name, factory);
        newAnnotator = true;

        // delete the existing annotator; we'll create one with the new props on demand
        // removing the annotator like this will not affect any
        // existing pipelines which use the old annotator, but if
        // those are all gone, then the old annotator will be garbage
        // collected and memory will be freed up
        annotators.remove(name);
      }
      // nothing to do if an annotator with same name and signature already exists
    } else {
      this.factories.put(name, factory);
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
    if (!this.annotators.containsKey(name)) {
      // Get the factory
      AnnotatorFactory factory = this.factories.get(name);
      if (factory == null) {
        throw new IllegalArgumentException("No annotator named " + name);
      }
      Annotator annotator;
      // Check the cache
      Pair<String, String> key = Pair.makePair(name, factory.signature());
      SoftReference<Annotator> value;
      synchronized (cache) {
        if ((value = cache.get(key)) != null) {
          annotator = value.get();
          if (annotator == null) {
            annotator = factory.create();
            cache.put(key, new SoftReference<>(annotator));
          }
        } else {
          annotator = factory.create();
          cache.put(key, new SoftReference<>(annotator));
        }
      }
      // Register the annotator
      assert annotator != null;
      this.annotators.put(name, annotator);
    }

    // Clean garbage collected annotators from the cache
    if (lastSweepMillis < System.currentTimeMillis() - (1000 * 60 * 15)) {  // 15 minutes
      synchronized (cache) {
        int numRemoved = 0;
        Iterator<Map.Entry<Pair<String, String>, SoftReference<Annotator>>> iter = cache.entrySet().iterator();
        while (iter.hasNext()) {
          if (iter.next().getValue().get() == null) {
            iter.remove();
            numRemoved += 1;
          }
        }
        if (numRemoved > 0) {
          log.info("Removed " + numRemoved + " evicted annotators from the AnnotatorPool cache");
        }
      }
      lastSweepMillis = System.currentTimeMillis();
    }

    // Return
    return this.annotators.get(name);
  }

}
