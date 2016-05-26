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

  private final String type;
  protected final Properties properties;

  protected AnnotatorFactory(String type, Properties properties) {
    this.type = type;
    // Let's copy the properties, just in case somebody messes with this object later.
    // By using stringPropertyNames(), we also pick up any defaults the Properties has.
    this.properties = new Properties();
    for (String key : properties.stringPropertyNames()) {
      this.properties.setProperty(key, properties.getProperty(key));
    }
  }

  protected AnnotatorFactory(Class<? extends Annotator> type, Properties properties) {
    this(type.getName(), properties);
  }

  /**
   * Creates and returns an Annotator given the local properties.
   *
   * @return A new instance of the type T
   */
  @Override
  public abstract Annotator create();

  /**
   * Returns additional bits of signature relevant for caching the annotator.
   */
  protected abstract String additionalSignature();

  /**
   * Creates the annotator's signature given the current properties.
   * We use this to understand if the user wants to recreate
   * the same annotator type but with different parameters.
   */
  public String signature() {
    return this.type + ':' + additionalSignature();
  }

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
