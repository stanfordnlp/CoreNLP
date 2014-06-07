// Tsurgeon
// Copyright (c) 2004-2010 The Board of Trustees of
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
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: parser-user@lists.stanford.edu
//    Licensing: parser-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/tregex.shtml

package edu.stanford.nlp.trees.tregex.tsurgeon;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.Macros;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;

/** Tsurgeon provides a way of editing trees based on a set of operations that
 *  are applied to tree locations matching a tregex pattern.
 *  A simple example from the command-line:
 *  <blockquote>
 * java edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon -treeFile atree
 *        exciseNP renameVerb
 * </blockquote>
 * The file {@code atree} has Penn Treebank (S-expression) format trees.
 * The other (here, two) files have Tsurgeon operations.  These consist of
 * a list of pairs of a tregex expression on one or more
 * lines, a blank line, and then some number of lines of Tsurgeon operations and then
 * another blank line.
 * <p>
 * Tsurgeon uses the Tregex engine to match tree patterns on trees;
 * for more information on Tregex's tree-matching functionality,
 * syntax, and semantics, please see the documentation for the
 * {@link TregexPattern} class.
 * <p>

 * If you want to use Tsurgeon as an API, the relevant method is
 * {@link #processPattern}.  You will also need to look at the
 * {@link TsurgeonPattern} class and the {@link Tsurgeon#parseOperation} method.
 * <p>
 * Here's the simplest form of invocation on a single Tree:
 * <pre>
 * Tree t = Tree.valueOf("(ROOT (S (NP (NP (NNP Bank)) (PP (IN of) (NP (NNP America)))) (VP (VBD called)) (. .)))");
 * TregexPattern pat = TregexPattern.compile("NP &lt;1 (NP &lt;&lt; Bank) &lt;2 PP=remove");
 * TsurgeonPattern surgery = Tsurgeon.parseOperation("excise remove remove");
 * Tsurgeon.processPattern(pat, surgery, t).pennPrint();
 * </pre>
 * <p>
 * Here is another sample invocation:
 * <pre>
 * TregexPattern matchPattern = TregexPattern.compile("SQ=sq &lt; (/^WH/ $++ VP)");
 * List&lt;TsurgeonPattern&gt; ps = new ArrayList&lt;TsurgeonPattern&gt;();
 *
 * TsurgeonPattern p = Tsurgeon.parseOperation("relabel sq S");
 *
 * ps.add(p);
 *
 * Treebank lTrees;
 * List&lt;Tree&gt; result = Tsurgeon.processPatternOnTrees(matchPattern,Tsurgeon.collectOperations(ps),lTrees);
 * </pre>
 * <p>
 * <i>Note:</i> If you want to apply multiple surgery patterns, you
 * will not want to call processPatternOnTrees, for each individual
 * pattern.  Rather, you should either call processPatternsOnTree and
 * loop through the trees yourself, or, as above, use
 * <code>collectOperations</code> to collect all the surgery patterns
 * into one TsurgeonPattern, and then to call processPatternOnTrees.
 * Either of these latter methods is much faster.
 * </p><p>
 * The parser also has the ability to collect multiple
 * TsurgeonPatterns into one pattern by itself by enclosing each
 * pattern in <code>[ ... ]</code>.  For example,
 * <br>
 * <code>Tsurgeon.parseOperation("[relabel foo BAR] [prune bar]")</code>
 * </p><p>
 * For more information on using Tsurgeon from the command line,
 * see the {@link #main} method and the package Javadoc.
 *
 * @author Roger Levy
 */
public class Tsurgeon {

  private static final boolean DEBUG = false;
  static boolean verbose; // = false;

  private static final Pattern emptyLinePattern = Pattern.compile("^\\s*$");
  private static final String commentIntroducingCharacter = "%";
  private static final Pattern commentPattern = Pattern.compile("(?<!\\\\)%.*$");
  private static final Pattern escapedCommentCharacterPattern = Pattern.compile("\\\\" + commentIntroducingCharacter);

