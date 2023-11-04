---
layout: page
title: Tregex, Tsurgeon, and Semgrex
keywords: tregex, tsurgeon, semgrex
permalink: '/tools_tregex.html'
nav_order: 6
toc: false
parent: Additional Tools
---

Tregex, Tsurgeon and Semgrex

### About

**Tregex** is a utility for matching patterns in trees, based on tree
relationships and regular expression matches on nodes (the name is short for
"tree regular expressions"). Tregex comes with **Tsurgeon** , a tree
transformation language. Also included from version 2.0 on is a similar
package which operates on dependency graphs (class `SemanticGraph`, called
**semgrex**.

**Tregex:** The best introduction to Tregex is the brief powerpoint tutorial
for Tregex by Galen Andrew. The best way to learn to use Tregex is by working
with the GUI (`TregexGUI`). It has help screens which summarize the syntax of
Tregex. You can find brief documentation of Tregex's pattern language on the
TregexPattern javadoc page, and, of course, you should also be very familiar
with Java regular expression syntax. Tregex contains essentially the same
functionality as TGrep2 (which had a superset of the functionality of the
original tgrep), plus several extremely useful relations for natural language
trees, for example "A is the lexical head of B", and "A and B share a (hand-
specified) variable substring" (useful for finding nodes coindexed with each
other). Because it does not create preprocessed indexed corpus files, it is
however somewhat slower than TGrep2 when searching over large treebanks, but
gains from being able to be run on any trees without requiring index
construction. As a Java application, it is platform independent, and can be
used programmatically in Java software. There is also both a graphical
interface (also platform independent) and a command line interface through the
`TregexPattern` main method. To launch the graphical interface double click
the stanford-tregex.jar file.

**Tsurgeon:** A good introduction is the powerpoint slides for Tsurgeon by
Marie-Catherine de Marneffe. Tsurgeon can be run from the command line and is
also incorporated into the TregexGUI graphical interface. Its syntax is
presented on the Tsurgeon javadoc page.

**Semgrex:** An included set of powerpoint slides and the  javadoc for
`SemgrexPattern` provide an overview of this package.

Tregex was written by Galen Andrew and Roger Levy. Tsurgeon was written by
Roger Levy. The graphical interface for both was written by Anna Rafferty. A
lot of bug fixing and various extensions to both were done by John Bauer.
Semgrex was written by Chloe Kiddon and John Bauer. These programs also rely
on classes developed by others as part of the Stanford JavaNLP project.

There is a paper describing Tregex and Tsurgeon. You're encouraged to cite it
if you use Tregex or Tsurgeon.

> Roger Levy and Galen Andrew. 2006. Tregex and Tsurgeon: tools for querying
> and manipulating tree data structures. _5th International Conference on
> Language Resources and Evaluation (LREC 2006)_.

Semgrex is very briefly described in this paper:

> Nathanael Chambers, Daniel Cer, Trond Grenager, David Hall, Chloe Kiddon
> Bill MacCartney, Marie-Catherine de Marneffe, Daniel Ramage Eric Yeh, and
> Christopher D. Manning. 2007. Learning Alignments and Leveraging Natural
> Logic. _Proceedings of the Workshop on Textual Entailment and Paraphrasing_
> , pages 165–170,

Tregex, Tsurgeon, and Semgrex are **licensed under theGNU General Public
License** (v2 or later). Note that this is the _full_ GPL, which allows many
free uses. For distributors of proprietary software, commercial licensing is
available. Source is included. The package includes components for command-
line invocation and a Java API.

  

### Questions

There is a tregex FAQ list (with answers!). Please send any other questions or
feedback, or extensions and bugfixes to `parser-user@lists.stanford.edu`.  

### Mailing Lists

We have 3 mailing lists for the Tregex/Tsurgeon, all of which are shared with
the Stanford Parser. Each is at `@lists.stanford.edu`:

  1. `parser-user` This is the best list to post to in order to ask questions, make announcements, or for discussion among Tregex/Tsurgeon users. Join the list via this webpage or by emailing `parser-user-join@lists.stanford.edu`. (Leave the subject and message body empty.) You can also look at the list archives. 
  2. `parser-announce` This list will be used only to announce new Tregex/Tsurgeon versions. So it will be very low volume (expect 1-3 message a year). Join the list via this webpage or by emailing `parser-announce-join@lists.stanford.edu`. (Leave the subject and message body empty.) 
  3. `parser-support` This list goes only to the Tregex/Tsurgeon maintainers. It's a good address for licensing questions, etc. **For general use and support questions, you're better off joining and using`parser-user`.** You cannot join `parser-support`, but you can mail questions to `parser-support@lists.stanford.edu`. 

  

