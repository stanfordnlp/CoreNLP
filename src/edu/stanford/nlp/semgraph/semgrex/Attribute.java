package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;

public class Attribute implements Serializable {
  final String key;
  final Object cased;
  final Object caseless;
  final boolean negated;

  Attribute(String key, Object cased, Object caseless, boolean negated) {
    this.key = key;
    this.cased = cased;
    this.caseless = caseless;
    this.negated = negated;
  }

  private static final long serialVersionUID = 973567614155612487L;
}