  private Tsurgeon() {} // not an instantiable class

  /** Usage: java edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon [-s] -treeFile file-with-trees [-po matching-pattern operation] operation-file-1 operation-file-2 ... operation-file-n
   *
   * <h4>Arguments:</h4>
   *
   * Each argument should be the name of a transformation file that contains a list of pattern
   * and transformation operation list pairs.  That is, it is a sequence of pairs of a
   * {@link TregexPattern} pattern on one or more lines, then a
   * blank line (empty or whitespace), then a list of transformation operations one per line
   * (as specified by <b>Legal operation syntax</b> below) to apply when the pattern is matched,
   * and then another blank line (empty or whitespace).
   * Note the need for blank lines: The code crashes if they are not present as separators
   * (although the blank line at the end of the file can be omitted).
   * The script file can include comment lines, either whole comment lines or
   * trailing comments introduced by %, which extend to the end of line.  A needed percent
   * mark can be escaped by a preceding backslash.
   * <p>
   * For example, if you want to excise an SBARQ node whenever it is the parent of an SQ node,
   * and relabel the SQ node to S, your transformation file would look like this:
   *
   * <blockquote>
   * <code>
   *    SBARQ=n1 &lt; SQ=n2<br>
   *    <br>
   *    excise n1 n1<br>
   *    relabel n2 S
   * </code>
   * </blockquote>
   *
   * </p><p>
   *
   * <h4>Options:</h4>
   * <ul>
   *   <li><code>-treeFile &#60;filename&#62;</code>  specify the name of the file that has the trees you want to transform.
   *   <li><code>-po &#60;matchPattern&#62; &#60;operation&#62;</code>  Apply a single operation to every tree using the specified match pattern and the specified operation.  Use this option
   *   when you want to quickly try the effect of one pattern/surgery combination, and are too lazy to write a transformation file.
   *   <li><code>-s</code> Print each output tree on one line (default is pretty-printing).
   *   <li><code>-m</code> For every tree that had a matching pattern, print "before" (prepended as "Operated on:") and "after" (prepended as "Result:").  Unoperated trees just pass through the transducer as usual.
   *   <li><code>-encoding X</code> Uses character set X for input and output of trees.
   *   <li><code>-macros &#60;filename&#62;</code> A file of macros to use on the tregex pattern.  Macros should be one per line, with original and replacement separated by tabs.
   *   <li><code>-hf &lt;headfinder-class-name&gt;</code> use the specified {@link HeadFinder} class to determine headship relations.
   *   <li><code>-hfArg &lt;string&gt;</code> pass a string argument in to the {@link HeadFinder} class's constructor.  <code>-hfArg</code> can be used multiple times to pass in multiple arguments.
   *   <li> <code>-trf &lt;TreeReaderFactory-class-name&gt;</code> use the specified {@link TreeReaderFactory} class to read trees from files.
   * </ul>
   *
   * <h4>Legal operation syntax:</h4>
   *
   * <ul>
   *
   * <li><code>delete &#60;name&#62;</code>  deletes the node and everything below it.
   *
   * <li><code>prune &#60;name&#62;</code>  Like delete, but if, after the pruning, the parent has no children anymore, the parent is pruned too.  Pruning continues to affect all ancestors until one is found with remaining children.  This may result in a null tree.
   *
   * <li><code>excise &#60;name1&#62; &#60;name2&#62;</code>
   *   The name1 node should either dominate or be the same as the name2 node.  This excises out everything from
   * name1 to name2.  All the children of name2 go into the parent of name1, where name1 was.
   *
   * <li><code>relabel &#60;name&#62; &#60;new-label&#62;</code> Relabels the node to have the new label. <br>
   * There are three possible forms: <br>
   * <code>relabel nodeX VP</code> - for changing a node label to an
   * alphanumeric string <br>
   * <code>relabel nodeX /''/</code> - for relabeling a node to
   * something that isn't a valid identifier without quoting <br>
   *
   * <code>relabel nodeX /^VB(.*)$/verb\\/$1/</code> - for regular
   * expression based relabeling. In this case, all matches of the
   * regular expression against the node label are replaced with the
   * replacement String.  This has the semantics of Java/Perl's
   * replaceAll: you may use capturing groups and put them in
   * replacements with $n. For example, if the pattern is /foo/bar/
   * and the node matched is "foo", the replaceAll semantics result in
   * "barbar".  If the pattern is /^foo(.*)$/bar$1/ and node matched is
   * "foofoo", relabel will result in "barfoo".  <br>
   *
   * When using the regex replacement method, you can also use the
   * sequences ={node} and %{var} in the replacement string to use
   * captured nodes or variable strings in the replacement string.
   * For example, if the Tregex pattern was "duck=bar" and the relabel
   * is /foo/={bar}/, "foofoo" will be replaced with "duckduck". <br>
   *
   * To concatenate two nodes named in the tregex pattern, for
   * example, you can use the pattern /^.*$/={foo}={bar}/.  Note that
   * the ^.*$ is necessary to make sure the regex pattern only matches
   * and replaces once on the entire node name. <br>
   *
   * To get an "=" or a "%" in the replacement, using \ escaping.
   * Also, as in the example you can escape a slash in the middle of
   * the second and third forms with \\/ and \\\\. <br>
   *
   * <li><code>insert &#60;name&#62; &#60;position&#62;</code> or <code>insert &lt;tree&gt; &#60;position&#62;</code>
   *   inserts the named node or tree into the position specified.
   *
   * <li><code>move &#60;name&#62; &#60;position&#62;</code> moves the named node into the specified position.
   * <p>Right now the  only ways to specify position are:
   * <p>
   *      <code>$+ &#60;name&#62;</code>     the left sister of the named node<br>
   *      <code>$- &#60;name&#62;</code>     the right sister of the named node<br>
   *      <code>&gt;i &#60;name&#62;</code> the i_th daughter of the named node<br>
   *      <code>&gt;-i &#60;name&#62;</code> the i_th daughter, counting from the right, of the named node.
   *
   * <li><code>replace &#60;name1&#62; &#60;name2&#62;</code>
   *     deletes name1 and inserts a copy of name2 in its place.
   *
   * <li><code>replace &#60;name&#62; &#60;tree&#62; &#60;tree2&#62;...</code>
   *     deletes name and inserts the new tree(s) in its place.  If
   *     more than one replacement tree is given, each of the new
   *     subtrees will be added in order where the old tree was.
   *     Multiple subtrees at the root is an illegal operation and
   *     will throw an exception.
   *
   * <li>{@code createSubtree <new-label> <name1> [<name2>]}
   *     Create a subtree out of all the nodes from {@code <name1>} through
   *     {@code <name2>} and puts the new subtree where
   *     that span used to be.  To limit the operation to just one
   *     node, elide {@code <name2>}.
   *
   * <li><code>adjoin &#60;auxiliary_tree&#62; &lt;name&gt;</code> Adjoins the specified auxiliary tree into the named node.
   *     The daughters of the target node will become the daughters of the foot of the auxiliary tree.
   * <li><code>adjoinH &#60;auxiliary_tree&#62; &lt;name&gt;</code> Similar to adjoin, but preserves the target node
   *     and makes it the root of &lt;tree&gt;. (It is still accessible as <code>name</code>.  The root of the
   *     auxiliary tree is ignored.)
   *
   * <li> <code>adjoinF &#60;auxiliary_tree&#62; &lt;name&gt;</code></dt>  Similar to adjoin,
   *     but preserves the target node and makes it the foot of &lt;tree&gt;.
   *     (It is still accessible as <code>name</code>, and retains its status as parent of its children.
   *     The root of the auxiliary tree is ignored.)
   *
   * <li> <dt><code>coindex &#60;name1&#62; &#60;name2&#62; ... &#60;nameM&#62; </code> Puts a (Penn Treebank style)
   *     coindexation suffix of the form "-N" on each of nodes name_1 through name_m.  The value of N will be
   *     automatically generated in reference to the existing coindexations in the tree, so that there is never
   *     an accidental clash of indices across things that are not meant to be coindexed.
   *
   * </ul>
   *
   * <p>
   * In the context of <code>adjoin</code>, <code>adjoinH</code>, and
   * <code>adjoinF</code>, an auxiliary tree is a tree in Penn
   * Treebank format with <code>@</code> on exactly one of the leaves
   * denoting the foot of the tree.  The operations which use the foot
   * use the labeled node.  For example: <br>
   * Tsurgeon: <code>adjoin (FOO (BAR@)) foo</code> <br>
   * Tregex: <code>B=foo</code> <br>
   * Input: <code>(A (B 1 2))</code>
   * Output: <code>(A (FOO (BAR 1 2)))</code>
   * </p><p>
   * Tsurgeon applies the same operation to the same tree for as long
   * as the given tregex operation matches.  This means that infinite
   * loops are very easy to cause.  One common situation where this comes up
   * is with an insert operation will repeats infinitely many times
   * unless you add an expression to the tregex that matches against
   * the inserted pattern.  For example, this pattern will infinite loop:
   *
   * <blockquote>
   * <code>
   *   TregexPattern tregex = TregexPattern.compile("S=node &lt;&lt; NP"); <br>
   *   TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("insert (NP foo) &gt;-1 node");
   * </code>
   * </blockquote>
   *
   * This pattern, though, will terminate:
   *
   * <blockquote>
   * <code>
   *   TregexPattern tregex = TregexPattern.compile("S=node &lt;&lt; NP !&lt;&lt; foo"); <br>
   *   TsurgeonPattern tsurgeon = Tsurgeon.parseOperation("insert (NP foo) &gt;-1 node");
   * </code>
   * </blockquote>
   *
   * </p>
   * <p>

   * Tsurgeon has (very) limited support for conditional statements.
   * If a pattern is prefaced with
   * <code>if exists &lt;name&gt;</code>,
   * the rest of the pattern will only execute if
   * the named node was found in the corresponding TregexMatcher.
   *
   * </p>
   *
   * @param args a list of names of files each of which contains a single tregex matching pattern plus a list, one per line,
   *        of transformation operations to apply to the matched pattern.
   * @throws Exception If an I/O or pattern syntax error
   */
  public static void main(String[] args) throws Exception {
    String headFinderClassName = null;
    String headFinderOption = "-hf";
    String[] headFinderArgs = null;
    String headFinderArgOption = "-hfArg";
    String encoding = "UTF-8";
    String encodingOption = "-encoding";
    if(args.length==0) {
      System.err.println("Usage: java edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon [-s] -treeFile <file-with-trees> [-po <matching-pattern> <operation>] <operation-file-1> <operation-file-2> ... <operation-file-n>");
      System.exit(0);
    }
    String treePrintFormats;
    String singleLineOption = "-s";
    String verboseOption = "-v";
    String matchedOption = "-m"; // if set, then print original form of trees that are matched & thus operated on
    String patternOperationOption = "-po";
    String treeFileOption = "-treeFile";
    String trfOption = "-trf";
    String macroOption = "-macros";
    String macroFilename = "";
    Map<String,Integer> flagMap = Generics.newHashMap();
    flagMap.put(patternOperationOption,2);
    flagMap.put(treeFileOption,1);
    flagMap.put(trfOption,1);
    flagMap.put(singleLineOption,0);
    flagMap.put(encodingOption,1);
    flagMap.put(headFinderOption,1);
    flagMap.put(macroOption, 1);
    Map<String,String[]> argsMap = StringUtils.argsToMap(args,flagMap);
    args = argsMap.get(null);

    if(argsMap.containsKey(headFinderOption)) headFinderClassName = argsMap.get(headFinderOption)[0];
    if(argsMap.containsKey(headFinderArgOption)) headFinderArgs = argsMap.get(headFinderArgOption);
    if(argsMap.containsKey(verboseOption))      verbose = true;
    if(argsMap.containsKey(singleLineOption))   treePrintFormats = "oneline,";   else treePrintFormats = "penn,";
    if(argsMap.containsKey(encodingOption)) encoding = argsMap.get(encodingOption)[0];
    if(argsMap.containsKey(macroOption)) macroFilename = argsMap.get(macroOption)[0];

    TreePrint tp = new TreePrint(treePrintFormats, new PennTreebankLanguagePack());
    PrintWriter pwOut = new PrintWriter(new OutputStreamWriter(System.out,encoding), true);

    TreeReaderFactory trf;
    if (argsMap.containsKey(trfOption)) {
      String trfClass = argsMap.get(trfOption)[0];
      trf = ReflectionLoading.loadByReflection(trfClass);
    } else {
      trf = new TregexPattern.TRegexTreeReaderFactory();
    }

    Treebank trees = new DiskTreebank(trf, encoding);
    if (argsMap.containsKey(treeFileOption)) {
      trees.loadPath(argsMap.get(treeFileOption)[0]);
    }
    List<Pair<TregexPattern,TsurgeonPattern>> ops = new ArrayList<Pair<TregexPattern,TsurgeonPattern>>();

    TregexPatternCompiler compiler;
    if (headFinderClassName == null) {
      compiler = new TregexPatternCompiler();
    } else {
      HeadFinder hf;
      if (headFinderArgs == null) {
        hf = ReflectionLoading.loadByReflection(headFinderClassName);
      } else {
        hf = ReflectionLoading.loadByReflection(headFinderClassName, (Object[]) headFinderArgs);
      }
      compiler = new TregexPatternCompiler(hf);
    }
    Macros.addAllMacros(compiler, macroFilename, encoding);
    if (argsMap.containsKey(patternOperationOption)) {
      TregexPattern matchPattern = compiler.compile(argsMap.get(patternOperationOption)[0]);
      TsurgeonPattern p = parseOperation(argsMap.get(patternOperationOption)[1]);
      ops.add(new Pair<TregexPattern,TsurgeonPattern>(matchPattern,p));
    } else {
      for (String arg : args) {
        List<Pair<TregexPattern,TsurgeonPattern>> pairs = getOperationsFromFile(arg, encoding, compiler);
        for (Pair<TregexPattern,TsurgeonPattern> pair : pairs) {
          if (verbose) {
            System.err.println(pair.second());
          }
          ops.add(pair);
        }
      }
    }

    for (Tree t : trees ) {
      Tree original = t.deepCopy();
      Tree result = processPatternsOnTree(ops, t);
      if (argsMap.containsKey(matchedOption) && matchedOnTree) {
        pwOut.println("Operated on: ");
        displayTree(original,tp,pwOut);
        pwOut.println("Result: ");
      }
      displayTree(result,tp,pwOut);
    }
  }

