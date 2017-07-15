---
title: TrueCaseAnnotator 
keywords: truecase
permalink: '/truecase.html'
---

## Description

Recognizes the “true” case of tokens (how it would be capitalized in
well-edited text) where this information was
lost, e.g., all upper case text. This is implemented with a
discriminative model using the CRF sequence tagger. A true
case category label, e.g., INIT_UPPER for each word is saved in `TrueCaseAnnotation`. The token
text adjusted to match its true case is saved under the 
`TrueCaseTextAnnotation`. There is an option to also overwrite the
TextAnnotation of the token, which will change the behavior of later
annotators (they will use the truecased text): `truecase.overwriteText`. The original text
prior to any normalization can still be retrieved from the
`OriginalTextAnnotation`. (The JSON output format is a text output format that
contains these annotations.) At present, we only have a trained
`truecase` model for English, but models could be trained for other languages.

Use of the `truecase` annotator is one of two good ways of dealing
with texts that mostly or entirely lack case distinctions. The other
is to use [caseless models](caseless.html).

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| truecase | TrueCaseAnnotator | TrueCaseAnnotation and TrueCaseTextAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| truecase.model | String | edu/stanford/nlp/models/truecase/truecasing.fast.caseless.qn.ser.gz | The truecasing model to use. |
| truecase.bias | String | INIT\_UPPER:-0.7,UPPER:-0.7,O:0 | Biases to choose certain behaviors. You can use this to adjust the proclivities of the truecaser. The truecaser classes are: UPPER, LOWER, INIT\_UPPER, and O (for mixed case words like _McVey_). |
| truecase.mixedcasefile | String | edu/stanford/nlp/models/truecase/MixDisambiguation.list | When the classifier chooses mixed case classification, the form in this file (if any) is used, otherwise the input token is left unchanged. |
| truecase.overwriteText | boolean | false | Whether the truecased token form should be used to overwrite the TextAnnotation, affecting the behavior of later annotators in a pipeline. |
| truecase.verbose | boolean | false | Whether to run more verbosely. |

## Example

To use the `truecase` model to work with uncased text, place it after
sentence splitting but before other annotators that use case
information.  Here is an example:

    % cat lakers.txt
    lonzo ball talked about kobe bryant after the lakers game.

With the default English models, no entities (and no proper nouns) are
found:

    % java edu.stanford.nlp.pipeline.StanfordCoreNLP -file lakers.txt -outputFormat conll -annotators tokenize,ssplit,pos,lemma,ner
    % cat lakers.txt.conll 
    1	lonzo	lonzo	NN	O	_	_
    2	ball	ball	NN	O	_	_
    3	talked	talk	VBD	O	_	_
    4	about	about	IN	O	_	_
    5	kobe	kobe	NN	O	_	_
    6	bryant	bryant	NN	O	_	_
    7	after	after	IN	O	_	_
    8	the	the	DT	O	_	_
    9	lakers	laker	NNS	O	_	_
    10	game	game	NN	O	_	_
    11	.	.	.	O	_	_

However, Instead, if we  run truecasing prior to POS tagging and NER,
then we get:

    % java edu.stanford.nlp.pipeline.StanfordCoreNLP -outputFormat conll -annotators tokenize,ssplit,truecase,pos,lemma,ner -file lakers.txt -truecase.overwriteText
    % cat lakers.txt.conll 
    1	Lonzo	Lonzo	NNP	PERSON	_	_
    2	ball	ball	NN	O	_	_
    3	talked	talk	VBD	O	_	_
    4	about	about	IN	O	_	_
    5	Kobe	Kobe	NNP	PERSON	_	_
    6	Bryant	Bryant	NNP	PERSON	_	_
    7	after	after	IN	O	_	_
    8	the	the	DT	O	_	_
    9	Lakers	Lakers	NNPS	ORGANIZATION	_	_
    10	game	game	NN	O	_	_
    11	.	.	.	O	_	_

Now, the organization *Lakers* is recognized, and in general nearly
all the entity words are tagged as proper nouns with the correct
entity label. However, the model fails to get *ball*, which remains a common noun. Of course, this is a fairly hard word to get right in caseless text, since *ball* is a quite frequent common noun.
