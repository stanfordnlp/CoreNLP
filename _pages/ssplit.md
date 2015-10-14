---
title: WordToSentenceAnnotator
keywords: ssplit
permalink: '/ssplit.html'
---

## Description

Splits a sequence of tokens into sentences.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| ssplit | WordToSentenceAnnotator | SentencesAnnotation |

## Options

* ssplit.eolonly: only split sentences on newlines.  Works well in
conjunction with "-tokenize.whitespace true", in which case
StanfordCoreNLP will treat the input as one sentence per line, only separating
words on whitespace.
* ssplit.isOneSentence: each document is to be treated as one
sentence, no sentence splitting at all.
* ssplit.newlineIsSentenceBreak: Whether to treat newlines as sentence
  breaks.  This property has 3 legal values: "always", "never", or
  "two". The default is "never".  "always" means that a newline is always
  a sentence break (but there still may be multiple sentences per
  line). This is often appropriate for texts with soft line
  breaks. "never" means to ignore newlines for the purpose of sentence
  splitting. This is appropriate when just the non-whitespace
  characters should be used to determine sentence breaks. "two" means
  that two or more consecutive newlines will be 
  treated as a sentence break. This option can be appropriate when
  dealing with text with hard line breaking, and a blank line between paragraphs.
  **Note**: A side-effect of setting ssplit.newlineIsSentenceBreak to "two" or "always" is that tokenizer will tokenize newlines.
    
* ssplit.boundaryMultiTokenRegex: Value is a multi-token sentence
  boundary regex.
* ssplit.boundaryTokenRegex:
* ssplit.boundariesToDiscard:
* ssplit.htmlBoundariesToDiscard
* ssplit.tokenPatternsToDiscard: 
