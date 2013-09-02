package edu.stanford.nlp.ie;

import java.util.HashMap;
import java.util.Map;

/**
 * Superclass of FieldExtractors that only return a single (literal) field.
 * Implements the FieldExtractor interface and in turn presents a simpler, more
 * tailored set of abstract methods for subclasses to override.
 * <p/>
 * Added 6/4/02: <code>extractField(Instance, String, Confidence)</code>
 * and <code>extractFields(Instance, String, Confidence)</code>,
 * for rudimentary merging functionality.
 * <p/>
 * Post-KAON cleanup done by Joseph Smarr on 2/12/03.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public abstract class SingleFieldExtractor extends AbstractFieldExtractor {

  /**
   * Used for serialization compatibility across minor edits
   */
  private static final long serialVersionUID = 5697076532695454354L;

  /**
   * Subclasses should override this method to return the name of the field they
   * know how to extract. This prevents them from having to deal with a list
   * of extractable fields.
   */
  public abstract String getExtractableField();


  /**
   * Returns a singleton array containing {@link #getExtractableField}.
   */
  public String[] getExtractableFields() {
    return (new String[]{getExtractableField()});
  }

  /**
   * Subclasses should override this method to perform extraction from the
   * given text. If nothing was extracted, return the empty string but not
   * null. This class will take care of creating the map and returning it.
   */
  public abstract String extractField(String text);

  /**
   * Returns a map with the single entry of the extractable field for this
   * class mapped to its extracted value from the given text. The key will
   * be extactly {@link #getExtractableField} and the value will be the
   * result of {@link #extractField}.
   */
  public Map extractFields(String text) {
    String extractedText = extractField(text);
    Map extractedFields = new HashMap();
    extractedFields.put(getExtractableField(), extractedText);
    return (extractedFields);
  }
}

