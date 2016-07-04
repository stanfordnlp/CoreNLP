---
title: NERClassifierCombiner 
keywords: ner
permalink: '/ner.html'
---

## Description

Recognizes named (PERSON, LOCATION, ORGANIZATION, MISC), numerical (MONEY, NUMBER, ORDINAL, PERCENT), and temporal (DATE, TIME, DURATION, SET) entities. Named entities are recognized using a combination of three CRF sequence taggers trained on various corpora, such as ACE and MUC. Numerical entities are recognized using a rule-based system. Numerical entities that require normalization, e.g., dates, are normalized to NormalizedNamedEntityTagAnnotation.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| ner | NERClassifierCombiner | NamedEntityTagAnnotation and NormalizedNamedEntityTagAnnotation |

## Options

* ner.useSUTime: Whether or not to use sutime.  On by default in the version which includes sutime, off by default in the version that doesn't.  If not processing English, make sure to set this to false.
* ner.model: NER model(s) in a comma separated list to use instead of the default models.  By default, the models used will be the 3class, 7class, and MISCclass models, in that order.
* ner.applyNumericClassifiers: Whether or not to use numeric classifiers, including [SUTime](http://nlp.stanford.edu/software/regexner/).  These are hardcoded for English, so if using a different language, this should be set to false.
* sutime.markTimeRanges: Tells sutime to mark phrases such as "From January to March" instead of marking "January" and "March" separately
* sutime.includeRange: If marking time ranges, set the time range in the TIMEX output from sutime

## SUTime

StanfordCoreNLP includes [SUTime](http://nlp.stanford.edu/software/sutime.shtml), Stanford's temporal expression
recognizer. SUTime is transparently called from the "ner" annotator,
so no configuration is necessary. Furthermore, the "cleanxml"
annotator now extracts the reference date for a given XML document, so
relative dates, e.g., "yesterday", are transparently normalized with
no configuration necessary.

SUTime supports the same annotations as before, i.e.,
NamedEntityTagAnnotation is set with the label of the numeric entity (DATE,
TIME, DURATION, MONEY, PERCENT, or NUMBER) and
NormalizedNamedEntityTagAnnotation is set to the value of the normalized
temporal expression. Note that NormalizedNamedEntityTagAnnotation now
follows the TIMEX3 standard, rather than Stanford's internal representation,
e.g., "2010-01-01" for the string "January 1, 2010", rather than "20100101".

Also, SUTime now sets the TimexAnnotation key to an
edu.stanford.nlp.time.Timex object, which contains the complete list of
TIMEX3 fields for the corresponding expressions, such as "val", "alt_val",
"type", "tid". This might be useful to developers interested in recovering
complete TIMEX3 expressions.

Reference dates are by default extracted from the "datetime" and
"date" tags in an xml document.  To set a different set of tags to
use, use the clean.datetags property.  When using the API, reference
dates can be added to an `Annotation` via
`edu.stanford.nlp.ling.CoreAnnotations.DocDateAnnotation`,
although note that when processing an xml document, the cleanxml
annotator will overwrite the `DocDateAnnotation` if
"datetime" or "date" are specified in the document.

## Caseless model

It is possible to run StanfordCoreNLP with an NER
model that ignores capitalization.  In order to do this, download the
[caseless models](http://nlp.stanford.edu/software/stanford-corenlp-caseless-2015-04-20-models.jar) package.  Be sure to include the path to the case
insensitive models jar in the `-cp` classpath flag as well.

```
-ner.model edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz
   edu/stanford/nlp/models/ner/english.muc.7class.caseless.distsim.crf.ser.gz
   edu/stanford/nlp/models/ner/english.conll.4class.caseless.distsim.crf.ser.gz
```



## More information 

For more details on the CRF tagger see [this page](http://nlp.stanford.edu/software/CRF-NER.shtml).
