package edu.stanford.nlp.trees.international.negra;

import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.ling.Label;

import java.util.HashMap;
import java.util.Map;

/* An object for edge labels as implemented in the Negra treebank.
 * Negra labels need a CATEGORY, an EDGE LABEL, and a MORPHOLOGICAL
 * INFORMATION value.  There is a different object for lexicalized
 * Negra labels.
 *
 * @author Roger Levy
 */

public class NegraLabel extends StringLabel {

  /**
   * 
   */
  private static final long serialVersionUID = 2847331882765391095L;

  public static final String FEATURE_SEP = "#";

  private String edge;

  @Override
  public LabelFactory labelFactory() {
    return new NegraLabelFactory();
  }

  private static class NegraLabelFactory implements LabelFactory {

    public Label newLabel(String labelStr) {
      return new NegraLabel(labelStr);
    }

    /**
     * Options don't do anything.
     */
    public Label newLabel(String labelStr, int options) {
      return newLabel(labelStr);
    }

    /**
     * Nothing fancy now: just makes the argument the value of the label
     */
    public Label newLabelFromString(String encodedLabelStr) {
      return newLabel(encodedLabelStr);
    }

    /**
     * Iff oldLabel is a NegraLabel, copy it.
     */
    public Label newLabel(Label oldLabel) {
      NegraLabel result;
      if(oldLabel instanceof NegraLabel) {
        NegraLabel l = (NegraLabel) oldLabel;
        result = new NegraLabel(l.value(), l.getEdge(), new HashMap<String,String>());
        for (Map.Entry<String,String> e : l.features.entrySet()) {
          result.features.put(e.getKey(), e.getValue());
        }
      } else {
        result = new NegraLabel(oldLabel.value());
      }
      return result;
    }
  }

  private Map<String,String> features;

  public void setEdge(String edge) {
    this.edge = edge;
  }

  public String getEdge() {
    return edge;
  }

  private NegraLabel() {
  }

  public NegraLabel(String str) {
    this(str, new HashMap<String,String>());
  }

  public NegraLabel(String str, Map<String,String> features) {
    this(str, null, features);
  }

  public NegraLabel(String str, String edge, Map<String,String> features) {
    super(str);
    this.edge = edge;
    this.features = features;
  }

  public void setFeatureValue(String feature, String value) {
    features.put(feature, value);
  }

  public String featureValue(String feature) {
    return features.get(feature);
  }


  @Override
  public String toString() {
    String str = value();
    if (edge != null) {
      str += "->" + getEdge();
    }
    if ( ! features.isEmpty()) {
      str += "." + features.toString();
    }
    return str;
  }

}
