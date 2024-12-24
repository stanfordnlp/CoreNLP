---
layout: page
title: Using CoreNLP within other programming languages and packages
keywords: other languages
permalink: '/other-languages.html'
nav_order: 10
parent: Usage
---

Below are interfaces and packages for running Stanford CoreNLP from other languages or within other packages. They have been written by many other people (thanks!). In general you should contact these people directly if you have problems with these packages.

### C#/F#/.NET

* [Stanford.NLP for .NET](https://sergey-tihon.github.io/Stanford.NLP.NET/) by [Sergey Tihon](https://sergeytihon.com/) uses standard CoreNLP JARs with [IKVM.Maven.Sdk](https://github.com/ikvmnet/ikvm-maven). This makes .NET DLLs. It is now aging with support only up through Java 8. But that works for CoreNLP through the lates version 4.x.y release.
* [CoreNLPClient.Net](https://github.com/AMArostegui/CoreNLPClient.Net)  .NET reimplementation of the Stanza client by [Antonio Miras](https://github.com/AMArostegui)

#### Previously

* [Stanford.NLP.CoreNLP for .NET](https://www.nuget.org/packages/Stanford.NLP.CoreNLP/) is now legacy.


### Clojure

* [DataLinguist](https://github.com/simongray/datalinguist) by Simon Gray wraps most of CoreNLP with an idiomatic Clojure API. As of 2022, this is the most complete and completely up-to-date Clojure API for CoreNLP.
* [org.clojurenlp.core](https://github.com/clojurenlp/core) extended the earlier `https://github.com/damienstanton/stanford-corenlp`. It incorporates work by Cory Giles, Hans Engel, Damien Stanton, Andrew McCloud, Leon Talbot, and Marek Owsikowski. It covers tokenization, POS tagging, NER, and parsing, but is currently (2021) not very actively maintained.
* [Clojure wrapper for CoreNLP](https://github.com/ngrunwald/stanford-nlp-tools)
by Nils Gruenwald. Very partial, currently only wrapping the tagger and TokensRegex, and not being developed.

### Docker

Okay, Docker isn't a language, but you know what we mean…. 

* [stanford-corenlp-docker](https://hub.docker.com/r/nlpbox/corenlp) A dockerfile by Arne Neumann. Updated Apr 2021. The NLPBox project provides dockerfiles for many NLP tools. Source on [GitHub](https://github.com/NLPbox/stanford-corenlp-docker/).
* [CoreNLP Complete dockerfile](https://hub.docker.com/r/graham3333/corenlp-complete) A Dockerfile for Stanford CoreNLP server by Graham MacDonald. Comes with good examples of use. Updated in Dec 2018. [GitHub](https://github.com/grahamimac/corenlp-complete-docker).

And there are about 200 others – it’s not so hard to build a dockerfile! Here's a list, which includes a number of dockerfiles setup to run CoreNLP with different human languages:

* [Search for CoreNLP on dockerhub](https://hub.docker.com/search?q=corenlp)

Note on running the CoreNLP server under docker: The container’s port 9000 has to be published to the host. For example, give a command like: `docker run -p 9000:9000 -itd --name CoreNLP graham3333/corenlp-complete`. If, when going to `localhost:9000/`, you see the error `This site can’t be reached. localhost refused to connect`, then this is what you failed to do!


### Elixir

* [corenlp](https://hex.pm/packages/corenlp) by Robert Bates provides a thin client interface in Elixir to a CoreNLP server. [GitHub](https://github.com/arpieb/corenlp).

### Go (golang)

* [go-corenlp](https://github.com/hironobu-s/go-corenlp) is a Golang wrapper for CoreNLP by Hironobu Saito.

* [corenlp-golang](https://github.com/genelet/coreNLP/) is another wrapper by Peter Bi written in 2022.

### Java

* [DKPro Core](https://dkpro.github.io/dkpro-core/) is a collection of NLP components, wrapped as **UIMA** components.
It includes the Stanford CoreNLP components, and there is a [tutorial](https://dkpro.github.io/dkpro-core/java/recipes/stanfordnlp/) on how to use them in the [DKPro Core documentation](https://dkpro.github.io/dkpro-core/documentation/).
DKPro Core is part of the [DKPro community](https://dkpro.github.io). It is well-maintained and our recommended way of using Stanford CoreNLP within UIMA. DKPro Core is principally developed by Richard Eckart de Castilho at the Ubiquitous Processing Lab (UKP) at the Technische Universität Darmstadt.
* [cleartk-stanford-corenlp](https://github.com/ClearTK/cleartk/tree/master/cleartk-stanford-corenlp) is a **UIMA** wrapper for Stanford CoreNLP built by Steven Bethard in the context of the [ClearTK](http://cleartk.github.io/cleartk/) toolkit.
* A [**Vert.x** module for acccessing Stanford CoreNLP](https://github.com/jonnywray/mod-stanford-corenlp) by Jonny Wray.
* [Wrapper for each of Stanford's Chinese tools](https://github.com/guokr/stan-cn-nlp) by Mingli Yuan.
* [RESTful API for integrating between Stanford CoreNLP](https://github.com/westei/stanbol-stanfordnlp) and [Apache Stanbol](https://stanbol.apache.org/) by Rupert Westenthaler and Cristian Petroaca.

### JavaScript (node.js)

#### Recommended

* [corenlp](https://www.npmjs.com/package/corenlp)
([github site](https://github.com/gerardobort/node-corenlp))
by Gerardo Bort is an actively developed node.js CoreNLP library. Multilingual support.
You can [run this package in your browser, using RunKit](https://npm.runkit.com/corenlp).
* [corenlp-sentiment](https://www.npmjs.com/package/corenlp-sentiment) ([github site](https://github.com/garrickmcmickell/corenlp-sentiment)) adds support for sentiment analysis to the above corenlp package. By Garrick James McMickell.
* [CoreNLP-client](https://www.npmjs.com/package/corenlp-client) ([GitHub site](https://github.com/rom1504/node-corenlp-client)) is a simple corenlp client to the corenlp http server using request-promise by Romain Beaumont. Extended by Christophe B. for multilingual use as [corenlp-client-multilang](https://www.npmjs.com/package/corenlp-client-multilang)
([github site](https://github.com/christophe77/node-corenlp-client)). The latter has multilingual support.
* [corenlp-request-wrapper ](https://www.npmjs.com/package/corenlp-request-wrapper)
([github site](https://github.com/nash403/corenlp-request-wrapper)) is a wrapper for a Stanford CoreNLP server by nash403.

#### Caution: Disrecommended

* [stanford-corenlp](https://www.npmjs.org/package/stanford-corenlp) ([github site](https://github.com/hiteshjoshi/node-stanford-corenlp)) is a simple node.js wrapper by hiteshjoshi.
* [stanford-corenlp-node](https://github.com/mhewett/stanford-corenlp-node) ([github site](https://github.com/mhewett/stanford-corenlp-node)) is a webservice interface to CoreNLP in node.js by Mike Hewett. No recent development.
* [stanford-simple-nlp](https://npmjs.org/package/stanford-simple-nlp) ([github site](https://github.com/xissy/node-stanford-simple-nlp)) is a node.js CoreNLP wrapper by Taeho Kim (xissy). This doesn't seem to have been updated lately. You're better off with something else
* [corenlp-js-interface](https://www.npmjs.com/package/corenlp-js-interface) was a (too) simple interface to a CoreNLP server in node.js. It is deprecated, suffers from a [command injection vulnerability](https://nvd.nist.gov/vuln/detail/CVE-2020-28439) and the GitHub site is no longer available.
<!-- ([github site](https://github.com/noahDess/corenlp-js-interface)) -->
* [corenlp-js-prefab](https://www.npmjs.com/package/corenlp-js-prefab) was a simple interface to the CoreNLP server with a prefab function so you only have to send text and no extra parameters with each call by Noah Dessauer. It is deprecated and the GitHub site is no longer available.

### Lua

* [CoreNLP.lua](https://github.com/vzhong/CoreNLP.lua). Lua client for StanfordCoreNLPServer by [Victor Zhong](http://www.victorzhong.com/). ([LuaRocks site](https://luarocks.org/modules/vzhong/corenlp))

### Perl

* [Perl wrapper](https://metacpan.org/module/Lingua::StanfordCoreNLP) by Kalle Raeisaenen.

### PHP

* [php-stanford-corenlp-adapter](https://github.com/DennisDeSwart/php-stanford-corenlp-adapter) by Dennis De Swart. Well-maintained client connection to Stanford CoreNLP server.
[PHPclasses](http://www.phpclasses.org/package/10056-PHP-Natural-language-processing-using-Stanford-server.html).
[Packagist](https://packagist.org/packages/dennis-de-swart/php-stanford-corenlp-adapter).
* [php-stanford-nlp-datastore](https://github.com/DennisDeSwart/php-stanford-nlp-datastore) by Dennis De Swart. Stores data analyzed by Stanford CoreNLP (words, NER, OpenIE triples, coreference) in an SQLite database, which can then be searched.
[PHPclasses](https://www.phpclasses.org/package/10147-PHP-Analyse-text-with-NLP-and-stores-in-a-database.html).
[Packagist](https://packagist.org/packages/dennis-de-swart/php-stanford-nlp-datastore).

### Python

#### Official Stanza Package by the Stanford NLP Group

We are actively developing a Python package called Stanza, with state-of-the-art NLP performance enabled by deep learning. Besides, this package also includes an API for starting and making requests to a Stanford CoreNLP server.  It is the recommended way to use Stanford CoreNLP in Python.

* [Stanza](https://stanfordnlp.github.io/stanza/): Official Stanford NLP Python package, covering 70+ human languages, as well as biomedical English text.

#### Packages using the Stanford CoreNLP server

These packages use the Stanford CoreNLP server that we've developed over the last couple of years.

* [stanfordcorenlp](https://github.com/Lynten/stanford-corenlp) by Lynten Guo. A Python wrapper to Stanford CoreNLP server, version 3.9.1. [PyPI page](https://pypi.python.org/pypi/stanfordcorenlp/): `pip install stanfordcorenlp`
* [pycorenlp, A Python wrapper for Stanford CoreNLP](https://github.com/smilli/py-corenlp) by Smitha Milli that uses the new CoreNLP v3.6+ server. Available on [PyPI](https://pypi.python.org/pypi/pycorenlp/).
* [corenlp-pywrap](https://github.com/hhsecond/corenlp_pywrap) by Sherin Thomas also uses the new CoreNLP v3.6+ server. Python 3.x (only). Also: [PyPI page](https://pypi.python.org/pypi/corenlp_pywrap).
* [Stanford CoreNLP Python Interface](https://github.com/stanfordnlp/python-corenlp): A reference implementation of a Python interface to the Stanford CoreNLP server. By Arun Chaganty. [PyPI page](https://pypi.org/project/stanford-corenlp/): `pip install stanford-corenlp`
[PyPI page](https://pypi.python.org/pypi/stanford-corenlp/).
* [pynlp](https://github.com/sina-al/pynlp) A (Pythonic) Python wrapper for Stanford CoreNLP by Sina.
[PyPI page](https://pypi.python.org/pypi/pynlp/).
* [**NLTK**](http://www.nltk.org/) since version 3.2.3 (from mid-2018) has a new interface to Stanford CoreNLP using the StanfordCoreNLPServer: `nltk.parse.corenlp.CoreNLPParser`. Please use it. There is [a nice wiki page of instructions](https://github.com/nltk/nltk/wiki/Stanford-CoreNLP-API-in-NLTK).  See also: [the API for the dependency and constituency parsers](http://www.nltk.org/api/nltk.parse.html#module-nltk.parse.corenlp)  (with many examples) and [the code for this module](http://www.nltk.org/_modules/nltk/parse/corenlp.html). Here's [a friendly introduction](https://www.districtdatalabs.com/syntax-parsing-with-corenlp-and-nltk) on how to get started by Data District Labs. <!--, and if you poke around the documentation, you can find equivalent interfaces to other CoreNLP components; for example [here is Stanford CoreNLP NER](http://www.nltk.org/api/nltk.tag.html?highlight=corenlp#nltk.tag.stanford.CoreNLPNERTagger).
--> Much of this work was done by Dmitrijs Milajevs. NLTK also includes an older generation of interfaces to Stanford NLP tools, and, unfortunately, they do not want to remove them until version 4 for compatibility reasons and, for some other reason that we don't understand, they don't even warn you against using them in the documentation. You should totally **avoid using** the old Stanford tokenizer/segmenter/NER/parser (unless stuck on a very old version of  NLTK) – these classes are **very slow**, since they perform calls to Java via the command-line for each invocation. That is, you should **avoid**: `nltk.tag.stanford.StanfordTagger`, `nltk.tag.StanfordNERTagger`, `nltk.tokenize.stanford.StanfordTokenizer`, `nltk.tokenize.stanford_segmenter.StanfordSegmenter`, and `nltk.parse.stanford.StanfordParser`. 

#### Miscellaneous Python packages

These packages are miscellaneous utilities or other frameworks that use Stanford CoreNLP.

* [python-corenlp-protobuf](https://github.com/stanfordnlp/python-corenlp-protobuf): Stanford CoreNLP Python Bindings by Arun Chaganty. This package contains python bindings for Stanford CoreNLP's protobuf specifications, as generated by protoc. These bindings can used to parse binary data produced by, e.g., the Stanford CoreNLP server.
[PyPI page](https://pypi.python.org/pypi/corenlp-protobuf).
* [PyStanfordDependencies](https://github.com/dmcc/PyStanfordDependencies), a Python interface for converting Penn Treebank trees to Stanford Dependencies by David McClosky (see also: [PyPI page](https://pypi.python.org/pypi/PyStanfordDependencies)). Last we checked, it is at Stanford CoreNLP v3.5.2 and can do Universal and Stanford dependencies (though it's currently missing Universal POS tags and features).
* [corenlp-xml](https://github.com/relwell/corenlp-xml-lib), a library for handling interactions with CoreNLP's XML output by Robert Elwell. Available on [PyPI](https://pypi.python.org/pypi/corenlp-xml). [Documentation](http://corenlp-xml-library.readthedocs.io/en/latest/#).
* [corpkit](https://www.github.com/interrogator/corpkit), a sophisticated corpus linguistics toolkit with GUI by Daniel McDonald. Interfaces with CoreNLP v3.6.0 to parse documents, and uses Tregex/[CoreNLP XML](https://github.com/relwell/corenlp-xml-lib) to find patterns in corpora. Available on [PyPI](https://pypi.python.org/pypi/corpkit). A [graphical interface](http://interrogator.github.io/corpkit/) is also available.
* [corenlp-xml-reader](https://github.com/enewe101/corenlp-xml-reader) by Edward Newell on GitHub and there it's a [PyPI package](https://pypi.python.org/pypi/corenlp-xml-reader/0.0.7). He also has [corenlpy](https://pypi.org/project/corenlpy/), which runs Java in a subprocess; see [Github repository](https://github.com/enewe101/corenlpy).

#### Older Python packages

These are previous generation Python interfaces to Stanford CoreNLP, using a subprocess or their own server. They are now not generally being developed and are **obsolete**. (But thanks a lot to the people who wrote them in the early days!)

* [The "Wordseer fork" of stanford-corenlp-python, a Python wrapper for Stanford CoreNLP](https://github.com/Wordseer/stanford-corenlp-python) (see also: [PyPI page](https://pypi.python.org/pypi/stanford-corenlp-python)).  The "Wordseer fork" seems to merge the work of a number of people building on the original Dustin Smith wrapper, namely: Hiroyoshi Komatsu, Johannes Castner, Robert Elwell, Tristan Chong, Aditi Muralidharan. At Stanford CoreNLP v3.5.2, last we checked.  See also [Robert Elwell's version](https://github.com/relwell/stanford-corenlp-python) (at CoreNLP v3.2.0, last we checked).
* [stanford-corepywrapper Python wrapper](https://github.com/brendano/stanford-corepywrapper) by Brendan O'Connor or maybe [John Beieler's fork](https://github.com/johnb30/stanford-corepywrapper). At CoreNLP v3.5.0, last we checked.
* [corenlp-python , an up-to-date fork of Smith (below) by Hiroyoshi Komatsu and Johannes Castner](https://bitbucket.org/torotoki/corenlp-python) (see also: [PyPI page](https://pypi.python.org/pypi/corenlp-python)). At CoreNLP v3.4.1, last we checked.
* [stanford-corenlp-python , the original Python wrapper including JSON-RPC server](https://github.com/dasmith/stanford-corenlp-python) by Dustin Smith. At CoreNLP v3.4.1, last we checked.
* [corenlp , a Python wrapper for Stanford CoreNLP](https://github.com/kedz/corenlp) by Chris Kedzie (see also: [PyPI page](https://pypi.python.org/pypi/corenlp)). At Stanford CoreNLP v3.2.0, last we checked. 

### R (CRAN)

* [cleanNLP: A Tidy Data Model for Natural Language Processing](https://cran.r-project.org/web/packages/cleanNLP/) by Taylor Arnold. [GitHub](https://github.com/statsmaths/cleanNLP). Paper: [pdf](https://arxiv.org/pdf/1703.09570.pdf).
* [coreNLP: Wrappers Around Stanford CoreNLP Tools](https://cran.r-project.org/web/packages/coreNLP/) by Taylor Arnold and Lauren Tilton.  [Github](https://github.com/statsmaths/coreNLP). Supports CoreNLP version &ge; 3.5.2.
* [NLP: Natural Language Processing Infrastructure](https://cran.r-project.org/web/packages/NLP/) by Kurt Hornik. Code and models available [here](http://datacube.wu.ac.at/src/contrib/).

### Ruby

* [Stanford CoreNLP Ruby bindings](https://github.com/louismullie/stanford-core-nlp) by Louis Mullie (see also: [Ruby Gems page](https://rubygems.org/gems/stanford-core-nlp)). (Updated in Feb 2017 to CoreNLP 3.5.0.)
* The larger [TREAT](https://github.com/louismullie/treat) NLP toolkit by Louis Mullie also makes available Stanford CoreNLP.
* [corenlp](https://github.com/lengio/corenlp) by Lengio Corp. is another interface to CoreNLP (last updated for CoreNLP 3.4).
* [stanford-core-nlp](https://bitbucket.org/wsh/stanford-core-nlp) by Will Hayworth is another older interface to CoreNLP (also for CoreNLP 3.4).

### Scala

* [CoreNLP wrapper for Apache Spark](https://github.com/databricks/spark-corenlp) by Xiangrui Meng of Databricks. Last we checked it was at version 0.41 supporting version 3.9.1 of CoreNLP.

* [Scala API for CoreNLP](https://github.com/sistanlp/processors) by Mihai Surdeanu, one of the original developers of the CoreNLP package.


### Thrift server

* [Apache Thrift server for Stanford CoreNLP](https://github.com/EducationalTestingService/stanford-thrift) by Diane Napolitano. (Written in Java, but usable from many languages.)

### XQuery

* [exist-stanford-nlp](https://github.com/lcahlander/exist-stanford-nlp) by Loren Cahlander

### ZeroMQ/ØMQ servers

* [stanford-0mq](https://github.com/dmnapolitano/stanford-0mq) by Diane Napolitano. An implementation of a server for Stanford's CoreNLP suite using Ømq and a basic client/server/JSON requests configuration. Last commit: Oct 2015.
* [stanford-corenlp-zeromq](https://github.com/URXtech/stanford-corenlp-zeromq) by URXtech. Basic JSON wrapper around CoreNLP.
* [corenlp-zmq](https://github.com/twneale/corenlp-zmq) by Thom Neale. A Dockerfile and Ansible provisioning script to build and run a Stanford CoreNLP server process with a single ZMQ broker font-end that proxies incoming requests to one or more back-end Scala workers. Last commit: 2015.
* [corenlp-server](https://github.com/kowey/corenlp-server) by Eric Kow. Simple Java server communicating with clients via XML through ZeroMQ. Example Python client included. Last commit: 2014.
 
