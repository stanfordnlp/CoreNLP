---
layout: page
title: CoreNLP API
keywords: api
permalink: '/api.html'
nav_order: 4
parent: Usage
---

## Quickstart (with convenience wrappers)

Below is a quick snippet of code that demonstrates running a full pipeline on some sample text.
It requires the english and english-kbp models jars which contain essential resources.

It uses new wrapper classes that have been developed for Stanford CoreNLP 3.9.0 to make it
easier to work with annotations.

Please note that this new API has not been tested as much as the classic API.  Below this section
is the documentation for the classic pipeline API.

```java
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ie.util.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.*;
import java.util.*;


public class BasicPipelineExample {

  public static String text = "Joe Smith was born in California. " +
      "In 2017, he went to Paris, France in the summer. " +
      "His flight left at 3:00pm on July 10th, 2017. " +
      "After eating some escargot for the first time, Joe said, \"That was delicious!\" " +
      "He sent a postcard to his sister Jane Smith. " +
      "After hearing about Joe's trip, Jane decided she might go to France one day.";

  public static void main(String[] args) {
    // set up pipeline properties
    Properties props = new Properties();
    // set the list of annotators to run
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp,quote");
    // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
    props.setProperty("coref.algorithm", "neural");
    // build pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // create a document object
    CoreDocument document = new CoreDocument(text);
    // annnotate the document
    pipeline.annotate(document);
    // examples

    // 10th token of the document
    CoreLabel token = document.tokens().get(10);
    System.out.println("Example: token");
    System.out.println(token);
    System.out.println();

    // text of the first sentence
    String sentenceText = document.sentences().get(0).text();
    System.out.println("Example: sentence");
    System.out.println(sentenceText);
    System.out.println();

    // second sentence
    CoreSentence sentence = document.sentences().get(1);

    // list of the part-of-speech tags for the second sentence
    List<String> posTags = sentence.posTags();
    System.out.println("Example: pos tags");
    System.out.println(posTags);
    System.out.println();

    // list of the ner tags for the second sentence
    List<String> nerTags = sentence.nerTags();
    System.out.println("Example: ner tags");
    System.out.println(nerTags);
    System.out.println();

    // constituency parse for the second sentence
    Tree constituencyParse = sentence.constituencyParse();
    System.out.println("Example: constituency parse");
    System.out.println(constituencyParse);
    System.out.println();

    // dependency parse for the second sentence
    SemanticGraph dependencyParse = sentence.dependencyParse();
    System.out.println("Example: dependency parse");
    System.out.println(dependencyParse);
    System.out.println();

    // kbp relations found in fifth sentence
    List<RelationTriple> relations =
        document.sentences().get(4).relations();
    System.out.println("Example: relation");
    System.out.println(relations.get(0));
    System.out.println();

    // entity mentions in the second sentence
    List<CoreEntityMention> entityMentions = sentence.entityMentions();
    System.out.println("Example: entity mentions");
    System.out.println(entityMentions);
    System.out.println();

    // coreference between entity mentions
    CoreEntityMention originalEntityMention = document.sentences().get(3).entityMentions().get(1);
    System.out.println("Example: original entity mention");
    System.out.println(originalEntityMention);
    System.out.println("Example: canonical entity mention");
    System.out.println(originalEntityMention.canonicalEntityMention().get());
    System.out.println();

    // get document wide coref info
    Map<Integer, CorefChain> corefChains = document.corefChains();
    System.out.println("Example: coref chains for document");
    System.out.println(corefChains);
    System.out.println();

    // get quotes in document
    List<CoreQuote> quotes = document.quotes();
    CoreQuote quote = quotes.get(0);
    System.out.println("Example: quote");
    System.out.println(quote);
    System.out.println();

    // original speaker of quote
    // note that quote.speaker() returns an Optional
    System.out.println("Example: original speaker of quote");
    System.out.println(quote.speaker().get());
    System.out.println();

    // canonical speaker of quote
    System.out.println("Example: canonical speaker of quote");
    System.out.println(quote.canonicalSpeaker().get());
    System.out.println();

  }

}

```



