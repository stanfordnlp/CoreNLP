---
title: Using Stanford CoreNLP within other programming languages and packages
keywords: other languages
permalink: '/other-languages.html'
---

Below are interfaces and packages for running Stanford CoreNLP from other languages or within other packages. They have been written by many other people (thanks!). In general you should contact these people directly if you have problems with these packages.

### C#/F#/.NET
* [Stanford CoreNLP for .NET](http://sergey-tihon.github.io/Stanford.NLP.NET/StanfordCoreNLP.html) by Sergey Tihon.  See also: [NuGet page](https://www.nuget.org/packages/Stanford.NLP.CoreNLP/).

### Clojure

* [Clojure wrapper for CoreNLP](https://github.com/damienstanton/stanford-corenlp) by Cory Giles, Hans Engel, and Damien Stanton. Incomplete. Currently only a tagger and parser.
* [Clojure wrapper for CoreNLP](https://github.com/ngrunwald/stanford-nlp-tools)
by Nils Gruenwald. Incomplete. Currently only wraps tagger and TokensRegex.

### Docker

Okay, Docker isn't a language, but you know what we mean…. Note on running the CoreNLP server under docker: The container’s port 9000 has to be published to the host. Give a command like:
`docker run -p 9000:9000 --name coreNLP --rm -i -t motiz88/corenlp`. If, when going to `localhost:9000/`, you see the error 
`This site can’t be reached. localhost refused to connect`, then this is what you failed to do!

* [corenlp-docker](https://hub.docker.com/r/motiz88/corenlp/) A Dockerfile for Stanford CoreNLP Server by motiz88.
* [corenlp-docker](https://github.com/chilland/corenlp-docker) Another by chilland.
* [corenlp-zmq](https://github.com/twneale/corenlp-zmq) By twneale. As the name says, this one has a ZMQ broker front end.
* [stanford-corenlp-server](https://github.com/akiomik/stanford-corenlp-server) By akiomik.
* [corenlp-server](https://github.com/hotpxl/corenlp-server) Another by hotpxl.
* [corenlp-docker](https://github.com/vzhong/corenlp-docker) And another by vzhong, a Stanford NLP graduate.

### Java

* [DKPro Core](https://dkpro.github.io/dkpro-core/) is a collection of NLP components, wrapped as **UIMA** components.
It includes the Stanford CoreNLP components, and there is a [tutorial](https://dkpro.github.io/dkpro-core/java/recipes/stanfordnlp/) on how to use them in the [DKPro Core documentation](https://dkpro.github.io/dkpro-core/documentation/).
DKPro Core is part of the [DKPro community](https://dkpro.github.io). It is well-maintained and our recommended way of using Stanford CoreNLP within UIMA. DKPro Core was principally developed by Richard Eckart de Castilho at the Ubiquitous Processing Lab (UKP) at the Technische Universität Darmstadt.
* [cleartk-stanford-corenlp](https://github.com/ClearTK/cleartk/tree/master/cleartk-stanford-corenlp) is a **UIMA** wrapper for Stanford CoreNLP built by Steven Bethard in the context of the [ClearTK](http://cleartk.github.io/cleartk/) toolkit.
* A [**Vert.x** module for acccessing Stanford CoreNLP](https://github.com/jonnywray/mod-stanford-corenlp) by Jonny Wray.
* [Wrapper for each of Stanford's Chinese tools](https://github.com/guokr/stan-cn-nlp) by Mingli Yuan.
* [RESTful API for integrating between Stanford CoreNLP](https://github.com/westei/stanbol-stanfordnlp) and [Apache Stanbol](https://stanbol.apache.org/) by
    Rupert Westenthaler and Cristian Petroaca.

### JavaScript (node.js)

* [stanford-simple-nlp](https://npmjs.org/package/stanford-simple-nlp) is a node.js CoreNLP wrapper by xissy 
([github site](https://github.com/xissy/node-stanford-simple-nlp))
* [CoreNLP-client](https://npmdaily.com/pkg/corenlp-client), a simple corenlp client to the corenlp http server using request-promise
* [stanford-corenlp](https://www.npmjs.org/package/stanford-corenlp), a simple node.js wrapper by hiteshjoshi 
([github site](https://github.com/hiteshjoshi/node-stanford-corenlp)) 
* [stanford-corenlp-node](https://github.com/mhewett/stanford-corenlp-node) is a webservice interface to CoreNLP in node.js by Mike Hewett 
([github site](https://github.com/mhewett/stanford-corenlp-node))
* [corenlp-js-interface](https://www.npmjs.com/package/corenlp-js-interface) is the simplest interface with CoreNLP server in node.js
([github site](https://github.com/noahDess/corenlp-js-interface)) 
* [corenlp-js-prefab](https://www.npmjs.com/package/corenlp-js-prefab) a simple interface to the CoreNLP server with a prefab function so you only have to send text no extra parameters with each call. By Noah Dessauer.

### Lua

* [CoreNLP.lua](https://github.com/vzhong/CoreNLP.lua). Lua client for StanfordCoreNLPServer by [Victor Zhong](http://www.victorzhong.com/)

### Perl

* [Perl wrapper](https://metacpan.org/module/Lingua::StanfordCoreNLP) by Kalle Raeisaenen.

### PHP

* [php-stanford-corenlp-adapter](https://github.com/DennisDeSwart/php-stanford-corenlp-adapter) by Dennis De Swart. Simple connection to Stanford CoreNLP server. [PHPclasses](http://www.phpclasses.org/package/10056-PHP-Natural-language-processing-using-Stanford-server.html). [Packagist](https://packagist.org/packages/dennis-de-swart/php-stanford-corenlp-adapter).
* [php-stanford-nlp-datastore](https://github.com/DennisDeSwart/php-stanford-nlp-datastore) by Dennis De Swart. Stores data analyzed by Stanford CoreNLP in SQLite database. [Packagist](https://packagist.org/packages/dennis-de-swart/php-stanford-nlp-datastore).

### Python

* [pycorenlp, A Python wrapper for Stanford CoreNLP](https://github.com/smilli/py-corenlp) by Smitha Milli that uses the new CoreNLP v3.6.0 server. Available on [PyPI](https://pypi.python.org/pypi/pycorenlp/).
* [corenlp-pywrap](https://github.com/hhsecond/corenlp_pywrap) by Sherin Thomas also uses the new CoreNLP v3.6.0 server. Python 3.x (only). Also: [PyPI page](https://pypi.python.org/pypi/corenlp_pywrap).
* [The "Wordseer fork" of stanford-corenlp-python, a Python wrapper for Stanford CoreNLP](https://github.com/Wordseer/stanford-corenlp-python) (see also: [PyPI page](https://pypi.python.org/pypi/stanford-corenlp-python)).  The "Wordseer fork" seems to merge the work of a number of people building on the original Dustin Smith wrapper, namely: Hiroyoshi Komatsu, Johannes Castner, Robert Elwell, Tristan Chong, Aditi Muralidharan. At Stanford CoreNLP v3.5.2, last we checked.  See also [Robert Elwell's version](https://github.com/relwell/stanford-corenlp-python) (at CoreNLP v3.2.0, last we checked).
* [stanford-corepywrapper Python wrapper](https://github.com/brendano/stanford-corepywrapper) by Brendan O'Connor or maybe [John Beieler's fork](https://github.com/johnb30/stanford-corepywrapper). At CoreNLP v3.5.0, last we checked.
* [corenlp-python , an up-to-date fork of Smith (below) by Hiroyoshi Komatsu and Johannes Castner](https://bitbucket.org/torotoki/corenlp-python) (see also: [PyPI page](https://pypi.python.org/pypi/corenlp-python)). At CoreNLP v3.4.1, last we checked.
* [stanford-corenlp-python , the original Python wrapper including JSON-RPC server](https://github.com/dasmith/stanford-corenlp-python) by Dustin Smith. At CoreNLP v3.4.1, last we checked.
* [corenlp , a Python wrapper for Stanford CoreNLP](https://github.com/kedz/corenlp) by Chris Kedzie (see also: [PyPI page](https://pypi.python.org/pypi/corenlp)). At Stanford CoreNLP v3.2.0, last we checked. 
* [PyStanfordDependencies , a Python interface for converting Penn Treebank trees to Stanford Dependencies](https://github.com/dmcc/PyStanfordDependencies) by David McClosky (see also: [PyPI page](https://pypi.python.org/pypi/PyStanfordDependencies)). Last we checked, it is at Stanford CoreNLP v3.5.2 and can do Universal and Stanford dependencies (though it's currently missing Universal POS tags and features).
* [corenlp-xml](https://github.com/relwell/corenlp-xml-lib), a library for handling interactions with CoreNLP's XML output by Robert Elwell. Available on [PyPI](https://pypi.python.org/pypi/corenlp-xml). [Documentation](http://corenlp-xml-library.readthedocs.io/en/latest/#).
* [corpkit](https://www.github.com/interrogator/corpkit), a sophisticated corpus linguistics toolkit with GUI by Daniel McDonald. Interfaces with CoreNLP v3.6.0 to parse documents, and uses Tregex/[CoreNLP XML](https://github.com/relwell/corenlp-xml-lib) to find patterns in corpora. Available on [PyPI](https://pypi.python.org/pypi/corpkit). A [graphical interface](http://interrogator.github.io/corpkit/) is also available.
* [corenlp-xml-reader](https://github.com/enewe101/corenlp-xml-reader) by Edward Newell on GitHub and there it's a [PyPI package](https://pypi.python.org/pypi/corenlp-xml-reader/0.0.7)

### R (CRAN)

* [coreNLP: Wrappers Around Stanford CoreNLP Tools](https://cran.r-project.org/web/packages/coreNLP/) by Taylor Arnold and Lauren Tilton.  [Github](https://github.com/statsmaths/coreNLP). Supports CoreNLP version &ge; 3.5.2.
* [NLP: Natural Language Processing Infrastructure](https://cran.r-project.org/web/packages/NLP/) by Kurt Hornik.

### Ruby

* [Stanford CoreNLP Ruby bindings](https://github.com/louismullie/stanford-core-nlp) by Louis Mullie (see also: [Ruby Gems page](https://rubygems.org/gems/stanford-core-nlp)).
* The larger [TREAT](https://github.com/louismullie/treat) NLP toolkit by Louis Mullie also makes available Stanford CoreNLP.

### Scala

* [CoreNLP wrapper for Spark v0.1](https://github.com/databricks/spark-corenlp) by Xiangrui Meng of Databricks.

* [Scala API for CoreNLP](https://github.com/sistanlp/processors) by Mihai Surdeanu, one of the original developers of the CoreNLP package.


### Thrift server

* [Apache Thrift server for Stanford CoreNLP](https://github.com/EducationalTestingService/stanford-thrift) by Diane Napolitano. (Written in Java, but usable from many languages.)

### ZeroMQ/ØMQ servers

* [stanford-0mq](https://github.com/dmnapolitano/stanford-0mq) by Diane Napolitano. An implementation of a server for Stanford's CoreNLP suite using Ømq and a basic client/server/JSON requests configuration. Last commit: Oct 2015.
* [stanford-corenlp-zeromq](https://github.com/URXtech/stanford-corenlp-zeromq) by URXtech. Basic JSON wrapper around CoreNLP.
* [corenlp-zmq](https://github.com/twneale/corenlp-zmq) by Thom Neale. A Dockerfile and Ansible provisioning script to build and run a Stanford CoreNLP server process with a single ZMQ broker font-end that proxies incoming requests to one or more back-end Scala workers. Last commit: 2015.
* [corenlp-server](https://github.com/kowey/corenlp-server) by Eric Kow. Simple Java server communicating with clients via XML through ZeroMQ. Example Python client included. Last commit: 2014.
 
