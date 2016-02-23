---
title: Other Languages: Using Stanford CoreNLP in other languages and packages
keywords: other languages
permalink: '/other-languages.html'
---

Below are interfaces and packages for running Stanford CoreNLP from other languages or within other packages. They have been written by many other people (thanks!). In general you should contact these people directly if you have problems with these packages.

#### C#/F#/.NET
* [Stanford CoreNLP for .NET](http://sergey-tihon.github.io/Stanford.NLP.NET/StanfordCoreNLP.html) by Sergey Tihon.  See also: [NuGet page](https://www.nuget.org/packages/Stanford.NLP.CoreNLP/).

#### Clojure

* [Clojure wrapper for CoreNLP](https://github.com/gilesc/stanford-corenlp) by Cory Giles. Incomplete. Currently only a parser wrapper.
* [Clojure wrapper for CoreNLP](https://github.com/ngrunwald/stanford-nlp-tools)
by Nils Gruenwald. Incomplete. Currently only wraps tagger and TokensRegex.

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

#### JavaScript (node.js)

* [stanford-simple-nlp](https://npmjs.org/package/stanford-simple-nlp) is a node.js CoreNLP wrapper by xissy 
([github site](https://github.com/xissy/node-stanford-simple-nlp))
* [stanford-corenlp](https://www.npmjs.org/package/stanford-corenlp),
  a simple node.js wrapper by hiteshjoshi 
([github site](https://github.com/hiteshjoshi/node-stanford-corenlp)) 
* [stanford-corenlp-node](https://github.com/mhewett/stanford-corenlp-node) is a webservice interface to CoreNLP in node.js by Mike Hewett 
([github site](https://github.com/mhewett/stanford-corenlp-node))

#### Perl

* [Perl wrapper](https://metacpan.org/module/Lingua::StanfordCoreNLP) by Kalle Raeisaenen.

#### Python
* [A Python wrapper for Stanford CoreNLP](https://github.com/smilli/py-corenlp) by Smitha Milli that uses the new CoreNLP v3.6.0 server. Available on [PyPI](https://pypi.python.org/pypi/pycorenlp/).
* [Brendan O'Connor's Python wrapper](https://github.com/brendano/stanford-corepywrapper) or maybe [John Beieler's fork](https://github.com/johnb30/stanford-corepywrapper). At CoreNLP v3.5.0, last we checked.
* [An up-to-date fork of Smith (below) by Hiroyoshi Komatsu and Johannes Castner](https://bitbucket.org/torotoki/corenlp-python) (see also: [PyPI page](https://pypi.python.org/pypi/corenlp-python)). At CoreNLP v3.4.1, last we checked.
* [Original Python wrapper including JSON-RPC server](https://github.com/dasmith/stanford-corenlp-python) by Dustin Smith. At CoreNLP v3.4.1, last we checked.
* [A Python wrapper for Stanford CoreNLP](https://github.com/Wordseer/stanford-corenlp-python) (see also: [PyPI page](https://pypi.python.org/pypi/stanford-corenlp-python)).  This "Wordseer fork" seems to merge the work of a number of people building on the original Dustin Smith wrapper, namely: Hiroyoshi Komatsu, Johannes Castner, Robert Elwell, Tristan Chong, Aditi Muralidharan. At Stanford CoreNLP v3.2.0, last we checked.  See also [Robert Elwell's version](https://github.com/relwell/stanford-corenlp-python) (also at CoreNLP v3.2.0, last we checked).
* [A Python wrapper for Stanford CoreNLP](https://github.com/kedz/corenlp) by Chris Kedzie (see also: [PyPI page](https://pypi.python.org/pypi/corenlp)). At Stanford CoreNLP v3.2.0, last we checked. 
* [Python interface for converting Penn Treebank trees to Stanford Dependencies](https://github.com/dmcc/PyStanfordDependencies) by David McClosky (see also: [PyPI page](https://pypi.python.org/pypi/PyStanfordDependencies)). Last we checked, it is at Stanford CoreNLP v3.5.2 and can do Universal and Stanford dependencies (though it's currently missing Universal POS tags and features).

#### R (CRAN)

* [coreNLP: Wrappers Around Stanford CoreNLP Tools](https://cran.r-project.org/web/packages/coreNLP/) by Taylor Arnold and Lauren Tilton.  [Github](https://github.com/statsmaths/coreNLP). 
* [NLP: Natural Language Processing Infrastructure](https://cran.r-project.org/web/packages/NLP/) by Kurt Hornik.

#### Ruby

* [Ruby bindings](https://github.com/louismullie/stanford-core-nlp) by Louis Mullie (see also: [Ruby Gems page](https://rubygems.org/gems/stanford-core-nlp)).
* The larger [TREAT](https://github.com/louismullie/treat) NLP toolkit by Louis Mullie also makes available Stanford CoreNLP.

#### Scala

* [Scala API for CoreNLP](https://github.com/sistanlp/processors) by Mihai Surdeanu, one of the original developers of the CoreNLP package.

#### Thrift server

* [Apache Thrift server for Stanford CoreNLP](https://github.com/EducationalTestingService/stanford-thrift) by Diane Napolitano. (Written in Java, but usable from many languages.)

#### ZeroMQ/ØMQ servers

* [stanford-0mq](https://github.com/dmnapolitano/stanford-0mq) by Diane Napolitano. An implementation of a server for Stanford's CoreNLP suite using Ømq and a basic client/server/JSON requests configuration. Last commit: Oct 2015.
* [stanford-corenlp-zeromq](https://github.com/URXtech/stanford-corenlp-zeromq) by URXtech. Basic JSON wrapper around CoreNLP.
* [corenlp-zmq](https://github.com/twneale/corenlp-zmq) by Thom Neale. A Dockerfile and Ansible provisioning script to build and run a Stanford CoreNLP server process with a single ZMQ broker font-end that proxies incoming requests to one or more back-end Scala workers. Last commit: 2015.
* [corenlp-server](https://github.com/kowey/corenlp-server) by Eric Kow. Simple Java server communicating with clients via XML through ZeroMQ. Example Python client included. Last commit: 2014.
 
