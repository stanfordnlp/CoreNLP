package edu.stanford.nlp.ie;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Map;

/**
 * Common interface for all information extraction components.
 * FieldExtractors have a unique name. They can answer what fields they know
 * how to extract, and they can extract those fields from arbitrary text.
 * They can be serialized to files and later reloaded. The FieldExtractor
 * interface hides the information extraction client from the particular
 * implementation of each extractor, which may be based on regular expressions,
 * hidden Markov models, or other methods. It also provides a way to load
 * and work with a heterogenous collection of extractors through a common
 * API.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public interface FieldExtractor extends Serializable {
  /**
   * Returns a unique name for this instantiated FieldExtractor.
   * Note that this is not a name for this class, but for this particular
   * instantiated extractor, which may have its own properties and settings.
   * Names are used to uniquely identify extractors in a collection.
   * Impementations may want to support a name passed in by the user on
   * construction or a parameterized string that prints some state like
   * toString might do.
   *
   * @return a unique name for this instantiated field extractor
   */
  public String getName();

  /**
   * Returns a brief description of this instantiated FieldExtractor.
   * The description should be suitable for users browsing a collection of
   * extractors and wanting to know more about their specific abilities and
   * internal workings. The description might appear in a tool tip or in an
   * accompanying description panel. It shouldn't be too long, but it should
   * provide informative information beyond the name.
   *
   * @return a brief description of this field extractor
   */
  public String getDescription();

  /**
   * Returns a list of the field names that this extractor knows how to fill.
   * It is assumed that when {@link #extractFields} is called, the resulting
   * map will be between all target fields in this list, and their extracted
   * text (which may or may not be empty). The returned array may be empty
   * but should never be null.
   *
   * @return a list of the field names this extractor knows how to extract
   */
  public String[] getExtractableFields();

  /**
   * Extracts fields from the given text and returns them as a map from target
   * fields (String) to extracted text (String). If a given target field fails
   * to find any extractable text, the map should contain the target field
   * mapped to the empty string. This way it's always possible to get the
   * contents of all extractable fields, though some may be empty.
   *
   * @param text the input text (document) from which to extract fields
   * @return a Map from each extractable field name (String) to the text
   *         extracted from the document (String). Extracted string values may be
   *         empty but may never be null, and the Map will contain keys for all
   *         target fields returned by {@link #getExtractableFields}.
   */
  public Map extractFields(String text);

  /**
   * Serializes this extractor to the given output stream for later retrieval.
   * It is expected that FieldExtractors will be individually instantiated
   * and/or trained with data and then serialized into a collection of
   * extractors that can be collectively loaded and used at a later time.
   * Note that loading of extractors is not done through a method inside
   * FieldExtractor because that would require first constructing a FieldExtractor
   * if the appropriate type and then loading the object file into itself.
   * Instead there is a static method {@link ExtractorUtilities#loadExtractor}
   * for loading extractors. It assumes that this method just wrote out the
   * extractor as a serialized object, so we may have to do something more
   * clever to support custom serialized formats.
   *
   * @throws IOException if there's a problem serializing the extractor
   */
  public void storeExtractor(OutputStream out) throws IOException;

}
