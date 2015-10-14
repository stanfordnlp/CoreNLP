---
title: Extensions -- Packages and models by others using Stanford CoreNLP
keywords: extensions
permalink: '/extensions.html'
---

#### Annotators/models

* [A stopword removal annotator](https://github.com/jconwell/coreNlp) by John Conwell.
* English informal text\: [aGATE Twitter part-of-speech tagger](https://gate.ac.uk/wiki/twitter-postagger.html), including [a `pos.model` loadable by CoreNLP.](http://downloads.gate.ac.uk/twitie/gate-EN-twitter.model)
* Swedish\: [Swedish POS tagger model](https://github.com/klintan/corenlp-swedish-pos-model) by Andreas Klintberg. See [medium post](https://medium.com/@klintcho/training-a-swedish-pos-tagger-for-stanford-corenlp-546e954a8ee7) for details.

We're happy to list other models and annotators that work with Stanford CoreNLP. If you have something, please get in touch!

#### Java

* [dkpro-core-gpl](https://code.google.com/p/dkpro-core-gpl/)
is a collection of GPL NLP components, principally Stanford CoreNLP,
wrapped as **UIMA** components, based on work at the Ubiquitous Knowledge
Processing Lab (UKP) at the Technische Universitaet Darmstadt.  It is
part of the [DKPro](http://www.ukp.tu-darmstadt.de/research/current-projects/dkpro/) project. See also the [DKPro Core wiki](http://code.google.com/p/dkpro-core-asl/wiki/WikiEntryPage)
and [a tutorial on the Stanford CoreNLP components](https://code.google.com/p/dkpro-core-asl/wiki/StanfordCoreComponents). It is up-to-date, well-maintained,
and our recommended way of using Stanford CoreNLP within UIMA.
* [cleartk-stanford-corenlp](http://cleartk.googlecode.com/git/cleartk-stanford-corenlp/) is a **UIMA** wrapper for Stanford CoreNLP built by Steven Bethard in the context of the [ClearTK](http://code.google.com/p/cleartk/) toolkit.
* A [**Vert.x** module for acccessing Stanford CoreNLP](https://github.com/jonnywray/mod-stanford-corenlp) by Jonny Wray.
* [Wrapper for each of Stanford's Chinese tools](https://github.com/guokr/stan-cn-nlp) by Mingli Yuan.
* [RESTful API for integrating between Stanford CoreNLP](https://github.com/westei/stanbol-stanfordnlp) and [Apache Stanbol](https://stanbol.apache.org/) by
    Rupert Westenthaler and Cristian Petroaca.

#### Thrift server

* [Apache Thrift server for Stanford CoreNLP](https://github.com/EducationalTestingService/stanford-thrift) by Diane Napolitano. (Written in Java, but usable from many languages.)

#### C#/F#/.NET
* [Stanford CoreNLP for .NET](http://sergey-tihon.github.io/Stanford.NLP.NET/StanfordCoreNLP.html) by Sergey Tihon.  (See also: [NuGet page](https://www.nuget.org/packages/Stanford.NLP.CoreNLP/)

#### Python

* [Brendan O'Connor's Python wrapper](https://github.com/brendano/stanford-corepywrapper) or maybe [John Beieler's fork](https://github.com/johnb30/stanford-corepywrapper). At CoreNLP v3.5.0, last we checked.
* [An up-to-date fork of Smith (below) by Hiroyoshi Komatsu and Johannes Castner](https://bitbucket.org/torotoki/corenlp-python) (see also: [PyPI page](https://pypi.python.org/pypi/corenlp-python)). At CoreNLP v3.4.1, last we checked.
* [A Python wrapper for Stanford CoreNLP](https://github.com/Wordseer/stanford-corenlp-python) (see also: [PyPI page](https://pypi.python.org/pypi/stanford-corenlp-python)).  This "Wordseer fork" seems to merge the work of a number of people building on the original Dustin Smith wrapper, namely: Hiroyoshi Komatsu, Johannes Castner, Robert Elwell, Tristan Chong, Aditi Muralidharan. At Stanford CoreNLP v3.2.0, last we checked.  See also [Robert Elwell's version](https://github.com/relwell/stanford-corenlp-python) (also at CoreNLP v3.2.0, last we checked).
* [A Python wrapper for Stanford CoreNLP](https://github.com/kedz/corenlp) by Chris Kedzie (see also: [PyPI page](https://pypi.python.org/pypi/corenlp)). At Stanford CoreNLP v3.2.0, last we checked. 
* [Original Python wrapper including JSON-RPC server](https://github.com/dasmith/stanford-corenlp-python) by Dustin Smith. At CoreNLP v1.3.3, last we checked.
* [Python interface for converting Penn Treebank trees to Stanford Dependencies](https://github.com/dmcc/PyStanfordDependencies) by David McClosky (see also: [PyPI page](https://pypi.python.org/pypi/PyStanfordDependencies)). Last we checked, it is at Stanford CoreNLP v3.5.2 and can do Universal and Stanford dependencies (though it's currently missing Universal POS tags and features).

#### Ruby

* [Ruby bindings](https://github.com/louismullie/stanford-core-nlp) by Louis Mullie (see also: [Ruby Gems page](https://rubygems.org/gems/stanford-core-nlp)).
* The larger [TREAT](https://github.com/louismullie/treat) NLP toolkit by Louis Mullie also makes available Stanford CoreNLP.

#### Perl

* [Perl wrapper](https://metacpan.org/module/Lingua::StanfordCoreNLP) by Kalle Raeisaenen.

#### Scala

* [Scala API for CoreNLP](https://github.com/sistanlp/processors) by Mihai Surdeanu, one of the original developers of the CoreNLP package.

#### Clojure

* [Clojure wrapper for CoreNLP](https://github.com/gilesc/stanford-corenlp) by Cory Giles. Incomplete. Currently only a parser wrapper.
* [Clojure wrapper for CoreNLP](https://github.com/ngrunwald/stanford-nlp-tools)
by Nils Gruenwald. Incomplete. Currently only wraps tagger and TokensRegex.

#### JavaScript (node.js)

* [stanford-simple-nlp](https://npmjs.org/package/stanford-simple-nlp) is a node.js CoreNLP wrapper by xissy 
([github site](https://github.com/xissy/node-stanford-simple-nlp))
* [stanford-corenlp](https://www.npmjs.org/package/stanford-corenlp),
  a simply node.js wrapper by hiteshjoshi 
([github site](https://github.com/hiteshjoshi/node-stanford-corenlp)) 
* [stanford-corenlp-node](https://github.com/mhewett/stanford-corenlp-node) is a webservice interface to CoreNLP in node.js by Mike Hewett 
([github site](https://github.com/mhewett/stanford-corenlp-node))

#### ZeroMQ server
* [corenlp-server](https://github.com/kowey/corenlp-server). Simple Java server communicating with clients via XML through ZeroMQ. Example Python client included. By Eric Kow.
