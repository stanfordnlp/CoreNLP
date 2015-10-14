---
title: Caseless models
keywords: caseless
permalink: '/caseless.html'
---

It is possible to run StanfordCoreNLP with tagger, parser, and NER
models that ignore capitalization.  In order to do this, download the
[caseless models](http://nlp.stanford.edu/software/stanford-corenlp-caseless-2015-04-20-models.jar) package.  Be sure to include the path to the case
insensitive models jar in the `-cp` classpath flag as well.

```
-pos.model edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger
-parse.model edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz
-ner.model edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz
   edu/stanford/nlp/models/ner/english.muc.7class.caseless.distsim.crf.ser.gz
   edu/stanford/nlp/models/ner/english.conll.4class.caseless.distsim.crf.ser.gz
```
