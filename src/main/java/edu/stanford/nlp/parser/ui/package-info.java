/**
 * <body>
 * This package contains graphical user interface components for parsing sentences
 * with parser created using lexparser.LexicalizedParser.
 * ParserPanel is a component that can also has a main() method that allows it to
 * be run as an app.  To run, on the command-line, type:
 * <B>java ParserPanel [<I>parser</I>] [<I>data file</I>]</B>
 * [<I>parser</I>] and [<I>data file</I>] are two optional parameters that allow the
 * user to specify at the command-line the path to a serialized parser and a text
 * data file respectively.  If no parser is specified at the command-line, the user
 * must first load a parser by clicking on the "Load Parser" button before they can
 * parse sentences.
 * TreeJPanel is a component for displaying edu.stanford.nlp.trees.Tree objects.  To
 * display a tree, simply use the <I>setTree(Tree tree)</I> method.
 * @author Dan Klein (klein@cs.stanford.edu)
 * @author Huy Nguyen (htnguyen@stanford.edu)
 * </body>
 */
package edu.stanford.nlp.parser.ui;