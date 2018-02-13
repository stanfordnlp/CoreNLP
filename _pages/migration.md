---
title: Migration Guide For Stanford CoreNLP
keywords: migration
permalink: '/migration.html'
---

## 3.9.0

###Several annotators have been enhanced to run other other annotators.

This original annotators list:

```bash
tokenize,ssplit,pos,lemma,ner,regexner,entitymentions,parse,mention,coref,quote,quote_attribution
```

can now be expressed as:

```bash
tokenize,ssplit,pos,lemma,ner,parse,coref,quote
```
