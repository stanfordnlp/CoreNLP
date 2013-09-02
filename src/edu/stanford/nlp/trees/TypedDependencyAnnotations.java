package edu.stanford.nlp.trees;

import java.util.Collection;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.ErasureUtils;

public class TypedDependencyAnnotations {
  public static class TypedDependenciesAnnotation 
    implements CoreAnnotation<Collection<TypedDependency>> 
  {
    public Class<Collection<TypedDependency>> getType() { 
      return ErasureUtils.<Class<Collection<TypedDependency>>> 
        uncheckedCast(Collection.class);
    }
  }
}
