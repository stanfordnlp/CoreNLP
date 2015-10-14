---
title: DeterministicCorefAnnotator 
keywords: dcoref
permalink: '/dcoref.html'
---

## Description

Implements both pronominal and nominal coreference resolution. The entire coreference graph (with head words of mentions as nodes) is saved in CorefChainAnnotation. 

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| dcoref | DeterministicCorefAnnotator | CorefChainAnnotation | 

## Options

* dcoref.sievePasses: list of sieve modules to enable in the system, specified as a comma-separated list of class names. By default, this property is set to include: "edu.stanford.nlp.dcoref.sievepasses.MarkRole, edu.stanford.nlp.dcoref.sievepasses.DiscourseMatch, edu.stanford.nlp.dcoref.sievepasses.ExactStringMatch, edu.stanford.nlp.dcoref.sievepasses.RelaxedExactStringMatch, edu.stanford.nlp.dcoref.sievepasses.PreciseConstructs, edu.stanford.nlp.dcoref.sievepasses.StrictHeadMatch1, edu.stanford.nlp.dcoref.sievepasses.StrictHeadMatch2, edu.stanford.nlp.dcoref.sievepasses.StrictHeadMatch3, edu.stanford.nlp.dcoref.sievepasses.StrictHeadMatch4, edu.stanford.nlp.dcoref.sievepasses.RelaxedHeadMatch, edu.stanford.nlp.dcoref.sievepasses.PronounMatch".  The default value can be found in Constants.SIEVEPASSES.
* dcoref.demonym: list of demonyms from <a href="http://en.wikipedia.org/wiki/List_of_adjectival_forms_of_place_names">http://en.wikipedia.org/wiki/List_of_adjectival_forms_of_place_names</a>. The format of this file is: location TAB singular gentilic form TAB plural gentilic form, e.g., "Algeria Algerian Algerians".
* dcoref.animate and dcoref.inanimate: lists of animate/inanimate words, from (Ji and Lin, 2009). The format is one word per line.
* dcoref.male, dcoref.female, dcoref.neutral: lists of words of male/female/neutral gender, from (Bergsma and Lin, 2006) and (Ji and Lin, 2009). The format is one word per line.
* dcoref.plural and dcoref.singular: lists of words that are plural or singular, from (Bergsma and Lin, 2006). The format is one word per line. All the above dictionaries are already set to the files included in the stanford-corenlp-models JAR file, but they can easily be adjusted to your needs by setting these properties.
* dcoref.maxdist: the maximum distance at which to look for mentions.  Can help keep the runtime down in long documents.
* oldCorefFormat: produce a CorefGraphAnnotation, the output format used in releases v1.0.3 or earlier.  Note that this uses quadratic memory rather than linear.

## More information 

For more details on the underlying coreference resolution algorithm, see [this page](http://nlp.stanford.edu/software/dcoref.shtml).
