Tregex GUI v3.7.0 - 2016-10-31
----------------------------------------------

Copyright (c) 2003-2012 The Board of Trustees of 
The Leland Stanford Junior University. All Rights Reserved.

Original core Tregex code by Roger Levy and Galen Andrew.
Original core Tsurgeon code by Roger Levy.
TregexGUI by Anna Rafferty
Support code, additional features, etc. by Chris Manning
This release prepared by John Bauer.

----------------------------
TREGEX GRAPHICAL USER INTERFACE (GUI) README
----------------------------

The Tregex GUI is a graphical user interface for Tregex and Tsurgeon.
You can access it by double- clicking on the jar file tregex.jar. For
searching large treebanks, you may need to use more memory; the script
run-tregex-gui.command includes this allocation of memory and can be run
from the command line or double-click to run on a Mac. If you still have
memory problems, you can allot more memory by opening the script in a
text editor and changing "-mx300m" to include a bigger number (e.g.,
"-mx512m").  Tregex requires Java 1.5+.  Further documentation for
Tregex and Tsurgeon can be found in README-tregex.txt and
README-tsurgeon.txt, respectively.

----------------------------
LOADING TREEBANKS/TREE FILES
----------------------------

To load a file containing Penn Treebank formatted trees, choose "Load trees..." from the file menu.  
Multiple tree files and/or directories may be selected.  After selecting the tree files you wish to 
load, press "Load with file filters" to choose what filters you would like to apply to the files.  
All filters are run based on the name of the file.  Possible filtering options are:

- Prefix: Load only files that start with the given character sequence

- Extension: Load only files that end with the given character sequence

- Has number in range: Loads only numbered files such that the number falls in the given range, inclusive.
Ranges can be disjoint as long as multiple ranges are comma-separated (e.g., "100-500,550-700")

File filters are combined such that all loaded files must obey all of
the filters; only one filter of any given type should be specified.  

Once the tree files are loaded, their names appear in the upper left hand panel "Tree files:".  
Unchecking the check boxes next to the files causes the unchecked files not to be included in 
searches/tsurgeon operations.  To remove all files from the tree panel, choose "Clear all files"
from the Edit menu.

----------------------------
PERFORMING TREGEX SEARCHES
----------------------------

To perform a Tregex search, load the files you would like to search and type a Tregex pattern 
in the "Pattern:" box in the top middle of the window.  Press "Help" beneath the Pattern box 
for information about Tregex syntax.  After you have typed the pattern, press "Search" to 
find all matches to the given pattern.

By default, trees that contain at least one match are displayed in the "Matches:" panel in the 
top right of the window, and the first matching tree is graphically displayed in the bottom 
portion of the window.  Click on a match in the Match panel to display it graphically.  In the 
graphical display, matched nodes in the tree are displayed in a different color than other nodes.  
To display only the matched subtrees, choose "Preferences..." (Mac, from the Application menu) or 
"Options..." (other OS, under Tools), and check "Show only matched portions of the tree".  You must 
rerun the search to switch between showing only matched portions and showing full trees.

In preferences, other display options can also be set, such as the colors, size, and font used by 
the graphical display.

----------------------------
USING TSURGEON
----------------------------

Tsurgeon modifications can also be performed using Interactive Tregex.  To enable Tsurgeon, choose 
"Preferences..." from the File menu and check "Enable Tsurgeon".  You can now run Tsurgeon scripts. 
Tsurgeon commands must be paired with a Tregex pattern that names the nodes on which modifications 
will be performed.  Type the Tregex pattern in the Pattern box, and type the modifications you would 
like to make in the "Tsurgeon script:" box.  Then click "Run script" to perform the modifications.  
Each Tsurgeon operation must appear on a separate line in the Tsurgeon script box.  Press "Help" for 
some information about Tsurgeon operation syntax.


----------------------------
SAVING RESULTS
----------------------------

You can save the results of a Tregex search or Tsurgeon operation by choosing "Save matches..." from the 
File menu.  This saves all trees in the Matches panel in Penn Treebank form. "Save matched sentences..." saves
the matches in sentence String form, just as they show up in the matches panel.

