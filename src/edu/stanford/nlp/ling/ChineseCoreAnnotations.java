package edu.stanford.nlp.ling;

import java.util.List;
import edu.stanford.nlp.util.ErasureUtils;

public class ChineseCoreAnnotations {

  private ChineseCoreAnnotations() { } // only static members

  public static class CharactersAnnotation 
    implements CoreAnnotation<List<CoreLabel>> 
  {
    public Class<List<CoreLabel>> getType() {
      return ErasureUtils.<Class<List<CoreLabel>>> uncheckedCast(List.class);
    }
  }
}