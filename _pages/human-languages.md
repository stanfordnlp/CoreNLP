---
layout: page
title: Using CoreNLP on other human languages
keywords: human languages
permalink: '/human-languages.html'
nav_order: 9
parent: Usage
---

¡Hola! − 您好！

Out-of-the-box, Stanford CoreNLP expects and processes English language text. But, Stanford CoreNLP was designed from the start to work with multiple human languages and it is careful about things like different character encodings. We have developed components for several major languages, and make language packs (jar files) available for some of them. The table below summarizes our current first party foreign language support. Other people have developed [models for other languages](#models-for-other-languages).

| Annotator | ar | zh | en | fr | de | es |
| --------------- |:---:|:---:|:---:|:---:|:---:|:---:|
| Tokenize / Segment | ✔ | ✔  | ✔ | ✔  |     | ✔ |
| Sentence Split | ✔ | ✔  | ✔ | ✔  | ✔ | ✔ |
| Part of Speech | ✔ | ✔  | ✔ | ✔  | ✔ | ✔ |
| Lemma |   |   | ✔ |   |   |    |
| Named Entities |   | ✔  | ✔ | ✔ | ✔ | ✔ |
| Constituency Parsing | ✔ | ✔  | ✔ | ✔ | ✔ | ✔ |
| Dependency Parsing |    | ✔  | ✔ | ✔ | ✔ |     |
| Sentiment Analysis |    |    | ✔ |  |  |     |
| Mention Detection |    | ✔  | ✔ |  |  |     |
| Coreference |    | ✔  | ✔ |  |  |     |
| Open IE |    |   | ✔ |  |  |     |

#### Models

To run Stanford CoreNLP on a supported language, you have to include the models jar for that language in your CLASSPATH.

The jars for each language can be found here:

| Language | model jar | version |
| :------- | :-------- | | :----- |
| Arabic  | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-arabic.jar) | 4.0.0 |
| Chinese | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-chinese.jar) | 4.0.0 |
| French | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-french.jar) | 4.0.0 |
| German | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-german.jar) | 4.0.0 |
| Spanish | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-spanish.jar) | 4.0.0 |

#### Running pipelines

There are sets of default properties that can be used to run pipelines for every supported language.

For instance, to run a Spanish pipeline, one could execute this command from the command line:

```sh
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -props spanish -file example.txt
```

Or build and run a pipeline in Java in this manner:

```java
String text = "La Universidad de Stanford se encuentra en Palo Alto.";
StanfordCoreNLP pipeline = new StanfordCoreNLP("spanish");
CoreDocument doc = pipeline.processToCoreDocument(text);
```

These examples would use the following sets of properties (found in `StanfordCoreNLP-spanish.properties`)

```sh
# annotators
annotators = tokenize, ssplit, mwt, pos, lemma, ner, depparse, kbp

# tokenize
tokenize.language = es

# mwt
mwt.mappingFile = edu/stanford/nlp/models/mwt/spanish/spanish-mwt.tsv

# pos
pos.model = edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger

# ner
ner.model = edu/stanford/nlp/models/ner/spanish.ancora.distsim.s512.crf.ser.gz
ner.applyNumericClassifiers = true
ner.useSUTime = true
ner.language = es

# sutime
sutime.language = spanish

# parse
parse.model = edu/stanford/nlp/models/srparser/spanishSR.beam.ser.gz

# depparse
depparse.model = edu/stanford/nlp/models/parser/nndep/UD_Spanish.gz
depparse.language = spanish

# regexner
ner.fine.regexner.mapping = edu/stanford/nlp/models/kbp/spanish/gazetteers/kbp_regexner_mapping_sp.tag
ner.fine.regexner.validpospattern = ^(NOUN|ADJ|PROPN).*
ner.fine.regexner.ignorecase = true
ner.fine.regexner.noDefaultOverwriteLabels = CITY,COUNTRY,STATE_OR_PROVINCE

# kbp
kbp.semgrex = edu/stanford/nlp/models/kbp/spanish/semgrex
kbp.tokensregex = edu/stanford/nlp/models/kbp/spanish/tokensregex
kbp.model = none
kbp.language = es

# entitylink
entitylink.caseless = true
entitylink.wikidict = edu/stanford/nlp/models/kbp/spanish/wikidict_spanish.tsv
```

The pattern is the same for the other supported languages.

#### UD supported languages

Currently French (UD 2.2), German (UD 2.2), and Spanish (AnCora UD 2.0) work off of the UD 2.0 tokenization standard. This means among other things that words are split into multiword tokens. For instance the French word `"des"` will be tokenized in some circumstances as `"de" "les"`. All tagging, parsing, and named entity recognition models rely on that tokenization standard, so it is necessary to use the `mwt` annotator which performs the multiword tokenization. For instance, in Spanish, the annotators required to run dependency parsing would be `tokenize,ssplit,mwt,pos,lemma,depparse`. The part of speech tags and dependency labels are from the UD 2.0 sets for each language.

#### Models for other languages

Other people have developed models using or compatible with CoreNLP for several further languages. They may or may not be compatible with the most recent release of CoreNLP that we provide.

* **Italian:** [Tint](http://tint.fbk.eu/) by Alessio Palmero Aprosio and Giovanni Moretti (Fondazione Bruno Kessler) largely builds on CoreNLP, but adds some other components, to provide a quite complete processing pipeline for Italian.
* **Portuguese (European):** [LX parser](http://lxcenter.di.fc.ul.pt/tools/en/LXParserEN.html) by Patricia Gonçalves and João Silva (University of Lisbon)  provides a constituency parser. It was built with a now quite old version of Stanford NLP.
* **Swedish:** Andreas Klintberg has built an [NER model](https://medium.com/@klintcho/training-a-swedish-ner-model-for-stanford-corenlp-part-2-20a0cfd801dd#.vnow3swam) and a [POS tagger](https://medium.com/@klintcho/training-a-swedish-pos-tagger-for-stanford-corenlp-546e954a8ee7#.ms2ym1he3).
