---
layout: page
title: Chinese and Arabic Segmenter
keywords: crf, segmenter
permalink: '/tools_segmenter.html'
nav_order: 16
toc: true
parent: Additional Tools
---

This software is for “tokenizing” or “segmenting” the words of Chinese or
Arabic text. Tokenization of raw text is a standard pre-processing step for
many NLP tasks. For English, tokenization usually involves punctuation
splitting and separation of some affixes like possessives. Other languages
require more extensive token pre-processing, which is usually called
_segmentation_.

The Stanford Word Segmenter currently supports Arabic and Chinese. (The
[Stanford Tokenizer](https://nlp.stanford.edu/software/tokenizer.html) can be
used for English, French, and Spanish.) The provided segmentation schemes have
been found to work well for a variety of applications.

The system requires Java 1.8+ to be installed. We recommend at least 1G of
memory for documents that contain long sentences. For files with shorter
sentences (e.g., 20 tokens), you can decrease the memory requirement by
changing the option `java -mx1g` in the run scripts.

### Arabic

Arabic is a root-and-template language with abundant bound clitics. These
clitics include possessives, pronouns, and discourse connectives. The Arabic
segmenter segments clitics from words (only). Segmenting clitics attached to
words reduces lexical sparsity and simplifies syntactic analysis.

The Arabic segmenter model processes raw text according to the Penn Arabic
Treebank 3 (ATB) standard. It is an implementation of the segmenter described
in:

> Will Monroe, Spence Green, and Christopher D. Manning. 2014. [_Word
> Segmentation of Informal Arabic with Domain
> Adaptation_](http://nlp.stanford.edu/pubs/monroe-green-manning-
> acls2014.pdf). In ACL.

### Chinese

Chinese is standardly written without spaces between words (as are some other
languages). This software will split Chinese text into a sequence of words,
defined according to some word segmentation standard. It is a Java
implementation of the CRF-based Chinese Word Segmenter described in:

> Huihsin Tseng, Pichuan Chang, Galen Andrew, Daniel Jurafsky and Christopher
> Manning. 2005. [_A Conditional Random Field Word
> Segmenter_](http://nlp.stanford.edu/pubs/sighan2005.pdf). In Fourth SIGHAN
> Workshop on Chinese Language Processing.

Two models with two different segmentation standards are included: [ Chinese
Penn Treebank standard](http://www.cis.upenn.edu/~chinese/segguide.3rd.ch.pdf)
and [ Peking University
standard](http://sighan.cs.uchicago.edu/bakeoff2005/data/pku_spec.pdf).

On May 21, 2008, we released a version that makes use of lexicon features.
With external lexicon features, the segmenter segments more consistently and
also achieves higher F measure when we train and test on the bakeoff data.
This version is close to the CRF-Lex segmenter described in:

> Pi-Chuan Chang, Michel Galley and Chris Manning. 2008. [_Optimizing Chinese
> Word Segmentation for Machine Translation
> Performance_](http://nlp.stanford.edu/pubs/acl-wmt08-cws.pdf). In WMT.

The older version (2006-05-11) without external lexicon features is still
available for download, but we recommend using the latest version.

Another new feature of recent releases is that the segmenter can now output
k-best segmentations. [ An example of how to train the
segmenter](http://nlp.stanford.edu/software/trainSegmenter-20080521.tar.gz) is
now also available.

### Tutorials

  * Michelle Fullwood wrote [a nice tutorial on segmenting and parsing Chinese](http://michelleful.github.io/code-blog/2015/09/10/parsing-chinese-with-stanford/) with the Stanford NLP tools.
  * [This page](http://www.linguisticsweb.org/doku.php?id=linguisticsweb:tutorials:linguistics_tutorials:automaticannotation:stanford_word_segmenter) from linguisticsweb has a few Windows examples (but text is a bit sparse). 

### Download

The segmenter is available for download, **licensed under the[GNU General
Public License](http://www.gnu.org/licenses/gpl-2.0.html)** (v2 or later).
Source is included. The package includes components for command-line
invocation and a Java API. The segmenter code is dual licensed (in a similar
manner to MySQL, etc.). Open source licensing is under the _full_ GPL, which
allows many free uses. For distributors of [proprietary
software](http://www.gnu.org/licenses/gpl-faq.html#GPLInProprietarySystem),
[commercial
licensing](http://otlportal.stanford.edu/techfinder/technology/ID=27276) is
available. If you don't need a commercial license, but would like to support
maintenance of these tools, we welcome gift funding.

The download is a zipped file consisting of model files, compiled code, and
source files. If you unpack the tar file, you should have everything needed.
Simple scripts are included to invoke the segmenter.

[Download Stanford Word Segmenter version 4.2.0](stanford-segmenter-4.2.0.zip)

### Questions

Have a support question? Please ask us on [Stack
Overflow](http://stackoverflow.com) using the tag `stanford-nlp`.

Feedback, questions, licensing issues, and bug reports / fixes can also be
sent to our mailing lists (see immediately below).

### Mailing Lists

We have 3 mailing lists for the Stanford Word Segmenter all of which are
shared with other JavaNLP tools (with the exclusion of the parser). Each
address is at `@lists.stanford.edu`:

  1. `java-nlp-user` This is the best list to post to in order to send feature requests, make announcements, or for discussion among JavaNLP users. (Please ask support questions on [Stack Overflow](http://stackoverflow.com) using the `stanford-nlp` tag.) 

You have to subscribe to be able to use this list. Join the list via [this
webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-user) or by
emailing `java-nlp-user-join@lists.stanford.edu`. (Leave the subject and
message body empty.) You can also [look at the list
archives](https://mailman.stanford.edu/pipermail/java-nlp-user/).

  2. `java-nlp-announce` This list will be used only to announce new versions of Stanford JavaNLP tools. So it will be very low volume (expect 2-4 messages a year). Join the list via [this webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-announce) or by emailing `java-nlp-announce-join@lists.stanford.edu`. (Leave the subject and message body empty.)

  3. `java-nlp-support` This list goes only to the software maintainers. It's a good address for licensing questions, etc. **For general use and support questions, you're better off using Stack Overflow or joining and using`java-nlp-user`.** You cannot join `java-nlp-support`, but you can mail questions to `java-nlp-support@lists.stanford.edu`.

  

### Extensions: Packages by others using Stanford Word Segmenter

  * **F#/C#/.NET:** Sergey Tihon has [ported Stanford NER to F# (and other .NET languages, such as C#)](http://sergeytihon.wordpress.com/2013/09/09/stanford-word-segmenter-is-available-on-nuget/), using IKVM. It's available on NuGet. 
  * **Python:** The Stanford Word Segmenter is incorporated into [nltk's tokenize package](http://www.nltk.org/api/nltk.tokenize.html#module-nltk.tokenize.stanford_segmenter). 

  

### Release History

  
Version| Date| Description  
---|---|---  
[4.2.0](stanford-segmenter-4.2.0.zip) | 2020-11-17 | Update for compatibility  
[4.0.0](stanford-segmenter-4.0.0.zip) | 2020-04-19 | New Chinese segmenter trained off of CTB 9.0  
[3.9.2](stanford-segmenter-2018-10-16.zip) | 2018-10-16 | Updated for compatibility  
[3.9.1](stanford-segmenter-2018-02-27.zip) | 2018-02-27 | Updated for compatibility  
[3.8.0](stanford-segmenter-2017-06-09.zip) | 2017-06-09 | Update for compatibility  
[3.7.0](stanford-segmenter-2016-10-31.zip) | 2016-10-31 | Update for compatibility  
[3.6.0](stanford-segmenter-2015-12-09.zip) | 2015-12-09 | Updated for compatibility  
[3.5.2](stanford-segmenter-2015-04-20.zip) | 2015-04-20 | Updated for compatibility  
[3.5.1](stanford-segmenter-2015-01-29.zip) | 2015-01-29 | Updated for compatibility  
[3.5.0](stanford-segmenter-2014-10-26.zip) | 2014-10-26 | Upgrade to Java 8  
[3.4.1](stanford-segmenter-2014-08-27.zip) | 2014-08-27 | Updated for compatibility  
[3.4](stanford-segmenter-2014-06-16.zip) | 2014-06-16 | Updated Arabic model  
[3.3.1](stanford-segmenter-2014-01-04.zip) | 2014-01-04 | Bugfix release  
[3.3.0](stanford-segmenter-2013-11-12.zip) | 2013-11-12 | Updated for compatibility  
[3.2.0](stanford-segmenter-2013-06-20.zip) | 2013-06-20 | Improved line by line handling  
[1.6.8](stanford-segmenter-2013-04-04.zip) | 2013-04-04 | ctb7 model, -nthreads option  
[1.6.7](stanford-segmenter-2012-11-11.zip) | 2012-11-11 | Bugfixes for both Arabic and Chinese, Chinese segmenter can now load data from a jar file  
[1.6.6](stanford-segmenter-2012-07-09.tgz) | 2012-07-09 | Improved Arabic model  
[1.6.5](stanford-segmenter-2012-05-22.tar.gz) | 2012-05-22 | Fixed encoding problems, supports stdin for Chinese segmenter  
[1.6.4](stanford-segmenter-2012-05-07.tar.gz) | 2012-05-07 | Included Arabic model  
[1.6.3](stanford-chinese-segmenter-2012-01-08.tar.gz) | 2012-01-08 | Minor bug fixes  
[1.6.2](stanford-chinese-segmenter-2011-09-14.tar.gz) | 2011-09-14 | Improved thread safety  
[1.6.1](stanford-chinese-segmenter-2011-06-19.tar.gz) | 2011-06-19 | Fixed empty document bug when training new models  
[1.6](stanford-chinese-segmenter-2011-05-15.tar.gz) | 2011-05-15 | Models updated to be slightly more accurate; code correctly released so it now builds; updated for compatibility with other Stanford releases |
[1.5](stanford-chinese-segmenter-2008-05-21.tar.gz) | 2008-05-21 | (with external lexicon features; able to output k-best segmentations)  
[1.0](StanfordChineseSegmenter-2006-05-11.tar.gz) | 2006-05-11 | Initial release  