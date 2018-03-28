---
title: Introduction to pipelines
keywords: pipeline annotator
permalink: '/pipelines.html'
---

## Introduction to pipelines

CoreNLP implements an annotation pipeline. An `Annotation` object is used that stores analyses of a piece of text. It is a `Map`. Initially, the text of a document is added to the `Annotation` as its only contents. Then, an `AnnotationPipeline` is run on the `Annotation`. An `AnnotationPipeline` is essentially a `List` of `Annotator`s that is run in turn. (And an `AnnotationPipeline` is itself an Annotator, so you can actually nest `AnnotationPipeline`s inside each other.) Each `Annotator` reads the value of one or more keys from the `Annotation`, does some natural language analysis, and then writes the results back to the `Annotation`, typically under different keys, so that the information stored in an `Annotation` is cumulative rather than things being overwritten. The overall picture is like in this picture.

![AnnotationPipeline picture]({{ site.github.url }}/images/AnnotationPipeline.png)

For the more technically inclined, an `Annotation` is stored as a typesafe heterogeneous map, following the ideas for this data
type presented by Bloch (2008) “Effective Java”.

Around this basic skeleton, `StanfordCoreNLP` adds a lot of stuff, for processing options, caching `Annotator`s, writing output in different formats, and all the other modcons of life. Normally, this stuff is convenient to have. However, if it is getting in your way, you can actually fairly easily make your own `AnnotationPipeline` using either or both the various `Annotator`s provided with CoreNLP or additional implementations of `Annotator` that you write. In Java code, that looks something like this:

```java
  public AnnotationPipeline buildPipeline(){
    AnnotationPipeline pl = new AnnotationPipeline();
    pl.addAnnotator(new TokenizerAnnotator(false));
    pl.addAnnotator(new WordsToSentencesAnnotator(false));
    pl.addAnnotator(new POSTaggerAnnotator(false));
    pl.addAnnotator(new MorphaAnnotator(false));
    pl.addAnnotator(new TimeAnnotator("sutime", props));
    pl.addAnnotator(new PhraseAnnotator(phrasesFile, false));
    return pl;
  }
  ```

An Annotator
is a class that implements three methods: a single
method for analysis, and two that describe the
dependencies between analysis steps:

```java
public void annotate(Annotation annotation);
public Set<Requirement> requirementsSatisfied();
public Set<Requirement> requires();
```

With a custom analysis pipeline, only the first method is used. The other two methods are used in `StanfordCoreNLP` to check for dependencies between `Annotator`s.
