package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.util.Pair;

public class Attribute implements Serializable {
  final String key;
  final Object cased;
  final Object caseless;
  final boolean negated;

  // specifies the groups in a regex that are captured as
  // matcher-global string variables
  final List<Pair<Integer, String>> variableGroups;

  Attribute(String key, Object cased, Object caseless, boolean negated, List<Pair<Integer, String>> varGroups) {
    this.key = key;
    this.cased = cased;
    this.caseless = caseless;
    this.negated = negated;
    this.variableGroups = Collections.unmodifiableList(new ArrayList<>(varGroups));
  }

  private static final long serialVersionUID = 973567614155612487L;
}
