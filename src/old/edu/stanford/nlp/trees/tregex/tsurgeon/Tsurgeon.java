// Tsurgeon
// Copyright (c) 2004-2007 The Board of Trustees of
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

package old.edu.stanford.nlp.trees.tregex.tsurgeon;

import old.edu.stanford.nlp.trees.*;
import old.edu.stanford.nlp.trees.tregex.TregexPattern;
import old.edu.stanford.nlp.trees.tregex.TregexMatcher;
import old.edu.stanford.nlp.util.StringUtils;
import old.edu.stanford.nlp.util.Pair;

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
 * The file <code>atree</code> has Penn Treebank (S-expression) format trees.
 * The other (here, two) files have Tsurgeon operations.  These consist of
 * a tregex expression on one
 * line, a blank line, and then some number of lines of Tsurgeon operations.
 * Note that (at present) you can only have one pattern and group of operations
 * per file. (This should probably be changed!)
 *
 * <p>
 * Tsurgeon uses the tregex engine to match tree patterns on trees;
 * for more information on tregex's tree-matching functionality,
 * syntax, and semantics, please see the documentation for the
 * {@link TregexPattern} class.
 * <p>

 * If you want to use Tsurgeon as an API, the relevant method is
 * {@link #processPattern}.  You will also need to look at the
 * {@link TsurgeonPattern} class and the {@link Tsurgeon#parseOperation} method.
 * <p>
 * Here is a sample invocation:
 * <pre>
 * TregexPattern matchPattern = TregexPattern.compile("SQ=sq < (/^WH/ $++ VP)");
 * List<TsurgeonPattern> ps = new ArrayList<TsurgeonPattern>();
 *
 * TsurgeonPattern p = Tsurgeon.parseOperation("relabel sq S");
 *
 * ps.add(p);
 *
 * Treebank lTrees;
 * List<Tree> result = Tsurgeon.processPatternOnTrees(matchPattern,Tsurgeon.collectOperations(ps),lTrees);
 * </pre>
 * <p>
 * <i>Note:</i> If you want to apply multiple surgery patterns, you will not want to call
 * processPatternOnTrees, but rather to call processPatternsOnTree, and to loop through the
 * trees yourself.  This is much faster.
 * <p>
 * For more information on using Tsurgeon from the command line,
 * see the {@link #main} method and the package Javadoc.
 *
 * @author Roger Levy
 */
public class Tsurgeon {

  static boolean verbose = false;

  static final Pattern emptyLinePattern = Pattern.compile("^\\s*$");
  static final String commentIntroducingCharacter = "%";
  static final Pattern commentPattern = Pattern.compile( commentIntroducingCharacter + ".*$");
  static final Pattern escapedCommentCharacterPattern = Pattern.compile('\\' + commentIntroducingCharacter);

  private Tsurgeon() {} // not an instantiable class

  /** Usage: java edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon [-s] -treeFile file-with-trees [-po matching-pattern operation] operation-file-1 operation-file-2 ... operation-file-n
   *
   * <h4>Arguments:</h4>
   *
   * Each argument should be the name of a transformation file that contains a {@link TregexPattern} pattern on the first line, then a
   * blank line, then a list of transformation operations (as specified by <b>Legal operation syntax</b> below) to apply when the pattern is matched.
   * <b>Note the bit about the blank line: currently the code crashes if it
   * isn't present!</b>
   * For example, if you want to excise an SBARQ node whenever it is the parent of an SQ node, and rename the SQ node to S, your transformation file would look like this:
   *
   * <blockquote>
   * <code>
   *    SBARQ=n1 &lt; SQ=n2<br>
   *    <br>
   *    excise n1 n1
   *    rename n2 S
   * <code>
   * </blockquote>
   *
   * <h4>Options:</h4>
   * <ul>
   *   <code>-treeFile &#60;filename&#62;</code>  specify the name of the file that has the trees you want to transform.
   *   <code>-po &#60;matchPattern&#62; &#60;operation&#62;</code>  Apply a single operation to every tree using the specified match pattern and the specified operation.  Use this option
   *   when you want to quickly try the effect of one pattern/surgery combination, and are too lazy to write a transformation file.
   *   <code>-s</code> Print each output tree on one line (default is pretty-printing).
   *   <code>-m</code> For every tree that had a matching pattern, print "before" (prepended as "Operated on:") and "after" (prepended as "Result:").  Unoperated trees just pass through the transducer as usual.
   *   <code>-encoding X</code> Uses character set X for input and output of trees.
   * </ul>
   *
   * <h4>Legal operation syntax:</h4>
   *
   * <ul>
   *   <li><code>delete &#60;name&#62;</code>  deletes the node and everything below it.
   *   <li><code>prune &#60;name&#62;</code>  Like delete, but if, after the pruning, the parent has no children anymore, the parent is pruned too.
   * <li><code>excise &#60;name1&#62; &#60;name2&#62;</code>
   *   The name1 node should either dominate or be the same as the name2 node.  This excises out everything from
   * name1 to name2.  All the children of name2 go into the parent of name1, where name1 was.
   * <li><code>relabel &#60;name&#62; &#60;new-label&#62;</code> relabels the node to have the new label.
   * <li><code>insert &#60;name&#62; &#60;position&#62;</code> inserts the named node into the position specified.
   * <li><code>move &#60;name&#62; &#60;position&#62;</code> moves the named node into the specified position
   * <p>Right now the  only ways to specify position are:
   * <p>
   *      <code>$+ &#60;name&#62;</code>     the left sister of the named node<br>
   *      <code>$- &#60;name&#62;</code>     the right sister of the named node<br>
   *      <code>&gt;i</code> the i_th daughter of the named node<br>
   *      <code>&gt;-i</code> the i_th daughter, counting from the right, of the named node.
   * <li><code>replace &#60;name1&#62; &#60;name2&#62;</code> deletes name1 and inserts a copy of name2 in its place.
   * <li><code>adjoin &#60;auxiliary_tree&#62; &lt;name&gt;</code> Adjoins the specified auxiliary tree into the named node.  The daughters of the target node will become the daughters of the foot of the auxiliary tree.
   * </ul>
   * @param args a list of names of files each of which contains a single tregex matching pattern plus a list, one per line,
   *        of transformation operations to apply to the matched pattern.
   * @throws Exception If an I/O or patern syntax error
   */
  public static void main(String[] args) throws Exception {
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
    Map<String,Integer> flagMap = new HashMap<String,Integer>();
    flagMap.put(patternOperationOption,2);
    flagMap.put(treeFileOption,1);
    flagMap.put(singleLineOption,0);
    flagMap.put(encodingOption,1);
    Map<String,String[]> argsMap = StringUtils.argsToMap(args,flagMap);
    args = argsMap.get(null);

    if(argsMap.containsKey(verboseOption))      verbose = true;
    if(argsMap.containsKey(singleLineOption))   treePrintFormats = "oneline,";   else treePrintFormats = "penn,";
    if(argsMap.containsKey(encodingOption)) encoding = argsMap.get(encodingOption)[0];

    TreePrint tp = new TreePrint(treePrintFormats, new PennTreebankLanguagePack());
    PrintWriter pwOut = new PrintWriter(new OutputStreamWriter(System.out,encoding), true);
    tp.setPrintWriter(pwOut);

    Treebank trees = new DiskTreebank(new TregexPattern.TRegexTreeReaderFactory(), encoding);
    if (argsMap.containsKey(treeFileOption)) {
      trees.loadPath(argsMap.get(treeFileOption)[0]);
    }
    List<Pair<TregexPattern,TsurgeonPattern>> ops = new ArrayList<Pair<TregexPattern,TsurgeonPattern>>();


    if (argsMap.containsKey(patternOperationOption)) {
      TregexPattern matchPattern = TregexPattern.compile(argsMap.get(patternOperationOption)[0]);
      TsurgeonPattern p = parseOperation(argsMap.get(patternOperationOption)[1]);
      ops.add(new Pair<TregexPattern,TsurgeonPattern>(matchPattern,p));
    }
    else {
      for(String arg : args) {
        Pair<TregexPattern,TsurgeonPattern> pair = getOperationFromFile(arg);
        if(verbose)
          System.err.println(pair.second());
        ops.add(pair);
      }
    }
    for (Tree t : trees ) {
      Tree original = t.deeperCopy();
      Tree result = processPatternsOnTree(ops, t);
      if (argsMap.containsKey(matchedOption) && matchedOnTree) {
        pwOut.println("Operated on: ");
        disposeOfTree(original,tp,pwOut);
        pwOut.println("Result: ");
      }
      disposeOfTree(result,tp,pwOut);
    }
  }

  private static void disposeOfTree(Tree t, TreePrint tp, PrintWriter pw) {
    if (t==null) {
      System.out.println("null");
    } else {
      tp.printTree(t,pw);
    }
  }

  /**
   * Parses a tsurgeon script text input and compiles all operations in the
   * file into one tsurgeon pattern.
   *
   * @param reader File to read patterns from
   * @return A pair of a tregex and tsurgeon pattern read from a file
   * @throws IOException If any IO problem
   */
  public static Pair<TregexPattern, TsurgeonPattern> getOperationFromReader(BufferedReader reader) throws IOException {
    String patternString = getPatternFromFile(reader);
    TregexPattern matchPattern;
    try {
      matchPattern = TregexPattern.compile(patternString);
    } catch (old.edu.stanford.nlp.trees.tregex.ParseException e) {
      System.err.println("Error parsing your tregex pattern:\n" + patternString);
      throw new RuntimeException(e);
    }

    TsurgeonPattern collectedPattern = getTsurgeonOperationsFromReader(reader);
    return new Pair<TregexPattern,TsurgeonPattern>(matchPattern,collectedPattern);
  }

  /**
   * Assumes that we arre at the beginning of a tsurgeon script file and gets the string for the
   * tregex pattern leading the file
   * @return tregex pattern string
   */
  public static String getPatternFromFile(BufferedReader reader) throws IOException {
    StringBuilder matchString = new StringBuilder();
    for (String thisLine; (thisLine = reader.readLine()) != null; ) {
      if (emptyLinePattern.matcher(thisLine).matches()) {
        break;
      } else {
        matchString.append(thisLine);
      }
    }
    return matchString.toString();
  }

  /**
   * Assumes the given reader has only tsurgeon operations (not a tregex pattern), and parses these out,
   * collection them into one operation.
   * @throws IOException
   */
  public static TsurgeonPattern getTsurgeonOperationsFromReader(BufferedReader reader) throws IOException {
    List<TsurgeonPattern> operations = new ArrayList<TsurgeonPattern>();
    for (String thisLine; (thisLine = reader.readLine()) != null; ) {
      Matcher m = commentPattern.matcher(thisLine);
      thisLine = m.replaceFirst("");
      Matcher m1 = escapedCommentCharacterPattern.matcher(thisLine);
      thisLine = m1.replaceAll(commentIntroducingCharacter);
      if (emptyLinePattern.matcher(thisLine).matches()) {
        continue;
      }
      try {
        operations.add(TsurgeonParser.parse(thisLine));
      }
      catch(ParseException e) {
        System.err.println("Error parsing your tsurgeon operation:\n" + thisLine);
        throw new RuntimeException(e.toString());
      }
    }
    return collectOperations(operations);
  }

  /**
   * Assumes the given reader has only tsurgeon operations (not a tregex pattern), and returns them as a string buffer,
   * mirroring the way the strings appear in the file - this is helpful for lazy evaluation of the operations, as in a GUI,
   * because you do not parse the operations on load.  Comments are still excised.
   * @throws IOException
   */
  public static String getTsurgeonTextFromReader(BufferedReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (String thisLine; (thisLine = reader.readLine()) != null; ) {
      Matcher m = commentPattern.matcher(thisLine);
      thisLine = m.replaceFirst("");
      Matcher m1 = escapedCommentCharacterPattern.matcher(thisLine);
      thisLine = m1.replaceAll(commentIntroducingCharacter);
      if (emptyLinePattern.matcher(thisLine).matches()) {
        continue;
      }
      sb.append(thisLine);
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Parses a tsurgeon script file and compiles all operations in the file into one tsurgeon pattern
   * @param filename file containing the tsurgeon script
   * @return A pair of a tregex and tsurgeon pattern read from a file
   * @throws IOException If there is any I/O problem
   */
  public static Pair<TregexPattern, TsurgeonPattern> getOperationFromFile(String filename) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(filename));
    Pair<TregexPattern, TsurgeonPattern> operation = getOperationFromReader(reader);
    reader.close();
    return operation;
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
    while(m.find()) {
      t = p.evaluate(t,m);
      if(t==null)
        break;
      m = matchPattern.matcher(t);
    }
    return t;
  }

  private static boolean matchedOnTree; // hack-in field for seeing whether there was a match.

  public static Tree processPatternsOnTree(List<Pair<TregexPattern, TsurgeonPattern>> ops, Tree t) {
    matchedOnTree = false;
    for (Pair<TregexPattern,TsurgeonPattern> op : ops) {
      try {
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
   * Parses an operation string into a {@link TsurgeonPattern}.  Throws an {@link IllegalArgumentException} if
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
      return new TsurgeonPatternRoot(new TsurgeonPattern[] {TsurgeonParser.parse(operationString)} );
    }
    catch(ParseException e) {
      throw new IllegalArgumentException("Ill-formed operation string: " + operationString, e);
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
