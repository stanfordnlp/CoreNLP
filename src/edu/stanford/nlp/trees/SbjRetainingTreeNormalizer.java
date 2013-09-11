package edu.stanford.nlp.trees;

/**
 * Same TreeNormalizer as BobChrisTreeNormalizer, but retains -SBJ
 * labels on NP with the new identification NP#SBJ
 *
 * @author Roger Levy
 */
public class SbjRetainingTreeNormalizer extends BobChrisTreeNormalizer {

  /**
   * 
   */
  private static final long serialVersionUID = 4211724108734555526L;

  /**
   * Remove things like hyphened functional tags and equals from the
   * end of a node label.
   */
  @Override
  protected String cleanUpLabel(String label) {
    //System.out.println("Cleaning up label " + label);

    if (label == null) {
      label = "ROOT";
      // String constants are always interned
    } else {
      if (label.matches("^NP-SBJ")) {
        label = label.replaceAll("^NP-SBJ", "NP#SBJ");
        //System.out.println("Changed functional tag for subject"); //testing
      }
      // a '-' at the beginning of label is okay (punctuation tag!)
      int k = label.indexOf('-');
      if (k > 0) {
        label = label.substring(0, k);
      }
      k = label.indexOf('=');
      if (k > 0) {
        label = label.substring(0, k);
      }
      k = label.indexOf('|');
      if (k > 0) {
        label = label.substring(0, k);
      }
    }
    //System.out.println("Cleaned up label is " + label);
    return label;
  }

}
