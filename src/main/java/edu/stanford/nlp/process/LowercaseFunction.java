package edu.stanford.nlp.process;

import java.io.Serializable;

import java.util.function.Function;

public class LowercaseFunction implements Function<String, String>, Serializable {

  @Override
  public String apply(String input) {
    if (input == null) {
      return null;
    }
    return input.toLowerCase();
  }

  private static final long serialVersionUID = 1L;
}
