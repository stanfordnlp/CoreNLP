---
title: NaturalLogicAnnotator 
keywords: natlog
permalink: '/natlog.html'
---

## Description

Marks quantifier scope and token polarity, according to [natural logic](http://nlp.stanford.edu/projects/natlog.html) semantics. This is useful for many shallow logical reasoning tasks; most notably the Open IE annotator. The annotator places an `OperatorAnnotation` on tokens which are quantifiers (or other natural logic operators), and a `PolarityAnnotation` on all tokens in the sentence. 

For example, for the sentence "*all cats have tails*", the annotator would mark *all* as a quantifier with subject scope [1, 2) and object scope [2, 4). In addition, it would mark *cats* as a downward-polarity token, and all other tokens as upwards polarity.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| natlog | NaturalLogicAnnotator | OperatorAnnotation, PolarityAnnotation |

## Options

* natlog.dopolarity: True by default. If set to false, the annotator will only annotate quantifiers and quantifier scopes, and not annotate the polarity of each token.
