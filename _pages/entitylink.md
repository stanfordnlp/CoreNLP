---
layout: page
title: Entity Linking
keywords: entity linking, entity link, WikidictAnnotator
permalink: '/entitylink.html'
nav_order: 17
parent: Pipeline
---

## Description

Uses a dictionary to match entity mention text to a specific entity in Wikipedia.

For instance the text `Hank Williams` is matched to [Hank Williams](https://en.wikipedia.org/wiki/Hank_Williams).

A useful example of this would be that both the strings `FDR` and `Franklin Delano Roosevelt` are mapped to
the [Franklin D. Roosevelt](https://en.wikipedia.org/wiki/Franklin_D._Roosevelt) page.

The English WikiDict contains 20948089 mappings from Strings to entities.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| entitylink | WikidictAnnotator | WikipediaEntityAnnotation |

## Example Usage

### Command Line

```
java -Xmx16g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner,entitylink -file example.txt
```

## Options

| Option name | Type | Description | Default |
| --- | --- | ----- | --- |
| entitylink.wikidict | file, classpath, or URL | Path of wikidict to use. | .../models/kbp/english/wikidict.tab.gz |
| entitylink.caseless | boolean | Ignore case when matching (e.g. `barack obama` and `Barack Obama` will map to the same thing. | false |

* There are currently dictionaries for `Chinese, English, Spanish`.

## More information 

The dictionary construction process is described in more detail in the paper [A Cross-Lingual Dictionary for English Wikipedia Concepts](https://nlp.stanford.edu/pubs/crosswikis.pdf)
