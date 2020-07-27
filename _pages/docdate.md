---
layout: page
title: Document Date
keywords: docdate
permalink: '/docdate.html'
nav_order: 1
parent: Full List Of Annotators
---

## Description

Provides several methods for setting the date of documents. One can use the standalone `docdate` annotator
or use the sub-annotator `ner.docdate` that is contained by the `ner` annotator. If using the sub-annotator
in `ner` do not also use the standalone annotator.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| docdate | DocDateAnnotator | DocDateAnnotation |

## Example Usage

### Command Line

```
# as a standalone annotator
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP edu.stanford.nlp.pipeline.DocDateAnnotator -annotators tokenize,ssplit,docdate -docdate.useFixedDate 2019-01-01 -file example.txt
```

```
# as a sub-annotator of ner
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -ner.docdate.useFixedDate 2019-01-01 -file example.txt
```

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| docdate.useFixedDate | String | - | Set every document to have a fixed date (e.g. 2019-01-01) |
| docdate.useMappingFile | file, classpath, or URL | - | Use a tab-delimited file to specify doc dates. First column is document ID, second column is date. |
| docdate.usePresent | - | - | Set every document to have the present date as the date. |
| docdate.useRegex | String | - | Specify a regular expression matching file names. The first group will be extracted as the date. (e.g. NYT-([0-9]{4}-[0-9]{2}-[0-9]{2}).xml ) |