  private static void displayTree(Tree t, TreePrint tp, PrintWriter pw) {
    if (t==null) {
      pw.println("null");
    } else {
      tp.printTree(t,pw);
    }
  }

  /**
   * Parses a tsurgeon script text input and compiles a tregex pattern and a list
   * of tsurgeon operations into a pair.
   *
   * @param reader Reader to read patterns from
   * @return A pair of a tregex and tsurgeon pattern read from a file, or <code>null</code>
   *    when the operations in the Reader have been exhausted
   * @throws IOException If any IO problem
   */
  public static Pair<TregexPattern, TsurgeonPattern> getOperationFromReader(BufferedReader reader, TregexPatternCompiler compiler) throws IOException {
    String patternString = getTregexPatternFromReader(reader);
    // System.err.println("Read tregex pattern: " + patternString);
    if ("".equals(patternString)) {
      return null;
    }
    TregexPattern matchPattern = compiler.compile(patternString);

    TsurgeonPattern collectedPattern = getTsurgeonOperationsFromReader(reader);
    return new Pair<TregexPattern,TsurgeonPattern>(matchPattern,collectedPattern);
  }

  /**
   * Assumes that we are at the beginning of a tsurgeon script file and gets the string for the
   * tregex pattern leading the file
   * @return tregex pattern string
   */
  public static String getTregexPatternFromReader(BufferedReader reader) throws IOException {
    StringBuilder matchString = new StringBuilder();
    for (String thisLine; (thisLine = reader.readLine()) != null; ) {
      if (matchString.length() > 0 && emptyLinePattern.matcher(thisLine).matches()) {
        // A blank line after getting some real content (not just comments or nothing)
        break;
      }
      Matcher m = commentPattern.matcher(thisLine);
      if (m.matches()) {
        // delete it
        thisLine = m.replaceFirst("");
      }
      if ( ! emptyLinePattern.matcher(thisLine).matches()) {
        matchString.append(thisLine);
      }
    }
    return matchString.toString();
  }

