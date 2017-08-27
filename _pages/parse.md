---
title: ParserAnnotator 
keywords: parse
permalink: '/parse.html'
---

## Description

Provides full syntactic analysis, minimally a constituency (phrase-structure tree) parse of sentences. If a rule-based conversion from constituency parses to dependency parses is available (this is currently the case for English and Chinese, only), then a dependency representation is also generated using this conversion. The constituent-based output is saved in TreeAnnotation. We generate three dependency-based outputs, as follows: basic dependencies, saved in BasicDependenciesAnnotation; enhanced dependencies saved in EnhancedDependenciesAnnotation; and enhanced++ dependencies in EnhancedPlusPlusDependenciesAnnotation. Most users of our parser will prefer the latter representation.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| parse | ParserAnnotator | TreeAnnotation, BasicDependenciesAnnotation, EnhancedDependenciesAnnotation, EnhancedPlusPlusDependenciesAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| parse.model | String | edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz | Which parsing model to use. There is usually no need to explicitly set this option, unless you want to use a different parsing model (for advanced developers only). By default, this is set to the parsing model included in the stanford-corenlp-models JAR file. |
| parse.debug | boolean | false | Whether to print verbose messages while parsing. |
| parse.flags | String | -retainTmpSubcategories | Flags to use when loading the parser model.  The default English model  "-retainTmpSubcategories"; other languages have an empty String default. The value is a whitespace separated list of flags and any arguments, just as would be passed on the command line. In particular, these are flags not properties, and an initial dash should be included. |
| parse.maxlen | integer | -1 | If set to a positive number, the annotator parses only sentences of length at most this number (in terms of number of tokens). For longer sentences, the parser creates a flat structure, where every token is assigned to the non-terminal X. This is useful when parsing noisy web text, which may generate arbitrarily long sentences. |
| parse.treemap | class | null | If set to the String name of a Java class that is a Function&lt;Tree,Tree&lt;, then this function will be applied to each tree output by the parser. |
| parse.maxtime | long | 0 | This was supposed to be for a time-based cutoff for parsing, but it is currently unimplemented. |
| parse.kbest | integer | 0 | If a positive number, store the k-best parses in `KBestTreesAnnotation`. Note that this option only has an effect if you parse sentences with a PCFG model. |
| parse.originalDependencies | boolean | false | Generate original Stanford Dependencies grammatical relations instead of Universal Dependencies. Note, however, that some annotators that use dependencies such as natlog might not function properly if you use this option.  If you are using the [Neural Network dependency parser](http://nlp.stanford.edu/software/nndep.html) and want to get the original SD relations, see the [CoreNLP FAQ](faq.html#how-can-i-get-original-stanford-dependencies-instead-of-universal-dependencies) on how to use a model trained on Stanford Dependencies. |
| parse.buildgraphs | boolean | true | |
| parse.nthreads | | | |
| parse.nosquash | | | |
| parse.keepPunct | boolean | | A boolean option on whether to keep punctuation depenencies in the dependency parse output of the parser. |
| parse.extradependencies | | | |
| parse.binaryTrees  | | | |

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

Details on how to use it are available on the [shift reduce parser](http://nlp.stanford.edu/software/srparser.html) page.

## PoS tagging

All of our parsers make use of parts of speech. Some of the models (e.g., neural dependency parser and shift-reduce parser) require an external PoS tagger; you must specify the `pos` annotator. Other parsers, such as the PCFG and Factored parsers can either do their own PoS tagging or use an external PoS tagger as a preprocessor. If you want to use a parser as a preprocessor, make sure you do *not* include `pos` in the list of annotators and position the annotator `parse` prior to any other annotator that requires part-of-speech information (such as `lemma`):
```
-annotators tokenize,ssplit,parse,lemma,ner
```
In general: these parsers are good POS taggers; they are not quite as accurate as the supplied maxent POS tagger in terms of overall token accuracy; however, they often do better in more “grammar-based” decision making, where broader syntactic context is useful, such as for distinguishing finite and non-finite verb forms.

## More information 

For more details on the original parsers, please see [this page](http://nlp.stanford.edu/software/lex-parser.html). 
There is also a page on the [shift reduce parser](http://nlp.stanford.edu/software/srparser.html).
For more details about Stanford dependencies, please refer to [this page](http://nlp.stanford.edu/software/stanford-dependencies.html).
A separate site documents [Universal Dependencies](http://universaldependencies.org/).
