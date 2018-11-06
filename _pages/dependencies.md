---
title: Annotator dependencies
keywords: dependencies
permalink: '/dependencies.html'
---

| Property name | Annotator class name | Requirements |
| --- | --- | --- |
| [tokenize](tokenize.html) | TokenizerAnnotator | None |
| [cleanxml](cleanxml.html) | CleanXmlAnnotator | `tokenize` |
| [ssplit](ssplit.html) | WordsToSentenceAnnotator | `tokenize` |
| [pos](pos.html) | POSTaggerAnnotator | `tokenize, ssplit` |
| [lemma](lemma.html) | MorphaAnnotator | `tokenize, ssplit, pos` |
| [ner](ner.html) | NERClassifierCombiner | `tokenize, ssplit, pos, lemma`  |
| [regexner](regexner.html) | RegexNERAnnotator | `tokenize, ssplit, pos` |
| [sentiment](sentiment.html) | SentimentAnnotator | `tokenize, ssplit, pos, parse` |
| [parse](parse.html) | ParserAnnotator | `tokenize, ssplit, parse` |
| [depparse](depparse.html) | DependencyParseAnnotator | `tokenize, ssplit, pos` |
| [dcoref](coref.html) | DeterministicCorefAnnotator | `tokenize, ssplit, pos, lemma, ner, parse` |
| [coref](coref.html) | CorefAnnotator | `tokenize, ssplit, pos, lemma, ner, parse` (Can also use `depparse`) |
| [relation](relation.html) | RelationExtractorAnnotator | `tokenize, ssplit, pos, lemma, ner, depparse` |
| [natlog](natlog.html) | NaturalLogicAnnotator | `tokenize, ssplit, pos, lemma, depparse` (Can also use `parse`) |
| [quote](quote.html) | QuoteAnnotator | `tokenize, ssplit, pos, lemma, ner, depparse, coref` |
