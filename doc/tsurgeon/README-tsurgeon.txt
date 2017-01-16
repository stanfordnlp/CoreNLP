Tsurgeon v3.7.0 - 2016-10-31
----------------------------------------------

Copyright (c) 2003-2012 The Board of Trustees of 
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
  
TSURGEON
----------------------------------------------
Tsurgeon is a tool for modifying trees that match a particular Tregex
pattern.  Further documentation for Tregex and Tregex GUI can be found in 
README-tregex.txt and README-gui.txt, respectively.

----------------------------------------------

Brief description:

Takes some trees, tries to match one or more tregex expressions to
each tree, and for each successful match applies some surgical
operations to the tree.  Pretty-prints each resulting tree (after all
successful match/operation sets have applied) to standard output.


A simple example:

  ./tsurgeon.csh -treeFile atree exciseNP renameVerb
  
-----------------------------------------
RUNNING TREGEX
-----------------------------------------

Program Command Line Options:

-treeFile <filename>

  specify the name of the file that has the trees you want to transform.

-po <matchPattern> <operation>

  Apply a single operation to every tree using the specified match
  pattern and the specified operation.

-s

  Prints the output trees one per line, instead of pretty-printed.

The arguments are then Tsurgeon scripts.
Each argument should be the name of a transformation file that contains a list of pattern
and transformation operation list pairs.  That is, it is a sequence of pairs of a
TregexPattern pattern on one or more lines, then a
blank line (empty or whitespace), then a list of transformation operations one per line
(as specified by Tsurgeon syntax below to apply when the pattern is matched,
and then another blank line (empty or whitespace).
Note the need for blank lines: The code crashes if they are not present as separators
(although the blank line at the end of the file can be omitted).
The script file can include comment lines, either whole comment lines or
trailing comments introduced by %, which extend to the end of line.  A needed percent
mark in partterns or operations can be escaped by a preceding backslash.

-----------------------------------------
TSURGEON SYNTAX
-----------------------------------------

Legal operation syntax and semantics (see Examples section for further detail):

delete <name_1> <name_2> ... <name_m>

  For each name_i, deletes the node it names and everything below it.

prune <name_1> <name_2> ... <name_m>

  For each name_i, prunes out the node it names.  Pruning differs from
  deletion in that if pruning a node causes its parent to have no
  children, then the parent is in turn pruned too.

excise <name1> <name2>

  The name1 node should either dominate or be the same as the name2
  node.  This excises out everything from name1 to name2.  All the
  children of name2 go into the parent of name1, where name1 was.

relabel <name> <new-label>

Relabels the node to have the new label. There are three possible forms
for the new-label:
  relabel nodeX VP - for changing a node label to an alphanumeric
  string, relabel nodeX /''/ - for relabeling a node to something that
  isn't a valid identifier without quoting, and relabel nodeX
  /^VB(.*)$/verb\/$1/ - for regular expression based relabeling. In the
  last case, all matches of the regular expression against the node
  label are replaced with the replacement String. This has the semantics
  of Java/Perl's replaceAll: you may use capturing groups and put them
  in replacements with $n. Also, as in the example, you can escape a
  slash in the middle of the second and third forms with \/ and \\.
  This last version lets you make a new label that is an arbitrary 
  String function of the original label and additional characters that
  you supply.

insert <name> <position>
insert <tree> <position>

  inserts the named node, or a manually specified tree (see below for
  syntax), into the position specified.  Right now the only ways to
  specify position are:

     $+ <name>     the left sister of the named node
     $- <name>     the right sister of the named node
     >i <name>     the i_th daughter of the named node.
     >-i <name>    the i_th daughter, counting from the right, of the named node.

move <name> <position>

  moves the named node into the specified position.  To be precise, it
  deletes (*NOT* prunes) the node from the tree, and re-inserts it
  into the specified position. See above for how to specify position

replace <name1> <name2>

  deletes name1 and inserts a copy of name2 in its place.

adjoin <tree> <target-node>

  adjoins the specified auxiliary tree (see below for syntax) into the
  target node specified.  The daughters of the target node will become
  the daughters of the foot of the auxiliary tree.
  
adjoinH <tree> <target-node>

  similar to adjoin, but preserves the target node and makes it the root
  of <tree>.  (It is still accessible as <code>name</code>.  The root of
  the auxiliary tree is ignored.) 
  
adjoinF <tree> <target-node>

  similar to adjoin, but preserves the target node and makes it the foot
  of <tree>.  (It is still accessible as <code>name</code>, and retains
  its status as parent of its children. The foot of the auxiliary tree
  is ignored.) 
  
coindex <name_1> <name_2> ... <name_m>

  Puts a (Penn Treebank style) coindexation suffix of the form "-N" on
  each of nodes name_1 through name_m.  The value of N will be
  automatically generated in reference to the existing coindexations
  in the tree, so that there is never an accidental clash of
  indices across things that are not meant to be coindexed.

-----------------------------------------

Syntax for trees to be inserted or adjoined:


A tree to be adjoined in can be specified with LISP-like
parenthetical-bracketing tree syntax such as those used for the Penn
Treebank.  For example, for the NP "the dog" to be inserted you might
use the syntax

  (NP (Det the) (N dog))

That's all that there is for a tree to be inserted.  Auxiliary trees
(a la Tree Adjoining Grammar) must also have exactly one frontier node
ending in the character "@", which marks it as the "foot" node for
adjunction.  Final instances of the character "@" in terminal node labels
will be removed from the actual label of the tree.

For example, if you wanted to adjoin the adverb "breathlessly" into a
VP, you might specify the following auxiliary tree:

  (VP (Adv breathlessly) VP@ )

All other instances of "@" in terminal nodes must be escaped (i.e.,
appear as \@); this escaping will be removed by tsurgeon.

In addition, any node of a tree can be named (the same way as in
tregex), by appending =<name> to the node label.  That name can be
referred to by subsequent tsurgeon operations triggered by the same
match.  All other instances of "=" in node labels must be escaped
(i.e., appear as \=); this escaping will be removed by tsurgeon.  For
example, if you want to insert an NP trace somewhere and coindex it
with a node named "antecedent" you might say

  insert (NP (-NONE- *T*=trace)) <node-location>
  coindex trace antecedent $
  
-----------------------------------------
Examples of Tsurgeon operations:  

Tree (used in all examples):
(ROOT
  (S
    (NP (NNP Maria_Eugenia_Ochoa_Garcia))
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))

Apply delete:
	VP < PP=prep
	delete prep
Result:
(ROOT
  (S
    (NP (NNP Maria_Eugenia_Ochoa_Garcia))
    (VP (VBD was)
      (VP (VBN arrested)
    (. .)))
The PP node directly dominated by a VP is removed, as is
everything under it.

Apply prune:
	S < (NP < NNP=noun)
	prune noun
Result:
(ROOT
  (S
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))
The NNP node is removed, and since this results in the NP above it
having no terminal children, the NP node is deleted as well.
Note: This is different from delete in which the NP above the NNP 
would remain.

Apply excise:
	VP < PP=prep
	excise prep prep
Result:
(ROOT
  (S
    (NP (NNP Maria_Eugenia_Ochoa_Garcia))
    (VP (VBD was)
      (VP (VBN arrested)
          (IN in)
          (NP (NNP May)))))
    (. .)))
The PP node is removed, and all of its children are added in the
place it was previously located.  Excise removes all the nodes from
the first named node to the second named node, and the children of 
the second node are added as children of the parent of the first node.
Thus, for another example:
	VP=verb < PP=prep
	excise verb prep
Result:
(ROOT
  (S
    (NP (NNP Maria_Eugenia_Ochoa_Garcia))
    (VP (VBD was)
		(IN in)
        (NP (NNP May)))
    (. .)))

    
Apply relabel: 
	VP=v < PP=prep
	relabel prep verbPrep
Result:
(ROOT
  (S
    (NP (NNP Maria_Eugenia_Ochoa_Garcia))
    (VP (VBD was)
      (VP (VBN arrested)
        (verbPrep (IN in)
          (NP (NNP May)))))
    (. .)))
The label for the node called prep (PP) is changed to verbPrep.
The other form of relabel uses regular expressions; consider the following
operation:
	/^VB.+/=v
	relabel v /^VB(.*)$/ #1
Result:
(ROOT
  (S
    (NP (NNP Maria_Eugenia_Ochoa_Garcia))
    (VP (D was)
      (VP (N arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))
The Tregex pattern matches all nodes that begin "VB" and have at least one
more character.  The Tsurgeon operation then matches the node label to the
regular expression "^VB(.*)$" and selects the text matching the first part
that is not completely specified in the pattern.  In this case, that is the
part matching the wildcard (.*), which matches all characters after the VB.
The node is then relabeled with that part of the text, causing, for example,
"VBD" to be relabeled "D".  The "#1" specifies that the name of the node
should be the first group in the regex.
    
Apply insert (shown here with inserting a node, but could also be a tree):
	S < (NP < (NNP=name !$- DET))
	insert (DET Ms.) $+ name
Result:
(ROOT
  (S
    (NP (DET Ms.)
        (NNP Maria_Eugenia_Ochoa_Garcia))
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))
The pattern matches the NNP node that is directly dominated by an NP
(which is directly dominated by an S) and is not a direct right sister
of a DET.  Thus, the (DET Ms.) node is inserted immediately to the left
of that NNP node, as specified by "$+ name".  "$+" is the location and
"name" describes what node the location is with respect to.
Note: Tsurgeon will re-search for matches after each run of the script;
thus, cycles may occur, causing the program to not terminate.  The key
is to write patterns that match prior to the changes you would like to
make but that do not match afterwards.  If the clause "!$- DET" had been
left out in this example, Tsurgeon would have matched the pattern after
every insert operation, causing an infinite number of DETs to be added.

Apply move:
	VP=verb < PP=prep
	move prep $- verb
Result:
(ROOT
  (S
    (NP (NNP Maria_Eugenia_Ochoa_Garcia))
    (VP (VBD was)
        (VP (VBN arrested)))
    (PP (IN in)
        (NP (NNP May)))
    (. .)))
The PP is moved out of the VP that dominates it and added as a direct right
sister of the VP.  As for insert, "$-" specifies the location for prep while
"verb" specifies what that location is relative to.
Note: "move" is a macro operation that deletes the given node and then inserts 
it. "move" does not use prune, and thus any branches that now lack terminals will
remain rather than being removed.

Apply replace:
	S < (NP=name < NNP)
	replace name (NP (DET A) (NN woman))
Result:
(ROOT
  (S
    (NP (DET A)
    	(NN woman))
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))
"name" is matched to an NP that is dominated by an S and dominates an NNP, and
a new subtree ("(NP (DET A) (NN woman))") is added in the place where "name" was.
Note: This operation is vulnerable to falling into an infinite loop. See the note 
concerning the "insert" operation and how patterns are matched.

Apply adjoin:
	S < (NP=name < NNP)
	adjoin (NP (DET A) (NN woman) NP@) name
Result:
(ROOT
  (S
    (NP (DET A)
    	(NN woman)
    	(NP (NNP Maria_Eugenia_Ochoa_Garcia)))
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))
First, the NP is matched to the NP dominating the NNP tag. Then, the specified
tree ("(NP (DET A) (NN woman) NP@)") is placed in that location.  The "@" symbol
specifies that the children of the original NP node ("name") are to be placed
as children of a new NP node that is directly to the right of (NN woman).  If
the specified tree were "(NP (DET A) (NN woman) VP@)" then the child
(NNP Maria_Eugenia_Ochoa_Garcia) would appear under a VP.  Exactly one "@" node 
must appear in the specified tree in order to indicate where to place the node
from the original tree.

Apply adjoinH:
	S < (NP=name < NNP)
	adjoinH ((NP (DET A) (NN woman) NP@)) name
Result:
(ROOT
  (S
    (NP (NP (DET A)
    		(NN woman)
    		(NP (NNP Maria_Eugenia_Ochoa_Garcia))))
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))
This operation differs from adjoin in that it retains the named node (in this
case, "name").  The named node is made the root of the specified tree, resulting
in two NP nodes dominating the DET in this example whereas only one was present
in the previous example.  Note that the specified tree is wrapped in an extra
pair of parentheses in order to show the syntax for retaining the named node.
If the extra parentheses were not there and the specified tree was, for example, 
(VP (DET A) (NN woman) NP@), the VP would be ignored in order to retain an NP as
the root.  Thus, in this case, "adjoinH (VP (DET A) (NN woman) NP@) name" and
"adjoinH ((DET A) (NN woman) NP@) name" both produce the same tree:
(ROOT
  (S
    (NP (DET A)
    	(NN woman)
    	(NP (NNP Maria_Eugenia_Ochoa_Garcia)))
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))


Apply adjoinF:
	S < (NP=name < NNP)
	adjoinF (NP(DET A) (NN woman) @) name
Result: 
(ROOT
  (S
    (NP (DET A)
    	(NN woman)
    	(NP (NNP Maria_Eugenia_Ochoa_Garcia)))
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP (NNP May)))))
    (. .)))
This operation is very similar to adjoin and adjoinH, but this time the original
named node ("name" in this case) is maintained as the root of the subtree that
is adjoined.  Thus, no node label needs to be given in front of the "@" and if
one is given, it will be ignored.  For instance, "adjoinF (NP(DET A) (NN woman) VP@) name"
would still produce the same tree as above, despite the VP preceding the @.

Apply coindex:
	NP=node < NNP=name
	coindex node name
Result:
(ROOT
  (S
    (NP-1 (NNP-1 Maria_Eugenia_Ochoa_Garcia))
    (VP (VBD was)
      (VP (VBN arrested)
        (PP (IN in)
          (NP-2 (NNP-2 May)))))
    (. .)))
This causes the named nodes to be numbered such that all nodes that are part
of the same match have the same number and all matches have distinct new names.
We had two instances of an NP dominating an NNP in this example, and they were
renamed such that NP-i < NNP-i for each match, with 1 <= i <= number of matches.

-----------------------------------------
TSURGEON SCRIPTS
-----------------------------------------
Script format:

Tsurgeon scripts are a combination of a Tregex pattern to match and a series
of Tsurgeon operations to perform on that match.  The first line of a Tsurgeon
script should be the Tregex pattern.  This should be followed by a blank line,
and then each subsequent line may contain one Tsurgeon operation.  Tsurgeon
operations should not be separated by blank lines.  The following is an example
of  correctly formatted script:

S < NP=node < NNP=name

relabel node NP_NAME
coindex node name


Comments: 

The character % introduces a comment that extends to the end of the
line.  All other intended uses of % must be escaped as \% .

-----------------------------------------
CONTACT
-----------------------------------------

For questions about this distribution, please contact Stanford's JavaNLP group at
parser-support@lists.stanford.edu.  We provide assistance on a best-effort basis.


-----------------------------------------
LICENSE
-----------------------------------------

 Tregex, Tsurgeon, and Interactive Tregex
 Copyright (c) 2003-2011 The Board of Trustees of 
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
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 For more information, bug reports, fixes, contact:
    Christopher Manning
    Dept of Computer Science, Gates 1A
    Stanford CA 94305-9010
    USA
    parser-support@lists.stanford.edu
    http://www-nlp.stanford.edu/software/tregex.shtml


