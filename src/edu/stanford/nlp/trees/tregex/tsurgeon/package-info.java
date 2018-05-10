/**
 * A package for performing
 * transformations of trees to be used in conjunction with
 * {@code edu.stanford.nlp.trees.tregex}.
 * Look at the description below and the class comments for
 * Tsurgeon for more information.
 *
 * <p>Operations are applied while their pattern match.  You must be careful
 * to ensure that patterns do not continue to match after they have been
 * applied, or else Tsurgeon will go into an infinite loop.
 *
 * <h3>Description of operations:</h3>
 *
 * <pre>
 * delete name_1 name_2 ... name_m
 *
 *   For each name_i, deletes the node it names and everything below it.
 *
 * prune name_1 name_2 ... name_m
 *
 *   For each name_i, prunes out the node it names.  Pruning differs from
 *   deletion in that if pruning a node causes its parent to have no
 *   children, then the parent is in turn pruned too.
 *
 * excise name1 name2
 *
 *   The name1 node should either dominate or be the same as the name2
 *   node.  This excises out everything from name1 to name2.  All the
 *   children of name2 go into the parent of name1, where name1 was.
 *
 * relabel name new-label
 *
 *   Relabels the node to have the new label. There are three possible forms
 *   for the new-label:
 *
 *   relabel nodeX VP - for changing a node label to an alphanumeric
 *   string
 *
 *   relabel nodeX /''/ - for relabeling a node to something that
 *   isn't a valid identifier without quoting, and relabel nodeX
 *
 *   /^VB(.*)$/verb\/$1/ - for regular expression based relabeling. In the
 *   last case, all matches of the regular expression against the node
 *   label are replaced with the replacement String. This has the semantics
 *   of Java/Perl's replaceAll: you may use capturing groups and put them
 *   in replacements with $n. Also, as in the example, you can escape a
 *   slash in the middle of the second and third forms with \/ and \\.
 *   This last version lets you make a new label that is an arbitrary
 *   String function of the original label and additional characters that
 *   you supply.
 *
 * relabel name new-label
 *
 *   Renames the node to have the new label.  If the new-label is not
 *   a valid tregex identifier, you can quote it by surrounding it by
 *   pipe characters (|new-label|).
 *
 * relabel name regex groupNumber
 *
 *   matches the regex against the node's current label, and then renames
 *   the node to have a label that corresponds to the n-th group of the
 *   regex.
 *
 * insert name position
 * insert tree position
 *
 *   inserts the named node, or a manually specified tree (see below for
 *   syntax), into the position specified.  Right now the only ways to
 *   specify position are:
 *
 *   $+ name     to insert the left sister of the named node
 *   $- name     to insert  the right sister of the named node
 *   &gt;i name     the i_th daughter of the named node.
 *   &gt;-i name    the i_th daughter, counting from the right, of the named node.
 *
 * move name position
 *
 *   moves the named node into the specified position.  To be precise, it
 *   deletes (*NOT* prunes) the node from the tree, and re-inserts it
 *   into the specified position.
 *
 * replace name1 name2
 *
 *   deletes name1 and inserts a copy of name2 in its place.
 *
 * adjoin tree target-node
 *
 *   adjoins the specified auxiliary tree (see below for syntax) into the
 *   target node specified.  The daughters of the target node will become
 *   the daughters of the foot of the auxiliary tree.
 *
 * adjoinH tree target-node
 *
 *   similar to adjoin, but preserves the target node and makes it the root
 *   of tree
 *
 * adjoinF tree target-node
 *
 *   similar to adjoin, but preserves the target node and makes it the foot
 *   of tree.  It thus retains its status as parent of its children, placed
 *   in the appropriate spot in tree.
 *
 * coindex name_1 name_2 ... name_m
 *
 *   Puts a (Penn Treebank style) coindexation suffix of the form "-N" on
 *   each of nodes name_1 through name_m.  The value of N will be
 *   automatically generated in reference to the existing coindexations
 *   in the tree, so that there is never an accidental clash of
 *   indices across things that are not meant to be coindexed.
 * </pre>
 *
 * <h3>Comments: </h3>
 *
 * For all lines after the first line of the file, the
 * character % introduces a comment that extends to the end of the line.
 * All other intended uses of % must be escaped as \% .
 *
 * <h3>Syntax for trees to be inserted or adjoined:</h3>
 *
 * A tree to be adjoined in can be specified with LISP-like
 * parenthetical-bracketing tree syntax such as those used for the Penn
 * Treebank.  For example, for the NP "the dog" to be inserted you might
 * use the syntax:
 * <p>
 * <blockquote>
 * (NP (Det the) (N dog))
 * </blockquote>
 * <p>
 * That's all that there is for a tree to be inserted.  Auxiliary trees
 * (a la Tree Adjoining Grammar) must also have exactly one frontier node
 * ending in the character "@", which marks it as the "foot" node for
 * adjunction.  Final instances of the character "@" in terminal node labels
 * will be removed from the actual label of the tree.
 * <p>
 * For example, if you wanted to adjoin the adverb "breathlessly" into a
 * VP, you might specify the following auxiliary tree:
 * <p>
 * <blockquote>
 * (VP (Adv breathlessly) VP@ )
 * </blockquote>
 * <p>
 * All other instances of "@" in terminal nodes must be escaped (i.e.,
 * appear as \@); this escaping will be removed by Tsurgeon.
 * <p>
 * In addition, any node of a tree can be named (the same way as in
 * tregex), by appending =name to the node label.  That name can be
 * referred to by subsequent Tsurgeon operations triggered by the same
 * match.  All other instances of "=" in node labels must be escaped
 * (i.e., appear as \=); this escaping will be removed by Tsurgeon.  For
 * example, if you want to insert an NP trace somewhere and coindex it
 * with a node named "antecedent" you might say
 * <p>
 * <blockquote>
 * insert (NP (-NONE- *T*=trace)) node-location
 * coindex trace antecedent $
 * </blockquote>
 * <p>
 * <i>TO DO:</i> Fix the relabel operation to allow any node label without
 * || syntax.  Document adjoinH and adjoinF.  Provide a spliceIn(Above)
 * operation that lets you insert a node above a given node.
 *
 * @author Roger Levy
 * @version 21 July 2005.
 */
package edu.stanford.nlp.trees.tregex.tsurgeon;