// TregexPattern -- a Tgrep2-style utility for recognizing patterns in trees.
// Tregex/Tsurgeon Distribution
// Copyright (c) 2003-2008, 2017 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
//
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/tregex.html


package edu.stanford.nlp.trees.tregex;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.StringLabelFactory;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.ArrayMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.VariableStrings;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * A TregexPattern is a regular expression-like pattern that is designed to match node configurations within
 * a Tree where the nodes are labeled with symbols, rather than a character string.
 * The Tregex language follows but slightly expands
 * the tree pattern languages pioneered by {@code tgrep} and {@code tgrep2}. However, unlike these
 * tree pattern matching systems, but like Unix {@code grep}, there is no pre-indexing of the data to be searched.
 * Rather there is a linear scan through the trees where matches are sought.
 * As a result, matching is slower, but a TregexPattern can be applied
 * to an arbitrary set of trees at runtime in a processing pipeline without pre-indexing.
 *
 * TregexPattern instances can be matched against instances of the {@link Tree} class.
 * The {@link #main} method can be used to find matching nodes of a treebank from the command line.
 *
 * <h2>Getting Started</h2>
 *
 * Suppose we want to find all examples of subtrees where the label of
 * the root of the subtree starts with MW and it has a child node with the label IN.
 * That is, we want any subtree whose root is labeled MWV, MWN, etc. that has an IN child.
 *
 * The first thing to do is figure out what pattern to use.  Since we
 * want to match anything starting with MW, we use a regular expression pattern
 * for the top node and then also check for the child. The pattern is: {@code /^MW/ < IN}.
 *
 * We then create a pattern, find matches in a given tree, and process
 * those matches as follows:
 *
 * <pre>{@code
 *   // Create a reusable pattern object
 *   TregexPattern patternMW = TregexPattern.compile("/^MW/ < IN");
 *   // Run the pattern on one particular tree
 *   TregexMatcher matcher = patternMW.matcher(tree);
 *   // Iterate over all of the subtrees that matched
 *   while (matcher.findNextMatchingNode()) {
 *     Tree match = matcher.getMatch();
 *     // do what we want to do with the subtree
 *     match.pennPrint();
 *   }
 * }</pre>
 *
 *  <h2>Tregex pattern language</h2>
 *
 * The currently supported node-node relations and their symbols are:
 *
 * <table border = "1">
 * <tr><th>Symbol<th>Meaning
 * <tr><td>A &lt;&lt; B <td>A dominates B
 * <tr><td>A &gt;&gt; B <td>A is dominated by B
 * <tr><td>A &lt; B <td>A immediately dominates B
 * <tr><td>A &gt; B <td>A is immediately dominated by B
 * <tr><td>A &lt;&lt;&lt; B <td>A dominates B and B is a leaf
 * <tr><td>A &#36; B <td>A is a sister of B (and not equal to B)
 * <tr><td>A .. B <td>A precedes B
 * <tr><td>A . B <td>A immediately precedes B
 * <tr><td>A ,, B <td>A follows B
 * <tr><td>A , B <td>A immediately follows B
 * <tr><td>A &lt;&lt;, B <td>B is a leftmost descendant of A
 * <tr><td>A &lt;&lt;- B <td>B is a rightmost descendant of A
 * <tr><td>A &gt;&gt;, B <td>A is a leftmost descendant of B
 * <tr><td>A &gt;&gt;- B <td>A is a rightmost descendant of B
 * <tr><td>A &lt;, B <td>B is the first child of A
 * <tr><td>A &gt;, B <td>A is the first child of B
 * <tr><td>A &lt;- B <td>B is the last child of A
 * <tr><td>A &gt;- B <td>A is the last child of B
 * <tr><td>A &lt;` B <td>B is the last child of A
 * <tr><td>A &gt;` B <td>A is the last child of B
 * <tr><td>A &lt;i B <td>B is the ith child of A (i &gt; 0)
 * <tr><td>A &gt;i B <td>A is the ith child of B (i &gt; 0)
 * <tr><td>A &lt;-i B <td>B is the ith-to-last child of A (i &gt; 0)
 * <tr><td>A &gt;-i B <td>A is the ith-to-last child of B (i &gt; 0)
 * <tr><td>A &lt;&lt;&lt;i B <td>B is the ith leaf of A
 * <tr><td>A &lt;&lt;&lt;-i B <td>B is the ith-to-last leaf of A
 * <tr><td>A &lt;: B <td>B is the only child of A
 * <tr><td>A &gt;: B <td>A is the only child of B
 * <tr><td>A &lt;&lt;: B <td>A dominates B via an unbroken chain (length &gt; 0) of unary local trees.
 * <tr><td>A &gt;&gt;: B <td>A is dominated by B via an unbroken chain (length &gt; 0) of unary local trees.
 * <tr><td>A &#36;++ B <td>A is a left sister of B (same as &#36;.. for context-free trees)
 * <tr><td>A &#36;-- B <td>A is a right sister of B (same as &#36;,, for context-free trees)
 * <tr><td>A &#36;+ B <td>A is the immediate left sister of B (same as &#36;. for context-free trees)
 * <tr><td>A &#36;- B <td>A is the immediate right sister of B (same as &#36;, for context-free trees)
 * <tr><td>A &#36;.. B <td>A is a sister of B and precedes B
 * <tr><td>A &#36;,, B <td>A is a sister of B and follows B
 * <tr><td>A &#36;. B <td>A is a sister of B and immediately precedes B
 * <tr><td>A &#36;, B <td>A is a sister of B and immediately follows B
 * <tr><td>A &lt;+(C) B <td>A dominates B via an unbroken chain of (zero or more) nodes matching description C
 * <tr><td>A &gt;+(C) B <td>A is dominated by B via an unbroken chain of (zero or more) nodes matching description C
 * <tr><td>A .+(C) B <td>A precedes B via an unbroken chain of (zero or more) nodes matching description C
 * <tr><td>A ,+(C) B <td>A follows B via an unbroken chain of (zero or more) nodes matching description C
 * <tr><td>A &lt;&lt;&#35; B <td>B is a head of phrase A
 * <tr><td>A &gt;&gt;&#35; B <td>A is a head of phrase B
 * <tr><td>A &lt;&#35; B <td>B is the immediate head of phrase A
 * <tr><td>A &gt;&#35; B <td>A is the immediate head of phrase B
 * <tr><td>A == B <td>A and B are the same node
 * <tr><td>A &lt;= B <td>A and B are the same node or A is the parent of B
 * <tr><td>A : B<td>[this is a pattern-segmenting operator that places no constraints on the relationship between A and B]
 * <tr><td>A &lt;... { B ; C ; ... }<td>A has exactly B, C, etc as its subtree, with no other children.
 * </table>
 *
 * Label descriptions can be literal strings, which much match labels
 * exactly, or regular expressions in regular expression bars: /regex/.
 * Literal string matching proceeds as String equality.
 * In order to prevent ambiguity with other Tregex symbols, ASCII symbols (ASCII range characters that
 * are not letters or digits) are
 * not allowed in literal strings, and literal strings cannot begin with ASCII digits.
 * (That is literals can be standard "identifiers" matching
 * [a-zA-Z]([a-zA-Z0-9_-])* but also may include letters from other alphabets.)
 * If you want to use other symbols, you can do so by using a regular
 * expression instead of a literal string.
 * <br>
 * A disjunctive list of literal strings can be given separated by '|'.
 * The special string '__' (two underscores) can be used to match any
 * node.  (WARNING!!  Use of the '__' node description may seriously
 * slow down search.)
 * The special string '_ROOT_' matches only at the root of a tree.
 * If a label description is preceded by '@', the
 * label will match any node whose <em>basicCategory</em> matches the
 * description.  <b>NB: A single '@' thus scopes over a disjunction
 * specified by '|': @NP|VP means things with basic category NP or VP.
 * </b> The basicCategory is defined according to a Function
 * mapping Strings to Strings, as provided by
 * {@link edu.stanford.nlp.trees.AbstractTreebankLanguagePack#getBasicCategoryFunction()}.
 * Note that Label description regular expressions are matched as {@code find()},
 * as in Perl/tgrep, not as {@code matches()};
 * you need to use {@code ^} or {@code $} to constrain matches to
 * the ends of strings.
 * <br>
 * <b>Chains of relations have a special non-associative semantics:</b>
 * In a chain of relations A op B op C ...,
 * all relations are relative to the first node in
 * the chain. For example, {@code (S < VP < NP) } means
 * "an S over a VP and also over an NP".
 * Nodes can be grouped using parentheses '(' and ')'
 * as in {@code S < (NP $++ VP) } to match an S
 * over an NP, where the NP has a VP as a right sister.
 * So, if instead what you want is an S above a VP above an NP, you must write
 * "{@code S < (VP < NP)}".
 *
 * <h2>Notes on relations</h2>
 *
 * Node {@code B} "follows" node {@code A} if {@code B}
 * or one of its ancestors is a right sibling of {@code A} or one
 * of its ancestors.  Node {@code B} "immediately follows" node
 * {@code A} if {@code B} follows {@code A} and there
 * is no node {@code C} such that {@code B} follows
 * {@code C} and {@code C} follows {@code A}.
 * <br>
 * Node {@code A} dominates {@code B} through an unbroken
 * chain of unary local trees only if {@code A} is also
 * unary. {@code (A (B))} is a valid example that matches
 * {@code A <<: B}
 * <br>
 * When specifying that nodes are dominated via an unbroken chain of
 * nodes matching a description {@code C}, the description
 * {@code C} cannot be a full Tregex expression, but only an
 * expression specifying the name of the node.  Negation of this
 * description is allowed.
 * <br>
 * {@code ==} has the same precedence as the other relations, so the expression
 * {@code A << B == A << C} associates as
 * {@code (((A << B) == A) << C)}, not as
 * {@code ((A << B) == (A << C))}.  (Both expressions are
 * equivalent, of course, but this is just an example.)
 *
 * <h2>Boolean relational operators</h2>
 *
 * Relations can be combined using the {@code '&' } and {@code '|' } operators,
 * negated with the {@code '!' } operator, and made optional with the {@code '?' } operator.
 * Thus {@code (NP < NN | < NNS) } will match an NP node dominating either
 * an NN or an NNS.  {@code (NP > S & $++ VP) } matches an NP that
 * is both under an S and has a VP as a right sister.
 *
 * Expressions stop evaluating as soon as the result is known.  For
 * example, if the pattern is {@code NP=a | NNP=b} and the NP
 * matches, then variable {@code b} will not be assigned even if
 * there is an NNP in the tree.
 *
 * Relations can be grouped using brackets '[' and ']'.  So the expression
 *
 * <blockquote>
 * {@code NP [< NN | < NNS] & > S }
 * </blockquote>
 *
 * matches an NP that (1) dominates either an NN or an NNS, and (2) is under an S.  Without
 * brackets, &amp; takes precedence over |, and equivalent operators are
 * left-associative.  Also note that &amp; is the default combining operator if the
 * operator is omitted in a chain of relations, so that the two patterns are equivalent:
 *
 * <blockquote>
 * {@code (S < VP < NP) }<br>
 * {@code (S < VP & < NP) }
 * </blockquote>
 *
 * As another example, {@code (VP < VV | < NP % NP) }
 * can be written explicitly as {@code (VP [< VV | [< NP & % NP] ] ) }
 * <br>
 *
 * Relations can be negated with the '!' operator, in which case the
 * expression will match only if there is no node satisfying the relation.
 * For example {@code (NP !< NNP) } matches only NPs not dominating
 * an NNP.  Label descriptions can also be negated with {@code '!'}: 
 * {@code (NP < !NNP|NNS)} matches NPs dominating some node
 * that is not an NNP or an NNS.
 * <br>
 * Relations can be made optional with the '?' operator.  This way the
 * expression will match even if the optional relation is not satisfied.
 * This is useful when used together with node naming (see below).
 *
 * <h2>Basic Categories</h2>
 *
 * In order to consider only the "basic category" of a tree label,
 * i.e. to ignore functional tags or other annotations on the label,
 * prefix that node's description with the {@code @} symbol.  For example
 * {@code (@NP < @/NN.?/) }  This can only be used for individual nodes;
 * if you want all nodes to use the basic category, it would be more efficient
 * to use a {@link edu.stanford.nlp.trees.TreeNormalizer} to remove functional
 * tags before passing the tree to the TregexPattern.
 *
 * <h2>Segmenting patterns</h2>
 *
 * The ":" operator allows you to segment a pattern into two pieces.  This can simplify your pattern writing.  For example,
 * the pattern
 *
 * <blockquote>
 *   S : NP
 * </blockquote>
 *
 * matches only those S nodes in trees that also have an NP node.
 *
 * <h2>Naming nodes</h2>
 *
 * Nodes can be given names (a.k.a. handles) using '='.  A named node will be stored in a
 * map that maps names to nodes so that if a match is found, the node
 * corresponding to the named node can be extracted from the map.  For
 * example {@code (NP < NNP=name) } will match an NP dominating an NNP
 * and after a match is found, the map can be queried with the
 * name to retreived the matched node using {@link TregexMatcher#getNode(String o)}
 * with (String) argument "name" (<b>not</b> "=name").
 * Note that you are not allowed to name a node that is under the scope of a negation operator (the semantics would
 * be unclear, since you can't store a node that never gets matched to).
 * Trying to do so will cause a {@link TregexParseException} to be thrown. Named nodes
 * <b>can be put within the scope of an optionality operator</b>.
 *
 * Named nodes that refer back to previous named nodes need not have a node
 * description -- this is known as "backreferencing".  In this case, the expression
 * will match only when all instances of the same name get matched to the same tree node.
 * For example: the pattern
 *
 * <blockquote>
 * {@code (@NP <, (@NP $+ (/,/ $+ (@NP $+ /,/=comma))) <- =comma) }
 * </blockquote>
 *
 * matches only an NP dominating exactly the four node sequence
 * {@code NP , NP ,} -- the mother NP cannot have any other
 * daughters. Multiple backreferences are allowed.  If the node w/ no
 * node description does not refer to a previously named node, there
 * will be no error, the expression simply will not match anything.
 *
 * Another way to refer to previously named nodes is with the "link" symbol: '~'.
 * A link is like a backreference, except that instead of having to be <i>equal to</i> the
 * referred node, the current node only has to match the label of the referred to node.
 * A link cannot have a node description, i.e. the '~' symbol must immediately follow a
 * relation symbol.
 *
 * <h2>Customizing headship and basic categories</h2>
 *
 * The HeadFinder used to determine heads for the head relations {@code <#}, {@code >#}, {@code <<#},
 * and {@code >>#}, and also
 * the Function mapping from labels to Basic Category tags can be
 * chosen by using a {@link TregexPatternCompiler}.
 *
 * <h2>Variable Groups</h2>
 *
 * If you write a node description using a regular expression, you can assign its matching groups to variable names.
 * If more than one node has a group assigned to the same variable name, then matching will only occur when all such groups
 * capture the same string.  This is useful for enforcing coindexation constraints.  The syntax is
 *
 * <blockquote>
 * {@code / <regex-stuff> /#<group-number>%<variable-name>}
 * </blockquote>
 *
 * For example, the pattern (designed for Penn Treebank trees)
 *
 * <blockquote>
 * {@code @SBAR < /^WH.*-([0-9]+)$/#1%index << (__=empty < (/^-NONE-/ < /^\*T\*-([0-9]+)$/#1%index)) }
 * </blockquote>
 *
 * will match only such that the WH- node under the SBAR is coindexed with the trace node that gets the name {@code empty}.
 *
 * <h2>Current known bugs/shortcomings:</h2>
 *
 * <ul>
 *
 * <li> Tregex does not support disjunctions at the root level.  For
 * example, the pattern {@code A | B} will not work.
 *
 * <li> Using multiple variable strings in one regex may not
 * necessarily work.  For example, suppose the first two regex
 * patterns are {@code /(.*)/#1%foo} and
 * {@code /(.*)/#1%bar}.  You might then want to write a pattern
 * that matches the concatenation of these patterns,
 * {@code /(.*)(.*)/#1%foo#2%bar}, but that will not work.
 *
 * </ul>
 *
 * @author Galen Andrew
 * @author Roger Levy (rog@csli.stanford.edu)
 * @author Anna Rafferty (filter mode)
 * @author John Bauer (extensively tested and bugfixed)
 */
public abstract class TregexPattern implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TregexPattern.class);

  private boolean neg; // = false;
  private boolean opt; // = false;
  private String patternString;
  private Set<String> knownVariables;

  void negate() {
    neg = true;
    if (opt) {
      throw new IllegalStateException("Node cannot be both negated and optional.");
    }
  }

  void makeOptional() {
    opt = true;
    if (neg) {
      throw new IllegalStateException("Node cannot be both negated and optional.");
    }
  }

  private void prettyPrint(PrintWriter pw, int indent) {
    for (int i = 0; i < indent; i++) {
      pw.print("   ");
    }
    if (neg) {
      pw.print('!');
    }
    if (opt) {
      pw.print('?');
    }
    pw.println(localString());
    for (TregexPattern child : getChildren()) {
      child.prettyPrint(pw, indent + 1);
    }
  }

  // package private constructor
  TregexPattern() {
  }

  abstract List<TregexPattern> getChildren();

  abstract String localString();

  boolean isNegated() {
    return neg;
  }

  boolean isOptional() {
    return opt;
  }

  abstract TregexMatcher matcher(Tree root, Tree tree,
                                 IdentityHashMap<Tree, Tree> nodesToParents,
                                 Map<String, Tree> namesToNodes,
                                 VariableStrings variableStrings,
                                 HeadFinder headFinder);

  /**
   * Get a {@link TregexMatcher} for this pattern on this tree.
   *
   * @param t a tree to match on
   * @return a TregexMatcher
   */
  public TregexMatcher matcher(Tree t) {
    // In the assumption that there will usually be very few names in
    // the pattern, we use an ArrayMap instead of a hash map
    // TODO: it would be even more efficient if we set this to be exactly the right size
    return matcher(t, t, null, ArrayMap.newArrayMap(), new VariableStrings(), null);
  }

  /**
   * Get a {@link TregexMatcher} for this pattern on this tree.  Any Relations which use heads of trees should use the provided HeadFinder.
   *
   * @param t a tree to match on
   * @param headFinder a HeadFinder to use when matching
   * @return a TregexMatcher
   */
  public TregexMatcher matcher(Tree t, HeadFinder headFinder) {
    return matcher(t, t, null, ArrayMap.newArrayMap(), new VariableStrings(), headFinder);
  }

  /**
   * Creates a pattern from the given string using the default HeadFinder and
   * BasicCategoryFunction.  If you want to use a different HeadFinder or
   * BasicCategoryFunction, use a {@link TregexPatternCompiler} object.
   *
   * @param tregex the pattern string
   * @return a TregexPattern for the string.
   * @throws TregexParseException if the string does not parse
   */
  public static TregexPattern compile(String tregex) {
    return TregexPatternCompiler.defaultCompiler.compile(tregex);
  }

  /**
   * Creates a pattern from the given string using the default HeadFinder and
   * BasicCategoryFunction.  If you want to use a different HeadFinder or
   * BasicCategoryFunction, use a {@link TregexPatternCompiler} object.
   * Rather than throwing an exception when the string does not parse,
   * simply returns null.
   *
   * @param tregex the pattern string
   * @param verbose whether to log errors when the string doesn't parse
   * @return a TregexPattern for the string, or null if the string does not parse.
   */
  public static TregexPattern safeCompile(String tregex, boolean verbose) {
    TregexPattern result = null;
    try {
      result = TregexPatternCompiler.defaultCompiler.compile(tregex);
    } catch (TregexParseException ex) {
      if (verbose) {
        log.info("Could not parse " + tregex + ':');
        log.info(ex);
      }
    }
    return result;
  }

  public String pattern() {
    return patternString;
  }

  /** Only used by the TregexPatternCompiler to set the pattern. Pseudo-final. */
  void setPatternString(String patternString) {
    this.patternString = patternString;
  }

  /** Only used by the TregexPatternCompiler to track the known variables in the tregex (and only at the root). Pseudo-final. */
  void setKnownVariables(Set<String> knownVariables) {
    this.knownVariables = knownVariables;
  }

  public Set<String> knownVariables() {
    return Collections.unmodifiableSet(knownVariables);
  }

  /**
   * @return A single-line string representation of the pattern
   */
  @Override
  public abstract String toString();

  /**
   * Print a multi-line representation
   * of the pattern illustrating it's syntax.
   */
  public void prettyPrint(PrintWriter pw) {
    prettyPrint(pw, 0);
  }

  /**
   * Print a multi-line representation
   * of the pattern illustrating it's syntax.
   */
  public void prettyPrint(PrintStream ps) {
    prettyPrint(new PrintWriter(new OutputStreamWriter(ps), true));
  }

  /**
   * Print a multi-line representation of the pattern illustrating
   * it's syntax to System.out.
   */
  public void prettyPrint() {
    prettyPrint(System.out);
  }


  private static final Pattern codePattern = Pattern.compile("([0-9]+):([0-9]+)");

  private static void extractSubtrees(List<String> codeStrings, String treeFile) {
    List<Pair<Integer,Integer>> codes = new ArrayList<>();
    for(String s : codeStrings) {
      Matcher m = codePattern.matcher(s);
      if(m.matches())
        codes.add(new Pair<>(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
      else
        throw new RuntimeException("Error: illegal node code " + s);
    }
    TreeReaderFactory trf = new TRegexTreeReaderFactory();
    MemoryTreebank treebank = new MemoryTreebank(trf);
    treebank.loadPath(treeFile,null, true);
    for (Pair<Integer,Integer> code : codes) {
      Tree t = treebank.get(code.first()-1);
      t.getNodeNumber(code.second()).pennPrint();
    }
  }

  /**
   * Prints out all matches of a tree pattern on each tree in the path. Usage:
   *
   * {@code
   * java edu.stanford.nlp.trees.tregex.TregexPattern [[-TCwfosnu] [-filter] [-h <node-name>]]* pattern filepath
   * }
   *
   *
   * Arguments:
   *
   * <ul>
   * <li>{@code pattern}: the tree
   * pattern which optionally names some set of nodes (i.e., gives it the "handle") {@code =name} (for some arbitrary
   * string "name")
   * <li> {@code filepath}: the path to files with trees. If this is a directory, there will be recursive descent and the pattern will be run on all files beneath the specified directory.
   * </ul>
   *
   * Options:
   *
   * <ul>
   * <li> {@code -C} suppresses printing of matches, so only the
   * number of matches is printed.
   * <li> {@code -w} causes ONLY the whole of a tree that matches to be printed.
   * <li> {@code -W} causes the whole of a tree that matches to be printed ALSO.
   * <li> {@code -f} causes the filename to be printed.
   * <li> {@code -i <filename>} causes the pattern to be matched to be read from {@code <filename>} rather than the command line.  Don't specify a pattern when this option is used.
   * <li> {@code -o} Specifies that each tree node can be reported only once as the root of a match (by default a node will
   * be printed once for every <em>way</em> the pattern matches).
   * <li> {@code -s} causes trees to be printed all on one line (by default they are pretty printed).
   * <li> {@code -n} causes the number of the tree in which the match was found to be
   * printed before every match.
   * <li> {@code -u} causes only the label of each matching node to be printed, not complete subtrees.
   * <li> {@code -t} causes only the yield (terminal words) of the selected node to be printed (or the yield of the whole tree, if the {@code -w} option is used).
   * <li> {@code -encoding <charset_encoding>} option allows specification of character encoding of trees..
   * <li> {@code -h <node-handle>} If a {@code -h} option is given, the root tree node will not be printed.  Instead,
   * for each {@code node-handle} specified, the node matched and given that handle will be printed.  Multiple nodes can be printed by using the
   * {@code -h} option multiple times on a single command line.
   * <li> {@code -hf <headfinder-class-name>} use the specified {@link HeadFinder} class to determine headship relations.
   * <li> {@code -hfArg <string>} pass a string argument in to the {@link HeadFinder} class's constructor.  {@code -hfArg} can be used multiple times to pass in multiple arguments.
   * <li> {@code -trf <TreeReaderFactory-class-name>} use the specified {@link TreeReaderFactory} class to read trees from files.
   * <li> {@code -e <extension>} Only attempt to read files with the given extension. If not provided, will attempt to read all files.</li>
   * <li> {@code -v} print every tree that contains no matches of the specified pattern, but print no matches to the pattern.
   *
   * <li> {@code -x} Instead of the matched subtree, print the matched subtree's identifying number as defined in <tt>tgrep2</tt>:a
   * unique identifier for the subtree and is in the form s:n, where s is an integer specifying
   * the sentence number in the corpus (starting with 1), and n is an integer giving the order
   * in which the node is encountered in a depth-first search starting with 1 at top node in the
   * sentence tree.
   *
   * <li> {@code -extract <tree-file>} extracts the subtree s:n specified by <tt>code</tt> from the specified <tt>tree-file</tt>.
   *     Overrides all other behavior of tregex.  Can't specify multiple encodings etc. yet.
   * <li> {@code -extractFile <code-file> <tree-file>} extracts every subtree specified by the subtree codes in
   *     {@code code-file}, which must appear exactly one per line, from the specified {@code tree-file}.
   *     Overrides all other behavior of tregex. Can't specify multiple encodings etc. yet.
   * <li> {@code -filter} causes this to act as a filter, reading tree input from stdin
   * <li> {@code -T} causes all trees to be printed as processed (for debugging purposes).  Otherwise only matching nodes are printed.
   * <li> {@code -macros <filename>} filename with macro substitutions to use.  file with tab separated lines original-tab-replacement
   * </ul>
   */
  public static void main(String[] args) throws IOException {
    Timing.startTime();

    StringBuilder treePrintFormats = new StringBuilder();
    String printNonMatchingTreesOption = "-v";
    String subtreeCodeOption = "-x";
    String extractSubtreesOption = "-extract";
    String extractSubtreesFileOption = "-extractFile";
    String inputFileOption = "-i";
    String headFinderOption = "-hf";
    String headFinderArgOption = "-hfArg";
    String trfOption = "-trf";
    String extensionOption = "-e";
    String extension = null;
    String headFinderClassName = null;
    String[] headFinderArgs = StringUtils.EMPTY_STRING_ARRAY;
    String treeReaderFactoryClassName = null;
    String printHandleOption = "-h";
    String markHandleOption = "-k";
    String encodingOption = "-encoding";
    String encoding = "UTF-8";
    String macroOption = "-macros";
    String macroFilename = "";
    String yieldOnly = "-t";
    String printAllTrees = "-T";
    String quietMode = "-C";
    String wholeTreeOnlyMode = "-w";
    String wholeTreeAlsoMode = "-W";
    String filenameOption = "-f";
    String oneMatchPerRootNodeMode = "-o";
    String reportTreeNumbers = "-n";
    String rootLabelOnly = "-u";
    String oneLine = "-s";
    String uniqueTrees = "-q";

    Map<String,Integer> flagMap = Generics.newHashMap();
    flagMap.put(extractSubtreesOption,2);
    flagMap.put(extractSubtreesFileOption,2);
    flagMap.put(subtreeCodeOption,0);
    flagMap.put(printNonMatchingTreesOption,0);
    flagMap.put(encodingOption,1);
    flagMap.put(inputFileOption,1);
    flagMap.put(printHandleOption,1);
    flagMap.put(markHandleOption,2);
    flagMap.put(headFinderOption,1);
    flagMap.put(headFinderArgOption,1);
    flagMap.put(trfOption,1);
    flagMap.put(extensionOption, 1);
    flagMap.put(macroOption, 1);
    flagMap.put(yieldOnly, 0);
    flagMap.put(quietMode, 0);
    flagMap.put(wholeTreeOnlyMode, 0);
    flagMap.put(wholeTreeAlsoMode, 0);
    flagMap.put(printAllTrees, 0);
    flagMap.put(filenameOption, 0);
    flagMap.put(oneMatchPerRootNodeMode, 0);
    flagMap.put(reportTreeNumbers, 0);
    flagMap.put(rootLabelOnly, 0);
    flagMap.put(oneLine, 0);
    flagMap.put(uniqueTrees, 0);
    Map<String, String[]> argsMap = StringUtils.argsToMap(args, flagMap);
    args = argsMap.get(null);

    if (argsMap.containsKey(encodingOption)) {
      encoding = argsMap.get(encodingOption)[0];
      log.info("Encoding set to " + encoding);
    }
    PrintWriter errPW = new PrintWriter(new OutputStreamWriter(System.err, encoding), true);

    if (argsMap.containsKey(extractSubtreesOption)) {
      List<String> subTreeStrings = Collections.singletonList(argsMap.get(extractSubtreesOption)[0]);
      extractSubtrees(subTreeStrings,argsMap.get(extractSubtreesOption)[1]);
      return;
    }
    if (argsMap.containsKey(extractSubtreesFileOption)) {
      List<String> subTreeStrings = Arrays.asList(IOUtils.slurpFile(argsMap.get(extractSubtreesFileOption)[0]).split("\n|\r|\n\r"));
      extractSubtrees(subTreeStrings,argsMap.get(extractSubtreesFileOption)[0]);
      return;
    }

    if (args.length < 1) {
      errPW.println("Usage: java edu.stanford.nlp.trees.tregex.TregexPattern [-T] [-C] [-w] [-W] [-f] [-o] [-n] [-s] [-filter]  [-hf class] [-trf class] [-h handle]* [-e ext] pattern [filepath]");
      return;
    }
    String matchString = args[0];

    if (argsMap.containsKey(macroOption)) {
      macroFilename = argsMap.get(macroOption)[0];
    }
    if (argsMap.containsKey(headFinderOption)) {
      headFinderClassName = argsMap.get(headFinderOption)[0];
      errPW.println("Using head finder " + headFinderClassName + "...");
    }
    if(argsMap.containsKey(headFinderArgOption)) {
      headFinderArgs = argsMap.get(headFinderArgOption);
    }
    if (argsMap.containsKey(trfOption)) {
      treeReaderFactoryClassName = argsMap.get(trfOption)[0];
      errPW.println("Using tree reader factory " + treeReaderFactoryClassName + "...");
    }
    if (argsMap.containsKey(extensionOption)) {
      extension = argsMap.get(extensionOption)[0];
    }
    if (argsMap.containsKey(printAllTrees)) {
      TRegexTreeVisitor.printTree = true;
    }
    if (argsMap.containsKey(inputFileOption)) {
      String inputFile = argsMap.get(inputFileOption)[0];
      matchString = IOUtils.slurpFile(inputFile, encoding);
      String[] newArgs = new String[args.length+1];
      System.arraycopy(args,0,newArgs,1,args.length);
      args = newArgs;
    }
    if (argsMap.containsKey(quietMode)) {
      TRegexTreeVisitor.printMatches = false;
      TRegexTreeVisitor.printNumMatchesToStdOut = true ;

    }
    if (argsMap.containsKey(printNonMatchingTreesOption)) {
      TRegexTreeVisitor.printNonMatchingTrees = true;
    }
    if (argsMap.containsKey(subtreeCodeOption)) {
      TRegexTreeVisitor.printSubtreeCode = true;
      TRegexTreeVisitor.printMatches = false;
    }
    if (argsMap.containsKey(wholeTreeOnlyMode)) {
      TRegexTreeVisitor.printWholeTreeOnly = true;
    }
    if (argsMap.containsKey(wholeTreeAlsoMode)) {
      TRegexTreeVisitor.printWholeTreeAlso = true;
    }
    if (argsMap.containsKey(filenameOption)) {
      TRegexTreeVisitor.printFilename = true;
    }
    if(argsMap.containsKey(oneMatchPerRootNodeMode))
      TRegexTreeVisitor.oneMatchPerRootNode = true;
    if(argsMap.containsKey(reportTreeNumbers))
      TRegexTreeVisitor.reportTreeNumbers = true;
    if (argsMap.containsKey(rootLabelOnly)) {
      treePrintFormats.append(TreePrint.rootLabelOnlyFormat).append(',');
    } else if (argsMap.containsKey(oneLine)) { // display short form
      treePrintFormats.append("oneline,");
    } else if (argsMap.containsKey(yieldOnly)) {
      treePrintFormats.append("words,");
    } else {
      treePrintFormats.append("penn,");
    }
    if (argsMap.containsKey(uniqueTrees)) {
      TRegexTreeVisitor.printOnlyUniqueTrees = true;
    }

    HeadFinder hf = new CollinsHeadFinder();
    if(headFinderClassName != null) {
      Class[] hfArgClasses = new Class[headFinderArgs.length];
      for (int i = 0; i < hfArgClasses.length; i++) {
        hfArgClasses[i] = String.class;
      }
      try {
        hf = (HeadFinder) Class.forName(headFinderClassName).getConstructor(hfArgClasses).newInstance((Object[]) headFinderArgs); // cast to Object[] necessary to avoid varargs-related warning.
      }
      catch(Exception e) { throw new RuntimeException("Error occurred while constructing HeadFinder: " + e); }
    }

    TRegexTreeVisitor.tp = new TreePrint(treePrintFormats.toString(), new PennTreebankLanguagePack());

    try {
      //TreePattern p = TreePattern.compile("/^S/ > S=dt $++ '' $-- ``");
      TregexPatternCompiler tpc = new TregexPatternCompiler(hf);
      Macros.addAllMacros(tpc, macroFilename, encoding);
      TregexPattern p = tpc.compile(matchString);
      errPW.println("Pattern string:\n" + p.pattern());
      errPW.println("Parsed representation:");
      p.prettyPrint(errPW);

      String[] handles = argsMap.get(printHandleOption);
      if (argsMap.containsKey("-filter")) {
        TreeReaderFactory trf = getTreeReaderFactory(treeReaderFactoryClassName);
        treebank = new MemoryTreebank(trf, encoding);//has to be in memory since we're not storing it on disk
        //read from stdin
        Reader reader = new BufferedReader(new InputStreamReader(System.in, encoding));
        ((MemoryTreebank) treebank).load(reader);
        reader.close();
      } else if (args.length == 1) {
        errPW.println("using default tree");
        TreeReader r = new PennTreeReader(new StringReader("(VP (VP (VBZ Try) (NP (NP (DT this) (NN wine)) (CC and) (NP (DT these) (NNS snails)))) (PUNCT .))"), new LabeledScoredTreeFactory(new StringLabelFactory()));
        Tree t = r.readTree();
        treebank = new MemoryTreebank();
        treebank.add(t);
      } else {
        int last = args.length - 1;
        errPW.println("Reading trees from file(s) " + args[last]);
        TreeReaderFactory trf = getTreeReaderFactory(treeReaderFactoryClassName);

        treebank = new DiskTreebank(trf, encoding);
        treebank.loadPath(args[last], extension, true);
      }
      TRegexTreeVisitor vis = new TRegexTreeVisitor(p, handles, encoding);

      treebank.apply(vis);
      Timing.endTime();
      if (TRegexTreeVisitor.printMatches) {
        errPW.println("There were " + vis.numMatches() + " matches in total.");
      }
      if (TRegexTreeVisitor.printNumMatchesToStdOut) {
        System.out.println(vis.numMatches());
      }
    } catch (IOException e) {
      log.warn(e);
    } catch (TregexParseException e) {
      errPW.println("Error parsing expression: " + args[0]);
      errPW.println("Parse exception: " + e);
    }
  }

  private static TreeReaderFactory getTreeReaderFactory(String treeReaderFactoryClassName) {
    TreeReaderFactory trf = new TRegexTreeReaderFactory();
    if (treeReaderFactoryClassName != null) {
      try {
        trf = (TreeReaderFactory) Class.forName(treeReaderFactoryClassName).getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("Error occurred while constructing TreeReaderFactory: " + e);
      }
    }
    return trf;
  }

  private static Treebank treebank; // used by main method, must be accessible

  private static final long serialVersionUID = 5060298043763944913L;


  // not thread-safe, but only used by TregexPattern's main method
  private static class TRegexTreeVisitor implements TreeVisitor {

    private static boolean printNumMatchesToStdOut = false;
    static boolean printNonMatchingTrees = false;
    static boolean printSubtreeCode = false;
    static boolean printTree = false;
    static boolean printWholeTreeOnly = false;
    static boolean printWholeTreeAlso = false;
    static boolean printMatches = true;
    static boolean printFilename = false;
    static boolean oneMatchPerRootNode = false;
    static boolean reportTreeNumbers = false;
    static boolean printOnlyUniqueTrees = false;

    static TreePrint tp;
    private PrintWriter pw;

    int treeNumber = 0;

    private final TregexPattern p;
    String[] handles;
    int numMatches;

    TRegexTreeVisitor(TregexPattern p, String[] handles, String encoding) {
      this.p = p;
      this.handles = handles;
      try {
        pw = new PrintWriter(new OutputStreamWriter(System.out, encoding),true);
      }
      catch (UnsupportedEncodingException e) {
        log.info("Error -- encoding " + encoding + " is unsupported.  Using platform default PrintWriter instead.");
        pw = new PrintWriter(System.out,true);
      }
    }

    // todo: add an option to only print each tree once, regardless.  Most useful in conjunction with -w
    @Override
    public void visitTree(Tree t) {
      treeNumber++;
      if (printTree) {
        pw.print(treeNumber+":");
        pw.println("Next tree read:");
        tp.printTree(t,pw);
      }
      TregexMatcher match = p.matcher(t);
      if(printNonMatchingTrees) {
        if(match.find())
          numMatches++;
        else
          tp.printTree(t,pw);
        return;
      }
      Tree lastMatchingRootNode = null;
      boolean printedTree = false;
      while (match.find()) {
        if(oneMatchPerRootNode) {
          if(lastMatchingRootNode == match.getMatch())
            continue;
          else
            lastMatchingRootNode = match.getMatch();
        }
        numMatches++;
        if (printFilename && treebank instanceof DiskTreebank) {
          DiskTreebank dtb = (DiskTreebank) treebank;
          pw.print("# ");
          pw.println(dtb.getCurrentFilename());
        }
        if(printSubtreeCode) {
          pw.print(treeNumber);
          pw.print(':');
          pw.println(match.getMatch().nodeNumber(t));
        }
        if (printMatches) {
          if(reportTreeNumbers) {
            pw.print(treeNumber);
            pw.print(": ");
          }
          if (printTree) {
            pw.println("Found a full match:");
          }
          if (printWholeTreeOnly) {
            tp.printTree(t,pw);
          } else if (handles != null) {
            if (printWholeTreeAlso && !printedTree && !printTree) {
              pw.println("Matching tree:");
              tp.printTree(t,pw);
              printedTree = true;
            }
            if (printTree) {
              pw.println("Here's the node you were interested in:");
            }
            for (String handle : handles) {
              Tree labeledNode = match.getNode(handle);
              if (labeledNode == null) {
                log.info("Error!!  There is no matched node \"" + handle + "\"!  Did you specify such a label in the pattern?");
              } else {
                tp.printTree(labeledNode,pw);
              }
            }
          } else {
            if (printWholeTreeAlso && !printedTree && !printTree) {
              pw.println("Matching tree:");
              tp.printTree(t,pw);
              printedTree = true;
            }
            tp.printTree(match.getMatch(),pw);
          }
          // pw.println();  // TreePrint already puts a blank line in
        } // end if (printMatches)

        if (printOnlyUniqueTrees) {
          break;
        }
      } // end while match.find()
    } // end visitTree

    public int numMatches() {
      return numMatches;
    }

  } // end class TRegexTreeVisitor


  public static class TRegexTreeReaderFactory implements TreeReaderFactory {

    private final TreeNormalizer tn;

    public TRegexTreeReaderFactory() {
      this(new TreeNormalizer() {

        private static final long serialVersionUID = -2998972954089638189L;

        @Override
        public String normalizeNonterminal(String str) {
          if (str == null) {
            return "";
          } else {
            return str;
          }
        }
      });
    }

    public TRegexTreeReaderFactory(TreeNormalizer tn) {
      this.tn = tn;
    }

    @Override
    public TreeReader newTreeReader(Reader in) {
      return new PennTreeReader(new BufferedReader(in), new LabeledScoredTreeFactory(), tn);
    }

  } // end class TRegexTreeReaderFactory

}
