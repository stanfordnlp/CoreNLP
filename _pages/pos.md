---
layout: page
title: Parts Of Speech
keywords: pos, POSTaggerAnnotator 
permalink: '/pos.html'
nav_order: 9
parent: Pipeline
---

## Description

Part of speech tagging assigns part of speech labels to tokens, such as whether they are verbs or nouns. Every token in a sentence is applied
a tag. For instance, in the sentence `Marie was born in Paris.` the word `Marie` is assigned the tag `NNP`. 

| Name | Annotator class name | Requirement | Generated Annotation | Description |
| --- | --- | --- | --- | --- |
| pos | POSTaggerAnnotator | TokensAnnotation, SentencesAnnotation | PartOfSpeechAnnotation | Applies part of speech tags to tokens. |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| pos.model | String | edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger | Model to use for part of speech tagging. |
| pos.maxlen | int | Integer.MAX_VALUE | Maximum sentence length to tag. Sentences longer than this will not be tagged. |


## Part Of Speech Tagging From The Command Line

This command will apply part of speech tags to the input text:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos -file input.txt
```

Other output formats include `conllu`, `conll`, `json`, and `serialized`.

This command will apply part of speech tags using a non-default model (e.g. the more powerful but slower bidirectional model):

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos -pos.model edu/stanford/nlp/models/pos-tagger/english-bidirectional-distsim.tagger -file input.txt
```

If running on French, German, or Spanish, it is crucial to use the MWT annotator:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -props french -annotators tokenize,ssplit,mwt,pos -file input.txt
```

## Part Of Speech Tagging From Java

```
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

import java.util.*;

public class POSTaggingExample {

  public static String text = "Marie was born in Paris.";

  public static void main(String[] args) {
    // set up pipeline properties
    Properties props = new Properties();
    // set the list of annotators to run
    props.setProperty("annotators", "tokenize,ssplit,pos");
    // build pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // create a document object
    CoreDocument document = pipeline.processToCoreDocument(text);
    // display tokens
    for (CoreLabel tok : document.tokens()) {
      System.out.println(String.format("%s\t%s", tok.word(), tok.tag()));
    }
  }
}
```

This demo code will print out the part of speech labels for each token:

```
Marie	NNP
was	VBD
born	VBN
in	IN
Paris	NNP
.	.
```
