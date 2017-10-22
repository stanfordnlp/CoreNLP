---
title: EntityMentionsAnnotator 
keywords: entitymentions
permalink: '/entitymentions.html'
---

## Description

This annotator generates a list of the mentions, identified by NER, found in each sentence of a document. Rather than per-token labeling, it produces whole entity mentions. For example, "New York City" will be identified as one entity mention. The "sentences" element of a document annotation will additionally contain an "entitymentions" element. The value of this is a list of individual entity mentions, including their text, their span in tokens and character offsets, their NER tag, and for quantities, their normalized value, and TIMEX form, as appropriate. This detail is available to API users. At present, entity mentions are only included in the output of the JSON outputter.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| entitymentions | EntityMentionsAnnotator | MentionsAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| entitymentions.nerCoreAnnotation | class | CoreAnnotations.NamedEntityTagAnnotation.class | Class to use as key to look up NER value. |
| entitymentions.nerNormalizedCoreAnnotation | class | CoreAnnotations.NormalizedNamedEntityTagAnnotation.class | Class to use as key to look up normalized named entity value. |
| entitymentions.mentionsCoreAnnotation | class | CoreAnnotations.MentionsAnnotation.class | Class to use as key to look up mentions. |
| entitymentions.acronyms | boolean | false | If true, heuristically search for organization acronyms, even if they are not marked explicitly by an NER tag. That is, it looks for putative acronyms of an organization identified elsewhere in the document. In some work this has been super useful (+20% recall). |
