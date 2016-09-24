---
title: ParserAnnotator 
keywords: parse
permalink: '/parse.html'
---

## Description

Provides full syntactic analysis, using both the constituent and the dependency representations. The constituent-based output is saved in TreeAnnotation. We generate three dependency-based outputs, as follows: basic, uncollapsed dependencies, saved in BasicDependenciesAnnotation; enhanced dependencies saved in EnhancedDependenciesAnnotation; and enhanced++ dependencies in EnhancedPlusPlusDependenciesAnnotation. Most users of our parser will prefer the latter representation.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| parse | ParserAnnotator | TreeAnnotation, BasicDependenciesAnnotation, EnhancedDependenciesAnnotation, EnhancedPlusPlusDependenciesAnnotation |

## Options


* parse.model: parsing model to use. There is no need to explicitly set this option, unless you want to use a different parsing model (for advanced developers only). By default, this is set to the parsing model included in the stanford-corenlp-models JAR file.
* parse.maxlen: if set, the annotator parses only sentences shorter (in terms of number of tokens) than this number. For longer sentences, the parser creates a flat structure, where every token is assigned to the non-terminal X. This is useful when parsing noisy web text, which may generate arbitrarily long sentences. By default, this option is not set.
* parse.flags: flags to use when loading the parser model.  The English model used by default uses "-retainTmpSubcategories"
* parse.originalDependencies: Generate original Stanford Dependencies grammatical relations instead of Universal Dependencies. Note, however, that some annotators that use dependencies such as natlog might not function properly if you use this option.  If you are using the [Neural Network dependency parser](http://nlp.stanford.edu/software/nndep.shtml) and want to get the original SD relations, see the [CoreNLP FAQ](faq.html#how-can-i-get-original-stanford-dependencies-instead-of-universal-dependencies) on how to use a model trained on Stanford Dependencies.
* parse.kbest Store the k-best parses in `KBestTreesAnnotation`. Note that this option only has an effect if you parse sentences with a PCFG model.


## Caseless models

It is possible to run StanfordCoreNLP with a parser
model that ignores capitalization. We have trained models like this
for English. You can find details on the
[Caseless models](caseless.html) page.


## Shift-reduce parser

There is a much faster and more memory efficient parser available in
the shift reduce parser.  It takes quite a while to load, and the
download is much larger, which is the main reason it is not the
default.

Details on how to use it are available on the [shift reduce parser](http://nlp.stanford.edu/software/srparser.shtml) page.

## More information 

For more details on the parser, please see [this page](http://nlp.stanford.edu/software/lex-parser.shtml). For more details about the dependencies, please refer to [this page](http://nlp.stanford.edu/software/stanford-dependencies.shtml).
