---
title: Quick Start 
keywords: quick-start 
permalink: '/quick-start.html'
---

## Generating annotations

A Stanford CoreNLP object represents a pipeline of annotators that will be applied to text.

First, specify the annotators you need, using a `Properties` object :

``` java
// creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution 
Properties props = new Properties();
props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
```

Then, instantiate a `StanfordCoreNLP` object with these properties :

``` java
StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
```

Read in your text...

``` java
// read some text in the text variable
String text = ... // Add your text here!
```

Create an empty `Annotation` with your text. An `Annotation` is a data structure which contains the results of annotators, stored as a map from from keys to bits of the annotation, such as the parse, the part-of-speech tags, or named entity tags.

``` java
// create an empty Annotation just with the given text
Annotation document = new Annotation(text);
```

You can now run the pipeline on the document.

``` java
// run all Annotators on this text
pipeline.annotate(document);
```

(If you do not anticipate requiring extensive customization, consider using the [Simple CoreNLP](simple.html) API.)

## Interpreting the output

The output of the `Annotator`s is accessed using the data structures `CoreMap` and `CoreLabel`. 

First, get the annotated sentences:

``` java
// these are all the sentences in this document
// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
List<CoreMap> sentences = document.get(SentencesAnnotation.class);
for(CoreMap sentence: sentences) {
```

We can now traverse the words in each sentence, which are represented by the `CoreLabel` data structure.

```java
  // traversing the words in the current sentence
  // a CoreLabel is a CoreMap with additional token-specific methods
  for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
    // this is the text of the token
    String word = token.get(TextAnnotation.class);
    // this is the POS tag of the token
    String pos = token.get(PartOfSpeechAnnotation.class);
    // this is the NER label of the token
    String ne = token.get(NamedEntityTagAnnotation.class);
  }
```

You can access the parse tree and dependency graph of a sentence:

```java
  // this is the parse tree of the current sentence
  Tree tree = sentence.get(TreeAnnotation.class);

  // this is the Stanford dependency graph of the current sentence
  SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
}
```
As well as the coreference link graph for the document:

```java
// This is the coreference link graph
// Each chain stores a set of mentions that link to each other,
// along with a method for getting the most representative mention
// Both sentence and token offsets start at 1!
Map<Integer, CorefChain> graph = 
  document.get(CorefChainAnnotation.class);
```


