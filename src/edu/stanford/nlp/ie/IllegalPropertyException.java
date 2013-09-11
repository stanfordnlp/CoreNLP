package edu.stanford.nlp.ie;

/**
 * Unchecked exception thrown to indicate a property value in a
 * FieldExtractorCreator that makes it impossible to create the FieldExtractor.
 * This might be a property referring to a non-existent file or a number property
 * that doesn't parse, etc. This Exception provides enough information that a UI
 * should be able to give a targeted message and allow a targeted fix of the
 * offending property.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class IllegalPropertyException extends java.lang.IllegalArgumentException {
  /**
   * 
   */
  private static final long serialVersionUID = -7218433234372062158L;
  private final FieldExtractorCreator fec;
  private final String description;
  private final String key;
  private final String value;

  /**
   * Constructs a new IllegalPropertyException for the given FieldExtractorCreator,
   * indicating the name and value of the illegal property and a description
   * of why it was illegal.
   *
   * @param fec         the FieldExtractorCreator that generated this Exception
   * @param description why this property setting was illegal
   * @param key         name of illegal property
   * @param value       value of illegal property
   */
  public IllegalPropertyException(FieldExtractorCreator fec, String description, String key, String value) {
    this.fec = fec;
    this.description = description;
    this.key = key;
    this.value = value;
  }

  /**
   * Returns the FieldExtractorCreator that generated this Exception.
   */
  public FieldExtractorCreator getFieldExtractorCreator() {
    return (fec);
  }

  /**
   * Returns a description of why this property setting was illegal.
   */
  public String getDescription() {
    return (description);
  }

  /**
   * Returns the name of the illegal property.
   */
  public String getKey() {
    return (key);
  }

  /**
   * Returns the value of the illegal property.
   */
  public String getValue() {
    return (value);
  }

  /**
   * Returns the description of why this property setting was illegal (this
   * is a more standard method to call, though there's no reason to call it
   * instead of <tt>getDescription</tt>.
   */
  @Override
  public String getMessage() {
    return (description);
  }
}