### Contents

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

### Download

**Download Tregex version 4.2.0** (source and executables for all platforms)  
  
Download Tregex version 3.4 Mac OS X disk image (GUI packaged as Mac
application; Java 1.7 runtime included)

### Extensions: Packages by others using Tregex/Semgrex

  * **Javascript (node.js):** semgrex: NodeJs wrapper for Stanford NLP Semgrex. [GitHub] 

### Release history

Version 4.2.0 | 2020-11-17| Update for compatibility  
---|---|---  
Version 4.0.0 | 2020-04-19| Update for compatibility  
Version 3.9.2 | 2018-10-16| Update for compatibility  
Version 3.9.1 | 2018-02-27| Update for compatibility  
Version 3.8.0 | 2017-06-09| Update for compatibility  
Version 3.7.0 | 2016-10-31| Update for compatibility  
Version 3.6.0 | 2015-12-09| Updated for compatibility  
Version 3.5.2 | 2015-04-20| Update for compatibility  
Version 3.5.1 | 2015-01-29| Update for compatibility  
Version 3.5.0 | 2014-10-26| Upgrade to Java 8  
Version 3.4.1 | 2014-08-27| Fix a thread safety issue in tsurgeon. _Last
version to support Java 6 and Java 7._  
Version 3.4 | 2014-06-16| Added a new tregex pattern, exact subtree, and
improved efficiency for certain operations  
Version 3.3.1 | 2014-01-04| Added a new tsurgeon operation, createSubtree  
Version 3.3.0 | 2013-11-12| Add an option to get a TregexMatcher from a
TregexPattern with a different HeadFinder  
Version 3.2.0 | 2013-06-20| Fix minor bug in tsurgeon indexing  
Version 2.0.6 | 2013-04-04| Updated for compatibility with other software
releases  
Version 2.0.5 | 2012-11-11| Minor efficiency improvements  
Version 2.0.4 | 2012-07-09| Minor bug fixes  
Version 2.0.3 | 2012-05-22| Updated to maintain compatibility with other
Stanford software.  
Version 2.0.2 | 2012-03-09| Regex matching efficiency improvement  
Version 2.0.1 | 2012-01-06| Fix matchesAt, fix category heads. _Last version
to support Java 5._  
Version 2.0 | 2011-09-14| Introduces semgrex, which operates on
SemanticGraphs.  
Version 1.4.4 | 2011-06-19| Updated to maintain compatibility with other
Stanford software.  
Version 1.4.3 | 2011-05-15| Updated to maintain compatibility with other
Stanford software.  
Version 1.4.2 | 2011-04-20| Addition of tree difference display. Several
bugfixes.  
Version 1.4.1 | 2010-11-18 | Small fixes and improvements (multipattern
Tsurgeon scripts, file and line numbers in sentence window, fixed GUI lock-up
and tregex immediate domination path matching)  
Version 1.4 | 2009-08-30| GUI slider for tree size, allow @ and __ in path
constraints, incompatibly generalize Tsurgeon relabel command, bug fix for
links and backreferences being used as named node, more memory/space efficient
treebank reading  
Version 1.3.2 | 2008-05-06| Additional features added to the graphical
interface, which is now version 1.1: browse trees, better memory handling  
Version 1.3.1| 2007-11-20| Additional features added to the graphical
interface: better copy/paste and drag and drop support, capability to save
matched sentences as well as matched trees, and can save files in different
encodings | Version 1.3| 2007-09-20| Various bug fixes and improvements;
additional Tsurgeon operations; and added a graphical interface | Version 1.2|
2005-11-23 | Bundled in Tsurgeon.  
Version 1.1.1| 2005-09-15 | Fixed bugs: 1) in variable groups; 2) in number of
reported matches for "<" relation  
Version 1.1| 2005-07-19 | Several new relations added; variable substring
capability added too.  
Version 1.0| 2005-02-17 | Initial release  
  
