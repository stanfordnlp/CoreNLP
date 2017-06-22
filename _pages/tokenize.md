---
title: TokenizerAnnotator 
keywords: tokenize
permalink: '/tokenize.html'
---

## Description

Tokenizes the text. This component started as a PTB-style tokenizer, but was extended since then to handle both other languages and noisy web-style text. The tokenizer saves the character offsets of each token in the input text, as CharacterOffsetBeginAnnotation and CharacterOffsetEndAnnotation.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| tokenize | TokenizerAnnotator | TokensAnnotation (list of tokens), and CharacterOffsetBeginAnnotation, CharacterOffsetEndAnnotation, TextAnnotation (for each token) | 

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| tokenize.language | Enum { English, French, German, Spanish, Unspecified, Whitesapce } | Unspecified | Use the appropriate tokenizer for the given language. If the tokenizer is Unspecified, it defaults to using the English PTBTokenizer. |
| tokenize.class | class name | null | If non-null, use this class as the `Tokenizer`. In general, you can now more easily do this by specifying a language to the TokenizerAnnotator. |
| tokenize.whitespace | boolean | false | If set to true, separates words only when whitespace is encountered. |
| tokenize.keepeol | boolean | false | If true, end-of-line tokens are kept and used as sentence boundaries with the WhitespaceTokenizer. |
| tokenize.options | String | null | Accepts the options of `PTBTokenizer` for example, things like "americanize=false" or "strictTreebank3=true,untokenizable=allKeep". See [the PTBTokenizer documentation](http://nlp.stanford.edu/software/tokenizer.html#Options). |
| tokenize.verbose | boolean | false | Make the TokenizerAnnotator verbose - that is, it prints out all tokenizations it performs. |


## More information 

The tokenizer is described in detail on the Stanford NLP [website](http://nlp.stanford.edu/software/tokenizer.html).
