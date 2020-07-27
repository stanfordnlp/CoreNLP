---
layout: page
title: Dependency Parsing
keywords: depparse, DependencyParseAnnotator, dependency parsing
permalink: '/depparse.html'
nav_order: 12
parent: Pipeline
---

## Description

Provides a fast syntactic dependency parser. We generate three dependency-based outputs, as follows: basic, uncollapsed dependencies, saved in BasicDependenciesAnnotation; enhanced dependencies saved in EnhancedDependenciesAnnotation; and enhanced++ dependencies in EnhancedPlusPlusDependenciesAnnotation. Most users of our parser will prefer the latter representation.

This is a separate annotator for a direct dependency parser. These
parsers require prior part-of-speech tagging. If you need constituency
parses then you should look at the `parse` annotator.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| depparse | DependencyParseAnnotator | BasicDependenciesAnnotation, EnhancedDependenciesAnnotation, EnhancedPlusPlusDependenciesAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| depparse.model | file, classpath, or URL | edu/stanford/nlp/models/parser/nndep/english\_UD.gz | Dependency parsing model to use. There is no need to explicitly set this option, unless you want to use a different parsing model than the default. By default, this is set to the UD parsing model included in the stanford-corenlp-models JAR file. |


## Training a model

Here is an example command for training your own model.  In this example we will train a French dependency parser.

```bash
java -Xmx12g edu.stanford.nlp.parser.nndep.DependencyParser -trainFile fr-ud-train.conllu -devFile fr-ud-dev.conllu -model new-french-UD-model.txt.gz -embedFile wiki.fr.vec -embeddingSize 300 -tlp edu.stanford.nlp.trees.international.french.FrenchTreebankLanguagePack -cPOS
```

* UD train/dev/test data for a variety of languages can be found [here](http://universaldependencies.org/)
* There are many places to find word embedding data, in this example Facebook fastText embeddings are being used, they are found [here](https://github.com/facebookresearch/fastText/blob/master/pretrained-vectors.md)
* Note that you need a tokenizer for your language that matches the tokenization of the UD training files, you may have to reprocess the files to match the tokenizing you plan to use
* Likewise, if you use the `-cPOS` setting, you will have to have POS tags that match the UD training data
* The amount of RAM necessary to train the model may vary depending on various factors

## More information 

For details about the dependency software, see [this page](http://nlp.stanford.edu/software/nndep.html). For more details about dependency parsing in general, see [this page](http://nlp.stanford.edu/software/stanford-dependencies.html).
