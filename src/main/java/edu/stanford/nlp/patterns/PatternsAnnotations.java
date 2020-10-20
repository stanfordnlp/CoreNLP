package edu.stanford.nlp.patterns;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel.GenericAnnotation;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.ErasureUtils;

public class PatternsAnnotations implements Serializable {
  private static final long serialVersionUID = 1L;

  public static class ProcessedTextAnnotation implements GenericAnnotation<String>{
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MatchedPattern implements GenericAnnotation<Boolean> {
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  public static class MatchedPatterns implements GenericAnnotation<Set<Pattern>> {
    public Class<Set<Pattern>> getType(){
      return ErasureUtils.<Class<Set<Pattern>>> uncheckedCast(Set.class);
    }

  }


  /** All matched phrases - can be from multiple labels*/
  public static class MatchedPhrases implements GenericAnnotation<CollectionValuedMap<String, CandidatePhrase>> {
    public Class<CollectionValuedMap<String, CandidatePhrase>> getType() {
      Class<CollectionValuedMap<String, CandidatePhrase>> claz = (Class) Map.class;
      return claz;
    }
  }


  /**
   * For each label, what was the longest phrase that matched. If none, then the map doesn't have the label key
   */
  public static class LongestMatchedPhraseForEachLabel implements  GenericAnnotation<Map<String, CandidatePhrase>>{
    @Override
    public Class<Map<String, CandidatePhrase>> getType() {
      return ErasureUtils.<Class<Map<String, CandidatePhrase>>> uncheckedCast(Map.class);
    }
  }

  public static class PatternLabel1 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel2 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel3 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel4 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel5 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel6 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel7 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel8 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel9 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternLabel10 implements GenericAnnotation<String>{
    public Class<String> getType(){
      return String.class;
    }
  }

  public static class SeedLabeledOrNot implements GenericAnnotation<Map<Class, Boolean>>{
    public Class<Map<Class, Boolean>> getType() {
      return ErasureUtils.<Class<Map<Class, Boolean>>> uncheckedCast(Map.class);}
  }
  public static class OtherSemanticLabel implements GenericAnnotation<String>{
    public Class<String> getType(){
      return String.class;
    }
  }

  public static class Features implements GenericAnnotation<Set<String>>{
    public Class<Set<String>> getType(){
      return ErasureUtils.<Class<Set<String>>> uncheckedCast(Set.class);
    }
  }


  public static class PatternHumanLabel1 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel2 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel3 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel4 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel5 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel6 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel7 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel8 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel9 implements GenericAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PatternHumanLabel10 implements GenericAnnotation<String>{
    public Class<String> getType(){
      return String.class;
    }
  }


}