You can also save a log of the number of matches found for each pattern you have searched.  By clicking the
"Statistics" button in the middle of the screen, below the Tsurgeon buttons, you can see a table of the patterns
for which you have searched, the number of trees that each matched, and the number of overall matches that were
found.  To save this information in a tab delimited text file, choose "Save statistics..." from the File menu. 

All three save options save files in the encoding specified in the Preferences panel for loading tree files.

----------------------------
MULTILANGUAGE SUPPORT
----------------------------

Some multilanguage support is built into Tregex GUI, and most languages can be read by the GUI.  To enable
this support, choose go to Preferences (Mac, under the application menu) or Options (other OS, under the Tools menu).
Several options may need to be changed: tree reader factory, head finder, font, and encoding.  Several possible
tree reader factories and head finders are provided; you may also specify your own. Two common languages you may be
trying to use are Chinese or Arabic; any head finder or tree reader factory beginning with "Chinese" or "Arabic" will
work for these languages, and additionally, CTBTreeReaderFactory is compatible with many Chinese treebanks.  Based on
your choice of head finder and tree reader factory, the Tregex GUI will guess if you may need a different font and/or 
text encoding. If a different text encoding is usually used for your selections, you will be prompted as to what text 
encoding you would like to use.  This may also be specified directly in the Preferences panel.  



----------------------------
QUESTIONS
----------------------------

For more information on Tregex or Tsurgeon, read README-tregex.txt and README-tsurgeon.txt, and also look at the javadocs 
suggested in those files.  For questions about this distribution, please contact Stanford's JavaNLP group at
parser-support@lists.stanford.edu.  We provide assistance on a best-effort basis.

----------------------------
LICENSE
----------------------------

 Tregex GUI
 Copyright (c) 2007-2011 The Board of Trustees of 
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
  

----------------------------
CHANGES
----------------------------

2016-10-31    3.7.0     Update for compatibility 

2015-12-09    3.6.0     Updated for compatibility 

2015-04-20    3.5.2     Update for compatibility 

2015-01-29    3.5.1     General bugfixes 

2014-10-26    3.5.0     Upgrade to Java 1.8 

2014-08-27    3.4.1     Fix bug by adding matcher object for tsurgeon 

2014-06-16      3.4     Improved efficiency for some operations, new 
                        tregex and tsurgeon patterns added 

2014-01-04    3.3.1     Bugfix release, new createSubtree tsurgeon 
                        operation 

2013-11-12    3.3.0     Allow a TregexMatcher to have its own 
                        HeadFinder, useful for the dependencies 

2013-06-19    3.2.0     Fix for tsurgeon number reading bug 

2013-04-04    2.0.6     Update to maintain compatibility 

2012-11-11    2.0.5     Efficiency improvements 

2012-07-09    2.0.4     Minor bug fixes 

2012-05-22    2.0.3     Rebuilt to be compatible with everything.

2012-03-09    2.0.2     Efficiency improvements

2011-12-16    2.0.1     Fix bug in matchesAt, fix bug in category 
                        function, add macros

2011-09-14    2.0.0     Efficiency improvements, include semgrex.

2011-05-15    1.4.4     Rebuilt to be compatible with everything.

2011-05-15    1.4.3     Rebuilt to be compatible with everything.

2011-04-17    1.4.2     Rebuilt to be compatible with tagger, parser, 
                        and corenlp.

2010-11-18    1.4.1     Small fixes and improvements (improved help 
                        screens, multipattern Tsurgeon scripts with
                        comments introduced by % supported, unclosed
                        regex no longer crashes GUI, support character
                        encodings in script files, fix bug in tregex
                        matching immediate domination path, TregexGUI
                        now shows filename and line number of each
                        match in matches panel)

2009-09-30      1.4     GUI slider for tree size, generalized relabel
                        command (incompatibly), __ and @ now supported
                        in path constraints; bugfixes.

2008-05-06      1.1     Several bug fixes; addition of browse trees
                        function, improved copy/paste and drag and
                        drop support; misc. feature additions

2007-09-20      1.0     Initial release
