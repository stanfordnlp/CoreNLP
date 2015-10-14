---
title: SentimentAnnotator 
keywords: sentiment
permalink: '/sentiment.html'
---

## Description

StanfordCoreNLP includes the sentiment tool and various programs
which support it.  The model can be used to analyze text as part of
StanfordCoreNLP by adding "sentiment" to the list of annotators.
There is also command line support and model training support.

SentimentAnnotator implements Socher et al's sentiment model.  Attaches a binarized tree of the sentence to the sentence level CoreMap.  The nodes of the tree then contain the annotations from RNNCoreAnnotations indicating the predicted class and scores for that subtree.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| sentiment | SentimentAnnotator | entimentCoreAnnotations.AnnotatedTree |

## Options

* sentiment.model: which model to load.  Will default to the model included in the models jar.

## More information 

See the [sentiment page](http://nlp.stanford.edu/sentiment) for more information about this project. 
