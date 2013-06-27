package edu.stanford.nlp.util;

import java.io.Serializable;

public class LowercaseFunction implements Function<String, String>, Serializable {
  public String apply(String input) {
    if (input == null) {
      return null;
    }
    return input.toLowerCase();
  }

  private static final long serialVersionUID = 1L;  
}
