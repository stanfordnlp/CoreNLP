---
layout: page
title: POS Tagger
keywords: pos, tagger
permalink: '/tools_pos_tagger.html'
nav_order: 18
toc: true
parent: Additional Tools
---

### About

A Part-Of-Speech Tagger (POS Tagger) is a piece of software that reads text in
some language and assigns parts of speech to each word (and other token), such
as noun, verb, adjective, etc., although generally computational applications
use more fine-grained POS tags like 'noun-plural'. This software is a Java
implementation of the log-linear part-of-speech taggers described in these
papers (if citing just one paper, cite the 2003 one):

> Kristina Toutanova and Christopher D. Manning. 2000. [Enriching the
> Knowledge Sources Used in a Maximum Entropy Part-of-Speech
> Tagger](http://nlp.stanford.edu/~manning/papers/emnlp2000.pdf). In
> _Proceedings of the Joint SIGDAT Conference on Empirical Methods in Natural
> Language Processing and Very Large Corpora (EMNLP/VLC-2000)_ , pp. 63-70.

> Kristina Toutanova, Dan Klein, Christopher Manning, and Yoram Singer. 2003.
> [Feature-Rich Part-of-Speech Tagging with a Cyclic Dependency
> Network](http://nlp.stanford.edu/~manning/papers/tagging.pdf). In
> _Proceedings of HLT-NAACL 2003_ , pp. 252-259.

The tagger was originally written by Kristina Toutanova. Since that time, Dan
Klein, Christopher Manning, William Morgan, Anna Rafferty, Michel Galley, and
John Bauer have improved its speed, performance, usability, and support for
other languages.

The system requires Java 8+ to be installed. Depending on whether you're
running 32 or 64 bit Java and the complexity of the tagger model, you'll need
somewhere between 60 and 200 MB of memory to run a trained tagger (i.e., you
may need to give Java an option like `java -mx200m`). Plenty of memory is
needed to train a tagger. It again depends on the complexity of the model but
at least 1GB is usually needed, often more.

Current downloads contain three trained tagger models for English, two each
for Chinese and Arabic, and one each for French, German, and Spanish. The
tagger can be retrained on any language, given POS-annotated training text for
the language.

**Part-of-speech name abbreviations:** The English taggers use the Penn
Treebank tag set. Here are some links to documentation of the Penn Treebank
English POS tag set: [1993 _Computational Linguistics_ article in
PDF](http://acl.ldc.upenn.edu/J/J93/J93-2004.pdf), [Chameleon Metadata list
(which includes recent additions to the
set)](https://chameleonmetadata.com/Education/NLP-3/ref_nlp_nlp4j_pos_tags_list.php).
The French, German, and Spanish models all use [the UD (v2)
tagset](https://universaldependencies.org/u/pos/). See the included README-
Models.txt in the models directory for more information about the tagset for
each language.

The tagger code is dual licensed (in a similar manner to MySQL, etc.). The
tagger is **licensed under the[GNU General Public
License](http://www.gnu.org/licenses/gpl-2.0.html)** (v2 or later), which
allows many free uses. Source is included. The package includes components for
command-line invocation, running as a server, and a Java API. For distributors
of [proprietary software](http://www.gnu.org/licenses/gpl-
faq.html#GPLInProprietarySystem), [commercial
licensing](http://otlportal.stanford.edu/techfinder/technology/ID=26062) is
available. If you don't need a commercial license, but would like to support
maintenance of these tools, we welcome gift funding.

  

### Questions

For documentation, first take a look at the included `README.txt`.

Galal Aly wrote a [tagging
tutorial](http://www.galalaly.me/index.php/2011/05/tagging-text-with-stanford-
pos-tagger-in-java-applications/) focused on usage in Java with Eclipse.

For more details, look at our included javadocs, particularly [the javadoc for
MaxentTagger](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/tagger/maxent/MaxentTagger.html).

There is a [FAQ](http://nlp.stanford.edu/software/pos-tagger-faq.html).

Matthew Jockers kindly produced [ an example and tutorial for running the
tagger](http://www.matthewjockers.net/2008/05/29/pos-tagging-xml-with-xgrid-
and-the-stanford-log-linear-part-of-speech-tagger/). This particularly
concentrates on command-line usage with XML and (Mac OS X) xGrid.

Have a support question? Ask us on [Stack Overflow](http://stackoverflow.com)
using the tag `stanford-nlp`.

Feedback and bug reports / fixes can be sent to our mailing lists.

#### Recipes

Tag text from a file _text.txt_ , producing tab-separated-column output:

> java -cp "*" edu.stanford.nlp.tagger.maxent.MaxentTagger -model
> models/english-left3words-distsim.tagger -textFile text.txt -outputFormat
> tsv -outputFile text.tag

### Mailing Lists

We have 3 mailing lists for the Stanford POS Tagger, all of which are shared
with other JavaNLP tools (with the exclusion of the parser). Each address is
at `@lists.stanford.edu`:

  1. `java-nlp-user` This is the best list to post to in order to send feature requests, make announcements, or for discussion among JavaNLP users. (Please ask support questions on [Stack Overflow](http://stackoverflow.com) using the `stanford-nlp` tag.) 

You have to subscribe to be able to use this list. Join the list via [this
webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-user) or by
emailing `java-nlp-user-join@lists.stanford.edu`. (Leave the subject and
message body empty.) You can also [look at the list
archives](https://mailman.stanford.edu/pipermail/java-nlp-user/).

  2. `java-nlp-announce` This list will be used only to announce new versions of Stanford JavaNLP tools. So it will be very low volume (expect 1-3 messages a year). Join the list via [this webpage](https://mailman.stanford.edu/mailman/listinfo/java-nlp-announce) or by emailing `java-nlp-announce-join@lists.stanford.edu`. (Leave the subject and message body empty.) 
  3. `java-nlp-support` This list goes only to the software maintainers. It's a good address for licensing questions, etc. **For general use and support questions, you're better off joining and using`java-nlp-user`.** You cannot join `java-nlp-support`, but you can mail questions to `java-nlp-support@lists.stanford.edu`. 

  

### Download

[ Download Stanford Tagger version 4.2.0](stanford-tagger-4.2.0.zip) [75 MB]

  

The full download is a 75 MB zipped file including models for English, Arabic,
Chinese, French, Spanish, and German. If you unpack the tar file, you should
have everything needed. This software provides a GUI demo, a command-line
interface, and an API. Simple scripts are included to invoke the tagger. For
more information on use, see the included README.txt.

  

### Extensions

#### Other models for the Stanford Tagger

  * **Twitter English:** An [English Twitter POS tagger model](https://gate.ac.uk/wiki/twitter-postagger.html) is available by Leon Derczynski and others at Sheffield. 

#### Packages for using the Stanford POS tagger from other programming
languages (by other people)

  * **Docker:** Cuzzo Yahn provides [a docker image for the Stanford POS tagger with the XMLRPC service](https://github.com/cuzzo/node-stanford-postagger) ([docker registry](https://registry.hub.docker.com/u/cuzzo/stanford-pos-tagger/)). 
  * **F#/C#/.NET:** Sergey Tihon has [ported the Stanford POS tagger to F# (.NET)](https://github.com/sergey-tihon/fsharp-stanford-nlp-samples/), using IKVM. See [his blog post](http://sergeytihon.wordpress.com/2013/02/08/nlp-stanford-pos-tagger-with-f-net/). 
  * **GATE:** GATE includes a [Stanofrd POS tagger plugin](http://gate.ac.uk/sale/tao/splitch23.html#sec:misc:creole:stanford) and the GATE team at the University of Sheffield produced a [Twitter tagger model and tagged data set](http://gate.ac.uk/wiki/twitter-postagger.html) compatible with version 3.3.1. 
  * **Go:** Kamil Drążkiewicz wrote [Go-Stanford-NLP](https://github.com/kamildrazkiewicz/go-stanford-nlp) as an interface to the Stanford POS tagger. 
  * **Javascript (node.js):** Cuzzo Yahn wrote [a node.js client for interacting with the Stanford POS tagger](https://github.com/cuzzo/node-stanford-postagger), using the XML-RPC service ([npm page](https://www.npmjs.org/package/node-stanford-postagger)). Ralf Engelschall wrote another: [Stanford-POSTagger](https://www.npmjs.com/package/stanford-postagger). 
  * **Matlab:** József Vass makes available on GitHub [a good package](https://github.com/jzsfvss/POSTaggerSML) for using the Stanford POS Tagger in MatLab. Earlier, Utkarsh Upadhyay also provided a [Matlab function for accessing the Stanford POS tagger](https://github.com/musically-ut/matlab-stanford-postagger). But note that it loads the tagger each time it is called, and you don't want to do that! You should load the tagger only once and then re-use it. Rojbir Pabla also contributed a simple [script](https://www.mathworks.com/matlabcentral/fileexchange/55297-matlab-stanford-postagger-for-a-text-document?focused=5936355&tab=function&s_tid=gn_loc_drop), which is on the MathWorks site. 
  * **PHP:** Patrick Schur in 2017 wrote [PHP wrapper for Stanford POS and NER taggers](https://github.com/patrickschur/stanford-nlp-tagger). Also on [packagist](https://packagist.org/packages/patrickschur/stanford-nlp-tagger). Other choices: [PHP wrapper by Anthony Gentile](https://github.com/agentile/PHP-Stanford-NLP); [PHP wrapper by Charles Hays](http://charleshays.com/php-class-wrapper-for-stanford-part-of-speech-tagger/) ([on github](https://github.com/TheCodeSlinger/PHP-Class-Stanford-POS-Tagger)). 
  * **Python:** 2020s advice: You should always use [a Python interface to the CoreNLPServer for performant use in Python. For NLTK, use the [nltk.parse.corenlp](http://www.nltk.org/api/nltk.parse.html#module-nltk.parse.corenlp) module. Historically, [NLTK (2.0+)](http://nltk.org/) contains an interface to the Stanford POS tagger. The original version was written by Nitin Madnani: [documentation](http://nltk.org/api/nltk.tag.html#module-nltk.tag.stanford) (note: in old versions, manually set the character encoding or you get ASCII!), [code](http://nltk.org/_modules/nltk/tag/stanford.html), [on Github](https://github.com/nltk/nltk/blob/master/nltk/tag/stanford.py). After a while there was a better [CoreNLPPOSTagger](http://www.nltk.org/_modules/nltk/tag/stanford.html#CoreNLPPOSTagger) class. ](https://stanfordnlp.github.io/CoreNLP/other-languages.html#python)
  * **Ruby:** tiendung has written [a Ruby Binding](http://github.com/tiendung/ruby-nlp) for the Stanford POS tagger and Named Entity Recognizer. 
  * **XML-RPC:** Ali Afshar wrote [an XML-RPC service interface](https://github.com/turian/stanford-pos-tagger-service) to the Stanford POS tagger. 

  

### Release History

| Version | Date | Description |
|---|---|---|
| 4.2.0 | 2020-11-17 | Add currency data for English models<br>          <br/><a href="stanford-tagger-4.2.0.zip">Full</a> |
| 4.1.0 | 2020-08-06 | Missing tagger extractor class added, Spanish tokenization improvements<br>          <br/><a href="stanford-tagger-4.1.0.zip">Full</a> |
| 4.0.0 | 2020-04-19 | Model tokenization updated to UDv2.0<br>          <br/><a href="stanford-tagger-4.0.0.zip">Full</a> |
| 3.9.2 | 2018-10-16 | New English models, better currency symbol handling<br>      <br/><a href="stanford-postagger-2018-10-16.zip">English</a> /<br>          <a href="stanford-postagger-full-2018-10-16.zip">Full</a> |
| 3.9.1 | 2018-02-27 | new French UD model<br>      <br/><a href="stanford-postagger-2017-06-09.zip">English</a> /<br>          <a href="stanford-postagger-full-2017-06-09.zip">Full</a> |
| 3.8.0 | 2017-06-09 | new Spanish and French UD models<br>      <br/><a href="stanford-postagger-2017-06-09.zip">English</a> /<br>          <a href="stanford-postagger-full-2017-06-09.zip">Full</a> |
| 3.7.0 | 2016-10-31 | Update for compatibility, German UD model<br>      <br/><a href="stanford-postagger-2016-10-31.zip">English</a> /<br>          <a href="stanford-postagger-full-2016-10-31.zip">Full</a> |
| 3.6.0 | 2015-12-09 | Updated for compatibility<br>      <br/><a href="stanford-postagger-2015-12-09.zip">English</a> /<br>          <a href="stanford-postagger-full-2015-12-09.zip">Full</a> |
| 3.5.2 | 2015-04-20 | Updated for compatibility<br>      <br/><a href="stanford-postagger-2015-04-20.zip">English</a> /<br>          <a href="stanford-postagger-full-2015-04-20.zip">Full</a> |
| 3.5.1 | 2015-01-29 | General bugfixes<br>      <br/><a href="stanford-postagger-2015-01-29.zip">English</a> /<br>          <a href="stanford-postagger-full-2015-01-29.zip">Full</a> |
| 3.5.0 | 2014-10-26 | Upgrade to Java 8<br>      <br/><a href="stanford-postagger-2014-10-26.zip">English</a> /<br>          <a href="stanford-postagger-full-2014-10-26.zip">Full</a> |
| 3.4.1 | 2014-08-27 | Add Spanish model<br>      <br/><a href="stanford-postagger-2014-08-27.zip">English</a> /<br>          <a href="stanford-postagger-full-2014-08-27.zip">Full</a> |
| 3.4 | 2014-06-16 | French model uses CC tagset<br>      <br/><a href="stanford-postagger-2014-06-16.zip">English</a> /<br>          <a href="stanford-postagger-full-2014-06-16.zip">Full</a> |
| 3.3.1 | 2014-01-04 | Bugfix release<br>      <br/><a href="stanford-postagger-2014-01-04.zip">English</a> /<br>          <a href="stanford-postagger-full-2014-01-04.zip">Full</a> |
| 3.3.0 | 2013-11-12 | imperatives included in English model<br>      <br/><a href="stanford-postagger-2013-11-12.zip">English</a> /<br>          <a href="stanford-postagger-full-2013-11-12.zip">Full</a> |
| 3.2.0 | 2013-06-20 | improved speed &amp; size of all models<br>      <br/><a href="stanford-postagger-2013-06-20.zip">English</a> /<br>          <a href="stanford-postagger-full-2013-06-20.zip">Full</a> |
| 3.1.5 | 2013-04-04 | ctb7 model, -nthreads option, improved speed<br>      <br/><a href="stanford-postagger-2013-04-04.zip">English</a> /<br>          <a href="stanford-postagger-full-2013-04-04.zip">Full</a> |
| 3.1.4 | 2012-11-11 | Improved Chinese model<br>      <br/><a href="stanford-postagger-2012-11-11.zip">English</a> /<br>          <a href="stanford-postagger-full-2012-11-11.zip">Full</a> |
| 3.1.3 | 2012-07-09 | Minor bug fixes<br>      <br/><a href="stanford-postagger-2012-07-09.tgz">English</a> /<br>          <a href="stanford-postagger-full-2012-07-09.tgz">Full</a> |
| 3.1.2 | 2012-05-22 | Included some "tech" words in the latest model<br>      <br/><a href="stanford-postagger-2012-05-22.tgz">English</a> /<br>          <a href="stanford-postagger-full-2012-05-22.tgz">Full</a> |
| 3.1.1 | 2012-03-09 | Caseless models added for English<br>      <br/><a href="stanford-postagger-2012-03-09.tgz">English</a> /<br>          <a href="stanford-postagger-full-2012-03-09.tgz">Full</a> |
| 3.1.0 | 2012-01-06 | French tagger added, tagging speed improved<br>      <br/><a href="stanford-postagger-2012-01-06.tgz">English</a> /<br>          <a href="stanford-postagger-full-2012-01-06.tgz">Full</a> |
| 3.0.4 | 2011-09-14 | Compatible with other recent Stanford releases.<br>      <br/><a href="stanford-postagger-2011-09-14.tgz">English</a> /<br>          <a href="stanford-postagger-full-2011-09-14.tgz">Full</a> |
| 3.0.3 | 2011-06-19 | Compatible with other recent Stanford releases.<br>      <br/><a href="stanford-postagger-2011-06-19.tgz">English</a> /<br>          <a href="stanford-postagger-full-2011-06-19.tgz">Full</a> |
| 3.0.2 | 2011-05-15 | Addition of TSV input format.<br>      <br/><a href="stanford-postagger-2011-05-15.tgz">English</a> /<br>          <a href="stanford-postagger-full-2011-05-15.tgz">Full</a> |
| 3.0.1 | 2011-04-20 | Faster Arabic and German models.<br>        Compatible with other recent Stanford releases.<br>      <br/><a href="stanford-postagger-2011-04-20.tgz">English</a> /<br>          <a href="stanford-postagger-full-2011-04-20.tgz">Full</a> |
| 3.0 | 2010-05-21 | Tagger is now re-entrant.  New tagger objects are loaded with <i>tagger = new MaxentTagger(path)</i> and then used with <i>tagger.tagMethod...</i><br><br/><a href="stanford-postagger-2010-05-26.tgz">English</a> /<br>          <a href="stanford-postagger-full-2010-05-26.tgz">Full</a> |
| 2.0 | 2009-12-24 | An order of magnitude faster, slightly more accurate best model,<br>    more options for training and deployment.<br>      <br/><a href="stanford-postagger-2009-12-24.tgz">English</a> /<br>          <a href="stanford-postagger-full-2009-12-24.tgz">Full</a> |
| 1.6 | 2008-09-28 | A fraction better, a fraction faster, more flexible model specification,<br>        and quite a few less bugs.<br>      <br/><a href="stanford-postagger-2008-09-28.tar.gz">English</a> /<br>          <a href="stanford-postagger-full-2008-09-28.tar.gz">Full</a> |
| 1.5.1 | 2008-06-06 | Tagger properties are now saved with the tagger, making taggers more portable; tagger can be trained off of treebank data or tagged text; fixes classpath bugs in 2 June 2008 patch; new foreign language taggers released on 7 July 2008 and packaged with 1.5.1.<br>      <br/><a href="stanford-postagger-2008-06-06.tar.gz">English</a> /<br>          <a href="stanford-postagger-full-2008-06-06.tar.gz">Full</a> /<br>          <a href="stanford-postagger-full-2008-07-07.tar.gz">Updated models</a><br> |
| 1.5 | 2008-05-21 | Added taggers for several languages, support for reading from and writing to XML, better support for<br>  changing the encoding, distributional similarity options, and many more small changes; patched on 2 June 2008 to fix a bug with tagging pre-tokenized text.<br>      <br/><a href="stanford-postagger-2008-05-19.tar.gz">English</a> /<br>          <a href="stanford-postagger-full-2008-05-19.tar.gz">Full</a> |
| 1.0 | 2006-01-10 | First cleaned-up release after Kristina graduated.<br>      <br/><a href="postagger-2006-01-20.tar.gz">Old School</a> |
| 0.1 | 2004-08-16 | First release. |