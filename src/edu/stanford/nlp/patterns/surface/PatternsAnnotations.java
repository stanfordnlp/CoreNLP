package edu.stanford.nlp.patterns.surface;

import java.io.Serializable;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel.GenericAnnotation;

public class PatternsAnnotations implements Serializable {
  private static final long serialVersionUID = 1L;

  public static class MatchedPattern implements GenericAnnotation<Boolean> {
    public Class<Boolean> getType() {
      return Boolean.class;
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
}
