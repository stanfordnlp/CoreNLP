package edu.stanford.nlp.patterns.surface;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreLabel.GenericAnnotation;
import edu.stanford.nlp.util.ErasureUtils;

public class PatternsAnnotations implements Serializable {
  private static final long serialVersionUID = 1L;

  public static class MatchedPattern implements GenericAnnotation<Boolean> {
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  public static class MatchedPatterns implements GenericAnnotation<Set<SurfacePattern>> {
    public Class<Set<SurfacePattern>> getType(){
      return ErasureUtils.<Class<Set<SurfacePattern>>> uncheckedCast(Set.class);
    }

  }


  public static class MatchedPhrases implements GenericAnnotation<Set<String>> {
    public Class<Set<String>> getType() {
      Class<Set<String>> claz = (Class) Set.class;
      return claz;
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


}
