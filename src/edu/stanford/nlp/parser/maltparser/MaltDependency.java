package edu.stanford.nlp.parser.maltparser;

/**
 * A small class used to hold the results of calling the malt parser
 */
public class MaltDependency {
  public final int governor;
  public final int dependent;
  public final String label;
  
  public MaltDependency(int governor, int dependent, String label) {
    this.governor = governor;
    this.dependent = dependent;
    this.label = label;
  }

  public String toString() {
    return governor + "->" + dependent + " " + label;
  }
}
