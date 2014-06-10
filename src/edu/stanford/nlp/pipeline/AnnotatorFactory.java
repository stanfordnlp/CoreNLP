package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.Factory;

import java.util.Properties;

/**
 * Creates annotators
 * @author Mihai
 */
public abstract class AnnotatorFactory implements Factory<Annotator> {
  protected final Properties properties;

  protected AnnotatorFactory(Properties properties) {
    // let's copy the properties, just in case somebody messes with this object later
    this.properties = new Properties();
    for(Object key: properties.keySet()) {
      this.properties.setProperty((String) key, properties.getProperty((String) key));
    }
  }

  /**
   * Creates and returns an annotator given the local properties
   * @return A new instance of the type T
   */
  @Override
  public abstract Annotator create();

  /**
   * Creates the annotator's signature given the current properties
   * We use to understand if the user wants to recreate
   * the same annotator type but with different parameters.
   */
  public abstract String signature();
}
