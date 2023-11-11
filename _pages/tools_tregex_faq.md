---
layout: page
title: Tregex and Tsurgeon FAQ
keywords: tregex, tsurgeon, semgrex, ssurgeon
permalink: '/tools_tregex_faq.html'
nav_order: 7
toc: true
parent: Additional Tools
---

### Is there a User Guide?

At present, no. There is:

* The information in the README-tregex.txt, README-tsurgeon.txt, and README-gui.txt files
* The information on patterns and programmatic use in the Javadocs. The `TregexPattern` and `Tsurgeon` class javadoc give relatively complete information on the pattern languages of these tools.
* The GUI has help screens, available within the graphical interface by clicking on the Help buttons, which provide similar information on Tregex and Tsurgeon syntax, as does [this page](http://nlp.stanford.edu/~manning/courses/ling289/Tregex.html).
* There are brief powerpoint tutorials for [Tregex](https://nlp.stanford.edu/software/tregex/The_Wonderful_World_of_Tregex.ppt) (by Galen Andrew) and [Tsurgeon](https://nlp.stanford.edu/software/tregex/Tsurgeon2.ppt) (by Marie-Catherine de Marneffe).
* For `tregex`, you may also usefully consult user guides for `tgrep` and `tgrep2`, with which `tregex` is mainly compatible.

### What command-line options does Tregex have?

Here are some details on the command-line options of Tregex, taken from the
javadoc for the main method of `TregexPattern`:

Usage:  
  
`java edu.stanford.nlp.trees.tregex.TregexPattern [[-TCwfosnu] [-filter] [-h <node-name>]]* pattern filepath`

Arguments:  

- `pattern`: the tree pattern which optionally names some set of nodes (i.e., gives it the "handle") `=name` (for some arbitrary string "name") 
- `filepath`: the path to files with trees. If this is a directory, there will be recursive descent and the pattern will be run on all files beneath the specified directory. 

Options:

- `-C` suppresses printing of matches, so only the number of matches is printed. 
- `-w` causes the whole of a tree that matches to be printed. 
- `-f` causes the filename to be printed. 
- `-i <filename>` causes the pattern to be matched to be read from `<filename>` rather than the command line. Don't specify a pattern when this option is used. 
- `-o` Specifies that each tree node can be reported only once as the root of a match (by default a node will be printed once for every _way_ the pattern matches). 
- `-s` causes trees to be printed all on one line (by default they are pretty printed). 
- `-n` causes the number of the tree in which the match was found to be printed before every match. 
- `-u` causes only the label of each matching node to be printed, not complete subtrees. 
- `-t` causes only the yield (terminal words) of the selected node to be printed (or the yield of the whole tree, if the `-w` option is used). 
- `-encoding <charset_encoding>` option allows specification of character encoding of trees. 
- `-h <node-handle>` If a `-h` option is given, the root tree node will not be printed. Instead, for each `node-handle` specified, the node matched and given that handle will be printed. Multiple nodes can be printed by using the `-h` option multiple times on a single command line. 
- `-hf <headfinder-class-name>` use the specified {@link HeadFinder} class to determine headship relations. 
- `-hfArg <string>` pass a string argument in to the {@link HeadFinder} class's constructor. `-hfArg` can be used multiple times to pass in multiple arguments. 
- `-trf <TreeReaderFactory-class-name>` use the specified {@link TreeReaderFactory} class to read trees from files. 
- `-v` print every tree that contains no matches of the specified pattern, but print no matches to the pattern. 
- `-x` Instead of the matched subtree, print the matched subtree's identifying number as defined in `tgrep2`:a unique identifier for the subtree and is in the form s:n, where s is an integer specifying the sentence number in the corpus (starting with 1), and n is an integer giving the order in which the node is encountered in a depth-first search starting with 1 at top node in the sentence tree. 
- `-extract <code> <tree-file>` extracts the subtree s:n specified by `code` from the specified `tree-file`. Overrides all other behavior of tregex. Can't specify multiple encodings etc. yet. 
- `-extractFile <code-file> <tree-file>` extracts every subtree specified by the subtree codes in `code-file`, which must appear exactly one per line, from the specified `tree-file`. Overrides all other behavior of tregex. Can't specify multiple encodings etc. yet. 
- `-filter` causes this to act as a filter, reading tree input from stdin 
- `-T` causes all trees to be printed as processed (for debugging purposes). Otherwise only matching nodes are printed. 

### Tsurgeon has stopped responding/gone into an infinite loop. Is that a bug?

Probably not (though you never know). Normally what this means is that you
have written an infinite loop in your tree surgery script.

Tsurgeon script matching and rewriting is applied repeatedly (recursively)
until no further matching and rewriting is possible. This is the behavior that
you want in complex cases: large trees may match several patterns in a complex
Tsurgeon script, and earlier edits may enable later edits. However,
unfortunately, it also makes it very easy to write infinite loops in Tsurgeon,
and this can confuse beginners. Here's a very simple example of how you can
produce an infinite loop (you can come up with complex examples by yourself!):

```
/^VB/=haveaux < have|has|having|had

relabel haveaux /^(.*)$/$1-HAVE/
```

The first poor `haveaux` node that matches the pattern, say `(VBZ has)`, will
get repeatedly relabeled as:

```
VBZ-HAVE  
VBZ-HAVE-HAVE  
VBZ-HAVE-HAVE-HAVE  
VBZ-HAVE-HAVE-HAVE-HAVE  
...
```

And you can see where that is heading. It is essential to write edits so that
they will not apply to their own output forever. For this example, things can
easily be fixed with the following changed script:

```
/^VB.?$/=haveaux < have|has|having|had  

relabel haveaux /^(.*)$/$1-HAVE/
```

For other questions, feedback, extensions, or bugfixes, please join and post
to the `parser-user@lists.stanford.edu` mailing list. Or you can send email to
[`parser-support@lists.stanford.edu`](mailto:parser-
support@lists.stanford.edu).

