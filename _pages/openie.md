---
title: OpenIE
keywords: openie
permalink: '/openie.html'
---

## Description

Extracts open-domain relation triples, representing a subject, a relation, and the
object of the relation.
This is useful for 
  (1) relation extraction tasks where there is limited or no training
data, and it is easy to extract the information required from such open domain triples;
and, 
  (2) when speed is essential. The system can process around 100 sentences per second
  per CPU core.
Relation triples are stored on the `RelationTripleAnnotation` key of a `CoreMap`
(i.e., sentence).

In addition to extracting relation triples, the annotator produces 
a number of sentence fragments corresponding to entailed fragments 
from the given original sentence.
These are stored on the `EntailedSentencesAnnotation` key of a `CoreMap` 
(i.e., sentence).


| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| openie | OpenIE | EntailedSentencesAnnotation, RelationTriplesAnnotation |


## Options

* `openie.format`	{reverb, ollie, default}: Change the output format of the program. Default will produce tab separated columns for confidence, the subject, relation, and the object of a relation. ReVerb will output a TSV in the ReVerb format. Ollie will output relations in the default format returned by Ollie.
* `openie.filelist`	/path/to/filelist:	A path to a file, which contains files to annotate. Each file should be on its own line. If this option is set, only these files are annotated and the files passed via bare arguments are ignored.
* `openie.threads`	integer:	The number of threads to run on. By default, this is the number of threads on the system.
* `openie.max_entailments_per_clause`	integer:	The maximum number of entailments to produce for each clause extracted in the sentence. The larger this value is, the slower the system will run, but the more relations it can potentially extract. Setting this below 100 is not recommended; setting it above 1000 is likewise not recommended.
* `openie.resolve_coref` boolean: If true, run coreference (and consequently NER as a dependency of coreference) and replace pronominal mentions with their canonical mention in the text.
* `openie.ignore_affinity`	boolean:	Ignore the affinity model for prepositional attachments.
* `openie.affinity_probability_cap`	double:	The affinity value above which confidence of the extraction is taken as 1.0. Default is 1/3.
* `openie.triple.strict`	boolean:	If true (the default), extract triples only if they consume the entire fragment. This is useful for ensuring that only logically warranted triples are extracted, but puts more burden on the entailment system to find minimal phrases (see -max_entailments_per_clause).
* `openie.triple.all_nominals`	boolean:	If true, extract nominal relations always and not only when a named entity tag warrants it. This greatly overproduces such triples, but can be useful in certain situations.

Some additional options are provided to fine-tune the inner workings of the
OpenIE system.
These should be changed only in very rare situations; for example, if you are
developing extensions to the system itself.

* `openie.splitter.model`	/path/to/model.ser.gz:	You can override the default location of the clause splitting model with this option.
* `openie.splitter.nomodel`:		Run without a clause splitting model -- that is, split on every clause.
* `openie.splitter.disable`:		Don't split clauses at all, and only extract relations centered around the root verb.
* `openie.affinity_model`	/path/to/model_dir:	A custom location to read the affinity models from.


## Usage

The OpenIE system can be run both through the command line, and through the
CoreNLP API

### Command Line
An interactive command-line shell can be run with the command:

```bash
java -mx1g -cp stanford-corenlp-<version>.jar:stanford-corenlp-models-<version>.jar edu.stanford.nlp.naturalli.OpenIE
```

In addition, the program can be run on a collection of files either by passing the
files directly as command-line arguments:

```bash
java -mx1g -cp stanford-corenlp-<version>.jar:stanford-corenlp-models-<version>.jar edu.stanford.nlp.naturalli.OpenIE  /path/to/file1  /path/to/file2 
```

or by setting the `-filelist` argument to a file containing a list of files to annotate,
one per line:

```bash
java -mx1g -cp stanford-corenlp-<version>.jar:stanford-corenlp-models-<version>.jar edu.stanford.nlp.naturalli.OpenIE  -filelist /path/to/filelist
```


### API
Relation triples can be accessed through the CoreNLP API through the standard
annotation pipeline.
An example class which does this is given below:

```java
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.util.Collection;
import java.util.Properties;

/**
 * A demo illustrating how to call the OpenIE system programmatically.
 */
public class OpenIEDemo {

  public static void main(String[] args) throws Exception {
    // Create the Stanford CoreNLP pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,depparse,natlog,openie");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    // Annotate an example document.
    Annotation doc = new Annotation("Obama was born in Hawaii. He is our president.");
    pipeline.annotate(doc);

    // Loop over sentences in the document
    for (CoreMap sentence : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      // Get the OpenIE triples for the sentence
      Collection<RelationTriple> triples = sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
      // Print the triples
      for (RelationTriple triple : triples) {
        System.out.println(triple.confidence + "\t" +
            triple.subjectLemmaGloss() + "\t" +
            triple.relationLemmaGloss() + "\t" +
            triple.objectLemmaGloss());
      }
    }
  }
}
```


### Simple CoreNLP API
The Simple CoreNLP API includes bindings for the OpenIE system,
via the method `Sentence.openieTriples()`.
An example usage is given below:

```java
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.simple.*;

/**
 * A demo illustrating how to call the OpenIE system programmatically.
 */
public class OpenIEDemo {

  public static void main(String[] args) throws Exception {
    // Create a CoreNLP document
    Document doc = new Document("Obama was born in Hawaii. He is our president.");

    // Iterate over the sentences in the document
    for (Sentence sent : doc.sentences()) {
      // Iterate over the triples in the sentence
      for (RelationTriple triple : sent.openieTriples()) {
        // Print the triple
        System.out.println(triple.confidence + "\t" +
            triple.subjectLemmaGloss() + "\t" +
            triple.relationLemmaGloss() + "\t" +
            triple.objectLemmaGloss());
      }
    }
  }
}
```

## More Information
More information can be found on the 
[Open IE homepage](http://nlp.stanford.edu/software/openie.shtml).

