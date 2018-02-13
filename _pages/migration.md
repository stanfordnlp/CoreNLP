---
title: Migration Guide For Stanford CoreNLP
keywords: migration
permalink: '/migration.html'
---

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
