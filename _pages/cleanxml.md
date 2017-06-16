---
title: CleanXMLAnnotator
keywords: cleanxml
permalink: '/cleanxml.html'
---

## Description

| Name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| cleanxml | CleanXmlAnnotator | XmlContextAnnotation | 

This annotator removes XML tags from an input document.
Stanford CoreNLP has the ability to remove all or most XML tags from a
document before processing it. This functionality is provided by a
finite automaton. It works fine for typical, simple XML, but complex
constructions and CDATA sections will not be correctly handled. 
If you want full and correct handling of XML, then you should run XML
documents through an XML parser (such as the one included standard in
Java) before passing appropriate text nodes to Stanford
CoreNLP. However, then you are unable to recover character offsets in
the original text with XML annotation.

The cleanxml annotator supports many complex processing options: 
You can choose to only delete
some XML tags, to treat certain XML tags as sentence ending, as
marking the speaker in a dialog, etc. You can also extract document
metadata from XML attributes. The cleanxml annotator can be
placed after tokenize in processing order.

For example, if run with the annotators 

```
annotators = tokenize, cleanxml, ssplit, pos, lemma, ner, parse, dcoref
```

and given the text
> `<xml>Stanford University is located in California. It is a great university.</xml>`

Stanford CoreNLP deletes the XML tags and generates output that is basically the same as for the default
`input.txt` example. The only difference between this and the original output is a change in CharacterOffsets. 


## Options

Note that although the annotator is called "cleanxml" in the
annotators list, the prefix for options is just "clean". We should
probably regularize this at some point…. Also, the option names
are case sensitive, so get them right!

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| clean.xmltags | regex | `".*"` | Discard xml tag tokens that match this regular expression.  For example, the default `.*` will discard all xml tags. |
| clean.sentenceendingtags | regex | `""` | Treat tags (XML elements) that match this regular expression as the end of a sentence. An empty string matches nothing.  For example, "p" will treat &lt;p&gt; or &lt;/p&gt; as the end of a sentence. Matching is case insensitive. |
| clean.singlesentencetags | regex | `""` | Treat the content of XML elements that match this regular expression as a single sentence. An empty string matches nothing.  For example, "sent" will treat any &lt;sent&gt; element as a single sentence, disabling sentence splitting. Matching is case insensitive. |
| clean.allowflawedxml | boolean | true | If this is true, allow errors such as unclosed tags.  Otherwise, such XML will cause an exception. |
| clean.datetags | regex | `"datetime\|date"` | A regular expression that specifies which tags to treat as giving the reference date of a document. |
| clean.docIdtags | regex | `"docid"` | A regular expression that specifies which tags to treat as giving the document id of a document. |
| clean.docTypetags | regex| `"doctype"` | A regular expression that specifies which tags to treat as giving the document type of a document. |
| clean.turntags | regex| `"turn"` | A regular expression that specifies which tags to treat as marking a turn in a dialog. |
| clean.speakertags | regex| `"speaker"` | A regular expression that specifies which tags to treat as marking a speaker in a dialog. |
| clean.docAnnotations | String| `"docID=doc[id],` `doctype=doc[type],` `docsourcetype=` `doctype[source]"` | A map of document level annotation keys (i.e., docid) along with a pattern indicating the tag to match, and the attribute to match. |
| clean.tokenAnnotations | String| `""` | A map of token level annotation keys (i.e., link, speaker) along with a pattern indicating the tag/attribute to match (tokens that belong to the text enclosed in the specified tag will be annotated). |
| clean.sectiontags | regex | `""` | A regular expression that specifies which tags to treat as marking sections of a document. |
| clean.sectionAnnotations | String | `""` | A map of section level annotation keys along with a pattern indicating the tag to match, and the attribute to match. |
| clean.ssplitDiscardTokens | regex | `""` | A regular expression of tokens discarded (?). |

### Example

Here is an example of setting many of these options for an XML file
that is similar to various LDC XML formats:

```
clean.xmltags = headline|dateline|text|post
clean.singlesentencetags = HEADLINE|DATELINE|SPEAKER|POSTER|POSTDATE
clean.sentenceendingtags = P|POST|QUOTE
clean.turntags = TURN|POST|QUOTE
clean.speakertags = SPEAKER|POSTER
clean.docidtags = DOCID
clean.datetags = DATETIME|DATE|DATELINE
clean.doctypetags = DOCTYPE
clean.docAnnotations = docID=doc[id],doctype=doc[type],docsourcetype=doctype[source]
clean.sectiontags = HEADLINE|DATELINE|POST
clean.sectionAnnotations = sectionID=post[id],sectionDate=post[date|datetime],sectionDate=postdate,author=post[author],author=poster
clean.tokenAnnotations = link=a[href],speaker=post[author],speaker=quote[orig_author]
clean.ssplitDiscardTokens = \\n|\\*NL\\*
```
