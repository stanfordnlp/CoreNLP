Tregex v4.2.0 - 2020-11-17
----------------------------------------------

Copyright (c) 2003-2020 The Board of Trustees of 
The Leland Stanford Junior University. All Rights Reserved.

Original core Tregex code by Roger Levy and Galen Andrew.
Original core Tsurgeon code by Roger Levy.
GUI by Anna Rafferty
Support code, additional features, etc. by Chris Manning
This release prepared by John Bauer.

This package contains Tregex and Tsurgeon.

Tregex is a Tgrep2-style utility for matching patterns in trees.  It can
be run in a graphical user interface, from the command line using the 
TregexPattern main method, or used programmatically in java code via the 
TregexPattern, TregexMatcher and TregexPatternCompiler classes.

As of version 1.2, the Tsurgeon tree-transformation utility is bundled
together with Tregex.  See the file README.tsurgeon for details.

Java version 1.8 is required to use Tregex.  If you really want to use
Tregex under an earlier version of Java, look into RetroWeaver:

  http://retroweaver.sourceforge.net/
  

QUICKSTART
-----------------------------------------------

Programmatic use, command-line use, and GUI-use are supported.  To access the 
graphical interface for Tsurgeon and Tregex, double-click the tregex.jar file. 
Some help (particularly with syntax) is provided within the program; for further
assistance, see README-gui.txt and the documentation mentioned below.

A full explanation of pattern syntax and usage is given in the javadocs
(particularly TregexPattern), and some of this information is also presented in
the TREGEX SYNTAX section below.  As a quick example of usage,
the following line will scan an English PennTreebank annotated corpus
and print all nodes representing a verb phrase dominating a past-tense
verb and a noun phrase.

./tregex.sh 'VP < VBD < NP' corpus_dir


CONTENTS
-----------------------------------------------

README-tregex.txt

  This file.
  
  
README-tsurgeon.txt

  Documentation for Tsurgeon, a tool for modifying trees.
  
README-gui.txt

  Documentation for the graphical interface for Tregex and Tsurgeon tools.
  
LICENSE.txt

  Tregex is licensed under the GNU General Public License.

stanford-tregex.jar

  This is a JAR file containing all the Stanford classes necessary to
  run tregex.

src

  A directory containing the Java 1.8 source code for the Tregex
  distribution.

javadoc

  Javadocs for the distribution.  In particular, look at the javadocs
  for the class edu.stanford.nlp.trees.tregex.TregexPattern.  The
  first part of that class's javadoc describes syntax and semantics
  for relations, node labels, node names, and variable groups.  The
  docs for the main method describe command-line options.

tregex.sh

  a shell script for invoking the Tregex tree search tool.

tsurgeon.sh

  a shell script for invoking the Tsurgeon tree transformation tool.
  
run-tregex-gui.command
  
  A command file that can be double-clicked on a Mac to start the gui.
  
run-tregex-gui.bat
 
  A bat file that can be double-clicked on a PC to start the gui.

examples

  a directory containing several sample files to show Tsurgeon operation:
- atree
  a sample natural-language tree in Penn Treebank annotation style.
- exciseNP
- renameVerb
- relabelWithGroupName
  Sample tree-transformation operation files for Tsurgeon.  See
  README-tsurgeon.txt for more information about the contents of these
  files.
  
  
TREGEX 
-----------------------------------------------
Tregex Pattern Syntax and Uses

Using a Tregex pattern, you can find only those trees that match the pattern you're 
looking for.  The following table shows the symbols that are allowed in the pattern, 
and below there is more information about using these patterns. 
 
Table of Symbols and Meanings:
A << B	
   A dominates B  
A >> B 
   A is dominated by B  
A < B 
   A immediately dominates B  
A > B 
   A is immediately dominated by B  
A $ B 
   A is a sister of B (and not equal to B)   
A .. B 
   A precedes B 
A . B 
   A immediately precedes B 
A ,, B 
   A follows B 
A , B 
   A immediately follows B 
A <<, B 
   B is a leftmost descendent of A 
A <<- B 
   B is a rightmost descendent of A 
A >>, B 
   A is a leftmost descendent of B 
A >>- B 
   A is a rightmost descendent of B 
A <, B 
   B is the first child of A 
A >, B 
   A is the first child of B 
A <- B 
   B is the last child of A 
A >- B 
   A is the last child of B 
A <` B 
   B is the last child of A 
A >` B 
   A is the last child of B 
A <i B 
   B is the ith child of A (i > 0) 
A >i B 
   A is the ith child of B (i > 0) 
A <-i B 
   B is the ith-to-last child of A (i > 0) 
A >-i B 
   A is the ith-to-last child of B (i > 0) 
A <: B 
   B is the only child of A 
A >: B 
   A is the only child of B 
A <<: B 
   A dominates B via an unbroken chain (length > 0) of unary local trees. 
A >>: B 
   A is dominated by B via an unbroken chain (length > 0) of unary local trees. 
A $++ B 
   A is a left sister of B (same as $.. for context-free trees) 
