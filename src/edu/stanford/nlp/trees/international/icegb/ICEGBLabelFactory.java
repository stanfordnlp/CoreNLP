package edu.stanford.nlp.trees.international.icegb;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.trees.international.icegb.ICEGBLabel;


/**
 * A <code>ICEGBLabelFactory</code> is a factory that makes
 * a <code>Label</code> which is a <code>ICEGBLabel</code>
 *
 * @author Pi-Chuan Chang
 */

public class ICEGBLabelFactory implements LabelFactory {
  /** Create a new Label, where the label is formed from the Label object passed in.
   * @param oldLabel the old label
   * @return the newly created label
   */
  public Label newLabel(Label oldLabel) {
    return new ICEGBLabel(oldLabel);
  }

  /** Make a new label with this String as the the whole description.
   * @param description should be in this format: "Function,Category(Features)" or "{Word}"
   * @return the newly created label
   */
  public Label newLabel(String description) {
    if (description.startsWith("{")) { // leaf node. the value is just the word
      description = description.replaceAll("^\\{","");
      description = description.replaceAll("\\}$","");
      return new StringLabel(description);
    } else {
      return new ICEGBLabel(description);
    }
  }

  /** Make a new label with this String as the whole description, 
   * @param description should be in this format: "Function,Category(Features)" or "{Word}"
   * @param options this is ignored
   * @return the newly created label
   */
  public Label newLabel(String description, int options) {
    return newLabel(description);
  }  

  /** Make a new label with this String as the the whole description.
   * @param description should be in this format: "Function,Category(Features)" or "{Word}"
   * @return the newly created label
   */
  public Label newLabelFromString(String description) {
    return newLabel(description);
  }
}

