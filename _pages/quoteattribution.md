---
title: QuoteAttributionAnnotator 
keywords: quoteattribution
permalink: '/quoteattribution.html'
---

## Description

Attributes quotes in a text to their speakers. This annotator uses a two-stage linking strategy in which quotes are first linked to mentions, then mentions are linked to speakers. In the end, each quote will be annotated with both a mention in the text and a speaker entity.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| quoteattribution | QuoteAttributionAnnotator | MentionAnnotation, MentionBeginAnnotation, MentionEndAnnotation, MentionTypeAnnotation, MentionSieveAnnotation, SpeakerAnnotation, SpeakerSieveAnnotation, ParagraphIndexAnnotation | 

## Available Sieves

Quote to mention linking can be done using the following sieves:

| Sieve name | Function |
| --- | --- |
| tri | Trigram detection such as QUOTE-CHARACTER-VERB |
| dep | Dependency parse information to find the nsubj relation of speech verbs like "say" |
| onename | If there is only a single mention in non-quote text in the same paragraph as the quote, use that mention |
| voc | Links a quote to vocatives in a quote preceding or following the target quote |
| paraend | If a quote appears at the end of a paragraph, link it to the final mention in the previous sentence |
| conv | Conversational sieve links the target quote to the same mention as the quote two before it if they appear to be in a conversation |
| sup | Supervised classifier sieve uses the trained model to link quotes to mentions |
| loose | Loose conversational sieve is similar to the conversational sieve but relaxes the requirements for how to determine if two quotes are considered subsequent turns in a conversation |

Mention to Speaker linking can be done using the following sieves:

| Sieve name | Function |
| --- | --- |
| det | Deterministicly links mentions to speakers using extact name match and coreference information |
| loose | Uses loose conversational patterns to link mentions to the same speakers as corresponds to the conversation |
| top | Links the mention to the top speaker that matches the mention in gender in a window around the target quote, containing logic to deal with conversations and vocative mentions |
| maj | Links the mention to the majority speaker |

## Options

* quoteattribution.charactersPath (required): path to file containing the character names, aliases, and gender information.
* quoteattribution.booknlpCoref (required): path to tokens file generated from [book-nlp](https://github.com/dbamman/book-nlp) containing coref information.
* quoteattribution.QMSieves: list of sieves to use in the quote to mention linking phase (default=tri,dep,onename,voc,paraend,conv,sup,loose).
* quoteattribution.MSSieves: list of sieves to use in the mention to speaker linking phase (default=det,top).
* quoteattribution.model: path to trained model file.
* quoteattribution.familyWordsFile: path to file with family words list.
* quoteattribution.animacyWordsFile: path to file with animacy words list.
* quoteattribution.genderNamesFile: path to file with names list with gender information.

## Citing Stanford Quote Attribution

> Grace Muzny, Michael Fang, Angel X. Chang and Dan Jurafsky. 2017. A Two-stage Sieve Approach for Quote Attribution. *Proceedings of the European Chapter of the Association for Computational Linguistics* (EACL). \[[pdf](http://nlp.stanford.edu/pubs/muzny2017twostage.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/muzny2017twostage.bib)\]
