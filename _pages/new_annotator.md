---
title: Adding a new annotator 
keywords: new-annotator
permalink: '/new_annotator.html'
---

Users can add custom annotators to StanfordCoreNLP.

1. First write the custom annotator class.  Here is an example which
will take in a dictionary of lemmas.

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.util.ArraySet;

import java.util.*;


public class CustomLemmaAnnotator implements Annotator {

  HashMap<String,String> wordToLemma = new HashMap<String,String>();

  public CustomLemmaAnnotator(String name, Properties props) {
    // load the lemma file
    // format should be tsv with word and lemma
    String lemmaFile = props.getProperty("custom.lemma.lemmaFile");
    List<String> lemmaEntries = IOUtils.linesFromFile(lemmaFile);
    for (String lemmaEntry : lemmaEntries) {
      wordToLemma.put(lemmaEntry.split("\\t")[0], lemmaEntry.split("\\t")[1]);
    }
  }

  public void annotate(Annotation annotation) {
    for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
      String lemma = wordToLemma.getOrDefault(token.word(), token.word());
      token.set(CoreAnnotations.LemmaAnnotation.class, lemma);
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.unmodifiableSet(new ArraySet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class
    )));
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.LemmaAnnotation.class);
  }
  
}
```
