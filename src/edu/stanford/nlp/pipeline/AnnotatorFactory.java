package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A Factory for creating a certain type of Annotator.
 *
 * @author Mihai Surdeanu
 */
public abstract class AnnotatorFactory implements Factory<Annotator> {

  private static final long serialVersionUID = -1554647325549869340L;

  /** The name of the annotator -- i.e., the prefix for all of its properties. */
  private final String name;
  private final String type;
  protected final Properties properties;

  protected AnnotatorFactory(String name, String type, Properties properties) {
    this.name = name;
    this.type = type;
    // Let's copy the properties, just in case somebody messes with this object later.
    // By using stringPropertyNames(), we also pick up any defaults the Properties has.
    this.properties = new Properties();
    for (String key : properties.stringPropertyNames()) {
      this.properties.setProperty(key, properties.getProperty(key));
    }
  }

  protected AnnotatorFactory(String name, Class<? extends Annotator> type, Properties properties) {
    this(name, type.getName(), properties);
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
   * By default, an annotator will have a signature of the properties set for the annotator.
   * This function is here to allow an annotator's signature to depend on more than just
   * its own properties.
   */
  protected String additionalSignature() { return ""; }

  /**
   * Creates the annotator's signature given the current properties.
   * We use this to understand if the user wants to recreate
   * the same annotator type but with different parameters.
   */
  public String signature() {
    return this.type + '#' +
        PropertiesUtils.getSignature(this.name, this.properties) + "#" +
        this.additionalSignature();
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
