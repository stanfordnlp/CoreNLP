---
layout: page
title: Full List Of Annotators
keywords: annotators
permalink: '/annotators.html'
nav_order: 4
parent: Pipeline
---

## Annotator Descriptions

| Name | Annotator class name | Generated Annotation | Description |
| --- | --- | --- | --- | 
| [tokenize](tokenize.html) | TokenizerAnnotator | TokensAnnotation (list of tokens); CharacterOffsetBeginAnnotation, CharacterOffsetEndAnnotation, TextAnnotation (for each token) | Tokenizes the text. This splits the text into roughly "words", using rules or methods suitable for the language being processed. Sometimes the tokens split up surface words in ways suitable for further NLP-processing, for example "isn't" becomes "is" and "n't". The tokenizer saves the beginning and end character offsets of each token in the input text. |
| [cleanxml](cleanxml.html) | CleanXmlAnnotator | XmlContextAnnotation | Remove xml tokens from the document. May use them to mark sentence ends or to extract metadata. |
| [docdate](docdate.html) | DocDateAnnotator | DocDateAnnotation | Allows user to specify dates for documents. |
| [ssplit](ssplit.html) | WordsToSentencesAnnotator | SentencesAnnotation | Splits a sequence of tokens into sentences. |
| [pos](pos.html) | POSTaggerAnnotator | PartOfSpeechAnnotation | Labels tokens with their POS tag. For more details see [this page](http://nlp.stanford.edu/software/tagger.html). |
| [lemma](lemma.html) | MorphaAnnotator | LemmaAnnotation | Generates the word lemmas for all tokens in the corpus. |
| [ner](ner.html) | NERCombinerAnnotator | NamedEntityTagAnnotation and NormalizedNamedEntityTagAnnotation | Recognizes named (PERSON, LOCATION, ORGANIZATION, MISC), numerical (MONEY, NUMBER, ORDINAL, PERCENT), and temporal (DATE, TIME, DURATION, SET) entities. Named entities are recognized using a combination of three CRF sequence taggers trained on various corpora, such as ACE and MUC. Numerical entities are recognized using a rule-based system. Numerical entities that require normalization, e.g., dates, are normalized to NormalizedNamedEntityTagAnnotation. For more details on the CRF tagger see [this page](http://nlp.stanford.edu/software/CRF-NER.html).  **Sub-annotators:** `docdate`, `regexner`, `tokensregex`, `entitymentions`, and `sutime` |
| [entitymentions](entitymentions.html) | EntityMentionsAnnotator | MentionsAnnotation | Group NER tagged tokens together into mentions.  **Run as part of:** `ner` | 
| [regexner](regexner.html) | TokensRegexNERAnnotator | NamedEntityTagAnnotation | Implements a simple, rule-based NER over token sequences using Java regular expressions. The goal of this Annotator is to provide a simple framework to incorporate NE labels that are not annotated in traditional NL corpora. For example, the default list of regular expressions that we distribute in the models file recognizes ideologies (IDEOLOGY), nationalities (NATIONALITY), religions (RELIGION), and titles (TITLE). Here is [a simple example](http://nlp.stanford.edu/software/regexner/) of how to use RegexNER. For more complex applications, you might consider [TokensRegex](tokensregex.html). |
| [tokensregex](tokensregex.html) | TokensRegexAnnotator | - | Runs a TokensRegex pipeline within a full NLP pipeline. |
| [parse](parse.html) | ParserAnnotator | TreeAnnotation, BasicDependenciesAnnotation, CollapsedDependenciesAnnotation, CollapsedCCProcessedDependenciesAnnotation | Provides full syntactic analysis, using both the constituent and the dependency representations. The constituent-based output is saved in TreeAnnotation. We generate three dependency-based outputs, as follows: basic, uncollapsed dependencies, saved in BasicDependenciesAnnotation; collapsed dependencies saved in CollapsedDependenciesAnnotation; and collapsed dependencies with processed coordinations, in CollapsedCCProcessedDependenciesAnnotation. Most users of our parser will prefer the latter representation. For more details on the parser, please see [this page](http://nlp.stanford.edu/software/lex-parser.html). For more details about the dependencies, please refer to [this page](http://nlp.stanford.edu/software/stanford-dependencies.html). |
| [depparse](depparse.html) | DependencyParseAnnotator | BasicDependenciesAnnotation, CollapsedDependenciesAnnotation, CollapsedCCProcessedDependenciesAnnotation | Provides a fast syntactic dependency parser. We generate three dependency-based outputs, as follows: basic, uncollapsed dependencies, saved in BasicDependenciesAnnotation; collapsed dependencies saved in CollapsedDependenciesAnnotation; and collapsed dependencies with processed coordinations, in CollapsedCCProcessedDependenciesAnnotation. Most users of our parser will prefer the latter representation. For details about the dependency software, see [this page](http://nlp.stanford.edu/software/nndep.html). For more details about dependency parsing in general, see [this page](http://nlp.stanford.edu/software/stanford-dependencies.html). |
| [coref](coref.html) | CorefAnnotator | CorefChainAnnotation | Performs coreference resolution on a document, building links between entity mentions that refer to the same entity.  Has a variety of modes, including rule-based, statistical, and neural.  **Sub-annotators:** `coref.mention` |
| [dcoref](coref.html) | DeterministicCorefAnnotator | CorefChainAnnotation | Implements both pronominal and nominal coreference resolution. The entire coreference graph (with head words of mentions as nodes) is saved in CorefChainAnnotation. For more details on the underlying coreference resolution algorithm, see [this page](http://nlp.stanford.edu/software/dcoref.html). |
| [relation](relation.html) | RelationExtractorAnnotator | MachineReadingAnnotations.RelationMentionsAnnotation | Stanford relation extractor is a Java implementation to find relations between two entities. The current relation extraction model is trained on the relation types (except the 'kill' relation) and data from the paper Roth and Yih, Global inference for entity and relation identification via a linear programming formulation, 2007, except instead of using the gold NER tags, we used the NER tags predicted by Stanford NER classifier to improve generalization. The default model predicts relations <tt>Live_In</tt>, <tt>Located_In</tt>, <tt>OrgBased_In</tt>, <tt>Work_For</tt>, and <tt>None</tt>. For more details of how to use and train your own model, see [this page](http://nlp.stanford.edu/software/relationExtractor.html) |
| [natlog](natlog.html) | NaturalLogicAnnotator | OperatorAnnotation, PolarityAnnotation | Marks quantifier scope and token polarity, according to natural logic semantics. Places an OperatorAnnotation on tokens which are quantifiers (or other natural logic operators), and a PolarityAnnotation on all tokens in the sentence. |
| [openie](openie.html) | OpenIEAnnotator | EntailedSentencesAnnotation, RelationTriplesAnnotation | Extract open-domain relation triples.  System description in this [paper](https://nlp.stanford.edu/pubs/2015angeli-openie.pdf) |
| [entitylink](entitylink.html) | WikidictAnnotator | WikipediaEntityAnnotation | Link entity mentions to Wikipedia entities |
| [kbp](kbp.html) | KBPAnnotator | KBPTriplesAnnotation | Extracts (subject, relation, object) triples from sentences, using a combination of a statistical model, patterns over tokens, and patterns over dependencies.  Extracts [TAC-KBP](https://tac.nist.gov/2017/KBP/) relations.  Details about models and rules can be found in our [write up](https://nlp.stanford.edu/pubs/zhang2016stanford.pdf) for the TAC-KBP 2016 competition. |
| [quote](quote.html) | QuoteAnnotator | QuotationAnnotation | Deterministically picks out quotes delimited by " or ' from a text. All top-level quotes are supplied by the top level annotation for a text. If a QuotationAnnotation corresponds to a quote that contains embedded quotes, these quotes will appear as embedded QuotationAnnotations that can be accessed from the QuotationAnnotation that they are embedded in. The QuoteAnnotator can handle multi-line and cross-paragraph quotes, but any embedded quotes must be delimited by a different kind of quotation mark than its parents. Does not depend on any other annotators. Support for unicode quotes is not yet present.  **Sub-annotators:** `quote.attribution` |
| [quote.attribution](quote.html) | QuoteAttributionAnnotator | - | Attribute quotes to speakers in the document.  **Run as part of:** `quote` |
| [sentiment](sentiment.html) | SentimentAnnotator | entimentCoreAnnotations.AnnotatedTree | Implements Socher et al's sentiment model.  Attaches a binarized tree of the sentence to the sentence level CoreMap.  The nodes of the tree then contain the annotations from RNNCoreAnnotations indicating the predicted class and scores for that subtree.  See the [sentiment page](http://nlp.stanford.edu/sentiment) for more information about this project. |
| [truecase](truecase.html) | TrueCaseAnnotator | TrueCaseAnnotation and TrueCaseTextAnnotation | Recognizes the true case of tokens in text where this information was lost, e.g., all upper case text. This is implemented with a discriminative model implemented using a CRF sequence tagger. The true case label, e.g., INIT_UPPER is saved in TrueCaseAnnotation. The token text adjusted to match its true case is saved as TrueCaseTextAnnotation. |
| [udfeats](udfeats.html) | UDFeatureAnnotator | CoNLLUFeats, CoarseTagAnnotation | Labels tokens with their Universal Dependencies universal part of speech (UPOS) and features. |

## Annotator Dependencies

| Property name | Annotator class name | Requirements |
| --- | --- | --- |
| [tokenize](tokenize.html) | TokenizerAnnotator | None |
| [cleanxml](cleanxml.html) | CleanXmlAnnotator | `tokenize` |
| [ssplit](ssplit.html) | WordsToSentenceAnnotator | `tokenize` |
| [docdate](docdate.html) | DocDateAnnotator | None |
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
| [entitylink](entitylink.html) | WikiDictAnnotator | `tokenize, ssplit, ner` |
| [kbp](kbp.html) | KBPAnnotator | `tokenize, ssplit, pos, lemma, parse, ner, coref` (Can also use `depparse` ; `coref` optional) |
| [quote](quote.html) | QuoteAnnotator | `tokenize, ssplit, pos, lemma, ner, depparse, coref` |

## Sub-Annotators

While every annotator can technically be run as a top-level component, in some cases it makes sense for one annotator to run
another as a sub-annotator.  For instance the `coref` annotator runs the `coref.mention` annotator (which identifies coref mentions) as a sub-annotator by default.  So instead of supplying an annotator list of `tokenize,ssplit,parse,coref.mention,coref` the list can just be `tokenize,ssplit,parse,coref`. Another example is the `ner` annotator running the `entitymentions` annotator to detect full entities.  Below is a table summarizing the annotator/sub-annotator relationships that currently exist in the pipeline.  By default annotators will generally run their sub-annotators.

| Annotator | Sub-Annotators |
| --- | --- |
| coref | coref.mention |
| ner | docdate,sutime,regexner,tokensregex,entitymentions |
| quote | quote.attribution |
