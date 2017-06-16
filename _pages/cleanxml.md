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
the original XML text file.

The cleanxml annotator supports many complex processing options: 
You can choose to only delete
some XML tags, to treat certain XML tags as sentence ending, as
marking the speaker in a dialog, etc. You can also extract document
metadata from XML attributes. The cleanxml annotator can be
placed after tokenize in processing order.

As a simple example, if run with the annotators 

```
annotators = tokenize, cleanxml, ssplit, pos, lemma, ner, parse, dcoref
```

and given the text
> `<xml>Stanford University is located in California. It is a great university.</xml>`

Stanford CoreNLP deletes the XML tags and generates output that is basically the same as for the default
`input.txt` example. The only difference between this and the original
output is a change in CharacterOffsets. A much more complex example
appears below.


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
| clean.quotetags | regex | `""` | If this regex matches an XML element name, store its contents as a quoted region (such as in an email or discussion forum post quoting earlier authors), including recording character offsets and author. |
| clean.quoteauthorattributes | String | `""` | A comma-separated list of XML attributes for a quote tag whose value is treated as the author of the quote. |
| clean.ssplitDiscardTokens | regex | `""` | A regular expression of tokens discarded while processing a section. |

### Example: Handling discussion forums

Here is an example of setting many of these options in order to access information from an XML file
that is similar to the LDC MPDF (multi-post discussion forum) XML format.

Here is a sample document (which you can also [download]({{ site.github.url }}/assets/DF-sample.xml)):

    <doc id="ENG_DF_sample_101">
    <headline>
    Worth looking in to?
    </headline>
    <post author="James Rood" datetime="2010-05-29T17:14:00" id="p1">
    Yesterday afternoon as I negotiated route 149 from Lake George to Fort Ann in NY I passed a new diner that had opened that day. I didnt notice the na\
    me but out side it had a Union Jack and a St Georges flag flying
    I wonder if they serve proper 'English' food, served by proper 'English' people ? I may have to check it out asap <img src="http://britishexpats.com/\
    forum/images/smilies/wink.gif"/>
    </post>
    <post author="UDDep" datetime="2010-05-30T15:43:00" id="p2">
    <quote orig_author="James Rood">
    Yesterday afternoon as I negotiated route 149 from Lake George to Fort Ann in NY I passed a new diner that had opened that day. I didnt notice the na\
    me but out side it had a Union Jack and a St Georges flag flying
    I wonder if they serve proper 'English' food, served by proper 'English' people ? I may have to check it out asap <img src="http://britishexpats.com/\
    forum/images/smilies/wink.gif"/>
    </quote>
    If they don't have english food and beer...tell em they've got a bloody cheek flying the flags and luring un-suspecting expats in....little buggers..\
    . <img src="http://britishexpats.com/forum/images/smilies/smile.gif"/>
    </post>
    <post author="Mack67" datetime="2010-05-30T18:23:00" id="p3">
    <quote orig_author="James Rood">
    Yesterday afternoon as I negotiated route 149 from Lake George to Fort Ann in NY I passed a new diner that had opened that day. I didnt notice the na\
    me but out side it had a Union Jack and a St Georges flag flying
    I wonder if they serve proper 'English' food, served by proper 'English' people ? I may have to check it out asap <img src="http://britishexpats.com/\
    forum/images/smilies/wink.gif"/>
    </quote>
    Halal?
    </post>
    <post author="cherise" datetime="2010-05-30T20:02:00" id="p4">
    <quote orig_author="Mack67">
    Halal?
    </quote>
    Tandoori.
    </post>
    </doc>

Here are the properties used (which you can also [download]({{ site.github.url }}/assets/cleanxml.properties))):

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
clean.quotetags = quote
clean.quoteauthorattributes = orig_author
clean.tokenAnnotations = link=a[href],speaker=post[author],speaker=quote[orig_author]
clean.ssplitDiscardTokens = \\n|\\*NL\\*
```

Here is sample java code to access information from this document:

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;

import java.util.*;
import java.util.stream.Collectors;


public class ForumPostExample {

  public static void main(String args[]) {
    // properties
    Properties props = StringUtils.argsToProperties("-props", args[0]);
    // set up pipeline
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // set up document
    Annotation testDocument = new Annotation(IOUtils.stringFromFile(args[1]));
    // annotate document
    pipeline.annotate(testDocument);
    // print out the forum posts
    for (CoreMap discussionForumPost : testDocument.get(CoreAnnotations.SectionsAnnotation.class)) {
      System.err.println("---");
      System.err.println("author: " + discussionForumPost.get(CoreAnnotations.AuthorAnnotation.class));
      System.err.println("date: " +
          discussionForumPost.get(CoreAnnotations.SectionDateAnnotation.class));
      System.err.println("char begin: " +
          discussionForumPost.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
      System.err.println("char end: " +
          discussionForumPost.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
      System.err.println("author start offset: " +
          discussionForumPost.get(CoreAnnotations.SectionAuthorCharacterOffsetBeginAnnotation.class));
      System.err.println("author end offset: " +
          discussionForumPost.get(CoreAnnotations.SectionAuthorCharacterOffsetEndAnnotation.class));
      System.err.println("section start tag: " +
          discussionForumPost.get(CoreAnnotations.SectionTagAnnotation.class));
      // print out the sentences
      System.err.println("sentences: ");
      for (CoreMap sentence : discussionForumPost.get(CoreAnnotations.SentencesAnnotation.class)) {
        System.err.println("\t***");
        boolean sentenceQuoted = (sentence.get(CoreAnnotations.QuotedAnnotation.class) != null) &&
            sentence.get(CoreAnnotations.QuotedAnnotation.class);
        String sentenceAuthor = sentence.get(CoreAnnotations.AuthorAnnotation.class);
        String potentialQuoteText = sentenceQuoted ? "(QUOTING: "+sentenceAuthor+")" : "" ;
        System.err.println("\t" + potentialQuoteText + " " +
            sentence.get(CoreAnnotations.TokensAnnotation.class).stream().
            map(token -> token.word()).collect(Collectors.joining(" ")));
      }
    }
  }
}
```

Here is running it from the command-line and producing JSON output:

```
```

And here is the part of the output json file showing the section information:

```json
  "sections": [
    {
      "charBegin": 40,
      "charEnd": 60,
      "sentenceIndexes": [
        {
          "index": 0
        }
      ]
    },
    {
      "charBegin": 139,
      "charEnd": 466,
      "author": "James Rood",
      "dateTime": "2010-05-29T17:14:00",
      "sentenceIndexes": [
        {
          "index": 1
        }
      ]
    },
    {
      "charBegin": 637,
      "charEnd": 1192,
      "author": "UDDep",
      "dateTime": "2010-05-30T15:43:00",
      "sentenceIndexes": [
        {
          "index": 2
        },
        {
          "index": 3
        },
        {
          "index": 4
        },
        {
          "index": 5
        }
      ]
    },
    {
      "charBegin": 1365,
      "charEnd": 1776,
      "author": "Mack67",
      "dateTime": "2010-05-30T18:23:00",
      "sentenceIndexes": [
        {
          "index": 6
        },
        {
          "index": 7
        },
        {
          "index": 8
        },
        {
          "index": 9
        }
      ]
    },
    {
      "charBegin": 1877,
      "charEnd": 1902,
      "author": "cherise",
      "dateTime": "2010-05-30T20:02:00",
      "sentenceIndexes": [
        {
          "index": 10
        },
        {
          "index": 11
        }
      ]
    }
  ]
  ```
  
