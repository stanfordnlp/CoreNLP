package edu.stanford.nlp.process;

import java.io.Serializable;

import edu.stanford.nlp.util.Function;

public class LowercaseAndAmericanizeFunction implements Function<String, String>, Serializable {
  public String apply(String input) {
    if (input == null) {
      return null;
    }
    return Americanize.americanize(input.toLowerCase());
  }

  private static final long serialVersionUID = 1L;    
}
