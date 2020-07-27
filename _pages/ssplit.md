---
layout: page
title: Sentence Splitting
keywords: ssplit, WordToSentencesAnnotator 
permalink: '/ssplit.html'
nav_order: 7
parent: Pipeline
---

## Description

Sentence splitting is the process of dividing text into sentences. For instance the document `Hello world. Hello world again.` would be split into the sentences
`Hello world.` and `Hello world again.` CoreNLP splits documents into sentences via a set of rules.

| Name | Annotator class name | Requirement | Generated Annotation | Description |
| --- | --- | --- | --- | --- |
| ssplit | WordToSentencesAnnotator | TokensAnnotation | SentencesAnnotation | Splits text into sentences. |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| ssplit.eolonly | Boolean | false | Split sentences at and only at newlines. Suitable for input such as many machine translation datasets which are already formatted to be treated as strictly one sentence per line. Works well in conjunction with `-tokenize.whitespace true`, in which case StanfordCoreNLP will treat the input as already tokenized and one sentence per line, only separating words on whitespace. |
| ssplit.isOneSentence | Boolean | false | Each document is to be treated as one sentence, with no sentence splitting at all. |
| ssplit.newlineIsSentenceBreak | Enum { always, never, two } | never | Whether to treat newlines as sentence breaks.  This property has 3 legal values. "always" means that a newline is always a sentence break (but there still may be multiple sentences per line). This is usually appropriate for texts with soft line breaks. "never" means to ignore newlines for the purpose of sentence splitting. This is appropriate for continuous text with hard line breaks, when just the non-whitespace characters should be used to determine sentence breaks. "two" means that two or more consecutive newlines will be treated as a sentence break. This option is appropriate when dealing with text with hard line breaks and a blank line between paragraphs. **Note**: A side-effect of setting ssplit.newlineIsSentenceBreak to "two" or "always" is that the tokenizer will tokenize newlines. |
| ssplit.boundaryMultiTokenRegex | Regex | null | If non-null, value is a multi-token regex, that is, a `tokensregex` expression, that will match something to be treated as a sentence boundary. For example, `ssplit.boundaryMultiTokenRegex = /(?:\\n|\\*NL\\*)/{2,}` is basically equivalent to `ssplit.newlineIsSentenceBreak = two`. The matched tokens will be treated as not part of the following sentence. They will be discarded if and only if they also match `ssplit.boundariesToDiscard` |
| ssplit.boundaryTokenRegex | Regex | `\\.|[!?]+` | If non-null, value is a regex for regular sentence boundary tokens; otherwise the default is used. For example, for Chinese, a possible setting might be: `ssplit.boundaryTokenRegex = [.。]|[!?！？]+`. |
| ssplit.boundariesToDiscard | List(String) | null | If non-null value is a String which contains a comma-separated list of String tokens that will be treated as sentence boundaries (when matched with String equality) and then discarded. For example, it might be `ssplit.boundariesToDiscard = <p>,<P>,</p>,</P>`. |
| ssplit.htmlBoundariesToDiscard | String | null | If non-null, value is a String which contains a comma-separated list of XML element names that will be treated as sentence boundaries (when matched with String equality), and then discarded. For example, it might be: `htmlBoundariesToDiscard = p,text,post,postdate,poster,turn,speaker,quote`. Note that this functionality overlaps, but is less flexible than, functionality that is available in the `cleanxml` annotator. |
| ssplit.tokenPatternsToDiscard | List(Regex) | null | If non-null, value is a comma-separated list of regex for tokens to discard without marking them as sentence boundaries. |
| ssplit.boundaryFollowersRegex | Regex | null | If non-null, value is a regex for tokens to allow to be part of the preceding sentence following something that matches `ssplit.boundaryTokenRegex` (but not something after a newline or XML sentence break). |

## Sentence Splitting From The Command Line

This command will take in the text of the file `input.txt` and produce a human readable output of the sentences:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit -file input.txt
```

Other output formats include `conllu`, `conll`, `json`, and `serialized`.

The following command shows an example of customizing sentence splitting. Here we will tell the annotator to only split on newlines, meaning the file
is one sentence per line:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit -ssplit.eolonly -file input.txt
```

## Sentence Splitting From Java

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.pipeline.*;

import java.util.*;

public class SentenceSplittingExample {

  public static String text = "Hello world. Hello world again.";

  public static void main(String[] args) {
    // set up pipeline properties
    Properties props = new Properties();
    // set the list of annotators to run
    props.setProperty("annotators", "tokenize,ssplit");
    // build pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // create a document object
    CoreDocument doc = new CoreDocument(text);
    // annotate
    pipeline.annotate(doc);
    // display sentences
    for (CoreSentence sent : doc.sentences()) {
        System.out.println(sent.text());
    }
  }
}
```

This demo code will produce this output:

```
Hello world.
Hello world again.
```
