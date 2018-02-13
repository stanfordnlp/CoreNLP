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

### Several annotators have been enhanced to run other other annotators.

This original annotators list:

```bash
tokenize,ssplit,pos,lemma,ner,regexner,entitymentions,parse,mention,coref,quote,quote_attribution
```

can now be expressed as:

```bash
tokenize,ssplit,pos,lemma,ner,parse,coref,quote
```

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

Likewise for `coref` annotation you can shut off the coref mention detection (if you
want to use a custom coref mention annotator)

```bash
coref.useCustomMentionDetection = true
```

And for `quote` annotation, quote attribution can be deactivated with

```bash
quote.attributeQuotes = false
```
