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

The Stanford Parser can be used to generate constituency and dependency parses of sentences for a variety of languages. The package includes PCFG, Shift Reduce, and Neural Dependency parsers. To fully utilize the parser, also make sure to download the models jar for the specific language you are interested in. Links to models jars provided below in History section or [here](https://stanfordnlp.github.io/CoreNLP/download.html). 

[<i class="fab fa-java"></i> Download Stanford Parser 4.2.0](http://nlp.stanford.edu/software/stanford-parser-4.2.0.zip){: .btn .fs-5 .mr-2 .mb-md-0 }

You can consult this legacy [FAQ](https://nlp.stanford.edu/software/parser-faq.html) for more info.

## Demo

You can see demonstrations of the various parsers [here](https://corenlp.run).

## Differences between Standalone and CoreNLP

If you are using Stanford NLP software for non-commercial purposes, you should use the full CoreNLP package.

Parsing requires tokenization and in some cases part-of-speech tagging. The Stanford Parser distribution includes English tokenization, but does not provide tokenization used for French, German, and Spanish. Access to that tokenization requires using the full CoreNLP package. Likewise usage of the part-of-speech tagging models requires the license for the Stanford POS tagger or full CoreNLP distribution.

## License

The parser code is dual licensed (in a similar manner to MySQL, etc.). Open source licensing is under the full GPL, which allows many free uses. For distributors of proprietary software, commercial licensing is available. (Fine print: The traditional (dynamic programmed) Stanford Parser does part-of-speech tagging as it works, but the newer constituency and neural network dependency shift-reduce parsers require pre-tagged input. For convenience, we include the part-of-speech tagger code, but not models with the parser download. However, if you want to use these parsers under a commercial license, then you need a license to both the Stanford Parser and the Stanford POS tagger. Or you can get the whole bundle of Stanford CoreNLP.) If you don't need a commercial license, but would like to support maintenance of these tools, we welcome gift funding: use [this form](https://makeagift.stanford.edu/get/page/makeagift?mop=CC&gfty=G&pgnTPC=399&stp=270&cturl=close&olc=21029) and write "Stanford NLP Group open source software" in the Special Instructions.

## History

| Version | Date&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; | Changes | Models |
| :--- | :----------------------------------- | :--- | :--- | 
| [4.2.0](http://nlp.stanford.edu/software/stanford-parser-4.2.0.zip) | 2020-11-17 | Retrain English models with treebank fixes |  [arabic](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-arabic.jar), [chinese](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-chinese.jar) , [english](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-english.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-english-kbp.jar), [french](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-french.jar) , [german](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-german.jar) , [spanish](http://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-spanish.jar) |
| [4.0.0](http://nlp.stanford.edu/software/stanford-parser-4.0.0.zip) | 2020-04-19 | Model tokenization updated to UDv2.0 |  [arabic](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-arabic.jar), [chinese](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-chinese.jar) , [english](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-english.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-english-kbp.jar), [french](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-french.jar) , [german](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-german.jar) , [spanish](http://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-spanish.jar) |
| [3.9.2](http://nlp.stanford.edu/software/stanford-parser-full-2018-10-17.zip) | 2018-10-17 | Updated for compatibility | [arabic](http://nlp.stanford.edu/software/stanford-arabic-corenlp-2018-10-05-models.jar), [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2018-10-05-models.jar) , [english](http://nlp.stanford.edu/software/stanford-english-corenlp-2018-10-05-models.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-english-kbp-corenlp-2018-10-05-models.jar), [french](http://nlp.stanford.edu/software/stanford-french-corenlp-2018-10-05-models.jar) , [german](http://nlp.stanford.edu/software/stanford-german-corenlp-2018-10-05-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2018-10-05-models.jar) |
| [3.9.1](https://nlp.stanford.edu/software/stanford-parser-full-2018-02-27.zip) | 2018-02-27 | new French and Spanish UD models, misc. UD enhancements, bug fixes| [arabic](http://nlp.stanford.edu/software/stanford-arabic-corenlp-2018-02-27-models.jar), [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2018-02-27-models.jar) , [english](http://nlp.stanford.edu/software/stanford-english-corenlp-2018-02-27-models.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-english-kbp-corenlp-2018-02-27-models.jar), [french](http://nlp.stanford.edu/software/stanford-french-corenlp-2018-02-27-models.jar) , [german](http://nlp.stanford.edu/software/stanford-german-corenlp-2018-02-27-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2018-02-27-models.jar) |
| [3.8.0](https://nlp.stanford.edu/software/stanford-parser-full-2017-06-09.zip) | 2017-06-09 | Updated for compatibility | [arabic](http://nlp.stanford.edu/software/stanford-arabic-corenlp-2017-06-09-models.jar), [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2017-06-09-models.jar) , [english](http://nlp.stanford.edu/software/stanford-english-corenlp-2017-06-09-models.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-english-kbp-corenlp-2017-06-09-models.jar), [french](http://nlp.stanford.edu/software/stanford-french-corenlp-2017-06-09-models.jar) , [german](http://nlp.stanford.edu/software/stanford-german-corenlp-2017-06-09-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2017-06-09-models.jar) | 
| [3.7.0](https://nlp.stanford.edu/software/stanford-parser-full-2016-10-31.zip) | 2016-10-31 | new UD models | [arabic](http://nlp.stanford.edu/software/stanford-arabic-corenlp-2016-10-31-models.jar), [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2016-10-31-models.jar) , [english](http://nlp.stanford.edu/software/stanford-english-corenlp-2016-10-31-models.jar) , [english (kbp)](http://nlp.stanford.edu/software/stanford-english-kbp-corenlp-2016-10-31-models.jar), [french](http://nlp.stanford.edu/software/stanford-french-corenlp-2016-10-31-models.jar) , [german](http://nlp.stanford.edu/software/stanford-german-corenlp-2016-10-31-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2016-10-31-models.jar) |
| [3.6.0](https://nlp.stanford.edu/software/stanford-parser-full-2015-12-09.zip) | 2015-12-09 | Updated for compatibility | [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2016-01-19-models.jar) , [english](http://nlp.stanford.edu/software/stanford-english-corenlp-2016-01-10-models.jar) , [french](http://nlp.stanford.edu/software/stanford-french-corenlp-2016-01-14-models.jar) , [german](http://nlp.stanford.edu/software/stanford-german-2016-01-19-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2015-10-14-models.jar) |
| [3.5.2](https://nlp.stanford.edu/software/stanford-parser-full-2015-04-20.zip) | 2015-04-20 | Switch to universal dependencies | [caseless](http://nlp.stanford.edu/software/stanford-corenlp-caseless-2015-04-20-models.jar) , [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2015-04-20-models.jar) , [shift reduce parser](http://nlp.stanford.edu/software/stanford-srparser-2014-10-23-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2015-01-08-models.jar) |
