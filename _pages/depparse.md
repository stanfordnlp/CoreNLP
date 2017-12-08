---
title: DependencyParseAnnotator 
keywords: depparse
permalink: '/depparse.html'
---

## Description

Provides a fast syntactic dependency parser. We generate three dependency-based outputs, as follows: basic, uncollapsed dependencies, saved in BasicDependenciesAnnotation; enhanced dependencies saved in EnhancedDependenciesAnnotation; and enhanced++ dependencies in EnhancedPlusPlusDependenciesAnnotation. Most users of our parser will prefer the latter representation.

This is a separate annotator for a direct dependency parser. These
parsers require prior part-of-speech tagging. If you need constituency
parses then you should look at the `parse` annotator.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| depparse | DependencyParseAnnotator | BasicDependenciesAnnotation, EnhancedDependenciesAnnotation, EnhancedPlusPlusDependenciesAnnotation |

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| depparse.model | file, classpath, or URL | edu/stanford/nlp/models/parser/nndep/english\_UD.gz | Dependency parsing model to use. There is no need to explicitly set this option, unless you want to use a different parsing model than the default. By default, this is set to the UD parsing model included in the stanford-corenlp-models JAR file. |



## More information 

For details about the dependency software, see [this page](http://nlp.stanford.edu/software/nndep.html). For more details about dependency parsing in general, see [this page](http://nlp.stanford.edu/software/stanford-dependencies.html).