  /**
   * Assumes the given reader has only tsurgeon operations (not a tregex pattern), and parses
   * these out, collecting them into one operation.  Stops on a whitespace line.
   *
   * @throws IOException
   */
  public static TsurgeonPattern getTsurgeonOperationsFromReader(BufferedReader reader) throws IOException {
    List<TsurgeonPattern> operations = new ArrayList<TsurgeonPattern>();
    for (String thisLine; (thisLine = reader.readLine()) != null; ) {
      if (emptyLinePattern.matcher(thisLine).matches()) {
        break;
      }
      thisLine = removeComments(thisLine);
      if (emptyLinePattern.matcher(thisLine).matches()) {
        continue;
      }
      // System.err.println("Read tsurgeon op: " + thisLine);
      operations.add(parseOperation(thisLine));
    }
    return collectOperations(operations);
  }


  private static String removeComments(String line) {
    Matcher m = commentPattern.matcher(line);
    line = m.replaceFirst("");
    Matcher m1 = escapedCommentCharacterPattern.matcher(line);
    line = m1.replaceAll(commentIntroducingCharacter);
    return line;
  }


  /**
   * Assumes the given reader has only tsurgeon operations (not a tregex pattern), and returns
   * them as a String, mirroring the way the strings appear in the file. This is helpful
   * for lazy evaluation of the operations, as in a GUI,
   * because you do not parse the operations on load.  Comments are still excised.
   * @throws IOException
   */
  public static String getTsurgeonTextFromReader(BufferedReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (String thisLine; (thisLine = reader.readLine()) != null; ) {
      thisLine = removeComments(thisLine);
      if (emptyLinePattern.matcher(thisLine).matches()) {
        continue;
      }
      sb.append(thisLine);
      sb.append('\n');
    }
    return sb.toString();
  }

