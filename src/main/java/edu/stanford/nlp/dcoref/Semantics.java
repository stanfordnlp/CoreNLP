package edu.stanford.nlp.dcoref;

import java.lang.reflect.Constructor;

/** Semantic knowledge: currently WordNet is available */
public class Semantics {
  public Object wordnet;
  
  public Semantics() {}

  public Semantics(Dictionaries dict) throws Exception{
    Constructor<?> wordnetConstructor = (Class.forName("edu.stanford.nlp.dcoref.WordNet")).getConstructor();
    wordnet = wordnetConstructor.newInstance();
  }
}