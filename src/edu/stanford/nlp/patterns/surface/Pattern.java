package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.ling.CoreLabel;

import java.util.List;
import java.util.Set;

/**
 * Created by Sonal Gupta on 10/27/14.
 */
public abstract class Pattern {

  private Class type;

  public Pattern(Class type){
    this.type = type;
  }


  public static boolean sameGenre(PatternFactory.PatternType patternClass, Pattern p1, Pattern p2){
    if(patternClass.equals(PatternFactory.PatternType.SURFACE))
      return SurfacePattern.sameGenre((SurfacePattern)p1, (SurfacePattern)p2);
    else
      throw new UnsupportedOperationException();
  }

  public abstract edu.stanford.nlp.util.CollectionValuedMap<String, String> getRelevantWords();

  public static boolean subsumes(PatternFactory.PatternType patternClass, Pattern pat, Pattern p) {
    if(patternClass.equals(PatternFactory.PatternType.SURFACE))
      return SurfacePattern.subsumes((SurfacePattern)pat, (SurfacePattern)p);
    else
      throw new UnsupportedOperationException();
  }

  public abstract int equalContext(Pattern p);

  public abstract String toStringSimple();

  public static Set getContext(PatternFactory.PatternType patternClass, List<CoreLabel> sent, int i) {
    if(patternClass.equals(PatternFactory.PatternType.SURFACE))
      return SurfacePatternFactory.getContext(sent, i);
    else
      throw new UnsupportedOperationException();
  }
}
