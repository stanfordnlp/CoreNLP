---
title: Named Entity Recognition – NERClassifierCombiner
keywords: ner
permalink: '/ner.html'
---

## Description

Recognizes named entities (person and company names, etc.) in text.
The set of entities recognized is language-dependent, and the
recognized set of entities is frequently more limited for other
languages than what is
described below for English. As the name “NERClassifierCombiner”
implies, commonly this annotator will run several named entity
recognizers and then combine their results.

For English, this annotator recognizes
named (PERSON, LOCATION, ORGANIZATION, MISC), numerical (MONEY,
NUMBER, ORDINAL, PERCENT), and temporal (DATE, TIME, DURATION, SET)
entities. Named entities are recognized using a combination of three
CRF sequence taggers trained on various corpora, such as CoNLL, ACE and
MUC. Numerical entities are recognized using a rule-based
system. Numerical entities that require normalization, e.g., dates,
have their normalized value stored in NormalizedNamedEntityTagAnnotation.

It is possible to recognize additional or more fine-grained entity
classes through the use of TokensRegex patterns; see the
[RegexNER](regexner.html) annotator for more about this.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| ner | NERClassifierCombiner | NamedEntityTagAnnotation and NormalizedNamedEntityTagAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| ner.useSUTime | boolean | true | Whether or not to use SUTime. If not processing English, make sure to set this to false. |
| ner.model | List(String) | null | A comma-separated list of NER model names (or just a single name is okay). If none are specified, a default list of English models is used (3class, 7class, and MISCclass, in that order). The names will be looked for as classpath resources, filenames, or URLs. |
| ner.applyNumericClassifiers | boolean | true | Whether or not to use numeric classifiers, including [SUTime](http://nlp.stanford.edu/software/regexner/).  These are hardcoded for English, so if using a different language, this should be set to false. |
| sutime.markTimeRanges | boolean | false | Tells SUTime whether to mark phrases such as “From January to March” as a range, instead of marking "January" and "March" separately. |
| sutime.includeRange | boolean | false | If marking time ranges, set the time range in the TIMEX output from SUTime. |

## SUTime

StanfordCoreNLP includes [SUTime](http://nlp.stanford.edu/software/sutime.html), Stanford's temporal expression
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


## Caseless models

It is possible to run StanfordCoreNLP with NER
models that ignore capitalization. We have trained models like this
for English. You can find details on the
[Caseless models](caseless.html) page.

## Training or retraining new models

CRF models are trained using the main method of
`CRFClassifier`. The CRF FAQ has [some instructions](https://nlp.stanford.edu/software/crf-faq.html#a).
SUTime rules can be changed by modifying its included
TokensRegex rule files. Changing other rule-based components (money,
etc.) requires changes to the Java source code.

## More information 

For more details on the CRF tagger see [this page](http://nlp.stanford.edu/software/CRF-NER.html).
