---
title: TrueCaseAnnotator 
keywords: truecase
permalink: '/truecase.html'
---

## Description

Recognizes the true case of tokens in text where this information was
lost, e.g., all upper case text. This is implemented with a
discriminative model implemented using a CRF sequence tagger. The true
case label, e.g., INIT_UPPER is saved in TrueCaseAnnotation. The token
text adjusted to match its true case is saved as
TrueCaseTextAnnotation. There is an option to also overwrite the
TextAnnotation of the token, which will change the behavior of later
annotators (they will use the truecased text). The original text
annotation prior to normalization can be retrieved from the
OriginalTextAnnotation. (The JSON format is a text output format that
preserves that writes this information.)

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| truecase | TrueCaseAnnotator | TrueCaseAnnotation and TrueCaseTextAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| truecase.model | String | "edu/stanford/nlp/models/truecase/truecasing.fast.caseless.qn.ser.gz" | The truecasing model to use. |
| truecase.bias | String | "INIT_UPPER:-0.7,UPPER:-0.7,O:0" | Biases to choose certain behaviors. You can use this to adjust the proclivities of the truecaser. |
| truecase.mixedcasefile | String | "edu/stanford/nlp/models/truecase/MixDisambiguation.list" | When the classifier chooses "MixedCase" classification, the form in this file (if any) is used, otherwise the input token is left unchanged. |
| truecase.overwriteText | boolean | false | Whether the truecased token form should be used to overwrite the TextAnnotation, affecting the behavior of later annotators in a pipeline. |
| truecase.verbose | boolean | false | Whether to run more verbosely. |
