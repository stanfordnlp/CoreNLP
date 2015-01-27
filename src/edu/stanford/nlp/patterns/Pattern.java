package edu.stanford.nlp.patterns;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.dep.DepPattern;
import edu.stanford.nlp.patterns.dep.DepPatternFactory;
import edu.stanford.nlp.patterns.surface.SurfacePattern;
import edu.stanford.nlp.patterns.surface.SurfacePatternFactory;
import edu.stanford.nlp.patterns.surface.Token;
import edu.stanford.nlp.util.CollectionValuedMap;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An abstract class to represent a Pattern. Currently only surface patterns are implemented.
 * In future, dependency patterns shd be implemented too.
 *
 * @Author Sonal Gupta @sonalg.
 */
public abstract class Pattern implements Serializable{

  public PatternFactory.PatternType type;

  public Pattern(PatternFactory.PatternType type){
    this.type = type;
  }


  public static boolean sameGenre(PatternFactory.PatternType patternClass, Pattern p1, Pattern p2){
    if(patternClass.equals(PatternFactory.PatternType.SURFACE))
      return SurfacePattern.sameGenre((SurfacePattern) p1, (SurfacePattern) p2);
    else if(patternClass.equals(PatternFactory.PatternType.DEP))
      return DepPattern.sameGenre((DepPattern) p1, (DepPattern) p2);
    else
      throw new UnsupportedOperationException();
  }

  public abstract edu.stanford.nlp.util.CollectionValuedMap<String, String> getRelevantWords();

  public static boolean subsumes(PatternFactory.PatternType patternClass, Pattern pat, Pattern p) {
    if(patternClass.equals(PatternFactory.PatternType.SURFACE))
      return SurfacePattern.subsumes((SurfacePattern)pat, (SurfacePattern)p);
    else if(patternClass.equals(PatternFactory.PatternType.DEP))
      return DepPattern.subsumes((DepPattern)pat, (DepPattern)p);
    else
      throw new UnsupportedOperationException();
  }

  public abstract int equalContext(Pattern p);

  public abstract String toStringSimple();

  /** Get set of patterns around this token.*/
  public static Set getContext(PatternFactory.PatternType patternClass, DataInstance sent, int i, Set<CandidatePhrase> stopWords) {
    if(patternClass.equals(PatternFactory.PatternType.SURFACE))
      return SurfacePatternFactory.getContext(sent.getTokens(), i, stopWords);
    else
      return DepPatternFactory.getContext(sent, i, stopWords);
  }

  public abstract String toString(List<String> notAllowedClasses);

  static protected void getRelevantWordsBase(Token[] t, CollectionValuedMap<String, String> relWords){
    if (t != null)
      for (Token s : t) {
        Map<String, String> str = s.classORRestrictionsAsString();
        if (str != null){
          relWords.addAll(str);
        }
      }
  }

  static protected void getRelevantWordsBase(Token t, CollectionValuedMap<String, String> relWords){
    if (t != null) {
      Map<String, String> str = t.classORRestrictionsAsString();
      if (str != null) {
        relWords.addAll(str);
      }
    }
  }
}
