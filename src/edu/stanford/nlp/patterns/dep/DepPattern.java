package edu.stanford.nlp.patterns.dep;

import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.util.CollectionValuedMap;

import java.util.List;

/**
 * Created by sonalg on 10/31/14.
 */
public class DepPattern extends Pattern {

  public DepPattern() {
    super(PatternFactory.PatternType.DEP);
  }

  //TODO: implement this class

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

  @Override
  public String toString(List<String> notAllowedClasses) {
    //TODO: implement this
    return null;
  }

  public static boolean sameGenre(DepPattern p1, DepPattern p2){
    return false;
  }

  public static boolean subsumes(DepPattern pat, DepPattern p) {
    return false;
  }
}
