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
| [3.5.0](https://nlp.stanford.edu/software/stanford-parser-full-2014-10-31.zip) | 2014-10-31 | Upgrade to Java 8; add [neural-network dependency parser](https://nlp.stanford.edu/software/nndep.html) | [caseless](http://nlp.stanford.edu/software/stanford-corenlp-caseless-2014-02-25-models.jar) , [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2014-10-23-models.jar) , [shift reduce parser](http://nlp.stanford.edu/software/stanford-srparser-2014-10-23-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2014-10-23-models.jar) |
| [3.4.1](https://nlp.stanford.edu/software/stanford-parser-full-2014-08-27.zip) | 2014-08-27 | Spanish models added. | [caseless](http://nlp.stanford.edu/software/stanford-corenlp-caseless-2014-02-25-models.jar) , [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2014-02-24-models.jar) , [shift reduce parser](http://nlp.stanford.edu/software/stanford-srparser-2014-08-28-models.jar) , [spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2014-08-26-models.jar) |
| [3.4](https://nlp.stanford.edu/software/stanford-parser-full-2014-06-16.zip) | 2014-06-16 | Shift-reduce parser, dependency improvements, French parser uses CC tagset | [caseless](http://nlp.stanford.edu/software/stanford-corenlp-caseless-2014-02-25-models.jar) , [chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2014-02-24-models.jar) , [shift reduce parser](http://nlp.stanford.edu/software/stanford-srparser-2014-07-01-models.jar) |
| [3.3.1](https://nlp.stanford.edu/software/stanford-parser-full-2014-01-04.zip) | 2014-01-04 | English dependency "infmod" and "partmod" combined into "vmod", other minor dependency improvements | |
| [3.3.0](https://nlp.stanford.edu/software/stanford-parser-full-2013-11-12.zip) | 2013-11-12 | English dependency "attr" removed, other dependency improvements, imperative training data added | |
| [3.2.0](https://nlp.stanford.edu/software/stanford-parser-full-2013-06-20.zip) | 2013-06-20 | New CVG based English model with higher accuracy | |
| [2.0.5](https://nlp.stanford.edu/software/stanford-parser-2013-04-05.zip) | 2013-04-05 | Dependency improvements, -nthreads option, ctb7 model | |
| [2.0.4](https://nlp.stanford.edu/software/stanford-parser-2012-11-12.zip) | 2012-11-12 | Improved dependency code extraction efficiency, other dependency changes | |
| [2.0.3](https://nlp.stanford.edu/software/stanford-parser-2012-07-09.tgz) | 2012-07-09 | Minor bug fixes | |
| [2.0.2](https://nlp.stanford.edu/software/stanford-parser-2012-05-22.tgz) | 2012-05-22 | Some models now support training with extra tagged, non-tree data | |
| [2.0.1](https://nlp.stanford.edu/software/stanford-parser-2012-03-09.tgz) | 2012-03-09 | Caseless English model included, bugfix for enforced tags | |
| [2.0](https://nlp.stanford.edu/software/stanford-parser-2012-02-03.tgz) | 2012-02-03 | Threadsafe! | |
| [1.6.9](https://nlp.stanford.edu/software/stanford-parser-2011-09-14.tgz) | 2011-09-14 | Improved recognition of imperatives, dependencies now explicitely include a root, parser knows osprey is a noun | |
| [1.6.8](https://nlp.stanford.edu/software/stanford-parser-2011-08-04.tgz) | 2011-08-04 | New French model, improved foreign language models, bug fixes | |
| [1.6.7](https://nlp.stanford.edu/software/stanford-parser-2011-05-18.tgz) | 2011-05-18 | Minor bug fixes. | |
| [1.6.6](https://nlp.stanford.edu/software/stanford-parser-2011-04-20.tgz) | 2011-04-20 | Internal code and API changes (ArrayLists rather than Sentence; use of CoreLabel objects) to match tagger and CoreNLP. | |
| [1.6.5](https://nlp.stanford.edu/software/stanford-parser-2010-11-30.tgz) | 2010-11-30 | Further improvements to English Stanford Dependencies and other minor changes | |
| [1.6.4](https://nlp.stanford.edu/software/stanford-parser-2010-08-20.tgz) | 2010-08-20 | More minor bug fixes and improvements to English Stanford Dependencies and question parsing | |
| [1.6.3](https://nlp.stanford.edu/software/stanford-parser-2010-07-09.tgz) | 2010-07-09 | Improvements to English Stanford Dependencies and question parsing, minor bug fixes | |
| [1.6.2](https://nlp.stanford.edu/software/stanford-parser-2010-02-26.tgz) | 2010-02-26 | Improvements to Arabic parser models, and to English and Chinese Stanford Dependencies | |
| [1.6.1](https://nlp.stanford.edu/software/stanford-parser-2008-10-26.tgz) | 2008-10-26 | Slightly improved Arabic and German parsing, and Stanford Dependencies | |
| [1.6](https://nlp.stanford.edu/software/stanford-parser-2007-08-19.tar.gz) | 2007-08-19 | Added Arabic, k-best PCCFG parsing; improved English grammatical relations | |
| [1.5.1](https://nlp.stanford.edu/software/StanfordParser-2006-06-11.tar.gz) | 2006-06-11 | Improved English and Chinese grammatical relations; fixed UTF-8 handling | |
| [1.5](https://nlp.stanford.edu/software/StanfordParser-2005-07-21.tar.gz) | 2005-07-21 | Added grammatical relations output; fixed bugs introduced in 1.4 | |
| [1.4](https://nlp.stanford.edu/software/StanfordParser-1.4.tar.gz) | 2004-03-24 | Made PCFG faster again (by FSA minimization); added German support | |
| [1.3](https://nlp.stanford.edu/software/StanfordParser-1.3.tar.gz) | 2003-09-06 | Made parser over twice as fast; added tokenization options | |
| 1.2 | 2003-07-20 | Halved PCFG memory usage; added support for Chinese | |
| 1.1 | 2003-03-25 | Improved parsing speed; included GUI, improved PCFG grammar | |
| 1.0 | 2002-12-05 | Initial release | |