# Software > Tregex, Tsurgeon and Semgrex

About | Questions | Mailing lists | Contents | Download | Extensions | Release
history | FAQ

### About

**Tregex** is a utility for matching patterns in trees, based on tree
relationships and regular expression matches on nodes (the name is short for
"tree regular expressions"). Tregex comes with **Tsurgeon** , a tree
transformation language. Also included from version 2.0 on is a similar
package which operates on dependency graphs (class `SemanticGraph`, called
**semgrex**.

**Tregex:** The best introduction to Tregex is the brief powerpoint tutorial
for Tregex by Galen Andrew. The best way to learn to use Tregex is by working
with the GUI (`TregexGUI`). It has help screens which summarize the syntax of
Tregex. You can find brief documentation of Tregex's pattern language on the
TregexPattern javadoc page, and, of course, you should also be very familiar
with Java regular expression syntax. Tregex contains essentially the same
functionality as TGrep2 (which had a superset of the functionality of the
original tgrep), plus several extremely useful relations for natural language
trees, for example "A is the lexical head of B", and "A and B share a (hand-
specified) variable substring" (useful for finding nodes coindexed with each
other). Because it does not create preprocessed indexed corpus files, it is
however somewhat slower than TGrep2 when searching over large treebanks, but
gains from being able to be run on any trees without requiring index
construction. As a Java application, it is platform independent, and can be
used programmatically in Java software. There is also both a graphical
interface (also platform independent) and a command line interface through the
`TregexPattern` main method. To launch the graphical interface double click
the stanford-tregex.jar file.

**Tsurgeon:** A good introduction is the powerpoint slides for Tsurgeon by
Marie-Catherine de Marneffe. Tsurgeon can be run from the command line and is
also incorporated into the TregexGUI graphical interface. Its syntax is
presented on the Tsurgeon javadoc page.

**Semgrex:** An included set of powerpoint slides and the  javadoc for
`SemgrexPattern` provide an overview of this package.

Tregex was written by Galen Andrew and Roger Levy. Tsurgeon was written by
Roger Levy. The graphical interface for both was written by Anna Rafferty. A
lot of bug fixing and various extensions to both were done by John Bauer.
Semgrex was written by Chloe Kiddon and John Bauer. These programs also rely
on classes developed by others as part of the Stanford JavaNLP project.

There is a paper describing Tregex and Tsurgeon. You're encouraged to cite it
if you use Tregex or Tsurgeon.

> Roger Levy and Galen Andrew. 2006. Tregex and Tsurgeon: tools for querying
> and manipulating tree data structures. _5th International Conference on
> Language Resources and Evaluation (LREC 2006)_.

Semgrex is very briefly described in this paper:

> Nathanael Chambers, Daniel Cer, Trond Grenager, David Hall, Chloe Kiddon
> Bill MacCartney, Marie-Catherine de Marneffe, Daniel Ramage Eric Yeh, and
> Christopher D. Manning. 2007. Learning Alignments and Leveraging Natural
> Logic. _Proceedings of the Workshop on Textual Entailment and Paraphrasing_
> , pages 165–170,

Tregex, Tsurgeon, and Semgrex are **licensed under theGNU General Public
License** (v2 or later). Note that this is the _full_ GPL, which allows many
free uses. For distributors of proprietary software, commercial licensing is
available. Source is included. The package includes components for command-
line invocation and a Java API.

  

### Questions

There is a tregex FAQ list (with answers!). Please send any other questions or
feedback, or extensions and bugfixes to `parser-user@lists.stanford.edu`.  

### Mailing Lists

We have 3 mailing lists for the Tregex/Tsurgeon, all of which are shared with
the Stanford Parser. Each is at `@lists.stanford.edu`:

  1. `parser-user` This is the best list to post to in order to ask questions, make announcements, or for discussion among Tregex/Tsurgeon users. Join the list via this webpage or by emailing `parser-user-join@lists.stanford.edu`. (Leave the subject and message body empty.) You can also look at the list archives. 
  2. `parser-announce` This list will be used only to announce new Tregex/Tsurgeon versions. So it will be very low volume (expect 1-3 message a year). Join the list via this webpage or by emailing `parser-announce-join@lists.stanford.edu`. (Leave the subject and message body empty.) 
  3. `parser-support` This list goes only to the Tregex/Tsurgeon maintainers. It's a good address for licensing questions, etc. **For general use and support questions, you're better off joining and using`parser-user`.** You cannot join `parser-support`, but you can mail questions to `parser-support@lists.stanford.edu`. 

  

