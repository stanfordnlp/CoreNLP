package edu.stanford.nlp.ie;

import edu.stanford.nlp.ling.BasicDocument;
import edu.stanford.nlp.ling.HasType;
import edu.stanford.nlp.ling.Word;

import java.util.Iterator;

/**
 * Document whose words are {@link edu.stanford.nlp.ling.TypedTaggedWord} objects. When reading in text,
 * all word types are assumed to be 0 (i.e. background state).
 *
 * @author Huy Nguyen (htnguyen@stanford.edu)
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see #getTypeSequence
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 */
public class TypedTaggedDocument<L> extends BasicDocument<L> {
  /**
   * 
   */
  private static final long serialVersionUID = -1223080266359464067L;
  private String[] targetFields;

  /**
   * Constructs a new TypedTaggedDocument with no target fields.
   */
  public TypedTaggedDocument() {
    this(new String[0]);
  }

  /**
   * Constructs a new TypedTaggedDocument with a single target field.
   */
  public TypedTaggedDocument(String targetField) {
    this(new String[]{targetField});
  }

  /**
   * Constructs a new TypedTaggedDocument with types corresponding to the
   * given list of target fields. Type 0 in the document refers to the
   * background state, and the numbered target types start with type 1
   * corresponding to <tt>targetTypes[0]</tt>.
   */
  public TypedTaggedDocument(String[] targetFields) {
    this.targetFields = getTargetFieldsPlusBG(targetFields);
  }

  /**
   * Returns the name of the targetField with the given index.
   * Index 0 is generally (Background) and [1-n] are the actual targets.
   */
  public String getTargetField(int targetFieldIndex) {
    return targetFields[targetFieldIndex];
  }

  /**
   * Adds "(Background)" as the 0th target field if it's not already there.
   * Returns either a new array with BG added or the existing array if it
   * already had it. A null targetFields is treated as an empty array.
   */
  public static String[] getTargetFieldsPlusBG(String[] targetFields) {
    // make sure target fields include background
    if (targetFields == null) {
      targetFields = new String[0]; // null is like empty
    }
    if (targetFields.length == 0 || !targetFields[0].equals("(Background)")) {
      String[] targetFieldsPlusBG = new String[targetFields.length + 1];
      targetFieldsPlusBG[0] = "(Background)";

      for (int i = 0; i < targetFields.length; i++) {
        targetFieldsPlusBG[i + 1] = targetFields[i];
      }
      return (targetFieldsPlusBG);
    }
    return (targetFields);
  }

  /**
   * Returns an array representing the type of each word in this Document.
   * The ith element in the returned array is the type of the ith word if it
   * implements HasType (as it should if you've constructed this normally),
   * or 0 (i.e. background state) otherwise.
   */
  public int[] getTypeSequence() {
    int[] types = new int[size()];
    Iterator<Word> iter = iterator();
    int i = 0; // index in array
    while (iter.hasNext()) {
      Word w = iter.next();
      if (w instanceof HasType) {
        types[i] = ((HasType) w).type();
      } else {
        types[i] = 0; // assume background type if none given
      }
      i++;
    }
    return (types);
  }
}
