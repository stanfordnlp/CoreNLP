---
title: TrueCaseAnnotator 
keywords: truecase
permalink: '/truecase.html'
---

## Description

Recognizes the true case of tokens in text where this information was lost, e.g., all upper case text. This is implemented with a discriminative model implemented using a CRF sequence tagger. The true case label, e.g., INIT_UPPER is saved in TrueCaseAnnotation. The token text adjusted to match its true case is saved as TrueCaseTextAnnotation.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| truecase | TrueCaseAnnotator | TrueCaseAnnotation and TrueCaseTextAnnotation |