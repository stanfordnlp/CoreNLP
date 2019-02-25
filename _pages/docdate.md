---
title: DocDateAnnotator 
keywords: docdate
permalink: '/docdate.html'
---

## Description

Provides several methods for setting the date of documents.  As of version 3.9.2 this annotator must be used as 
a sub-annotator of `ner`, or as a custom annotator.  Its main purpose is to provide document dates for SUTime, 
which is generally used as a sub-annotator of `ner` as well.  If you wish to set document dates without running
`ner` you can specify `docdate` as a custom annotator.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| docdate | DocDateAnnotator | DocDateAnnotation |

## Example Usage

### Command Line

```
# as a custom annotator
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -customAnnotatorClass.docdate edu.stanford.nlp.pipeline.DocDateAnnotator -annotators tokenize,ssplit,docdate -docdate.useFixedDate 2019-01-01 -file example.txt
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

