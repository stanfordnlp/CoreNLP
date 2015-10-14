---
title: POSTaggerAnnotator 
keywords: pos
permalink: '/pos.html'
---

## Description

Labels tokens with their POS tag. For more details see [this page](http://nlp.stanford.edu/software/tagger.shtml). 

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| pos | POSTaggerAnnotator | PartOfSpeechAnnotation |

## Options

* pos.model: POS model to use. There is no need to explicitly set this option, unless you want to use a different POS model (for advanced developers only). By default, this is set to the english left3words POS model included in the stanford-corenlp-models JAR file.
* pos.maxlen: Maximum sentence size for the POS sequence tagger.  Useful to control the speed of the tagger on noisy text without punctuation marks.  Note that the parser, if used, will be much more expensive than the tagger.

## Caseless model

It is possible to run StanfordCoreNLP with a POS tagger
model that ignores capitalization.  In order to do this, download the
[caseless models](http://nlp.stanford.edu/software/stanford-corenlp-caseless-2015-04-20-models.jar) package.  Be sure to include the path to the case
insensitive models jar in the `-cp` classpath flag as well.

```
-pos.model edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger
```

## More information 

The POS tagger is described in detail on the Stanford NLP [website](http://nlp.stanford.edu/software/tagger.shtml).
