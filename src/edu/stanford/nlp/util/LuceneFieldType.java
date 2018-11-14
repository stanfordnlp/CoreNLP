package edu.stanford.nlp.util;

import org.apache.lucene.document.FieldType;

/** An easy way to access types of fields instead of setting variables up every time.
 * Copied from KBPFieldType written by Angel.
 * Created by sonalg on 10/14/14.
 */




public class LuceneFieldType {

  /* Indexed, tokenized, stored. */
  public static final FieldType ANALYZED = new FieldType();
  public static final FieldType ANALYZED_NO_POSITION = new FieldType();
  /* Indexed, tokenized, not stored. */
  public static final FieldType ANALYZED_NOT_STORED = new FieldType();

  /* Indexed, not tokenized, stored. */
  public static final FieldType NOT_ANALYZED = new FieldType();
  /* not Indexed, not tokenized, stored. */
  public static final FieldType NOT_INDEXED = new FieldType();

  static {
    ANALYZED_NOT_STORED.setIndexed(true);
    ANALYZED_NOT_STORED.setTokenized(true);
    ANALYZED_NOT_STORED.setStored(false);
    ANALYZED_NOT_STORED.setStoreTermVectors(true);
    ANALYZED_NOT_STORED.setStoreTermVectorPositions(true);
    ANALYZED_NOT_STORED.freeze();

    ANALYZED.setIndexed(true);
    ANALYZED.setTokenized(true);
    ANALYZED.setStored(true);
    ANALYZED.setStoreTermVectors(true);
    ANALYZED.setStoreTermVectorPositions(true);
    ANALYZED.freeze();

    ANALYZED_NO_POSITION.setIndexed(true);
    ANALYZED_NO_POSITION.setTokenized(true);
    ANALYZED_NO_POSITION.setStoreTermVectors(true);
    ANALYZED_NO_POSITION.setStoreTermVectorPositions(false);
    ANALYZED_NO_POSITION.freeze();

    NOT_ANALYZED.setIndexed(true);
    NOT_ANALYZED.setTokenized(false);
    NOT_ANALYZED.setStored(true);
    NOT_ANALYZED.setStoreTermVectors(false);
    NOT_ANALYZED.setStoreTermVectorPositions(false);
    NOT_ANALYZED.freeze();

    NOT_INDEXED.setIndexed(false);
    NOT_INDEXED.setTokenized(false);
    NOT_INDEXED.setStored(true);
    NOT_INDEXED.setStoreTermVectors(false);
    NOT_INDEXED.setStoreTermVectorPositions(false);
    NOT_INDEXED.freeze();
  }


}