## Generating annotations

The backbone of the CoreNLP package is formed by two classes: Annotation and Annotator. Annotations are the data structure which hold the results of annotators. Annotations are basically maps, from keys to bits of the annotation, such as the parse, the part-of-speech tags, or named entity tags. Annotators are a lot like functions, except that they operate over Annotations instead of Objects. They do things like tokenize, parse, or NER tag sentences. Annotators and Annotations are integrated by AnnotationPipelines, which create sequences of generic Annotators. Stanford CoreNLP inherits from the AnnotationPipeline class, and is customized with NLP Annotators.

The Annotators currently supported and the Annotations they generate are summarized [here](annotators.html).

To construct a Stanford CoreNLP object from a given set of properties, use `StanfordCoreNLP(Properties props)`. This method creates the pipeline using the annotators given in the "annotators" property (see below for an example setting). The complete list of accepted annotator names is listed in the first column of the table [here](annotators.html). To parse an arbitrary text, use the `annotate(Annotation document)` method.

```java
import edu.stanford.nlp.pipeline.*;
import java.util.*;

public class BasicPipelineExample {

    public static void main(String[] args) {

        // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // read some text in the text variable
        String text = "...";

        // create an empty Annotation just with the given text
        Annotation document = new Annotation(text);

        // run all Annotators on this text
        pipeline.annotate(document);

    }

}
```

You can give other properties to CoreNLP by building a Properties object
with more stuff in it. There are some overall properties like
`"annotators"` but most properties apply to one annotator and are
written as `annotator.property`. Note that **the value of a property is
always a String**. In our documentation of individual annotators, we
variously refer to their Type as "boolean", "file, classpath, or URL"
or "List(String)". This means that the String value will be
interpreted as objects of this type by using String parsing
methods. The value itself in the Properties object is always a String.
If you want to set several properties, you might find it
conventient to use our `PropertiesUtils.asProperties(String ...)`
method which will take a list of Strings that are alternately keys and
values and build a Properties object:

``` java
// build pipeline
StanfordCoreNLP pipeline = new StanfordCoreNLP(
	PropertiesUtils.asProperties(
		"annotators", "tokenize,ssplit,pos,lemma,parse,natlog",
		"ssplit.isOneSentence", "true",
		"parse.model", "edu/stanford/nlp/models/srparser/englishSR.ser.gz",
		"tokenize.language", "en"));

// read some text in the text variable
String text = ... // Add your text here!
Annotation document = new Annotation(text);

// run all Annotators on this text
pipeline.annotate(document);
```

If you do not anticipate requiring extensive customization, consider using the [Simple CoreNLP](simple.html) API.

If you want to do funkier things with CoreNLP, such as to use a second
`StanfordCoreNLP` object to add additional analyses to an existing
`Annotation` object, then you need to include the property
`enforceRequirements = false` to avoid complaints about required
earlier annotators not being present in the pipeline.

## Interpreting the output

The output of the Annotators is accessed using the data structures CoreMap and CoreLabel. 

``` java
// these are all the sentences in this document
// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
List<CoreMap> sentences = document.get(SentencesAnnotation.class);

for(CoreMap sentence: sentences) {
  // traversing the words in the current sentence
  // a CoreLabel is a CoreMap with additional token-specific methods
  for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
    // this is the text of the token
    String word = token.get(TextAnnotation.class);
    // this is the POS tag of the token
    String pos = token.get(PartOfSpeechAnnotation.class);
    // this is the NER label of the token
    String ne = token.get(NamedEntityTagAnnotation.class);
  }

  // this is the parse tree of the current sentence
  Tree tree = sentence.get(TreeAnnotation.class);

  // this is the Stanford dependency graph of the current sentence
  SemanticGraph dependencies = sentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
}

// This is the coreference link graph
// Each chain stores a set of mentions that link to each other,
// along with a method for getting the most representative mention
// Both sentence and token offsets start at 1!
Map<Integer, CorefChain> graph = 
  document.get(CorefChainAnnotation.class);
```

