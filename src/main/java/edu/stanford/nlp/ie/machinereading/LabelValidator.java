/**
 * Verifies if the label predicted by a classifier is compatible with the object itself
 * For example, in KBP you cannot have a "org:*" relation if the first argument is PER entity
 */
package edu.stanford.nlp.ie.machinereading;

import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;

public interface LabelValidator {
  public boolean validLabel(String label, ExtractionObject object);
}
