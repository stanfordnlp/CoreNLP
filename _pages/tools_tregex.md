---
layout: page
title: Tregex, Tsurgeon, Semgrex, and Ssurgeon
keywords: tregex, tsurgeon, semgrex, ssurgeon
permalink: '/tools_tregex.html'
nav_order: 6
toc: true
parent: Additional Tools
---

### About

**Tregex** is a utility for matching patterns in trees, based on tree
relationships and regular expression matches on nodes (the name is short for
"tree regular expressions"). Tregex comes with **Tsurgeon** , a tree
transformation language. Also included from version 2.0 on is a similar
package which operates on dependency graphs (class `SemanticGraph`, called
**semgrex**.  Recent versions of CoreNLP include a dependency graph editor
based on Semgrex called **Ssurgeon**.

**Tregex:** The best introduction to Tregex is the brief powerpoint tutorial
for [Tregex](https://nlp.stanford.edu/software/tregex/The_Wonderful_World_of_Tregex.ppt) by Galen Andrew. The
best way to learn to use Tregex is by working with the GUI (`TregexGUI`). It
has help screens which summarize the syntax of Tregex. You can find brief
documentation of Tregex's pattern language on [the TregexPattern javadoc
page](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/trees/tregex/TregexPattern.html),
and, of course, you should also be very familiar with [Java regular expression
syntax](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html).
Tregex contains essentially the same functionality as
[TGrep2](http://tedlab.mit.edu/~dr/Tgrep2/) (which had a superset of the
functionality of the original tgrep), plus several extremely useful relations
for natural language trees, for example "A is the lexical head of B", and "A
and B share a (hand-specified) variable substring" (useful for finding nodes
coindexed with each other). Because it does not create preprocessed indexed
corpus files, it is however somewhat slower than TGrep2 when searching over
large treebanks, but gains from being able to be run on any trees without
requiring index construction. As a Java application, it is platform
independent, and can be used programmatically in Java software. There is also
both a graphical interface (also platform independent) and a command line
interface through the `TregexPattern` main method. To launch the graphical
interface double click the stanford-tregex.jar file.

