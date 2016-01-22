package edu.stanford.nlp.process;

import java.io.Serializable;

import java.util.function.Function;

public class AmericanizeFunction implements Function<String, String>, Serializable {
  public String apply(String input) {
    if (input == null) {
      return null;
    }
    return Americanize.americanize(input);
  }

  private static final long serialVersionUID = 1L;    
}
