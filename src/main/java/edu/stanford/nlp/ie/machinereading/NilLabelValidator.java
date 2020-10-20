package edu.stanford.nlp.ie.machinereading;

import java.io.Serializable;

import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;

public class NilLabelValidator implements Serializable, LabelValidator {

  private static final long serialVersionUID = 1L;

  public boolean validLabel(String label, ExtractionObject object) {
    return true;
  }

  
}
