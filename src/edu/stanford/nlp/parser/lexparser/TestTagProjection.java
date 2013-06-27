package edu.stanford.nlp.parser.lexparser;

// Looks like the intended behavior of TestTagProjection is:
// 1) Include the basic category (everything before a '-' or '^' annotation)
// 2) Include any annotation introduced with '-'
// 3) Exclude any annotation introduced with '^'
// 4) Annotations introduced with other characters will be included or excluded
//    as determined by the previous annotation or basic category.
//
// This seems awfully haphazard :(
//
//  Roger


/** @author Dan Klein */
class TestTagProjection implements TagProjection {

  private static final long serialVersionUID = 9161675508802284114L;

  public String project(String tagStr) {
    StringBuilder sb = new StringBuilder();
    boolean good = true;
    for (int pos = 0, len = tagStr.length(); pos < len; pos++) {
      char c = tagStr.charAt(pos);
      if (c == '-') {
        good = true;
      } else if (c == '^') {
        good = false;
      }
      if (good) {
        sb.append(c);
      }
    }
    String ret = sb.toString();
    // System.err.println("TTP mapped " + tagStr + " to " + ret);
    return ret;
  }

}
