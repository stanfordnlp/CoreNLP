---
layout: page
title: Named Entity Recognizer
keywords: NER, CRF-NER, CRF
permalink: '/tools_crf_ner.html'
nav_order: 9
toc: true
parent: Additional Tools
---

### About

Stanford NER is a Java implementation of a Named Entity Recognizer. Named
Entity Recognition (NER) labels sequences of words in a text which are the
names of things, such as person and company names, or gene and protein names.
It comes with well-engineered feature extractors for Named Entity Recognition,
and many options for defining feature extractors. Included with the download
are good named entity recognizers for English, particularly for the 3 classes
(PERSON, ORGANIZATION, LOCATION), and we also make available on this page
various other models for different languages and circumstances, including
models trained on just the [CoNLL
2003](http://www.cnts.ua.ac.be/conll2003/ner/) English training data.

Stanford NER is also known as CRFClassifier. The software provides a general
implementation of (arbitrary order) linear chain Conditional Random Field
(CRF) sequence models. That is, by training your own models on labeled data,
you can actually use this code to build sequence models for NER or any other
task. (CRF models were pioneered by [Lafferty, McCallum, and Pereira
(2001)](http://www.cis.upenn.edu/~pereira/papers/crf.pdf); see [Sutton and
McCallum (2006)](http://people.cs.umass.edu/~mccallum/papers/crf-tutorial.pdf)
or [Sutton and McCallum (2010)](http://arxiv.org/pdf/1011.4088v1) for more
comprehensible introductions.)

The original CRF code is by Jenny Finkel. The feature extractors are by Dan
Klein, Christopher Manning, and Jenny Finkel. Much of the documentation and
usability is due to Anna Rafferty. More recent code development has been done
by various Stanford NLP Group members.

Stanford NER is available for download, **licensed under the[GNU General
Public License](http://www.gnu.org/licenses/gpl-2.0.html)** (v2 or later).
Source is included. The package includes components for command-line
invocation (look at the shell scripts and batch files included in the
download), running as a server (look at `NERServer` in the sources jar file),
and a Java API (look at the simple examples in the `[NERDemo.java](ner-
example/NERDemo.java)` file included in the download, and then at the
javadocs). Stanford NER code is dual licensed (in a similar manner to MySQL,
etc.). Open source licensing is under the _full_ GPL, which allows many free
uses. For distributors of [proprietary
software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem),
[commercial
licensing](http://otlportal.stanford.edu/techfinder/technology/ID=24628) is
available. If you don't need a commercial license, but would like to support
maintenance of these tools, we welcome gifts.

  

### Citation

The CRF sequence models provided here do not precisely correspond to any
published paper, but the correct paper to cite for the model and software is:

> Jenny Rose Finkel, Trond Grenager, and Christopher Manning. 2005.
> Incorporating Non-local Information into Information Extraction Systems by
> Gibbs Sampling. _Proceedings of the 43nd Annual Meeting of the Association
> for Computational Linguistics (ACL 2005),_ pp. 363-370.
> [`http://nlp.stanford.edu/~manning/papers/gibbscrf3.pdf`](http://nlp.stanford.edu/~manning/papers/gibbscrf3.pdf)

The software provided here is similar to the baseline local+Viterbi model in
that paper, but adds new distributional similarity based features (in the
`-distSim` classifiers). Distributional similarity features improve
performance but the models require somewhat more memory. Our big English NER
models were trained on a mixture of CoNLL, MUC-6, MUC-7 and ACE named entity
corpora, and as a result the models are fairly robust across domains.

  

### Getting started

You can try out [Stanford NER CRF
classifiers](http://nlp.stanford.edu:8080/ner/) or [Stanford NER as part of
Stanford CoreNLP](http://corenlp.run/) on the web, to understand what Stanford
NER is and whether it will be useful to you.

To use the software on your computer, [download the zip file](http://nlp.stanford.edu/software/CRF-NER.html#Download). You then unzip
the file by either double-clicing on the zip file, using a program for
unpacking zip files, or by using the `unzip` command. This shord create a
`stanford-ner` folder. There is no installation procedure, you should be able
to run Stanford NER from that folder. Normally, Stanford NER is run from the
command line (i.e., shell or terminal). Current releases of Stanford NER
require Java 1.8 or later. Either make sure you have or get [Java 8](http://java.com/) or consider running an earlier version of the software
(versions through 3.4.1 support Java 6 and 7)..

#### NER GUI

Providing java is on your PATH, you should be able to run an NER GUI
demonstration by just clicking. It might work to double-click on the stanford-
ner.jar archive but this may well fail as the operating system does not give
Java enough memory for our NER system, so it is safer to instead double click
on the ner-gui.bat icon (Windows) or ner-gui.sh (Linux/Unix/MacOSX). Then,
using the top option from the Classifier menu, load a CRF classifier from the
classifiers directory of the distribution. You can then either load a text
file or web page from the File menu, or decide to use the default text in the
window. Finally, you can now named entity tag the text by pressing the Run NER
button.

#### Single CRF NER Classifier from command-line

From a command line, you need to have java on your PATH and the stanford-
ner.jar file in your CLASSPATH. (The way of doing this depends on your
OS/shell.) The supplied `ner.bat` and `ner.sh` should work to allow you to tag
a single file, when running from inside the Stanford NER folder. For example,
for Windows:

> ner file

This corresponds to the full command:

> `java -mx600m -cp "*;lib\*" edu.stanford.nlp.ie.crf.CRFClassifier
> -loadClassifier classifiers/english.all.3class.distsim.crf.ser.gz -textFile
> sample.txt`

Or on Unix/Linux you should be able to parse the test file in the distribution
directory with the command:

> `java -mx600m -cp "*:lib/*" edu.stanford.nlp.ie.crf.CRFClassifier
> -loadClassifier classifiers/english.all.3class.distsim.crf.ser.gz -textFile
> sample.txt`

Here's an output option that will print out entities and their class to the
first two columns of a tab-separated columns output file:

> `java -mx600m -cp "*;lib/*" edu.stanford.nlp.ie.crf.CRFClassifier
> -loadClassifier classifiers/english.all.3class.distsim.crf.ser.gz
> -outputFormat tabbedEntities -textFile sample.txt > sample.tsv`

#### Full Stanford NER functionality

This standalone distribution also allows access to the full NER capabilities
of the Stanford CoreNLP pipeline. These capabilities can be accessed via the
`NERClassifierCombiner` class. NERClassifierCombiner allows for multiple CRFs
to be used together, and has options for recognizing numeric sequence patterns
and time patterns with the rule-based NER of SUTime.

To use NERClassifierCombiner at the command-line, the jars in lib directory
and stanford-ner.jar must be in the CLASSPATH. Here is an example command:

> `java -mx1g -cp "*:lib/*" edu.stanford.nlp.ie.NERClassifierCombiner
> -textFile sample.txt -ner.model
> classifiers/english.all.3class.distsim.crf.ser.gz,classifiers/english.conll.4class.distsim.crf.ser.gz,classifiers/english.muc.7class.distsim.crf.ser.gz`

The one difference you should see from above is that _Sunday_ is now
recognized as a DATE.

#### Programmatic use via API

You can call Stanford NER from your own code. The file `NERDemo.java` included
in the distribution illustrates several ways of calling the system
programatically. We suggest that you start from there, and then look at the
javado, etc. as needed.

#### Programmatic use via a service

Stanford NER can also be set up to run as a server listening on a socket.

### Questions

You can look at a Powerpoint Introduction to NER and the Stanford NER package
[[ppt](jenny-ner-2007.ppt)] [[pdf](jenny-ner-2007.pdf)]. There is also a list
of [Frequently Asked Questions](crf-faq.html) (FAQ), with answers! This
includes some information on training models. Further documentation is
provided in the included `README.txt` and in the javadocs.

Have a support question? Ask us on [Stack Overflow](http://stackoverflow.com)
using the tag `stanford-nlp`.

Feedback and bug reports / fixes can be sent to our mailing lists.

### Mailing Lists

We have 3 mailing lists for the Stanford Named Entity Recognizer, all of which
are shared with other JavaNLP tools (with the exclusion of the parser). Each
address is at `@lists.stanford.edu`:

  1. `java-nlp-user` This is the best list to post to in order to send feature requests, make announcements, or for discussion among JavaNLP users. (Please ask support questions on [Stack Overflow](http://stackoverflow.com) using the `stanford-nlp` tag.) 

You have to subscribe to be able to use this list. Join the list via [this
webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-user) or by
emailing `java-nlp-user-join@lists.stanford.edu`. (Leave the subject and
message body empty.) You can also [look at the list
archives](https://mailman.stanford.edu/pipermail/java-nlp-user/).

  2. `java-nlp-announce` This list will be used only to announce new versions of Stanford JavaNLP tools. So it will be very low volume (expect 1-3 messages a year). Join the list via [this webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-announce) or by emailing `java-nlp-announce-join@lists.stanford.edu`. (Leave the subject and message body empty.) 
  3. `java-nlp-support` This list goes only to the software maintainers. It's a good address for licensing questions, etc. **For general use and support questions, you're better off joining and using`java-nlp-user`.** You cannot join `java-nlp-support`, but you can mail questions to `java-nlp-support@lists.stanford.edu`. 

  

### Download

**[Download Stanford Named Entity Recognizer version 4.2.0](stanford-
ner-4.2.0.zip)**  

The download is a 151M zipped file (mainly consisting of classifier data
objects). If you unpack that file, you should have everything needed for
English NER (or use as a general CRF). It includes batch files for running
under Windows or Unix/Linux/MacOSX, a simple GUI, and the ability to run as a
server. Stanford NER requires Java v1.8+. If you want to use Stanford NER for
other languages, you'll also need to download model files for those languages;
see further below.

  

### Extensions: Packages by others using Stanford NER

For some (computer) languages, there are more up-to-date interfaces to
Stanford NER available by using it inside [Stanford
CoreNLP](http://stanfordnlp.github.io/CoreNLP/other-languages.html), and you
are better off getting those from the CoreNLP page and using them....

  * **Apache Tika:** [Named Entity Recognition (NER) with Tika](https://wiki.apache.org/tika/TikaAndNER). 
  * **JavaScript/npm:**
    * Pranav Herur has written [ner-server](https://www.npmjs.com/package/ner-server). [Source](https://github.com/PranavHerur/ner-server) on github. 
    * Nikhil Srivastava has written [ner](https://www.npmjs.com/package/ner). [Source](https://github.com/niksrc/ner) on github. 
    * Varun Chatterji has written [stanford-ner](https://www.npmjs.com/package/stanford-ner). [Source](https://github.com/vchatterji/stanford-ner) on github. 
  * **.NET/F#/C#:** Sergey Tihon has [ported Stanford NER to F# (and other .NET languages, such as C#)](http://sergey-tihon.github.io/Stanford.NLP.NET/StanfordNER.html), using IKVM. See also pages on: [GitHub](http://sergey-tihon.github.io/Stanford.NLP.NET/StanfordNER.html) and [NuGet](http://nuget.org/packages/Stanford.NLP.NER/). 
  * **Perl:** Kieren Diment has written [Text-NLP-Stanford-EntityExtract](https://metacpan.org/pod/Text::NLP::Stanford::EntityExtract), a Perl module that provides an interface to Stanford NER running as a server. 
  * **PHP:** Patrick Schur in 2017 wrote [PHP wrapper for Stanford POS and NER taggers](https://github.com/patrickschur/stanford-nlp-tagger). Also on [packagist](https://packagist.org/packages/patrickschur/stanford-nlp-tagger). Second choice: [PHP-Stanford-NLP](https://github.com/agentile/PHP-Stanford-NLP). Supports POS Tagger, NER, Parser. By Anthony Gentile (agentile). 
  * **Python:**
    * Dat Hoang wrote [pyner](https://github.com/dat/pyner), a Python interface to Stanford NER. _[Old version.]_
    * [NLTK (2.0+)](http://nltk.org/) contains an interface to Stanford NER written by Nitin Madnani: [documentation](http://nltk.org/api/nltk.tag.html#module-nltk.tag.stanford) (note: set the character encoding or you get ASCII by default!), [code](http://nltk.org/_modules/nltk/tag/stanford.html), [on Github](https://github.com/nltk/nltk/blob/master/nltk/tag/stanford.py).
    * [scrapy-corenlp](https://github.com/vu3jej/scrapy-corenlp), a Python [Scrapy](https://scrapy.org/) (web page scraping) middleware by Jithesh E. J. [PyPI](https://pypi.python.org/pypi/scrapy-corenlp).
  * **Ruby:** tiendung has written [a Ruby Binding](http://github.com/tiendung/ruby-nlp) for the Stanford POS tagger and Named Entity Recognizer. 
  * **UIMA:** Florian Laws made a Stanford NER [UIMA](http://uima.apache.org/) annotator using a modified version of Stanford NER, which is available on his [homepage](http://www.florianlaws.de/software/). _[Old version.]_

  

### Models

Included with Stanford NER are a 4 class model trained on the CoNLL 2003
`eng.train`, a 7 class model trained on the MUC 6 and MUC 7 training data
sets, and a 3 class model trained on both data sets and some additional data
(including ACE 2002 and limited amounts of in-house data) on the intersection
of those class sets. (The training data for the 3 class model does not include
any material from the CoNLL `eng.testa` or `eng.testb` data sets, nor any of
the MUC 6 or 7 test or devtest datasets, nor Alan Ritter's Twitter NER data,
so all of these remain valid tests of its performance.)

3 class:| Location, Person, Organization  
---|---  
4 class:| Location, Person, Organization, Misc  
7 class: | Location, Person, Organization, Money, Percent, Date, Time  
  

These models each use distributional similarity features, which provide
considerable performance gain at the cost of increasing their size and
runtime. We also have models that are the same except without the
distributional similarity features. You can find them in our English models
jar. You can either unpack the jar file or add it to the classpath; if you add
the jar file to the classpath, you can then load the models from the path
`edu/stanford/nlp/models/...`. You can run `jar -tf <jar-file>` to get the
list of files in the jar file.

Also available are caseless versions of these models, better for use on texts
that are mainly lower or upper case, rather than follow the conventions of
standard English

[CoreNLP models jars download
page](https://stanfordnlp.github.io/CoreNLP/index.html#download)  

**Important note:** There was a problem with the v3.6.0 English Caseless NER
model. See [this page](http://stanfordnlp.github.io/CoreNLP/caseless.html).

  

#### German

A German NER model is available, based on work by Manaal Faruqui and Sebastian
Padó. You can find it in the CoreNLP German models jar. For citation and other
information relating to the German classifiers, please see [ Sebastian Pado's
German NER page](http://www.nlpado.de/~sebastian/software/ner_german.html)
(but the models there are now many years old; you should use the better models
that we have!). It is a 4 class IOB1 classifier (see, e.g., [Memory-Based
Shallow Parsing](https://arxiv.org/abs/cs/0204049) by Erik F. Tjong Kim Sang).
The tags given to words are: I-LOC, I-PER, I-ORG, I-MISC, B-LOC, B-PER, B-ORG,
B-MISC, O. It is trained over the CoNLL 2003 data with distributional
similarity classes built from the Huge German Corpus.

German resources (written in German): [a help page for Stanford
NER](https://fortext.net/routinen/lerneinheiten/named-entity-recognition-mit-
dem-stanford-named-entity-recognizer); [a workshop on using Stanford CoreNLP
for NER](https://dh3.hypotheses.org/886) with [materials
available](https://github.com/MarieFlueh/Workshop-NER-fuer-
GeisteswissenschaftlerInnen).

[CoreNLP models jars download
page](https://stanfordnlp.github.io/CoreNLP/index.html#download)  

Here are a couple of commands using these models, two sample files, and a
couple of notes. Running on TSV files: the models were saved with options for
testing on German CoNLL NER files. While the models use just the surface word
form, the input reader expects the word in the first column and the class in
the fifth colum (1-indexed colums). You can either make the input like that or
else change the expectations with, say, the option `-map "word=0,answer=1"`
(0-indexed columns). These models were also trained on data with straight
ASCII quotes and BIO entity tags. Also, be careful of the text encoding: The
default is Unicode; use `-encoding iso-8859-15` if the text is in 8-bit
encoding.  

> TSV mini test file: [`german-ner.tsv`](german-ner.tsv) — Text mini test
> file: [`german-ner.txt`](german-ner.txt)  
>
>  
>  
>     java -cp "*" edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier
> edu/stanford/nlp/models/ner/german.conll.hgc_175m_600.crf.ser.gz -testFile
> german-ner.tsv
>     java -cp "*" edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier
> edu/stanford/nlp/models/ner/german.conll.hgc_175m_600.crf.ser.gz
> -tokenizerOptions latexQuotes=false -textFile german-ner.txt
>  

  

#### Spanish

From version 3.4.1 forward, we have a Spanish model available for NER. It is
included in the Spanish corenlp models jar.

[CoreNLP models jars download
page](https://stanfordnlp.github.io/CoreNLP/index.html#download)  

#### Chinese

We also provide Chinese models built from the Ontonotes Chinese named entity
data. There are two models, one using distributional similarity clusters and
one without. These are designed to be run on _word-segmented Chinese_. So, if
you want to use these on normal Chinese text, you will first need to run
[Stanford Word Segmenter](http://nlp.stanford.edu/software/segmenter.html) or
some other Chinese word segmenter, and then run NER on the output of that!

[CoreNLP models jars download
page](https://stanfordnlp.github.io/CoreNLP/index.html#download)  

### Online Demo

We have an [online demo](http://nlp.stanford.edu:8080/ner) of several of our
NER models. Special thanks to [Dat Hoang](https://github.com/dat), who
provided the initial version. Note that the online demo demonstrates single
CRF models; in order to see the effect of the time annotator or the combined
models, see [CoreNLP](http://nlp.stanford.edu/software/corenlp.html).

  

### Release History

  
Version| Date| Description  
---|---|---  
[4.2.0](stanford-ner-4.2.0.zip) | 2020-11-17 | Update for compatibility  
[4.0.0](stanford-ner-4.0.0.zip) | 2020-04-19 | Update to UDv2.0 tokenization  
[3.9.2](stanford-ner-2018-10-16.zip) | 2018-10-16 | Updated for compatibility  
[3.9.1](stanford-ner-2018-02-27.zip) | 2018-02-27 | KBP ner models for Chinese
and Spanish  
[3.8.0](stanford-ner-2017-06-09.zip) | 2017-06-09 | Updated for compatibility  
[3.7.0](stanford-ner-2016-10-31.zip) | 2016-10-31 | Improvements to Chinese
and German NER  
[3.6.0](stanford-ner-2015-12-09.zip) | 2015-12-09 | Updated for compatibility  
[3.5.2](stanford-ner-2015-04-20.zip) | 2015-04-20 | synch standalone and
CoreNLP functionality  
[3.5.1](stanford-ner-2015-01-29.zip) | 2015-01-29 | Substantial accuracy
improvements  
[3.5.0](stanford-ner-2014-10-26.zip) | 2014-10-26 | Upgrade to Java 8  
[3.4.1](stanford-ner-2014-08-27.zip) | 2014-08-27 | Added Spanish models  
[3.4](stanford-ner-2014-06-16.zip) | 2014-06-16 | Fix serialization of new
models  
[3.3.1](stanford-ner-2014-01-04.zip) | 2014-01-04 | Bugfix release  
[3.3.0](stanford-ner-2013-11-12.zip) | 2013-11-12 | Updated for compatibility  
[3.2.0](stanford-ner-2013-06-20.zip) | 2013-06-20 | Improved line by line
handling  
[1.2.8](stanford-ner-2013-04-04.zip) | 2013-04-04 | -nthreads option  
[1.2.7](stanford-ner-2012-11-11.zip) | 2012-11-11 | Add Chinese model, include
Wikipedia data in 3-class English model  
[1.2.6](stanford-ner-2012-07-09.tgz) | 2012-07-09 | Minor bug fixes  
[1.2.5](stanford-ner-2012-05-22.tgz) | 2012-05-22 | Fix encoding issue  
[1.2.4](stanford-ner-2012-04-07.tgz) | 2012-04-07 | Caseless versions of
models supported  
[1.2.3](stanford-ner-2012-01-06.tgz) | 2012-01-06 | Minor bug fixes  
[1.2.2](stanford-ner-2011-09-14.tgz) | 2011-09-14 | Improved thread safety  
[1.2.1](stanford-ner-2011-06-19.tgz) | 2011-06-19 | Models reduced in size but
on average improved in accuracy (improved distsim clusters)  
[1.2](stanford-ner-2011-05-16.tgz) | 2011-05-16 | Normal download includes 3,
4, and 7 class models. Updated for compatibility with other software releases.  
[1.1.1](stanford-ner-2009-01-16.tgz) | 2009-01-16 | Minor bug and usability
fixes, and changed API (in particular the methods to classify and output
tagged text)  
[1.1](stanford-ner-2008-05-07.tgz) | 2008-05-07 | Additional feature flags,
various code updates  
[1.0](stanford-ner-2006-09-18.tar.gz) | 2006-09-18 | Initial release  