package edu.stanford.nlp.ling;

import edu.stanford.nlp.util.TypesafeMap;

public interface AbstractCoreLabel extends Label, HasWord, HasIndex, HasTag, HasLemma, HasOffset, TypesafeMap {
  public String ner();

  public void setNER(String ner);

  public String originalText();

  public void setOriginalText(String originalText);

  public <KEY extends Key<String>> String getString(Class<KEY> key);
}
