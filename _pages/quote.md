---
title: QuoteAnnotator 
keywords: quote
permalink: '/quote.html'
---

## Description

Deterministically picks out quotes delimited by " or ' from a text. All top-level quotes, are supplied by the top level annotation for a text. If a QuotationAnnotation corresponds to a quote that contains embedded quotes, these quotes will appear as embedded QuotationAnnotations that can be accessed from the QuotationAnnotation that they are embedded in. The QuoteAnnotator can handle multi-line and cross-paragraph quotes, but any embedded quotes must be delimited by a different kind of quotation mark than its parents. Does not depend on any other annotators. Support for unicode quotes is not yet present.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| quote | QuoteAnnotator | QuotationAnnotation | 

## Options

* quote.singleQuotes: whether or not to consider single quotes as quote delimiters. Default is "false".
