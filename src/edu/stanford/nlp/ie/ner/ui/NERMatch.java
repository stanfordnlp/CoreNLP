package edu.stanford.nlp.ie.ner.ui;

/**
 * Class that packages a matched named entity with its start and end index (in words),
 * and whether it was a true positive (TP), false positve (FP), or false negative (FN)
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class NERMatch {
  public static final int TP = 0;
  public static final int FP = 1;
  public static final int FN = 2;

  public NERMatch(int type, int start, int end) {
    this.start = start;
    this.end = end;
    this.type = type;
  }

  public int getStart() {
    return (start);
  }

  public int getEnd() {
    return (end);
  }

  public int getType() {
    return (type);
  }

  private int start; // the starting word index
  private int end; // the ending word index
  private int type; // whether it was a TP, FP, or FN

  /**
   * @param type the string type ("TP","FP","FN")
   * @return the integer constant for the given type
   */
  public static int getType(String type) {
    if (type != null && type.indexOf("TP") != -1) {
      return TP;
    }
    if ("FP".equals(type)) {
      return FP;
    }
    if ("FN".equals(type)) {
      return FN;
    }
    throw(new IllegalArgumentException());
  }
}