A $-- B 
   A is a right sister of B (same as $,, for context-free trees) 
A $+ B 
   A is the immediate left sister of B (same as $. for context-free trees) 
A $- B 
   A is the immediate right sister of B (same as $, for context-free trees) 
A $.. B 
   A is a sister of B and precedes B 
A $,, B 
   A is a sister of B and follows B 
A $. B 
   A is a sister of B and immediately precedes B 
A $, B 
   A is a sister of B and immediately follows B 
A <+(C) B 
   A dominates B via an unbroken chain of (zero or more) nodes matching description C 
A >+(C) B 
   A is dominated by B via an unbroken chain of (zero or more) nodes matching description C 
A .+(C) B 
   A precedes B via an unbroken chain of (zero or more) nodes matching description C 
A ,+(C) B 
   A follows B via an unbroken chain of (zero or more) nodes matching description C 
A <<# B 
   B is a head of phrase A 
A >># B 
   A is a head of phrase B 
A <# B 
   B is the immediate head of phrase A 
A ># B 
   A is the immediate head of phrase B 
A == B 
   A and B are the same node 
A : B
   [this is a pattern-segmenting operator that places no constraints on the relationship between A and B] 

 Label descriptions can be literal strings, which much match labels  exactly, or regular 
 expressions in regular expression bars: /regex/.  Literal string matching proceeds as 
 String equality. In order to prevent ambiguity with other Tregex symbols, only standard  
 "identifiers" are allowed as literals, i.e., strings matching [a-zA-Z]([a-zA-Z0-9_])* . 
 If you want to use other symbols, you can do so by using a regular expression instead of 
 a literal string.  A disjunctive list of literal strings can be given separated by '|'. 
 The special string '__' (two underscores) can be used to match any  node.  (WARNING!!  
 Use of the '__' node description may seriously  slow down search.)  If a label description 
 is preceeded by '@', the label will match any node whose basicCategory matches the description.  
NB: A single '@' thus scopes over a disjunction  specified by '|': @NP|VP means things with basic category NP or VP.  

Label description regular expressions are matched as find(),  as in Perl/tgrep;  
you need to specify ^ or $ to constrain matches.    

In a chain of relations, all relations are relative to the first node in the chain. 
For example, (S < VP < NP) means an S over a VP and also over an NP.  If instead what 
you want is an S above a VP above an NP, you should write  S < (VP < NP).  

Nodes can be grouped using parentheses '(' and ')'  as in  S < (NP $++ VP)  to match an S 
over an NP, where the NP has a VP as a right sister.  

Boolean relational operators

Relations can be combined using the '&' and '|' operators, negated with the '!' operator, 
and made optional with the '?' operator.  Thus (NP < NN | < NNS)  will match an NP node 
dominating either  an NN or an NNS.   (NP > S & $++ VP)  matches an NP that  is both under 
an S and has a VP as a right sister.   

Relations can be grouped using brackets '[' and ']'.  So the  expression 

NP [< NN | < NNS] & > S 

matches an NP that (1) dominates either an NN or an NNS, and (2) is under an S.  Without  
brackets, & takes precedence over |, and equivalent operators are left-associative.  Also 
note that & is the default combining operator if the  operator is omitted in a chain of 
relations, so that the two patterns are equivalent: 
   (S < VP < NP)
   (S < VP & < NP)     
   
As another example,  (VP < VV | < NP % NP) can be written explicitly as  (VP [< VV | [< NP & % NP] ] ). 

Relations can be negated with the '!' operator, in which case the expression will match 
only if there is no node satisfying the relation.  For example  (NP !< NNP)  matches only 
NPs not dominating  an NNP.  Label descriptions can also be negated with '!': (NP < !NNP|NNS) 
matches  NPs dominating some node that is not an NNP or an NNS.  

Relations can be made optional with the '?' operator.  This way the expression will match even 
if the optional relation is not satisfied.  This is useful when used together with node naming 
(see below).  


Basic Categories

In order to consider only the "basic category" of a tree label,  i.e. to ignore functional tags 
or other annotations on the label,  prefix that node's description with the @ symbol.  For example   
(@NP < @/NN.?/).   This can only be used for individual nodes;  if you want all nodes to use the 
basic category, it would be more efficient  to use a TreeNormalizer to remove functional tags 
before passing the tree to the TregexPattern.   


Segmenting patterns

The ":" operator allows you to segment a pattern into two pieces.  This can simplify your pattern 
writing.  For example,  the pattern S : NP matches only those S nodes in trees that also have an NP node.   


Naming nodes

