---
title: DependencyParseAnnotator 
keywords: depparse
permalink: '/depparse.html'
---

## Description

Provides a fast syntactic dependency parser. We generate three dependency-based outputs, as follows: basic, uncollapsed dependencies, saved in BasicDependenciesAnnotation; collapsed dependencies saved in CollapsedDependenciesAnnotation; and collapsed dependencies with processed coordinations, in CollapsedCCProcessedDependenciesAnnotation. Most users of our parser will prefer the latter representation.

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| depparse | DependencyParseAnnotator | BasicDependenciesAnnotation, CollapsedDependenciesAnnotation, CollapsedCCProcessedDependenciesAnnotation |

## Options

* depparse.model: dependency parsing model to use. There is no need to
  explicitly set this option, unless you want to use a different parsing
  model than the default. By default, this is set to the UD parsing model included in the stanford-corenlp-models JAR file.

* depparse.extradependencies: Whether to include extra (enhanced)
  dependencies in the output. The default is NONE (basic dependencies)
  and this can have other values of the GrammaticalStructure.Extras
  enum, such as SUBJ_ONLY or MAXIMAL (all extra dependencies).

## More information 

For details about the dependency software, see [this page](http://nlp.stanford.edu/software/nndep.shtml). For more details about dependency parsing in general, see [this page](http://nlp.stanford.edu/software/stanford-dependencies.shtml).