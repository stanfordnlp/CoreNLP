---
layout: page
title: Lexicalized Parser
keywords: lexparser, parser
permalink: '/tools_lex_parser.html'
nav_order: 12
toc: true
parent: Additional Tools
---

### About

A natural language parser is a program that works out the grammatical
**structure of sentences** , for instance, which groups of words go together
(as "phrases") and which words are the **subject** or **object** of a verb.
Probabilistic parsers use knowledge of language gained from hand-parsed
sentences to try to produce the _most likely_ analysis of new sentences. These
statistical parsers still make some mistakes, but commonly work rather well.
Their development was one of the biggest breakthroughs in natural language
processing in the 1990s. You can [try out our parser
online](http://nlp.stanford.edu:8080/parser/).

### Package contents

This package is a Java implementation of probabilistic natural language
parsers, both highly optimized PCFG and lexicalized dependency parsers, and a
lexicalized PCFG parser. The original version of this parser was mainly
written by Dan Klein, with support code and linguistic grammar development by
Christopher Manning. Extensive additional work (internationalization and
language-specific modeling, flexible input/output, grammar compaction, lattice
parsing, _k_ -best parsing, typed dependencies output, user support, etc.) has
been done by Roger Levy, Christopher Manning, Teg Grenager, Galen Andrew,
Marie-Catherine de Marneffe, Bill MacCartney, Anna Rafferty, Spence Green,
Huihsin Tseng, Pi-Chuan Chang, Wolfgang Maier, and Jenny Finkel.

The lexicalized probabilistic parser implements a factored product model, with
separate PCFG phrase structure and lexical dependency experts, whose
preferences are combined by efficient exact inference, using an A* algorithm.
Or the software can be used simply as an accurate unlexicalized stochastic
context-free grammar parser. Either of these yields a good performance
statistical parsing system. A GUI is provided for viewing the phrase structure
tree output of the parser.

As well as providing an **English** parser, the parser can be and has been
adapted to work with other languages. A **Chinese** parser based on the
Chinese Treebank, a **German** parser based on the Negra corpus and **Arabic**
parsers based on the Penn Arabic Treebank are also included. The parser has
also been used for other languages, such as Italian, Bulgarian, and
Portuguese.

The parser provides [Universal Dependencies (v1) and Stanford
Dependencies](https://nlp.stanford.edu/software/stanford-dependencies.html) output as well as phrase structure
trees. Typed dependencies are otherwise known **grammatical relations**. This
style of output is available only for English and Chinese. For more details,
please refer to the [Stanford Dependencies webpage](stanford-
dependencies.html) and the [Universal Dependencies v1
documentation](http://universaldependencies.org/docsv1/). (See also [the
current Universal Dependencies
documentation](http://universaldependencies.github.com/docs/), but we are yet
to update to it.).

#### Shift-reduce constituency parser

As of version 3.4 in 2014, the parser includes the code necessary to run a
[shift reduce parser](tools_srparser.md), a much faster constituent parser with
competitive accuracy. Models for this parser are linked below.

#### Neural-network dependency parser

In version 3.5.0 (October 2014) we released a [high-performance dependency
parser](pytorch-depparse.md) powered by a neural network. The parser outputs typed
dependency parses for English and Chinese. The models for this parser are
included in the general Stanford Parser models package.

#### Dependency scoring

The package includes a tool for scoring of generic dependency parses, in a
class `edu.stanford.nlp.trees.DependencyScoring`. This tool measures scores
for dependency trees, doing F1 and labeled attachment scoring. The included
usage message gives a detailed description of how to use the tool.

### Usage notes

**The current version of the parser requires Java 8 or later.** (You can also
download an old version of the parser, version 1.4, which runs under JDK 1.4,
version 2.0 which runs under JDK 1.5, version 3.4.1 which runs under JDK 1.6,
but those distributions are no longer supported.) The parser also requires a
reasonable amount of memory (at least 100MB to run as a PCFG parser on
sentences up to 40 words in length; typically around 500MB of memory to be
able to parse similarly long typical-of-newswire sentences using the factored
model).

The parser is available for download, **licensed under the[GNU General Public
License](http://www.gnu.org/licenses/gpl-2.0.html)** (v2 or later). Source is
included. The package includes components for command-line invocation, a Java
parsing GUI, and a Java API.

The download is a 261 MB zipped file (mainly consisting of included grammar
data files). If you unpack the zip file, you should have everything needed.
Simple scripts are included to invoke the parser on a Unix or Windows system.
For another system, you merely need to similarly configure the classpath.

### Licensing

The parser code is dual licensed (in a similar manner to MySQL, etc.). Open
source licensing is under the _full_ GPL, which allows many free uses. For
distributors of [proprietary software](http://www.gnu.org/licenses/gpl-
faq.html#GPLInProprietarySystem), [commercial
licensing](http://otlportal.stanford.edu/techfinder/technology/ID=24472) is
available. (Fine print: The traditional (dynamic programmed) Stanford Parser
does part-of-speech tagging as it works, but the newer constituency and neural
network dependency shift-reduce parsers require pre-tagged input. For
convenience, we include the part-of-speech tagger code, but not models with
the parser download. However, if you want to use these parsers under a
commercial license, then you need a license to both the Stanford Parser and
the Stanford POS tagger. Or you can get the whole bundle of Stanford CoreNLP.)
If you don't need a commercial license, but would like to support maintenance
of these tools, we welcome gift funding: use [this
form](http://giving.stanford.edu/) and write "Stanford NLP
Group open source software" in the Special Instructions.

### Citing the Stanford Parser

The main technical ideas behind how these parsers work appear in these papers.
Feel free to cite one or more of the following papers or people depending on
what you are using. Since the parser is regularly updated, we appreciate it if
papers with numerical results reflecting parser performance mention the
version of the parser being used!

> _For the neural-network dependency parser:_  
>  Danqi Chen and Christopher D Manning. 2014. [A Fast and Accurate Dependency
> Parser using Neural
> Networks](http://cs.stanford.edu/~danqi/papers/emnlp2014.pdf). _Proceedings
> of EMNLP 2014_

> _For the Compositional Vector Grammar parser (starting at version 3.2):_  
>  Richard Socher, John Bauer, Christopher D. Manning and Andrew Y. Ng. 2013.
> [Parsing With Compositional Vector
> Grammars](http://nlp.stanford.edu/pubs/SocherBauerManningNg_ACL2013.pdf).
> _Proceedings of ACL 2013_

> _For the Shift-Reduce Constituency parser (starting at version 3.2):_  
>  This parser was written by John Bauer. You can thank him and [cite the web
> page describing it](tools_srparser.md)
> You can also cite the original research papers of others mentioned on that page.

> _For the PCFG parser (which also does POS tagging):_  
>  Dan Klein and Christopher D. Manning. 2003. [Accurate Unlexicalized
> Parsing](http://nlp.stanford.edu/~manning/papers/unlexicalized-parsing.pdf).
> _Proceedings of the 41st Meeting of the Association for Computational
> Linguistics_ , pp. 423-430.

> _For the factored parser (which also does POS tagging):_  
>  Dan Klein and Christopher D. Manning. 2003. [Fast Exact Inference with a
> Factored Model for Natural Language
> Parsing](http://nlp.stanford.edu/~manning/papers/lex-parser.pdf). In
> _Advances in Neural Information Processing Systems 15 (NIPS 2002)_ ,
> Cambridge, MA: MIT Press, pp. 3-10.

> _For the Universal Dependencies representation:_  
>  Joakim Nivre, Marie-Catherine de Marneffe, Filip Ginter, Yoav Goldberg, Jan
> Hajič, Christopher D. Manning, Ryan McDonald, Slav Petrov, Sampo Pyysalo,
> Natalia Silveira, Reut Tsarfaty, and Daniel Zeman. 2016. [Universal
> Dependencies v1: A Multilingual Treebank
> Collection](http://nlp.stanford.edu/pubs/nivre2016ud.pdf). In _LREC 2016_.

> _For the English Universal Dependencies converter and the enhanced English
> Universal Dependencies representation:_  
>  Sebastian Schuster and Christopher D. Manning. 2016. [Enhanced English
> Universal Dependencies: An Improved Representation for Natural Language
> Understanding Tasks.](http://nlp.stanford.edu/~sebschu/pubs/schuster-
> manning-lrec2016.pdf) In _LREC 2016_.

> _For the (English) Stanford Dependencies representation:_  
>  Marie-Catherine de Marneffe, Bill MacCartney and Christopher D. Manning.
> 2006. [Generating Typed Dependency Parses from Phrase Structure
> Parses](http://nlp.stanford.edu/pubs/LREC06_dependencies.pdf). In _LREC
> 2006_.

> _For the German parser:_  
>  Anna Rafferty and Christopher D. Manning. 2008. [Parsing Three German
> Treebanks: Lexicalized and Unlexicalized Baselines](/pubs/german-acl08.pdf).
> In _ACL Workshop on Parsing German_.

> _For the Chinese Parser:_  
>  Roger Levy and Christopher D. Manning. 2003\. [Is it harder to parse
> Chinese, or the Chinese Treebank?](http://nlp.stanford.edu/pubs/acl2003-chinese.pdf) _ACL 2003_ ,
> pp. 439-446.

> _For the Chinese Stanford Dependencies:_  
>  Pi-Chuan Chang, Huihsin Tseng, Dan Jurafsky, and Christopher D. Manning.
> 2009\. [Discriminative Reordering with Chinese Grammatical Relations
> Features](http://nlp.stanford.edu/pubs/ssst09-chang.pdf). In _Proceedings of the Third Workshop on
> Syntax and Structure in Statistical Translation_.

> _For the Arabic parser:_  
>  Spence Green and Christopher D. Manning. 2010\. [Better Arabic Parsing:
> Baselines, Evaluations, and Analysis](http://nlp.stanford.edu/pubs/coling2010-arabic.pdf). In
> _COLING 2010_.

> _For the French parser:_  
>  Spence Green, Marie-Catherine de Marneffe, John Bauer, and Christopher D.
> Manning. 2010\. [Multiword Expression Identification with Tree Substitution
> Grammars: A Parsing _tour de force_ with
> French.](http://nlp.stanford.edu/pubs/green+demarneffe+bauer+manning.emnlp11.pdf). In _EMNLP 2011_.

> _For the Spanish parser:_  
>  Most of the work on Spanish was by Jon Gauthier. There is no published
> paper, but you can thank him and/or cite this webpage:
> <https://nlp.stanford.edu/software/spanish-faq.html>

### Questions about the parser?

  1. If you're new to parsing, you can start by running the GUI to try out the parser. Scripts are included for linux (lexparser-gui.sh) and Windows (lexparser-gui.bat). 
  2. Take a look at the Javadoc `lexparser` package documentation and `LexicalizedParser` class documentation. (Point your web browser at the `index.html` file in the included `javadoc` directory and navigate to those items.)
  3. Look at the [parser FAQ](tools_parser_faq.md) for answers to common questions.
  4. If none of that helps, please see [our email guidelines](email.html) for instructions on how to reach us for further assistance.

  

### Download

[The standard CoreNLP download](index.md) includes the code for the lexicalized parser and all of its siblings described above.

This standard download includes models for Arabic, Chinese, English, French,
German, and Spanish. There are additional models we do not release with the
standalone parser, including shift-reduce models, that can be found in the
models jars for each language.

Older versions are linked below.

### Extensions: Packages by others using the parser

**Java**

  * [tydevi](http://tydevi.sourceforge.net) Typed Dependency Viewer that makes a picture of the Stanford Dependencies analysis of a sentence. By Bernard Bou. 
  * [ DependenSee](http://chaoticity.com/dependensee-a-dependency-parse-visualisation-tool/) A Dependency Parse Visualisation Tool that makes pictures of Stanford Dependency output. By Awais Athar. ([GitHub](https://github.com/awaisathar/dependensee)) 
  * [GATE plug-in](http://gate.ac.uk/sale/tao/splitch17.html#sec:parsers:stanford). By the GATE Team (esp. Adam Funk). 
  * [GrammarScope](http://grammarscope.sourceforge.net/) grammatical relation browser. GUI, especially focusing on grammatical relations (typed dependencies), including an editor. By Bernard Bou. 

**PHP**

  * [PHP-Stanford-NLP](https://github.com/agentile/PHP-Stanford-NLP). Supports POS Tagger, NER, Parser. By Anthony Gentile (agentile). 

**Python/Jython**

  * [Python interface](http://projects.csail.mit.edu/spatial/Stanford_Parser) built using JPype by Stefanie Tellex. 
  * [Jython interface.](https://github.com/vpekar/stanford-parser-in-jython) by Viktor Pekar. 

**Ruby**

  * [Ruby wrapper to the Stanford Natural Language Parser](http://stanfordparser.rubyforge.org/). By Bill McNeill. An extended and better packaged version of this by John Wilkinson is [available at github](http://github.com/jcwilk/stanfordparser). 

**.NET / F# / C#**

  * Sergey Tihon has [ported the Stanford Parser to F# (or any .NET language, including C#)](https://github.com/sergey-tihon/fsharp-stanford-nlp-samples/), using IKVM. See [his blog post](http://sergeytihon.wordpress.com/2013/02/05/nlp-stanford-parser-with-f-net/), [his Github site](https://github.com/sergey-tihon/fsharp-stanford-nlp-samples), or [the listing on NuGet](https://nuget.org/packages/Stanford.NLP.Parser/). 

**OS X**

  * If you use Homebrew, you can install the Stanford Parser with: `brew install stanford-parser`

  

### Release history

  
| Version | Date | Description | Models |
|---|---|---|---|
| <a href="https://nlp.stanford.edu/software/stanford-parser-4.2.0.zip">4.2.0</a> | 2020-11-17 | Retrain English models with treebank fixes | <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-arabic.jar">arabic  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-chinese.jar">chinese  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-english.jar">english  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-french.jar">french  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-german.jar">german  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.2.0-models-spanish.jar">spanish</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-4.0.0.zip">4.0.0</a> | 2020-05-22 | Model tokenization updated to UDv2.0 | <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-arabic.jar">arabic  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-chinese.jar">chinese  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-english.jar">english  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-french.jar">french  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-german.jar">german  </a> <a href="https://nlp.stanford.edu/software/stanford-corenlp-4.0.0-models-spanish.jar">spanish</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2018-10-17.zip">3.9.2</a> | 2018-10-17 | Updated for compatibility | <a href="https://nlp.stanford.edu/software/stanford-arabic-corenlp-2018-10-05-models.jar">arabic  </a> <a href="https://nlp.stanford.edu/software/stanford-chinese-corenlp-2018-10-05-models.jar">chinese  </a> <a href="https://nlp.stanford.edu/software/stanford-english-corenlp-2018-10-05-models.jar">english  </a> <a href="https://nlp.stanford.edu/software/stanford-french-corenlp-2018-10-05-models.jar">french  </a> <a href="https://nlp.stanford.edu/software/stanford-german-corenlp-2018-10-05-models.jar">german  </a> <a href="https://nlp.stanford.edu/software/stanford-spanish-corenlp-2018-10-05-models.jar">spanish</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2018-02-27.zip">3.9.1</a> | 2018-02-27 | new French and Spanish UD models, misc. UD enhancements, bug fixes | <a href="https://nlp.stanford.edu/software/stanford-arabic-corenlp-2018-02-27-models.jar">arabic  </a> <a href="https://nlp.stanford.edu/software/stanford-chinese-corenlp-2018-02-27-models.jar">chinese  </a> <a href="https://nlp.stanford.edu/software/stanford-english-corenlp-2018-02-27-models.jar">english  </a> <a href="https://nlp.stanford.edu/software/stanford-french-corenlp-2018-02-27-models.jar">french  </a> <a href="https://nlp.stanford.edu/software/stanford-german-corenlp-2018-02-27-models.jar">german  </a> <a href="https://nlp.stanford.edu/software/stanford-spanish-corenlp-2018-02-27-models.jar">spanish</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2017-06-09.zip">3.8.0</a> | 2017-06-09 | Updated for compatibility | <a href="https://nlp.stanford.edu/software/stanford-arabic-corenlp-2017-06-09-models.jar">arabic  </a> <a href="https://nlp.stanford.edu/software/stanford-chinese-corenlp-2017-06-09-models.jar">chinese  </a> <a href="https://nlp.stanford.edu/software/stanford-english-corenlp-2017-06-09-models.jar">english  </a> <a href="https://nlp.stanford.edu/software/stanford-french-corenlp-2017-06-09-models.jar">french  </a> <a href="https://nlp.stanford.edu/software/stanford-german-corenlp-2017-06-09-models.jar">german  </a> <a href="https://nlp.stanford.edu/software/stanford-spanish-corenlp-2017-06-09-models.jar">spanish</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2016-10-31.zip">3.7.0</a> | 2016-10-31 | new UD models | <a href="https://nlp.stanford.edu/software/stanford-arabic-corenlp-2016-10-31-models.jar">arabic  </a> <a href="https://nlp.stanford.edu/software/stanford-chinese-corenlp-2016-10-31-models.jar">chinese  </a> <a href="https://nlp.stanford.edu/software/stanford-english-corenlp-2016-10-31-models.jar">english  </a> <a href="https://nlp.stanford.edu/software/stanford-french-corenlp-2016-10-31-models.jar">french  </a> <a href="https://nlp.stanford.edu/software/stanford-german-corenlp-2016-10-31-models.jar">german  </a> <a href="https://nlp.stanford.edu/software/stanford-spanish-corenlp-2016-10-31-models.jar">spanish</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2015-12-09.zip">3.6.0</a> | 2015-12-09 | Updated for compatibility | <a href="https://nlp.stanford.edu/software/stanford-chinese-corenlp-2016-01-19-models.jar">chinese  </a> <a href="https://nlp.stanford.edu/software/stanford-english-corenlp-2016-01-10-models.jar">english  </a> <a href="https://nlp.stanford.edu/software/stanford-french-corenlp-2016-01-14-models.jar">french  </a> <a href="https://nlp.stanford.edu/software/stanford-german-2016-01-19-models.jar">german  </a> <a href="https://nlp.stanford.edu/software/stanford-spanish-corenlp-2015-10-14-models.jar">spanish</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2015-04-20.zip">3.5.2</a> | 2015-04-20 | Switch to universal dependencies | <a href="https://nlp.stanford.edu/software/stanford-srparser-2014-10-23-models.jar">shift reduce parser models</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2015-01-29.zip">3.5.1</a> | 2015-01-29 | Dependency parser fixes and model improvements | <a href="https://nlp.stanford.edu/software/stanford-srparser-2014-10-23-models.jar">shift reduce parser models</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2014-10-31.zip">3.5.0</a> | 2014-10-31 | <a href="/software/nndep.html">neural-network dependency parser</a> | <a href="https://nlp.stanford.edu/software/stanford-srparser-2014-10-23-models.jar">shift reduce parser models</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2014-08-27.zip">3.4.1</a> | 2014-08-27 | Add Spanish models | <a href="https://nlp.stanford.edu/software/stanford-srparser-2014-08-28-models.jar">shift reduce parser models</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2014-06-16.zip">3.4</a> | 2014-06-16 | Shift-reduce parser, dependency improvements, French parser uses CC tagset | <a href="https://nlp.stanford.edu/software/stanford-srparser-2014-06-16-models.jar">shift reduce parser models</a> |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2014-01-04.zip">3.3.1</a> | 2014-01-04 | English dependency "infmod" and "partmod" combined into "vmod", other minor dependency improvements |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2013-11-12.zip">3.3.0</a> | 2013-11-12 | English dependency "attr" removed, other dependency improvements, imperative training data added |
| <a href="https://nlp.stanford.edu/software/stanford-parser-full-2013-06-20.zip">3.2.0</a> | 2013-06-20 | New CVG based English model with higher accuracy |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2013-04-05.zip">2.0.5</a> | 2013-04-05 | Dependency improvements, -nthreads option, ctb7 model |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2012-11-12.zip">2.0.4</a> | 2012-11-12 | Improved dependency code extraction efficiency, other dependency changes |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2012-07-09.tgz">2.0.3</a> | 2012-07-09 | Minor bug fixes |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2012-05-22.tgz">2.0.2</a> | 2012-05-22 | Some models now support training with extra tagged, non-tree data |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2012-03-09.tgz">2.0.1</a> | 2012-03-09 | Caseless English model included, bugfix for enforced tags |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2012-02-03.tgz">2.0</a> | 2012-02-03 | Threadsafe! |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2011-09-14.tgz">1.6.9</a> | 2011-09-14 | Improved recognition of imperatives, dependencies now explicitely include a root, parser knows osprey is a noun |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2011-08-04.tgz">1.6.8</a> | 2011-06-19 | New French model, improved foreign language models, bug fixes |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2011-05-18.tgz">1.6.7</a> | 2011-05-18 | Minor bug fixes. |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2011-04-20.tgz">1.6.6</a> | 2011-04-20 | Internal code and API changes (ArrayLists rather than Sentence; use of CoreLabel objects) to match tagger and CoreNLP. |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2010-11-30.tgz">1.6.5</a> | 2010-11-30 | Further improvements to English Stanford Dependencies and other minor changes |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2010-08-20.tgz">1.6.4</a> | 2010-08-20 | More minor bug fixes and improvements to English Stanford Dependencies and question parsing |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2010-07-09.tgz">1.6.3</a> | 2010-07-09 | Improvements to English Stanford Dependencies and question parsing, minor bug fixes |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2010-02-26.tgz">1.6.2</a> | 2010-02-26 | Improvements to Arabic parser models, and to English and Chinese Stanford Dependencies |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2008-10-26.tgz">1.6.1</a> | 2008-10-26 | Slightly improved Arabic and German parsing, and Stanford Dependencies |
| <a href="https://nlp.stanford.edu/software/stanford-parser-2007-08-19.tar.gz">1.6</a> | 2007-08-19 | Added Arabic, k-best PCCFG parsing; improved English grammatical relations |
| <a href="https://nlp.stanford.edu/software/StanfordParser-2006-06-11.tar.gz">1.5.1</a> | 2006-06-11 | Improved English and Chinese grammatical relations; fixed UTF-8 handling |
| <a href="https://nlp.stanford.edu/software/StanfordParser-2005-07-21.tar.gz">1.5</a> | 2005-07-21 | Added grammatical relations output; fixed bugs introduced in 1.4 |
| <a href="https://nlp.stanford.edu/software/StanfordParser-1.4.tar.gz">1.4</a> | 2004-03-24 | Made PCFG faster again (by FSA minimization); added German support |
| <a href="https://nlp.stanford.edu/software/StanfordParser-1.3.tar.gz">1.3</a> | 2003-09-06 | Made parser over twice as fast; added tokenization options |
| 1.2 | 2003-07-20 | Halved PCFG memory usage; added support for Chinese |
| 1.1 | 2003-03-25 | Improved parsing speed; included GUI, improved PCFG grammar |
| 1.0 | 2002-12-05 | Initial release |

  

### Sample input and output

The parser can read various forms of plain text input and can output various
analysis formats, including part-of-speech tagged text, phrase structure
trees, and a grammatical relations (typed dependency) format. For example,
consider the text:

> `The strongest rain ever recorded in India shut down the financial hub of
> Mumbai, snapped communication lines, closed airports and forced thousands of
> people to sleep in their offices or walk home during the night, officials
> said today.`

The following output shows part-of-speech tagged text, then a context-free
phrase structure grammar representation, and finally a typed dependency
representation. All of these are different views of the output of the parser.

>
>     The/DT strongest/JJS rain/NN ever/RB recorded/VBN in/IN India/NNP
>     shut/VBD down/RP the/DT financial/JJ hub/NN of/IN Mumbai/NNP ,/,
>     snapped/VBD communication/NN lines/NNS ,/, closed/VBD airports/NNS
>     and/CC forced/VBD thousands/NNS of/IN people/NNS to/TO sleep/VB in/IN
>     their/PRP$ offices/NNS or/CC walk/VB home/NN during/IN the/DT night/NN
>     ,/, officials/NNS said/VBD today/NN ./.
>  
>     (ROOT
>       (S
>         (S
>           (NP
>             (NP (DT The) (JJS strongest) (NN rain))
>             (VP
>               (ADVP (RB ever))
>               (VBN recorded)
>               (PP (IN in)
>                 (NP (NNP India)))))
>           (VP
>             (VP (VBD shut)
>               (PRT (RP down))
>               (NP
>                 (NP (DT the) (JJ financial) (NN hub))
>                 (PP (IN of)
>                   (NP (NNP Mumbai)))))
>             (, ,)
>             (VP (VBD snapped)
>               (NP (NN communication) (NNS lines)))
>             (, ,)
>             (VP (VBD closed)
>               (NP (NNS airports)))
>             (CC and)
>             (VP (VBD forced)
>               (NP
>                 (NP (NNS thousands))
>                 (PP (IN of)
>                   (NP (NNS people))))
>               (S
>                 (VP (TO to)
>                   (VP
>                     (VP (VB sleep)
>                       (PP (IN in)
>                         (NP (PRP$ their) (NNS offices))))
>                     (CC or)
>                     (VP (VB walk)
>                       (NP (NN home))
>                       (PP (IN during)
>                         (NP (DT the) (NN night))))))))))
>         (, ,)
>         (NP (NNS officials))
>         (VP (VBD said)
>           (NP-TMP (NN today)))
>         (. .)))
>  
>     det(rain-3, The-1)
>     amod(rain-3, strongest-2)
>     nsubj(shut-8, rain-3)
>     nsubj(snapped-16, rain-3)
>     nsubj(closed-20, rain-3)
>     nsubj(forced-23, rain-3)
>     advmod(recorded-5, ever-4)
>     partmod(rain-3, recorded-5)
>     prep_in(recorded-5, India-7)
>     ccomp(said-40, shut-8)
>     prt(shut-8, down-9)
>     det(hub-12, the-10)
>     amod(hub-12, financial-11)
>     dobj(shut-8, hub-12)
>     prep_of(hub-12, Mumbai-14)
>     conj_and(shut-8, snapped-16)
>     ccomp(said-40, snapped-16)
>     nn(lines-18, communication-17)
>     dobj(snapped-16, lines-18)
>     conj_and(shut-8, closed-20)
>     ccomp(said-40, closed-20)
>     dobj(closed-20, airports-21)
>     conj_and(shut-8, forced-23)
>     ccomp(said-40, forced-23)
>     dobj(forced-23, thousands-24)
>     prep_of(thousands-24, people-26)
>     aux(sleep-28, to-27)
>     xcomp(forced-23, sleep-28)
>     poss(offices-31, their-30)
>     prep_in(sleep-28, offices-31)
>     xcomp(forced-23, walk-33)
>     conj_or(sleep-28, walk-33)
>     dobj(walk-33, home-34)
>     det(night-37, the-36)
>     prep_during(walk-33, night-37)
>     nsubj(said-40, officials-39)
>     root(ROOT-0, said-40)
>     tmod(said-40, today-41)
>  

This output was generated with the command:

> ` java -mx200m edu.stanford.nlp.parser.lexparser.LexicalizedParser
> -retainTMPSubcategories -outputFormat "wordsAndTags,penn,typedDependencies"
> englishPCFG.ser.gz mumbai.txt `