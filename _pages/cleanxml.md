---
title: CleanXMLAnnotator
keywords: cleanxml
permalink: '/cleanxml.html'
---

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| cleanxml | CleanXmlAnnotator | XmlContextAnnotation | 

## Description

This annotator removes XML tags from an input document.
Stanford CoreNLP also has the ability to remove most XML from a
document before processing it. This functionality is provided by a
finite automaton. It works fine for typical XML, but complex
constructions and CDATA sections will not be correctly handled. 
If you want full and correct handling of XML, then you should run XML
documents through an XML parser (such as the one included standard in
Java) before passing appropriate text nodes to Stanford CoreNLP.

The cleanxml annotator supports many complex processing options: 
You can choose to only delete
some XML tags, to treat certain XML tags as sentence ending, as
marking the speaker in a dialog, etc. The cleanxml annotator can be
placed after tokenize in processing order.

For example, if run with the annotators 

```
annotators = tokenize, cleanxml, ssplit, pos, lemma, ner, parse, dcoref
```

and given the text
> `<xml>Stanford University is located in California. It is a great university.</xml>`

Stanford CoreNLP generates output that is basically the same as for the default
`input.txt` example. The only difference between this and the original output is a change in CharacterOffsets. 


## Options

Some of the supported options are documented below. At present, there
are others that can only be found in the source code….

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| clean.xmltags | regex | ".*" | edu/stanford/nlp/models/parser/nndep/english\_UD.gz | Discard xml tag tokens that match this regular expression.  For example, the default `.*` will discard all xml tags. |
| clean.sentenceendingtags | regex | "" | Treat tags that match this regular expression as the end of a sentence, or none if an empty string.  For example, p will treat &lt;p&gt; as the end of a sentence. |
| clean.allowflawedxml | boolean | false | If this is true, allow errors such as unclosed tags.  Otherwise, such xml will cause an exception. |
| clean.datetags | regex | "datetime|date" | A regular expression that specifies which tags to treat as the reference date of a document. |
| clean.docIdtags | regex | "docid" | A regular expression that specifies which tags to treat as the document id of a document. |
| clean.docTypetags | regex| "doctype" | A regular expression that specifies which tags to treat as the document type of a document. |

