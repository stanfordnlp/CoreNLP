---
title: CleanXMLAnnotator
keywords: cleanxml
permalink: '/cleanxml.html'
---

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| cleanxml | CleanXmlAnnotator | XmlContextAnnotation | 

## Description

Remove xml tokens from the document.

## Options

* clean.xmltags: Discard xml tag tokens that match this regular expression.  For example, .* will discard all xml tags.
* clean.sentenceendingtags: treat tags that match this regular expression as the end of a sentence.  For example, p will treat &lt;p&gt; as the end of a sentence.
* clean.allowflawedxml: if this is true, allow errors such as unclosed tags.  Otherwise, such xml will cause an exception.
* clean.datetags: a regular expression that specifies which tags to treat as the reference date of a document.  Defaults to datetime|date
