package edu.stanford.nlp.patterns.dep;

import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.util.CollectionValuedMap;

/**
 * Created by sonalg on 10/31/14.
 */
public class DepPattern extends Pattern {
  @Override
  public CollectionValuedMap<String, String> getRelevantWords() {
    return null;
  }

  @Override
  public int equalContext(Pattern p) {
    return 0;
  }

  @Override
  public String toStringSimple() {
    return null;
  }

  public static boolean sameGenre(DepPattern p1, DepPattern p2){
    return false;
  }

  public static boolean subsumes(DepPattern pat, DepPattern p) {
    return false;
  }
}
