package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.Factory;

import java.util.Properties;

/**
 * A Factory for creating a certain type of Annotator.
 *
 * @author Mihai Surdeanu
 */
public abstract class AnnotatorFactory implements Factory<Annotator> {

  private static final long serialVersionUID = -1554647325549869340L;

  protected final Properties properties;
  private final AnnotatorImplementations implementations;

  protected AnnotatorFactory(Properties properties, AnnotatorImplementations implementations) {
    // Let's copy the properties, just in case somebody messes with this object later.
    // By using stringPropertyNames(), we also pick up any defaults the Properties has.
    this.properties = new Properties();
    for (String key : properties.stringPropertyNames()) {
      this.properties.setProperty(key, properties.getProperty(key));
    }
    this.implementations = implementations;
  }

  /**
   * Creates and returns an Annotator given the local properties.
   *
   * @return A new instance of the type T
   */
  @Override
  public abstract Annotator create();

  /**
   * Creates the annotator's signature given the current properties.
   * We use this to understand if the user wants to recreate
   * the same annotator type but with different parameters.
   */
  public String signature() {
    return this.implementations.getClass().getName() + ':' + additionalSignature();
  }

  protected abstract String additionalSignature();

  /**
   * Can be used to get a signature by iterating over the properties
   * that apply to the given name.  Some annotators may need to extend
   * this if they use properties outside their normal set, such as
   * ssplit using tokenize.whitespace
   */
  static String baseSignature(Properties props, String name) {
    String prefix = name + '.';
    StringBuilder signature = new StringBuilder();
    for (String key : props.stringPropertyNames()) {
      if (key.startsWith(prefix)) {
        signature.append(key).append('=').append(props.getProperty(key)).append('\n');
      }
    }
    return signature.toString();
  }

}
