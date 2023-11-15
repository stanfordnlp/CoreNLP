---
layout: page
title: Classifier
keywords: classifier
permalink: '/tools_classifier.html'
nav_order: 8
toc: true
parent: Additional Tools
---

A classifier is a machine learning tool that will take data items and place
them into one of _k_ classes. A probabilistic classifier, like this one, can
also give a probability distribution over the class assignment for a data
item. This software is a Java implementation of a maximum entropy classifier.
Maximum entropy models are otherwise known as softmax classifiers and are
essentially equivalent to multiclass logistic regression models (though
parameterized slightly differently, in a way that is advantageous with sparse
explanatory feature vectors). In other words, this is the same basic
technology that you're usually getting in various of the cloud-based machine
learning APIs (Amazon, Google, ...) The classification method is described in:

> Christopher Manning and Dan Klein. 2003. Optimization, Maxent Models, and
> Conditional Estimation without Magic. Tutorial at HLT-NAACL 2003 and ACL 2003.
> [pdf slides](https://nlp.stanford.edu/pubs/maxent-tutorial-slides.pdf)
> [pdf handouts](https://nlp.stanford.edu/pubs/maxent-tutorial-slides-6.pdf)

Version 2 of the classifier was developed by Anna Rafferty, Alex Kleeman,
Jenny Finkel, and Christopher Manning.

The software requires requires Java (now Java 8). As well as API access, the
program includes an easy-to-use command-line interface,
`ColumnDataClassifier`, for building models. Its features are especially
suited to building models over text data, but it also supports numeric
variables.

**Licensing.** The Stanford Classifier is available for download, **licensed
under the [GNU General Public License](http://www.gnu.org/licenses/gpl-2.0.html)** (v2 or later). Source is
included. The Stanford Classifier code is dual licensed (in a similar manner
to MySQL, etc.). Open source licensing is under the _full_ GPL, which allows
many free uses. For distributors of [proprietary
software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem),
[commercial
licensing](http://otlportal.stanford.edu/techfinder/technology/ID=27277) is
available. If you don't need a commercial license, but would like to support
maintenance of these tools, we welcome gift funding.

The download is a 9.6 MB zipped file. If you unpack that file, you should have
everything needed, including example files and documentation. Start by reading
the `README.txt` file. Send any questions or feedback to
[`java-nlp-support@lists.stanford.edu`](mailto:java-nlp-support@lists.stanford.edu).

### Introduction

An introduction to the classifier and some examples of its use are available
on our [Wiki page](http://nlp.stanford.edu/wiki/Software/Classifier). You can
also look at the [javadoc for ColumnDataClassifier](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/classify/ColumnDataClassifier.html).

### Extensions: Packages by others using the Stanford Classifier

- **JavaScript/npm:** Milos Bejda has written [stanford-classifier](https://www.npmjs.com/package/stanford-classifier) Nodejs wrapper. [Source](https://github.com/mbejda/Nodejs-Stanford-Classifier) on github. 

### Availability

The Stanford Classifier is included in the most recent versions of CoreNLP.

### Questions

Have a support question, PR, or bug report?  Please post an issue [on our Github](https://github.com/stanfordnlp/CoreNLP)

Feedback and licensing issues can also be
sent to our mailing lists (see immediately below).

### Mailing Lists

We have 3 mailing lists for the Stanford Classifier all of which are shared
with other JavaNLP tools (with the exclusion of the parser). Each address is
at `@lists.stanford.edu`:

  1. `java-nlp-user` This is the best list to post to in order to send feature requests, make announcements, or for discussion among JavaNLP users. (Please ask support questions on [Github](https://github.com/stanfordnlp/CoreNLP).)
You have to subscribe to be able to use this list. Join the list via [this
webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-user) or by
emailing `java-nlp-user-join@lists.stanford.edu`. (Leave the subject and
message body empty.) You can also [look at the list archives](https://mailman.stanford.edu/pipermail/java-nlp-user/).

  2. `java-nlp-announce` This list will be used only to announce new versions of Stanford JavaNLP tools. So it will be very low volume (expect 2-4 messages a year). Join the list via [this webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-announce) or by emailing `java-nlp-announce-join@lists.stanford.edu`. (Leave the subject and message body empty.)

  3. `java-nlp-support` This list goes only to the software maintainers. It's a good address for licensing questions, etc. **For general use and support questions, you're better off using Github or joining and using `java-nlp-user`.** You cannot join `java-nlp-support`, but you can mail questions to `java-nlp-support@lists.stanford.edu`.

  
### Release history

There is a standalone distribution of the classifier which has not been updated in some time.

**[Download Stanford Classifier version 4.2.0](stanford-classifier-4.2.0.zip)**  

| Version | Date | Description |
|---|---|---|
|  |
| <a href="stanford-classifier-4.2.0.zip">4.2.0</a> | 2020-11-17 | Update for compatibility |
| <a href="stanford-classifier-4.0.0.zip">4.0.0</a> | 2020-04-19 | Update for compatibility |
| <a href="stanford-classifier-2018-10-16.zip">3.9.2</a> | 2018-10-16 | Updated for compatibility |
| <a href="stanford-classifier-2018-02-27.zip">3.9.1</a> | 2018-02-27 | Updated for compatibility |
| <a href="stanford-classifier-2017-06-09.zip">3.8.0</a> | 2017-06-09 | Updated for compatibility |
| <a href="stanford-classifier-2016-10-31.zip">3.7.0</a> | 2016-10-31 | Update for compatibility |
| <a href="stanford-classifier-2015-12-09.zip">3.6.0</a> | 2015-12-09 | Updated for compatibility |
| <a href="stanford-classifier-2015-04-20.zip">3.5.2</a> | 2015-04-20 | Updated for compatibility |
| <a href="stanford-classifier-2015-01-29.zip">3.5.1</a> | 2015-01-29 | New input / output format options; support for GloVe word vector features |
| <a href="stanford-classifier-2014-10-26.zip">3.5.0</a> | 2014-10-26 | Upgrade to Java 8 |
| <a href="stanford-classifier-2014-08-27.zip">3.4.1</a> | 2014-08-27 | Updated for compatibility with other Stanford releases |
| <a href="stanford-classifier-2014-06-16.zip">3.4</a> | 2014-06-16 | Updated for compatibility with other Stanford releases |
| <a href="stanford-classifier-2014-01-04.zip">3.3.1</a> | 2014-01-04 | Bugfix release |
| <a href="stanford-classifier-2013-11-12.zip">3.3.0</a> | 2013-11-12 | Updated for compatibility with other Stanford releases |
| <a href="stanford-classifier-2013-06-20.zip">3.2.0</a> | 2013-06-20 | Updated for compatibility with other Stanford releases |
| <a href="stanford-classifier-2013-04-04.tgz">2.1.8</a> | 2013-04-04 | Updated for compatibility with other Stanford releases |
| <a href="stanford-classifier-2012-11-11.zip">2.1.7</a> | 2012-11-11 | Pair of word features added |
| <a href="stanford-classifier-2012-07-09.tgz">2.1.6</a> | 2012-07-09 | Minor bug fixes |
| <a href="stanford-classifier-2012-05-22.tgz">2.1.5</a> | 2012-05-22 | Updated for compatibility with other Stanford releases |
| <a href="stanford-classifier-2012-03-09.tgz">2.1.4</a> | 2012-03-09 | Bugfix for svmlight format |
| <a href="stanford-classifier-2011-12-22.tgz">2.1.3</a> | 2011-12-22 | Updated for compatibility with other Stanford releases |
| <a href="stanford-classifier-2011-09-14.tar.gz">2.1.2</a> | 2011-09-14 | Change ColumnDataClassifier to be an object with API rather than static methods; ColumnDataClassifier thread safe |
| <a href="stanford-classifier-2011-06-19.tar.gz">2.1.1</a> | 2011-06-19 | Updated for compatibility with other Stanford releases |
| <a href="stanford-classifier-2011-05-15.tar.gz">2.1</a> | 2011-05-15 | Updated with more documentation |
| <a href="stanford-classifier-2007-08-15.tar.gz">2.0</a> | 2007-08-15 | New command line interface, substantial increase in options and features (updated on 2007-09-28 with a bug fix) |
| 1.0 | 2003-05-26 | Initial release |

  

