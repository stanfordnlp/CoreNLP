package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Map;

import edu.stanford.nlp.util.Generics;

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
      AnnotatorFactory factory = this.factories.get(name);
      if (factory == null) {
        throw new IllegalArgumentException("No annotator named " + name);
      }
      this.annotators.put(name, factory.create());
    }
    return this.annotators.get(name);
  }

}
