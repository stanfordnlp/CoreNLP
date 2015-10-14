---
title: Simple CoreNLP
keywords: simple
permalink: '/simple.html'
---

### Simple CoreNLP

In addition to the fully-featured annotator pipeline interface to CoreNLP, Stanford provides a simple API for users who do not need a lot of customization. The intended audience of this package is users of CoreNLP who want "`import nlp`" to work as fast and easily as possible, and do not care about the details of the behaviors of the algorithms.

An example usage is given below:

```java
import edu.stanford.nlp.simple.*

Sentence sent = new Sentence("Lucy is in the sky with diamonds.");
List<String> nerTags = sent.ners();  // [PERSON, O, O, O, O, O, O, O]
String firstLemma = sent.lemma(0);   // Lucy
...
```

### Advantages and Disadvantages

This interface offers a number of advantages (and a few disadvantages -- see below) over the default annotator pipeline:

  * __Intuitive Syntax__ Conceptually, documents and sentences are stored as objects, and have functions corresponding to annotations you would like to retrieve from them.

  * __Lazy Computation__ Annotations are run as needed only when requested. This allows you to "change your mind" later in a program and request new annotations.

  * __No `NullPointerException`s__ Lazy computation allows us to ensure that no function will ever return null. Items which may not exist are wrapped inside of an `Optional` to clearly mark that they may be empty.

  * __Fast, Robust Serialization__ All objects are backed by [protocol buffers](https://developers.google.com/protocol-buffers/?hl=en), meaning that serialization and deserialization is both very easy and very fast. In addition to being easily readable from other languages, our experiments show this to be over an order of magnitude faster than the default Java serialization.

  * __Maintains Thread Safety__ Like the CoreNLP pipeline, this wrapper is threadsafe.

In exchange for these advantages, users should be aware of a few disadvantages:

  * __Less Customizability__ Although the ability to pass properties to annotators is supported, it is significantly more clunky than the annotation pipeline interface, and is generally discouraged.
  
  * __Possible Nondeterminism__ There is no guarantee that the same algorithm will be used to compute the requested function on each invocation. For example, if a dependency parse is requested, followed by a constituency parse, we will compute the dependency parse with the [Neural Dependency Parser](http://nlp.stanford.edu/software/nndep.shtml), and then use the [Stanford Parser](http://nlp.stanford.edu/software/lex-parser.shtml) for the constituency parse. If, however, you request the constituency parse before the dependency parse, we will use the Stanford Parser for both.

### Usage

There are two main classes in the interface: `Document` and `Sentence`. Tokens are represented as array elements in a sentence; e.g., to get the lemma of a token, get the lemmas array from the sentence and index it at the appropriate index. A constructor is provided for both the `Document` and `Sentence` class. For the former, the text is treated as an entire document containing potentially multiple sentences. For the latter, the text is forced to be interpreted as a single sentence.

An example program using the interface is given below:

```java
import edu.stanford.nlp.simple.*;

public class SimpleCoreNLPDemo {
    public static void main(String[] args) { 
        // Create a document. No computation is done yet.
        Document doc = new Document("add your text here! It can contain multiple sentences.");
        for (Sentence sent : doc.sentences()) {  // Will iterate over two sentences
            // We're only asking for words -- no need to load any models yet
            System.out.println("The second word of the sentence '" + sent + "' is " + sent.word(1));
            // When we ask for the lemma, it will load and run the part of speech tagger
            System.out.println("The third lemma of the sentence '" + sent + "' is " + sent.lemma(2));
            // When we ask for the parse, it will load and run the parser
            System.out.println("The parse of the sentence '" + sent + "' is " + sent.parse());
            // ...
        }
    }
}
```

### Supported Annotators

The interface is not guaranteed to support all of the annotators in the CoreNLP pipeline. However, most common annotators are supported. A list of these, and their invocation, is given below. Functionality is the plain-english description of the task to be performed. The second column lists the analogous CoreNLP annotator for that task. The implementing class and function describe the class and function used in this wrapper to perform the same tasks.

| Functionality               | Annotator in CoreNLP | Implementing Class      | Function                         |
| --------------------------- | :------------------: | ----------------------- | -------------------------------- |
| Tokenization                | `tokenize`           | `Sentence`              | `.words()`                       |
| Sentence Splitting          | `ssplit`             | `Document`              | `.sentences()`                   |
| Part of Speech Tagging      | `pos`                | `Sentence`              | `.posTags()` / `.posTag(int)`    |
| Lemmatization               | `lemma`              | `Sentence`              | `.lemmas()` / `.lemma(int)`      |
| Named Entity Recognition    | `lemma`              | `Sentence`              | `.nerTags()` / `.nerTag(int)`    |
| Constituency Parsing        | `parse`              | `Sentence`              | `.parse()`                       |
| Dependency Parsing          | `depparse`           | `Sentence`              | `.governor(int)` / `.incomingDependencyLabel(int)` |
| Coreference Resolution      | `dcoref`             | `Document`              | `.coref()`                       |
| Natural Logic Polarity      | `natlog`             | `Sentence`              | `.natlogPolarities()` / `natlogPolarity(int)` |
| Open Information Extraction | `openie`             | `Sentence`              | `.openie()` / `.openieTriples()` |

Patches for incorporating additional annotators are of course always welcome!

### Miscellaneous Extras

Some potentially useful utility functions are implemented in the `SentenceAlgorithms` class. These can be called from a `Sentence` object with, e.g.:

```java
Sentence sent = new Sentence("your text should go here");
sent.algorithms().headOfSpan(new Span(0, 2));  // Should return 1
```

A selection of useful algorithms are:

  * __`headOfSpan(Span)`__ Finds the index of the head word of the given span. So, for example, _United States president Barack Obama_ would return _Obama_.

  * __`dependencyPathBetween(int, int)`__ Returns the dependency path between the words at the given two indices. This is returned as a list of `String` objects, meant primarily as an input to a featurizer.

