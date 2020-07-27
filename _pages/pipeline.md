---
layout: page
title: Pipeline
keywords: pipeline
permalink: '/pipeline.html'
nav_order: 4
has_children: true
---

# Pipeline

The centerpiece of CoreNLP is the pipeline. Pipelines take in text or xml and generate full annotation objects.

<p align="center">
   <img src="assets/images/pipeline.png">
</p>

Pipelines are constructed with Properties objects which provide specifications for what annotators to run and
how to customize the annotators.

## Running A Pipeline From The Command Line

You can immediately run a pipeline by issuing the following command:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -file input.txt
```

The output will be stored in the file `input.txt.out`, and will by default contain a human readable presentation
of the annotations.

You can customize your pipeline by providing properties in a properties file.

Here is an example properties file stored at `example.props`:

```bash
# output JSON instead
outputFormat = json

# list of annotators to run
annotators = tokenize,ssplit,pos

# customize the pos model
pos.model = edu/stanford/nlp/models/pos-tagger/english-bidirectional-distsim.tagger
```

Here is the command for running a pipeline with these configurations:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -props example.props -file input.txt
```

This will store JSON output in the file `input.txt.json`.

Of course all of those properties could be specified at the command line as well:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos -pos.model edu/stanford/nlp/models/pos-tagger/english-bidirectional-distsim.tagger -outputFormat json -file input.txt
```

If you want to run a non-English language pipeline, you can just specify the name of one of the CoreNLP supported languages:

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -props french -file french-input.txt
```

## Running A Pipeline In Java Code

Here is a basic demo class showing how to run a pipeline in Java code:

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.pipeline.*;

import java.util.*;


public class PipelineExample {

    public static String text = "Marie was born in Paris.";

    public static void main(String[] args) {
        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        // create a document object
        CoreDocument document = pipeline.processToCoreDocument(text);
    }

}
```

To customize pipelines in Java, add properties to the Properties object in the same way
the `annotators` property is set in the code example.
