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

Stanford CoreNLP is an integrated framework. Its goal is to
make it very easy to apply a bunch of linguistic analysis tools to a piece
of text. Starting from plain text, you can run all the tools on it with
just two lines of code. It is designed to be highly
flexible and extensible.  With a single option you can change which
tools should be enabled and which should be disabled. 
Stanford CoreNLP integrates many of Stanford&rsquo;s NLP tools,
including [the part-of-speech (POS) tagger](http://nlp.stanford.edu/software/tagger.shtml), 
[the named entity recognizer (NER)](http://nlp.stanford.edu/software/CRF-NER.shtml),
[the parser](http://nlp.stanford.edu/software/lex-parser.shtml),
[the coreference resolution system](http://nlp.stanford.edu/software/dcoref.shtml),
[sentiment analysis](http://nlp.stanford.edu/sentiment/), and
[the bootstrapped pattern learning](http://nlp.stanford.edu/software/patternslearning.shtml) tools.
Its analyses provide the foundational building blocks for
higher-level and domain-specific text understanding applications.

## Download

Stanford CoreNLP can be downloaded via the link below. This will download a large (536 MB) zip file containing (1) the CoreNLP code jar, (2) the CoreNLP models jar (required in your classpath for most tasks) (3) the libraries required to run CoreNLP, and (4) documentation / source code for the project.

<div style="text-align:center; margin-top: 5ex; margin-bottom:5ex;"> <a class="downloadbutton" href="http://nlp.stanford.edu/software/stanford-corenlp-full-2015-12-09.zip">Download CoreNLP 3.6.0</a> </div>

For more information, see the [download page](download.html).

## Human languages supported

The basic distribution provides model files for the analysis of **English**,
but the engine is compatible with models for other languages. We provide
packaged models for **Chinese** and **Spanish**, and
Stanford NLP models for **French**, **German**, and **Arabic** are
also usable inside CoreNLP.

| Annotator | ar | zh | en | fr | de | es |
| ------------ |:---:|:---:|:---:|:---:|:---:|:---:|
| Tokenize / Segment | &check; | &check;  | &check; | &check;  |     | &check; |
| Sentence Split | &check; | &check;  | &check; | &check;  | &check; | &check; |
| Part of Speech | &check; | &check;  | &check; | &check;  | &check; | &check; |
| Named Entities |   | &check;  | &check; |    | &check; | &check; |
| Constituency Parsing | &check; | &check;  | &check; | &check; | &check; | &check; |
| Dependency Parsing |    | &check;  | &check; | &check; | &check; |     |
| Sentiment Analysis |    |    | &check; |  |  |     |
| Mention Detection |    | &check;  | &check; |  |  |     |
| Coreference |    | &check;  | &check; |  |  |     |
| Open IE |    |   | &check; |  |  |     |


## Programming languages and operating systems

Stanford CoreNLP is written in **Java**; current releases  require **Java 1.8+**. 
There is a complete zip file with everything or
it is [available on Maven Central](http://mvnrepository.com/artifact/edu.stanford.nlp/stanford-corenlp).
Source is [available on GitHub](https://github.com/stanfordnlp/CoreNLP).

You can use Stanford CoreNLP from the command-line, via its Java
programmatic API, via third party APIs for most major modern programming languages, or via a service.
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
<a href="http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem">proprietary software</a>,
<a href="http://techfinder.stanford.edu/technology_detail.php?ID=29724">commercial licensing</a>
is available from Stanford. You can contact us at 
[java-nlp-support@lists.stanford.edu](mailto:java-nlp-support@lists.stanford.edu).


## Citing Stanford CoreNLP in papers

If you&rsquo;re just running the CoreNLP pipeline, please cite this CoreNLP
demo paper. If you&rsquo;re dealing in depth with particular annotators,
you&rsquo;re also encouraged to cite the papers that cover individual
components. You can find more information on the Stanford NLP
[software pages](http://nlp.stanford.edu/software/index.shtml) and/or
[publications page](http://nlp.stanford.edu/publications.shtml).

> Manning, Christopher D., Mihai Surdeanu, John Bauer, Jenny Finkel, Steven J. Bethard, and David McClosky. 2014. [The Stanford CoreNLP Natural Language Processing Toolkit](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.pdf) In *Proceedings of the 52nd Annual Meeting of the Association for Computational Linguistics: System Demonstrations*, pp. 55-60. \[[pdf](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.bib)\]