Nodes can be given names (a.k.a. handles) using '='.  A named node will be stored in a  map that 
maps names to nodes so that if a match is found, the node  corresponding to the named node can 
be extracted from the map.  For  example  (NP < NNP=name)  will match an NP dominating an NNP  
and after a match is found, the map can be queried with the  name to retreived the matched node 
using {@link TregexMatcher#getNode(Object o)}  with (String) argument "name" (not "=name").  Note 
that you are not allowed to name a node that is under the scope of a negation operator (the 
semantics would  be unclear, since you can't store a node that never gets matched to).  Trying to 
do so will cause a ParseException to be thrown. Named nodes can be put within the scope of an 
optional operator.   

Named nodes that refer back to previous named nodes need not have a node  description -- this is 
known as "backreferencing".  In this case, the expression  will match only when all instances of 
the same name get matched to the same tree node.  For example, the pattern:

(@NP <, (@NP $+ (/,/ $+ (@NP $+ /,/=comma))) <- =comma)  

matches only an NP dominating exactly the sequence NP, NP; the mother NP cannot have any other 
daughters. Multiple  backreferences are allowed.  If the node with no node description does not 
refer  to a previously named node, there will be no error, the expression simply will  not match 
anything.   

Another way to refer to previously named nodes is with the "link" symbol: '~'.  A link is like a 
backreference, except that instead of having to be <i>equal to</i> the  referred node, the 
current node only has to match the label of the referred to node.  A link cannot have a node 
description, i.e. the '~' symbol must immediately follow a  relation symbol.   


Variable Groups

If you write a node description using a regular expression, you can assign its matching groups to 
variable names.  If more than one node has a group assigned to the same variable name, then matching 
will only occur when all such groups  capture the same string.  This is useful for enforcing 
coindexation constraints.  The syntax is:

/ <regex-stuff> /#<group-number>%<variable-name>

For example, the pattern (designed for Penn Treebank trees):    

@SBAR < /^WH.*-([0-9]+)$/#1%index<<(__=empty < (/^-NONE-/< /^\\*T\\*-([0-9]+)$/#1%index))

will match only such that the WH- node under the SBAR is coindexed with the trace node that gets the name empty.


MISCELLANEOUS
-----------------------------------------------

Head Finders

  To use the headship relations <# ># <<# >># correctly it is
  important to specify a HeadFinder class appropriate to the trees
  that you are searching.  For information about how to specify a
  HeadFinder class at the command line or through the API, please read
  the javadocs for the class
  edu.stanford.nlp.trees.tregex.TregexPattern.  The following
  HeadFinder classes are included with the Tregex distribution:

  Penn Treebank of English (http://www.cis.upenn.edu/~treebank/):

    edu.stanford.nlp.trees.CollinsHeadFinder (default)

  Penn Treebank of Chinese (http://www.cis.upenn.edu/~chinese/):

    edu.stanford.nlp.trees.international.pennchinese.ChineseHeadFinder

  Penn Treebank of Arabic (http://www.ircs.upenn.edu/arabic/):

    edu.stanford.nlp.trees.international.arabic.ArabicHeadFinder

  NEGRA (http://www.coli.uni-saarland.de/projects/sfb378/negra-corpus/) 

  and

  TIGER (http://www.ims.uni-stuttgart.de/projekte/TIGER/TIGERCorpus/)

  treebanks of German (these can use the same headfinder):

    edu.stanford.nlp.trees.international.negra.NegraHeadFinder

  Tuebingen Treebank of Written German (http://www.sfs.uni-tuebingen.de/en/ascl/resources/corpora/tueba-dz.html):

    edu.stanford.nlp.trees.international.tuebadz.TueBaDZHeadFinder
    
    
Tdiff

  TregexGUI supports a constituent diff'ing method--similar to the UNIX diff command--for trees. To
enable Tdiff:
  1) Clear the tree file list: File -> Clear tree file list 
  2) Enable Tdiff: Options -> Tdiff
  3) Load two (2) files using the "File -> Load" dialog.
  4) Select "Browse" on the main display
  
The GUI will display differences between each pair of trees in the two files. As such, the two files must 
contain the same number of trees. 

The first file in the tree file list is treated as the reference. Trees from the second file 
will be displayed in the GUI, with bracketing differences highlighted in blue. Below the tree, 
constituents in the reference tree that do not appear in the tree from the second file are shown
as lines below each respective span.

Tregex searches are supported and apply to the trees in the second file.

This feature was designed for debugging and analyzing parser output.

THANKS
-----------------------------------------------

Thanks to the members of the Stanford Natural Language Processing Lab
for great collaborative work on Java libraries for natural language
processing.

  http://nlp.stanford.edu/javanlp/
  
LICENSE
-----------------------------------------------

 Tregex, Tsurgeon, and Interactive Tregex
 Copyright (c) 2003-2012 The Board of Trustees of 
 The Leland Stanford Junior University. All Rights Reserved.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see http://www.gnu.org/licenses/ .

 For more information, bug reports, fixes, contact:
    Christopher Manning
    Dept of Computer Science, Gates 2A
    Stanford CA 94305-9020
    USA
    parser-support@lists.stanford.edu
    http://nlp.stanford.edu/software/tregex.html
  

CONTACT
-----------------------------------------------

For questions about this distribution, please contact Stanford's JavaNLP group at
parser-support@lists.stanford.edu.  We provide assistance on a best-effort basis.