### Contents

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

### Download

**Download Tregex version 4.2.0** (source and executables for all platforms)  
  
Download Tregex version 3.4 Mac OS X disk image (GUI packaged as Mac
application; Java 1.7 runtime included)

### Extensions: Packages by others using Tregex/Semgrex

  * **Javascript (node.js):** semgrex: NodeJs wrapper for Stanford NLP Semgrex. [GitHub] 

### Release history

Version 4.2.0 | 2020-11-17| Update for compatibility  
---|---|---  
Version 4.0.0 | 2020-04-19| Update for compatibility  
Version 3.9.2 | 2018-10-16| Update for compatibility  
Version 3.9.1 | 2018-02-27| Update for compatibility  
Version 3.8.0 | 2017-06-09| Update for compatibility  
Version 3.7.0 | 2016-10-31| Update for compatibility  
Version 3.6.0 | 2015-12-09| Updated for compatibility  
Version 3.5.2 | 2015-04-20| Update for compatibility  
Version 3.5.1 | 2015-01-29| Update for compatibility  
Version 3.5.0 | 2014-10-26| Upgrade to Java 8  
Version 3.4.1 | 2014-08-27| Fix a thread safety issue in tsurgeon. _Last
version to support Java 6 and Java 7._  
Version 3.4 | 2014-06-16| Added a new tregex pattern, exact subtree, and
improved efficiency for certain operations  
Version 3.3.1 | 2014-01-04| Added a new tsurgeon operation, createSubtree  
Version 3.3.0 | 2013-11-12| Add an option to get a TregexMatcher from a
TregexPattern with a different HeadFinder  
Version 3.2.0 | 2013-06-20| Fix minor bug in tsurgeon indexing  
Version 2.0.6 | 2013-04-04| Updated for compatibility with other software
releases  
Version 2.0.5 | 2012-11-11| Minor efficiency improvements  
Version 2.0.4 | 2012-07-09| Minor bug fixes  
Version 2.0.3 | 2012-05-22| Updated to maintain compatibility with other
Stanford software.  
Version 2.0.2 | 2012-03-09| Regex matching efficiency improvement  
Version 2.0.1 | 2012-01-06| Fix matchesAt, fix category heads. _Last version
to support Java 5._  
Version 2.0 | 2011-09-14| Introduces semgrex, which operates on
SemanticGraphs.  
Version 1.4.4 | 2011-06-19| Updated to maintain compatibility with other
Stanford software.  
Version 1.4.3 | 2011-05-15| Updated to maintain compatibility with other
Stanford software.  
Version 1.4.2 | 2011-04-20| Addition of tree difference display. Several
bugfixes.  
Version 1.4.1 | 2010-11-18 | Small fixes and improvements (multipattern
Tsurgeon scripts, file and line numbers in sentence window, fixed GUI lock-up
and tregex immediate domination path matching)  
Version 1.4 | 2009-08-30| GUI slider for tree size, allow @ and __ in path
constraints, incompatibly generalize Tsurgeon relabel command, bug fix for
links and backreferences being used as named node, more memory/space efficient
treebank reading  
Version 1.3.2 | 2008-05-06| Additional features added to the graphical
interface, which is now version 1.1: browse trees, better memory handling  
Version 1.3.1| 2007-11-20| Additional features added to the graphical
interface: better copy/paste and drag and drop support, capability to save
matched sentences as well as matched trees, and can save files in different
encodings | Version 1.3| 2007-09-20| Various bug fixes and improvements;
additional Tsurgeon operations; and added a graphical interface | Version 1.2|
2005-11-23 | Bundled in Tsurgeon.  
Version 1.1.1| 2005-09-15 | Fixed bugs: 1) in variable groups; 2) in number of
reported matches for "<" relation  
Version 1.1| 2005-07-19 | Several new relations added; variable substring
capability added too.  
Version 1.0| 2005-02-17 | Initial release

