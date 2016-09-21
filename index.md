---
title: About
keywords: overview, about
type: first_page
homepage: true
---

## About

Stanford CoreNLP provides a set of natural language analysis
tools. It can give the base
forms of words, their parts of speech, whether they are names of
companies, people, etc., normalize dates, times, and numeric quantities,
and mark up the structure of sentences in terms of
phrases and word dependencies, indicate which noun phrases refer to
the same entities, indicate sentiment, extract open-class relations between mentions, etc. 

Choose Stanford CoreNLP if you need:

* An integrated toolkit with a good range of grammatical analysis tools
* Fast, reliable analysis of arbitrary texts
* The overall highest quality text analytics
* Support for a number of major (human) languages
* Interfaces available for various major modern programming languages
* Ability to run as a simple web service

Stanford CoreNLP is an integrated framework. Its goal is to
make it very easy to apply a bunch of linguistic analysis tools to a piece
of text. A CoreNLP tool pipeline can be run on a piece of plain text with
just two lines of code. It is designed to be highly
flexible and extensible.  With a single option you can change which
tools should be enabled and which should be disabled. 
Stanford CoreNLP integrates many of Stanford&rsquo;s NLP tools,
including [the part-of-speech (POS) tagger](http://nlp.stanford.edu/software/tagger.html), 
[the named entity recognizer (NER)](http://nlp.stanford.edu/software/CRF-NER.html),
[the parser](http://nlp.stanford.edu/software/lex-parser.html),
[the coreference resolution system](http://nlp.stanford.edu/software/dcoref.html),
[sentiment analysis](http://nlp.stanford.edu/sentiment/),
[bootstrapped pattern learning](http://nlp.stanford.edu/software/patternslearning.html),
and the
[open information extraction](http://nlp.stanford.edu/software/openie.html)
tools.
Its analyses provide the foundational building blocks for
higher-level and domain-specific text understanding applications.

![CoreNLP screenshot]({{ site.github.url }}/images/Xi-Jinping.png)


## Download

Stanford CoreNLP can be downloaded via the link below. This will download a large (536 MB) zip file containing (1) the CoreNLP code jar, (2) the CoreNLP models jar (required in your classpath for most tasks) (3) the libraries required to run CoreNLP, and (4) documentation / source code for the project. Unzip this file, open the folder that results and you're ready to use it.

<div style="text-align:center; margin-top: 5ex; margin-bottom:5ex;"> <a class="downloadbutton" href="http://nlp.stanford.edu/software/stanford-corenlp-full-2015-12-09.zip">Download CoreNLP 3.6.0</a> </div>

Alternatively, Stanford CoreNLP is [available on **Maven Central**](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22stanford-corenlp%22).
Source is [available on **GitHub**](https://github.com/stanfordnlp/CoreNLP).
For more information on obtaining CoreNLP, see the [download page](download.html).

You can find the jars containing the models for each language we support in the table below.

| Language | model jar | version |
| :------- | :-------- | | :----- |
| Chinese | [download](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2016-01-19-models.jar) | 3.6.0 |
| English | [download](http://nlp.stanford.edu/software/stanford-english-corenlp-2016-01-10-models.jar) | 3.6.0 |
| French | [download](http://nlp.stanford.edu/software/stanford-french-corenlp-2016-01-14-models.jar) | 3.6.0 |
| German | [download](http://nlp.stanford.edu/software/stanford-german-2016-01-19-models.jar) | 3.6.0 |
| Spanish | [download](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2015-10-14-models.jar) | 3.6.0 |

## Human languages supported

The basic distribution provides model files for the analysis of well-edited **English**,
but the engine is compatible with models for other languages. We provide
packaged models for [Chinese](http://nlp.stanford.edu/software/stanford-chinese-corenlp-2016-01-19-models.jar), [French](http://nlp.stanford.edu/software/stanford-french-corenlp-2016-01-14-models.jar), 
[German](http://nlp.stanford.edu/software/stanford-german-2016-01-19-models.jar), and [Spanish](http://nlp.stanford.edu/software/stanford-spanish-corenlp-2015-10-14-models.jar).
We also provide a jar that contains all of our
[English](http://nlp.stanford.edu/software/stanford-english-corenlp-2016-01-10-models.jar)
models, which includes various variant models, and in particular has
one optimized for working with uncased English (e.g., mostly or all
either uppercase or lowercase).
Stanford NLP models for  **Arabic** are
also usable inside CoreNLP, and other people have built models for
some other languages.  You can find out more about using CoreNLP with
various human languages on the
[other human languages](human-languages.html) page.


## Programming languages and operating systems

Stanford CoreNLP is written in **Java**; current releases  require **Java 1.8+**. 

You can use Stanford CoreNLP from the command-line, via its Java
programmatic API, via [third party APIs](other-languages.html) for most major modern
programming languages, or via a [service](corenlp-server.html).
It works on Linux, OS X, and Windows.

## License

Stanford CoreNLP is licensed under the [GNU General Public License](http://www.gnu.org/licenses/gpl.html)
(v3 or later; in general Stanford NLP
code is GPL v2+, but CoreNLP uses several Apache-licensed libraries, and so the composite is v3+).
Note that the license is the <i>full</i> GPL,
which allows many free uses, but not its use in 
[proprietary software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem) 
which is distributed to others.
For distributors of
[proprietary software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem),
[commercial licensing](http://techfinder.stanford.edu/technology_detail.php?ID=29724)
is available from Stanford. You can contact us at 
[java-nlp-support@lists.stanford.edu](mailto:java-nlp-support@lists.stanford.edu).
If you don't need a commercial license, but would like to support
maintenance of these tools, we welcome gift funding:
use [this form](http://giving.stanford.edu/goto/writeingift)
and write "Stanford NLP Group open source software" in the Special Instructions.


## Citing Stanford CoreNLP in papers

If you&rsquo;re just running the CoreNLP pipeline, please cite this CoreNLP
demo paper: 

> Manning, Christopher D., Mihai Surdeanu, John Bauer, Jenny Finkel, Steven J. Bethard, and David McClosky. 2014. [The Stanford CoreNLP Natural Language Processing Toolkit](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.pdf) In *Proceedings of the 52nd Annual Meeting of the Association for Computational Linguistics: System Demonstrations*, pp. 55-60. \[[pdf](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.bib)\]

If you&rsquo;re dealing in depth with particular annotators,
you&rsquo;re also encouraged to cite the papers that cover individual
components:
[POS tagging](http://nlp.stanford.edu/software/tagger.shtml),
[NER](http://nlp.stanford.edu/software/CRF-NER.shtml),
[parsing (with parse annotator)](http://nlp.stanford.edu/software/lex-parser.shtml),
[dependency parsing (with depparse annotator)](http://nlp.stanford.edu/software/nndep.shtml),
[coreference resolution](http://nlp.stanford.edu/software/dcoref.shtml),
[sentiment](http://nlp.stanford.edu/sentiment/), or [Open IE](http://nlp.stanford.edu/software/openie.html).
You can find more information on the Stanford NLP
[software pages](http://nlp.stanford.edu/software/) and/or
[publications page](http://nlp.stanford.edu/pubs/).