**Tsurgeon:** A good introduction is [the powerpoint slides for
Tsurgeon](https://nlp.stanford.edu/software/tregex/Tsurgeon2.ppt) by Marie-Catherine de Marneffe. Tsurgeon can
be run from the command line and is also incorporated into the TregexGUI
graphical interface. Its syntax is presented on the [Tsurgeon javadoc
page](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/trees/tregex/tsurgeon/Tsurgeon.html).

**Semgrex:** An included set of [powerpoint slides](https://nlp.stanford.edu/software/tregex/Semgrex.ppt) and the [javadoc for `SemgrexPattern`](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/semgraph/semgrex/SemgrexPattern.html)
provide an overview of this package.

**Ssurgeon:** The [Javadoc](https://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/semgraph/semgrex/ssurgeon/Ssurgeon.html) page describes the basic opterations available for Ssurgeon.

Tregex was written by Galen Andrew and Roger Levy. Tsurgeon was written by
Roger Levy. The graphical interface for both was written by Anna Rafferty. A
lot of bug fixing and various extensions to both were done by John Bauer.
Semgrex was written by Chloé Kiddon and John Bauer. Ssurgeon was written by Eric Yeh and John Bauer.  These programs also rely
on classes developed by others as part of the Stanford JavaNLP project.

There is a paper describing Tregex and Tsurgeon. You're encouraged to cite it
if you use Tregex or Tsurgeon.

> Roger Levy and Galen Andrew. 2006. [Tregex and Tsurgeon: tools for querying
> and manipulating tree data structures](/pubs/levy_andrew_lrec2006.pdf). _5th
> International Conference on Language Resources and Evaluation (LREC 2006)_.

Semgrex is very briefly described in this paper:

> Nathanael Chambers, Daniel Cer, Trond Grenager, David Hall, Chloé Kiddon
> Bill MacCartney, Marie-Catherine de Marneffe, Daniel Ramage Eric Yeh, and
> Christopher D. Manning. 2007. [Learning Alignments and Leveraging Natural
> Logic](http://www.aclweb.org/anthology/W07-1427). _Proceedings of the
> Workshop on Textual Entailment and Paraphrasing_ , pages 165–170.

We published a more complete description of Semgrex and Ssurgeon at GURT 2023:

> John Bauer, Chloé Kiddon, Eric Yeh, Alex Shan, and Christopher
> D. Manning. 2023.  [Semgrex and Ssurgeon, Searching and Manipulating
> Dependency Graphs](https://aclanthology.org/2023.tlt-1.7/)
> _Proceedings of the 21st International Workshop on Treebanks and
> Linguistic Theories (TLT, GURT/SyntaxFest 2023)_


Tregex, Tsurgeon, Semgrex, and Ssurgoen are **licensed under the [GNU General Public License](http://www.gnu.org/licenses/gpl-2.0.html)** (v2 or later). Note that
this is the _full_ GPL, which allows many free uses. For distributors of
[proprietary software](http://www.gnu.org/licenses/gpl-
faq.html#GPLInProprietarySystem), [commercial
licensing](http://otlportal.stanford.edu/techfinder/technology/ID=24472) is
available. Source is included. The package includes components for command-
line invocation and a Java API.

  

### Questions

There is a [tregex FAQ list](tools_tregex_faq.md) (with answers!). Please send any
other questions or feedback, or extensions and bugfixes to [`parser-
user@lists.stanford.edu`](mailto:parser-user@lists.stanford.edu).  

### Mailing Lists

We have 3 mailing lists for the Tregex/Tsurgeon, all of which are shared with
the Stanford Parser. Each is at `@lists.stanford.edu`:

  1. `parser-user` This is the best list to post to in order to ask questions, make announcements, or for discussion among Tregex/Tsurgeon users. Join the list via [this webpage](https://mailman.stanford.edu/mailman/listinfo/parser-user) or by emailing `parser-user-join@lists.stanford.edu`. (Leave the subject and message body empty.) You can also [look at the list archives](https://mailman.stanford.edu/pipermail/parser-user/). 
  2. `parser-announce` This list will be used only to announce new Tregex/Tsurgeon versions. So it will be very low volume (expect 1-3 message a year). Join the list via [this webpage](https://mailman.stanford.edu/mailman/listinfo/parser-announce) or by emailing `parser-announce-join@lists.stanford.edu`. (Leave the subject and message body empty.) 
  3. `parser-support` This list goes only to the Tregex/Tsurgeon maintainers. It's a good address for licensing questions, etc. **For general use and support questions, you're better off joining and using`parser-user`.** You cannot join `parser-support`, but you can mail questions to `parser-support@lists.stanford.edu`. 

  
### Extensions: Packages by others using Tregex/Semgrex

  * **Javascript (node.js):** [semgrex](https://www.npmjs.com/package/semgrex): NodeJs wrapper for Stanford NLP Semgrex. [[GitHub](https://github.com/koorchik/node-semgrex)] 


### Download

Tregex, Tsurgeon, Semgrex, and Ssurgeon are all included in the latest CoreNLP releases.

### Standalone Package

Older versions were built as standalone packages, described here.

#### Contents

The download is a 9 Mb zip file. It contains:

  1. README-tregex.txt -- Basic information about the distribution, including a "quickstart" guide. 
  2. README-tsurgeon.txt -- information about Tsurgeon. 
  3. README-gui.txt -- information about using the graphical interface 
  4. LICENSE -- Tregex is licensed under the Gnu General Public License. 
  5. stanford-tregex.jar -- This is a JAR file containing all the Stanford classes necessary to run tregex. 
  6. src directory -- a directory with the source files for Tregex and Tsurgeon 
  7. lib directory -- library files required for recompiling the distribution (with Mac OS X customization; see `lib/ABOUT-AppleJavaExtensions.txt` for removing this dependency) 
  8. build.xml, Makefile -- files for recompiling (with ant or make) the distribution 
  9. javadoc -- Javadocs for the distribution 
  10. tregex.sh, tsurgeon.sh -- sample scripts for running Tregex and Tsurgeon from the command line 
  11. run-tregex-gui.command, run-tregex-gui.bat -- shell script for running the graphical interface for Tregex with more memory for searching larger treebanks; can be double-clicked to open on a Mac or PC, respectively 
  12. examples directory -- example files for Tregex and Tsurgeon 

**[Download Tregex version 4.2.0](stanford-tregex-4.2.0.zip)** (source and
executables for all platforms)  
  
[Download Tregex version 3.4 Mac OS X disk image](stanford-tregex-2014-07-23.dmg) (GUI packaged as Mac application; Java 1.7 runtime
included)

### Release history

| Version | Date | Description |
|---|---|---|
| <a href="http://nlp.stanford.edu/software/stanford-tregex-4.2.0.zip">4.2.0</a> | 2020&#8209;11&#8209;17 | Update for compatibility |
| <a href="stanford-tregex-4.0.0.zip">4.0.0</a> | 2020&#8209;04&#8209;19 | Update for compatibility |
| <a href="stanford-tregex-2018-10-16.zip">3.9.2</a> | 2018&#8209;10&#8209;16 | Update for compatibility |
| <a href="stanford-tregex-2018-02-27.zip">3.9.1</a> | 2018&#8209;02&#8209;27 | Update for compatibility |
| <a href="stanford-tregex-2017-06-09.zip">3.8.0</a> | 2017&#8209;06&#8209;09 | Update for compatibility |
| <a href="stanford-tregex-2016-10-31.zip">3.7.0</a> | 2016&#8209;10&#8209;31 | Update for compatibility |
| <a href="stanford-tregex-2015-12-09.zip">3.6.0</a> | 2015&#8209;12&#8209;09 | Updated for compatibility |
| <a href="stanford-tregex-2015-04-20.zip">3.5.2</a> | 2015&#8209;04&#8209;20 | Update for compatibility |
| <a href="stanford-tregex-2015-01-29.zip">3.5.1</a> | 2015&#8209;01&#8209;29 | Update for compatibility |
| <a href="stanford-tregex-2014-10-26.zip">3.5.0</a> | 2014&#8209;10&#8209;26 | Upgrade to Java 8 |
| <a href="stanford-tregex-2014-08-27.zip">3.4.1</a> | 2014&#8209;08&#8209;27 | Fix a thread safety issue in tsurgeon. Last version to support Java 6 and Java 7. |
| <a href="stanford-tregex-2014-06-16.zip">3.4</a> | 2014&#8209;06&#8209;16 | Added a new tregex pattern, exact subtree, and improved efficiency for certain operations |
| <a href="stanford-tregex-2014-01-04.zip">3.3.1</a> | 2014&#8209;01&#8209;04 | Added a new tsurgeon operation, createSubtree |
| <a href="stanford-tregex-2013-11-12.zip">3.3.0</a> | 2013&#8209;11&#8209;12 | Add an option to get a TregexMatcher from a TregexPattern with a different HeadFinder |
| <a href="stanford-tregex-2013-06-20.zip">3.2.0</a> | 2013&#8209;06&#8209;20 | Fix minor bug in tsurgeon indexing |
| <a href="stanford-tregex-2013-04-04.zip">2.0.6</a> | 2013&#8209;04&#8209;04 | Updated for compatibility with other software releases |
| <a href="stanford-tregex-2012-11-11.zip">2.0.5</a> | 2012&#8209;11&#8209;11 | Minor efficiency improvements |
| <a href="stanford-tregex-2012-07-09.tgz">2.0.4</a> | 2012&#8209;07&#8209;09 | Minor bug fixes |
| <a href="stanford-tregex-2012-05-22.tgz">2.0.3</a> | 2012&#8209;05&#8209;22 | Updated to maintain compatibility with other Stanford software. |
| <a href="stanford-tregex-2012-03-09.tgz">2.0.2</a> | 2012&#8209;03&#8209;09 | Regex matching efficiency improvement |
| <a href="stanford-tregex-2012-01-06.tgz">2.0.1</a> | 2012&#8209;01&#8209;06 | Fix matchesAt, fix category heads. Last version to support Java 5. |
| <a href="stanford-tregex-2011-09-14.tgz">2.0</a> | 2011&#8209;09&#8209;14 | Introduces semgrex, which operates on SemanticGraphs. |
| <a href="stanford-tregex-2011-06-19.tgz">1.4.4</a> | 2011&#8209;06&#8209;19 | Updated to maintain compatibility with other Stanford software. |
| <a href="stanford-tregex-2011-05-15.tgz">1.4.3</a> | 2011&#8209;05&#8209;15 | Updated to maintain compatibility with other Stanford software. |
| <a href="stanford-tregex-2011-04-20.tgz">1.4.2</a> | 2011&#8209;04&#8209;20 | Addition of tree difference display.  Several bugfixes. |
| <a href="stanford-tregex-2010-11-18.tgz">1.4.1</a> | 2010&#8209;11&#8209;18 | Small fixes and improvements (multipattern Tsurgeon scripts, file and line numbers in sentence window, fixed GUI lock-up and tregex immediate domination path matching) |
| <a href="stanford-tregex-2009-08-30.tgz">1.4</a> | 2009&#8209;08&#8209;30 | GUI slider for tree size, allow @ and __ in path constraints, incompatibly generalize Tsurgeon relabel command, bug fix for links and backreferences being used as named node, more memory/space efficient treebank reading |
| <a href="stanford-tregex-2008-05-08.tar.gz">1.3.2</a> | 2008&#8209;05&#8209;06 | Additional features added to the graphical interface, which is now version 1.1: browse trees, better memory handling |
| 1.3.1 | 2007&#8209;11&#8209;20 | Additional features added to the graphical interface: better copy/paste and drag and drop support, capability to save matched sentences as well as matched trees, and can save files in different encodings |
| 1.3 | 2007&#8209;09&#8209;20 | Various bug fixes and improvements; additional Tsurgeon operations; and added a graphical interface |
| 1.2 | 2005&#8209;11&#8209;23 | Bundled in Tsurgeon. |
| 1.1.1 | 2005&#8209;09&#8209;15 | Fixed bugs: 1) in variable groups; 2) in number of reported matches for "<" relation |
| 1.1 | 2005&#8209;07&#8209;19 | Several new relations added; variable substring capability added too. |
| 1.0 | 2005&#8209;02&#8209;17 | Initial release |
