package edu.stanford.nlp.scenegraph;

import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.ErasureUtils;

public class SceneGraphCoreAnnotations {

  private SceneGraphCoreAnnotations() {

  }

  public static class IndicesAnnotation implements CoreAnnotation<Set<Integer>> {
    public Class<Set<Integer>> getType() {
      return ErasureUtils.<Class<Set<Integer>>> uncheckedCast(Set.class);
    }
  }

  public static class GoldEntityAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PredictedEntityAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class SceneGraphEntitiyAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class CompoundWordAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }


  public static class CompoundLemmaAnnotation implements CoreAnnotation<String> {
    public Class<String> getType() {
      return String.class;
    }
  }


}



