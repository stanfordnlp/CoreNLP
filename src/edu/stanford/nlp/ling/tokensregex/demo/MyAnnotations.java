package edu.stanford.nlp.ling.tokensregex.demo;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.ErasureUtils;

import java.util.List;

/**
 * Example annotations.
 *
 * @author Angel Chang
 */
public class MyAnnotations {

  // My custom tokens
  public static class MyTokensAnnotation implements CoreAnnotation<List<? extends CoreMap>> {
    @Override
    public Class<List<? extends CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  // My custom type
  public static class MyTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return ErasureUtils.uncheckedCast(String.class);
    }
  }

  // My custom value
  public static class MyValueAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return ErasureUtils.uncheckedCast(String.class);
    }
  }

}
