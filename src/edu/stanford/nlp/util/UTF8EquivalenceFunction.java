package edu.stanford.nlp.util;

import java.io.Serializable;

/**
 * A word function that can be applied to Chinese text in the tagger
 * or similar systems to make it treat ( and （ the same.
 *
 * @author John Bauer
 */
public class UTF8EquivalenceFunction implements Function<String, String>, Serializable {
  public String apply(String input) {
    if (input == null) {
      return null;
    }
    if (input.equals("(")) {
      return "（";
    } else if (input.equals(")")) {
      return "）";
    } else if (input.equals("[")) {
      return "［";
    } else if (input.equals("]")) {
      return "］";
    } else {
      return input;
    }
  }

  private static final long serialVersionUID = 1L;    
}
