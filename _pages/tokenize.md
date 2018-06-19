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
| tokenize.language | Enum { English, French, German, Spanish, Unspecified, Whitespace } | Unspecified | Use the appropriate tokenizer for the given language. If the tokenizer is Unspecified, it defaults to using the English PTBTokenizer. |
| tokenize.class | class name | null | If non-null, use this class as the `Tokenizer`. In general, you can now more easily do this by specifying a language to the TokenizerAnnotator. |
| tokenize.whitespace | boolean | false | If set to true, separates words only when whitespace is encountered. |
| tokenize.keepeol | boolean | false | If true, end-of-line tokens are kept and used as sentence boundaries with the WhitespaceTokenizer. |
| tokenize.options | String | null | Accepts the options of `PTBTokenizer` for example, things like "americanize=false" or "strictTreebank3=true,untokenizable=allKeep". See [the PTBTokenizer documentation](http://nlp.stanford.edu/software/tokenizer.html#Options). |
| tokenize.verbose | boolean | false | Make the TokenizerAnnotator verbose - that is, it prints out all tokenizations it performs. |

## Command line

Here are some example usages at the command line

Here is a basic example.

```bash
java -Xmx1g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit -file example.txt -outputFormat text
```

If run on `example.txt`

```
Joe Smith was born in California.
It will probably rain on Friday.
```

You should get this output in `example.txt.out`

```
Document: ID=example.txt (2 sentences, 14 tokens)

Sentence #1 (7 tokens):
Joe Smith was born in California.

Tokens:
[Text=Joe CharacterOffsetBegin=0 CharacterOffsetEnd=3]
[Text=Smith CharacterOffsetBegin=4 CharacterOffsetEnd=9]
[Text=was CharacterOffsetBegin=10 CharacterOffsetEnd=13]
[Text=born CharacterOffsetBegin=14 CharacterOffsetEnd=18]
[Text=in CharacterOffsetBegin=19 CharacterOffsetEnd=21]
[Text=California CharacterOffsetBegin=22 CharacterOffsetEnd=32]
[Text=. CharacterOffsetBegin=32 CharacterOffsetEnd=33]

Sentence #2 (7 tokens):
It will probably rain on Friday.

Tokens:
[Text=It CharacterOffsetBegin=34 CharacterOffsetEnd=36]
[Text=will CharacterOffsetBegin=37 CharacterOffsetEnd=41]
[Text=probably CharacterOffsetBegin=42 CharacterOffsetEnd=50]
[Text=rain CharacterOffsetBegin=51 CharacterOffsetEnd=55]
[Text=on CharacterOffsetBegin=56 CharacterOffsetEnd=58]
[Text=Friday CharacterOffsetBegin=59 CharacterOffsetEnd=65]
[Text=. CharacterOffsetBegin=65 CharacterOffsetEnd=66]
```

## Java API

Here is a demo Java class that will run tokenization on text.  It demonstrates both the new Core Wrapper API and the older Annotation API.

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;

import java.util.*;

public class PipelineDemo {

  public static void main(String[] args) {
    // set up pipeline properties
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit");
    // set up pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // the following has examples for the new Core Wrapper API and the older Annotation API
    // example using Core Wrappers (new API designed to make it easier to work with NLP data)
    System.out.println("---");
    System.out.println("Accessing Tokens In A CoreDocument");
    System.out.println("(text, char offset begin, char offset end)");
    CoreDocument exampleDocument = new CoreDocument("Here is the text to tokenize.");
    // annotate document
    pipeline.annotate(exampleDocument);
    // access tokens from a CoreDocument
    // a token is represented by a CoreLabel
    List<CoreLabel> firstSentenceTokens = exampleDocument.sentences().get(0).tokens();
    // this for loop will print out all of the tokens and the character offset info
    for (CoreLabel token : firstSentenceTokens) {
      System.out.println(token.word() + "\t" + token.beginPosition() + "\t" + token.endPosition());
    }
    // example using older Annotation API
    System.out.println("---");
    System.out.println("Accessing Tokens In An Annotation");
    System.out.println("(text, char offset begin, char offset end)");
    Annotation exampleAnnotation = new Annotation("Here is the text to tokenize.");
    pipeline.annotate(exampleAnnotation);
    CoreMap firstSentence = exampleAnnotation.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    // this for loop will print out all of the tokens and the character offset info
    for (CoreLabel token : firstSentence.get(CoreAnnotations.TokensAnnotation.class)) {
      System.out.println(token.word() + "\t" + token.beginPosition() + "\t" + token.endPosition());
    }
  }
}
```

## More information 

The tokenizer is described in detail on the Stanford NLP [website](http://nlp.stanford.edu/software/tokenizer.html).
