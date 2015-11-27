---
title: About
keywords: overview, about
type: first_page
homepage: true
---

Stanford CoreNLP provides a set of natural language analysis
tools. It can take raw text input and give the base
forms of words, their parts of speech, whether they are names of
companies, people, etc., normalize dates, times, and numeric quantities,
and mark up the structure of sentences in terms of
phrases and word dependencies, indicate which noun phrases refer to
the same entities, indicate sentiment, etc. 

Choose Stanford CoreNLP if you need:

* An integrated toolkit with a good range of functionality
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
Stanford CoreNLP integrates many of Stanford's NLP tools,
including [the part-of-speech (POS) tagger](http://nlp.stanford.edu/software/tagger.shtml), 
[the named entity recognizer (NER)](http://nlp.stanford.edu/software/CRF-NER.shtml),
[the parser](http://nlp.stanford.edu/software/lex-parser.shtml),
[the coreference resolution system](http://nlp.stanford.edu/software/dcoref.shtml),
[sentiment analysis](http://nlp.stanford.edu/sentiment/), and
[the bootstrapped pattern learning](http://nlp.stanford.edu/software/patternslearning.shtml) tools.
Its analyses provide the foundational building blocks for
higher-level and domain-specific text understanding applications.

## Human languages supported

The basic distribution provides model files for the analysis of **English**,
but the engine is compatible with models for other languages. We provide
packaged models for **Chinese** and **Spanish**, and
Stanford NLP models for **French**, **German**, and **Arabic** are
also usable inside CoreNLP.

## Programming languages and operating systems

Stanford CoreNLP is written in **Java** and licensed under the [GNU General Public License](http://www.gnu.org/licenses/gpl.html) (v3 or later; in general Stanford NLP
code is GPL v2+, but CoreNLP uses several Apache-licensed libraries, and
so the composite is v3+).  Source is [available on GitHub]().
Note that the license is the <i>full</i> GPL,
which allows many free uses, but not its use in [proprietary software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem) which is distributed to others.
The download is 260 MB and requires **Java 1.8+**.
You can use Stanford CoreNLP from the command-line, via its
programmatic API or via a services on Linux, OS X, or Windows.

## License

Stanford CoreNLP is licensed under the [GNU General Public License](http://www.gnu.org/licenses/gpl.html)
(v3 or later; in general Stanford NLP
code is GPL v2+, but CoreNLP uses several Apache-licensed libraries, and
so the composite is v3+).  Source is [available on GitHub]().
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

If you're just running the CoreNLP pipeline, please cite this CoreNLP
demo paper. If you're dealing in depth with particular annotators,
you're also encouraged to cite the papers that cover individual
components. You can find more information on the Stanford NLP
[software pages](http://nlp.stanford.edu/software/index.shtml) and/or
[publications page](http://nlp.stanford.edu/publications.shtml).

> Manning, Christopher D., Mihai Surdeanu, John Bauer, Jenny Finkel, Steven J. Bethard, and David McClosky. 2014. [The Stanford CoreNLP Natural Language Processing Toolkit](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.pdf) In *Proceedings of the 52nd Annual Meeting of the Association for Computational Linguistics: System Demonstrations*, pp. 55-60. \[[pdf](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.pdf)\] \[[bib](http://nlp.stanford.edu/pubs/StanfordCoreNlp2014.bib)\]

