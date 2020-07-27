---
layout: page
title: Lemmatization
keywords: lemma, MorphaAnnotator 
permalink: '/lemma.html'
nav_order: 10
parent: Pipeline
---

## Description

Lemmatization maps a word to its lemma (dictionary form). For instance, the word `was` is mapped to the word `be`.

| Name | Annotator class name | Requirement | Generated Annotation | Description |
| --- | --- | --- | --- | --- |
| lemma | MorphaAnnotator | TokensAnnotation, SentencesAnnotation, PartOfSpeechAnnotation | LemmaAnnotation | Determine lemmas for each token. |


## Lemmatization From The Command Line

This command will find lemmas for the input text:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma -file input.txt
```

Other output formats include `conllu`, `conll`, `json`, and `serialized`.

## Lemmatization From Java

```
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

import java.util.*;


public class LemmatizingExample {

  public static String text = "Marie was born in Paris.";

  public static void main(String[] args) {
    // set up pipeline properties
    Properties props = new Properties();
    // set the list of annotators to run
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
    // build pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // create a document object
    CoreDocument document = pipeline.processToCoreDocument(text);
    // display tokens
    for (CoreLabel tok : document.tokens()) {
      System.out.println(String.format("%s\t%s", tok.word(), tok.lemma()));
    }
  }

}
```

This demo code will print out the lemmas for each token:

```
Marie	Marie
was	be
born	bear
in	in
Paris	Paris
.	.
```
