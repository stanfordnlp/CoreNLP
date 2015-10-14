---
title: Annotator dependencies
keywords: dependencies
permalink: '/dependencies.html'
---

| Property name | Annotator class name | Requirements |
| --- | --- | --- |
| [tokenize](tokenize.html) | TokenizerAnnotator | None |
| [cleanxml](cleanxml.html) | CleanXmlAnnotator | `tokenize` |
| [ssplit](ssplit.html) | WordToSentenceAnnotator | `tokenize` |
| [pos](pos.html) | POSTaggerAnnotator | `tokenize, ssplit` |
| [lemma](lemma.html) | MorphaAnnotator | `tokenize, ssplit, pos` |
| [ner](ner.html) | NERClassifierCombiner | `tokenize, ssplit, pos, lemma`  |
| [regexner](regexner.html) | RegexNERAnnotator | ? |
| [sentiment](sentiment.html) | SentimentAnnotator | ? |
| [parse](parse.html) | ParserAnnotator | `tokenize, ssplit` |
| [depparse](depparse.html) | DependencyParseAnnotator | `tokenize, ssplit, pos` |
| [dcoref](dcoref.html) | DeterministicCorefAnnotator | `tokenize, ssplit, pos, lemma, ner, parse` |
| [relation](relation.html) | RelationExtractorAnnotator | `tokenize, ssplit, pos, lemma, ner, depparse` |
| [natlog](natlog.html) | NaturalLogicAnnotator | `tokenize, ssplit, pos, lemma, depparse` (Can also use `parse`) |
| [quote](quote.html) | QuoteAnnotator | None |
