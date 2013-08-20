package edu.stanford.nlp.trees.international.icegb;

import edu.stanford.nlp.ling.HasCategory;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * An object for labels as implemented in the ICE-GB corpus.
 * The label includes a category, a function and a List of features
 *
 * @author Pi-Chuan Chang
 */

public class ICEGBLabel implements HasCategory, Label { 
// this class don't implement HasWord, because the terminal node is not supposed to be a ICEGBLabel.

  private String category = null;
  private String function = null; //get+set
  private Set<String> features = null;
  private String word = null;
  
  private static final boolean addFeatures = true; // temporarily added to specify the different "value" of ICEGBLabel

  public String category() {
    return category;
  }
  
  public String value() {
    if (addFeatures) {
      StringBuilder val = new StringBuilder();
      val.append(category);
      val.append(features);
      return val.toString();
    } else {
      return category;
    }
  }

  public void setCategory(String cat) {
    category = cat;
  }

  public void setValue(String val) {
    if (addFeatures) {
      throw new UnsupportedOperationException();
    } else {
      setCategory(val);
    }
  }


  /**
   * Sets the functional category
   */
  public void setFunction(String function) {
    this.function = function;
  }

  /**
   * Retrieves the functional category
   */
  public String function() {
    return function;
  }

  public void setWord(String word) {
    this.word = word;
  }
  
  public String word() {
    return word;
  }

  public ICEGBLabel() {
    features = new LinkedHashSet<String>();
  }

  public ICEGBLabel(Label oldLabel) {
    if (oldLabel instanceof ICEGBLabel) {
      this.function = ((ICEGBLabel)oldLabel).function();
      this.category = ((ICEGBLabel)oldLabel).category();
      this.features = new LinkedHashSet<String>();
      this.features.addAll(((ICEGBLabel)oldLabel).features());
    } else {
      throw new UnsupportedOperationException();
    }
  }

  public ICEGBLabel(String str) {
    setFromString(str);
  }

  public void setFromString(String str) {
    str = str.trim();
    String[] twoParts = str.split("[{}]");
    // if the "{foo}" part exists
    if (twoParts.length >= 2) 
      this.word = twoParts[1];

    String[] toks = twoParts[0].split("[,()]");
    setFunction(toks[0].trim());
    setCategory(toks[1].trim());
    features = new LinkedHashSet<String>();

    for (int i = 2; i < toks.length; i++) {
      String t = toks[i].trim();
      if (t.length()>0) {
        features.add(t);
      }
    }
  }
  
  /**
   * Sets the feature values
   */
  public void setFeatures(Set<String> feat) {
    features = feat;
  }

  /**
   * Retrieves the feature values
   */
  public Set<String> features() {
    return features;
  }

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {
    private static final LabelFactory lf = new ICEGBLabelFactory();
  }

  /**
   * Returns a factory that makes <code>ICEGBLabel</code>s.
   */
  public LabelFactory labelFactory() {
    return LabelFactoryHolder.lf;
  }
  
  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append(function());
    str.append("/");
    str.append(category());
    str.append(features());
    if (word() != null) { 
      str.append("{");
      str.append(word());
      str.append("}");
    }
    return str.toString();
  }
  
  /**
   * test only
   */
  public static void main(String[] args) {
    ICEGBLabel l = new ICEGBLabel("PU,CL(main,montr,pres)");
    System.err.println(l);
  }
}
