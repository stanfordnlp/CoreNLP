---
layout: page
title: Migration
keywords: migration
permalink: '/migration.html'
nav_order: 2
parent: Usage
---

## 4.0.0

### Dependency parsing for English, French, German, and Spanish now uses UD 2.0 dependencies

The neural models for English, French, German, and Spanish have been retrained with UD 2.0 dependencies,
this will change several labels for the dependency parses.  Info about UD 2.0 can be found [here](https://universaldependencies.org/).

### Tokenization has been upgraded to UD 2.0 for English, French, German, and Spanish.

Annotators, models, and rules for English, French, German, and Spanish now work with UD 2.0
tokenization by default. This includes models for tagging, parsing, named entity recognition
(with an important exception), and KBP relation extraction. For example, the English tokenizer 
now splits most hyphenated tokens, does not normalize parentheses (e.g. turn `(` into `-LRB-`), 
and does not normalize quotation marks.

A specialized tokenization that is mostly the UD 2.0 version is used for named entity
recognition (see below).

Custom models trained with version 3.9.2 or earlier may suffer performance issues since they
expect a different tokenization standard. It is advised to retrain models with tokenization
that follows the UD 2.0 standard. If retraining an NER model, note that the training data
should not split tokens on hyphen.

The tokenization process for these languages has been designed to maximize F1 on dev/test
sets from the CoNLL 2018 shared task, similar to [Stanza](https://stanfordnlp.github.io/stanza/).

Examples of UD 2.0 tokenization for these languages can be found [here](https://universaldependencies.org/).

### NER Specific Tokenization

A complication is that the UD 2.0 standard for English and German says to split tokens on
hyphen, but this can lead to diminished performance. Consider the example of double barrel
names such as `Daniel Day-Lewis` or hyphenated place names such as `Bergen-Enkheim`. It
was found that splitting on hyphen dropped F1 score, so the hyphen splitting is mostly
deactivated for named entity recognition. The only exceptions are the following key words:
`based, area, registered, headquartered, native, born, raised, backed, controlled, owned, resident, 
trained, educated`. So `Chicago-based` WILL be split into `Chicago` `-` `based` to allow
for the token `Chicago` to be recognized as a `CITY`.

The NERAnnotator by default takes in UD 2.0 tokens, and then merges all tokens
that were originally joined by a hyphen in the text (except for cases like Chicago-based).
The model is run on the modified tokens list, and the labels are finally applied to the original UD 2.0 tokens. 
This behavior can be turned off by setting `ner.useNERSpecificTokenization` to `false`.

### MWT annotator required for French, German, and Spanish

Related to the tokenization change, French, German, and Spanish now require the use
of the MWTAnnotator which splits some tokens into multiple words with rules and
statistical models. For instance the French token "des" is sometimes split into the
words "de" and "les".

Some multi-word token splitting for these languages used to occur in the `tokenize`
annotator, but now this annotator focuses on creating tokens, and the `mwt` annotator
is used to make token splitting decisions, sometimes via a dictionary and other times
via a statistical model.

These languages require the `mwt` annotator be run immediately after the `ssplit`
annotator.

For example, the German default annotators list has changed from

```bash
tokenize, ssplit, pos, ner, depparse
```

to

```bash
tokenize, ssplit, mwt, pos, ner, depparse
```

## 3.9.0

### Annotator renaming

| Original name | New name |
| :--- | :--- |
| mention | coref.mention |
| quote_attribution | quote.attribution |

### Several annotators have been enhanced to run other annotators.

This original annotators list:

```bash
tokenize,ssplit,pos,lemma,ner,regexner,entitymentions,parse,mention,coref,quote,quote_attribution
```

can now be expressed as:

```bash
tokenize,ssplit,pos,lemma,ner,parse,coref,quote
```

The `ner`, `coref`, and `quote` annotators will run some of the annotators themselves
as sub-annotators.  This means for instance that the `ner` annotator will run a combination
of CRF classifiers (adding ner tags to tokens), then the TokensRegex based `regexner` to produce 
fine-grained annotations ("LOCATION" -> "COUNTRY"), and then finally it will annotate the full
entity mentions ("Joe", "Smith" --> "Joe Smith") with its internal `entitymentions` annotator.

| Annotator | Sub-annotators |
| :--- | :--- |
| ner | regexner,entitymentions |
| coref | coref.mention |
| quote | quote.attribution |

You can run the `ner` annotator without the additional annotators with these options

```bash
ner.applyFineGrained = false
ner.buildEntityMentions = false
```

If you wish to set parameters for the `ner` annotator's internal `regexner` annotator
set `ner.fine.regexner` properties.  For instance:

`ner.fine.regexner.mapping = edu/stanford/nlp/models/kbp/spanish/kbp_regexner_mapping_sp.tag`

Likewise to set the `ner` annotator's internal `entitymentions` annotator, set
`ner.entitymentions` properties.  For instance:

`ner.entitymentions.acronyms = true`

Likewise for `coref` annotation you can shut off the coref mention detection (if you
want to use a custom coref mention annotator)

```bash
coref.useCustomMentionDetection = true
```

And for `quote` annotation, quote attribution can be deactivated with

```bash
quote.attributeQuotes = false
```
