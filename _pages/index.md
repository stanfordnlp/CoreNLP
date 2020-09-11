---
layout: default
title: Overview
keywords: CoreNLP, Java, NLP, Natural Language Processing
type: first_page
permalink: '/index.html'
nav_order: 1
homepage: true
---


<p align="center">
   <img src="assets/images/corenlp-title.png">
</p>

[<i class="fab fa-java"></i> Download CoreNLP 4.1.0](http://nlp.stanford.edu/software/stanford-corenlp-latest.zip){: .btn .fs-5 .mr-2 .mb-md-0 }
[<i class="fab fa-github"></i> CoreNLP on GitHub](https://github.com/stanfordnlp/CoreNLP){: .btn .fs-5 .mr-2 .mb-md-0 }
[<i class="fab fa-sonatype"></i> CoreNLP on Maven](https://search.maven.org/artifact/edu.stanford.nlp/stanford-corenlp/4.0.0/jar){: .btn .fs-5 .mr-2 .mb-md-0 }

{: .no_toc }

## About

CoreNLP is your one stop shop for natural language processing in Java! CoreNLP enables users to derive linguistic annotations for text, including token
and sentence boundaries, parts of speech, named entities, numeric and time values, dependency and constituency parses, coreference, sentiment, 
quote attributions, and relations. CoreNLP currently supports 6 languages: Arabic, Chinese, English, French, German, and Spanish.

### Pipeline

The centerpiece of CoreNLP is the pipeline. Pipelines take in raw text, run a series of NLP annotators on the text, and produce a final
set of annotations.

<p align="center">
   <img src="assets/images/pipeline.png">
</p>

### CoreDocument

Pipelines produce CoreDocuments, data objects that contain all of the annotation information, accessible with a simple API, and serializable
to a Google Protocol Buffer.

<p align="center">
  <img src="assets/images/text-to-annotation.png">
</p> 

### Annotations

CoreNLP generates a variety of linguistic annotations, including:

#### Parts Of Speech

<p align="center">
  <img src="assets/images/pos.png">
</p> 

#### Named Entities

<p align="center">
  <img src="assets/images/ner.png">
</p> 

#### Dependency Parses

<p align="center">
  <img src="assets/images/depparse.png">
</p> 

#### Coreference

<p align="center">
  <img src="assets/images/coref.png">
</p> 

## Quickstart

* Download and unzip [CoreNLP 4.1.0](http://nlp.stanford.edu/software/stanford-corenlp-latest.zip)

* Download model jars for the language you want to work on and move the jars to the distribution directory.

| Language | model jar | version |
| :------- | :-------- | | :----- |
| Arabic  | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.1.0-models-arabic.jar) | 4.1.0 |
| Chinese | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.1.0-models-chinese.jar) | 4.1.0 |
| English | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.1.0-models-english.jar) | 4.1.0 |
| English (KBP) | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.1.0-models-english-kbp.jar) | 4.1.0 |
| French | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.1.0-models-french.jar) | 4.1.0 |
| German | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.1.0-models-german.jar) | 4.1.0 |
| Spanish | [download](http://nlp.stanford.edu/software/stanford-corenlp-4.1.0-models-spanish.jar) | 4.1.0 |

```bash
mv /path/to/stanford-corenlp-4.1.0-models-french.jar /path/to/stanford-corenlp-4.1.0
```

* Include the distribution directory in your CLASSPATH.

```bash
export CLASSPATH=$CLASSPATH:/path/to/stanford-corenlp-4.1.0/*:
```

* You're ready to go! There are many ways to run a CoreNLP pipeline. For instance here's how to run a pipeline on a text file.
The output will be available in a file called `input.txt.out`.

```bash
java -Xmx5g edu.stanford.nlp.pipeline.StanfordCoreNLP -file input.txt
```
## Programming languages and operating systems

Stanford CoreNLP is written in **Java**; recent releases  require
**Java 1.8+**. You need to have Java installed to run
CoreNLP. However, you can interact with CoreNLP via the command-line
or its web service;
many people use CoreNLP while writing their own code in Javascript,
Python, or some other language.

You can use Stanford CoreNLP from the [command-line](cmdline.html),
via its original Java
[programmatic API](api.html), via the object-oriented [simple API](https://stanfordnlp.github.io/CoreNLP/simple.html),
via [third party APIs](other-languages.html) for most major modern
programming languages, or via a [web service](corenlp-server.html).
It works on Linux, macOS, and Windows.

## License

The full Stanford CoreNLP is licensed under the [GNU General Public License](http://www.gnu.org/licenses/gpl.html)
v3 or later. More precisely, all the Stanford NLP
code is GPL v2+, but CoreNLP uses some Apache-licensed libraries,
and so our understanding is that the the composite is correctly
licensed as v3+. You can run almost all of CoreNLP under GPL v2; you
simply need to omit the time-related libraries, and then you lose the
functionality of SUTime.
Note that the license is the <i>full</i> GPL,
which allows many free uses, but not its use in 
[proprietary software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem) 
which is distributed to others.
For distributors of
[proprietary software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem),
CoreNLP is also available from Stanford under a
[commercial licensing](http://techfinder.stanford.edu/technology_detail.php?ID=29724)
You can contact us at
[java-nlp-support@lists.stanford.edu](mailto:java-nlp-support@lists.stanford.edu).
If you don't need a commercial license, but would like to support
maintenance of these tools, we welcome gift funding:
use [this form](http://giving.stanford.edu/goto/writeingift)
and write "Stanford NLP Group open source software" in the Special Instructions.


## Citing Stanford CoreNLP in papers

If you&rsquo;re just running the CoreNLP pipeline, please cite this CoreNLP paper:

> Manning, Christopher D., Mihai Surdeanu, John Bauer, Jenny Finkel, Steven J. Bethard, and David McClosky. 2014. [The Stanford CoreNLP Natural Language Processing Toolkit](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.pdf) In *Proceedings of the 52nd Annual Meeting of the Association for Computational Linguistics: System Demonstrations*, pp. 55-60. \[[pdf](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.bib)\]

If you&rsquo;re dealing in depth with particular annotators,
you&rsquo;re also encouraged to cite the papers that cover individual
components:
[POS tagging](http://nlp.stanford.edu/software/tagger.html),
[NER](http://nlp.stanford.edu/software/CRF-NER.html),
[constituency parsing](http://nlp.stanford.edu/software/lex-parser.html),
[dependency parsing](http://nlp.stanford.edu/software/nndep.html),
[coreference resolution](http://nlp.stanford.edu/software/dcoref.html),
[sentiment](http://nlp.stanford.edu/sentiment/), or [Open IE](http://nlp.stanford.edu/software/openie.html).
You can find more information on the Stanford NLP
[software pages](http://nlp.stanford.edu/software/) and/or
[publications page](http://nlp.stanford.edu/pubs/).
