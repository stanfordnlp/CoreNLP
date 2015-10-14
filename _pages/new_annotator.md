---
title: Adding a new annotator 
keywords: new-annotator
permalink: '/new_annotator.html'
---

StanfordCoreNLP also has the capacity to add a new annotator by
reflection without altering the code in StanfordCoreNLP.java.  To
create a new annotator, extend the class
`edu.stanford.nlp.pipeline.Annotator` and define a constructor with the
signature `(String, Properties)`.  Then, add the property
`customAnnotatorClass.FOO=BAR` to the properties used to create the
pipeline.  If `FOO` is then added to the list of annotators, the class
`BAR` will be created, with the name used to create it and the
properties file passed in.

