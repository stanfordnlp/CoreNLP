package edu.stanford.nlp.tagger.maxent;

/**
 * Extractor for adding a conjunction of distsim information.
 *
 * @author rafferty
 */
public class ExtractorDistsimConjunction extends Extractor {

  private static final long serialVersionUID = 1L;

  private final Distsim lexicon;
  private final int left;
  private final int right;
  private String name;

  @Override
  String extract(History h, PairsHolder pH) {
    StringBuilder sb = new StringBuilder();
    for (int j = left; j <= right; j++) {
      String word = pH.getWord(h, j);
      String distSim = lexicon.getMapping(word);
      sb.append(distSim);
      if (j < right) {
        sb.append('|');
      }
    }
    return sb.toString();
  }

  ExtractorDistsimConjunction(String distSimPath, int left, int right) {
    super();
    lexicon = Distsim.initLexicon(distSimPath);
    this.left = left;
    this.right = right;
    name = "ExtractorDistsimConjunction(" + left + ',' + right + ')';
  }

  @Override
  public String toString() {
    return name;
  }

  @Override public boolean isLocal() { return false; }
  @Override public boolean isDynamic() { return false; }
  
} // end static class ExtractorDistsimConjunction

