---
title: RegexNERAnnotator 
keywords: regexner
permalink: '/regexner.html'
---

## Description

Implements a simple, rule-based NER over token sequences using Java regular expressions. The goal of this Annotator is to provide a simple framework to incorporate NE labels that are not annotated in traditional NL corpora. For example, the default list of regular expressions that we distribute in the models file recognizes ideologies (IDEOLOGY), nationalities (NATIONALITY), religions (RELIGION), and titles (TITLE). Here is [a simple example](http://nlp.stanford.edu/software/regexner/) of how to use RegexNER. For more complex applications, you might consider [TokensRegex](http://nlp.stanford.edu/software/tokensregex.shtml).
| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| regexner | RegexNERAnnotator | NamedEntityTagAnnotation |

## Options

* tokenize.whitespace: if set to true, separates words only when
whitespace is encountered.
* tokenize.options: Accepts the options of `PTBTokenizer` or example, things like "americanize=false" or "strictTreebank3=true,untokenizable=allKeep".


## More information 

RegexNER is described in detail on the Stanford NLP [website](http://nlp.stanford.edu/software/regexner/).
