---
layout: page
title: Parser
keywords: parser standalone
permalink: '/parser-standalone.html'
nav_order: 6
toc: false
parent: Standalone Distributions
---

## About

The Stanford Parser can be used to generate constituency and dependency parses of sentences for a variety of languages. The package includes PCFG, Shift Reduce, and Neural Dependency parsers.

[<i class="fab fa-java"></i> Download Stanford Parser 4.2.0](http://nlp.stanford.edu/software/stanford-parser-4.2.0.zip){: .btn .fs-5 .mr-2 .mb-md-0 }

## License

The parser code is dual licensed (in a similar manner to MySQL, etc.). Open source licensing is under the full GPL, which allows many free uses. For distributors of proprietary software, commercial licensing is available. (Fine print: The traditional (dynamic programmed) Stanford Parser does part-of-speech tagging as it works, but the newer constituency and neural network dependency shift-reduce parsers require pre-tagged input. For convenience, we include the part-of-speech tagger code, but not models with the parser download. However, if you want to use these parsers under a commercial license, then you need a license to both the Stanford Parser and the Stanford POS tagger. Or you can get the whole bundle of Stanford CoreNLP.) If you don't need a commercial license, but would like to support maintenance of these tools, we welcome gift funding: use this form and write "Stanford NLP Group open source software" in the Special Instructions.

## History

| Version | Date&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Changes | Models |
| :--- | :----------------------------------- | :--- | :--- | 
| [4.2.0](http://nlp.stanford.edu/software/stanford-parser-4.2.0.zip) | 2020-11-17 | Retrain English models with treebank fixes |  [arabic](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-arabic.jar), [chinese](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-chinese.jar) , [english](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-english.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-english-kbp.jar), [french](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-french.jar) , [german](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-german.jar) , [spanish](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-spanish.jar) |
| [4.0.0](http://nlp.stanford.edu/software/stanford-parser-4.0.0.zip) | 2020-04-19 | Model tokenization updated to UDv2.0 |  [arabic](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-arabic.jar), [chinese](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-chinese.jar) , [english](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-english.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-english-kbp.jar), [french](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-french.jar) , [german](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-german.jar) , [spanish](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-spanish.jar) |
| [3.9.2](http://nlp.stanford.edu/software/stanford-parser-full-2018-10-17.zip) | 2018-10-17 | Updated for compatibility | [arabic](http://nlp.stanford.edu/software/stanford-arabic-corenlp-2018-10-05-models.jar), [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2018-10-05-models.jar) , [english](http://nlp.stanford.edu/software/stanford-english-corenlp-2018-10-05-models.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-english-kbp-corenlp-2018-10-05-models.jar), [french](http://nlp.stanford.edu/software/stanford-french-corenlp-2018-10-05-models.jar) , [german](http://nlp.stanford.edu/software/stanford-german-corenlp-2018-10-05-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2018-10-05-models.jar) |
