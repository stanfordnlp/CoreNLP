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
      // handle special case of tokenize and ssplit
      String otherSig = "";
      if (name.equals("ssplit")) {
        otherSig = PropertiesUtils.getSignature("tokenize", props);
      } else if (name.equals("tokenize")) {
        otherSig = PropertiesUtils.getSignature("ssplit", props);
      }
      newSig += otherSig;
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
    CachedAnnotator factory =  this.factories.get(name);
    if (factory != null) {
      return factory.annotator.get();
    } else {
      throw new IllegalArgumentException("No annotator named " + name);
    }
  }

}
