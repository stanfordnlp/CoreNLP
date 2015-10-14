---
title: TokenizerAnnotator 
keywords: tokenize
permalink: '/tokenize.html'
---

## Description

Tokenizes the text. This component started as a PTB-style tokenizer, but was extended since then to handle noisy and web text. The tokenizer saves the character offsets of each token in the input text, as CharacterOffsetBeginAnnotation and CharacterOffsetEndAnnotation.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| tokenize | TokenizerAnnotator | TokensAnnotation (list of tokens), and CharacterOffsetBeginAnnotation, CharacterOffsetEndAnnotation, TextAnnotation (for each token) | 

## Options

* tokenize.whitespace: if set to true, separates words only when
whitespace is encountered.
* tokenize.options: Accepts the options of `PTBTokenizer` or example, things like "americanize=false" or "strictTreebank3=true,untokenizable=allKeep".


## More information 

The tokenizer is described in detail on the Stanford NLP [website](http://nlp.stanford.edu/software/tokenizer.shtml).