  /**
   * Parses a tsurgeon script file and compiles all operations in the file into a list
   * of pairs of tregex and tsurgeon patterns.
   *
   * @param filename file containing the tsurgeon script
   * @return A pair of a tregex and tsurgeon pattern read from a file
   * @throws IOException If there is any I/O problem
   */
  public static List<Pair<TregexPattern, TsurgeonPattern>> getOperationsFromFile(String filename, String encoding, TregexPatternCompiler compiler) throws IOException {
    List<Pair<TregexPattern,TsurgeonPattern>> operations = new ArrayList<Pair<TregexPattern, TsurgeonPattern>>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), encoding));
    for ( ; ; ) {
      Pair<TregexPattern, TsurgeonPattern> operation = getOperationFromReader(reader, compiler);
      if (operation == null) {
        break;
      }
      operations.add(operation);
    }
    reader.close();
    return operations;
  }

  /**
   * Applies {#processPattern} to a collection of trees.
   * @param matchPattern A {@link TregexPattern} to be matched against a {@link Tree}.
   * @param p A {@link TsurgeonPattern} to apply.
   * @param inputTrees The input trees to be processed
   * @return A List of the transformed trees
   */
  public static List<Tree> processPatternOnTrees(TregexPattern matchPattern, TsurgeonPattern p, Collection<Tree> inputTrees) {
    List<Tree> result = new ArrayList<Tree>();
    for (Tree tree : inputTrees)
      result.add(processPattern(matchPattern,p,tree));
    return result;
  }

  /**
   * Tries to match a pattern against a tree.  If it succeeds, apply the surgical operations contained in a {@link TsurgeonPattern}.
   * @param matchPattern A {@link TregexPattern} to be matched against a {@link Tree}.
   * @param p A {@link TsurgeonPattern} to apply.
   * @param t the {@link Tree} to match against and perform surgery on.
   * @return t, which has been surgically modified.
   */
  public static Tree processPattern(TregexPattern matchPattern, TsurgeonPattern p, Tree t) {
    TregexMatcher m = matchPattern.matcher(t);
    while (m.find()) {
      t = p.evaluate(t,m);
      if (t==null) {
        break;
      }
      m = matchPattern.matcher(t);
    }
    return t;
  }

  private static boolean matchedOnTree; // hack-in field for seeing whether there was a match.

  public static Tree processPatternsOnTree(List<Pair<TregexPattern, TsurgeonPattern>> ops, Tree t) {
    matchedOnTree = false;
    for (Pair<TregexPattern,TsurgeonPattern> op : ops) {
      try {
        if (DEBUG) {
          System.err.println("Running pattern " + op.first());
        }
        TregexMatcher m = op.first().matcher(t);
        while (m.find()) {
          matchedOnTree = true;
          t = op.second().evaluate(t,m);
          if (t == null) {
            return null;
          }
          m = op.first().matcher(t);
        }
      } catch (NullPointerException npe) {
        throw new RuntimeException("Tsurgeon.processPatternsOnTree failed to match label for pattern: " + op.first() + ", " + op.second(), npe);
      }
    }
    return t;
  }



  /**
   * Parses an operation string into a {@link TsurgeonPattern}.  Throws an {@link TsurgeonParseException} if
   * the operation string is ill-formed.
   * <p>
   * Example of use:
   * <p>
   * <tt>
   * TsurgeonPattern p = Tsurgeon.parseOperation("prune ed");
   * </tt>
   * @param operationString The operation to perform, as a text string
   * @return the operation pattern.
   */
  public static TsurgeonPattern parseOperation(String operationString) {
    try {
      TsurgeonParser parser =
        new TsurgeonParser(new StringReader(operationString + "\n"));
      return parser.Root();
    } catch(ParseException e) {
      throw new TsurgeonParseException("Error parsing Tsurgeon expression: " +
                                       operationString, e);
    } catch(TokenMgrError e) {
      throw new TsurgeonParseException("Error parsing Tsurgeon expression: " +
                                       operationString, e);
    }
  }

  /**
   * Collects a list of operation patterns into a sequence of operations to be applied.  Required to keep track of global properties
   * across a sequence of operations.  For example, if you want to insert a named node and then coindex it with another node,
   * you will need to collect the insertion and coindexation operations into a single TsurgeonPattern so that tsurgeon is aware
   * of the name of the new node and coindexation becomes possible.
   * @param patterns a list of {@link TsurgeonPattern} operations that you want to collect together into a single compound operation
   * @return a new {@link TsurgeonPattern} that performs all the operations in the sequence of the <code>patterns</code> argument
   */
  public static TsurgeonPattern collectOperations(List<TsurgeonPattern> patterns) {
    return new TsurgeonPatternRoot(patterns.toArray(new TsurgeonPattern[patterns.size()]));
  }

}
