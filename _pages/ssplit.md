---
title: WordsToSentenceAnnotator
keywords: ssplit
permalink: '/ssplit.html'
---

## Description

Splits a sequence of tokens into sentences. (In current CoreNLP, sentence splitting comes after tokenization, and exploits the decisions of the tokenizer.)

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| ssplit | WordsToSentenceAnnotator | SentencesAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| ssplit.eolonly | Boolean | false | Split sentences at and only at newlines. Suitable for input such as many machine translation datasets which are already formatted to be treated as strictly one sentence per line. Works well in conjunction with `-tokenize.whitespace true`, in which case StanfordCoreNLP will treat the input as already tokenized and one sentence per line, only separating words on whitespace. |
| ssplit.isOneSentence | Boolean | false | Each document is to be treated as one sentence, with no sentence splitting at all. |
| ssplit.newlineIsSentenceBreak | Enum { always, never, two } | never | Whether to treat newlines as sentence breaks.  This property has 3 legal values. "always" means that a newline is always a sentence break (but there still may be multiple sentences per line). This is usually appropriate for texts with soft line breaks. "never" means to ignore newlines for the purpose of sentence splitting. This is appropriate for continuous text with hard line breaks, when just the non-whitespace characters should be used to determine sentence breaks. "two" means that two or more consecutive newlines will be treated as a sentence break. This option is appropriate when dealing with text with hard line breaks and a blank line between paragraphs. **Note**: A side-effect of setting ssplit.newlineIsSentenceBreak to "two" or "always" is that the tokenizer will tokenize newlines. |
| ssplit.boundaryMultiTokenRegex | Regex | null | If non-null, value is a multi-token regex that will match sentence boundaries. |
| ssplit.boundaryTokenRegex | Regex | null | If non-null, value is a regex for regular sentence boundary tokens. |
| ssplit.boundariesToDiscard | Regex | null | If non-null value is a regex for newline boundaries which are discarded. |
| ssplit.htmlBoundariesToDiscard | Regex | null | If non-null, value is a regex for HTML tokens treated as boundaries, which are discarded. |
| ssplit.tokenPatternsToDiscard | Regex | null | If non-null, value is a regex for toekns to discard without marking them as sentence boundaries. |
