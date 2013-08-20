package edu.stanford.nlp.ie;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.Word;

import java.util.Set;

/**
 * FieldExtractorCreator for classifier-based field extractors.
 * That is, this extractor actually does text classification over a finite
 * set of classes.  Currently the
 * only way to build a ClassifierFieldExtractor is to first build a Classifier.
 * That classifier can then be passed directly to a ClassifierFieldExtractor,
 * or it can be serialized and passed to ClassifierFieldExtractorCreator via
 * a property for its filename.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class ClassifierFieldExtractorCreator extends AbstractFieldExtractorCreator {
  private static final String serializedClassifierProperty = "serializedClassifier";

  /**
   * Constructs a new ClassifierFieldExtractorCreator with name "Classifier-based Extractor"
   * and the required property  "serializedClassifier".
   */
  public ClassifierFieldExtractorCreator() {
    setName("Classifier-based Extractor");
    setPropertyRequired(serializedClassifierProperty);
    setPropertyDescription(serializedClassifierProperty, "Filename of serialized Classifier to use with this extractor");
    setPropertyClass(serializedClassifierProperty, PC_FILE);
  }

  /**
   * Returns the property "serializedClassifier".
   * <ul>
   * <li><tt>serializedClassifier</tt> the filename of the serialized
   * Classifier to use
   * </ul>
   */
  @Override
  public Set<String> propertyNames() {
    return (asSet(new String[]{serializedClassifierProperty}));
  }

  /**
   * Creates a new ClassifierFieldExtractor with the given name, and classifier.
   * The classifier is unserialized from the <tt>serializedClassifier</tt>
   * property and used to create the ClassifierFieldExtractor.
   * The extracted field name will be the same as the extractor name.
   *
   * @throws IllegalPropertyException if targetField is empty or regexp has illegal syntax.
   */
  public FieldExtractor createFieldExtractor(String name) throws IllegalPropertyException {
    try {
      Classifier<String, Word> classifier = IOUtils.readObjectFromFile(getProperty(serializedClassifierProperty));
      return (new ClassifierFieldExtractor(classifier, name));
    } catch (Exception e) {
      throw(new IllegalPropertyException(this, "Error while unserializing classifier: " + e.getMessage(), serializedClassifierProperty, getProperty(serializedClassifierProperty)));
    }
  }

}
